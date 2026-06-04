package com.kosign.wecms.domain.banking.service;

import com.kosign.wecms.common.auditlog.service.AuditLogContext;
import com.kosign.wecms.common.auditlog.service.AuditLogService;
import com.kosign.wecms.common.enums.AcctTypeCd;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.banking.dto.DailyCashReportResponse;
import com.kosign.wecms.domain.banking.repository.DailyCashAccountProjection;
import com.kosign.wecms.domain.banking.repository.DailyCashRecomputeRepository;
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
 * per-account detail, currency flow rollup) by <b>live recompute</b> from
 * {@code ACCT_MSTR} + {@code ACCT_TRNS_HSTR} at request time
 * ({@link DailyCashRecomputeRepository#findAccountsAsOf}), the refactor
 * equivalent of legacy {@code rept_0900_001_rec1}. This replaces the prior
 * read of the batch snapshot {@code DAILY_CASH_REPT_DAILY_SUMR_DETL}, so a
 * selected date returns accounts (with balances) even when the nightly
 * batch produced no snapshot for it — matching legacy, which recomputes via
 * the {@code rec1/rec2} DB functions on every read.
 *
 * <p>The per-currency summary (legacy {@code rec2}) is computed by
 * aggregating the recomputed account rows by account-type bucket — the
 * identical {@code SUM(CASE …)} legacy {@code rec2} performs over
 * {@code rec1}. Cash (legacy {@code rec3/rec4}) reads {@code CASH_REPY_MSTR},
 * which is out of the refactor PROD active-table scope, so {@code total_cash}
 * stays {@code 0}.
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

    private final DailyCashRecomputeRepository recomputeRepo;
    private final BankCdResolver bankResolver;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public DailyCashReportResponse search(String asOfDate, DailyCashCurrencyFilter filter) {
        final String bizKey = TenantContext.currentBizKey();
        final String refDate = toRefDate(asOfDate);

        // Live recompute (legacy rec1 equivalent) — reads ACCT_MSTR +
        // ACCT_TRNS_HSTR, not the batch snapshot, so any date with master
        // accounts returns rows. Currency filter mirrors the legacy
        // dynamic-0 (exact pin OR exclude tenant-local for FCY).
        List<DailyCashAccountProjection> rows = recomputeRepo.findAccountsAsOf(
                bizKey, refDate,
                filter.exact(),
                filter.exclude(LOCAL_CCY));

        List<DailyCashReportResponse.AccountRow> accounts = new ArrayList<>(rows.size());
        Map<String, CurrencyAccumulator> byCcy = new LinkedHashMap<>();

        for (DailyCashAccountProjection r : rows) {
            String ccy = trim(r.getCcyCd());
            String type = trim(r.getAcctTypeCd());

            accounts.add(new DailyCashReportResponse.AccountRow(
                    bizKey,
                    null, // last-collected-date — no snapshot on the live-recompute path
                    refDate,
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

        void add(String type, DailyCashAccountProjection r) {
            BigDecimal bal = nz(r.getBal());
            // DB-confirmed legacy mapping (rept_0900_001_rec2): 03/04 = current/demand,
            // 05/06 = savings, 02 = term/fixed deposit, 01 = loan. 07/08 are in NO legacy
            // summary bucket. CASH is never derived from ACCT_TYPE_CD — legacy TOTL_CASH
            // comes from cash_repy_mstr (rec3/rec4), which is out of the refactor active-
            // table scope, so `cash` stays 0. Unknown / unmapped codes contribute to
            // nothing (safe no-op).
            AcctTypeCd acctType = AcctTypeCd.fromCode(type).orElse(null);
            if (acctType != null) {
                switch (acctType) {
                    case LOAN                     -> loan    = loan.add(bal);    // 01
                    case DEPOSIT                  -> term    = term.add(bal);    // 02
                    case CURRENT_KHR, CURRENT_USD -> demand  = demand.add(bal);  // 03, 04
                    case SAVINGS_KHR, SAVINGS_USD -> savings = savings.add(bal); // 05, 06
                    case CURRENT_KRW, SAVINGS_KRW -> { /* 07, 08 — not in any legacy summary bucket */ }
                }
            }
            sndCnt = sndCnt.add(nz(r.getSndCnt()));
            sndAmt = sndAmt.add(nz(r.getSndAmt()));
            rcvCnt = rcvCnt.add(nz(r.getRcvCnt()));
            rcvAmt = rcvAmt.add(nz(r.getRcvAmt()));
        }

        private static BigDecimal nz(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }
    }
}
