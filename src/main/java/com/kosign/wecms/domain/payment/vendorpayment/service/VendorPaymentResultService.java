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
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

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

        String trnfYnFilter = emptyToNull(trnfStts);
        String srchWdPattern = buildLikePattern(srchWd);

        // Use `searchDispatched` (not `search`) so only batches the user
        // has actually clicked Transfer on appear here — `trnf_dtm IS NOT
        // NULL` is the canonical marker. Without that filter, every
        // freshly-uploaded batch that's still in the approval flow leaks
        // into Payment Result reading as "In Progress" even though no
        // dispatch has been attempted. Same fix that landed for Internal
        // Transfer's TransferResultService.
        Page<ScdulPymnSumr> rows = sumrRepository.searchDispatched(
                bizKey, ScdulPymnTypeCd.VENDOR.code(),
                fromDtm, toDtm, srchWdPattern, trnfYnFilter,
                pageable);

        Map<String, long[]> counts = loadDispatchCounts(bizKey, rows.getContent());

        List<TransferResultRow> dtoRows = rows.getContent().stream()
                .map(s -> toResultRow(s, counts.get(s.getScdulPymnNo())))
                .toList();
        List<CurrencyTotal> totals = computeTotals(rows.getContent());

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

        Sort lineSort = Sort.by(Sort.Order.asc("scdulPymnDetlNo"));
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

        // G2: requester display name (legacy pymn_0002_002 EXPT_RESOL_MSTR.DRAFT_USER_NM),
        // looked up by APPR_SEQ_NO. null when the batch has no approval request yet —
        // the frontend falls back to regId. Mirrors InternalTransferResultService.
        String apprSeqNo = master.getApprSeqNo();
        String requestor = (apprSeqNo != null && !apprSeqNo.isBlank())
                ? exptResolMstrRepository
                        .findByBizKeyAndApprSeqNoIn(bizKey, List.of(apprSeqNo))
                        .stream()
                        .findFirst()
                        .map(ExptResolMstr::getDraftUserNm)
                        .filter(s -> s != null && !s.isBlank())
                        .orElse(null)
                : null;

        return new TransferResultDetailResponse(
                master.getScdulPymnNo(),
                master.getOutBankCd(),
                outBankNm,
                master.getWdrlActNo(),
                master.getTitl(),
                master.getTotalTranAmt(),
                master.getTotTrnsCnt(),
                master.getApprSeqNo(),
                master.getErpFileNum(),
                master.getCcyCd(),
                master.getWdrlCcyCd(),
                master.getTrnfYn(),
                master.getTrnfDtm(),
                master.getOrgFileNm(),
                master.getFileSize(),
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

    private static TransferResultRow toResultRow(ScdulPymnSumr s, long[] counts) {
        BigDecimal succ = counts != null ? BigDecimal.valueOf(counts[0]) : null;
        BigDecimal err  = counts != null ? BigDecimal.valueOf(counts[1]) : null;
        return new TransferResultRow(
                s.getScdulPymnNo(),
                s.getScdulPymnTypeCd(),
                s.getCcyCd(),
                s.getTitl(),
                s.getOrgFileNm(),
                s.getTotalTranAmt(),
                s.getTotTrnsCnt(),
                succ,
                err,
                s.getTrnfYn(),
                s.getRegDtm(),
                s.getApprSeqNo(),
                s.getErpFileNum());
    }

    private static List<CurrencyTotal> computeTotals(List<ScdulPymnSumr> rows) {
        Map<String, BigDecimal> amountByCcy = new TreeMap<>();
        Map<String, BigDecimal> countByCcy  = new TreeMap<>();
        Map<String, BigDecimal> trnsByCcy   = new TreeMap<>();
        for (ScdulPymnSumr r : rows) {
            String ccy = Objects.requireNonNullElse(r.getCcyCd(), "");
            amountByCcy.merge(ccy, Objects.requireNonNullElse(r.getTotalTranAmt(), BigDecimal.ZERO), BigDecimal::add);
            countByCcy.merge(ccy, BigDecimal.ONE, BigDecimal::add);
            trnsByCcy.merge(ccy, Objects.requireNonNullElse(r.getTotTrnsCnt(), BigDecimal.ZERO), BigDecimal::add);
        }
        return amountByCcy.entrySet().stream()
                .sorted(Comparator.comparing(Map.Entry::getKey))
                .map(e -> new CurrencyTotal(
                        e.getValue(),
                        countByCcy.get(e.getKey()),
                        e.getKey(),
                        trnsByCcy.get(e.getKey())))
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
                            .and(Sort.by(Sort.Order.desc("regDtm")));
                })
                .orElseGet(() -> Sort.by(Sort.Order.desc("regDtm"),
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
