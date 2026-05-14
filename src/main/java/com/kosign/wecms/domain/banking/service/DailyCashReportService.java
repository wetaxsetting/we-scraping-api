package com.kosign.wecms.domain.banking.service;

import com.kosign.wecms.common.auditlog.service.AuditLogContext;
import com.kosign.wecms.common.auditlog.service.AuditLogService;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.banking.dto.DailyCashReportResponse;
import com.kosign.wecms.domain.banking.entity.DailyCashReptDailySumrDetl;
import com.kosign.wecms.domain.banking.repository.DailyCashReptDailySumrDetlRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Daily Cash report — builds the three-tier response (currency summary,
 * per-account detail, currency flow rollup) from
 * {@code DAILY_CASH_REPT_DAILY_SUMR_DETL}. The legacy currency-level
 * summary and flow rollup tables are NOT in the PROD Active Table List,
 * so summary + flow are computed by aggregation over the detail rows
 * (column-equivalent to the legacy R003/R004 reads — same source data,
 * different aggregation path).
 *
 * Refactor slug: {@code daily-cash-report} — used as the audit
 * {@code WSVC_ID} and as the View Setting {@code WEB_SVC_ID}.
 */
@Service
@RequiredArgsConstructor
public class DailyCashReportService {

    /** Legacy account-type codes — see JEX {@code AcctTypeCd} enum. */
    private static final String TYPE_CASH        = "01"; // physical cash
    private static final String TYPE_DEMAND      = "11"; // current / demand deposit
    private static final String TYPE_SAVINGS     = "12"; // savings / passbook
    private static final String TYPE_TERM        = "13"; // term deposit
    private static final String TYPE_LOAN        = "21";

    /** Tenant local currency. VND today; resolves per-tenant when the
     *  refactor introduces base-currency settings (spec §7 OQ-3). */
    private static final String LOCAL_CCY = "VND";

    private final DailyCashReptDailySumrDetlRepository detlRepo;
    private final BankCdResolver bankResolver;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public DailyCashReportResponse search(String asOfDate, DailyCashCurrencyFilter filter) {
        final String bizKey = TenantContext.currentBizKey();
        final String refDate = toRefDate(asOfDate);

        List<DailyCashReptDailySumrDetl> rows = detlRepo.findRows(
                bizKey, refDate,
                filter.exact(),
                filter.exclude(LOCAL_CCY));

        List<DailyCashReportResponse.AccountRow> accounts = new ArrayList<>(rows.size());
        Map<String, CurrencyAccumulator> byCcy = new LinkedHashMap<>();

        for (DailyCashReptDailySumrDetl r : rows) {
            String ccy = trim(r.getCcyCd());
            String type = trim(r.getAcctTypeCd());

            accounts.add(new DailyCashReportResponse.AccountRow(
                    r.getBizKey(),
                    r.getLstCollDate(),
                    r.getRefDate(),
                    ccy,
                    type,
                    r.getBankCd(),
                    bankResolver.resolveBankNm(r.getBankCd()),
                    r.getAcctNo(),
                    r.getBal(),
                    r.getLoanNm(),
                    r.getExpiDt(),
                    r.getAgmtAmt(),
                    r.getSndCnt(),
                    r.getSndAmt(),
                    r.getRcvCnt(),
                    r.getRcvAmt()));

            CurrencyAccumulator acc = byCcy.computeIfAbsent(ccy, k -> new CurrencyAccumulator());
            acc.add(type, r);
        }

        List<DailyCashReportResponse.CurrencySummary> summary = new ArrayList<>(byCcy.size());
        List<DailyCashReportResponse.CurrencyFlow> flow = new ArrayList<>(byCcy.size());
        for (Map.Entry<String, CurrencyAccumulator> e : byCcy.entrySet()) {
            CurrencyAccumulator a = e.getValue();
            summary.add(new DailyCashReportResponse.CurrencySummary(
                    e.getKey(),
                    a.cash, a.demand, a.term, a.loan, a.savings));
            BigDecimal totalBalance = a.cash.add(a.demand).add(a.term).add(a.savings).add(a.loan);
            flow.add(new DailyCashReportResponse.CurrencyFlow(
                    e.getKey(),
                    totalBalance.subtract(a.rcvAmt).add(a.sndAmt), // opening = closing - inflow + outflow
                    totalBalance,
                    a.sndCnt, a.sndAmt,
                    a.rcvCnt, a.rcvAmt));
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

    private static final class CurrencyAccumulator {
        BigDecimal cash    = BigDecimal.ZERO;
        BigDecimal demand  = BigDecimal.ZERO;
        BigDecimal savings = BigDecimal.ZERO;
        BigDecimal term    = BigDecimal.ZERO;
        BigDecimal loan    = BigDecimal.ZERO;
        BigDecimal sndCnt  = BigDecimal.ZERO;
        BigDecimal sndAmt  = BigDecimal.ZERO;
        BigDecimal rcvCnt  = BigDecimal.ZERO;
        BigDecimal rcvAmt  = BigDecimal.ZERO;

        void add(String type, DailyCashReptDailySumrDetl r) {
            BigDecimal bal = nz(r.getBal());
            switch (type) {
                case TYPE_CASH    -> cash    = cash.add(bal);
                case TYPE_DEMAND  -> demand  = demand.add(bal);
                case TYPE_SAVINGS -> savings = savings.add(bal);
                case TYPE_TERM    -> term    = term.add(bal);
                case TYPE_LOAN    -> loan    = loan.add(bal);
                default           -> { /* unrecognised type — leave totals untouched */ }
            }
            sndCnt = sndCnt.add(nz(r.getSndCnt()));
            sndAmt = sndAmt.add(nz(r.getSndAmt()));
            rcvCnt = rcvCnt.add(nz(r.getRcvCnt()));
            rcvAmt = rcvAmt.add(nz(r.getRcvAmt()));
        }

        private static BigDecimal nz(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }
    }
}
