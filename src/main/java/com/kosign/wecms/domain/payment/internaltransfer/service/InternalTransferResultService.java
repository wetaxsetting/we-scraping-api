package com.kosign.wecms.domain.payment.internaltransfer.service;

import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.common.web.SortDirection;
import com.kosign.wecms.domain.payment.approvalbox.entity.ExptResolMstr;
import com.kosign.wecms.domain.payment.approvalbox.repository.ExptResolMstrRepository;
import com.kosign.wecms.domain.payment.common.TransferLineDetailMapper;
import com.kosign.wecms.domain.payment.dispatch.DispatchCountRepository;
import com.kosign.wecms.domain.payment.dispatch.service.DispatchResult;
import com.kosign.wecms.domain.payment.dispatch.service.DispatchResultResolver;
import com.kosign.wecms.domain.payment.dispatch.DispatchCountRow;
import com.kosign.wecms.domain.payment.internaltransfer.dto.CurrencyTotal;
import com.kosign.wecms.domain.payment.internaltransfer.dto.TransferResultDetailResponse;
import com.kosign.wecms.domain.payment.internaltransfer.dto.TransferResultLine;
import com.kosign.wecms.domain.payment.internaltransfer.dto.TransferResultListResponse;
import com.kosign.wecms.domain.payment.internaltransfer.dto.TransferResultRow;
import com.kosign.wecms.domain.payment.internaltransfer.entity.ScdulPymnSumr;
import com.kosign.wecms.domain.payment.internaltransfer.entity.ScdulPymnSumrDetl;
import com.kosign.wecms.domain.payment.internaltransfer.repository.ScdulPymnSumrDetlRepository;
import com.kosign.wecms.domain.payment.internaltransfer.repository.ScdulPymnSumrRepository;
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
 * Payment &gt; Internal Transfer — Transfer Result read-only service.
 * Mirrors the legacy read paths of:
 *
 * <ul>
 *   <li>{@code pymn_0010_001_r001} — result list (legacy delegates to
 *       {@code PymnHelper.pymn_0002_001_r001(ScdulPymnTypeCd.INTERNAL)}).</li>
 *   <li>{@code pymn_0010_002_r001} — result detail.</li>
 * </ul>
 *
 * <p>Phase A scope: both endpoints return data from
 * {@code SCDUL_PYMN_SUMR} + {@code SCDUL_PYMN_SUMR_DETL}. Dispatch-level
 * fields ({@code proc_stat}, {@code rs_cd}, {@code rs_msg},
 * {@code proc_dttm}, {@code rslt_dttm}) come from the join with
 * {@code TB_PAY_TARGET} / {@code TB_PAY_FILE_DATA} which Phase A defers
 * — those columns ship as {@code null}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InternalTransferResultService {

    private final ScdulPymnSumrRepository      sumrRepository;
    private final ScdulPymnSumrDetlRepository  detlRepository;
    private final DispatchCountRepository      dispatchCountRepository;
    /** Shared bank-name resolver — same chain used by the Scheduled
     *  detail (BANK_INFM → V86 NAPAS/CITAD function →
     *  CITAD_BANK_CD canonical fallback). Surfaces `tarBankNm` on
     *  each Result line so the Deposit Bank cell renders
     *  "970424 - ShinhanBank" instead of just "970424". */
    private final TransferLineDetailMapper     transferLineDetailMapper;
    /** Phase 2 dispatch-result resolver — surfaces PROC_STAT / RS_CD /
     *  RS_MSG / PROC_DTTM / RSLT_DTTM per line by reading back
     *  TB_PAY_TARGET / TB_PAY_FILE_MST / TB_PAY_FILE_DATA / TB_FIRM_TRAN.
     *  Mirrors the legacy {@code SCDUL_PYMN_SUMR_R006} IDO query. */
    private final DispatchResultResolver       dispatchResultResolver;
    /** Approval master — read-only here to surface the requester name
     *  ({@code DRAFT_USER_NM}) on the result-detail header (legacy
     *  {@code pymn_0010_002} Requestor field). */
    private final ExptResolMstrRepository      exptResolMstrRepository;

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

        // trnfStts filter — null shows every state, "Y" filters to
        // dispatched batches. Phase A defers the in-progress / pending
        // states which require the dispatch tables.
        String trnfYnFilter = emptyToNull(trnfStts);

        // Use `searchDispatched` (not `search`) so only batches the user
        // has actually clicked Transfer on appear here — `trnf_dtm IS NOT
        // NULL` is the canonical marker. Without that filter, every
        // freshly-uploaded batch that's still in the approval flow leaks
        // into Transfer Result reading as "In Progress" even though no
        // dispatch has been attempted.
        Page<ScdulPymnSumr> rows = sumrRepository.searchDispatched(
                bizKey, ScdulPymnTypeCd.INTERNAL.code(),
                fromDtm, toDtm, emptyToNull(srchWd), trnfYnFilter,
                pageable);

        Map<String, long[]> counts = loadDispatchCounts(bizKey, rows.getContent());

        List<TransferResultRow> dtoRows = rows.getContent().stream()
                .map(s -> toResultRow(s, counts.get(s.getScdulPymnNo())))
                .toList();
        List<CurrencyTotal> totals = computeTotals(rows.getContent());

        log.info("feature=internal-transfer result-list bizKey={} total={} page={} size={}",
                bizKey, rows.getTotalElements(), page, pageable.getPageSize());
        return new TransferResultListResponse(
                rows.getTotalElements(), page, pageable.getPageSize(),
                totals, dtoRows);
    }

    /**
     * Pull per-batch (succ, err) counts from the dispatch tables. Falls
     * back to an empty map when the dispatch tables aren't reachable
     * (fresh local DB without TB_PAY_TARGET / TB_PAY_FILE_MST /
     * TB_PAY_FILE_DATA) so the page still renders — chips simply
     * disappear and the row reads as "In Progress" via the frontend's
     * count-math rule.
     */
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
            log.warn("feature=internal-transfer dispatch-count query failed — degrading to null counts ({})",
                    e.getMessage());
            return Collections.emptyMap();
        }
    }

    @Transactional(readOnly = true)
    public TransferResultDetailResponse detail(String id) {
        String bizKey = requireTenant();
        ScdulPymnSumr master = sumrRepository
                .findByBizKeyAndScdulPymnNoAndScdulPymnTypeCd(
                        bizKey, id, ScdulPymnTypeCd.INTERNAL.code())
                .orElseThrow(() -> new BusinessException("WBE2060"));

        Sort lineSort = ScdulPymnSumrDetlRepository.DETL_NO_NUMERIC_ASC;
        List<ScdulPymnSumrDetl> detailRows = detlRepository
                .findByBizKeyAndScdulPymnNo(bizKey, id, lineSort);
        // Phase 2: one bulk lookup per detail() call against TB_PAY_* +
        // TB_FIRM_TRAN so the per-line PROC_STAT / RS_CD / RS_MSG /
        // PROC_DTTM / RSLT_DTTM round-trip to the frontend.
        Map<String, DispatchResult> resultByDetlNo =
                dispatchResultResolver.resolve(master, detailRows);
        List<TransferResultLine> lines = detailRows.stream()
                .map(d -> toResultLine(d, resultByDetlNo.getOrDefault(
                        d.getScdulPymnDetlNo(), DispatchResult.EMPTY)))
                .toList();

        // G2: header tab counts (All / Success / Failed / In Progress) —
        // mirrors legacy pymn_0010_002_r001's TOT_CNT/SUC_CNT/ERR_CNT/PRC_CNT.
        // Derived from the already-resolved per-line statuses so the header
        // numbers equal the per-line chip distribution the user sees.
        long[] counts = countByStatus(lines);

        // Withdrawal bank name — same resolver the per-line Deposit Bank cell
        // uses (BANK_INFM → NAPAS/CITAD function → CITAD canonical fallback).
        // null when unresolvable; the frontend falls back to the raw code.
        // Mirrors legacy pymn_0010_002 OUT_BANK_NM.
        String outBankNm = transferLineDetailMapper.resolveBankNm(master.getOutBankCd());

        // Requester name — legacy pymn_0010_002 shows EXPT_RESOL_MSTR.DRAFT_USER_NM.
        // Looked up by APPR_SEQ_NO (single-element IN reuses the existing finder,
        // no new repo method). null when the batch has no approval request yet;
        // the frontend falls back to reg_id.
        String apprSeqNo = master.getApprSeqNo();
        String requestor = (apprSeqNo != null && !apprSeqNo.isBlank())
                ? exptResolMstrRepository
                        .findByBizKeyAndApprSeqNoIn(bizKey, java.util.List.of(apprSeqNo))
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
     * counts for the Transfer Result detail header tabs (All / Success /
     * Failed / In Progress).
     *
     * <p>Classification mirrors the shared frontend classifier
     * {@code components/payment/shared/deriveResultStatus.ts:69-82} and the
     * legacy per-line rule {@code pymn_0010_002.js:238-253}, so the header
     * counts always equal the per-line chip distribution the user sees:
     * <ul>
     *   <li>{@code proc_stat="0"} AND {@code rs_cd="0000"} &rarr; success</li>
     *   <li>{@code proc_stat} &isin; {{@code 9},{@code 1},{@code 6}} &rarr; in&nbsp;progress</li>
     *   <li>blank {@code proc_stat} AND blank {@code rs_cd} &rarr; in&nbsp;progress
     *       (dispatched batch whose bank-side result has not landed yet)</li>
     *   <li>everything else ({@code proc_stat="3"}, or {@code "0"} with a
     *       non-{@code "0000"} {@code rs_cd}, or a blank {@code proc_stat}
     *       with a non-blank {@code rs_cd}, or a stray code) &rarr; failed</li>
     * </ul>
     *
     * <p>Note: {@code ProcStatKey.forCode(String)} is intentionally NOT
     * reused here — it keys off {@code proc_stat} alone and would mark the
     * legacy {@code proc_stat="0"} + {@code rs_cd != "0000"} case as success,
     * diverging from the chip. The combined {@code proc_stat} + {@code rs_cd}
     * rule above is the chip-consistent classification.
     *
     * <p>Invariant: {@code success + failed + inProgress == lines.size()}
     * (= {@code TOT_TRNS_CNT}).
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

    private String requireTenant() {
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
        Sort sort = InternalTransferResultSortField.parse(sortField)
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

    private static String emptyToNull(String s) {
        if (s == null) return null;
        String trimmed = s.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
