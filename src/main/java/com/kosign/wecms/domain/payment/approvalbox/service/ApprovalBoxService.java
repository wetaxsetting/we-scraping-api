package com.kosign.wecms.domain.payment.approvalbox.service;

import com.kosign.wecms.common.auditlog.service.AuditLogContext;
import com.kosign.wecms.common.auditlog.service.AuditLogService;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.common.web.SortDirection;
import com.kosign.wecms.domain.payment.approvalbox.dto.ApprovalBoxActionResponse;
import com.kosign.wecms.domain.payment.approvalbox.dto.ApprovalBoxApprover;
import com.kosign.wecms.domain.payment.approvalbox.dto.ApprovalBoxBulkApproveResponse;
import com.kosign.wecms.domain.payment.approvalbox.dto.ApprovalBoxListResponse;
import com.kosign.wecms.domain.payment.approvalbox.dto.ApprovalBoxProjection;
import com.kosign.wecms.domain.payment.approvalbox.dto.ApprovalBoxRow;
import com.kosign.wecms.domain.payment.approvalbox.dto.ApprovalBoxTabCounts;
import com.kosign.wecms.domain.payment.approvalbox.entity.ExptResolApprvrInfm;
import com.kosign.wecms.domain.payment.approvalbox.entity.ExptResolApprvrInfmId;
import com.kosign.wecms.domain.payment.approvalbox.entity.ExptResolMstr;
import com.kosign.wecms.domain.payment.approvalbox.entity.ExptResolMstrId;
import com.kosign.wecms.domain.payment.approvalbox.repository.ExptResolApprvrInfmRepository;
import com.kosign.wecms.domain.payment.approvalbox.repository.ExptResolMstrRepository;
import com.kosign.wecms.domain.payment.internaltransfer.repository.ScdulPymnSumrRepository;
import com.kosign.wecms.domain.payment.overseas.repository.OvrsPymnSumrRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Payment &gt; Approval Box service. Mirrors the legacy
 * {@code WECMS DEV/WECMS/web/WEB-INF/action/jex/wabooks/resl/resl_0001_001_r001_act.jsp}
 * read path and the {@code comm_0195_001} approve / reject family (those
 * approve / reject endpoints live in the JEX detail popup — the refactor
 * consolidates them under one feature service).
 *
 * <h2>Implemented in this MR</h2>
 * <ul>
 *   <li>{@link #list} — paginated list + four tab counts + per-row
 *       approver chain. Read-only.</li>
 *   <li>{@link #approve} — single-row approve with status advance +
 *       audit row.</li>
 *   <li>{@link #reject} — single-row reject with downstream
 *       {@code WAITING → STOPPED} cascade + audit row.</li>
 *   <li>{@link #bulkApprove} — per-row {@link #approve} with partial-
 *       success result envelope.</li>
 * </ul>
 *
 * <h2>Deferred to follow-up MRs</h2>
 * Detail popup, hard delete, file download, Excel export — see
 * {@code docs/refactor-wecms/features/payment/approval-box/migration-plan.md}
 * Phases 2-4.
 *
 * <h2>Audit logging</h2>
 * Every write path appends one event via {@link AuditLogService} with
 * {@code WSVC_ID = "approval-box"}, {@code REF_NO = "EXPT_RESOL_MSTR"},
 * {@code REF_NO1 = apprSeqNo} (single-column natural id beyond the
 * tenant key). The shared writer fans out to one row per supported
 * locale in its own {@code REQUIRES_NEW} transaction.
 *
 * <h2>Status derivation</h2>
 * The wire {@code appr_stts} ({@code P}/{@code I}/{@code C}/{@code R})
 * is computed in {@link #deriveApprStts} from {@code APPR_TYPE_CD}.
 * Replaces the legacy {@code wb_get_appr_stts(...)} PL/pgSQL helper per
 * {@code wecms-api/CLAUDE.md} §"DB Function / Legacy Function Policy".
 *
 * <h2>Tenant scoping</h2>
 * {@code BIZ_KEY} is read from {@link TenantContext#currentBizKey()} and
 * applied as the first predicate of every query. Cross-tenant lookups
 * surface as {@code WBE2050} ("not found") — never leak the existence
 * of another tenant's row.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApprovalBoxService {

    public static final int MAX_PAGE_SIZE     = 100;
    public static final int DEFAULT_PAGE_SIZE = 15;
    public static final int MAX_BULK_SIZE     = 100;

    /** Upper bound for the unpaged Excel-export fetch. Matches the
     *  shared {@code ExcelExportService.MAX_ROWS = 50_000} safety cap so
     *  the single-page export query and the streaming writer agree on
     *  the ceiling. The export path deliberately bypasses
     *  {@link #MAX_PAGE_SIZE} (which bounds the on-screen list) so a
     *  filtered result of more than one page still exports in full up to
     *  this protection limit. */
    public static final int MAX_EXPORT_ROWS   = 50_000;

    /** Refactor route slug used as the audit-log {@code wsvc_id}
     *  discriminator across every Approval Box write. Per workspace
     *  CLAUDE.md §"Refactor identifier naming" — slug, not legacy
     *  {@code resl_0001_001}. */
    static final String AUDIT_WSVC_ID = "approval-box";
    static final String REF_NO_EXPT_RESOL_MSTR = "EXPT_RESOL_MSTR";

    private static final String AUDIT_ACTIVITY_KEY      = "audit.approval_box.activity";
    private static final String AUDIT_APPROVE_DETAIL    = "audit.approval_box.approve.detail";
    private static final String AUDIT_REJECT_DETAIL     = "audit.approval_box.reject.detail";
    private static final String AUDIT_BULK_DETAIL       = "audit.approval_box.bulk_approve.detail";
    private static final String AUDIT_WITHDRAW_DETAIL   = "audit.approval_box.withdraw.detail";
    private static final String AUDIT_CHANGE_LINE_DETAIL = "audit.approval_box.change_approval_line.detail";

    /** Default ORDER BY when the wire {@code sort_field} doesn't resolve
     *  to a whitelisted enum constant. {@code regDtm DESC, apprSeqNo
     *  DESC} keeps a deterministic order across paged requests. */
    private static final Sort DEFAULT_SORT =
            Sort.by(Sort.Order.desc("regDtm"), Sort.Order.desc("apprSeqNo"));

    private static final DateTimeFormatter YYYYMMDD =
            DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter DTM14 =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final ExptResolMstrRepository        masterRepository;
    private final ExptResolApprvrInfmRepository  approverRepository;
    private final OvrsPymnSumrRepository         ovrsPymnSumrRepository;
    private final ScdulPymnSumrRepository        scdulPymnSumrRepository;
    private final AuditLogService                auditLogService;
    private final ApprovalBoxFanoutService       fanoutService;

    /* =================================================================
     *  List
     * ================================================================= */

    /**
     * Paginated list + tab counts + per-row approver chain.
     *
     * @param fromDate      required {@code YYYYMMDD}
     * @param toDate        required {@code YYYYMMDD}, ≥ {@code fromDate}
     * @param srchWd        optional partial match on {@code DOC_NM} /
     *                      {@code TITL} / {@code DOC_NO} (lower-case
     *                      LIKE with {@code !}-escape)
     * @param apprTypeCd    optional one of {@code 00} / {@code 01} /
     *                      {@code 02} / {@code 03}; blank = all tabs
     * @param sortField     optional whitelisted FIELD_ID (see
     *                      {@link ApprovalBoxSortField})
     * @param sortDirection optional {@code ASC} / {@code DESC}
     * @param page          zero-based page index (default {@code 0})
     * @param size          rows per page (default {@link #DEFAULT_PAGE_SIZE},
     *                      clamped to {@link #MAX_PAGE_SIZE})
     */
    @Transactional(readOnly = true)
    public ApprovalBoxListResponse list(
            String fromDate,
            String toDate,
            String srchWd,
            String apprTypeCd,
            String sortField,
            String sortDirection,
            int    page,
            int    size) {

        String fromDt = validateDate("from_date", fromDate);
        String toDt   = validateDate("to_date",   toDate);
        validatePeriod(fromDt, toDt);
        String apprTypeFilter = validateApprTypeCd(apprTypeCd);
        String keywordPattern = buildKeywordPattern(srchWd);

        int safeSize = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        int safePage = Math.max(0, page);
        Sort sort = buildSort(
                ApprovalBoxSortField.parse(sortField),
                SortDirection.parse(sortDirection));
        Pageable pageable = PageRequest.of(safePage, safeSize, sort);

        String  bizKey  = TenantContext.currentBizKey();
        String  userId  = TenantContext.currentUserId();
        boolean isAdmin = TenantContext.currentIsAdmin();

        Page<ApprovalBoxProjection> result = masterRepository.search(
                bizKey, userId, isAdmin,
                fromDt, toDt, apprTypeFilter, keywordPattern, keywordPattern, pageable);

        ApprovalBoxTabCounts tabCounts = masterRepository.countTabs(
                bizKey, userId, isAdmin, fromDt, toDt, keywordPattern, keywordPattern);
        if (tabCounts == null) tabCounts = ApprovalBoxTabCounts.empty();

        List<ApprovalBoxRow> rows = mapProjections(bizKey, result.getContent());

        return new ApprovalBoxListResponse(
                rows,
                result.getTotalElements(),
                tabCounts,
                result.getNumber(),
                result.getSize(),
                result.getTotalPages());
    }

    /* =================================================================
     *  List (Excel export) — all matching rows, unpaged
     * ================================================================= */

    /**
     * Fetch <em>every</em> row matching the current filters for the Excel
     * export — same query path, filter, sort, tenant scope, and per-row
     * mapping as {@link #list}, but without the {@link #MAX_PAGE_SIZE}
     * page clamp. Bounded by {@link #MAX_EXPORT_ROWS} (= the shared
     * {@code ExcelExportService.MAX_ROWS} safety limit) so the export
     * stays memory-bounded.
     *
     * <p>Consumed by {@code ApprovalBoxExportProvider.fetch(...)}. Tab
     * counts are not computed (the workbook subheader is a per-currency
     * total over the returned rows, not a tab breakdown). Legacy parity:
     * {@code resl_0001_001} Excel runs the same list query with
     * {@code PAGE_PER_CNT = 0} (all rows).
     */
    @Transactional(readOnly = true)
    public List<ApprovalBoxRow> listForExport(
            String fromDate,
            String toDate,
            String srchWd,
            String apprTypeCd,
            String sortField,
            String sortDirection) {

        String fromDt = validateDate("from_date", fromDate);
        String toDt   = validateDate("to_date",   toDate);
        validatePeriod(fromDt, toDt);
        String apprTypeFilter = validateApprTypeCd(apprTypeCd);
        String keywordPattern = buildKeywordPattern(srchWd);

        Sort sort = buildSort(
                ApprovalBoxSortField.parse(sortField),
                SortDirection.parse(sortDirection));
        Pageable pageable = PageRequest.of(0, MAX_EXPORT_ROWS, sort);

        String  bizKey  = TenantContext.currentBizKey();
        String  userId  = TenantContext.currentUserId();
        boolean isAdmin = TenantContext.currentIsAdmin();

        Page<ApprovalBoxProjection> result = masterRepository.search(
                bizKey, userId, isAdmin,
                fromDt, toDt, apprTypeFilter, keywordPattern, keywordPattern, pageable);

        return mapProjections(bizKey, result.getContent());
    }

    /* =================================================================
     *  Approver chain (read) — shared by the payment Scheduled detail
     *  pages so they render the REAL EXPT_RESOL_APPRVR_INFM statuses and
     *  gate Approve/Reject on "current user is the active pending
     *  approver" (legacy APPR_YN / comm_0195_001 parity), instead of a
     *  synthesised chain.
     * ================================================================= */

    /**
     * Return the ordered approver chain ({@code APPRVR_LINE_NO ASC}) for one
     * approval document. Read-only; tenant-scoped via {@link TenantContext}.
     * Carries the real per-approver {@code APPRVR_STTS} (P/A/R/W/S) so the
     * frontend can show the current pending approver and the per-row status
     * chips. Returns an empty list when the document has no approver rows.
     *
     * @throws BusinessException {@code WBE_VALIDATION} when {@code apprSeqNo}
     *         is blank.
     */
    @Transactional(readOnly = true)
    public List<ApprovalBoxApprover> approvers(String apprSeqNo) {
        if (apprSeqNo == null || apprSeqNo.isBlank()) {
            throw new BusinessException("WBE_VALIDATION", "appr_seq_no: is required");
        }
        String bizKey = TenantContext.currentBizKey();
        return approverRepository.findChain(bizKey, apprSeqNo).stream()
                .map(ApprovalBoxService::toApprover)
                .toList();
    }

    /* =================================================================
     *  Approve
     * ================================================================= */

    /**
     * Approve one document on behalf of the current user. The caller
     * must be an assigned approver whose row is in {@code PENDING}
     * state. Per legacy semantics, the per-step ordering is enforced by
     * the chain's earlier {@code WAITING → PENDING} promotion (which is
     * done at the moment the previous step approves) — by the time a
     * user can see the Approve button, their row should already be
     * {@code PENDING}.
     *
     * <p>State transitions ({@code apprvr_stts}, {@code appr_type_cd}):
     * <ol>
     *   <li>Caller's approver row {@code P → A}, {@code APPR_DTM} stamped.</li>
     *   <li>If a downstream peer remains in {@code W} state — promote
     *       the next one in line to {@code P}. Master
     *       {@code APPR_TYPE_CD = '03'} (In Progress).</li>
     *   <li>Else (every approver now {@code A}) — master
     *       {@code APPR_TYPE_CD = '01'} (Approved).</li>
     * </ol>
     *
     * @throws BusinessException {@code WBE2050} when the master row is
     *         missing for the current tenant.
     * @throws BusinessException {@code WBE2053} when the workflow is
     *         already finalised (master {@code APPR_TYPE_CD} in
     *         {@code 01}/{@code 02}).
     * @throws BusinessException {@code WBE2051} when the caller has no
     *         approver row on this document.
     * @throws BusinessException {@code WBE2052} when the caller's
     *         approver row is not in {@code PENDING} state.
     */
    @Transactional
    public ApprovalBoxActionResponse approve(String apprSeqNo) {
        ApproveContext ctx = loadForAction(apprSeqNo);
        applyApprove(ctx);

        auditLogService.append(AuditLogContext.builder()
                .wsvcId(AUDIT_WSVC_ID)
                .refNo(REF_NO_EXPT_RESOL_MSTR)
                .refNo1(ctx.master.getApprSeqNo())
                .activityKey(AUDIT_ACTIVITY_KEY)
                .detailKey(AUDIT_APPROVE_DETAIL)
                .build());

        log.info("approval-box approved bizKey={} apprSeqNo={} approverId={} newApprTypeCd={}",
                ctx.master.getBizKey(), ctx.master.getApprSeqNo(), ctx.userId,
                ctx.master.getApprTypeCd());

        return new ApprovalBoxActionResponse(
                ctx.master.getApprSeqNo(),
                deriveApprStts(ctx.master.getApprTypeCd()),
                ctx.approvedCount,
                ctx.totalCount);
    }

    /* =================================================================
     *  Reject
     * ================================================================= */

    /**
     * Reject one document on behalf of the current user. Cancels the
     * workflow immediately:
     * <ol>
     *   <li>Caller's approver row {@code P → R}, {@code APPR_DTM} stamped.</li>
     *   <li>Every still-waiting downstream peer {@code W → S} (one
     *       UPDATE).</li>
     *   <li>Master {@code APPR_TYPE_CD = '02'} (Rejected).</li>
     * </ol>
     *
     * @throws BusinessException same code set as {@link #approve}.
     */
    @Transactional
    public ApprovalBoxActionResponse reject(String apprSeqNo) {
        ApproveContext ctx = loadForAction(apprSeqNo);
        applyReject(ctx);

        auditLogService.append(AuditLogContext.builder()
                .wsvcId(AUDIT_WSVC_ID)
                .refNo(REF_NO_EXPT_RESOL_MSTR)
                .refNo1(ctx.master.getApprSeqNo())
                .activityKey(AUDIT_ACTIVITY_KEY)
                .detailKey(AUDIT_REJECT_DETAIL)
                .build());

        log.info("approval-box rejected bizKey={} apprSeqNo={} approverId={}",
                ctx.master.getBizKey(), ctx.master.getApprSeqNo(), ctx.userId);

        return new ApprovalBoxActionResponse(
                ctx.master.getApprSeqNo(),
                deriveApprStts(ctx.master.getApprTypeCd()),
                ctx.approvedCount,
                ctx.totalCount);
    }

    /* =================================================================
     *  Bulk approve
     * ================================================================= */

    /**
     * Apply {@link #approve} to every {@code apprSeqNo} in the batch.
     * Per-row outcome is reported separately so partial success is
     * visible to the caller: e.g. "2 approved, 1 not-your-turn".
     *
     * <p>Each row is wrapped in its own savepoint via a self-invocation
     * of {@link #approve} — when one row's transaction rolls back the
     * others still commit. The audit-log writer's {@code REQUIRES_NEW}
     * propagation keeps the audit row written for every successful
     * approval irrespective of sibling outcomes.
     */
    public ApprovalBoxBulkApproveResponse bulkApprove(List<String> apprSeqNos) {
        if (apprSeqNos == null || apprSeqNos.isEmpty()) {
            throw new BusinessException("WBE2054", MAX_BULK_SIZE);
        }
        if (apprSeqNos.size() > MAX_BULK_SIZE) {
            throw new BusinessException("WBE2054", MAX_BULK_SIZE);
        }

        List<ApprovalBoxActionResponse> successes = new ArrayList<>();
        List<ApprovalBoxBulkApproveResponse.Failure> failures = new ArrayList<>();

        for (String apprSeqNo : apprSeqNos) {
            try {
                successes.add(approve(apprSeqNo));
            } catch (BusinessException ex) {
                failures.add(new ApprovalBoxBulkApproveResponse.Failure(
                        apprSeqNo, ex.getCode(), ex.getMessage()));
            }
        }

        if (!successes.isEmpty()) {
            // One audit row per bulk-approve invocation, in addition
            // to the per-row Approve audits already written. Carries
            // the first apprSeqNo as REF_NO1 — bulk operations have
            // no single PK; the legacy approach was the same.
            auditLogService.append(AuditLogContext.builder()
                    .wsvcId(AUDIT_WSVC_ID)
                    .refNo(REF_NO_EXPT_RESOL_MSTR)
                    .refNo1(successes.get(0).apprSeqNo())
                    .activityKey(AUDIT_ACTIVITY_KEY)
                    .detailKey(AUDIT_BULK_DETAIL)
                    .build());
        }

        return new ApprovalBoxBulkApproveResponse(successes, failures);
    }

    /* =================================================================
     *  Withdraw / Change approval line (non-card payment types)
     * ================================================================= */

    /**
     * Withdraw an in-flight Approval Box request for any non-card payment
     * type (Internal Transfer / Vendor / Expense / Payroll / Overseas).
     * Delegates the approval-table work to
     * {@link ApprovalBoxFanoutService#withdrawApproval} (Pending /
     * In Progress guard + delete of {@code EXPT_RESOL_MSTR} +
     * {@code EXPT_RESOL_APPRVR_INFM}), then clears the source payment
     * batch's stamp so it returns to Draft, then writes one audit row.
     * Mirrors the card-specific
     * {@code CardTransactionsService.withdrawApproval} flow (which clears
     * {@code CARD_TRNS_HSTR} instead).
     *
     * <p>Card ({@code APPR_DV = '33'}) keeps its dedicated
     * {@code /card-transactions/by-approval/{apprSeqNo}} endpoint; the
     * frontend never routes card rows here. The back-stamp routing below
     * matches {@link #clearSourceApprStamp} exactly (OVERSEAS → OVRS, every
     * other type → SCDUL) — clearing {@code APPR_SEQ_NO} only, since neither
     * source table has an {@code APPR_STTS} column in legacy JEX.
     *
     * @throws BusinessException {@code WBE_NOT_FOUND} when the master is
     *         missing for the tenant; {@code WBE_VALIDATION} when the
     *         master is already Approved / Rejected or {@code apprSeqNo}
     *         is blank (both surfaced by the fanout helper).
     */
    @Transactional
    public void withdraw(String apprSeqNo) {
        ExptResolMstr master = fanoutService.withdrawApproval(apprSeqNo);

        String userId = TenantContext.currentUserId();
        String nowDtm = OffsetDateTime.now(ZoneOffset.UTC).format(DTM14);
        clearSourceApprStamp(master.getBizKey(), master.getApprSeqNo(),
                master.getApprDv(), userId, nowDtm);

        auditLogService.append(AuditLogContext.builder()
                .wsvcId(AUDIT_WSVC_ID)
                .refNo(REF_NO_EXPT_RESOL_MSTR)
                .refNo1(master.getApprSeqNo())
                .activityKey(AUDIT_ACTIVITY_KEY)
                .detailKey(AUDIT_WITHDRAW_DETAIL)
                .build());

        log.info("approval-box withdraw bizKey={} apprSeqNo={} apprDv={} userId={}",
                master.getBizKey(), master.getApprSeqNo(), master.getApprDv(), userId);
    }

    /**
     * Replace the approver chain on an in-flight Approval Box request for
     * any non-card payment type with the chain attached to
     * {@code apprLineNo}, resetting the master to Pending. Delegates to
     * {@link ApprovalBoxFanoutService#changeApprovalLine} (Pending /
     * In Progress guard + chain rebuild) then writes one audit row. The
     * source payment batch's {@code APPR_SEQ_NO} is intentionally left
     * unchanged — only the approver chain rotates (legacy + card parity).
     *
     * @throws BusinessException same code set as {@link #withdraw} plus
     *         {@code WBE_VALIDATION} when {@code apprLineNo} is blank or
     *         resolves to no approvers.
     */
    @Transactional
    public void changeApprovalLine(String apprSeqNo, Long apprLineNo) {
        ExptResolMstr master = fanoutService.changeApprovalLine(apprSeqNo, apprLineNo);

        auditLogService.append(AuditLogContext.builder()
                .wsvcId(AUDIT_WSVC_ID)
                .refNo(REF_NO_EXPT_RESOL_MSTR)
                .refNo1(master.getApprSeqNo())
                .activityKey(AUDIT_ACTIVITY_KEY)
                .detailKey(AUDIT_CHANGE_LINE_DETAIL)
                .build());

        log.info("approval-box change-approval-line bizKey={} apprSeqNo={} newApprLineNo={}",
                master.getBizKey(), master.getApprSeqNo(), apprLineNo);
    }

    /** Clear the approval stamp on the source payment batch so a
     *  withdrawn request returns to Draft (nulls {@code APPR_SEQ_NO} only —
     *  neither source table has an {@code APPR_STTS} column in legacy JEX).
     *  OVERSEAS ({@code '34'}) → OVRS_PYMN_SUMR, every other non-card type →
     *  SCDUL_PYMN_SUMR. A zero-row update is a harmless no-op (e.g. seed data
     *  with no source row). */
    private void clearSourceApprStamp(String bizKey, String apprSeqNo, String apprDv,
                                      String userId, String nowDtm) {
        if (ApprDv.OVERSEAS.code().equals(apprDv)) {
            ovrsPymnSumrRepository.clearApprSeqNo(bizKey, apprSeqNo, userId, nowDtm);
        } else {
            scdulPymnSumrRepository.clearApprSeqNo(bizKey, apprSeqNo, userId, nowDtm);
        }
    }

    /* =================================================================
     *  Action helpers — load + mutate
     * ================================================================= */

    /** Shared load step for approve / reject. Loads the master, asserts
     *  it's not already finalised, loads the caller's approver row, and
     *  asserts it's in PENDING state. */
    private ApproveContext loadForAction(String apprSeqNo) {
        if (apprSeqNo == null || apprSeqNo.isBlank()) {
            throw new BusinessException("WBE_VALIDATION", "appr_seq_no: is required");
        }
        String bizKey = TenantContext.currentBizKey();
        String userId = TenantContext.currentUserId();
        if (bizKey == null || userId == null) {
            throw new BusinessException("WBE9999");
        }

        ExptResolMstr master = masterRepository
                .findById(new ExptResolMstrId(bizKey, apprSeqNo))
                .orElseThrow(() -> {
                    log.warn("approval-box action target missing bizKey={} apprSeqNo={}",
                            bizKey, apprSeqNo);
                    return new BusinessException("WBE2050");
                });

        if (ApprTypeCd.APPROVED.equals(master.getApprTypeCd())
         || ApprTypeCd.REJECTED.equals(master.getApprTypeCd())) {
            throw new BusinessException("WBE2053");
        }

        ExptResolApprvrInfm approverRow = approverRepository
                .findById(new ExptResolApprvrInfmId(bizKey, apprSeqNo, userId))
                .orElseThrow(() -> {
                    log.warn("approval-box action caller is not assigned bizKey={} apprSeqNo={} userId={}",
                            bizKey, apprSeqNo, userId);
                    return new BusinessException("WBE2051");
                });

        if (!ApprvrStts.PENDING.equals(approverRow.getApprvrStts())) {
            throw new BusinessException("WBE2052");
        }

        ApproveContext ctx = new ApproveContext();
        ctx.bizKey      = bizKey;
        ctx.userId      = userId;
        ctx.master      = master;
        ctx.approverRow = approverRow;
        return ctx;
    }

    private void applyApprove(ApproveContext ctx) {
        String nowDtm = OffsetDateTime.now(ZoneOffset.UTC).format(DTM14);

        ctx.approverRow.setApprvrStts(ApprvrStts.APPROVED);
        ctx.approverRow.setApprDtm(nowDtm);
        ctx.approverRow.setModId(ctx.userId);
        ctx.approverRow.setModDtm(nowDtm);
        approverRepository.save(ctx.approverRow);

        long pendingRemaining = approverRepository.countByStatus(
                ctx.bizKey, ctx.master.getApprSeqNo(), ApprvrStts.PENDING);
        long waitingRemaining = approverRepository.countByStatus(
                ctx.bizKey, ctx.master.getApprSeqNo(), ApprvrStts.WAITING);

        if (pendingRemaining == 0 && waitingRemaining == 0) {
            ctx.master.setApprTypeCd(ApprTypeCd.APPROVED);
        } else {
            ctx.master.setApprTypeCd(ApprTypeCd.IN_PROGRESS);
            // Promote the next waiting approver (by line_no) to PENDING.
            promoteNextWaiting(ctx, nowDtm);
        }

        ctx.master.setModId(ctx.userId);
        ctx.master.setModDtm(nowDtm);
        masterRepository.save(ctx.master);

        // No write-back to the payment source table: neither SCDUL_PYMN_SUMR nor
        // OVRS_PYMN_SUMR has an APPR_STTS column in legacy JEX. The list/detail
        // pages derive status on read from EXPT_RESOL_MSTR.APPR_TYPE_CD.

        ctx.totalCount    = approverRepository.findChain(
                ctx.bizKey, ctx.master.getApprSeqNo()).size();
        ctx.approvedCount = approverRepository.countByStatus(
                ctx.bizKey, ctx.master.getApprSeqNo(), ApprvrStts.APPROVED);
    }

    private void applyReject(ApproveContext ctx) {
        String nowDtm = OffsetDateTime.now(ZoneOffset.UTC).format(DTM14);

        ctx.approverRow.setApprvrStts(ApprvrStts.REJECTED);
        ctx.approverRow.setApprDtm(nowDtm);
        ctx.approverRow.setModId(ctx.userId);
        ctx.approverRow.setModDtm(nowDtm);
        approverRepository.save(ctx.approverRow);

        // Downstream cascade: every waiting peer → stopped.
        approverRepository.stopDownstream(
                ctx.bizKey, ctx.master.getApprSeqNo(), ctx.userId, nowDtm);

        ctx.master.setApprTypeCd(ApprTypeCd.REJECTED);
        ctx.master.setModId(ctx.userId);
        ctx.master.setModDtm(nowDtm);
        masterRepository.save(ctx.master);

        // No write-back: status derives on read from EXPT_RESOL_MSTR.APPR_TYPE_CD
        // (legacy JEX has no APPR_STTS column on SCDUL_PYMN_SUMR / OVRS_PYMN_SUMR).

        ctx.totalCount    = approverRepository.findChain(
                ctx.bizKey, ctx.master.getApprSeqNo()).size();
        ctx.approvedCount = approverRepository.countByStatus(
                ctx.bizKey, ctx.master.getApprSeqNo(), ApprvrStts.APPROVED);
    }

    private void promoteNextWaiting(ApproveContext ctx, String nowDtm) {
        List<ExptResolApprvrInfm> chain = approverRepository.findChain(
                ctx.bizKey, ctx.master.getApprSeqNo());
        for (ExptResolApprvrInfm a : chain) {
            if (ApprvrStts.WAITING.equals(a.getApprvrStts())) {
                a.setApprvrStts(ApprvrStts.PENDING);
                a.setModId(ctx.userId);
                a.setModDtm(nowDtm);
                approverRepository.save(a);
                return;
            }
        }
    }

    /* =================================================================
     *  Status derivation — replaces wb_get_appr_stts(...)
     * ================================================================= */

    /** Maps the legacy {@code APPR_TYPE_CD} tab discriminator to the
     *  wire {@code appr_stts} value. */
    static String deriveApprStts(String apprTypeCd) {
        if (apprTypeCd == null) return ApprvrStts.PENDING;
        return switch (apprTypeCd) {
            case ApprTypeCd.APPROVED    -> "C";  // Completed
            case ApprTypeCd.REJECTED    -> "R";
            case ApprTypeCd.IN_PROGRESS -> "I";
            default                     -> "P";
        };
    }

    /* =================================================================
     *  Validation
     * ================================================================= */

    private static String validateDate(String fieldName, String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BusinessException("WBE_VALIDATION", fieldName + ": is required");
        }
        String trimmed = raw.trim();
        if (trimmed.length() != 8) {
            throw new BusinessException("WBE_VALIDATION", fieldName + ": invalid date format (YYYYMMDD)");
        }
        try {
            LocalDate.parse(trimmed, YYYYMMDD);
        } catch (DateTimeParseException ex) {
            throw new BusinessException("WBE_VALIDATION", fieldName + ": invalid date format (YYYYMMDD)");
        }
        return trimmed;
    }

    private static void validatePeriod(String fromDt, String toDt) {
        if (fromDt.compareTo(toDt) > 0) {
            throw new BusinessException("WBE_VALIDATION", "from_date: must be on or before to_date");
        }
    }

    /** {@code appr_type_cd} optional; blank ⇒ all tabs. If supplied,
     *  must be one of {@code 00}/{@code 01}/{@code 02}/{@code 03}. */
    private static String validateApprTypeCd(String apprTypeCd) {
        if (apprTypeCd == null) return null;
        String trimmed = apprTypeCd.trim();
        if (trimmed.isEmpty()) return null;
        if (!trimmed.equals(ApprTypeCd.PENDING)
         && !trimmed.equals(ApprTypeCd.APPROVED)
         && !trimmed.equals(ApprTypeCd.REJECTED)
         && !trimmed.equals(ApprTypeCd.IN_PROGRESS)) {
            throw new BusinessException("WBE_VALIDATION", "appr_type_cd: must be 00/01/02/03");
        }
        return trimmed;
    }

    /** Same convention as {@code ErpTrnsHistoryService.buildKeywordPattern}
     *  / {@code FirmBankingHistoryService}: {@code !}-escape on LIKE
     *  meta-chars, wrap with {@code %…%}, lowercase. */
    private static String buildKeywordPattern(String raw) {
        if (raw == null) return null;
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) return null;
        StringBuilder b = new StringBuilder(trimmed.length() + 2).append('%');
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (c == '%' || c == '_' || c == '!') b.append('!');
            b.append(c);
        }
        b.append('%');
        return b.toString().toLowerCase(Locale.ROOT);
    }

    private static Sort buildSort(ApprovalBoxSortField field, SortDirection direction) {
        if (field == null || direction == null) {
            return DEFAULT_SORT;
        }
        Sort.Order primary = direction == SortDirection.ASC
                ? Sort.Order.asc(field.entityProperty())
                : Sort.Order.desc(field.entityProperty());
        return Sort.by(primary).and(Sort.by(Sort.Order.desc("apprSeqNo")));
    }

    /* =================================================================
     *  Row mapping — projection + chain → wire DTO
     * ================================================================= */

    /**
     * Map a page of projections to wire rows, shared by {@link #list} and
     * {@link #listForExport}. Batch-fetches CCY_CD from both
     * OVRS_PYMN_SUMR and SCDUL_PYMN_SUMR for every apprSeqNo in the slice
     * (SCDUL covers Internal Transfer / Expense / Vendor / Payroll; OVRS
     * covers Overseas). The two result sets are merged so a USD batch
     * from either table resolves to the correct amount column instead of
     * falling back to the "VND" default.
     */
    private List<ApprovalBoxRow> mapProjections(
            String bizKey, List<ApprovalBoxProjection> projections) {
        List<String> apprSeqNos = projections.stream()
                .map(ApprovalBoxProjection::apprSeqNo)
                .filter(s -> s != null && !s.isBlank())
                .distinct()
                .collect(Collectors.toList());
        Map<String, String> ccyByApprSeqNo = new HashMap<>();
        Map<String, String> fileNameByApprSeqNo = new HashMap<>();
        if (!apprSeqNos.isEmpty()) {
            ovrsPymnSumrRepository.findCcyCdByBizKeyAndApprSeqNos(bizKey, apprSeqNos)
                    .forEach(row -> ccyByApprSeqNo.put((String) row[0], (String) row[1]));
            // putIfAbsent keeps the OVRS value when both tables happen to
            // carry the same apprSeqNo (shouldn't occur, but defensive).
            scdulPymnSumrRepository.findCcyCdByBizKeyAndApprSeqNos(bizKey, apprSeqNos)
                    .forEach(row -> ccyByApprSeqNo.putIfAbsent((String) row[0], (String) row[1]));

            // File Name — uploaded batch file name. Legacy reads
            // COALESCE(SCDUL.TITL, OVRS.TITL) because it stored the file
            // name in TITL; the refactor moved it to ORG_FILE_NM (TITL now
            // holds the typed title), so the refactor-correct source is
            // COALESCE(SCDUL.ORG_FILE_NM, OVRS.ORG_FILE_NM) — matching the
            // orgFileNm source the Scheduled Transfer / Transfer Result
            // pages already use. SCDUL wins, so load it first (put) and let
            // OVRS fill only the gaps (putIfAbsent). Skip nulls so a null
            // SCDUL file name doesn't mask a populated OVRS one (mirrors the
            // COALESCE fall-through); a null on both stays blank, per spec.
            scdulPymnSumrRepository.findOrgFileNmByBizKeyAndApprSeqNos(bizKey, apprSeqNos)
                    .forEach(row -> {
                        if (row[1] != null) fileNameByApprSeqNo.put((String) row[0], (String) row[1]);
                    });
            ovrsPymnSumrRepository.findOrgFileNmByBizKeyAndApprSeqNos(bizKey, apprSeqNos)
                    .forEach(row -> {
                        if (row[1] != null) fileNameByApprSeqNo.putIfAbsent((String) row[0], (String) row[1]);
                    });
        }
        return projections.stream()
                .map(p -> toRow(bizKey, p, ccyByApprSeqNo, fileNameByApprSeqNo))
                .toList();
    }

    private ApprovalBoxRow toRow(String bizKey, ApprovalBoxProjection p,
                                  Map<String, String> ccyByApprSeqNo,
                                  Map<String, String> fileNameByApprSeqNo) {
        List<ApprovalBoxApprover> approvers = approverRepository
                .findChain(bizKey, p.apprSeqNo())
                .stream()
                .sorted(Comparator.comparing(
                        a -> a.getApprvrLineNo() == null ? BigDecimal.ZERO : a.getApprvrLineNo()))
                .map(ApprovalBoxService::toApprover)
                .toList();

        // Prefer the actual CCY_CD from OVRS_PYMN_SUMR; fall back to "VND" for
        // payment types whose sibling table hasn't been joined yet.
        String currency = ccyByApprSeqNo.getOrDefault(p.apprSeqNo(), "VND");
        if (currency == null || currency.isBlank()) currency = "VND";

        // Pick the amount column that matches the currency, with TOT_PAY_KHR_AMT
        // as a fallback for legacy records written before the three-column split.
        // Old code always wrote to KHR regardless of currency; new code writes
        // to the correct column and sets KHR = 0 for USD rows.
        BigDecimal khr = p.totPayKhrAmt() != null ? p.totPayKhrAmt() : BigDecimal.ZERO;
        BigDecimal amt;
        if ("USD".equalsIgnoreCase(currency)) {
            BigDecimal usd = p.totPayUsdAmt();
            amt = (usd != null && usd.compareTo(BigDecimal.ZERO) != 0) ? usd : khr;
        } else if ("VND".equalsIgnoreCase(currency)) {
            BigDecimal vnd = p.totPayVndAmt();
            amt = (vnd != null && vnd.compareTo(BigDecimal.ZERO) != 0) ? vnd : khr;
        } else {
            amt = khr;
        }

        return new ApprovalBoxRow(
                p.apprSeqNo(),
                p.docNo(),
                p.titl(),
                fileNameByApprSeqNo.getOrDefault(p.apprSeqNo(), ""),
                p.cont(),
                p.apprDv(),
                ApprDv.resolveSlug(p.apprDv()),
                currency,
                amt.toPlainString(),
                p.payDate(),
                p.draftUserId(),
                p.draftUserNm(),
                p.regDtm(),
                deriveApprStts(p.apprTypeCd()),
                p.apprvrTotCnt() == null ? 0L : p.apprvrTotCnt(),
                p.apprvrApprTotCnt() == null ? 0L : p.apprvrApprTotCnt(),
                p.payEndYn(),
                approvers);
    }

    private static ApprovalBoxApprover toApprover(ExptResolApprvrInfm a) {
        return new ApprovalBoxApprover(
                a.getApprvrLineNo() == null ? null : a.getApprvrLineNo().intValue(),
                a.getApprvrId(),
                a.getApprvrNm(),
                a.getApprvrStts(),
                a.getApprDtm());
    }

    /** Plain context holder shared across the approve / reject load +
     *  mutate steps. Local to the service. */
    private static final class ApproveContext {
        String              bizKey;
        String              userId;
        ExptResolMstr       master;
        ExptResolApprvrInfm approverRow;
        long                totalCount;
        long                approvedCount;
    }
}
