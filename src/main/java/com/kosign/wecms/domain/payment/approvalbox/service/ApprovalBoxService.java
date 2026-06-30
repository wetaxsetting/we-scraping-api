package com.kosign.wecms.domain.payment.approvalbox.service;

import com.kosign.wecms.common.auditlog.service.AuditLogContext;
import com.kosign.wecms.common.auditlog.service.AuditLogService;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.common.web.SortDirection;
import com.kosign.wecms.domain.payment.approvalbox.dto.ApprovalBoxActionResponse;
import com.kosign.wecms.domain.payment.approvalbox.dto.ApprovalBoxApprover;
import com.kosign.wecms.domain.payment.approvalbox.dto.ApprovalBoxBulkApproveResponse;
import com.kosign.wecms.domain.payment.approvalbox.dto.ApprovalBoxFooterTotals;
import com.kosign.wecms.domain.payment.approvalbox.dto.ApprovalBoxListResponse;
import com.kosign.wecms.domain.payment.approvalbox.dto.ApprovalBoxProjection;
import com.kosign.wecms.domain.payment.approvalbox.dto.ApprovalBoxRow;
import com.kosign.wecms.domain.payment.approvalbox.dto.ApprovalBoxTabCounts;
import com.kosign.wecms.domain.payment.approvalbox.entity.ExptResolApprvrInfm;
import com.kosign.wecms.domain.payment.approvalbox.entity.ExptResolApprvrInfmId;
import com.kosign.wecms.domain.payment.approvalbox.entity.ExptResolMstr;
import com.kosign.wecms.domain.payment.approvalbox.entity.ExptResolMstrId;
import com.kosign.wecms.domain.auth.entity.UserBusiConnInfm;
import com.kosign.wecms.domain.auth.repository.UserBusiConnInfmRepository;
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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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

    /** Corporate Card {@code APPR_DV}. Card approvals take their amount
     *  from {@code SUM(CARD_TRNS_HSTR.TRSC_AMT)} (legacy
     *  {@code IDO.EXPT_RESOL_MSTR_R003.xml:62-65}), not the
     *  {@code EXPT_RESOL_MSTR} amount columns. */
    private static final String CARD_APPR_DV = "33";

    private static final DateTimeFormatter YYYYMMDD =
            DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter DTM14 =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /** Shared, non-admin user-image URI template — same value the
     *  Approval Line + notification paths emit. The streaming endpoint
     *  is {@code UserController#streamImage} ({@code GET /api/v1/users/{id}/image}). */
    private static final String USER_IMAGE_URI_TEMPLATE = "/api/v1/users/%s/image";

    /** Active-link sentinel for {@code USER_BUSI_CONN_INFM.DEL_YN}. */
    private static final String DEL_YN_ACTIVE = "N";

    private final ExptResolMstrRepository        masterRepository;
    private final ExptResolApprvrInfmRepository  approverRepository;
    private final UserBusiConnInfmRepository     userBusiRepo;
    private final OvrsPymnSumrRepository         ovrsPymnSumrRepository;
    private final ScdulPymnSumrRepository        scdulPymnSumrRepository;
    /** Corporate Card (APPR_DV='33') currency enrichment — the card
     *  approval has no SCDUL / OVRS row, so its display currency comes
     *  from CARD_TRNS_HSTR.CCY_CD (GAP — was an unconditional "VND"). */
    private final com.kosign.wecms.domain.payment.cardtransactions.repository.CardTrnsHstrRepository cardTrnsHstrRepository;
    private final AuditLogService                auditLogService;
    private final ApprovalBoxFanoutService       fanoutService;
    /** Lifecycle bulletin/notification writer — approve/reject remark +
     *  notification (legacy parity). */
    private final com.kosign.wecms.domain.payment.notification.service.ApprovalNotificationService
                                                 approvalNotificationService;
    /** Emits APPROVED / REJECTED lifecycle signals consumed by the HAENGSUNG
     *  Oracle approval-status write-back (the single Oracle-sync subscriber). */
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;

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

        // Footer totals are TAB-UNFILTERED (legacy resl_0001_001 footer bar):
        // the document count is the tab-unfiltered row count — equal to the sum
        // of the four tab counts because APPR_TYPE_CD is NOT NULL ∈ {00,01,02,03}
        // — and the per-currency amount sums span the same full scope, NOT the
        // tab-filtered page. result.getTotalElements() (tab-filtered) still
        // drives pagination below.
        long footerDocumentCount = tabCounts.pending() + tabCounts.inProgress()
                + tabCounts.approved() + tabCounts.rejected();
        ApprovalBoxFooterTotals footerTotals = computeFooterTotals(
                bizKey, userId, isAdmin, fromDt, toDt, keywordPattern, footerDocumentCount);

        return new ApprovalBoxListResponse(
                rows,
                result.getTotalElements(),
                tabCounts,
                footerTotals,
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
        List<ExptResolApprvrInfm> chain = approverRepository.findChain(bizKey, apprSeqNo);
        Map<String, String> imgUrlByApprvrId = resolveImageUrls(
                bizKey, chain.stream().map(ExptResolApprvrInfm::getApprvrId).toList());
        return chain.stream()
                .map(a -> toApprover(a, imgUrlByApprvrId))
                .toList();
    }

    /**
     * Single Approval Box master row by {@code APPR_SEQ_NO} — a reliable
     * detail lookup (direct {@code EXPT_RESOL_MSTR} PK read), NOT the
     * keyword {@code srch_wd} list search (which matches {@code DOC_NM} /
     * {@code DOC_NO} / title and would never match a raw sequence number).
     * Returns the same {@link ApprovalBoxRow} shape the list emits — built
     * through {@link #mapProjections} so currency / file-name / approver-chain
     * resolution stay identical to the list path. Used by
     * {@code ApprovalDetailService} to back the common Approval Details popup
     * (notification entry point, etc.). Tenant-scoped; {@code null} when the
     * approval is missing or belongs to another tenant.
     */
    @Transactional(readOnly = true)
    public ApprovalBoxRow findOne(String apprSeqNo) {
        if (apprSeqNo == null || apprSeqNo.isBlank()) {
            return null;
        }
        String bizKey = TenantContext.currentBizKey();
        ExptResolMstr m = masterRepository
                .findByBizKeyAndApprSeqNoIn(bizKey, List.of(apprSeqNo))
                .stream()
                .findFirst()
                .orElse(null);
        if (m == null) {
            return null;
        }

        // Approver-chain counts mirror the list query's apprvr_tot_cnt /
        // apprvr_appr_tot_cnt (cosmetic "Approver (n/total)" label); the full
        // chain itself is re-resolved inside toRow.
        long total = approverRepository.findChain(bizKey, apprSeqNo).size();
        long approved = approverRepository.findChain(bizKey, apprSeqNo).stream()
                .filter(a -> "A".equals(a.getApprvrStts()))
                .count();

        ApprovalBoxProjection p = new ApprovalBoxProjection(
                m.getApprSeqNo(), m.getDocNo(), m.getTitl(), m.getApprDv(), m.getDocNm(), m.getCont(),
                m.getTotPayKhrAmt(), m.getTotPayUsdAmt(), m.getTotPayVndAmt(), m.getPayDate(),
                m.getDraftUserId(), m.getDraftUserNm(), m.getRegDtm(), m.getApprTypeCd(), m.getPayEndYn(),
                total, approved);

        ApprovalBoxRow row = mapProjections(bizKey, List.of(p)).stream().findFirst().orElse(null);
        // Payroll Type (APPR_FUND_TP) — detail-only, sourced from the master
        // entity here (mapProjections has no access to it). Null for non-payroll;
        // the frontend renders the row only when kind === payroll.
        return row == null ? null : row.withApprFundTp(m.getApprFundTp());
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

        // Oracle sync signal only on FINAL approval ('01'); an intermediate
        // multi-approver advance ('03' IN_PROGRESS) has no Oracle mapping.
        if (ApprTypeCd.APPROVED.equals(ctx.master.getApprTypeCd())) {
            eventPublisher.publishEvent(new com.kosign.wecms.domain.payment.approvalbox.event
                    .PaymentApprovalStatusChangedEvent(ctx.master.getBizKey(), ctx.master.getApprSeqNo(),
                    com.kosign.wecms.domain.payment.approvalbox.event.PaymentApprovalStatus.APPROVED));
        }

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

        eventPublisher.publishEvent(new com.kosign.wecms.domain.payment.approvalbox.event
                .PaymentApprovalStatusChangedEvent(ctx.master.getBizKey(), ctx.master.getApprSeqNo(),
                com.kosign.wecms.domain.payment.approvalbox.event.PaymentApprovalStatus.REJECTED));

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

        String comntTaskCd;
        if (pendingRemaining == 0 && waitingRemaining == 0) {
            ctx.master.setApprTypeCd(ApprTypeCd.APPROVED);
            // Legacy parity (comm_0192_010_u001:156-160): stamp PAY_END_*
            // once every approver has voted.
            ctx.master.setPayEndYn("Y");
            ctx.master.setPayEndDtm(nowDtm);
            ctx.master.setPayEndNm(TenantContext.currentUserNm());
            comntTaskCd = "R003"; // ComntTaskCd.COMPLETE
        } else {
            ctx.master.setApprTypeCd(ApprTypeCd.IN_PROGRESS);
            // Promote the next waiting approver (by line_no) to PENDING.
            promoteNextWaiting(ctx, nowDtm);
            comntTaskCd = "R004"; // ComntTaskCd.APPROVED
        }

        ctx.master.setModId(ctx.userId);
        ctx.master.setModDtm(nowDtm);
        masterRepository.save(ctx.master);

        // No write-back to the payment source table: neither SCDUL_PYMN_SUMR nor
        // OVRS_PYMN_SUMR has an APPR_STTS column in legacy JEX. The list/detail
        // pages derive status on read from EXPT_RESOL_MSTR.APPR_TYPE_CD.

        // Lifecycle parity: BLTB_COMNT_INFM remark + BLTB_NOTI_INFM/CONT to
        // admins + acted approvers (legacy createNotificationToApprvrAfterRequest).
        approvalNotificationService.notifyApprovalAction(
                ctx.master.getApprSeqNo(), ctx.master.getApprDv(), comntTaskCd);

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

        // Legacy parity (comm_0192_010_u002:129-133): PAY_END_* is stamped only
        // when every approver has now voted. Measured BEFORE the refactor's
        // W→S cascade (legacy has no cascade, so this matches its
        // totCnt == totApprCnt + totRjtCnt condition).
        long pendingRemaining = approverRepository.countByStatus(
                ctx.bizKey, ctx.master.getApprSeqNo(), ApprvrStts.PENDING);
        long waitingRemaining = approverRepository.countByStatus(
                ctx.bizKey, ctx.master.getApprSeqNo(), ApprvrStts.WAITING);

        // Downstream cascade: every waiting peer → stopped.
        approverRepository.stopDownstream(
                ctx.bizKey, ctx.master.getApprSeqNo(), ctx.userId, nowDtm);

        ctx.master.setApprTypeCd(ApprTypeCd.REJECTED);
        if (pendingRemaining == 0 && waitingRemaining == 0) {
            ctx.master.setPayEndYn("Y");
            ctx.master.setPayEndDtm(nowDtm);
            ctx.master.setPayEndNm(TenantContext.currentUserNm());
        }
        ctx.master.setModId(ctx.userId);
        ctx.master.setModDtm(nowDtm);
        masterRepository.save(ctx.master);

        // No write-back: status derives on read from EXPT_RESOL_MSTR.APPR_TYPE_CD
        // (legacy JEX has no APPR_STTS column on SCDUL_PYMN_SUMR / OVRS_PYMN_SUMR).

        // Lifecycle parity: BLTB_COMNT_INFM remark (REJECT) + BLTB_NOTI_INFM/CONT.
        approvalNotificationService.notifyApprovalAction(
                ctx.master.getApprSeqNo(), ctx.master.getApprDv(), "R002"); // ComntTaskCd.REJECT

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
        // Corporate Card (APPR_DV='33') amount basis — SUM(CARD_TRNS_HSTR.TRSC_AMT)
        // per approval. Legacy resl_0001_001 sources the card row amount from
        // this sum, NOT the EXPT_RESOL_MSTR amount columns (which are 0 for the
        // current card-creation flow). See toRow / resolveAmount.
        Map<String, BigDecimal> cardAmtByApprSeqNo = new HashMap<>();
        if (!apprSeqNos.isEmpty()) {
            ovrsPymnSumrRepository.findCcyCdByBizKeyAndApprSeqNos(bizKey, apprSeqNos)
                    .forEach(row -> ccyByApprSeqNo.put((String) row[0], (String) row[1]));
            // putIfAbsent keeps the OVRS value when both tables happen to
            // carry the same apprSeqNo (shouldn't occur, but defensive).
            scdulPymnSumrRepository.findCcyCdByBizKeyAndApprSeqNos(bizKey, apprSeqNos)
                    .forEach(row -> ccyByApprSeqNo.putIfAbsent((String) row[0], (String) row[1]));
            // Corporate Card (APPR_DV='33') has neither a SCDUL nor an OVRS
            // row — resolve its currency from CARD_TRNS_HSTR (mirrors legacy
            // EXPT_RESOL_MSTR_R008). putIfAbsent: card apprSeqNos never appear
            // in the SCDUL/OVRS maps, so this fills only the card gaps.
            cardTrnsHstrRepository.findCcyCdByBizKeyAndApprSeqNos(bizKey, apprSeqNos)
                    .forEach(row -> ccyByApprSeqNo.putIfAbsent((String) row[0], (String) row[1]));

            // Card amount basis (APPR_DV='33') — keyed by apprSeqNo; non-card
            // approvals never appear in CARD_TRNS_HSTR so the map only carries
            // card sums (resolveAmount reads it only for APPR_DV='33').
            cardTrnsHstrRepository.sumTrscAmtByBizKeyAndApprSeqNos(bizKey, apprSeqNos)
                    .forEach(row -> cardAmtByApprSeqNo.put((String) row[0], toBigDecimal(row[1])));

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
                .map(p -> toRow(bizKey, p, ccyByApprSeqNo, fileNameByApprSeqNo, cardAmtByApprSeqNo))
                .toList();
    }

    private ApprovalBoxRow toRow(String bizKey, ApprovalBoxProjection p,
                                  Map<String, String> ccyByApprSeqNo,
                                  Map<String, String> fileNameByApprSeqNo,
                                  Map<String, BigDecimal> cardAmtByApprSeqNo) {
        List<ExptResolApprvrInfm> chain = approverRepository.findChain(bizKey, p.apprSeqNo());
        Map<String, String> imgUrlByApprvrId = resolveImageUrls(
                bizKey, chain.stream().map(ExptResolApprvrInfm::getApprvrId).toList());
        List<ApprovalBoxApprover> approvers = chain.stream()
                .sorted(Comparator.comparing(
                        a -> a.getApprvrLineNo() == null ? BigDecimal.ZERO : a.getApprvrLineNo()))
                .map(a -> toApprover(a, imgUrlByApprvrId))
                .toList();

        String currency = resolveCurrency(ccyByApprSeqNo, p.apprSeqNo());
        BigDecimal amt = resolveAmount(
                p.apprDv(), p.apprSeqNo(), currency,
                p.totPayKhrAmt(), p.totPayUsdAmt(), p.totPayVndAmt(),
                cardAmtByApprSeqNo);

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
                approvers,
                // List rows carry no detail-only fields — withdrawal account
                // (4) + payroll fund type are populated only on the single-row
                // detail read (ApprovalDetailService.master → withDetail;
                // findOne → withApprFundTp).
                null, null, null, null, null);
    }

    private static ApprovalBoxApprover toApprover(
            ExptResolApprvrInfm a, Map<String, String> imgUrlByApprvrId) {
        return new ApprovalBoxApprover(
                a.getApprvrLineNo() == null ? null : a.getApprvrLineNo().intValue(),
                a.getApprvrId(),
                a.getApprvrNm(),
                a.getApprvrStts(),
                a.getApprDtm(),
                imgUrlByApprvrId.get(a.getApprvrId()));
    }

    /**
     * Resolve {@code user_img_url} for every approver id with a single
     * batched {@code findAllByUserIdInAndBizKeyAndDelYn}. Returns a map
     * keyed by {@code apprvrId}; an approver with no stored image is
     * absent from the map (→ {@code toApprover} ships a null URL and the
     * frontend renders the initials disc). Mirrors
     * {@code ApprovalLineService.resolveImageUrls} so both approver
     * surfaces emit the identical shared {@code /api/v1/users/{id}/image}
     * URL shape.
     */
    private Map<String, String> resolveImageUrls(String bizKey, List<String> apprvrIds) {
        if (apprvrIds == null || apprvrIds.isEmpty()) return Map.of();
        Set<String> distinct = new HashSet<>(apprvrIds);
        List<UserBusiConnInfm> links = userBusiRepo
                .findAllByUserIdInAndBizKeyAndDelYn(distinct, bizKey, DEL_YN_ACTIVE);
        Map<String, String> out = new HashMap<>(links.size());
        for (UserBusiConnInfm link : links) {
            String image = link.getUserImg();
            if (image != null && !image.isBlank()) {
                out.put(link.getUserId(), String.format(USER_IMAGE_URI_TEMPLATE, link.getUserId()));
            }
        }
        return out;
    }

    /* =================================================================
     *  Currency + amount resolution — shared by per-row mapping
     *  ({@link #toRow}) and the footer totals ({@link #computeFooterTotals})
     *  so the two never drift.
     * ================================================================= */

    /** Resolve the display currency for an approval: prefer the sibling
     *  table's {@code CCY_CD} (OVRS / SCDUL / CARD), default {@code "VND"}
     *  when none is known or it is blank. */
    private static String resolveCurrency(Map<String, String> ccyByApprSeqNo, String apprSeqNo) {
        String currency = ccyByApprSeqNo.getOrDefault(apprSeqNo, "VND");
        return (currency == null || currency.isBlank()) ? "VND" : currency;
    }

    /**
     * Resolve the row amount, matching legacy {@code resl_0001_001}.
     *
     * <ul>
     *   <li><b>Card (APPR_DV='33')</b> → {@code SUM(CARD_TRNS_HSTR.TRSC_AMT)}
     *       from {@code cardAmtByApprSeqNo} (legacy IDO R003:62-65); falls
     *       back to {@code TOT_PAY_KHR_AMT} only when no card rows exist.</li>
     *   <li><b>Other types</b> → the currency-matched master column with
     *       {@code TOT_PAY_KHR_AMT} as the zero-aware fallback (legacy rows
     *       wrote the amount to KHR regardless of currency; the per-currency
     *       columns are 0 there).</li>
     * </ul>
     */
    private static BigDecimal resolveAmount(
            String apprDv, String apprSeqNo, String currency,
            BigDecimal khrAmt, BigDecimal usdAmt, BigDecimal vndAmt,
            Map<String, BigDecimal> cardAmtByApprSeqNo) {
        BigDecimal khr = khrAmt != null ? khrAmt : BigDecimal.ZERO;
        if (CARD_APPR_DV.equals(apprDv)) {
            BigDecimal cardSum = cardAmtByApprSeqNo.get(apprSeqNo);
            return cardSum != null ? cardSum : khr;
        }
        if ("USD".equalsIgnoreCase(currency)) {
            return (usdAmt != null && usdAmt.compareTo(BigDecimal.ZERO) != 0) ? usdAmt : khr;
        }
        if ("VND".equalsIgnoreCase(currency)) {
            return (vndAmt != null && vndAmt.compareTo(BigDecimal.ZERO) != 0) ? vndAmt : khr;
        }
        return khr;
    }

    /** Null-safe numeric coercion for {@code Object[]} JPQL tuple columns
     *  (a {@code SUM(...)} / {@code COALESCE(...)} may arrive as
     *  {@code BigDecimal}, {@code Long}, or another {@link Number}). */
    private static BigDecimal toBigDecimal(Object value) {
        if (value == null) return BigDecimal.ZERO;
        if (value instanceof BigDecimal b) return b;
        if (value instanceof Number n) return new BigDecimal(n.toString());
        return new BigDecimal(value.toString());
    }

    /**
     * Footer totals — document count + per-currency amount sums over the
     * tab-unfiltered result set (legacy {@code resl_0001_001} footer bar,
     * {@code IDO.EXPT_RESOL_MSTR_R003_CNT.xml:36-39}). The document count
     * is supplied by the caller (sum of the four {@code apprTypeCd} tab
     * counts — {@code apprTypeCd} is NOT NULL ∈ {00,01,02,03}, so it equals
     * the tab-unfiltered row count without a second count query). Per-row
     * currency + amount resolution reuses {@link #resolveCurrency} /
     * {@link #resolveAmount} so the footer matches the on-screen rows
     * exactly (including the card-amount rule). Only USD / VND / KRW are
     * surfaced — the three buckets the footer renders.
     */
    private ApprovalBoxFooterTotals computeFooterTotals(
            String bizKey, String userId, boolean isAdmin,
            String fromDt, String toDt, String keywordPattern,
            long totalDocumentCount) {

        List<Object[]> basis = masterRepository.findAmountBasisForTotals(
                bizKey, userId, isAdmin, fromDt, toDt, keywordPattern, keywordPattern);
        if (basis.isEmpty()) {
            return new ApprovalBoxFooterTotals(
                    totalDocumentCount, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        }

        List<String> apprSeqNos = basis.stream()
                .map(r -> (String) r[0])
                .filter(s -> s != null && !s.isBlank())
                .distinct()
                .collect(Collectors.toList());

        Map<String, String> ccyByApprSeqNo = new HashMap<>();
        Map<String, BigDecimal> cardAmtByApprSeqNo = new HashMap<>();
        if (!apprSeqNos.isEmpty()) {
            ovrsPymnSumrRepository.findCcyCdByBizKeyAndApprSeqNos(bizKey, apprSeqNos)
                    .forEach(row -> ccyByApprSeqNo.put((String) row[0], (String) row[1]));
            scdulPymnSumrRepository.findCcyCdByBizKeyAndApprSeqNos(bizKey, apprSeqNos)
                    .forEach(row -> ccyByApprSeqNo.putIfAbsent((String) row[0], (String) row[1]));
            cardTrnsHstrRepository.findCcyCdByBizKeyAndApprSeqNos(bizKey, apprSeqNos)
                    .forEach(row -> ccyByApprSeqNo.putIfAbsent((String) row[0], (String) row[1]));
            cardTrnsHstrRepository.sumTrscAmtByBizKeyAndApprSeqNos(bizKey, apprSeqNos)
                    .forEach(row -> cardAmtByApprSeqNo.put((String) row[0], toBigDecimal(row[1])));
        }

        BigDecimal usd = BigDecimal.ZERO;
        BigDecimal vnd = BigDecimal.ZERO;
        BigDecimal krw = BigDecimal.ZERO;
        for (Object[] r : basis) {
            String apprSeqNo = (String) r[0];
            String apprDv    = (String) r[1];
            String currency  = resolveCurrency(ccyByApprSeqNo, apprSeqNo);
            BigDecimal amt   = resolveAmount(
                    apprDv, apprSeqNo, currency,
                    toBigDecimal(r[2]), toBigDecimal(r[3]), toBigDecimal(r[4]),
                    cardAmtByApprSeqNo);
            switch (currency.toUpperCase(Locale.ROOT)) {
                case "USD" -> usd = usd.add(amt);
                case "VND" -> vnd = vnd.add(amt);
                case "KRW" -> krw = krw.add(amt);
                default    -> { /* other currencies not in the 3-bucket footer (legacy parity) */ }
            }
        }
        return new ApprovalBoxFooterTotals(totalDocumentCount, usd, vnd, krw);
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
