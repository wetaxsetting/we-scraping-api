package com.kosign.wecms.domain.banking.service;

import com.kosign.wecms.common.auditlog.service.AuditLogContext;
import com.kosign.wecms.common.auditlog.service.AuditLogService;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.banking.dto.DailyCashReportResponse;
import com.kosign.wecms.domain.banking.repository.DailyCashRec1Row;
import com.kosign.wecms.domain.banking.repository.DailyCashRec2Row;
import com.kosign.wecms.domain.banking.repository.DailyCashRec3Row;
import com.kosign.wecms.domain.banking.repository.DailyCashRec4Row;
import com.kosign.wecms.domain.banking.repository.DailyCashReportFunctionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Daily Cash report — builds the three-tier response (currency summary,
 * per-account detail, currency flow rollup) directly from the legacy DB
 * functions {@code rept_0900_001_rec1..rec4}, the source of truth (same stance
 * as Cash Flow and Daily Financial HS). All bucketing and cash math live in
 * the functions; the service only projects their output onto the wire shape
 * and joins {@code rec4} cash onto the {@code rec2} summary:
 *
 * <ul>
 *   <li>{@code rec1} → {@code accounts} (per-account detail)</li>
 *   <li>{@code rec2} → {@code currencySummary} bank-account buckets
 *       (demand / savings / term / loan)</li>
 *   <li>{@code rec4} → {@code currencySummary.totalCash} (joined by currency)</li>
 *   <li>{@code rec3} → {@code currencyFlow} (opening / closing balance + movements)</li>
 * </ul>
 *
 * <p>The legacy currency filter is applied <b>outside</b> the functions (the
 * functions take no currency arg) via
 * {@link DailyCashReportFunctionRepository}: ALL = no filter, exact currency =
 * {@code ccy_cd = currency}, FCY = {@code ccy_cd <> 'VND'} — see
 * {@link DailyCashCurrencyFilter}. {@code reqDate2} mirrors {@code reqDate}.
 *
 * <p>{@code rec3}/{@code rec4} read {@code CASH_REPY_MSTR}, which is empty in
 * the R&amp;D DB — a data condition, not a code fault. The service maps empty
 * results to an empty currency-flow list and {@code totalCash = 0}.
 *
 * <p>This replaces the prior Java live-recompute path
 * ({@code DailyCashRecomputeRepository}) + Java account-type bucketing
 * ({@code CurrencyAccumulator}); both are bypassed (no longer wired here) and
 * retained only as cleanup candidates.
 *
 * Refactor slug: {@code daily-cash-report} — used as the audit
 * {@code WSVC_ID} and as the View Setting {@code WEB_SVC_ID}.
 */
@Service
@RequiredArgsConstructor
public class DailyCashReportService {

    /** Tenant local currency. VND today; resolves per-tenant when the
     *  refactor introduces base-currency settings (spec §7 OQ-3). */
    private static final String LOCAL_CCY = "VND";

    private final DailyCashReportFunctionRepository fnRepo;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public DailyCashReportResponse search(String asOfDate, DailyCashCurrencyFilter filter) {
        final String bizKey = TenantContext.currentBizKey();
        final String refDate = toRefDate(asOfDate);
        final String reqDate2 = refDate;                 // legacy passes req_date2 = req_date
        final String ccyExact = filter.exact();          // exact-currency pin, or null
        final String ccyExclude = filter.exclude(LOCAL_CCY); // FCY excludes local, else null

        // rec1 → per-account detail (bank name resolved inside the function)
        List<DailyCashRec1Row> rec1 = fnRepo.rec1(bizKey, refDate, reqDate2, ccyExact, ccyExclude);
        List<DailyCashReportResponse.AccountRow> accounts = new ArrayList<>(rec1.size());
        for (DailyCashRec1Row r : rec1) {
            accounts.add(new DailyCashReportResponse.AccountRow(
                    trim(r.getBizKey()),
                    null, // last-collected-date — DB-function path carries no batch snapshot timestamp
                    trim(r.getRefDate()),
                    trim(r.getCcyCd()),
                    trim(r.getAcctTypeCd()),
                    r.getBankCd(),
                    r.getBankNm(),
                    r.getAcctNo(),
                    r.getBal(),
                    r.getLoanNm(),
                    r.getExpiDt(),
                    r.getAgmtAmt(),
                    r.getSndCnt(),
                    r.getSndAmt(),
                    r.getRcvCnt(),
                    r.getRcvAmt()));
        }

        // rec4 → per-currency total cash (empty when CASH_REPY_MSTR is empty)
        Map<String, BigDecimal> cashByCcy = new LinkedHashMap<>();
        for (DailyCashRec4Row r : fnRepo.rec4(bizKey, refDate, reqDate2, ccyExact, ccyExclude)) {
            cashByCcy.put(trim(r.getCcyCd()), nz(r.getTotlCash()));
        }

        // rec2 → per-currency summary buckets, with rec4 cash joined by currency
        List<DailyCashReportResponse.CurrencySummary> summary = new ArrayList<>();
        Set<String> summaryCcys = new LinkedHashSet<>();
        for (DailyCashRec2Row r : fnRepo.rec2(bizKey, refDate, reqDate2, ccyExact, ccyExclude)) {
            String ccy = trim(r.getCcyCd());
            summaryCcys.add(ccy);
            summary.add(new DailyCashReportResponse.CurrencySummary(
                    ccy,
                    cashByCcy.getOrDefault(ccy, BigDecimal.ZERO), // totalCash ← rec4
                    nz(r.getTotlNormAmt()),  // totalDemandDeposit ← 03/04
                    nz(r.getTotlAcctAmt()),  // totalTermDeposit   ← 02
                    nz(r.getTotlLoanAmt()),  // totalLoan          ← 01
                    nz(r.getTotlSavAmt())    // totalSavings       ← 05/06
            ));
        }
        // Cash-only currencies (present in rec4 but with no bank accounts in rec2):
        // surface them so their cash total is not lost. Inert while CASH_REPY_MSTR is empty.
        for (Map.Entry<String, BigDecimal> e : cashByCcy.entrySet()) {
            if (!summaryCcys.contains(e.getKey())) {
                summary.add(new DailyCashReportResponse.CurrencySummary(
                        e.getKey(), e.getValue(),
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
            }
        }

        // rec3 → per-currency cash flow (opening / closing balance + same-day movements)
        List<DailyCashReportResponse.CurrencyFlow> flow = new ArrayList<>();
        for (DailyCashRec3Row r : fnRepo.rec3(bizKey, refDate, reqDate2, ccyExact, ccyExclude)) {
            flow.add(new DailyCashReportResponse.CurrencyFlow(
                    trim(r.getCcyCd()),
                    nz(r.getBefDateBal()), // opening balance
                    nz(r.getBal()),        // closing balance
                    nz(r.getSndCnt()),
                    nz(r.getSndAmt()),
                    nz(r.getRcvCnt()),
                    nz(r.getRcvAmt())));
        }

        auditLogService.append(AuditLogContext.builder()
                .wsvcId("daily-cash-report")
                .refNo("DAILY_CASH_REPT_DAILY_SUMR_DETL")
                .refNo1(refDate)
                .refNo2(filter.name())
                .detailKey("audit.daily_cash_report.search.detail")
                .build());

        return new DailyCashReportResponse(asOfDate, filter.name(), summary, accounts, flow);
    }

    /** Convert ISO {@code yyyy-MM-dd} to legacy {@code yyyyMMdd}. */
    private static String toRefDate(String iso) {
        if (iso == null) return null;
        return iso.replace("-", "");
    }

    private static String trim(String s) { return s == null ? "" : s.trim(); }

    private static BigDecimal nz(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }
}
