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
import com.kosign.wecms.domain.payment.dto.ChangeApprovalLineRequest;
import com.kosign.wecms.domain.payment.dto.OverseasApprover;
import com.kosign.wecms.domain.payment.dto.OverseasCurrencyTotal;
import com.kosign.wecms.domain.payment.dto.OverseasPaymentDetailResponse;
import com.kosign.wecms.domain.payment.dto.OverseasPaymentDetailRowResponse;
import com.kosign.wecms.domain.payment.dto.OverseasResultListResponse;
import com.kosign.wecms.domain.payment.dto.OverseasResultRow;
import com.kosign.wecms.domain.payment.dto.OverseasScheduledListResponse;
import com.kosign.wecms.domain.payment.dto.OverseasScheduledRow;
import com.kosign.wecms.domain.payment.entity.OvrsPymnDtls;
import com.kosign.wecms.domain.payment.entity.OvrsPymnSumr;
import com.kosign.wecms.domain.payment.internaltransfer.dto.ApprovalActionRequest;
import com.kosign.wecms.domain.payment.internaltransfer.dto.RequestApprovalRequest;
import com.kosign.wecms.domain.payment.internaltransfer.dto.RequestApprovalResponse;
import com.kosign.wecms.domain.payment.overseas.repository.OvrsPymnDtlsRepository;
import com.kosign.wecms.domain.payment.overseas.repository.OvrsPymnSumrRepository;
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

    private final OverseasPaymentRepository      paymentRepo;
    private final OverseasApproverRepository     approverRepo;
    private final OvrsPymnSumrRepository         sumrRepository;
    private final OvrsPymnDtlsRepository         dtlsRepository;
    private final ExptResolMstrRepository        exptResolMstrRepository;
    private final ExptResolApprvrInfmRepository  exptResolApprvrInfmRepository;
    private final AuditLogService                auditLogService;
    private final BusiInfmRepository             busiInfmRepository;

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

        List<OverseasScheduledRow> rows = rawRows.stream()
                .map(r -> mapRow(r, approversBySeq))
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
     * Fetch a single scheduled-payment row by {@code OVRS_PYMN_NO}.
     *
     * @throws BusinessException {@code WBE_NOT_FOUND} when the id is unknown or belongs to a different tenant
     */
    public OverseasScheduledRow getById(String ovrsPymnNo) {
        String bizKey = TenantContext.currentBizKey();
        Object[] r = paymentRepo.findScheduledById(bizKey, ovrsPymnNo)
                .orElseThrow(() -> new BusinessException("WBE_NOT_FOUND",
                        "overseas payment not found: " + ovrsPymnNo));
        String apprSeqNo = (String) r[11];
        Map<String, List<Object[]>> approversBySeq = apprSeqNo != null
                ? approverRepo.findByApprSeqNos(bizKey, List.of(apprSeqNo))
                : Map.of();
        return mapRow(r, approversBySeq);
    }

    // ─── result list ──────────────────────────────────────────────────────────

    public OverseasResultListResponse listResult(
            String fromDt,
            String toDt,
            String keyword,
            String trnfYn,
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
        String trnfFilter = (trnfYn != null && !trnfYn.isBlank()) ? trnfYn : null;
        String orderBy    = buildOrderBy(OverseasResultSortField.parse(sortField), sortDirection, "ops.reg_dtm DESC");

        long totalCount = paymentRepo.countResult(bizKey, fromDt, toDt, likePat, trnfFilter);

        java.util.List<Object[]> rawRows = totalCount == 0
                ? java.util.List.of()
                : paymentRepo.findResult(bizKey, fromDt, toDt, likePat, trnfFilter, offset, pageSize, orderBy);

        java.util.List<OverseasResultRow> rows = rawRows.stream()
                .map(this::mapResultRow)
                .collect(java.util.stream.Collectors.toList());

        java.util.List<Object[]> rawTotals = paymentRepo.sumResultByCurrency(bizKey, fromDt, toDt, likePat, trnfFilter);
        java.util.List<OverseasCurrencyTotal> totals = rawTotals.stream()
                .map(this::mapTotal)
                .collect(java.util.stream.Collectors.toList());

        return new OverseasResultListResponse(totalCount, pageIndex, pageSize, rows, totals);
    }

    public OverseasResultRow getResultById(String ovrsPymnNo) {
        String bizKey = TenantContext.currentBizKey();
        Object[] r = paymentRepo.findResultById(bizKey, ovrsPymnNo)
                .orElseThrow(() -> new BusinessException("WBE_NOT_FOUND",
                        "overseas payment result not found: " + ovrsPymnNo));
        return mapResultRow(r);
    }

    /**
     * Streams the Overseas Payment Result XLSX per the WeCMS spec:
     * title row → summary row (USD/VND/KRW totals) → header row → one data row per
     * OVRS_PYMN_DTLS across all matching batches.
     *
     * <p>Mirrors legacy {@code pymn_0007_001} Excel export path.
     */
    public void exportResult(String fromDt, String toDt, String keyword, String trnfYn,
                             HttpServletResponse response) throws IOException {
        if (fromDt == null || fromDt.isBlank()) throw new BusinessException("WBE_VALIDATION", "FROM_DT is required");
        if (toDt   == null || toDt.isBlank())   throw new BusinessException("WBE_VALIDATION", "TO_DT is required");

        String bizKey    = TenantContext.currentBizKey();
        String trnfFilter = (trnfYn != null && !trnfYn.isBlank()) ? trnfYn : null;
        String likePat    = buildLikePattern(keyword);

        List<Object[]> batches = paymentRepo.findResultForExport(bizKey, fromDt, toDt, likePat, trnfFilter);

        Map<String, BigDecimal> currencyTotals = new LinkedHashMap<>();
        List<OverseasPaymentResultExcelWriter.ExportRow> exportRows = new ArrayList<>();

        for (Object[] b : batches) {
            String ovrsPymnNo  = toStr(b[0]);
            String batchRegDtm = toStr(b[5]);
            String draftUserNm = toStr(b[6]);
            String regId       = toStr(b[7]);
            String outBankNm   = toStr(b[8]);
            String wdrlActNo   = toStr(b[9]);
            String trnfYnVal   = toStr(b[10]);
            String docNo       = toStr(b[11]);
            String apprTitl    = toStr(b[12]);
            String apprRegDtm  = toStr(b[13]);
            String apprTypeCd  = toStr(b[14]);
            String payDate     = toStr(b[15]);
            String orgFileNm   = toStr(b[2]);
            String requestor   = draftUserNm != null && !draftUserNm.isBlank()
                    ? draftUserNm : OverseasPaymentResultExcelWriter.safe(regId);

            List<OvrsPymnDtls> dtls =
                    dtlsRepository.findByBizKeyAndOvrsPymnNoOrderByTrnSeqAsc(bizKey, ovrsPymnNo);

            for (OvrsPymnDtls d : dtls) {
                BigDecimal amt    = d.getTrnfAmt() != null ? d.getTrnfAmt() : BigDecimal.ZERO;
                String     rowCcy = d.getCcyCd()   != null ? d.getCcyCd()   : toStr(b[1]);
                currencyTotals.merge(rowCcy, amt, BigDecimal::add);

                exportRows.add(new OverseasPaymentResultExcelWriter.ExportRow(
                        OverseasPaymentResultExcelWriter.formatDtm(batchRegDtm),
                        String.valueOf(d.getTrnSeq()),
                        OverseasPaymentResultExcelWriter.mapStatus(trnfYnVal),
                        OverseasPaymentResultExcelWriter.formatDtm(apprRegDtm),
                        OverseasPaymentResultExcelWriter.stripExtension(orgFileNm),
                        OverseasPaymentResultExcelWriter.safe(outBankNm),
                        OverseasPaymentResultExcelWriter.safe(wdrlActNo),
                        OverseasPaymentResultExcelWriter.safe(d.getBnfcNm()),
                        OverseasPaymentResultExcelWriter.safe(d.getBnfcBankNm()),
                        OverseasPaymentResultExcelWriter.safe(d.getBicCd()),
                        OverseasPaymentResultExcelWriter.safe(d.getBnfcActNo()),
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

        OverseasPaymentResultExcelWriter.write(currencyTotals, exportRows, response.getOutputStream());
    }

    // ─── detail + request approval ───────────────────────────────────────────

    /**
     * Full batch detail for the Request Approval / Approval Details dialogs.
     * Returns summary metadata + per-line beneficiary rows.
     * Mirrors legacy {@code pymn_0007_001} approval-request dialog load.
     */
    public OverseasPaymentDetailResponse getDetail(String ovrsPymnNo) {
        String bizKey = TenantContext.currentBizKey();
        OvrsPymnSumr sumr = sumrRepository.findByBizKeyAndOvrsPymnNo(bizKey, ovrsPymnNo)
                .orElseThrow(() -> new BusinessException("WBE2045",
                        "overseas payment not found: " + ovrsPymnNo));

        List<OvrsPymnDtls> dtlList =
                dtlsRepository.findByBizKeyAndOvrsPymnNoOrderByTrnSeqAsc(bizKey, ovrsPymnNo);

        List<OverseasPaymentDetailRowResponse> rows = dtlList.stream()
                .map(d -> new OverseasPaymentDetailRowResponse(
                        d.getTrnSeq(),
                        d.getBnfcCode(),
                        d.getCcyCd(),
                        d.getTrnfAmt() != null ? d.getTrnfAmt() : BigDecimal.ZERO,
                        d.getFeePayerCd(),
                        d.getMemo() != null ? d.getMemo() : "",
                        d.getRprtAcctCd(),
                        d.getBnfcNm(),
                        d.getBnfcBankNm(),
                        d.getBicCd(),
                        d.getBnfcActNo(),
                        d.getBnfcAddr(),
                        d.getTrnfYn() != null ? d.getTrnfYn() : "N"))
                .collect(Collectors.toList());

        String docNo = null;
        if (sumr.getApprSeqNo() != null) {
            ExptResolMstr mstr = exptResolMstrRepository.findById(
                    new com.kosign.wecms.domain.payment.approvalbox.entity.ExptResolMstrId(bizKey, sumr.getApprSeqNo())
            ).orElse(null);
            if (mstr != null) {
                docNo = mstr.getDocNo();
            }
        }

        return new OverseasPaymentDetailResponse(
                sumr.getOvrsPymnNo(),
                sumr.getTitl(),
                sumr.getApprStts(),
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
                null,                       // fileSize
                sumr.getOrgFileNm(),
                rows);
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
    public RequestApprovalResponse requestApproval(String ovrsPymnNo, RequestApprovalRequest req) {
        String bizKey = TenantContext.currentBizKey();
        OvrsPymnSumr sumr = sumrRepository.findByBizKeyAndOvrsPymnNo(bizKey, ovrsPymnNo)
                .orElseThrow(() -> new BusinessException("WBE2045",
                        "overseas payment not found: " + ovrsPymnNo));

        if ("Y".equals(sumr.getTrnfYn())) {
            throw new BusinessException("WBE2095");
        }

        String existingStts = sumr.getApprStts();
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
            // Rejected: allow re-submission — reset state so a new approval chain starts
            sumr.setApprStts(null);
            sumr.setApprSeqNo(null);
        }

        if (req == null || req.approvers() == null || req.approvers().isEmpty()) {
            throw new BusinessException("WBE_VALIDATION", "approvers is required");
        }

        String apprSeqNo = allocateApprSeqNo(bizKey);
        try {
            stampApproval(sumr, apprSeqNo, req);
        } catch (org.springframework.dao.DataIntegrityViolationException race) {
            log.info("feature=overseas-payment apprSeqNo race retrying once bizKey={}", bizKey);
            apprSeqNo = allocateApprSeqNo(bizKey);
            stampApproval(sumr, apprSeqNo, req);
        }

        auditLogService.append(AuditLogContext.builder()
                .wsvcId(WSVC_ID)
                .refNo(ovrsPymnNo)
                .refNo1(apprSeqNo)
                .activityKey("audit.overseas_payment.activity")
                .detailKey("audit.overseas_payment.request_approval.detail")
                .build());

        log.info("feature=overseas-payment request-approval bizKey={} ovrsPymnNo={} apprSeqNo={} apprLineNo={} approvers={}",
                bizKey, ovrsPymnNo, apprSeqNo, req.apprLineNo(), req.approvers().size());

        return new RequestApprovalResponse(apprSeqNo);
    }

    /**
     * Approve or reject an overseas payment batch (Phase A stamp-only).
     * Flips {@code APPR_STTS} on {@code OVRS_PYMN_SUMR} to {@code "C"} (approved)
     * or {@code "X"} (rejected).
     *
     * <p>Guards: {@code WBE2094} when no approval request exists yet;
     * {@code WBE2095} when the batch is already in a final state.
     */
    @Transactional
    public void actOnApproval(String ovrsPymnNo, ApprovalActionRequest req) {
        String bizKey = TenantContext.currentBizKey();
        OvrsPymnSumr sumr = sumrRepository.findByBizKeyAndOvrsPymnNo(bizKey, ovrsPymnNo)
                .orElseThrow(() -> new BusinessException("WBE2045",
                        "overseas payment not found: " + ovrsPymnNo));

        if (sumr.getApprSeqNo() == null || sumr.getApprSeqNo().isBlank()) {
            throw new BusinessException("WBE2094");
        }

        if ("Y".equals(sumr.getTrnfYn())) {
            // Batch has already been transferred — approval decision is final
            throw new BusinessException("WBE2095");
        }

        String next   = req.isApprove() ? "C" : "X";
        String nowDtm = ZonedDateTime.now(VIETNAM_ZONE).format(DTM_FMT);
        sumr.setApprStts(next);
        sumr.setModDtm(nowDtm);
        sumr.setModId(TenantContext.currentUserId());
        sumrRepository.save(sumr);

        // Keep EXPT_RESOL_MSTR.APPR_TYPE_CD in sync so the list filter and
        // the status badge both derive from the same authoritative column.
        String apprTypeCd = req.isApprove() ? "01" : "02";
        exptResolMstrRepository.findById(
                new com.kosign.wecms.domain.payment.approvalbox.entity.ExptResolMstrId(bizKey, sumr.getApprSeqNo()))
                .ifPresent(mstr -> {
                    mstr.setApprTypeCd(apprTypeCd);
                    mstr.setModId(TenantContext.currentUserId());
                    mstr.setModDtm(nowDtm);
                    exptResolMstrRepository.save(mstr);
                });

        // On reject: stop all pending + waiting approvers so the Approval Box
        // detail view reflects the correct chain state.
        if (!req.isApprove()) {
            exptResolApprvrInfmRepository.stopAllActive(
                    bizKey, sumr.getApprSeqNo(),
                    TenantContext.currentUserId(), nowDtm);
        }

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
     * Saves the selected reporting account code on a single detail line.
     * Mirrors the legacy inline account-picker save in the scheduled-payment
     * detail dialog ({@code pymn_0007_001} detail row update).
     */
    @Transactional
    public void saveReportingAccount(String ovrsPymnNo, int trnSeq, String rprtAcctCd) {
        String bizKey = TenantContext.currentBizKey();
        OvrsPymnDtls dtl = dtlsRepository
                .findByBizKeyAndOvrsPymnNoAndTrnSeq(bizKey, ovrsPymnNo, trnSeq)
                .orElseThrow(() -> new BusinessException("WBE2045",
                        "detail line not found: " + ovrsPymnNo + " / " + trnSeq));

        String code = (rprtAcctCd == null || rprtAcctCd.isBlank()) ? null : rprtAcctCd.trim();
        dtl.setRprtAcctCd(code);
        dtlsRepository.save(dtl);

        log.info("feature=overseas-payment reporting-account bizKey={} ovrsPymnNo={} trnSeq={} rprtAcctCd={}",
                bizKey, ovrsPymnNo, trnSeq, code);
    }

    /** Per-tenant MAX+1 across both EXPT_RESOL_MSTR and OVRS_PYMN_SUMR so the
     *  newly issued APPR_SEQ_NO is above any Phase-A-only legacy rows. */
    private String allocateApprSeqNo(String bizKey) {
        Long fromMstr = exptResolMstrRepository.findMaxApprSeqNoByBizKey(bizKey);
        Long fromSumr = sumrRepository.findMaxApprSeqNoByBizKey(bizKey);
        long max = Math.max(
                fromMstr == null ? 0L : fromMstr,
                fromSumr == null ? 0L : fromSumr);
        return Long.toString(max + 1L);
    }

    private void stampApproval(OvrsPymnSumr sumr, String apprSeqNo, RequestApprovalRequest req) {
        String bizKey  = sumr.getBizKey();
        String userId  = TenantContext.currentUserId();
        String userNm  = TenantContext.currentUserNm();
        String nowDtm  = ZonedDateTime.now(VIETNAM_ZONE).format(DTM_FMT);
        // yyyyMMdd and HHmmss slices of the 14-char timestamp
        String draftDate = nowDtm.substring(0, 8);
        String draftTime = nowDtm.substring(8, 14);

        // 1. Stamp OVRS_PYMN_SUMR
        sumr.setApprSeqNo(apprSeqNo);
        sumr.setApprStts("P");  // Pending
        if (req.postTitle() != null && !req.postTitle().isBlank()) {
            sumr.setTitl(req.postTitle().trim());
        }
        sumr.setModDtm(nowDtm);
        sumr.setModId(userId);
        sumrRepository.save(sumr);

        // 2. Write EXPT_RESOL_MSTR master row
        ExptResolMstr mstr = new ExptResolMstr();
        mstr.setBizKey(bizKey);
        mstr.setApprSeqNo(apprSeqNo);
        mstr.setApprDv(ApprDv.OVERSEAS.code());     // "34"
        mstr.setApprSts("0");                       // initial pending state
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
        mstr.setApprBoxUseYn("Y");
        mstr.setApprDatDispYn("N");
        mstr.setCmpnLogoDispYn("Y");
        mstr.setRegDispYn("Y");
        mstr.setTitl(req.postTitle() != null ? req.postTitle().trim() : "");
        mstr.setDocNo(sumr.getOvrsPymnNo());
        mstr.setDocNm(sumr.getTitl() != null ? sumr.getTitl() : sumr.getOvrsPymnNo());
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
    }

    // ─── change approval line ────────────────────────────────────────────────

    /**
     * Replaces the approver chain on a pending overseas payment batch.
     * Deletes existing {@code EXPT_RESOL_APPRVR_INFM} rows and inserts the
     * new chain from the selected approval line. Only allowed when
     * {@code APPR_STTS = 'P'} (pending — first approver has not yet acted).
     *
     * @throws BusinessException {@code WBE2045} — batch not found
     * @throws BusinessException {@code WBE2097} — batch is not in pending state
     */
    @Transactional
    public void changeApprovalLine(String ovrsPymnNo, ChangeApprovalLineRequest req) {
        String bizKey = TenantContext.currentBizKey();
        OvrsPymnSumr sumr = sumrRepository.findByBizKeyAndOvrsPymnNo(bizKey, ovrsPymnNo)
                .orElseThrow(() -> new BusinessException("WBE2045",
                        "overseas payment not found: " + ovrsPymnNo));

        if (!"P".equals(sumr.getApprStts())) {
            throw new BusinessException("WBE2097");
        }
        if ("Y".equals(sumr.getTrnfYn())) {
            throw new BusinessException("WBE2095");
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
        OvrsPymnSumr sumr = sumrRepository.findByBizKeyAndOvrsPymnNo(bizKey, ovrsPymnNo)
                .orElseThrow(() -> new BusinessException("WBE2045",
                        "overseas payment not found: " + ovrsPymnNo));

        if (!"P".equals(sumr.getApprStts())) {
            throw new BusinessException("WBE2098");
        }
        if ("Y".equals(sumr.getTrnfYn())) {
            throw new BusinessException("WBE2095");
        }

        String apprSeqNo = sumr.getApprSeqNo();
        String userId    = TenantContext.currentUserId();
        String nowDtm    = ZonedDateTime.now(VIETNAM_ZONE).format(DTM_FMT);

        // Delete approver chain rows
        List<ExptResolApprvrInfm> existing = exptResolApprvrInfmRepository.findChain(bizKey, apprSeqNo);
        exptResolApprvrInfmRepository.deleteAll(existing);
        exptResolApprvrInfmRepository.flush();

        // Delete master approval row
        exptResolMstrRepository.findById(
                new com.kosign.wecms.domain.payment.approvalbox.entity.ExptResolMstrId(bizKey, apprSeqNo))
                .ifPresent(exptResolMstrRepository::delete);
        exptResolMstrRepository.flush();

        // Reset the batch to draft state
        sumr.setApprSeqNo(null);
        sumr.setApprStts(null);
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

    // ─── mappers ──────────────────────────────────────────────────────────────

    private OverseasScheduledRow mapRow(Object[] r, Map<String, List<Object[]>> approversBySeq) {
        String apprSeqNo = (String) r[11];

        List<OverseasApprover> approvers = apprSeqNo == null
                ? List.of()
                : approversBySeq.getOrDefault(apprSeqNo, List.of())
                        .stream()
                        .map(this::mapApprover)
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

    private OverseasApprover mapApprover(Object[] r) {
        return new OverseasApprover(
                toStr(r[2]),  // apprvrId
                toStr(r[3]),  // apprvrNm
                toStr(r[4]),  // apprvrStts (CHAR(1))
                toStr(r[1]),  // apprvrLineNo
                toStr(r[5]),  // modDtm
                toStr(r[6])   // regImgUrl
        );
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
                0,                   // successCount — not yet tracked at batch level
                0,                   // failedCount  — not yet tracked at batch level
                toStr(r[14]),        // payDate
                toStr(r[15]),        // apprStts (appr_type_cd)
                toStr(r[16])         // apprvrNm (NULL — complex join deferred)
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
