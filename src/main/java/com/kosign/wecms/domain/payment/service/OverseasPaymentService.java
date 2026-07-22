package com.kosign.wecms.domain.payment.service;

import com.kosign.wecms.common.auditlog.service.AuditLogContext;
import com.kosign.wecms.common.auditlog.service.AuditLogService;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.payment.approvalbox.entity.ExptResolApprvrInfm;
import com.kosign.wecms.domain.payment.approvalbox.entity.ExptResolMstr;
import com.kosign.wecms.domain.payment.approvalbox.repository.ExptResolApprvrInfmRepository;
import com.kosign.wecms.domain.payment.approvalbox.repository.ExptResolMstrRepository;
import com.kosign.wecms.domain.payment.approvalbox.service.ApprDv;
import com.kosign.wecms.domain.payment.internaltransfer.service.ApprovalContentLoader;
import com.kosign.wecms.domain.payment.dto.ChangeApprovalLineRequest;
import com.kosign.wecms.domain.payment.dto.OverseasApprover;
import com.kosign.wecms.domain.payment.dto.OverseasCurrencyTotal;
import com.kosign.wecms.domain.payment.dto.OverseasPaymentDetailResponse;
import com.kosign.wecms.domain.payment.dto.OverseasPaymentDetailRowResponse;
import com.kosign.wecms.domain.payment.dto.OverseasResultListResponse;
import com.kosign.wecms.domain.payment.dto.OverseasResultRow;
import com.kosign.wecms.domain.payment.dto.OverseasMultiApprovalRequest;
import com.kosign.wecms.domain.payment.dto.OverseasScheduledListResponse;
import com.kosign.wecms.domain.payment.dto.OverseasScheduledRow;
import com.kosign.wecms.domain.payment.entity.OvrsPymnMstr;
import com.kosign.wecms.domain.payment.entity.OvrsPymnMstrId;
import com.kosign.wecms.domain.payment.entity.OvrsPymnSumrDetl;
import com.kosign.wecms.domain.payment.entity.OvrsPymnSumr;
import com.kosign.wecms.domain.payment.overseas.repository.OvrsPymnMstrRepository;
import com.kosign.wecms.domain.payment.internaltransfer.dto.ApprovalActionRequest;
import com.kosign.wecms.domain.payment.internaltransfer.dto.ApprovalAttachmentRow;
import com.kosign.wecms.domain.payment.internaltransfer.service.ApprovalAttachmentLoader;
import com.kosign.wecms.domain.payment.internaltransfer.dto.RequestApprovalRequest;
import com.kosign.wecms.domain.payment.internaltransfer.dto.RequestApprovalResponse;
import com.kosign.wecms.domain.payment.overseas.repository.OvrsPymnDtlsRepository;
import com.kosign.wecms.domain.payment.overseas.repository.OvrsPymnSumrRepository;
import com.kosign.wecms.domain.payment.overseas.service.OverseasDispatchExecutor;
import com.kosign.wecms.domain.payment.dispatch.service.DispatchResult;
import com.kosign.wecms.domain.payment.dispatch.service.DispatchResultResolver;
import com.kosign.wecms.domain.payment.repository.OverseasApproverRepository;
import com.kosign.wecms.domain.payment.repository.OverseasPaymentRepository;
import com.kosign.wecms.domain.auth.repository.BusiInfmRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Overseas-payment scheduled-payment list service.
 *
 * <p>Mirrors the legacy {@code pymn_0007_001_r001} read path:
 * <ul>
 *   <li>Paged UNION ALL query across {@code OVRS_PYMN_SUMR} (type E) and
 *       {@code OVRS_PYMN_MSTR} (type M) joined to {@code EXPT_RESOL_MSTR}.</li>
 *   <li>Per-row approver list from {@code EXPT_RESOL_APPRVR_INFM} (batch
 *       fetch to avoid N+1).</li>
 *   <li>Per-currency totals (mirrors IDO.OVRS_PYMN_SUMR_R003).</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OverseasPaymentService {

    public static final int DEFAULT_PAGE_SIZE = 10;
    public static final int MAX_PAGE_SIZE     = 100;

    private static final ZoneId            VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter DTM_FMT      = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final String            WSVC_ID      = "overseas-payment";
    /** F2 — WeCMS→ERP ('W') audit code for the Overseas Payment RESULT export
     *  (legacy {@code ErpTypeCd.OVERSEAS_REQUEST_RESULT}). */
    private static final String            ERP_TRNS_TYPE_OVERSEAS_RESULT = "14";
    /** G1 — deleted-batch ('D') audit code for the Overseas SCHEDULED payment
     *  (legacy {@code ErpTypeCd.OVERSEAS_PAYMENT_REQUEST} = "13", passed by
     *  {@code pymn_0007_001_d001_act.jsp:90}). Restores parity with the four
     *  domestic scheduled modules, which each write an {@code ERP_TYPE='D'}
     *  row on delete so the batch appears in the ERP "Deleted" tab. */
    private static final String            ERP_TRNS_TYPE_OVERSEAS_SCHEDULED = "13";

    private final OverseasPaymentRepository      paymentRepo;
    /** Emits the PROCESSING lifecycle signal on dispatch (HAENGSUNG Oracle write-back). */
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;
    private final OverseasApproverRepository     approverRepo;
    private final OvrsPymnSumrRepository         sumrRepository;
    /** OVRS_PYMN_MSTR access for the multi-payment ("M" type) Request
     *  Approval path (legacy {@code OVRS_PYMN_TYPE='M'}) + the Approval
     *  Details legacy-M-mode detail fallback (GAP-OVS-MODE). */
    private final OvrsPymnMstrRepository         ovrsPymnMstrRepository;
    /** Combined approval-attachment loader — UNION of EXPT_RESOL_FILE +
     *  EXPT_RESOL_PAY_EVID_FILE (GAP-ATT-1 / GAP-OVS-ATT). */
    private final ApprovalAttachmentLoader       approvalAttachmentLoader;
    private final OvrsPymnDtlsRepository         dtlsRepository;
    private final OverseasDispatchExecutor       dispatchExecutor;
    /** Phase 2 dispatch-result resolver — surfaces PROC_STAT / RS_CD /
     *  RS_MSG / PROC_DTTM / RSLT_DTTM per beneficiary line on the
     *  Overseas Result detail page. */
    private final DispatchResultResolver         dispatchResultResolver;
    private final ExptResolMstrRepository        exptResolMstrRepository;
    private final ExptResolApprvrInfmRepository  exptResolApprvrInfmRepository;
    /** Shared avatar-URL resolver — maps approver ids → /api/v1/users/{id}/image
     *  (null when no stored image). Single home for the USER_IMG → URL logic. */
    private final com.kosign.wecms.domain.auth.service.UserImageUrlResolver userImageUrlResolver;
    /** Shared Approval Content loader — reads {@code BLTB_POST_INFM.POST_CONT}
     *  (the legacy source of truth), identical to the four scheduled types. */
    private final ApprovalContentLoader          approvalContentLoader;
    private final AuditLogService                auditLogService;
    /** F2 — shared ERP_TRNS_HSTR writer for the Overseas Result export 'W' row.
     *  Also G1 — writes the {@code ERP_TYPE='D'} deleted-batch row on
     *  {@link #deleteDraftBatch}. */
    private final com.kosign.wecms.domain.setting.erptransactionhistory.service.ErpTrnsHistoryWriter erpHistoryWriter;
    /** G1 — resolves the uploaded Excel path so a deleted batch's file can be
     *  preserved into ERP Transaction History (read-fallback aware). */
    private final com.kosign.wecms.domain.payment.overseas.service.OverseasPaymentFileResolver fileResolver;
    private final BusiInfmRepository             busiInfmRepository;
    private final DocNoGenerator                 docNoGenerator;
    /** Pre-dispatch cut-off window guard. Mirrors legacy
     *  {@code PymnHelper.validateTransferTiming} — weekend block for
     *  non-NAPAS networks (so every SWIFT dispatch is blocked on   
     *  Sat / Sun) + per-(bank, network) cutoff window. Closes the G6
     *  gap from the 2026-05-29 dispatch audit. */
    private final com.kosign.wecms.domain.payment.internaltransfer.service.CutoffValidator
                                                 cutoffValidator;
    private final com.kosign.wecms.common.legacy.LegacyFunctionRepository legacyFunctions;
    private final com.kosign.wecms.domain.payment.approvalbox.service.ApprovalBoxFanoutService approvalBoxFanoutService;
    /** Shared request-approval bulletin / notification writer (BLTB_* tables). */
    private final com.kosign.wecms.domain.payment.notification.service.ApprovalNotificationService approvalNotificationService;
    /** H-1: NAPAS bank-code conversion for the OUT_BANK_CD back-stamp
     *  (legacy comm_0192_001_c005_act.jsp:404-410 updateOverseasPaymentSummary). */
    private final com.kosign.wecms.domain.payment.common.OutBankCdNormalizer outBankCdNormalizer;
    /** M-2/M-3 (legacy parity): EXPT_RESOL_MSTR display flags from BUSI_ENV_SET_INFM. */
    private final com.kosign.wecms.domain.payment.approvalbox.service.ApprovalDisplayFlagsResolver displayFlagsResolver;
    /** M-1 (legacy parity): shared Request Approval attachment writer (EXPT_RESOL_FILE). */
    private final com.kosign.wecms.domain.payment.approvalbox.service.ApprovalAttachmentWriter approvalAttachmentWriter;

    /**
     * Overseas scheduled-payment list.
     *
     * @param fromDt      required, yyyyMMdd
     * @param toDt        required, yyyyMMdd
     * @param apprTypeCds optional approval-status filter
     * @param keyword     optional search term (matched against file title / beneficiary name)
     * @param page        zero-based page index
     * @param size        rows per page (0 = use default)
     */
    public OverseasScheduledListResponse list(
            String fromDt,
            String toDt,
            List<String> apprTypeCds,
            String keyword,
            int page,
            int size,
            String sortField,
            String sortDirection) {

        if (fromDt == null || fromDt.isBlank()) {
            throw new BusinessException("WBE_VALIDATION", "FROM_DT is required");
        }
        if (toDt == null || toDt.isBlank()) {
            throw new BusinessException("WBE_VALIDATION", "TO_DT is required");
        }

        String bizKey = TenantContext.currentBizKey();

        int pageSize   = resolvePageSize(size);
        int pageIndex  = Math.max(0, page);
        int offset     = pageIndex * pageSize;

        String likePattern = buildLikePattern(keyword);
        String orderBy     = buildOrderBy(OverseasScheduledSortField.parse(sortField), sortDirection, "reg_dtm DESC");

        long totalCount = paymentRepo.countScheduled(bizKey, fromDt, toDt, apprTypeCds, likePattern);

        List<Object[]> rawRows = totalCount == 0
                ? List.of()
                : paymentRepo.findScheduled(bizKey, fromDt, toDt, apprTypeCds, likePattern, offset, pageSize, orderBy);

        // Batch-fetch approvers for all rows on this page
        List<String> apprSeqNos = rawRows.stream()
                .map(r -> (String) r[11])
                .filter(seq -> seq != null)
                .distinct()
                .collect(Collectors.toList());

        Map<String, List<Object[]>> approversBySeq =
                approverRepo.findByApprSeqNos(bizKey, apprSeqNos);
        Map<String, String> imgUrlByApprvrId = resolveApproverImageUrls(bizKey, approversBySeq);

        List<OverseasScheduledRow> rows = rawRows.stream()
                .map(r -> mapRow(r, approversBySeq, imgUrlByApprvrId))
                .collect(Collectors.toList());

        // Currency totals (same filter as the paged query)
        List<Object[]> rawTotals = paymentRepo.sumByCurrency(bizKey, fromDt, toDt, apprTypeCds, likePattern);
        List<OverseasCurrencyTotal> totals = rawTotals.stream()
                .map(this::mapTotal)
                .collect(Collectors.toList());

        return new OverseasScheduledListResponse(totalCount, pageIndex, pageSize, rows, totals);
    }

    // ─── single row ───────────────────────────────────────────────────────────

    /**
     * Fetch a single scheduled-payment row by {@code OVRS_PYMN_NO} or {@code DOC_NO}.
     *
     * <p>The {@code id} parameter accepts either:
     * <ul>
     *   <li>{@code OVRS_PYMN_NO} — the internal batch key (e.g. {@code 20260521000002})</li>
     *   <li>{@code DOC_NO} — the user-visible document number in {@code VO_YYMMDD_NNN}
     *       format assigned when the batch is submitted for approval. The frontend
     *       navigates to this value after request-approval so the URL stays human-readable.</li>
     * </ul>
     *
     * @throws BusinessException {@code WBE_NOT_FOUND} when the id is unknown or belongs to a different tenant
     */
    public OverseasScheduledRow getById(String id) {
        String bizKey = TenantContext.currentBizKey();

        // Primary lookup: OVRS_PYMN_NO (internal batch key)
        java.util.Optional<Object[]> raw = paymentRepo.findScheduledById(bizKey, id);

        // Fallback: treat id as DOC_NO → resolve EXPT_RESOL_MSTR → OVRS_PYMN_SUMR
        if (raw.isEmpty()) {
            raw = exptResolMstrRepository.findByBizKeyAndDocNoAndDelYn(bizKey, id, "N")
                    .filter(m -> m.getApprSeqNo() != null)
                    .flatMap(m -> sumrRepository.findByBizKeyAndApprSeqNo(bizKey, m.getApprSeqNo()))
                    .map(OvrsPymnSumr::getOvrsPymnNo)
                    .flatMap(resolvedNo -> paymentRepo.findScheduledById(bizKey, resolvedNo));
        }

        Object[] r = raw.orElseThrow(() -> new BusinessException("WBE_NOT_FOUND",
                "overseas payment not found: " + id));
        String apprSeqNo = (String) r[11];
        Map<String, List<Object[]>> approversBySeq = apprSeqNo != null
                ? approverRepo.findByApprSeqNos(bizKey, List.of(apprSeqNo))
                : Map.of();
        Map<String, String> imgUrlByApprvrId = resolveApproverImageUrls(bizKey, approversBySeq);
        return mapRow(r, approversBySeq, imgUrlByApprvrId);
    }

    // ─── result list ──────────────────────────────────────────────────────────

    public OverseasResultListResponse listResult(
            String fromDt,
            String toDt,
            String keyword,
            String procStat,
            int page,
            int size,
            String sortField,
            String sortDirection) {

        if (fromDt == null || fromDt.isBlank()) throw new BusinessException("WBE_VALIDATION", "FROM_DT is required");
        if (toDt   == null || toDt.isBlank())   throw new BusinessException("WBE_VALIDATION", "TO_DT is required");

        String bizKey     = TenantContext.currentBizKey();
        int    pageSize   = resolvePageSize(size);
        int    pageIndex  = Math.max(0, page);
        int    offset     = pageIndex * pageSize;
        String likePat    = buildLikePattern(keyword);
        // F2 — map the legacy PROC_STAT CSV (checkbox values "0,3" / "6") to the
        // computed TRNF_STTS letters {C,F,P} the query filters on.
        List<String> statusLetters = mapProcStatToStatusLetters(procStat);
        String orderBy    = buildOrderBy(OverseasResultSortField.parse(sortField), sortDirection, "reg_dtm DESC");

        long totalCount = paymentRepo.countResult(bizKey, fromDt, toDt, likePat, statusLetters);

        java.util.List<Object[]> rawRows = totalCount == 0
                ? java.util.List.of()
                : paymentRepo.findResult(bizKey, fromDt, toDt, likePat, statusLetters, offset, pageSize, orderBy);

        java.util.List<OverseasResultRow> rows = rawRows.stream()
                .map(this::mapResultRow)
                .collect(java.util.stream.Collectors.toList());

        // Footer totals — legacy parity: NO status filter (mirrors R005_CNT which
        // is fed date+keyword only). See OverseasPaymentRepository.sumResultByCurrency.
        java.util.List<Object[]> rawTotals = paymentRepo.sumResultByCurrency(bizKey, fromDt, toDt, likePat);
        java.util.List<OverseasCurrencyTotal> totals = rawTotals.stream()
                .map(this::mapTotal)
                .collect(java.util.stream.Collectors.toList());

        return new OverseasResultListResponse(totalCount, pageIndex, pageSize, rows, totals);
    }

    /**
     * Maps the legacy {@code PROC_STAT} CSV the Completed / In-Progress checkboxes
     * send (Completed = {@code "0,3"}, In Progress = {@code "6"}) to the computed
     * {@code TRNF_STTS} letters the result query filters on. Mirrors legacy
     * {@code pymn_0008_001_r001_act.jsp} {@code getDynamicStatusFilter}:
     * {@code 0→C}, {@code 3→F}, {@code 6→P}, {@code 9→P}. Returns {@code null}
     * (no filter, "all") when blank or no recognised value.
     */
    private static List<String> mapProcStatToStatusLetters(String procStat) {
        if (procStat == null || procStat.isBlank()) return null;
        java.util.LinkedHashSet<String> letters = new java.util.LinkedHashSet<>();
        for (String p : procStat.split(",")) {
            switch (p.trim()) {
                case "0" -> letters.add("C");
                case "3" -> letters.add("F");
                case "6", "9" -> letters.add("P");
                default -> { /* ignore unrecognised values */ }
            }
        }
        return letters.isEmpty() ? null : new java.util.ArrayList<>(letters);
    }

    public OverseasResultRow getResultById(String ovrsPymnNo) {
        String bizKey = TenantContext.currentBizKey();
        Object[] r = paymentRepo.findResultById(bizKey, ovrsPymnNo)
                .orElseThrow(() -> new BusinessException("WBE_NOT_FOUND",
                        "overseas payment result not found: " + ovrsPymnNo));
        return mapResultRow(r);
    }

    /**
     * Ordered list of Overseas Payment Result batch keys ({@code OVRS_PYMN_NO})
     * for the current filter — drives the toolbar bulk template-report export
     * ({@code OverseasPaymentDownloadService.generateBulk}). Uses the SAME
     * predicate + ordering as {@link #exportResult} / {@link #listResult}
     * ({@code findResultForExport}), so the bulk report mirrors what the user
     * sees on screen. One id per batch (E and M alike); the download service
     * dispatches each id to its E/M branch.
     */
    @Transactional(readOnly = true)
    public List<String> resultIdsForExport(String fromDt, String toDt, String keyword, String procStat) {
        if (fromDt == null || fromDt.isBlank()) throw new BusinessException("WBE_VALIDATION", "FROM_DT is required");
        if (toDt   == null || toDt.isBlank())   throw new BusinessException("WBE_VALIDATION", "TO_DT is required");
        String bizKey = TenantContext.currentBizKey();
        List<String> statusLetters = mapProcStatToStatusLetters(procStat);
        String likePat = buildLikePattern(keyword);
        return paymentRepo.findResultForExport(bizKey, fromDt, toDt, likePat, statusLetters)
                .stream()
                .map(b -> toStr(b[0]))
                .filter(s -> s != null && !s.isBlank())
                .toList();
    }

    /**
     * Streams the Overseas Payment Result XLSX per the WeCMS spec:
     * title row → summary row (USD/VND/KRW totals) → header row → one data row per
     * OVRS_PYMN_DTLS across all matching batches.
     *
     * <p>Mirrors legacy {@code pymn_0007_001} Excel export path.
     */
    public void exportResult(String fromDt, String toDt, String keyword, String procStat,
                             HttpServletResponse response) throws IOException {
        if (fromDt == null || fromDt.isBlank()) throw new BusinessException("WBE_VALIDATION", "FROM_DT is required");
        if (toDt   == null || toDt.isBlank())   throw new BusinessException("WBE_VALIDATION", "TO_DT is required");

        String bizKey    = TenantContext.currentBizKey();
        // F2 — same computed-status filter as the list (what you see is what exports).
        List<String> statusLetters = mapProcStatToStatusLetters(procStat);
        String likePat    = buildLikePattern(keyword);

        List<Object[]> batches = paymentRepo.findResultForExport(bizKey, fromDt, toDt, likePat, statusLetters);

        Map<String, BigDecimal> currencyTotals = new LinkedHashMap<>();
        List<OverseasPaymentResultExcelWriter.ExportRow> exportRows = new ArrayList<>();

        // Index layout matches OverseasPaymentRepository.buildResultSql (reused by
        // findResultForExport). b[5]=reg_dtm (=TRNF_DT, F1); b[2]=file_nm (=TITL, F4).
        for (Object[] b : batches) {
            String ovrsPymnNo  = toStr(b[0]);
            String batchRegDtm = toStr(b[5]);   // TRNF_DT → Transfer Date Time
            String draftUserNm = toStr(b[6]);
            String regId       = toStr(b[7]);
            String outBankNm   = toStr(b[8]);
            String wdrlActNo   = toStr(b[10]);
            String trnfYnVal   = toStr(b[11]);
            String docNo       = toStr(b[12]);
            String apprTitl    = toStr(b[13]);
            String apprRegDtm  = toStr(b[22]);  // EXPT_RESOL_MSTR.REG_DTM → Request Date/Time
            String apprTypeCd  = toStr(b[15]);
            String payDate     = toStr(b[14]);
            String orgFileNm   = toStr(b[2]);   // file_nm column = OVRS_PYMN_SUMR.ORG_FILE_NM (uploaded file name)
            String requestor   = draftUserNm != null && !draftUserNm.isBlank()
                    ? draftUserNm : OverseasPaymentResultExcelWriter.safe(regId);

            // F3 — M-mode batch: one export line from OVRS_PYMN_MSTR itself
            // (no OVRS_PYMN_SUMR_DETL child). Mirrors legacy R010 M branch.
            if ("M".equals(toStr(b[23]))) {
                for (Object[] m : paymentRepo.findMmodeDetailRows(bizKey, ovrsPymnNo)) {
                    String rowCcy = toStr(m[2]) != null ? toStr(m[2]) : toStr(b[1]);
                    BigDecimal amt = toBigDecimal(m[3]);
                    currencyTotals.merge(rowCcy, amt, BigDecimal::add);
                    exportRows.add(new OverseasPaymentResultExcelWriter.ExportRow(
                            OverseasPaymentResultExcelWriter.formatDtm(batchRegDtm),
                            ovrsPymnNo,
                            OverseasPaymentResultExcelWriter.mapStatus(trnfYnVal),
                            OverseasPaymentResultExcelWriter.formatDtm(apprRegDtm),
                            OverseasPaymentResultExcelWriter.stripExtension(orgFileNm),
                            OverseasPaymentResultExcelWriter.safe(outBankNm),
                            OverseasPaymentResultExcelWriter.safe(wdrlActNo),
                            OverseasPaymentResultExcelWriter.safe(toStr(m[7])),   // bnfcNm
                            OverseasPaymentResultExcelWriter.safe(toStr(m[8])),   // bnfcBankNm
                            OverseasPaymentResultExcelWriter.safe(toStr(m[9])),   // bic
                            OverseasPaymentResultExcelWriter.safe(toStr(m[10])),  // bnfcAcctNo
                            OverseasPaymentResultExcelWriter.safe(toStr(m[11])),  // bnfcAddr
                            rowCcy,
                            amt,
                            OverseasPaymentResultExcelWriter.mapFeePayer(toStr(m[4])),
                            OverseasPaymentResultExcelWriter.safe(toStr(m[6])),   // rprtAcct (chart_acct_cd)
                            OverseasPaymentResultExcelWriter.safe(toStr(m[5])),   // memo
                            requestor,
                            OverseasPaymentResultExcelWriter.formatDate(payDate),
                            OverseasPaymentResultExcelWriter.mapApprovalStatus(apprTypeCd),
                            OverseasPaymentResultExcelWriter.safe(docNo),
                            OverseasPaymentResultExcelWriter.safe(apprTitl)
                    ));
                }
                continue;
            }

            List<OvrsPymnSumrDetl> dtls =
                    dtlsRepository.findByBizKeyAndOvrsPymnNoOrderByOvrsPymnDetlNoAsc(bizKey, ovrsPymnNo);

            for (OvrsPymnSumrDetl d : dtls) {
                BigDecimal amt    = d.getTrnfAmt() != null ? d.getTrnfAmt() : BigDecimal.ZERO;
                String     rowCcy = d.getCcyCd()   != null ? d.getCcyCd()   : toStr(b[1]);
                currencyTotals.merge(rowCcy, amt, BigDecimal::add);

                exportRows.add(new OverseasPaymentResultExcelWriter.ExportRow(
                        OverseasPaymentResultExcelWriter.formatDtm(batchRegDtm),
                        d.getOvrsPymnDetlNo(),
                        OverseasPaymentResultExcelWriter.mapStatus(trnfYnVal),
                        OverseasPaymentResultExcelWriter.formatDtm(apprRegDtm),
                        OverseasPaymentResultExcelWriter.stripExtension(orgFileNm),
                        OverseasPaymentResultExcelWriter.safe(outBankNm),
                        OverseasPaymentResultExcelWriter.safe(wdrlActNo),
                        OverseasPaymentResultExcelWriter.safe(d.getBnfcNm()),
                        OverseasPaymentResultExcelWriter.safe(d.getBnfcBankNm()),
                        OverseasPaymentResultExcelWriter.safe(d.getBicCd()),
                        OverseasPaymentResultExcelWriter.safe(d.getBnfcAcctNo()),
                        OverseasPaymentResultExcelWriter.safe(d.getBnfcAddr()),
                        rowCcy,
                        amt,
                        OverseasPaymentResultExcelWriter.mapFeePayer(d.getFeePayerCd()),
                        OverseasPaymentResultExcelWriter.safe(d.getRprtAcctCd()),
                        OverseasPaymentResultExcelWriter.safe(d.getMemo()),
                        requestor,
                        OverseasPaymentResultExcelWriter.formatDate(payDate),
                        OverseasPaymentResultExcelWriter.mapApprovalStatus(apprTypeCd),
                        OverseasPaymentResultExcelWriter.safe(docNo),
                        OverseasPaymentResultExcelWriter.safe(apprTitl)
                ));
            }
        }

        String fileName = "OverseasPaymentResult.xlsx";
        String encoded  = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + encoded);
        response.setHeader("Access-Control-Expose-Headers", "Content-Disposition");

        // F2 — legacy pymn_0008_002_r003 writes an ERP_TRNS_HSTR 'W' (code 14)
        // audit row on the Overseas Result export, then serves the file. Tee the
        // workbook through a temp file so the 'W' row (saved copy) is persisted
        // before streaming. Non-blocking: recordExportedFile is REQUIRES_NEW +
        // swallow-and-log, so a failed audit never aborts the download. Response
        // headers + bytes are unchanged from the prior direct-stream version.
        java.nio.file.Path temp = java.nio.file.Files.createTempFile("wecms-ovrs-result-export-", ".xlsx");
        try {
            try (java.io.OutputStream fileOut = java.nio.file.Files.newOutputStream(temp)) {
                OverseasPaymentResultExcelWriter.write(currencyTotals, exportRows, fileOut);
            }
            erpHistoryWriter.recordExportedFile(ERP_TRNS_TYPE_OVERSEAS_RESULT, temp, fileName);
            try (java.io.InputStream in = java.nio.file.Files.newInputStream(temp)) {
                in.transferTo(response.getOutputStream());
            }
        } finally {
            try {
                java.nio.file.Files.deleteIfExists(temp);
            } catch (java.io.IOException ignore) {
                /* temp cleanup is best-effort */
            }
        }
    }

    // ─── detail + request approval ───────────────────────────────────────────

    /**
     * Full batch detail for the Request Approval / Approval Details dialogs.
     * Returns summary metadata + per-line beneficiary rows.
     * Mirrors legacy {@code pymn_0007_001} approval-request dialog load.
     *
     * <p>The {@code id} parameter accepts either:
     * <ul>
     *   <li>{@code OVRS_PYMN_NO} — the internal batch key (e.g. {@code 20260520000001})</li>
     *   <li>{@code DOC_NO} — the user-visible document number in {@code VO_YYMMDD_NNN}
     *       format generated by {@link DocNoGenerator} when the batch is submitted for
     *       approval. The frontend uses this value for navigation after request-approval.</li>
     * </ul>
     * When a direct {@code OVRS_PYMN_NO} lookup misses, the service resolves
     * {@code DOC_NO → EXPT_RESOL_MSTR.APPR_SEQ_NO → OVRS_PYMN_SUMR} as a fallback.
     */
    public OverseasPaymentDetailResponse getDetail(String id) {
        String bizKey = TenantContext.currentBizKey();

        // Primary lookup: treat the id as OVRS_PYMN_NO (internal key).
        java.util.Optional<OvrsPymnSumr> sumrOpt = sumrRepository.findByBizKeyAndOvrsPymnNo(bizKey, id);

        // Fallback: treat the id as DOC_NO (VO_YYMMDD_NNN user-visible key).
        // Resolves DOC_NO → APPR_SEQ_NO → OVRS_PYMN_SUMR. We keep the resolved
        // APPR_SEQ_NO so the legacy M-mode fallback (GAP-OVS-MODE) can reuse it.
        String docFallbackApprSeqNo = null;
        if (sumrOpt.isEmpty()) {
            java.util.Optional<ExptResolMstr> mstrByDoc = exptResolMstrRepository
                    .findByBizKeyAndDocNoAndDelYn(bizKey, id, "N")
                    .filter(m -> m.getApprSeqNo() != null && !m.getApprSeqNo().isBlank());
            if (mstrByDoc.isPresent()) {
                docFallbackApprSeqNo = mstrByDoc.get().getApprSeqNo();
                sumrOpt = sumrRepository.findByBizKeyAndApprSeqNo(bizKey, docFallbackApprSeqNo);
            }
        }

        // GAP-OVS-MODE: the approval may be a legacy "M-mode" record that
        // lives only in OVRS_PYMN_MSTR (not OVRS_PYMN_SUMR). Instead of
        // throwing WBE2045 — which crashed the popup for 57% of overseas
        // approvals in DEV — return a graceful header-only response. Throws
        // WBE2045 only when the id resolves to nothing in either table.
        if (sumrOpt.isEmpty()) {
            return resolveLegacyMmodeOrThrow(bizKey, id, docFallbackApprSeqNo);
        }

        OvrsPymnSumr sumr = sumrOpt.get();

        String ovrsPymnNo = sumr.getOvrsPymnNo();
        List<OvrsPymnSumrDetl> dtlList =
                dtlsRepository.findByBizKeyAndOvrsPymnNoOrderByOvrsPymnDetlNoAsc(bizKey, ovrsPymnNo);

        // Phase 2 — one bulk lookup against TB_PAY_FILE_MST + TB_PAY_FILE_DATA
        // (Overseas is always File path) so the per-line PROC_STAT / RS_CD /
        // PROC_DTTM / RSLT_DTTM round-trip on the wire.
        Map<String, DispatchResult> resultByTrnSeq =
                dispatchResultResolver.resolveOverseas(sumr, dtlList);

        List<OverseasPaymentDetailRowResponse> rows = dtlList.stream()
                .map(d -> {
                    DispatchResult r = resultByTrnSeq.getOrDefault(
                            d.getOvrsPymnDetlNo(), DispatchResult.EMPTY);
                    return new OverseasPaymentDetailRowResponse(
                            d.getOvrsPymnDetlNo(),
                            d.getHandTrcoKey(),
                            d.getCcyCd(),
                            d.getTrnfAmt() != null ? d.getTrnfAmt() : BigDecimal.ZERO,
                            d.getFeePayerCd(),
                            d.getMemo() != null ? d.getMemo() : "",
                            d.getRprtAcctCd(),
                            d.getBnfcNm(),
                            d.getBnfcBankNm(),
                            d.getBicCd(),
                            d.getBnfcAcctNo(),
                            d.getBnfcAddr(),
                            d.getBnfcTownNm(),
                            // OVRS_PYMN_SUMR_DETL has no per-row trnf_yn; the batch-level
                            // transfer flag lives on the master OVRS_PYMN_SUMR.
                            sumr.getTrnfYn() != null ? sumr.getTrnfYn() : "N",
                            r.procStat(), r.procStatKey(), r.procDttm(), r.rsltDttm(),
                            r.rsCd(), r.rsMsg(),
                            // Transaction Seq — legacy R007 maps PAY_TRAN_SEQ → PAY_FILE_SEQ_NO
                            // (pymn_0008_002 "Transaction Seq" column).
                            parsePayTranSeq(d.getPayTranSeq()));
                })
                .collect(Collectors.toList());

        String docNo = null;
        String apprStts = null;
        if (sumr.getApprSeqNo() != null && !sumr.getApprSeqNo().isBlank()) {
            ExptResolMstr mstr = exptResolMstrRepository.findById(
                    new com.kosign.wecms.domain.payment.approvalbox.entity.ExptResolMstrId(bizKey, sumr.getApprSeqNo())
            ).orElse(null);
            docNo    = mstr != null ? mstr.getDocNo() : null;
            apprStts = mstr != null ? mapApprTypeCd(mstr.getApprTypeCd()) : "P";
        }

        // GAP-OVS-ATT: approval attachments (UNION of EXPT_RESOL_FILE +
        // EXPT_RESOL_PAY_EVID_FILE) keyed by APPR_SEQ_NO. Previously never
        // loaded for overseas — the popup showed no attachments.
        List<ApprovalAttachmentRow> attachments =
                approvalAttachmentLoader.load(bizKey, sumr.getApprSeqNo());

        // Approval Content — BLTB_POST_INFM.POST_CONT (legacy R010 source).
        String cont = approvalContentLoader.load(bizKey, sumr.getApprSeqNo());

        return new OverseasPaymentDetailResponse(
                sumr.getOvrsPymnNo(),
                sumr.getTitl(),
                apprStts,
                sumr.getTotalTranAmt() != null ? sumr.getTotalTranAmt() : BigDecimal.ZERO,
                sumr.getCcyCd(),
                sumr.getTotTrnsCnt(),
                sumr.getRegId(),
                sumr.getRegDtm(),
                sumr.getModDtm(),
                sumr.getOutBankCd(),
                sumr.getWdrlActNo(),
                sumr.getCcyCd(),            // wdrlCcyCd — same as payment currency for Excel uploads
                docNo,
                sumr.getApprSeqNo(),
                sumr.getOrgFileNm(),
                sumr.getFileSize(),         // fileSize — OVRS_PYMN_SUMR.FILE_SIZE (was hardcoded null)
                sumr.getOrgFileNm(),
                rows,
                attachments,
                cont);
    }

    /**
     * F3 (2026-06-19): full M-mode (OVRS_PYMN_MSTR) Result detail — a record
     * that lives in {@code OVRS_PYMN_MSTR} (type 'M', individual/manual) and has
     * no {@code OVRS_PYMN_SUMR} row. Mirrors legacy R006 (header) + R007 (the
     * single remittance line). An M-mode payment is one remittance, so the line
     * table renders exactly one row sourced from the {@code OVRS_PYMN_MSTR} record
     * itself (no {@code OVRS_PYMN_SUMR_DETL} child); {@code OVRS_PYMN_DETL_NO} is
     * null (R010 parity). The per-line {@code PROC_STAT}/{@code RS_CD} come from
     * {@code TB_PAY_FILE_MST}/{@code _DATA}, so the FE derives the result status
     * the same way it does for E-mode lines. {@code legacyMmode} is now
     * {@code false} — M-mode is fully supported.
     *
     * <p>Header withdrawal bank/account = the debit-account fields
     * ({@code DBT_ACCT_CD}/{@code DBT_ACCT_NO}/{@code DBT_CCY_CD}) per R006 M branch.
     *
     * <p>Throws {@code WBE2045} only when the id resolves to nothing in
     * {@code OVRS_PYMN_MSTR} either (genuinely not found / wrong tenant).
     */
    private OverseasPaymentDetailResponse resolveLegacyMmodeOrThrow(
            String bizKey, String id, String docFallbackApprSeqNo) {

        // Resolve the single target M record. When id was a DOC_NO the fallback
        // already gave us its APPR_SEQ_NO (take the first remittance under it);
        // otherwise id is an OVRS_PYMN_NO that lives only in OVRS_PYMN_MSTR.
        OvrsPymnMstr target;
        String apprSeqNo = docFallbackApprSeqNo;
        if (apprSeqNo != null) {
            target = ovrsPymnMstrRepository.findByBizKeyAndApprSeqNo(bizKey, apprSeqNo)
                    .stream().findFirst().orElse(null);
        } else {
            target = ovrsPymnMstrRepository.findByBizKeyAndOvrsPymnNo(bizKey, id).orElse(null);
            if (target != null) apprSeqNo = target.getApprSeqNo();
        }

        if (target == null) {
            throw new BusinessException("WBE2045", "overseas payment not found: " + id);
        }

        ExptResolMstr mstr = (apprSeqNo != null && !apprSeqNo.isBlank())
                ? exptResolMstrRepository.findById(
                        new com.kosign.wecms.domain.payment.approvalbox.entity.ExptResolMstrId(bizKey, apprSeqNo))
                        .orElse(null)
                : null;
        String docNo    = mstr != null ? mstr.getDocNo() : null;
        String apprStts = mstr != null ? mapApprTypeCd(mstr.getApprTypeCd()) : "P";
        String titl     = mstr != null ? mstr.getTitl() : null;

        // F3 — the M remittance line(s) for this OVRS_PYMN_NO (R007 M branch),
        // carrying PROC_STAT / RS_CD from TB_PAY_FILE so the FE derives status.
        List<OverseasPaymentDetailRowResponse> rows =
                paymentRepo.findMmodeDetailRows(bizKey, target.getOvrsPymnNo())
                        .stream()
                        .map(this::mapMmodeDetailRow)
                        .collect(Collectors.toList());

        List<ApprovalAttachmentRow> attachments =
                approvalAttachmentLoader.load(bizKey, apprSeqNo);

        // R006 M: header total = OPM.TOTAL_TRAN_AMT (fall back to the single
        // remittance amount when the summary column is null).
        BigDecimal total = target.getTotalTranAmt() != null ? target.getTotalTranAmt()
                : (target.getTrnfAmt() != null ? target.getTrnfAmt() : BigDecimal.ZERO);

        log.debug("feature=overseas-payment result-detail M-mode (OVRS_PYMN_MSTR) "
                + "bizKey={} apprSeqNo={} ovrsPymnNo={} lines={}",
                bizKey, apprSeqNo, target.getOvrsPymnNo(), rows.size());

        return new OverseasPaymentDetailResponse(
                target.getOvrsPymnNo(),
                titl,
                apprStts,
                total,
                target.getTrnfCcyCd(),
                target.getTotTrnsCnt() != null ? target.getTotTrnsCnt() : rows.size(),
                mstr != null ? mstr.getRegId() : target.getRegId(),
                mstr != null ? mstr.getRegDtm() : target.getRegDtm(),
                target.getModDtm(),
                // M-mode withdrawal = debit-account fields (R006 M branch).
                target.getDbtAcctCd(),
                target.getDbtAcctNo(),
                target.getDbtCcyCd(),
                docNo,
                apprSeqNo,
                null,                       // fileNm
                null,                       // fileSize
                null,                       // orgFileNm
                rows,
                attachments,                // legacyMmode=false, notice=null (via convenience ctor)
                approvalContentLoader.load(bizKey, apprSeqNo));  // Approval Content — BLTB_POST_INFM.POST_CONT
    }

    /**
     * Maps one {@link OverseasPaymentRepository#findMmodeDetailRows} row to the
     * detail-row DTO. {@code ovrsPymnDetlNo} is null (M-mode, R010 parity);
     * {@code procStatKey} is left null so the FE {@code deriveResultStatus}
     * resolves the status from the raw {@code proc_stat}/{@code rs_cd} (same path
     * as an E-mode line whose key isn't yet mapped).
     */
    private OverseasPaymentDetailRowResponse mapMmodeDetailRow(Object[] m) {
        String memo = toStr(m[5]);
        return new OverseasPaymentDetailRowResponse(
                null,                                  // ovrsPymnDetlNo (M-mode)
                toStr(m[1]),                           // bnfcCode = HAND_TRCO_KEY
                toStr(m[2]),                           // ccyCd (TRNF_CCY_CD)
                m[3] != null ? toBigDecimal(m[3]) : BigDecimal.ZERO, // trnfAmt
                toStr(m[4]),                           // feePayerCd
                memo != null ? memo : "",              // memo
                toStr(m[6]),                           // rprtAcctCd (CHART_ACCT_CD)
                toStr(m[7]),                           // bnfcNm
                toStr(m[8]),                           // bnfcBankNm
                toStr(m[9]),                           // bicCd (BNFC_BANK_BIC)
                toStr(m[10]),                          // bnfcAcctNo
                toStr(m[11]),                          // bnfcAddr
                null,                                  // bnfcTownNm (M-mode — findMmodeDetailRows selects no town column; E-mode scheduled batches carry it via getDetail)
                toStr(m[12]) != null ? toStr(m[12]) : "N", // trnfYn
                toStr(m[13]),                          // procStat
                null,                                  // procStatKey (FE derives from raw)
                toStr(m[14]),                          // procDttm
                toStr(m[14]),                          // rsltDttm = procDttm (R007 RSLT_DTTM)
                toStr(m[15]),                          // rsCd
                toStr(m[16]),                          // rsMsg
                null);                                 // trnSeq (M-mode — no PAY_TRAN_SEQ column in findMmodeDetailRows)
    }

    /**
     * Parse the legacy {@code OVRS_PYMN_SUMR_DETL.PAY_TRAN_SEQ} varchar into the
     * {@code Integer} the wire {@code trn_seq} field expects. Null / blank /
     * non-numeric → null (renders blank in the Result detail "Transaction Seq"
     * column rather than throwing).
     */
    private static Integer parsePayTranSeq(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Overseas per-line transactions for one approval, normalized to the shared
     * {@link com.kosign.wecms.domain.payment.approvalbox.dto.ApprovalTransactionRow}
     * the Approval Details popup consumes. Resolves by {@code APPR_SEQ_NO}:
     * E-mode batches live in {@code OVRS_PYMN_SUMR} + {@code OVRS_PYMN_SUMR_DETL};
     * M-mode (individual/manual) remittances live only in {@code OVRS_PYMN_MSTR}
     * (one line). Empty when the approval resolves in neither table.
     *
     * <p>Field mapping mirrors the per-type Overseas Scheduled page's
     * {@code detailLineToApprovalTransaction} 1:1 (bic_cd→deposit bank code,
     * bnfc_bank_nm→deposit bank name, bnfc_nm→recipient, memo→remarks/description,
     * rprt_acct_cd→reporting account) so the popup renders identically whether
     * opened from the Scheduled page (per-type fetch) or the shared
     * {@code CommonApprovalDetailsDialog} (this path, used by the Result page +
     * Notifications). Backs the {@code APPR_DV='34'} branch in
     * {@code ApprovalDetailService.transactions}.
     */
    @Transactional(readOnly = true)
    public List<com.kosign.wecms.domain.payment.approvalbox.dto.ApprovalTransactionRow>
            transactionLinesByApprSeqNo(String bizKey, String apprSeqNo) {
        if (apprSeqNo == null || apprSeqNo.isBlank()) {
            return List.of();
        }
        // E-mode: OVRS_PYMN_SUMR + OVRS_PYMN_SUMR_DETL.
        java.util.Optional<OvrsPymnSumr> sumrOpt =
                sumrRepository.findByBizKeyAndApprSeqNo(bizKey, apprSeqNo);
        if (sumrOpt.isPresent()) {
            return dtlsRepository
                    .findByBizKeyAndOvrsPymnNoOrderByOvrsPymnDetlNoAsc(
                            bizKey, sumrOpt.get().getOvrsPymnNo())
                    .stream()
                    .map(OverseasPaymentService::toApprovalTxnFromDetl)
                    .collect(Collectors.toList());
        }
        // M-mode: OVRS_PYMN_MSTR (single remittance line, R007 M branch).
        OvrsPymnMstr mstr = ovrsPymnMstrRepository
                .findByBizKeyAndApprSeqNo(bizKey, apprSeqNo)
                .stream().findFirst().orElse(null);
        if (mstr != null) {
            return paymentRepo.findMmodeDetailRows(bizKey, mstr.getOvrsPymnNo())
                    .stream()
                    .map(this::mapMmodeDetailRow)
                    .map(OverseasPaymentService::toApprovalTxnFromRow)
                    .collect(Collectors.toList());
        }
        return List.of();
    }

    /** E-mode {@code OVRS_PYMN_SUMR_DETL} → shared {@code ApprovalTransactionRow}. */
    private static com.kosign.wecms.domain.payment.approvalbox.dto.ApprovalTransactionRow
            toApprovalTxnFromDetl(OvrsPymnSumrDetl d) {
        return new com.kosign.wecms.domain.payment.approvalbox.dto.ApprovalTransactionRow(
                d.getOvrsPymnDetlNo(),
                d.getBicCd(),                                  // deposit bank code = beneficiary BIC
                d.getBnfcBankNm(),                             // deposit bank name = beneficiary bank
                d.getCcyCd(),
                d.getBnfcAcctNo(),                             // deposit account = beneficiary account
                d.getTrnfAmt(),
                d.getBnfcNm(),                                 // recipient = beneficiary name
                d.getMemo(),                                   // remarks = memo
                null,                                          // contact name — n/a for overseas
                d.getMemo(),                                   // description = memo (FE parity)
                null,                                          // document no — n/a
                d.getRprtAcctCd(),                             // reporting account
                null,                                          // reporting breadcrumb
                d.getBnfcAddr(),                               // beneficiary address (overseas col)
                d.getFeePayerCd(),                             // fee payer (overseas col)
                d.getBnfcTownNm());                            // town name (overseas col)
    }

    /** M-mode row (already mapped to {@link OverseasPaymentDetailRowResponse})
     *  → shared {@code ApprovalTransactionRow}. */
    private static com.kosign.wecms.domain.payment.approvalbox.dto.ApprovalTransactionRow
            toApprovalTxnFromRow(OverseasPaymentDetailRowResponse r) {
        return new com.kosign.wecms.domain.payment.approvalbox.dto.ApprovalTransactionRow(
                r.ovrsPymnDetlNo(),
                r.bicCd(),
                r.bnfcBankNm(),
                r.ccyCd(),
                r.bnfcAcctNo(),
                r.trnfAmt(),
                r.bnfcNm(),
                r.memo(),
                null,
                r.memo(),
                null,
                r.rprtAcctCd(),
                null,
                r.bnfcAddr(),                                  // beneficiary address (overseas col)
                r.feePayerCd(),                                // fee payer (overseas col)
                r.bnfcTownNm());                               // town name — null for M-mode (no town col)
    }

    /** Withdrawal-account fields for the Approval Details popup's General
     *  Information row, keyed by {@code APPR_SEQ_NO}. {@code outBankNm} is left
     *  null (overseas {@code getDetail} likewise ships the raw code; the
     *  frontend falls back to it) to avoid a separate bank-name resolver here. */
    public record ApprovalWithdrawalInfo(
            String outBankCd, String outBankNm, String wdrlActNo, String wdrlCcyCd,
            /** Dispatchable overseas batch id ({@code OVRS_PYMN_SUMR.OVRS_PYMN_NO},
             *  E-mode). Null for M-mode ({@code OVRS_PYMN_MSTR}) — that path has no
             *  batch-dispatch endpoint, so the auto-transfer flow falls back to the
             *  manual Transfer button there. */
            String ovrsPymnNo) {}

    /**
     * Overseas withdrawal account by {@code APPR_SEQ_NO} — E-mode reads
     * {@code OVRS_PYMN_SUMR} (OUT_BANK_CD / WDRL_ACT_NO / CCY_CD); M-mode reads
     * the {@code OVRS_PYMN_MSTR} debit-account fields (DBT_ACCT_CD / DBT_ACCT_NO
     * / DBT_CCY_CD), mirroring {@link #getDetail}'s withdrawal mapping for both
     * modes. Empty when the approval resolves in neither table. Backs the
     * overseas fallback in {@code ApprovalDetailService.master}.
     */
    @Transactional(readOnly = true)
    public java.util.Optional<ApprovalWithdrawalInfo> withdrawalByApprSeqNo(
            String bizKey, String apprSeqNo) {
        if (apprSeqNo == null || apprSeqNo.isBlank()) {
            return java.util.Optional.empty();
        }
        java.util.Optional<OvrsPymnSumr> sumrOpt =
                sumrRepository.findByBizKeyAndApprSeqNo(bizKey, apprSeqNo);
        if (sumrOpt.isPresent()) {
            OvrsPymnSumr s = sumrOpt.get();
            return java.util.Optional.of(new ApprovalWithdrawalInfo(
                    s.getOutBankCd(), null, s.getWdrlActNo(), s.getCcyCd(),
                    s.getOvrsPymnNo()));
        }
        OvrsPymnMstr m = ovrsPymnMstrRepository
                .findByBizKeyAndApprSeqNo(bizKey, apprSeqNo)
                .stream().findFirst().orElse(null);
        if (m != null) {
            // M-mode has no batch-dispatch endpoint → ovrsPymnNo left null so the
            // auto-transfer flow falls back to the manual Transfer button.
            return java.util.Optional.of(new ApprovalWithdrawalInfo(
                    m.getDbtAcctCd(), null, m.getDbtAcctNo(), m.getDbtCcyCd(), null));
        }
        return java.util.Optional.empty();
    }

    /**
     * Preview the next {@code VO_YYMMDD_NNN} document number for the Request
     * Approval popup (read-only). Computes via the shared {@link DocNoGenerator}
     * WITHOUT reserving or persisting anything — the authoritative DOC_NO is
     * stamped at {@link #requestApproval} submit time. Mirrors
     * {@link com.kosign.wecms.domain.payment.internaltransfer.service.InternalTransferService#previewNextDocNo}.
     */
    @Transactional(readOnly = true)
    public com.kosign.wecms.domain.payment.internaltransfer.dto.NextDocNoResponse previewNextDocNo() {
        return new com.kosign.wecms.domain.payment.internaltransfer.dto.NextDocNoResponse(
                docNoGenerator.nextDocNo(TenantContext.currentBizKey()));
    }

    /**
     * Phase-A "stamp-only" request-approval for overseas payment Excel batches.
     * Stamps {@code APPR_SEQ_NO} on {@code OVRS_PYMN_SUMR} so the list query's
     * {@code deriveStatus()} fallback flips the row to "pending" (LEFT JOIN miss
     * on {@code EXPT_RESOL_MSTR} + non-null {@code apprSeqNo}).
     *
     * <p>Full {@code EXPT_RESOL_MSTR} / approver-chain write is deferred
     * (same precedent as expense-payment / vendor-payment Phase A).
     */
    @Transactional
    public RequestApprovalResponse requestApproval(String ovrsPymnNo, RequestApprovalRequest req,
            java.util.List<org.springframework.web.multipart.MultipartFile> attachments) {
        String bizKey = TenantContext.currentBizKey();
        OvrsPymnSumr sumr = resolveSumr(bizKey, ovrsPymnNo);

        if ("Y".equals(sumr.getTrnfYn())) {
            throw new BusinessException("WBE2127");
        }

        String existingStts = deriveApprStts(sumr);
        if ("C".equals(existingStts)) {
            // Approved and awaiting transfer — cannot re-submit; approver can revise via approval-action
            throw new BusinessException("WBE2046");
        }
        if (sumr.getApprSeqNo() != null && !sumr.getApprSeqNo().isBlank()
                && !"X".equals(existingStts)) {
            // Already has a pending approval request (not rejected) — block duplicate
            throw new BusinessException("WBE2046");
        }
        if ("X".equals(existingStts)) {
            // Rejected: allow re-submission. Clearing APPR_SEQ_NO detaches the old
            // EXPT_RESOL_MSTR row; status now derives from the fresh chain stamped below.
            sumr.setApprSeqNo(null);
        }

        if (req == null || req.approvers() == null || req.approvers().isEmpty()) {
            throw new BusinessException("WBE_VALIDATION", "approvers is required");
        }

        String apprSeqNo = allocateApprSeqNo(bizKey);
        String docNo;
        try {
            docNo = stampApproval(sumr, apprSeqNo, req);
        } catch (org.springframework.dao.DataIntegrityViolationException race) {
            log.info("feature=overseas-payment apprSeqNo race retrying once bizKey={}", bizKey);
            apprSeqNo = allocateApprSeqNo(bizKey);
            docNo = stampApproval(sumr, apprSeqNo, req);
        }

        // M-1 (legacy parity): persist Request Approval attachments into
        // EXPT_RESOL_FILE (legacy processFileAttachments / REC1).
        approvalAttachmentWriter.persist(bizKey, apprSeqNo, attachments);

        auditLogService.append(AuditLogContext.builder()
                .wsvcId(WSVC_ID)
                .refNo(ovrsPymnNo)
                .refNo1(apprSeqNo)
                .activityKey("audit.overseas_payment.activity")
                .detailKey("audit.overseas_payment.request_approval.detail")
                .build());

        log.info("feature=overseas-payment request-approval bizKey={} ovrsPymnNo={} apprSeqNo={} docNo={} apprLineNo={} approvers={}",
                bizKey, ovrsPymnNo, apprSeqNo, docNo, req.apprLineNo(), req.approvers().size());

        // Lifecycle signal — request-approval submitted (PENDING). The HAENGSUNG
        // Oracle write-back listener maps this to TB_TRNS_MAST.LAST_STATUS='10'
        // (legacy comm_0192_001_c005 → ErpStatusUpdateHelper PENDING). No-op for
        // non-HAENGSUNG / non-ERP batches (listener guards). apprSeqNo is the
        // value resolved above (after the one-shot race retry). NOTE: the M-mode
        // requestApprovalMulti path is intentionally NOT wired — the HAENGSUNG
        // refresh imports overseas as single AF1 batches (OVRS_PYMN_SUMR), never
        // M-mode, so its apprSeqNo would not resolve to an ERP-linked summary.
        eventPublisher.publishEvent(new com.kosign.wecms.domain.payment.approvalbox.event
                .PaymentApprovalStatusChangedEvent(bizKey, apprSeqNo,
                com.kosign.wecms.domain.payment.approvalbox.event.PaymentApprovalStatus.PENDING));

        return new RequestApprovalResponse(apprSeqNo, docNo);
    }

    /**
     * Multi-payment ("M" type) Request Approval — approves a list of
     * {@code OVRS_PYMN_MSTR} rows under one {@code EXPT_RESOL_MSTR}.
     *
     * <p>Mirrors the legacy {@code comm_0192_001_c005} {@code OVRS_PYMN_TYPE='M'}
     * branch:
     * <ul>
     *   <li>{@code createExptResolDetlForOverseas} — one
     *       {@code EXPT_RESOL_DETL} per payment (DOC_ID = OVRS_PYMN_NO,
     *       CCY_CD = TRNF_CCY_CD, AMT = TRNF_AMT).</li>
     *   <li>{@code updateOvrsPymnMstrData} — stamps {@code APPR_SEQ_NO}
     *       onto every {@code OVRS_PYMN_MSTR} row.</li>
     * </ul>
     */
    @Transactional
    public RequestApprovalResponse requestApprovalMulti(OverseasMultiApprovalRequest req) {
        String bizKey = TenantContext.currentBizKey();
        if (req == null || req.ovrsPymnNos() == null || req.ovrsPymnNos().isEmpty()) {
            throw new BusinessException("WBE_VALIDATION", "ovrs_pymn_nos is required");
        }
        if (req.approvers() == null || req.approvers().isEmpty()) {
            throw new BusinessException("WBE_VALIDATION", "approvers is required");
        }

        // Resolve + validate every master row FIRST (legacy
        // validateOverseasPayment per REC4 item). Reject if already
        // transferred or already carrying an approval.
        List<OvrsPymnMstr> masters = new ArrayList<>();
        // Batch-load all referenced masters in one query, then run the
        // original per-element checks (in request order) against the map.
        // Behavior is preserved byte-for-byte: blank → WBE_VALIDATION,
        // missing → WBE_NOT_FOUND, transferred → WBE2127, approved →
        // WBE2046, all in the same first-failure order; duplicate input
        // ids re-check the same row exactly as the prior findById loop.
        List<OvrsPymnMstrId> ids = new ArrayList<>();
        for (String no : req.ovrsPymnNos()) {
            if (no != null && !no.isBlank()) {
                ids.add(new OvrsPymnMstrId(bizKey, no.trim()));
            }
        }
        Map<String, OvrsPymnMstr> byNo = new LinkedHashMap<>();
        for (OvrsPymnMstr loaded : ovrsPymnMstrRepository.findAllById(ids)) {
            byNo.put(loaded.getOvrsPymnNo(), loaded);
        }
        for (String no : req.ovrsPymnNos()) {
            if (no == null || no.isBlank()) {
                throw new BusinessException("WBE_VALIDATION", "ovrs_pymn_no: blank entry");
            }
            OvrsPymnMstr m = byNo.get(no.trim());
            if (m == null) {
                throw new BusinessException("WBE_NOT_FOUND", "ovrs_pymn_no: " + no);
            }
            if ("Y".equals(m.getTrnfYn())) {
                throw new BusinessException("WBE2127");
            }
            if (m.getApprSeqNo() != null && !m.getApprSeqNo().isBlank()) {
                throw new BusinessException("WBE2046");
            }
            masters.add(m);
        }

        String apprSeqNo = allocateApprSeqNo(bizKey);
        String docNo;
        try {
            docNo = stampApprovalMulti(masters, apprSeqNo, req);
        } catch (org.springframework.dao.DataIntegrityViolationException race) {
            log.info("feature=overseas-payment apprSeqNo race retrying once bizKey={}", bizKey);
            apprSeqNo = allocateApprSeqNo(bizKey);
            docNo = stampApprovalMulti(masters, apprSeqNo, req);
        }

        auditLogService.append(AuditLogContext.builder()
                .wsvcId(WSVC_ID)
                .refNo(masters.get(0).getOvrsPymnNo())
                .refNo1(apprSeqNo)
                .activityKey("audit.overseas_payment.activity")
                .detailKey("audit.overseas_payment.request_approval.detail")
                .build());

        log.info("feature=overseas-payment request-approval-multi bizKey={} count={} apprSeqNo={} docNo={} apprLineNo={} approvers={}",
                bizKey, masters.size(), apprSeqNo, docNo, req.apprLineNo(), req.approvers().size());

        return new RequestApprovalResponse(apprSeqNo, docNo);
    }

    /** Writes the master + N details + approver chain + appr-line snapshot,
     *  then stamps APPR_SEQ_NO on every OVRS_PYMN_MSTR row. Mirrors the
     *  single-payment {@link #stampApproval} but iterates the master list. */
    private String stampApprovalMulti(List<OvrsPymnMstr> masters, String apprSeqNo,
                                      OverseasMultiApprovalRequest req) {
        String bizKey = masters.get(0).getBizKey();
        String userId = TenantContext.currentUserId();
        String userNm = TenantContext.currentUserNm();
        String nowDtm = ZonedDateTime.now(VIETNAM_ZONE).format(DTM_FMT);
        String draftDate = nowDtm.substring(0, 8);
        String draftTime = nowDtm.substring(8, 14);

        // Batch total + representative currency (legacy stores the single
        // input.TOT_PAY_KHR_AMT; we sum the per-payment TRNF_AMT and pick
        // the first payment's currency for the column split).
        BigDecimal total = masters.stream()
                .map(m -> m.getTrnfAmt() != null ? m.getTrnfAmt() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        String ccy = masters.get(0).getTrnfCcyCd() != null
                ? masters.get(0).getTrnfCcyCd().toUpperCase()
                : "VND";

        // 1. EXPT_RESOL_MSTR (APPR_DV='34', APPR_STS='2', APPR_TYPE_CD='00')
        ExptResolMstr mstr = new ExptResolMstr();
        mstr.setBizKey(bizKey);
        mstr.setApprSeqNo(apprSeqNo);
        mstr.setApprDv(ApprDv.OVERSEAS.code());
        mstr.setApprSts("2");
        mstr.setDraftDate(draftDate);
        mstr.setDraftTime(draftTime);
        mstr.setDraftUserId(userId != null ? userId : "");
        mstr.setDraftUserNm(userNm != null ? userNm : "");
        mstr.setDraftUserSiteCd(bizKey);
        String cmpnNm = busiInfmRepository.findById(bizKey)
                .map(b -> b.getCmpnNm() != null ? b.getCmpnNm() : bizKey)
                .orElse(bizKey);
        mstr.setDraftUserSiteNm(cmpnNm);
        mstr.setHandRegYn("Y");
        // M-2 (legacy parity): display flags from BUSI_ENV_SET_INFM (00014/16/17/18),
        // not hardcoded. Mirrors comm_0192_001_c005_act.jsp:316-320.
        var flags = displayFlagsResolver.resolve(bizKey);
        mstr.setApprBoxUseYn(flags.apprBoxUseYn());
        mstr.setApprDatDispYn(flags.apprDatDispYn());
        mstr.setCmpnLogoDispYn(flags.cmpnLogoDispYn());
        mstr.setRegDispYn(flags.regDispYn());
        String titl = req.postTitle() != null ? req.postTitle().trim() : "";
        mstr.setTitl(titl);
        String docNo = docNoGenerator.nextDocNo(bizKey);
        mstr.setDocNo(docNo);
        mstr.setDocNm(ApprDv.OVERSEAS.docNm());   // L-1 (legacy parity): legacy DOC_NM = apprDv.getLabel()
        boolean isUsd = "USD".equals(ccy);
        boolean isVnd = "VND".equals(ccy);
        mstr.setTotPayKhrAmt(isUsd ? BigDecimal.ZERO : total);
        mstr.setTotPayUsdAmt(isUsd ? total : BigDecimal.ZERO);
        mstr.setTotPayVndAmt(isVnd ? total : BigDecimal.ZERO);
        mstr.setCont(req.postCont() != null && !req.postCont().isBlank() ? req.postCont().trim() : null);
        mstr.setPayDate(req.payDate() != null && !req.payDate().isBlank() ? req.payDate().trim() : null);
        mstr.setDelYn("N");
        mstr.setPayEndYn("N");
        mstr.setApprTypeCd("00");
        mstr.setRegId(userId != null ? userId : "");
        mstr.setRegDtm(nowDtm);
        mstr.setModId(userId);
        mstr.setModDtm(nowDtm);
        exptResolMstrRepository.save(mstr);

        // 2. EXPT_RESOL_APPRVR_INFM — first "P", rest "W"; APPR_LINE_NO set.
        List<RequestApprovalRequest.ApproverInput> approvers = req.approvers();
        for (int i = 0; i < approvers.size(); i++) {
            RequestApprovalRequest.ApproverInput ap = approvers.get(i);
            ExptResolApprvrInfm row = new ExptResolApprvrInfm();
            row.setBizKey(bizKey);
            row.setApprSeqNo(apprSeqNo);
            row.setApprvrId(ap.apprvrId());
            row.setApprvrStts(i == 0 ? "P" : "W");
            row.setApprvrNm(ap.apprvrNm());
            row.setApprDtm(null);
            row.setRegId(userId);
            row.setRegDtm(nowDtm);
            row.setModId(userId != null ? userId : "");
            row.setModDtm(nowDtm);
            row.setReadYn("N");
            row.setApprvrLineNo(BigDecimal.valueOf(i + 1));
            row.setApprLineNo(BigDecimal.valueOf(req.apprLineNo()));
            exptResolApprvrInfmRepository.save(row);
        }

        // 3. EXPT_RESOL_DETL — one row per payment (legacy
        //    createExptResolDetlForOverseas: DOC_ID=OVRS_PYMN_NO,
        //    CCY_CD=TRNF_CCY_CD, AMT=TRNF_AMT).
        for (OvrsPymnMstr m : masters) {
            approvalBoxFanoutService.writeResolutionDetail(
                    apprSeqNo, m.getOvrsPymnNo(), m.getTrnfAmt(), m.getTrnfCcyCd());
        }

        // 4. EXPT_RESOL_APPR_LINE print-header snapshot (env-gated).
        approvalBoxFanoutService.writeApprovalLineSnapshot(apprSeqNo);

        // 5. Stamp APPR_SEQ_NO on every OVRS_PYMN_MSTR row (legacy
        //    updateOvrsPymnMstrData).
        for (OvrsPymnMstr m : masters) {
            m.setApprSeqNo(apprSeqNo);
            m.setModId(userId);
            m.setModDtm(nowDtm);
            ovrsPymnMstrRepository.save(m);
        }

        // 6. Bulletin / notification fan-out (legacy createBulletinPost +
        // createUserNewPostNotifications + BltbHelper.createNotificationToApprvr).
        approvalNotificationService.notifyRequestApproval(
                apprSeqNo, req.postTitle(), req.postCont(), "34", "Oversea Payment");

        return docNo;
    }

    /**
     * Approve or reject an overseas payment batch (Phase A stamp-only).
     * Flips {@code APPR_STTS} on {@code OVRS_PYMN_SUMR} to {@code "C"} (approved)
     * or {@code "X"} (rejected).
     *
     * <p>Guards: {@code WBE2126} when no approval request exists yet;
     * {@code WBE2127} when the batch is already in a final state.
     */
    @Transactional
    public void actOnApproval(String ovrsPymnNo, ApprovalActionRequest req) {
        String bizKey = TenantContext.currentBizKey();
        OvrsPymnSumr sumr = resolveSumr(bizKey, ovrsPymnNo);

        if (sumr.getApprSeqNo() == null || sumr.getApprSeqNo().isBlank()) {
            throw new BusinessException("WBE2126");
        }

        if ("Y".equals(sumr.getTrnfYn())) {
            // Batch has already been transferred — approval decision is final
            throw new BusinessException("WBE2127");
        }

        String userId = TenantContext.currentUserId();
        String nowDtm = ZonedDateTime.now(VIETNAM_ZONE).format(DTM_FMT);
        String apprSeqNo = sumr.getApprSeqNo();

        // Locate the caller's per-approver row and flip its status so the
        // per-row "X/Y" badge on the list (count of apprvr_stts='A') stays
        // in sync with the master APPR_STTS. Mirrors
        // ApprovalBoxService.applyApprove / applyReject — without this
        // step the master flips to 'C'/'X' but every EXPT_RESOL_APPRVR_INFM
        // row stays at 'P'/'W', producing the "Approved 0/1" badge bug.
        ExptResolApprvrInfm callerRow = exptResolApprvrInfmRepository
                .findById(new com.kosign.wecms.domain.payment.approvalbox.entity
                        .ExptResolApprvrInfmId(bizKey, apprSeqNo, userId))
                .orElseThrow(() -> new BusinessException("WBE2051"));
        // Out-of-order guard — mirrors ApprovalBoxService.loadForAction so the
        // Overseas path enforces the same legacy rule as the other modules:
        // only the current PENDING approver may approve/reject. A WAITING
        // ('W') or already-acted ('A'/'R'/'S') row is rejected, so a later
        // approver can never act before the earlier approvers do.
        if (!"P".equals(callerRow.getApprvrStts())) {
            throw new BusinessException("WBE2052");
        }
        callerRow.setApprvrStts(req.isApprove() ? "A" : "R");
        callerRow.setApprDtm(nowDtm);
        callerRow.setModId(userId);
        callerRow.setModDtm(nowDtm);
        exptResolApprvrInfmRepository.save(callerRow);

        // Decide the master's terminal status. Approve only finalises to
        // 'C' once no pending + waiting peers remain; otherwise the master
        // stays in 'I' (in-progress) and the next waiting peer is promoted
        // to PENDING. Reject is always terminal.
        String apprTypeCd;
        if (req.isApprove()) {
            long pendingRemaining = exptResolApprvrInfmRepository
                    .countByStatus(bizKey, apprSeqNo, "P");
            long waitingRemaining = exptResolApprvrInfmRepository
                    .countByStatus(bizKey, apprSeqNo, "W");
            if (pendingRemaining == 0 && waitingRemaining == 0) {
                apprTypeCd = "01";
            } else {
                apprTypeCd = "03";
                // Promote the next WAITING approver (by line_no) to PENDING.
                List<ExptResolApprvrInfm> chain = exptResolApprvrInfmRepository
                        .findChain(bizKey, apprSeqNo);
                for (ExptResolApprvrInfm a : chain) {
                    if ("W".equals(a.getApprvrStts())) {
                        a.setApprvrStts("P");
                        a.setModId(userId);
                        a.setModDtm(nowDtm);
                        exptResolApprvrInfmRepository.save(a);
                        break;
                    }
                }
            }
        } else {
            apprTypeCd = "02";
            // Downstream cascade: every WAITING peer → 'S' (stopped).
            exptResolApprvrInfmRepository.stopDownstream(
                    bizKey, apprSeqNo, userId, nowDtm);
        }

        // OVRS_PYMN_SUMR carries no APPR_STTS column (JEX parity) — the batch
        // status derives from EXPT_RESOL_MSTR.APPR_TYPE_CD written just below.
        sumr.setModDtm(nowDtm);
        sumr.setModId(userId);
        sumrRepository.save(sumr);

        // Keep EXPT_RESOL_MSTR.APPR_TYPE_CD in sync so the list filter and
        // the status badge both derive from the same authoritative column.
        final String finalApprTypeCd = apprTypeCd;
        exptResolMstrRepository.findById(
                new com.kosign.wecms.domain.payment.approvalbox.entity.ExptResolMstrId(bizKey, apprSeqNo))
                .ifPresent(mstr -> {
                    mstr.setApprTypeCd(finalApprTypeCd);
                    mstr.setModId(userId);
                    mstr.setModDtm(nowDtm);
                    exptResolMstrRepository.save(mstr);
                });

        String detailKey = req.isApprove()
                ? "audit.overseas_payment.approve.detail"
                : "audit.overseas_payment.reject.detail";
        auditLogService.append(AuditLogContext.builder()
                .wsvcId(WSVC_ID)
                .refNo(ovrsPymnNo)
                .refNo1(sumr.getApprSeqNo())
                .activityKey("audit.overseas_payment.activity")
                .detailKey(detailKey)
                .build());

        log.info("feature=overseas-payment approval-action bizKey={} ovrsPymnNo={} action={} apprSeqNo={}",
                bizKey, ovrsPymnNo, req.action(), sumr.getApprSeqNo());
    }

    /**
     * Execute the "Transfer" dispatch flow for an approved Overseas batch.
     * Mirrors legacy {@code pymn_0007_001_c001_act.jsp:100-456} for the
     * type='S' (Summary/batch) path. The type='M' (single beneficiary)
     * path lands when the Overseas Master UI is ported.
     *
     * <p>Sequence:
     * <ol>
     *   <li>Resolve master + verify it's the current tenant's row.</li>
     *   <li>Reject when {@code TRNF_YN='Y'} ({@code WBE2064} — already
     *       dispatched).</li>
     *   <li>Reject when {@code APPR_SEQ_NO} is null or
     *       {@code APPR_STTS != 'C'} (must be approved first).</li>
     *   <li>Load all detail lines via the OvrsPymnSumrDetl repo; reject if
     *       empty.</li>
     *   <li>Delegate to {@link OverseasDispatchExecutor#execute} which
     *       writes {@code TB_PAY_FILE_MST} + one {@code TB_PAY_FILE_DATA}
     *       per line and stamps shadow keys on the master + each line.</li>
     *   <li>Stamp {@code TRNF_YN='Y'}, {@code TRNF_DT}=now, {@code MOD_DTM},
     *       {@code MOD_ID} on the master.</li>
     *   <li>Append an audit row.</li>
     * </ol>
     *
     * <p>The bank-network integration (SWIFT callback flipping
     * {@code PROC_STAT} to '0'/'3') is handled by the live JEX dispatch
     * daemon — refactor R&D is local-only.
     */
    @Transactional
    public void dispatch(String ovrsPymnNo) {
        String bizKey = TenantContext.currentBizKey();
        String userId = TenantContext.currentUserId();
        String nowDtm = ZonedDateTime.now(VIETNAM_ZONE).format(DTM_FMT);

        // L — M-mode (legacy OVRS_PYMN_TYPE='M'): when an OVRS_PYMN_MSTR row exists for this
        // ovrsPymnNo, dispatch the single-beneficiary master (legacy pymn_0007 M-mode path).
        // Otherwise fall through to the S-mode summary path below, byte-unchanged. M and S
        // partition the ovrsPymnNo namespace by OVRS_PYMN_TYPE, so at most one table matches.
        Optional<OvrsPymnMstr> mstrOpt = ovrsPymnMstrRepository.findByBizKeyAndOvrsPymnNo(bizKey, ovrsPymnNo);
        if (mstrOpt.isPresent()) {
            dispatchMaster(mstrOpt.get(), userId, nowDtm);
            return;
        }

        OvrsPymnSumr sumr = resolveSumr(bizKey, ovrsPymnNo);

        if ("Y".equals(sumr.getTrnfYn())) {
            throw new BusinessException("WBE2064");
        }
        if (sumr.getApprSeqNo() == null || sumr.getApprSeqNo().isBlank()) {
            throw new BusinessException("WBE2063");
        }
        if (!"C".equals(deriveApprStts(sumr))) {
            // Approval-action must have flipped the batch to approved ('C') first.
            throw new BusinessException("WBE2063");
        }

        List<OvrsPymnSumrDetl> lines = dtlsRepository
                .findByBizKeyAndOvrsPymnNoOrderByOvrsPymnDetlNoAsc(bizKey, sumr.getOvrsPymnNo());
        if (lines.isEmpty()) {
            throw new BusinessException("WBE2061");
        }

        // Cut-off time guard — legacy parity (PymnHelper.validateTransferTiming,
        // pymn_0007_001_c001_act.jsp:140). Overseas always uses the SWIFT
        // network so weekend dispatches are blocked, and any configured
        // per-(bank, SWIFT) cutoff window is enforced. Unknown banks /
        // missing windows silently pass — matching the SCDUL modules.
        cutoffValidator.validate(
                sumr.getOutBankCd(),
                com.kosign.wecms.domain.payment.internaltransfer.service.TransferNetwork.SWIFT);

        // Writes TB_PAY_FILE_MST + TB_PAY_FILE_DATA inside this
        // @Transactional. Stamps PAY_TRAN_DT + PAY_FILE_SEQ_NO on master
        // and PAY_TRAN_DT + PAY_TRAN_SEQ on each detail.
        dispatchExecutor.execute(sumr, lines);

        sumr.setTrnfYn("Y");
        sumr.setTrnfDt(nowDtm);
        sumr.setModDtm(nowDtm);
        sumr.setModId(userId);
        sumrRepository.save(sumr);

        // Lifecycle signal — dispatched / processing (HAENGSUNG Oracle write-back).
        eventPublisher.publishEvent(new com.kosign.wecms.domain.payment.approvalbox.event
                .PaymentApprovalStatusChangedEvent(sumr.getBizKey(), sumr.getApprSeqNo(),
                com.kosign.wecms.domain.payment.approvalbox.event.PaymentApprovalStatus.PROCESSING));

        auditLogService.append(AuditLogContext.builder()
                .wsvcId(WSVC_ID)
                .refNo(sumr.getOvrsPymnNo())
                .refNo1(sumr.getApprSeqNo())
                .activityKey("audit.overseas_payment.activity")
                .detailKey("audit.overseas_payment.dispatch.detail")
                .build());

        log.info("feature=overseas-payment dispatch bizKey={} ovrsPymnNo={} lines={} ccyCd={}",
                bizKey, sumr.getOvrsPymnNo(), lines.size(), sumr.getCcyCd());
    }

    /**
     * L — M-mode dispatch (legacy {@code pymn_0007} {@code OVRS_PYMN_TYPE='M'}, single
     * {@code OVRS_PYMN_MSTR}). Same gate + write-back shape as the S-mode {@link #dispatch}
     * (not-yet-dispatched WBE2064; approved — apprSeqNo present + status 'C', WBE2063; SWIFT
     * cut-off), then the single-row {@link OverseasDispatchExecutor#execute(OvrsPymnMstr)} +
     * {@code TRNF_YN='Y'} stamp + lifecycle event + audit. Does not touch the S-mode path.
     */
    private void dispatchMaster(OvrsPymnMstr mstr, String userId, String nowDtm) {
        if ("Y".equals(mstr.getTrnfYn())) {
            throw new BusinessException("WBE2064");
        }
        if (mstr.getApprSeqNo() == null || mstr.getApprSeqNo().isBlank()) {
            throw new BusinessException("WBE2063");
        }
        if (!"C".equals(deriveApprStts(mstr.getBizKey(), mstr.getApprSeqNo()))) {
            throw new BusinessException("WBE2063");
        }

        // Cut-off (SWIFT) — legacy parity; the master's OUT_BANK_CD is DBT_ACCT_CD.
        cutoffValidator.validate(
                mstr.getDbtAcctCd(),
                com.kosign.wecms.domain.payment.internaltransfer.service.TransferNetwork.SWIFT);

        dispatchExecutor.execute(mstr);

        mstr.setTrnfYn("Y");
        mstr.setModDtm(nowDtm);
        mstr.setModId(userId);
        ovrsPymnMstrRepository.save(mstr);

        eventPublisher.publishEvent(new com.kosign.wecms.domain.payment.approvalbox.event
                .PaymentApprovalStatusChangedEvent(mstr.getBizKey(), mstr.getApprSeqNo(),
                com.kosign.wecms.domain.payment.approvalbox.event.PaymentApprovalStatus.PROCESSING));

        auditLogService.append(AuditLogContext.builder()
                .wsvcId(WSVC_ID)
                .refNo(mstr.getOvrsPymnNo())
                .refNo1(mstr.getApprSeqNo())
                .activityKey("audit.overseas_payment.activity")
                .detailKey("audit.overseas_payment.dispatch.detail")
                .build());

        log.info("feature=overseas-payment dispatch(M) bizKey={} ovrsPymnNo={} outBankCd={}",
                mstr.getBizKey(), mstr.getOvrsPymnNo(), mstr.getDbtAcctCd());
    }

    /**
     * Saves the selected reporting account on a single detail line.
     * Mirrors the legacy inline account-picker save in the scheduled-payment
     * detail dialog ({@code pymn_0007_001_u001} → patches CHART_ACCT_SEQ_NO +
     * CHART_ACCT_CD together).
     *
     * <p>Both {@code CHART_ACCT_SEQ_NO} and {@code CHART_ACCT_CD} are persisted
     * as a pair: the seq_no is the {@code CHART_ACCT_INFM} PK identity and the
     * legacy name-resolver functions key on it, so writing only the code would
     * leave a stale seq_no and mis-render the account in legacy / shared-DB
     * screens (migration-safety + coexistence parity).
     */
    @Transactional
    public void saveReportingAccount(String ovrsPymnNo, String ovrsPymnDetlNo,
                                     String rprtAcctCd, String rprtAcctSeqNo) {
        String bizKey = TenantContext.currentBizKey();
        // Resolve via doc_no fallback so URL like VO_260521_003 works
        String resolvedNo = resolveSumr(bizKey, ovrsPymnNo).getOvrsPymnNo();
        OvrsPymnSumrDetl dtl = dtlsRepository
                .findByBizKeyAndOvrsPymnNoAndOvrsPymnDetlNo(bizKey, resolvedNo, ovrsPymnDetlNo)
                .orElseThrow(() -> new BusinessException("WBE2045",
                        "detail line not found: " + ovrsPymnNo + " / " + ovrsPymnDetlNo));

        String code  = (rprtAcctCd == null || rprtAcctCd.isBlank()) ? null : rprtAcctCd.trim();
        String seqNo = (rprtAcctSeqNo == null || rprtAcctSeqNo.isBlank()) ? null : rprtAcctSeqNo.trim();
        dtl.setRprtAcctCd(code);
        dtl.setChartAcctSeqNo(seqNo);
        dtlsRepository.save(dtl);

        log.info("feature=overseas-payment reporting-account bizKey={} ovrsPymnNo={} ovrsPymnDetlNo={} rprtAcctCd={} rprtAcctSeqNo={}",
                bizKey, ovrsPymnNo, ovrsPymnDetlNo, code, seqNo);
    }

    /**
     * Resolves an {@code OvrsPymnSumr} by either its internal {@code OVRS_PYMN_NO}
     * or the user-visible {@code DOC_NO} (e.g. {@code VO_260521_003}).
     * Throws {@code WBE2045} when the id is unknown or belongs to a different tenant.
     */
    private OvrsPymnSumr resolveSumr(String bizKey, String id) {
        OvrsPymnSumr sumr = sumrRepository.findByBizKeyAndOvrsPymnNo(bizKey, id).orElse(null);
        if (sumr == null) {
            sumr = exptResolMstrRepository.findByBizKeyAndDocNoAndDelYn(bizKey, id, "N")
                    .filter(m -> m.getApprSeqNo() != null)
                    .flatMap(m -> sumrRepository.findByBizKeyAndApprSeqNo(bizKey, m.getApprSeqNo()))
                    .orElseThrow(() -> new BusinessException("WBE2045",
                            "overseas payment not found: " + id));
        }
        return sumr;
    }

    /** Per-tenant MAX+1 across both EXPT_RESOL_MSTR and OVRS_PYMN_SUMR so the
     *  newly issued APPR_SEQ_NO is above any Phase-A-only legacy rows. */
    private String allocateApprSeqNo(String bizKey) {
        // APPR_SEQ_NO via the single wb_get_next_val funnel (V140 #1 — the GLOBAL
        // sq_appr_seq_no sequence shared across every Payment module, so the id
        // space stays contiguous and never collides with the live JEX / WABOOKS
        // systems). Replaces the per-tenant cross-table MAX+1 ceiling.
        return legacyFunctions.nextApprSeqNo(bizKey);
    }

    /**
     * Derives the batch approval status (P/C/X/I) from the authoritative
     * {@code EXPT_RESOL_MSTR.APPR_TYPE_CD} column. {@code OVRS_PYMN_SUMR} carries
     * no {@code APPR_STTS} column in legacy JEX, so the status is reconstructed
     * here — keeping the entity column-for-column with the JEX schema. Returns
     * {@code null} when the batch has no linked approval request.
     */
    private String deriveApprStts(OvrsPymnSumr sumr) {
        return deriveApprStts(sumr.getBizKey(), sumr.getApprSeqNo());
    }

    /** L — apprSeqNo-based approval-status derivation, shared by the S-mode {@code OvrsPymnSumr}
     *  and the M-mode {@code OvrsPymnMstr} dispatch gate ({@code EXPT_RESOL_MSTR} is the common
     *  approval table, so both types resolve status identically). */
    private String deriveApprStts(String bizKey, String apprSeqNo) {
        if (apprSeqNo == null || apprSeqNo.isBlank()) {
            return null;
        }
        return exptResolMstrRepository.findById(
                        new com.kosign.wecms.domain.payment.approvalbox.entity.ExptResolMstrId(
                                bizKey, apprSeqNo))
                .map(m -> mapApprTypeCd(m.getApprTypeCd()))
                .orElse("P");
    }

    /**
     * Maps {@code EXPT_RESOL_MSTR.APPR_TYPE_CD} to the legacy P/C/X/I status
     * vocabulary the overseas-payment flow guards on. Mirrors the {@code CASE}
     * in {@code OverseasPaymentRepository} so the list query and the service
     * derive identical values. {@code "00"} (Pending tab) and any unknown / null
     * value fall back to pending.
     */
    private static String mapApprTypeCd(String apprTypeCd) {
        if (apprTypeCd == null) {
            return "P";
        }
        return switch (apprTypeCd) {
            case "01" -> "C";
            case "02" -> "X";
            case "03" -> "I";
            default   -> "P";
        };
    }

    private String stampApproval(OvrsPymnSumr sumr, String apprSeqNo, RequestApprovalRequest req) {
        String bizKey  = sumr.getBizKey();
        String userId  = TenantContext.currentUserId();
        String userNm  = TenantContext.currentUserNm();
        String nowDtm  = ZonedDateTime.now(VIETNAM_ZONE).format(DTM_FMT);
        // yyyyMMdd and HHmmss slices of the 14-char timestamp
        String draftDate = nowDtm.substring(0, 8);
        String draftTime = nowDtm.substring(8, 14);

        // 1. Stamp OVRS_PYMN_SUMR
        sumr.setApprSeqNo(apprSeqNo);
        // Status is "P" (pending) by derivation — the EXPT_RESOL_MSTR row written
        // below carries APPR_TYPE_CD='00' which deriveApprStts maps to pending.
        if (req.postTitle() != null && !req.postTitle().isBlank()) {
            sumr.setTitl(req.postTitle().trim());
        }
        // H-1 (legacy parity, comm_0192_001_c005_act.jsp:404-410
        // updateOverseasPaymentSummary): persist the withdrawal account the user
        // selected in the Request Approval popup. OUT_BANK_CD is the
        // NAPAS-converted code — OutBankCdNormalizer reproduces the legacy
        // NapasBankInfoDAO "bank_cd = ? OR napas_bank_cd = ?" lookup.
        if (req.bankCd() != null && !req.bankCd().isBlank()) {
            sumr.setOutBankCd(outBankCdNormalizer.normalize(req.bankCd()));
        }
        if (req.acctNo() != null && !req.acctNo().isBlank()) {
            sumr.setWdrlActNo(req.acctNo().trim());
        }
        if (req.ccyCd() != null && !req.ccyCd().isBlank()) {
            sumr.setWdrlCcyCd(req.ccyCd().trim());
        }
        sumr.setModDtm(nowDtm);
        sumr.setModId(userId);
        sumrRepository.save(sumr);

        // 2. Write EXPT_RESOL_MSTR master row
        ExptResolMstr mstr = new ExptResolMstr();
        mstr.setBizKey(bizKey);
        mstr.setApprSeqNo(apprSeqNo);
        mstr.setApprDv(ApprDv.OVERSEAS.code());     // "34"
        // F-4 (legacy parity): legacy comm_0192_001_c005 sets ApprSts.STS_2
        // ("2" = In Progress) at request time, not "0" (temp save).
        mstr.setApprSts("2");
        mstr.setDraftDate(draftDate);
        mstr.setDraftTime(draftTime);
        mstr.setDraftUserId(userId != null ? userId : "");
        mstr.setDraftUserNm(userNm != null ? userNm : "");
        // Legacy NOT NULL display columns — values match DEV data for appr_dv='34' rows
        mstr.setDraftUserSiteCd(bizKey);
        String cmpnNm = busiInfmRepository.findById(bizKey)
                .map(b -> b.getCmpnNm() != null ? b.getCmpnNm() : bizKey)
                .orElse(bizKey);
        mstr.setDraftUserSiteNm(cmpnNm);
        mstr.setHandRegYn("Y");
        // M-2 (legacy parity): display flags from BUSI_ENV_SET_INFM (00014/16/17/18),
        // not hardcoded. Mirrors comm_0192_001_c005_act.jsp:316-320.
        var flags = displayFlagsResolver.resolve(bizKey);
        mstr.setApprBoxUseYn(flags.apprBoxUseYn());
        mstr.setApprDatDispYn(flags.apprDatDispYn());
        mstr.setCmpnLogoDispYn(flags.cmpnLogoDispYn());
        mstr.setRegDispYn(flags.regDispYn());
        mstr.setTitl(req.postTitle() != null ? req.postTitle().trim() : "");
        // DOC_NO follows the VO_YYMMDD_NNN pattern (mirrors ReslHelper.getDocNo /
        // wb_get_next_val('expt_resol_mstr','doc_no',bizKey) in V25 migration).
        String docNo = docNoGenerator.nextDocNo(bizKey);
        mstr.setDocNo(docNo);
        mstr.setDocNm(ApprDv.OVERSEAS.docNm());   // L-1 (legacy parity): legacy DOC_NM = apprDv.getLabel()
        // Store amount in the column that matches the payment currency.
        // Column convention (3 currencies supported):
        //   VND → TOT_PAY_KHR_AMT (VN-tenant legacy alias) + TOT_PAY_VND_AMT
        //   USD → TOT_PAY_USD_AMT
        //   KRW → TOT_PAY_KHR_AMT (no dedicated KRW column; KHR column used for non-USD foreign)
        BigDecimal amt = sumr.getTotalTranAmt() != null ? sumr.getTotalTranAmt() : BigDecimal.ZERO;
        String ccy = sumr.getCcyCd() != null ? sumr.getCcyCd().toUpperCase() : "VND";
        boolean isUsd = "USD".equals(ccy);
        boolean isVnd = "VND".equals(ccy);
        mstr.setTotPayKhrAmt(isUsd ? BigDecimal.ZERO : amt);   // VND + KRW
        mstr.setTotPayUsdAmt(isUsd ? amt : BigDecimal.ZERO);   // USD only
        mstr.setTotPayVndAmt(isVnd ? amt : BigDecimal.ZERO);   // VND only
        mstr.setPayDate(req.payDate() != null && !req.payDate().isBlank() ? req.payDate().trim() : null);
        mstr.setDelYn("N");
        mstr.setPayEndYn("N");
        mstr.setApprTypeCd("00");   // Pending tab
        mstr.setRegId(userId != null ? userId : "");
        mstr.setRegDtm(nowDtm);
        mstr.setModId(userId);
        mstr.setModDtm(nowDtm);
        exptResolMstrRepository.save(mstr);

        // 3. Write EXPT_RESOL_APPRVR_INFM rows — first approver is "P" (active),
        //    subsequent approvers are "W" (waiting for their turn)
        List<RequestApprovalRequest.ApproverInput> approvers = req.approvers();
        for (int i = 0; i < approvers.size(); i++) {
            RequestApprovalRequest.ApproverInput ap = approvers.get(i);
            ExptResolApprvrInfm row = new ExptResolApprvrInfm();
            row.setBizKey(bizKey);
            row.setApprSeqNo(apprSeqNo);
            row.setApprvrId(ap.apprvrId());
            row.setApprvrStts(i == 0 ? "P" : "W");
            row.setApprvrNm(ap.apprvrNm());
            row.setApprDtm(null);
            row.setRegId(userId);
            row.setRegDtm(nowDtm);
            row.setModId(userId != null ? userId : "");
            row.setModDtm(nowDtm);
            row.setReadYn("N");
            row.setApprvrLineNo(BigDecimal.valueOf(i + 1));
            row.setApprLineNo(BigDecimal.valueOf(req.apprLineNo()));
            exptResolApprvrInfmRepository.save(row);
        }

        // F-1 (legacy parity): EXPT_RESOL_DETL row — DOC_ID = OVRS_PYMN_NO,
        // AMT = batch total, CCY_CD = batch currency. Mirrors legacy
        // comm_0192_001_c005_act.jsp:380 createExptResolDetl_ (single
        // non-"M" overseas path; the "M" multi-detail branch stays out of
        // Phase 1 scope — F-7).
        approvalBoxFanoutService.writeResolutionDetail(
                apprSeqNo, sumr.getOvrsPymnNo(), sumr.getTotalTranAmt(), sumr.getCcyCd());
        // F-2 (legacy parity): EXPT_RESOL_APPR_LINE print-header snapshot
        // (env-gated). Mirrors comm_0192_001_c005_act.jsp:489 createApprovalLines.
        approvalBoxFanoutService.writeApprovalLineSnapshot(apprSeqNo);

        // Bulletin / notification fan-out (legacy createBulletinPost +
        // createUserNewPostNotifications + BltbHelper.createNotificationToApprvr).
        approvalNotificationService.notifyRequestApproval(
                apprSeqNo, req.postTitle(), req.postCont(), "34", "Oversea Payment");

        return docNo;
    }

    // ─── change approval line ────────────────────────────────────────────────

    /**
     * Replaces the approver chain on a pending overseas payment batch.
     * Deletes existing {@code EXPT_RESOL_APPRVR_INFM} rows and inserts the
     * new chain from the selected approval line. Only allowed when
     * {@code APPR_STTS = 'P'} (pending — first approver has not yet acted).
     *
     * @throws BusinessException {@code WBE2045} — batch not found
     * @throws BusinessException {@code WBE2128} — batch is not in pending state
     */
    @Transactional
    public void changeApprovalLine(String ovrsPymnNo, ChangeApprovalLineRequest req) {
        String bizKey = TenantContext.currentBizKey();
        OvrsPymnSumr sumr = resolveSumr(bizKey, ovrsPymnNo);

        if (!"P".equals(deriveApprStts(sumr))) {
            throw new BusinessException("WBE2128");
        }
        if ("Y".equals(sumr.getTrnfYn())) {
            throw new BusinessException("WBE2127");
        }

        String apprSeqNo = sumr.getApprSeqNo();
        String userId    = TenantContext.currentUserId();
        String nowDtm    = ZonedDateTime.now(VIETNAM_ZONE).format(DTM_FMT);

        // Replace approver chain
        List<ExptResolApprvrInfm> existing = exptResolApprvrInfmRepository.findChain(bizKey, apprSeqNo);
        exptResolApprvrInfmRepository.deleteAll(existing);
        exptResolApprvrInfmRepository.flush();

        List<ChangeApprovalLineRequest.ApproverInput> approvers = req.approvers();
        for (int i = 0; i < approvers.size(); i++) {
            ChangeApprovalLineRequest.ApproverInput ap = approvers.get(i);
            ExptResolApprvrInfm row = new ExptResolApprvrInfm();
            row.setBizKey(bizKey);
            row.setApprSeqNo(apprSeqNo);
            row.setApprvrId(ap.apprvrId());
            row.setApprvrStts(i == 0 ? "P" : "W");
            row.setApprvrNm(ap.apprvrNm());
            row.setApprDtm(null);
            row.setRegId(userId);
            row.setRegDtm(nowDtm);
            row.setModId(userId != null ? userId : "");
            row.setModDtm(nowDtm);
            row.setReadYn("N");
            row.setApprvrLineNo(java.math.BigDecimal.valueOf(i + 1));
            row.setApprLineNo(java.math.BigDecimal.valueOf(req.apprLineNo()));
            exptResolApprvrInfmRepository.save(row);
        }

        // Touch mod timestamp on master rows
        exptResolMstrRepository.findById(
                new com.kosign.wecms.domain.payment.approvalbox.entity.ExptResolMstrId(bizKey, apprSeqNo))
                .ifPresent(mstr -> {
                    mstr.setModId(userId != null ? userId : "");
                    mstr.setModDtm(nowDtm);
                    exptResolMstrRepository.save(mstr);
                });

        sumr.setModId(userId != null ? userId : "");
        sumr.setModDtm(nowDtm);
        sumrRepository.save(sumr);

        log.info("feature=overseas-payment change-approval-line bizKey={} ovrsPymnNo={} apprSeqNo={} apprLineNo={} approvers={}",
                bizKey, ovrsPymnNo, apprSeqNo, req.apprLineNo(), approvers.size());
    }

    // ─── remove approval line ─────────────────────────────────────────────────

    /**
     * Retracts the pending approval request on an overseas payment batch.
     * Deletes {@code EXPT_RESOL_APPRVR_INFM} and {@code EXPT_RESOL_MSTR} rows,
     * then clears {@code APPR_SEQ_NO} and {@code APPR_STTS} on {@code OVRS_PYMN_SUMR}
     * so the batch returns to draft state. Only allowed when {@code APPR_STTS = 'P'}.
     *
     * @throws BusinessException {@code WBE2045} — batch not found
     * @throws BusinessException {@code WBE2098} — batch is not in pending state
     */
    @Transactional
    public void removeApprovalLine(String ovrsPymnNo) {
        String bizKey = TenantContext.currentBizKey();
        OvrsPymnSumr sumr = resolveSumr(bizKey, ovrsPymnNo);

        if (!"P".equals(deriveApprStts(sumr))) {
            throw new BusinessException("WBE2098");
        }
        if ("Y".equals(sumr.getTrnfYn())) {
            throw new BusinessException("WBE2127");
        }

        String apprSeqNo = sumr.getApprSeqNo();
        String userId    = TenantContext.currentUserId();
        String nowDtm    = ZonedDateTime.now(VIETNAM_ZONE).format(DTM_FMT);

        // Delete the full approval object graph child-first (EXPT_RESOL_
        // PAY_EVID_FILE / FILE+bulletin / DETL / APPR_LINE / APPRVR_INFM /
        // MSTR-last) through the shared helper — the EXPT_RESOL_DETL leg is
        // what keeps expt_resol_detl_fk01 satisfied. Mirrors legacy
        // ExptResolMstrDAO.deleteByPk (comm_0192_004_d001).
        exptResolMstrRepository.findById(
                new com.kosign.wecms.domain.payment.approvalbox.entity.ExptResolMstrId(bizKey, apprSeqNo))
                .ifPresent(mstr -> approvalBoxFanoutService.purgeApprovalGraph(bizKey, apprSeqNo, mstr));
        exptResolMstrRepository.flush();

        // Reset the batch to draft state — clearing APPR_SEQ_NO is the reset;
        // status derives to null once no EXPT_RESOL_MSTR row is linked.
        sumr.setApprSeqNo(null);
        sumr.setModId(userId != null ? userId : "");
        sumr.setModDtm(nowDtm);
        sumrRepository.save(sumr);

        auditLogService.append(AuditLogContext.builder()
                .wsvcId(WSVC_ID)
                .refNo(ovrsPymnNo)
                .refNo1(apprSeqNo)
                .activityKey("audit.overseas_payment.activity")
                .detailKey("audit.overseas_payment.remove_approval.detail")
                .build());

        log.info("feature=overseas-payment remove-approval-line bizKey={} ovrsPymnNo={} apprSeqNo={}",
                bizKey, ovrsPymnNo, apprSeqNo);
    }

    // ─── delete draft batch ───────────────────────────────────────────────────

    /**
     * Deletes a draft overseas payment batch (summary + detail rows).
     * Only allowed when the batch has never been submitted for approval
     * ({@code APPR_SEQ_NO} is null). Transferred batches ({@code TRNF_YN='Y'})
     * and pending/approved batches are blocked.
     *
     * @throws BusinessException {@code WBE2045} — batch not found
     * @throws BusinessException {@code WBE1063} — batch has already been submitted for approval
     *         (legacy delete-block code, shared across all 5 payment modules)
     * @throws BusinessException {@code WBE2127} — batch has already been transferred
     */
    @Transactional
    public void deleteDraftBatch(String ovrsPymnNo) {
        String bizKey = TenantContext.currentBizKey();
        OvrsPymnSumr sumr = sumrRepository.findByBizKeyAndOvrsPymnNo(bizKey, ovrsPymnNo)
                .orElseThrow(() -> new BusinessException("WBE2045",
                        "overseas payment not found: " + ovrsPymnNo));

        // Approval-requested batches are not deletable. Uses the legacy
        // delete-block code WBE1063 (pymn_0007_001_d001_act.jsp:83-85),
        // aligned with the 4 domestic modules (PO decision 2026-07-08).
        if (sumr.getApprSeqNo() != null && !sumr.getApprSeqNo().isBlank()) {
            throw new BusinessException("WBE1063");
        }
        if ("Y".equals(sumr.getTrnfYn())) {
            throw new BusinessException("WBE2127");
        }

        // G1 — snapshot the uploaded-file metadata BEFORE the row goes away so
        // the ERP-history writer can copy the Excel and reference it. Mirrors
        // legacy pymn_0007_001_d001_act.jsp:90 (erpTrnsHisDeleter, ErpType.DELETED)
        // and the four domestic scheduled modules (e.g. InternalTransferService.delete).
        String orgFileNm  = sumr.getOrgFileNm();
        String saveFileNm = sumr.getSaveFileNm();

        dtlsRepository.deleteByBizKeyAndOvrsPymnNo(bizKey, ovrsPymnNo);
        dtlsRepository.flush();
        sumrRepository.delete(sumr);
        sumrRepository.flush();

        // Preserve the uploaded Excel in ERP Transaction History so the user can
        // still download it after the source batch is gone. TRNS_TYPE_CD = "13"
        // (Overseas Payment Request / Overseas Scheduled Payment); the writer
        // stamps ERP_TYPE='D' so the row lands in the "Deleted" tab. The writer
        // is REQUIRES_NEW + swallows its own errors, so an ERP audit-write failure
        // cannot roll back the delete. No-op when the batch has no uploaded file.
        if (saveFileNm != null && !saveFileNm.isBlank()) {
            try {
                java.nio.file.Path source = fileResolver.resolveForRead(bizKey, saveFileNm);
                erpHistoryWriter.recordDeletedFile(
                        ERP_TRNS_TYPE_OVERSEAS_SCHEDULED,
                        source,
                        orgFileNm);
            } catch (RuntimeException ex) {
                // Defence in depth — resolveForRead throws only on a malformed
                // saveFileNm that fails the path-composition / traversal guard.
                // The writer's own try/catch covers everything past that.
                log.warn(
                        "feature=overseas-payment ERP history skip "
                      + "(path resolver rejected) bizKey={} ovrsPymnNo={} saveFileNm={}",
                        bizKey, ovrsPymnNo, saveFileNm, ex);
            }
        }

        log.info("feature=overseas-payment delete-draft bizKey={} ovrsPymnNo={}", bizKey, ovrsPymnNo);
    }

    // ─── mappers ──────────────────────────────────────────────────────────────

    private OverseasScheduledRow mapRow(Object[] r, Map<String, List<Object[]>> approversBySeq,
                                        Map<String, String> imgUrlByApprvrId) {
        String apprSeqNo = (String) r[11];

        List<OverseasApprover> approvers = apprSeqNo == null
                ? List.of()
                : approversBySeq.getOrDefault(apprSeqNo, List.of())
                        .stream()
                        .map(a -> mapApprover(a, imgUrlByApprvrId))
                        .collect(Collectors.toList());

        Number apprvrTotCnt     = (Number) r[24];
        Number apprvrApprTotCnt = (Number) r[25];

        return new OverseasScheduledRow(
                toStr(r[2]),                                           // ovrsPymnNo
                toStr(r[6]),                                           // ccyCd
                toStr(r[3]),                                           // ovrsPymnTypeCd
                toStr(r[5]),                                           // titl
                apprSeqNo,                                             // apprSeqNo
                toBigDecimal(r[7]),                                    // totalTranAmt
                toInt(r[8]),                                           // totTrnsCnt
                toStr(r[9]),                                           // regDtm
                toStr(r[12]),                                          // draftUserId
                toStr(r[13]),                                          // draftUserNm
                toStr(r[15]),                                          // apprStts
                toStr(r[17]),                                          // trnfYn (CHAR(1) → Character)
                apprvrApprTotCnt != null ? apprvrApprTotCnt.intValue() : 0,
                apprvrTotCnt     != null ? apprvrTotCnt.intValue()     : 0,
                toStr(r[18]),                                          // apprDv
                toStr(r[14]),                                          // docNo
                null,                                                  // fileUrl (not in base query for R&D)
                toStr(r[19]),                                          // outBankCd
                toStr(r[21]),                                          // outBankNm
                toStr(r[20]),                                          // wdrlActNo
                toStr(r[22]),                                          // bnfcNm
                toStr(r[1]),                                           // ovrsPymnType ('E'/'M')
                toStr(r[23]),                                          // payDate
                toStr(r[4]),                                           // fileNm
                toStr(r[10]),                                          // regId
                approvers
        );
    }

    private OverseasApprover mapApprover(Object[] r, Map<String, String> imgUrlByApprvrId) {
        String apprvrId = toStr(r[2]);
        return new OverseasApprover(
                apprvrId,     // apprvrId
                toStr(r[3]),  // apprvrNm
                toStr(r[4]),  // apprvrStts (CHAR(1))
                toStr(r[1]),  // apprvrLineNo
                toStr(r[5]),  // modDtm
                // Shared, non-admin avatar URL (/api/v1/users/{id}/image) for
                // approvers WITH a stored image; null otherwise (the query's
                // r[6] reg_img_url column stays NULL — resolved in Java here).
                imgUrlByApprvrId.get(apprvrId)   // regImgUrl
        );
    }

    /** Collect every approver id across the page's chains and batch-resolve
     *  their avatar URLs in one query via the shared resolver. */
    private Map<String, String> resolveApproverImageUrls(
            String bizKey, Map<String, List<Object[]>> approversBySeq) {
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (List<Object[]> chain : approversBySeq.values()) {
            for (Object[] a : chain) {
                String id = toStr(a[2]);
                if (id != null && !id.isBlank()) ids.add(id);
            }
        }
        return userImageUrlResolver.resolveImageUrls(bizKey, ids);
    }

    private OverseasCurrencyTotal mapTotal(Object[] r) {
        return new OverseasCurrencyTotal(
                (String) r[0],   // ccyCd
                toBigDecimal(r[1]),  // totlAmt
                toInt(r[2]),         // totCntCcyCd (row count)
                toInt(r[3])          // totTrnsCnt
        );
    }

    /** Maps a result-list query row (see {@link com.kosign.wecms.domain.payment.repository.OverseasPaymentRepository#buildResultSql}). */
    private OverseasResultRow mapResultRow(Object[] r) {
        return new OverseasResultRow(
                toStr(r[0]),         // ovrsPymnNo
                toStr(r[1]),         // ccyCd
                toStr(r[2]),         // fileNm
                toBigDecimal(r[3]),  // totalTranAmt
                toInt(r[4]),         // totTrnsCnt
                toStr(r[5]),         // regDtm
                toStr(r[6]),         // draftUserNm
                toStr(r[7]),         // regId
                toStr(r[8]),         // outBankNm
                toStr(r[9]),         // outBankCd
                toStr(r[10]),        // wdrlActNo
                toStr(r[11]),        // trnfYn (CHAR(1) → Character)
                toStr(r[12]),        // docNo
                toStr(r[13]),        // titl (approval title)
                toInt(r[18]),        // successCount — F2 (PROC_STAT='0' AND RS_CD='0000')
                toInt(r[19]),        // failedCount  — F2 (PROC_STAT='3' or '0'+RS_CD<>'0000')
                toStr(r[14]),        // payDate
                toStr(r[15]),        // apprStts (appr_type_cd)
                toStr(r[16]),        // apprvrNm (NULL — complex join deferred)
                toStr(r[17]),        // apprSeqNo (F4b — OVRS_PYMN_SUMR.APPR_SEQ_NO)
                toStr(r[21]),        // trnfStts (computed F/P/C — F2)
                toInt(r[20])         // prsCnt — F2 (tot - error - success)
        );
    }

    // ─── utilities ────────────────────────────────────────────────────────────

    private int resolvePageSize(int requested) {
        if (requested <= 0) return DEFAULT_PAGE_SIZE;
        return Math.min(requested, MAX_PAGE_SIZE);
    }

    /**
     * Escapes special LIKE metacharacters in user input and wraps with %
     * to produce an ILIKE pattern. Mirrors the JEX escaping convention
     * (escape char = '!'). Returns null when keyword is blank.
     */
    private String buildLikePattern(String keyword) {
        if (keyword == null || keyword.isBlank()) return null;
        String escaped = keyword
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
        return "%" + escaped.toLowerCase() + "%";
    }

    private BigDecimal toBigDecimal(Object val) {
        if (val == null) return BigDecimal.ZERO;
        if (val instanceof BigDecimal bd) return bd;
        return new BigDecimal(val.toString());
    }

    private int toInt(Object val) {
        if (val == null) return 0;
        if (val instanceof Number n) return n.intValue();
        return Integer.parseInt(val.toString());
    }

    /** Handles CHAR(1) columns that JDBC/PostgreSQL returns as Character, not String. */
    private String toStr(Object val) {
        if (val == null) return null;
        return val.toString();
    }

    private String buildOrderBy(OverseasScheduledSortField field, String direction, String fallback) {
        if (field == null) return fallback;
        String dir = "DESC".equalsIgnoreCase(direction) ? "DESC" : "ASC";
        return field.sqlAlias() + " " + dir;
    }

    private String buildOrderBy(OverseasResultSortField field, String direction, String fallback) {
        if (field == null) return fallback;
        String dir = "DESC".equalsIgnoreCase(direction) ? "DESC" : "ASC";
        return field.sqlExpr() + " " + dir;
    }
}
