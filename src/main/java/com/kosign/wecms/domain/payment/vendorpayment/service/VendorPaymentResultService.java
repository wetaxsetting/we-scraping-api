package com.kosign.wecms.domain.payment.vendorpayment.service;

import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.common.web.SortDirection;
import com.kosign.wecms.domain.payment.approvalbox.entity.ExptResolMstr;
import com.kosign.wecms.domain.payment.approvalbox.repository.ExptResolMstrRepository;
import com.kosign.wecms.domain.payment.common.TransferLineDetailMapper;
import com.kosign.wecms.domain.payment.dispatch.DispatchCountRepository;
import com.kosign.wecms.domain.payment.dispatch.DispatchCountRow;
import com.kosign.wecms.domain.payment.dispatch.service.DispatchResult;
import com.kosign.wecms.domain.payment.dispatch.service.DispatchResultResolver;
import com.kosign.wecms.domain.payment.internaltransfer.dto.CurrencyTotal;
import com.kosign.wecms.domain.payment.internaltransfer.dto.TransferResultDetailResponse;
import com.kosign.wecms.domain.payment.internaltransfer.dto.TransferResultLine;
import com.kosign.wecms.domain.payment.internaltransfer.dto.TransferResultListResponse;
import com.kosign.wecms.domain.payment.internaltransfer.dto.TransferResultRow;
import com.kosign.wecms.domain.payment.internaltransfer.entity.ScdulPymnSumr;
import com.kosign.wecms.domain.payment.internaltransfer.entity.ScdulPymnSumrDetl;
import com.kosign.wecms.domain.payment.internaltransfer.repository.ScdulPymnSumrDetlRepository;
import com.kosign.wecms.domain.payment.internaltransfer.repository.ScdulPymnSumrRepository;
import com.kosign.wecms.domain.payment.internaltransfer.service.ScdulPymnTypeCd;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Payment &gt; Vendor Payment — Payment Result list service. Mirrors the
 * legacy {@code pymn_0002_001_r001} read path (which
 * {@code PymnHelper.pymn_0002_001_r001} parameterises by
 * {@code SCDUL_PYMN_TYPE_CD = '01'}).
 *
 * <p>Phase A: read-only list. Detail (legacy {@code pymn_0002_002_r001})
 * lands in Phase B.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VendorPaymentResultService {

    private final ScdulPymnSumrRepository     sumrRepository;
    private final ScdulPymnSumrDetlRepository detlRepository;
    private final DispatchCountRepository     dispatchCountRepository;
    /** Shared bank-name resolver — same chain used by the Scheduled
     *  detail (BANK_INFM → V86 NAPAS/CITAD function →
     *  CITAD_BANK_CD canonical fallback). Surfaces `tarBankNm` on
     *  each Result line so the Deposit Bank cell renders the bank
     *  name alongside the code. */
    private final TransferLineDetailMapper    transferLineDetailMapper;
    /** Phase 2 dispatch-result resolver — see
     *  {@link com.kosign.wecms.domain.payment.internaltransfer.service.InternalTransferResultService}
     *  for the contract; shared across all 4 SCDUL_PYMN modules. */
    private final DispatchResultResolver      dispatchResultResolver;
    /** Approval master — read-only here to surface the requester name
     *  ({@code DRAFT_USER_NM}) on the result-detail header (legacy
     *  {@code pymn_0002_002} Requestor field). Mirrors
     *  {@link com.kosign.wecms.domain.payment.internaltransfer.service.InternalTransferResultService}. */
    private final ExptResolMstrRepository     exptResolMstrRepository;

    @Transactional(readOnly = true)
    public TransferResultListResponse list(
            String fromDate, String toDate, String srchWd, String trnfStts,
            String sortField, String sortDirection,
            int page, int size) {

        validateDateRange(fromDate, toDate);
        Pageable pageable = pageable(page, size, sortField, sortDirection);
        String bizKey  = requireTenant();
        String fromDtm = (fromDate + "000000").substring(0, 14);
        String toDtm   = (toDate   + "235959").substring(0, 14);

        // C2: the Result list's Completed / In Progress checkboxes drive the
        // legacy status codes 'C' / 'P' (route-aware PROC_STAT EXISTS inside
        // searchDispatched), NOT the old TRNF_YN-shaped 'Y' / 'N'. null = no
        // status filter.
        String procSttsFilter = emptyToNull(trnfStts);
        String srchWdPattern = buildLikePattern(srchWd);

        // Use `searchDispatched` (not `search`) so only batches the user
        // has actually clicked Transfer on appear here — `trnf_yn = 'Y'` is
        // the canonical dispatched marker. The Completed/In Progress filter
        // (procSttsFilter) is applied to the row query only.
        Page<ScdulPymnSumr> rows = sumrRepository.searchDispatched(
                bizKey, ScdulPymnTypeCd.VENDOR.code(),
                fromDtm, toDtm, srchWdPattern, procSttsFilter,
                pageable);

        Map<String, long[]> counts = loadDispatchCounts(bizKey, rows.getContent());

        // EXPT_RESOL_MSTR row per batch, keyed by APPR_SEQ_NO. One bulk query
        // for the page; supplies BOTH the Approval Ref (DOC_NO — its canonical
        // home, NOT SCDUL_PYMN_SUMR.ERP_FILE_NUM) and the Approval Title
        // (ERM.TITL, not SCDUL_PYMN_SUMR.TITL). Mirrors the Internal Transfer
        // reference fix (F-3 / F-4a).
        Map<String, ExptResolMstr> apprByApprSeqNo = buildApprovalMstrMap(bizKey, rows.getContent());

        List<TransferResultRow> dtoRows = rows.getContent().stream()
                .map(s -> toResultRow(s, counts.get(s.getScdulPymnNo()), apprByApprSeqNo))
                .toList();
        // Footer currency totals — aggregated across the FULL filtered,
        // dispatched result set and grouped by CCY_CD (legacy IDO
        // SCDUL_PYMN_SUMR_R008). C2: the status filter is intentionally NOT
        // applied here (null) — legacy R008 sums all dispatched rows regardless
        // of the Completed / In Progress checkboxes, so the footer total stays
        // stable while the row list narrows. Same date/search predicate as the
        // paged query so it never reflects only the current page (F-1).
        List<CurrencyTotal> totals = mapCurrencyTotals(
                sumrRepository.searchDispatchedCurrencyTotals(
                        bizKey, ScdulPymnTypeCd.VENDOR.code(),
                        fromDtm, toDtm, srchWdPattern, null));

        log.info("feature=vendor-payment result-list bizKey={} total={} page={} size={}",
                bizKey, rows.getTotalElements(), page, pageable.getPageSize());
        return new TransferResultListResponse(
                rows.getTotalElements(), page, pageable.getPageSize(),
                totals, dtoRows);
    }

    /** Pull per-batch (succ, err) counts from the dispatch tables —
     *  falls back to an empty map when the dispatch tables aren't
     *  reachable so the page still renders. See
     *  {@link InternalTransferResultService#loadDispatchCounts}. */
    private Map<String, long[]> loadDispatchCounts(
            String bizKey, List<ScdulPymnSumr> batches) {
        if (batches.isEmpty()) return Collections.emptyMap();
        List<String> ids = batches.stream().map(ScdulPymnSumr::getScdulPymnNo).toList();
        try {
            List<DispatchCountRow> raw = dispatchCountRepository.countByBatch(bizKey, ids);
            Map<String, long[]> out = new HashMap<>(raw.size());
            for (DispatchCountRow r : raw) {
                long succ = r.getSuccCnt() != null ? r.getSuccCnt() : 0L;
                long err  = r.getErrCnt()  != null ? r.getErrCnt()  : 0L;
                out.put(r.getScdulPymnNo(), new long[] { succ, err });
            }
            return out;
        } catch (Exception e) {
            log.warn("feature=vendor-payment dispatch-count query failed — degrading to null counts ({})",
                    e.getMessage());
            return Collections.emptyMap();
        }
    }

    /** Hard cap on the number of batches a single bulk Excel export pulls. */
    private static final int EXPORT_MAX = 10_000;

    /**
     * Ordered list of dispatched Payment-Result batch keys for the current
     * filter — drives the toolbar bulk Excel export. Uses the SAME predicate,
     * date-window, search pattern and ordering as {@link #list} (so the export
     * mirrors what the user sees) but is not page-capped.
     */
    @Transactional(readOnly = true)
    public List<String> dispatchedIdsForExport(
            String fromDate, String toDate, String srchWd, String trnfStts) {
        validateDateRange(fromDate, toDate);
        String bizKey  = requireTenant();
        String fromDtm = (fromDate + "000000").substring(0, 14);
        String toDtm   = (toDate   + "235959").substring(0, 14);
        String procSttsFilter = emptyToNull(trnfStts);
        String srchWdPattern  = buildLikePattern(srchWd);
        Sort sort = Sort.by(Sort.Order.desc("trnfDtm"), Sort.Order.desc("scdulPymnNo"));
        Page<ScdulPymnSumr> rows = sumrRepository.searchDispatched(
                bizKey, ScdulPymnTypeCd.VENDOR.code(),
                fromDtm, toDtm, srchWdPattern, procSttsFilter,
                PageRequest.of(0, EXPORT_MAX, sort));
        if (rows.getTotalElements() > EXPORT_MAX) {
            log.warn("feature=vendor-payment bulk-export truncated bizKey={} total={} cap={}",
                    bizKey, rows.getTotalElements(), EXPORT_MAX);
        }
        return rows.getContent().stream().map(ScdulPymnSumr::getScdulPymnNo).toList();
    }

    /* ----------------------------------------------------------------
     *  Detail
     * ---------------------------------------------------------------- */

    /**
     * Payment Result detail. Mirrors legacy {@code pymn_0002_002_r001}.
     * Phase A defers the dispatch-tables join — `suc_cnt` / `err_cnt` /
     * `prc_cnt` ship as null until {@code TB_PAY_TARGET} / {@code TB_PAY_FILE_DATA}
     * are wired.
     */
    @Transactional(readOnly = true)
    public TransferResultDetailResponse detail(String id) {
        String bizKey = requireTenant();
        ScdulPymnSumr master = sumrRepository
                .findByBizKeyAndScdulPymnNoAndScdulPymnTypeCd(
                        bizKey, id, ScdulPymnTypeCd.VENDOR.code())
                .orElseThrow(() -> new BusinessException("WBE2060"));

        Sort lineSort = ScdulPymnSumrDetlRepository.DETL_NO_NUMERIC_ASC;
        List<ScdulPymnSumrDetl> detailRows = detlRepository
                .findByBizKeyAndScdulPymnNo(bizKey, id, lineSort);
        Map<String, DispatchResult> resultByDetlNo =
                dispatchResultResolver.resolve(master, detailRows);
        List<TransferResultLine> lines = detailRows.stream()
                .map(d -> toResultLine(d, resultByDetlNo.getOrDefault(
                        d.getScdulPymnDetlNo(), DispatchResult.EMPTY)))
                .toList();

        // G1: header tab counts (All / Success / Failed / In Progress) —
        // mirrors legacy pymn_0002_002_r001's TOT_CNT/SUC_CNT/ERR_CNT/PRC_CNT.
        // Derived from the already-resolved per-line statuses so the header
        // numbers equal the per-line chip distribution the user sees. Same
        // pattern as InternalTransferResultService.detail() (G2 fix).
        long[] counts = countByStatus(lines);

        // G6: withdrawal bank display name (legacy OUT_BANK_NM) — same shared
        // resolver the per-line Deposit Bank cell uses. null when unresolvable;
        // the frontend falls back to the raw outBankCd.
        String outBankNm = transferLineDetailMapper.resolveBankNm(master.getOutBankCd());

        // Load the approval master once and reuse it for BOTH the requester
        // display name (legacy pymn_0002_002 EXPT_RESOL_MSTR.DRAFT_USER_NM) and
        // the Approval Ref (DOC_NO). doc_no's canonical home is
        // EXPT_RESOL_MSTR.DOC_NO, NOT SCDUL_PYMN_SUMR.ERP_FILE_NUM (null in DEV
        // → blank). C1 fix; mirrors InternalTransferResultService.detail() and
        // this service's own list() path. Empty until submitted — the frontend
        // falls back to regId for the requestor.
        String apprSeqNo = master.getApprSeqNo();
        java.util.Optional<ExptResolMstr> apprMstr = (apprSeqNo != null && !apprSeqNo.isBlank())
                ? exptResolMstrRepository
                        .findByBizKeyAndApprSeqNoIn(bizKey, List.of(apprSeqNo))
                        .stream()
                        .findFirst()
                : java.util.Optional.empty();
        String requestor = apprMstr
                .map(ExptResolMstr::getDraftUserNm)
                .filter(s -> s != null && !s.isBlank())
                .orElse(null);

        return new TransferResultDetailResponse(
                master.getScdulPymnNo(),
                master.getOutBankCd(),
                outBankNm,
                master.getWdrlActNo(),
                master.getTitl(),
                master.getTotalTranAmt(),
                master.getTotTrnsCnt(),
                master.getApprSeqNo(),
                apprMstr.map(ExptResolMstr::getDocNo).orElse(null),
                master.getCcyCd(),
                master.getWdrlCcyCd(),
                master.getTrnfYn(),
                master.getTrnfDtm(),
                master.getOrgFileNm(),
                master.getFileSize() == null || master.getFileSize().isBlank() ? null : new java.math.BigDecimal(master.getFileSize().trim()),
                master.getRegId(),
                requestor,
                // suc/err/prc — derived from the resolved per-line statuses
                // (countByStatus). Array order is [success, failed, inProgress].
                BigDecimal.valueOf(counts[0]),
                BigDecimal.valueOf(counts[1]),
                BigDecimal.valueOf(counts[2]),
                lines);
    }

    /**
     * Bucket the resolved detail lines into {@code [success, failed, inProgress]}
     * counts for the Payment Result detail header tabs (All / Success / Failed /
     * In Progress).
     *
     * <p>Classification mirrors the shared frontend classifier
     * {@code components/payment/shared/deriveResultStatus.ts} and the legacy
     * per-line rule {@code pymn_0002_002.js}, so the header counts always equal
     * the per-line chip distribution the user sees:
     * <ul>
     *   <li>blank {@code proc_stat} AND blank {@code rs_cd} &rarr; in&nbsp;progress
     *       (dispatched batch whose bank-side result has not landed yet)</li>
     *   <li>{@code proc_stat="0"} AND {@code rs_cd="0000"} &rarr; success</li>
     *   <li>{@code proc_stat} &isin; {{@code 9},{@code 1},{@code 6}} &rarr; in&nbsp;progress</li>
     *   <li>everything else ({@code proc_stat="3"}, or {@code "0"} with a
     *       non-{@code "0000"} {@code rs_cd}, or a blank {@code proc_stat} with a
     *       non-blank {@code rs_cd}, or a stray code) &rarr; failed</li>
     * </ul>
     *
     * <p>Identical classification to
     * {@code InternalTransferResultService.countByStatus} (kept private per the
     * established per-service pattern — no shared helper exists yet).
     * Invariant: {@code success + failed + inProgress == lines.size()}.
     */
    private static long[] countByStatus(List<TransferResultLine> lines) {
        long success = 0, failed = 0, inProgress = 0;
        for (TransferResultLine line : lines) {
            String ps = line.procStat() == null ? "" : line.procStat().trim();
            String rs = line.rsCd()     == null ? "" : line.rsCd().trim();
            if (ps.isEmpty() && rs.isEmpty()) {
                inProgress++;
            } else if ("0".equals(ps) && "0000".equals(rs)) {
                success++;
            } else if ("9".equals(ps) || "1".equals(ps) || "6".equals(ps)) {
                inProgress++;
            } else {
                failed++;
            }
        }
        return new long[] { success, failed, inProgress };
    }

    /* ---------------------------------------------------------------- */

    private TransferResultLine toResultLine(ScdulPymnSumrDetl d, DispatchResult r) {
        return new TransferResultLine(
                d.getScdulPymnDetlNo(),
                d.getTranAmt(),
                d.getAntDivCd(),
                d.getRcptNm(),
                d.getTarBankCd(),
                transferLineDetailMapper.resolveBankNm(d.getTarBankCd()),
                d.getFincMdaNo(),
                d.getTranUsag(),
                d.getTrnfCcyCd(),
                d.getTrnfCcyCd(),
                d.getDepoCcyCd(),
                d.getNote(),
                d.getDocNo(),
                d.getChartAcctCd(),
                d.getChartAcctSeqNo(),
                d.getBankTrnsShortId(),
                r.procStat(), r.procStatKey(), r.procDttm(), r.rsltDttm(),
                r.rsCd(), r.rsMsg(),
                // Banking-match read fields (Phase 1) — SCDUL_PYMN_SUMR_DETL.
                d.getLinkMatchYn(), d.getAcctSeqNo(), d.getBankTrnsFullId());
    }

    /**
     * Resolve the {@link ExptResolMstr} approval-master row per batch, keyed by
     * {@code APPR_SEQ_NO}. One bulk query for the page — supplies the Approval
     * Ref ({@code DOC_NO}) and Approval Title ({@code TITL}), whose canonical
     * home is {@code EXPT_RESOL_MSTR}, never {@code SCDUL_PYMN_SUMR}. Mirrors
     * {@code InternalTransferResultService#buildApprovalMstrMap}.
     */
    private Map<String, ExptResolMstr> buildApprovalMstrMap(String bizKey, List<ScdulPymnSumr> rows) {
        List<String> apprSeqNos = rows.stream()
                .map(ScdulPymnSumr::getApprSeqNo)
                .filter(seq -> seq != null && !seq.isBlank())
                .distinct()
                .toList();
        if (apprSeqNos.isEmpty()) return Map.of();
        return exptResolMstrRepository.findByBizKeyAndApprSeqNoIn(bizKey, apprSeqNos)
                .stream()
                .collect(java.util.stream.Collectors.toMap(
                        ExptResolMstr::getApprSeqNo,
                        m -> m,
                        (a, b) -> a));
    }

    private static TransferResultRow toResultRow(
            ScdulPymnSumr s, long[] counts, Map<String, ExptResolMstr> apprByApprSeqNo) {
        BigDecimal succ = counts != null ? BigDecimal.valueOf(counts[0]) : null;
        BigDecimal err  = counts != null ? BigDecimal.valueOf(counts[1]) : null;
        String apprSeqNo = s.getApprSeqNo();
        ExptResolMstr appr = apprSeqNo != null && !apprSeqNo.isBlank()
                ? apprByApprSeqNo.get(apprSeqNo)
                : null;
        // Approval Ref (doc_no) — canonical home EXPT_RESOL_MSTR.DOC_NO (legacy
        // SCDUL_PYMN_SUMR_R007), NOT SCDUL_PYMN_SUMR.ERP_FILE_NUM (which is null
        // in DEV → Approval Ref rendered blank). F-4a fix.
        String docNo = appr != null ? appr.getDocNo() : null;
        // Approval Title — legacy R007 sources this from EXPT_RESOL_MSTR.TITL;
        // SCDUL_PYMN_SUMR.TITL is the upload subject (the File Name source).
        // COALESCE(NULLIF(ERM.TITL,''), SPS.TITL): blank-aware fallback covers
        // refactor rows that write ERM.TITL="" and rows with no approval row.
        // F-3 fix.
        String ermTitl = appr != null ? appr.getTitl() : null;
        String approvalTitle = (ermTitl != null && !ermTitl.isBlank())
                ? ermTitl
                : s.getTitl();
        return new TransferResultRow(
                s.getScdulPymnNo(),
                s.getScdulPymnTypeCd(),
                s.getCcyCd(),
                approvalTitle,
                // File Name unchanged — ORG_FILE_NM (the genuine uploaded name).
                s.getOrgFileNm(),
                s.getTotalTranAmt(),
                s.getTotTrnsCnt(),
                succ,
                err,
                s.getTrnfYn(),
                // Date/Time — TRNF_DTM (dispatch datetime). Wire field name
                // `reg_dtm` unchanged (legacy aliases TRNF_DTM AS REG_DTM); the
                // displayed VALUE is the transfer datetime, matching the legacy
                // R007 list + the date filter window. Date-display fix.
                s.getTrnfDtm(),
                s.getApprSeqNo(),
                docNo);
    }

    /**
     * Project the full-dataset currency aggregate
     * ({@link ScdulPymnSumrRepository#searchDispatchedCurrencyTotals}) into the
     * wire {@link CurrencyTotal} list. The DB has already summed across the
     * entire filtered+dispatched set and grouped by currency (legacy
     * SCDUL_PYMN_SUMR_R008, F-1); this only does the COUNT-Long → BigDecimal
     * projection and null-safety. {@code COALESCE} in the query guarantees
     * non-null amount / count / ccy, so the defaults here are belt-and-braces.
     * Mirrors {@code InternalTransferService#mapCurrencyTotals} (the proven
     * Scheduled-list projection).
     */
    private static List<CurrencyTotal> mapCurrencyTotals(List<Object[]> aggregated) {
        return aggregated.stream()
                .map(r -> new CurrencyTotal(
                        r[1] == null ? BigDecimal.ZERO : (BigDecimal) r[1],
                        r[2] == null ? BigDecimal.ZERO : BigDecimal.valueOf(((Number) r[2]).longValue()),
                        r[0] == null ? "" : (String) r[0],
                        r[3] == null ? BigDecimal.ZERO : (BigDecimal) r[3]))
                .toList();
    }

    private static String requireTenant() {
        String bizKey = TenantContext.currentBizKey();
        if (bizKey == null) {
            throw new IllegalStateException("tenant context not populated");
        }
        return bizKey;
    }

    private static void validateDateRange(String fromDate, String toDate) {
        if (fromDate == null || !fromDate.matches("^[0-9]{8}$")
                || toDate == null || !toDate.matches("^[0-9]{8}$")) {
            throw new BusinessException("WBE_VALIDATION");
        }
        if (fromDate.compareTo(toDate) > 0) {
            throw new BusinessException("WBE_VALIDATION");
        }
    }

    private static Pageable pageable(int page, int size,
                                     String sortField, String sortDirection) {
        int safePage = Math.max(0, page);
        int safeSize = size <= 0 ? 10 : Math.min(size, 200);
        Sort sort = VendorPaymentResultSortField.parse(sortField)
                .map(sf -> {
                    SortDirection dir = SortDirection.parse(sortDirection);
                    Sort.Direction d = dir == SortDirection.DESC
                            ? Sort.Direction.DESC : Sort.Direction.ASC;
                    return Sort.by(d, sf.entityProperty())
                            .and(Sort.by(Sort.Order.desc("trnfDtm")));
                })
                .orElseGet(() -> Sort.by(Sort.Order.desc("trnfDtm"),
                        Sort.Order.desc("scdulPymnNo")));
        return PageRequest.of(safePage, safeSize, sort);
    }

    private static String buildLikePattern(String raw) {
        if (raw == null) return null;
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) return null;
        return "%" + trimmed.toLowerCase() + "%";
    }

    private static String emptyToNull(String s) {
        if (s == null) return null;
        String trimmed = s.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
