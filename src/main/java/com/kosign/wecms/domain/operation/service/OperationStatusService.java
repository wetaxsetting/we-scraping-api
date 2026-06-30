package com.kosign.wecms.domain.operation.service;

import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.common.web.SortDirection;
import com.kosign.wecms.domain.banking.nxisas.audit.NxsiasModuleTranLogHisRepository;
import com.kosign.wecms.domain.banking.nxisas.audit.entity.NxsiasModuleTranLogHis;
import com.kosign.wecms.domain.banking.synchistory.entity.ExtTranCallHis;
import com.kosign.wecms.domain.banking.synchistory.repository.ExtTranCallHisRepository;
import com.kosign.wecms.domain.operation.client.OsmcGdtClient;
import com.kosign.wecms.domain.operation.client.OsmcGdtClient.OsmcGdtRegistration;
import com.kosign.wecms.domain.operation.client.OsmcGdtClient.OsmcGdtResult;
import com.kosign.wecms.domain.operation.client.OsmcGdtClient.OsmcGdtSearchOutcome;
import com.kosign.wecms.domain.operation.dto.CreateOperationStatusRequest;
import com.kosign.wecms.domain.operation.dto.InquireOperationStatusResponse;
import com.kosign.wecms.domain.operation.dto.OperationStatusListResponse;
import com.kosign.wecms.domain.operation.dto.OperationStatusRow;
import com.kosign.wecms.domain.operation.dto.UpdateOperationStatusResponse;
import com.kosign.wecms.domain.operation.entity.OprtSttsInfo;
import com.kosign.wecms.domain.operation.entity.OprtSttsInfoId;
import com.kosign.wecms.domain.operation.repository.OprtSttsInfoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Add-on &gt; Operation Status Inquiry service. Reads
 * {@code OPRT_STTS_INFO} (legacy {@code aosv_0001_001_r001}) — every
 * row is a tax-authority operating-status snapshot for one counterparty
 * (per (BIZ_KEY, TAXCODE) pair).
 *
 * <p>The live OSMC → GDT bridge IS wired: {@link #inquire} (single
 * lookup, legacy {@code aosv_0001_002_r001}) and {@link #bulkUpdate}
 * (re-poll + upsert, legacy {@code aosv_0001_001_u001}) both call the
 * real {@link OsmcGdtClient} and write {@code EXT_TRAN_CALL_HIS} +
 * {@code NXSIAS_MODULE_TRAN_LOG_HIS} audit rows. {@link #create}
 * persists a freshly-looked-up registration (legacy
 * {@code aosv_0001_002_c001}, create-only with a duplicate guard).
 * {@link #list} reads the cached {@code OPRT_STTS_INFO} snapshot. The
 * gateway host is environment-config-driven ({@code wecms.osmc.*}); a
 * non-PROD run must override it off the live Coocon default.
 */
@Service
@RequiredArgsConstructor
public class OperationStatusService {

    /** Largest {@code size} the controller will honour. Matches the
     *  frontend {@code PAGE_SIZE_OPTIONS} maximum ({@code 100}). */
    public static final int MAX_PAGE_SIZE = 100;

    /** Default page size when the caller omits {@code size}. Matches
     *  {@code PAGE_SIZE_OPTIONS[0] = 10}. */
    public static final int DEFAULT_PAGE_SIZE = 10;

    private final OprtSttsInfoRepository repo;
    private final ExtTranCallHisRepository extTranCallHisRepo;
    private final NxsiasModuleTranLogHisRepository nxisasLogRepo;
    private final OsmcGdtClient osmcClient;
    private final com.kosign.wecms.common.legacy.LegacyFunctionRepository legacyFunctions;

    /** Legacy {@code aosv_0001_002_r001} sentinels — pinned verbatim so
     *  the audit rows the refactor writes line up with the live JEX
     *  data when the two systems coexist on a shared DB. */
    private static final String EXT_SVC_ID         = "BI09";
    private static final String EXT_WEB_SVC_ID_R   = "aosv_0001_001_r001";
    private static final String NXISAS_CLASS       = "COMMON";
    private static final String NXISAS_MODULE      = "GLOBAL/VNM/PUBLIC_MASOTHUE";
    private static final String NXISAS_JOB         = "BUSSINESS_REGISTRATION_LIST";

    private static final DateTimeFormatter REF_DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter DTM_14_FMT   = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /**
     * Default ORDER BY mirrors the legacy listing's practical order —
     * latest poll first, with TAXCODE as the deterministic tiebreaker
     * so pagination stays stable across rows that share a poll
     * timestamp. Legacy uses {@code SortHelper.getSortDynamic(...)}
     * which is View-Setting-driven; in real DEV data the seeded
     * View Setting resolves to {@code SEARCHDATETIME DESC}.
     */
    private static final Sort DEFAULT_SORT =
            Sort.by(Sort.Order.desc("searchdatetime"),
                    Sort.Order.asc("taxcode"));

    /* ------------------------------------------------------------------
     *  Public list overloads
     * ------------------------------------------------------------------ */

    /**
     * Filtered + paginated list. Builds the {@link Pageable} from caller
     * primitives. {@code page} is zero-based; {@code size} is clamped to
     * {@link #MAX_PAGE_SIZE} and defaults to {@link #DEFAULT_PAGE_SIZE}
     * when {@code size <= 0}.
     */
    @Transactional(readOnly = true)
    public OperationStatusListResponse list(
            OperationStatusFilter filter,
            String sortField,
            String sortDirection,
            int page,
            int size) {
        int safeSize = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        int safePage = Math.max(0, page);
        Sort sort = buildSort(OperationStatusSortField.parse(sortField),
                              SortDirection.parse(sortDirection));
        Pageable pageable = PageRequest.of(safePage, safeSize, sort);
        return list(filter, sortField, sortDirection, pageable);
    }

    /**
     * Core list method — every other overload funnels here. Pass
     * {@link Pageable#unpaged()} for the export path and any caller that
     * needs the full filtered set.
     */
    @Transactional(readOnly = true)
    public OperationStatusListResponse list(
            OperationStatusFilter filter,
            String sortField,
            String sortDirection,
            Pageable pageable) {
        final String bizKey = TenantContext.currentBizKey();
        final OperationStatusFilter effective = filter == null ? OperationStatusFilter.empty() : filter;

        // Apply DEFAULT_SORT only when the caller's Pageable carries no
        // sort of its own (export path passes Sort.unsorted()). When the
        // caller already built a Sort (controller's PageRequest above
        // already wove DEFAULT_SORT in via buildSort), we must not double
        // it.
        Pageable effectivePageable = pageable.getSort().isSorted()
                ? pageable
                : applyDefaultSortIfNeeded(pageable, sortField, sortDirection);

        String keywordPattern       = buildLikePattern(effective.keyword());
        String companyStatusPattern = buildLikePattern(effective.companyStatus());

        Page<OprtSttsInfo> p = repo.findFiltered(
                bizKey,
                companyStatusPattern,
                keywordPattern,
                effectivePageable);

        List<OperationStatusRow> rows = p.getContent().stream().map(this::toRow).toList();

        // Page.getSize() and getTotalPages() handle the unpaged case:
        // Spring Data's Chunk#getSize() returns content.size() when the
        // underlying Pageable is unpaged, and PageImpl#getTotalPages()
        // collapses to 1.
        return new OperationStatusListResponse(
                rows,
                p.getTotalElements(),
                p.getNumber(),
                p.getSize(),
                p.getTotalPages()
        );
    }

    /* ------------------------------------------------------------------
     *  Filter / sort plumbing
     * ------------------------------------------------------------------ */

    /**
     * Lower-case + escape LIKE meta-chars + wrap with {@code %…%}.
     * Uses {@code !} as the escape character so the JPQL pattern stays
     * portable — pairs with the {@code ESCAPE '!'} clause on every
     * predicate in {@link OprtSttsInfoRepository#findFiltered}.
     *
     * <p>Returns {@code null} when the value is null/blank so the JPQL
     * guard skips the predicate entirely.
     */
    private static String buildLikePattern(String raw) {
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

    /**
     * Build a Spring Data {@link Sort} from the validated whitelist enum
     * + direction. The user's primary sort is always followed by the
     * default order so equal-keyed rows keep a deterministic order
     * across requests.
     */
    private static Sort buildSort(OperationStatusSortField field, SortDirection direction) {
        if (field == null || direction == null) {
            return DEFAULT_SORT;
        }
        Sort.Order primary = direction == SortDirection.ASC
                ? Sort.Order.asc(field.entityProperty())
                : Sort.Order.desc(field.entityProperty());
        return Sort.by(primary).and(DEFAULT_SORT);
    }

    /** When a caller passes a {@link Pageable} with no sort, weave the
     *  user-driven sort (or DEFAULT_SORT) in. Used by the export path
     *  which builds {@code Pageable.unpaged()} without a Sort. */
    private static Pageable applyDefaultSortIfNeeded(
            Pageable pageable, String sortField, String sortDirection) {
        Sort sort = buildSort(OperationStatusSortField.parse(sortField),
                              SortDirection.parse(sortDirection));
        if (pageable.isUnpaged()) {
            return Pageable.unpaged(sort);
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
    }

    /* ------------------------------------------------------------------
     *  Row mapping
     * ------------------------------------------------------------------ */

    private OperationStatusRow toRow(OprtSttsInfo r) {
        return new OperationStatusRow(
                r.getTaxcode(),
                r.getCompanyname(),
                r.getCompanystatus(),
                resolveStatusCd(r.getCompanystatus()),
                r.getOfficeadd(),
                r.getSearchdatetime()
        );
    }

    /**
     * Resolve the GDT free-text Vietnamese {@code COMPANYSTATUS} into a
     * stable status code, reproducing the legacy {@code aosv_0001_001.ts}
     * {@code companyStatus} map 1:1 (legacy
     * {@code WECMS_STATIC/ts-src/js/jex/wabooks/aosv/aosv_0001_001.ts:58-72}).
     *
     * <p>The legacy column renders one of <b>13 distinct tax-authority
     * statuses</b> (each keyed on the <i>exact</i> Vietnamese phrase) plus
     * an unknown fallback that shows the raw {@code COMPANYSTATUS} string
     * verbatim. The earlier compact 3-state model
     * ({@code active_certificate_issued / suspended / not_registered}) was
     * a refactor invention that collapsed e.g. "tạm nghỉ" (Temporarily
     * closed) into "suspended" — removed 2026-06-30 for legacy parity.
     * See {@code wecms-api/docs/audits/operation-status-inquiry-status-column-legacy-audit.md}.
     *
     * <p>Matching is <b>exact</b> (after trim), mirroring the legacy
     * object-key lookup — not a substring scan. Anything unmatched →
     * {@code "unknown"}, and the frontend renders the raw GDT string with
     * no icon (legacy {@code status: "", msg: v.COMPANYSTATUS}). Each
     * code's green/active vs red/inactive presentation lives on the
     * frontend ({@code OperationStatusCell}), mirroring the legacy
     * {@code status: "active" | "Inactive"} flag.
     */
    static String resolveStatusCd(String status) {
        if (status == null) return "unknown";
        return LEGACY_STATUS_CODES.getOrDefault(status.trim(), "unknown");
    }

    /**
     * Exact Vietnamese {@code COMPANYSTATUS} → stable code, copied verbatim
     * from the legacy {@code aosv_0001_001.companyStatus} object keys. The
     * three "đang hoạt động*" phrases are the legacy {@code status:"active"}
     * set; the remaining ten are {@code status:"Inactive"}.
     */
    private static final Map<String, String> LEGACY_STATUS_CODES = Map.ofEntries(
            Map.entry("NNT đang hoạt động",                                                  "active"),
            Map.entry("NNT đang hoạt động (đã được cấp GCN ĐKT)",                            "active_certificate_issued"),
            Map.entry("NNT đang hoạt động (được cấp thông báo MST)",                         "active_tax_notice_issued"),
            Map.entry("NNT không hoạt động tại địa chỉ đã đăng ký",                          "address_mismatch"),
            Map.entry("NNT chờ làm thủ tục phá sản",                                         "bankruptcy_in_progress"),
            Map.entry("NNT ngừng hoạt động và đã đóng MST",                                  "closed"),
            Map.entry("NNT ngừng hoạt động và đã hoàn thành thủ tục chấm dứt hiệu lực MST",  "mst_terminated"),
            Map.entry("NNT ngừng hoạt động nhưng chưa hoàn thành thủ tục đóng MST",          "on_closing"),
            Map.entry("NNT chờ xác minh tình trạng hoạt động tại địa chỉ đã đăng ký",        "pending_address_verification"),
            Map.entry("Mã số thuế chờ cập nhật thông tin số định danh cá nhân",              "pending_personal_id_update"),
            Map.entry("NNT đã được cấp MST",                                                 "tax_code_issued"),
            Map.entry("NNT đã chuyển cơ quan thuế quản lý",                                  "tax_office_changed"),
            Map.entry("NNT tạm nghỉ kinh doanh có thời hạn",                                 "temporarily_closed")
    );

    /* ------------------------------------------------------------------
     *  Inquire — single Tax ID lookup (Get Business Info popup)
     * ------------------------------------------------------------------ */

    /**
     * "Get Business Info" — single Tax ID lookup against the GDT bridge.
     * Mirrors legacy {@code aosv_0001_002_r001_act.jsp}:
     * <ol>
     *   <li>Allocate next {@code SEQ_NO} + insert {@code EXT_TRAN_CALL_HIS}
     *       row (pre-call audit).</li>
     *   <li>Call {@code TRCO_TRCO001_00003} via the OSMC bridge.</li>
     *   <li>Patch the audit row with the bridge's error code / message.</li>
     *   <li>Write a {@code NXSIAS_MODULE_TRAN_LOG_HIS} row (NXISAS audit).</li>
     *   <li>Return the registration detail matching the requested tax code.</li>
     * </ol>
     * On bridge failure or empty {@code registrationDetail}, throws
     * {@code BusinessException("WBE_OSMC_LOOKUP_FAILED")} so the frontend
     * surfaces the orange warning + "Input tax ID to get customer info"
     * empty-state.
     */
    @Transactional
    public InquireOperationStatusResponse inquire(String taxCode) {
        final String bizKey = TenantContext.currentBizKey();
        final String userId = TenantContext.currentUserId();
        final LocalDate today = LocalDate.now();
        final String refDate = today.format(REF_DATE_FMT);

        // 1. Pre-call audit row in EXT_TRAN_CALL_HIS.
        BigDecimal seqNo = new BigDecimal(
                legacyFunctions.nextExtTranCallHisSeqNo(EXT_SVC_ID, refDate));
        ExtTranCallHis audit = new ExtTranCallHis();
        audit.setSvcId(EXT_SVC_ID);
        audit.setRefDate(refDate);
        audit.setSeqNo(seqNo);
        audit.setWebSvcId(EXT_WEB_SVC_ID_R);
        audit.setBizKey(bizKey);
        audit.setUserId(userId);
        audit.setTaxcode(taxCode);
        audit.setRegDtm(LocalDateTime.now(ZoneOffset.UTC).format(DTM_14_FMT));
        extTranCallHisRepo.save(audit);

        // 2. Call the OSMC bridge.
        String uniqueKey = generateUniqueKey();
        OsmcGdtResult result = osmcClient.lookup(List.of(taxCode), uniqueKey);

        // 3. Patch the EXT_TRAN_CALL_HIS row with the bridge outcome.
        audit.setRsltCd(result.errorCode());
        audit.setErrCont(truncate(result.errorMessage(), 1000));
        extTranCallHisRepo.save(audit);

        // 4. NXISAS audit row.
        writeNxisasAudit(bizKey, userId, taxCode, result.errorCode(),
                         result.errorMessage());

        // 5. Surface the matching registration row.
        if (!result.isSuccess()) {
            throw new BusinessException("WBE_OSMC_LOOKUP_FAILED",
                    result.errorMessage() == null ? "" : result.errorMessage());
        }
        OsmcGdtRegistration hit = result.rows().stream()
                .filter(r -> taxCode.equals(r.taxCode()))
                .findFirst()
                .orElse(null);
        if (hit == null) {
            // Legacy parity — WBE1152 surfaced on empty registrationDetail.
            throw new BusinessException("WBE_OSMC_NO_RESULT");
        }
        return new InquireOperationStatusResponse(
                hit.taxCode(),
                hit.companyName(),
                hit.companyStatus(),
                resolveStatusCd(hit.companyStatus()),
                hit.officeAdd(),
                hit.representativeFirstName(),
                hit.searchDatetime()
        );
    }

    /* ------------------------------------------------------------------
     *  Create — persist the inquired row (popup Save)
     * ------------------------------------------------------------------ */

    /**
     * Inquire-popup "Save" — persists a GDT-looked-up registration into
     * {@code OPRT_STTS_INFO}. Mirrors legacy
     * {@code aosv_0001_002_c001_act.jsp}:
     * <ol>
     *   <li>Required-field validation (handled by bean validation on
     *       {@link CreateOperationStatusRequest}).</li>
     *   <li>Duplicate guard on {@code (BIZ_KEY, TAXCODE)} —
     *       {@code existsById} → {@code WBE_OPERATION_STATUS_DUPLICATE}
     *       (legacy {@code WBE0003}). Create-only, never an upsert (the
     *       bulk Update path owns the refresh/upsert flow).</li>
     *   <li>Insert with {@code REG_ID}/{@code REG_DTM} stamped from the
     *       JWT-bound user.</li>
     * </ol>
     *
     * <p>Tenant is read from {@link TenantContext}; the wire never carries
     * {@code BIZ_KEY}. A concurrent insert of the same key surfaces as
     * {@link DataIntegrityViolationException} and is mapped to the same
     * duplicate error so the caller sees a friendly message, not a 500.
     *
     * @return the persisted row in the same wire shape the list endpoint
     *         emits, so the popup can optimistically reflect it.
     */
    @Transactional
    public OperationStatusRow create(CreateOperationStatusRequest req) {
        final String bizKey = TenantContext.currentBizKey();
        final String userId = TenantContext.currentUserId();

        OprtSttsInfoId pk = new OprtSttsInfoId(bizKey, req.taxCode());
        if (repo.existsById(pk)) {
            throw new BusinessException("WBE_OPERATION_STATUS_DUPLICATE", req.taxCode());
        }

        OprtSttsInfo row = new OprtSttsInfo();
        row.setBizKey(bizKey);
        row.setTaxcode(req.taxCode());
        row.setCompanyname(req.companyNm());
        row.setCompanystatus(req.companyStatus());
        row.setOfficeadd(req.officeAdd());
        row.setSearchdatetime(req.searchDatetime());
        // GDT may return a blank representative; the column is NOT NULL, so
        // coalesce null → "" (same handling as bulkUpdate's upsert path).
        row.setRepresentativefirstname(
                req.representativeFirstName() == null ? "" : req.representativeFirstName());
        stampAudit(row, userId);

        try {
            repo.save(row);
        } catch (DataIntegrityViolationException e) {
            // Lost the race against a concurrent Save of the same
            // (BIZ_KEY, TAXCODE) — surface the duplicate, not a 500.
            throw new BusinessException("WBE_OPERATION_STATUS_DUPLICATE", req.taxCode());
        }
        return toRow(row);
    }

    /* ------------------------------------------------------------------
     *  Bulk update — Re-poll every cached row from the GDT
     * ------------------------------------------------------------------ */

    /**
     * Toolbar "Update" — re-polls {@code OPRT_STTS_INFO} rows from the
     * GDT and upserts fresh values. Mirrors legacy
     * {@code aosv_0001_001_u001_act.jsp} but the input list comes from
     * the local cache (legacy reads the same list off the request body).
     * Per-row errors do not abort the batch — the count is returned for
     * UI feedback.
     *
     * @param selectedTaxCodes when {@code null} or empty, every cached
     *        row in the tenant is re-polled. When non-empty, only those
     *        tax codes that exist in the local cache for the tenant are
     *        re-polled — silently dropping any unknown codes so a stale
     *        frontend selection can't cross the tenant boundary.
     */
    @Transactional
    public UpdateOperationStatusResponse bulkUpdate(List<String> selectedTaxCodes) {
        final String bizKey = TenantContext.currentBizKey();
        final String userId = TenantContext.currentUserId();
        final LocalDate today = LocalDate.now();
        final String refDate = today.format(REF_DATE_FMT);

        List<OprtSttsInfo> cached = repo.findAllByBizKey(bizKey);
        if (cached.isEmpty()) {
            return new UpdateOperationStatusResponse(0, 0, 0);
        }

        // Resolve the working set:
        //   - null / empty selection -> every cached row.
        //   - non-empty selection    -> intersection with cached (drops
        //                               unknown / cross-tenant codes
        //                               silently).
        final Set<String> selectionSet =
                (selectedTaxCodes == null || selectedTaxCodes.isEmpty())
                        ? null
                        : Set.copyOf(selectedTaxCodes);
        List<String> taxCodes = cached.stream()
                .map(OprtSttsInfo::getTaxcode)
                .filter(tc -> selectionSet == null || selectionSet.contains(tc))
                .toList();
        if (taxCodes.isEmpty()) {
            return new UpdateOperationStatusResponse(0, 0, 0);
        }

        // 1. EXT_TRAN_CALL_HIS pre-call row (batch — single SEQ_NO covers
        //    the whole multi-tax-code request).
        BigDecimal seqNo = new BigDecimal(
                legacyFunctions.nextExtTranCallHisSeqNo(EXT_SVC_ID, refDate));
        ExtTranCallHis audit = new ExtTranCallHis();
        audit.setSvcId(EXT_SVC_ID);
        audit.setRefDate(refDate);
        audit.setSeqNo(seqNo);
        audit.setWebSvcId(EXT_WEB_SVC_ID_R);
        audit.setBizKey(bizKey);
        audit.setUserId(userId);
        audit.setRegDtm(LocalDateTime.now(ZoneOffset.UTC).format(DTM_14_FMT));
        extTranCallHisRepo.save(audit);

        // 2. OSMC bridge call.
        String uniqueKey = generateUniqueKey();
        OsmcGdtResult result = osmcClient.lookup(taxCodes, uniqueKey);

        audit.setRsltCd(result.errorCode());
        audit.setErrCont(truncate(result.errorMessage(), 1000));
        extTranCallHisRepo.save(audit);

        if (!result.isSuccess()) {
            // Whole-batch failure — write one NXISAS audit row and
            // surface to the caller. Local cache stays intact.
            writeNxisasAudit(bizKey, userId, null, result.errorCode(),
                             result.errorMessage());
            throw new BusinessException("WBE_OSMC_LOOKUP_FAILED",
                    result.errorMessage() == null ? "" : result.errorMessage());
        }

        // 3. Index returned details by tax code so we can resolve per
        //    cached row deterministically. Defensive: GDT can echo
        //    multiple details for one search but only the first match
        //    on TAXCODE is upserted (mirrors aosv_0001_002_r001_act.jsp
        //    behavior — see line 126 of legacy).
        Map<String, OsmcGdtRegistration> detailByTaxCode = new HashMap<>();
        for (OsmcGdtRegistration r : result.rows()) {
            detailByTaxCode.putIfAbsent(r.taxCode(), r);
        }

        // Optional per-search outcome map (only populated when the
        // gateway returned the RespData[] wrapper — flat-shape callers
        // skip this branch).
        Map<String, OsmcGdtSearchOutcome> outcomeByTaxCode = new HashMap<>();
        for (OsmcGdtSearchOutcome o : result.outcomes()) {
            outcomeByTaxCode.putIfAbsent(o.taxCode(), o);
        }

        // 4. Walk the cached list in request order so the counter is
        //    derived per-tax-code (not by delta-math). Each cached row
        //    either upserts (success) or writes a failure audit row.
        int updated = 0;
        int failed  = 0;
        for (String taxCode : taxCodes) {
            OsmcGdtRegistration r = detailByTaxCode.get(taxCode);
            if (r != null) {
                OprtSttsInfoId pk = new OprtSttsInfoId(bizKey, r.taxCode());
                OprtSttsInfo row = repo.findById(pk).orElseGet(OprtSttsInfo::new);
                row.setBizKey(bizKey);
                row.setTaxcode(r.taxCode());
                row.setCompanyname(r.companyName());
                row.setCompanystatus(r.companyStatus());
                row.setOfficeadd(r.officeAdd());
                row.setRepresentativefirstname(
                        r.representativeFirstName() == null ? "" : r.representativeFirstName());
                row.setSearchdatetime(r.searchDatetime());
                stampAudit(row, userId);
                repo.save(row);
                writeNxisasAudit(bizKey, userId, r.taxCode(), "00000000", null);
                updated++;
            } else {
                // No detail returned for this tax code — either GDT
                // didn't find it or per-search ErrorCode was non-zero.
                // Write the audit row (legacy `_u001_act.jsp:206-221`)
                // and count as failed; do NOT touch the cached row.
                OsmcGdtSearchOutcome o = outcomeByTaxCode.get(taxCode);
                String code = o == null ? "WBE_OSMC_NO_RESULT" : o.errorCode();
                String msg  = o == null ? "No registration detail returned for tax code"
                                        : o.errorMessage();
                writeNxisasAudit(bizKey, userId, taxCode, code, msg);
                failed++;
            }
        }
        return new UpdateOperationStatusResponse(taxCodes.size(), updated, failed);
    }

    /* ------------------------------------------------------------------
     *  Helpers
     * ------------------------------------------------------------------ */

    private void writeNxisasAudit(String bizKey, String userId, String taxCode,
                                  String errorCode, String errorMessage) {
        NxsiasModuleTranLogHis log = new NxsiasModuleTranLogHis();
        log.setUuid(UUID.randomUUID().toString().replace("-", "").substring(0, 30));
        log.setRegDtm(LocalDateTime.now(ZoneOffset.UTC).format(DTM_14_FMT));
        log.setBizKey(bizKey);
        log.setNxisasClass(NXISAS_CLASS);
        log.setNxisasModule(NXISAS_MODULE);
        log.setNxisasJob(NXISAS_JOB);
        log.setNxisasErrCode(errorCode == null ? "" : errorCode);
        log.setNxisasErrCont(errorMessage);
        log.setRegId(userId);
        if (taxCode != null) {
            log.setReqData("{\"search\":\"" + taxCode.replace("\"", "\\\"") + "\"}");
        }
        nxisasLogRepo.save(log);
    }


    private void stampAudit(OprtSttsInfo row, String userId) {
        String now = LocalDateTime.now(ZoneOffset.UTC).format(DTM_14_FMT);
        String safeUser = userId == null ? "system" : userId;
        if (row.getRegId() == null || row.getRegId().isBlank()) {
            row.setRegId(safeUser);
            row.setRegDtm(now);
        }
        row.setModId(safeUser);
        row.setModDtm(now);
    }

    /** 7-character mixed-case alphanumeric correlation token sent as the
     *  bridge's {@code UniqueKey} field. Matches the legacy bigdata
     *  contract's {@code @Size(max = 7)} cap on {@code ApiIn.uniqueKey}
     *  and the example shape ({@code rb3Xy43}). Fresh per call —
     *  {@code SecureRandom} keeps it non-guessable so a leaked id can't
     *  be replayed by a third party. */
    private static String generateUniqueKey() {
        char[] out = new char[7];
        for (int i = 0; i < out.length; i++) {
            out[i] = UNIQUE_KEY_ALPHABET.charAt(RNG.nextInt(UNIQUE_KEY_ALPHABET.length()));
        }
        return new String(out);
    }

    private static final SecureRandom RNG = new SecureRandom();
    private static final String UNIQUE_KEY_ALPHABET =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
