package com.kosign.wecms.domain.payment.payrollpayment.service;

import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.common.web.SortDirection;
import com.kosign.wecms.domain.payment.dispatch.DispatchCountRepository;
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

        // `searchDispatched` filters on `trnf_dtm IS NOT NULL` so only
        // batches the user has actually clicked Transfer on appear here.
        // Same fix that landed for Internal Transfer / Vendor / Expense.
        Page<ScdulPymnSumr> rows = sumrRepository.searchDispatched(
                bizKey, ScdulPymnTypeCd.PAYROLL.code(),
                fromDtm, toDtm, srchWdPattern, trnfYnFilter,
                pageable);

        Map<String, long[]> counts = loadDispatchCounts(bizKey, rows.getContent());

        List<TransferResultRow> dtoRows = rows.getContent().stream()
                .map(s -> toResultRow(s, counts.get(s.getScdulPymnNo())))
                .toList();
        List<CurrencyTotal> totals = computeTotals(rows.getContent());

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
     * Payment Result detail. Mirrors legacy {@code pymn_0006_002_r001}.
     */
    @Transactional(readOnly = true)
    public TransferResultDetailResponse detail(String id) {
        String bizKey = requireTenant();
        ScdulPymnSumr master = sumrRepository
                .findByBizKeyAndScdulPymnNoAndScdulPymnTypeCd(
                        bizKey, id, ScdulPymnTypeCd.PAYROLL.code())
                .orElseThrow(() -> new BusinessException("WBE2060"));

        Sort lineSort = Sort.by(Sort.Order.asc("scdulPymnDetlNo"));
        List<TransferResultLine> lines = detlRepository
                .findByBizKeyAndScdulPymnNo(bizKey, id, lineSort)
                .stream()
                .map(PayrollPaymentResultService::toResultLine)
                .toList();

        return new TransferResultDetailResponse(
                master.getScdulPymnNo(),
                master.getOutBankCd(),
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
                null, null, null,
                lines);
    }

    /* ---------------------------------------------------------------- */

    private static TransferResultLine toResultLine(ScdulPymnSumrDetl d) {
        return new TransferResultLine(
                d.getScdulPymnDetlNo(),
                d.getTranAmt(),
                d.getAntDivCd(),
                d.getRcptNm(),
                d.getTarBankCd(),
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
                null, null, null, null, null, null);
    }

    private static TransferResultRow toResultRow(ScdulPymnSumr s, long[] counts) {
        BigDecimal succ = counts != null ? BigDecimal.valueOf(counts[0]) : null;
        BigDecimal err  = counts != null ? BigDecimal.valueOf(counts[1]) : null;
        return new TransferResultRow(
                s.getScdulPymnNo(),
                s.getScdulPymnTypeCd(),
                s.getCcyCd(),
                s.getTitl(),
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
        Sort sort = PayrollPaymentResultSortField.parse(sortField)
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
