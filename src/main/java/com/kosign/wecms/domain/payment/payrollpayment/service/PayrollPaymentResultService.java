package com.kosign.wecms.domain.payment.payrollpayment.service;

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
 * Payment &gt; Payroll Payment — Payment Result list + detail service.
 * Mirrors the legacy {@code pymn_0006_001_r001} + {@code pymn_0006_002_r001}
 * read paths (which {@code PymnHelper.pymn_0006_001_r001} parameterises by
 * {@code SCDUL_PYMN_TYPE_CD = '00'}).
 *
 * <p>Phase A: read-only list + detail. Dispatch fields
 * ({@code suc_cnt}, {@code err_cnt}, {@code prc_cnt}) ship as {@code null}
 * until {@code TB_PAY_TARGET} / {@code TB_PAY_FILE_DATA} are wired.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayrollPaymentResultService {

    private final ScdulPymnSumrRepository     sumrRepository;
    private final ScdulPymnSumrDetlRepository detlRepository;
    private final DispatchCountRepository     dispatchCountRepository;
    /** Shared bank-name resolver — same chain used by the Scheduled
     *  detail (BANK_INFM → V86 NAPAS/CITAD function →
     *  CITAD_BANK_CD canonical fallback). Surfaces `tarBankNm` on
     *  each Result line so the Deposit Bank cell renders the bank
     *  name alongside the code. */
    private final TransferLineDetailMapper    transferLineDetailMapper;
    /** Phase 2 dispatch-result resolver — shared across all 4 SCDUL_PYMN
     *  modules. See {@code DispatchResultResolver} for the contract. */
    private final DispatchResultResolver      dispatchResultResolver;
    /** Approval master — read-only here to surface the requester name
     *  ({@code DRAFT_USER_NM}) on the result-detail header (legacy
     *  {@code pymn_0006_002} Requestor field). Same wiring as
     *  {@link com.kosign.wecms.domain.payment.vendorpayment.service.VendorPaymentResultService}. */
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

        // C2: Completed / In Progress checkboxes drive the legacy status codes
        // 'C' / 'P' (route-aware PROC_STAT EXISTS inside searchDispatched), NOT
        // the old TRNF_YN-shaped 'Y' / 'N'. null = no status filter.
        String procSttsFilter = emptyToNull(trnfStts);
        String srchWdPattern = buildLikePattern(srchWd);

        // `searchDispatched` gates on `trnf_yn = 'Y'` (dispatched marker); the
        // Completed/In Progress filter (procSttsFilter) applies to rows only.
        Page<ScdulPymnSumr> rows = sumrRepository.searchDispatched(
                bizKey, ScdulPymnTypeCd.PAYROLL.code(),
                fromDtm, toDtm, srchWdPattern, procSttsFilter,
                pageable);

        Map<String, long[]> counts = loadDispatchCounts(bizKey, rows.getContent());

        // EXPT_RESOL_MSTR row per batch, keyed by APPR_SEQ_NO. One bulk query
        // for the page; supplies the Approval Ref (DOC_NO — its canonical home,
        // NOT SCDUL_PYMN_SUMR.ERP_FILE_NUM), the Approval Title (ERM.TITL — not
        // SCDUL_PYMN_SUMR.TITL), AND the Payroll Type (APPR_FUND_TP — legacy
        // SCDUL_PYMN_SUMR_R010 LEFT JOIN; "0"=Salary / "1"=Bonus / "9"/null
        // blank on the FE). Mirrors the Internal Transfer reference fix
        // (F-3 / F-4a) and consolidates the former fund-type-only lookup into
        // the single approval-master map.
        Map<String, ExptResolMstr> apprByApprSeqNo = buildApprovalMstrMap(bizKey, rows.getContent());

        List<TransferResultRow> dtoRows = rows.getContent().stream()
                .map(s -> toResultRow(s, counts.get(s.getScdulPymnNo()), apprByApprSeqNo))
                .toList();
        // Footer currency totals — aggregated across the FULL filtered,
        // dispatched result set and grouped by CCY_CD (legacy IDO
        // SCDUL_PYMN_SUMR_R008). C2: status filter intentionally NOT applied
        // here (null) — legacy R008 sums all dispatched rows regardless of the
        // Completed / In Progress checkboxes. Same date/search predicate as the
        // paged query so it never reflects only the current page (F-1).
        List<CurrencyTotal> totals = mapCurrencyTotals(
                sumrRepository.searchDispatchedCurrencyTotals(
                        bizKey, ScdulPymnTypeCd.PAYROLL.code(),
                        fromDtm, toDtm, srchWdPattern, null));

        log.info("feature=payroll-payment result-list bizKey={} total={} page={} size={}",
                bizKey, rows.getTotalElements(), page, pageable.getPageSize());
        return new TransferResultListResponse(
                rows.getTotalElements(), page, pageable.getPageSize(),
                totals, dtoRows);
    }

    /** Pull per-batch (succ, err) counts from the dispatch tables. See
     *  {@link com.kosign.wecms.domain.payment.internaltransfer.service.InternalTransferResultService#loadDispatchCounts}. */
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
            log.warn("feature=payroll-payment dispatch-count query failed — degrading to null counts ({})",
                    e.getMessage());
            return Collections.emptyMap();
        }
    }

    /**
     * Resolve the {@link ExptResolMstr} approval-master row per batch, keyed by
     * {@code APPR_SEQ_NO}. One bulk query for the page — supplies the Approval
     * Ref ({@code DOC_NO}), Approval Title ({@code TITL}), and Payroll Type
     * ({@code APPR_FUND_TP}, legacy {@code SCDUL_PYMN_SUMR_R010} LEFT JOIN on
     * {@code (BIZ_KEY, APPR_SEQ_NO)}). All three columns' canonical home is
     * {@code EXPT_RESOL_MSTR}, never {@code SCDUL_PYMN_SUMR}. Empty when no row
     * on the page has been submitted for approval. Mirrors
     * {@code InternalTransferResultService#buildApprovalMstrMap}.
     */
    private Map<String, ExptResolMstr> buildApprovalMstrMap(
            String bizKey, List<ScdulPymnSumr> batches) {
        List<String> apprSeqNos = batches.stream()
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

    /**
     * Payment Result detail. Mirrors legacy {@code pymn_0006_002_r001}.
     */
    @Transactional(readOnly = true)
    public TransferResultDetailResponse detail(String id) {
        String bizKey = requireTenant();
        ScdulPymnSumr master = sumrRepository
                .findByBizKeyAndScdulPymnNoAndScdulPymnTypeCd(
                        bizKey, id, ScdulPymnTypeCd.PAYROLL.code())
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
        // mirrors legacy pymn_0006_002_r001's TOT_CNT/SUC_CNT/ERR_CNT/PRC_CNT.
        // Derived from the already-resolved per-line statuses so the header
        // numbers equal the per-line chip distribution the user sees. Same
        // pattern as VendorPaymentResultService / InternalTransferResultService.
        long[] counts = countByStatus(lines);

        // Issue-1 (Result detail): withdrawal bank display name (legacy OUT_BANK_NM) —
        // same shared resolver the per-line Deposit Bank cell uses. null when
        // unresolvable; the frontend falls back to the raw outBankCd. Lets the
        // Payroll Result-detail header render the bank name like Vendor/Expense.
        String outBankNm = transferLineDetailMapper.resolveBankNm(master.getOutBankCd());

        // Load the approval master once and reuse it for BOTH the requester
        // display name (legacy pymn_0006_002 EXPT_RESOL_MSTR.DRAFT_USER_NM) and
        // the Approval Ref (DOC_NO). doc_no's canonical home is
        // EXPT_RESOL_MSTR.DOC_NO, NOT SCDUL_PYMN_SUMR.ERP_FILE_NUM (null in DEV
        // → blank). C1 fix; mirrors VendorPaymentResultService.detail() and this
        // service's own list() path. Empty until submitted — the frontend falls
        // back to regId for the requestor.
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
                lines,
                // Phase 1 — header-level banking-match state, read straight from
                // SCDUL_PYMN_SUMR (legacy pymn_0006_002: LINK_MATCH_YN drives the
                // Match-vs-Unmatch header visibility; ACCT_SEQ_NO / BANK_TRNS_*
                // are the matched-ref). Read-only here; the candidate-search +
                // match/unmatch write path is a later phase. Payroll is the only
                // module that populates these (header-level 1:1 batch match).
                master.getLinkMatchYn(),
                master.getAcctSeqNo(),
                master.getBankTrnsShortId(),
                master.getBankTrnsFullId());
    }

    /**
     * Bucket the resolved detail lines into {@code [success, failed, inProgress]}
     * counts for the Payment Result detail header tabs (All / Success / Failed /
     * In Progress).
     *
     * <p>Classification mirrors the shared frontend classifier
     * {@code components/payment/shared/deriveResultStatus.ts} and the legacy
     * per-line rule {@code pymn_0006_002.js}, so the header counts always equal
     * the per-line chip distribution the user sees:
     * <ul>
     *   <li>blank {@code proc_stat} AND blank {@code rs_cd} &rarr; in&nbsp;progress</li>
     *   <li>{@code proc_stat="0"} AND {@code rs_cd="0000"} &rarr; success</li>
     *   <li>{@code proc_stat} &isin; {{@code 9},{@code 1},{@code 6}} &rarr; in&nbsp;progress</li>
     *   <li>everything else &rarr; failed</li>
     * </ul>
     *
     * <p>Byte-identical classification to
     * {@code VendorPaymentResultService.countByStatus} /
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
                r.rsCd(), r.rsMsg());
    }

    private static TransferResultRow toResultRow(
            ScdulPymnSumr s, long[] counts, Map<String, ExptResolMstr> apprByApprSeqNo) {
        BigDecimal succ = counts != null ? BigDecimal.valueOf(counts[0]) : null;
        BigDecimal err  = counts != null ? BigDecimal.valueOf(counts[1]) : null;
        String apprSeqNo = s.getApprSeqNo();
        ExptResolMstr appr = apprSeqNo != null && !apprSeqNo.isBlank()
                ? apprByApprSeqNo.get(apprSeqNo)
                : null;
        // Approval Ref (doc_no) — EXPT_RESOL_MSTR.DOC_NO (legacy R007), NOT
        // SCDUL_PYMN_SUMR.ERP_FILE_NUM (null in DEV → blank). F-4a fix.
        String docNo = appr != null ? appr.getDocNo() : null;
        // Approval Title — EXPT_RESOL_MSTR.TITL (legacy R007); blank-aware
        // fallback to SCDUL_PYMN_SUMR.TITL. F-3 fix.
        String ermTitl = appr != null ? appr.getTitl() : null;
        String approvalTitle = (ermTitl != null && !ermTitl.isBlank())
                ? ermTitl
                : s.getTitl();
        // PR1: Payroll Type — EXPT_RESOL_MSTR.APPR_FUND_TP ("0"=Salary /
        // "1"=Bonus / "9"/null blank on the FE).
        String apprFundTp = appr != null ? appr.getApprFundTp() : null;
        return new TransferResultRow(
                s.getScdulPymnNo(),
                s.getScdulPymnTypeCd(),
                s.getCcyCd(),
                approvalTitle,
                s.getOrgFileNm(),
                s.getTotalTranAmt(),
                s.getTotTrnsCnt(),
                succ,
                err,
                s.getTrnfYn(),
                // Date/Time — TRNF_DTM (dispatch datetime); wire field `reg_dtm`
                // unchanged (legacy TRNF_DTM AS REG_DTM alias). Date-display fix.
                s.getTrnfDtm(),
                s.getApprSeqNo(),
                docNo,
                apprFundTp);
    }

    /**
     * Project the full-dataset currency aggregate
     * ({@link ScdulPymnSumrRepository#searchDispatchedCurrencyTotals}) into the
     * wire {@link CurrencyTotal} list. The DB has already summed across the
     * entire filtered+dispatched set and grouped by currency (legacy
     * SCDUL_PYMN_SUMR_R008, F-1); this only does the COUNT-Long → BigDecimal
     * projection and null-safety. Mirrors
     * {@code InternalTransferService#mapCurrencyTotals}.
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
        Sort sort = PayrollPaymentResultSortField.parse(sortField)
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
