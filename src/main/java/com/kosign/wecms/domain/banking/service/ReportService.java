package com.kosign.wecms.domain.banking.service;

import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.banking.dto.CashFlowReport;
import com.kosign.wecms.domain.banking.dto.DailyCashReport;
import com.kosign.wecms.domain.banking.dto.DailyFinancialCmcReport;
import com.kosign.wecms.domain.banking.dto.DailyFinancialHsReport;
import com.kosign.wecms.domain.banking.entity.AcctMstr;
import com.kosign.wecms.domain.banking.repository.AcctMstrRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Banking Reports service.
 *
 * Real reads — Daily Financial CMC pulls account balances from ACCT_MSTR.
 *
 * Stubs — Daily Cash, Daily Financial HS, Cash Flow. All return
 * deterministic Sprint-1 datasets (account ledger from ACCT_MSTR where
 * applicable). The original BUSI_COND_DAILY_SUMR-backed summary path
 * was removed when the table was dropped (V31) for being out of the PROD
 * Active Table Scope; the real source for Sprint 2 is
 * DAILY_CASH_REPT_DAILY_SUMR_DETL / DAILY_FINCL_REPT_DAILY_SUMR_DETL.
 *
 * TODO: Sprint 2 — wire daily-cash summary to DAILY_CASH_REPT_DAILY_SUMR_DETL.
 * TODO: Sprint 2 — replace daily-financial-hs with real
 *       {@code rept_0017_001} ledger query.
 * TODO: Sprint 2 — replace cash-flow with real BCS batch port.
 */
@Service
@RequiredArgsConstructor
public class ReportService {

    private final AcctMstrRepository acctRepo;
    private final BankCdResolver bankResolver;

    /* -------------------- Daily Cash (STUB summary) -------------------- */

    @Transactional(readOnly = true)
    public DailyCashReport dailyCash(String asOfDt, String currencyScope) {
        final String bizKey = TenantContext.currentBizKey();

        // Sprint 1 STUB: per-currency summary returns empty until Sprint 2
        // wires the real source DAILY_CASH_REPT_DAILY_SUMR_DETL. The accounts
        // ledger below remains a real read against ACCT_MSTR.
        List<DailyCashReport.SummaryRow> summary = List.of();

        List<AcctMstr> accts = acctRepo.findByBizKeyAndActvYnOrderByBankCdAscAcctNoAsc(bizKey, "Y");
        List<DailyCashReport.AccountLedgerRow> accountLedger = new ArrayList<>();
        for (AcctMstr a : accts) {
            if (currencyScope != null && "VND".equalsIgnoreCase(currencyScope) && !"VND".equals(a.getCcyCd().trim())) continue;
            if (currencyScope != null && "FCY".equalsIgnoreCase(currencyScope) && "VND".equals(a.getCcyCd().trim())) continue;
            accountLedger.add(new DailyCashReport.AccountLedgerRow(
                    a.getBankCd() + "_" + a.getAcctNo(),
                    bankResolver.resolveBankNm(a.getBankCd()),
                    a.getAcctNo(),
                    a.getCcyCd().trim(),
                    0, "0",
                    0, "0",
                    AmountFormatter.wire(a.getBal())
            ));
        }

        return new DailyCashReport(asOfDt, currencyScope == null ? "ALL" : currencyScope, summary, List.of(), accountLedger);
    }

    /* ----- Daily Financial (CMC) (real read against seed data) ----- */

    @Transactional(readOnly = true)
    public DailyFinancialCmcReport dailyFinancial(String asOfDt, String currencyScope) {
        final String bizKey = TenantContext.currentBizKey();
        List<AcctMstr> accts = acctRepo.findByBizKeyAndActvYnOrderByBankCdAscAcctNoAsc(bizKey, "Y");

        // Financial Status — per-currency totals across accounts.
        Map<String, BigDecimal> totalByCcy = new LinkedHashMap<>();
        Map<String, BigDecimal> currentByCcy = new LinkedHashMap<>();
        Map<String, BigDecimal> savingByCcy = new LinkedHashMap<>();
        for (AcctMstr a : accts) {
            String ccy = a.getCcyCd().trim();
            BigDecimal bal = a.getBal() != null ? a.getBal() : BigDecimal.ZERO;
            totalByCcy.merge(ccy, bal, BigDecimal::add);
            String type = a.getAcctTypeCd() == null ? "" : a.getAcctTypeCd().trim();
            if ("11".equals(type)) currentByCcy.merge(ccy, bal, BigDecimal::add);
            else if ("12".equals(type)) savingByCcy.merge(ccy, bal, BigDecimal::add);
        }
        List<DailyFinancialCmcReport.FinancialStatusRow> status = new ArrayList<>();
        for (String ccy : totalByCcy.keySet()) {
            if (currencyScope != null && "VND".equalsIgnoreCase(currencyScope) && !"VND".equals(ccy)) continue;
            if (currencyScope != null && "FCY".equalsIgnoreCase(currencyScope) && "VND".equals(ccy))   continue;
            status.add(new DailyFinancialCmcReport.FinancialStatusRow(
                    ccy,
                    AmountFormatter.wire(totalByCcy.get(ccy)),
                    AmountFormatter.wire(currentByCcy.getOrDefault(ccy, BigDecimal.ZERO)),
                    AmountFormatter.wire(savingByCcy.getOrDefault(ccy, BigDecimal.ZERO))
            ));
        }

        // Current Accounts ledger — one row per active CURRENT account.
        List<DailyFinancialCmcReport.CurrentAccountRow> ledger = new ArrayList<>();
        for (AcctMstr a : accts) {
            String type = a.getAcctTypeCd() == null ? "" : a.getAcctTypeCd().trim();
            if (!"11".equals(type)) continue;
            String ccy = a.getCcyCd().trim();
            if (currencyScope != null && "VND".equalsIgnoreCase(currencyScope) && !"VND".equals(ccy)) continue;
            if (currencyScope != null && "FCY".equalsIgnoreCase(currencyScope) && "VND".equals(ccy)) continue;
            ledger.add(new DailyFinancialCmcReport.CurrentAccountRow(
                    a.getBankCd() + "_" + a.getAcctNo(),
                    bankResolver.resolveBankNm(a.getBankCd()),
                    a.getAcctNo(),
                    a.getAcctNickNm() != null ? a.getAcctNickNm() : a.getAcctNm(),
                    ccy,
                    0, "0",
                    0, "0",
                    0, "0",
                    0, "0",
                    AmountFormatter.wire(a.getBal())
            ));
        }

        return new DailyFinancialCmcReport(asOfDt, currencyScope == null ? "ALL" : currencyScope, status, ledger);
    }

    /* -------------------- Daily Financial HS (STUB) -------------------- */

    @Transactional(readOnly = true)
    public DailyFinancialHsReport dailyFinancialHs(String asOfDt, String bankCd) {
        // Sprint 1 STUB: return a deterministic fixed dataset that mirrors
        // the static-UI shape. See §12 of the Sprint 1 plan.
        // TODO: Sprint 2 — replace with the real rept_0017_001 ledger query
        //       grouped by bank + currency with sub-totals + TOTAL rows.
        final String bizKey = TenantContext.currentBizKey();
        // Touch the tenant context so a missing JWT trips Sprint 0 auth.
        if (bizKey == null) throw new IllegalStateException("missing tenant context");

        List<DailyFinancialHsReport.LedgerRow> rows = List.of(
                new DailyFinancialHsReport.LedgerRow("BIDV", "0001-00000569-14", "VND", "RCV", "수입금",       "Receipts",    "0",       "5000000",  "25000000", "DETAIL"),
                new DailyFinancialHsReport.LedgerRow("BIDV", "0001-00000569-14", "VND", "PAY", "지급금",       "Payments",    "1500000", "0",        "23500000", "DETAIL"),
                new DailyFinancialHsReport.LedgerRow("BIDV", "",                  "VND", "",    "은행 합계",   "Bank Total",  "1500000", "5000000",  "23500000", "SUBTOTAL"),
                new DailyFinancialHsReport.LedgerRow("WOORI","0001-00000523-06", "VND", "RCV", "수입금",       "Receipts",    "0",       "0",        "20000000", "DETAIL"),
                new DailyFinancialHsReport.LedgerRow("WOORI","",                  "VND", "",    "은행 합계",   "Bank Total",  "0",       "0",        "20000000", "SUBTOTAL"),
                new DailyFinancialHsReport.LedgerRow("",     "",                  "VND", "",    "총계",         "TOTAL",       "1500000", "5000000",  "43500000", "TOTAL")
        );
        return new DailyFinancialHsReport(asOfDt, bankCd, rows);
    }

    /* ------------------------- Cash Flow (STUB) ------------------------- */

    @Transactional(readOnly = true)
    public CashFlowReport cashFlow(String fromYm, String toYm) {
        // Sprint 1 STUB: deterministic monthly grid.
        // TODO: Sprint 2 — replace with the real BCS batch port that
        //       aggregates ACCT_TRNS_HSTR (PROD active) +
        //       DAILY_FINCL_REPT_DAILY_SUMR_DETL (PROD active).
        final String bizKey = TenantContext.currentBizKey();
        if (bizKey == null) throw new IllegalStateException("missing tenant context");

        List<String> months = List.of("2025-01", "2025-02", "2025-03", "2025-04", "2025-05");
        List<String> opening = List.of("100000000", "120000000", "135000000", "140000000", "155000000");

        CashFlowReport.DetailRow d1 = new CashFlowReport.DetailRow(uuid(), "REV-001", "Sales Revenue",
                List.of("80000000", "85000000", "90000000", "95000000", "100000000"));
        CashFlowReport.DetailRow d2 = new CashFlowReport.DetailRow(uuid(), "REV-002", "Service Revenue",
                List.of("20000000", "22000000", "25000000", "27000000", "30000000"));
        CashFlowReport.Subgroup revSub = new CashFlowReport.Subgroup(uuid(), "매출", "Revenue", List.of(d1, d2));
        CashFlowReport.Group revenue = new CashFlowReport.Group(uuid(), "영업", "REVENUE",
                List.of(revSub),
                List.of("100000000", "107000000", "115000000", "122000000", "130000000"));

        CashFlowReport.DetailRow e1 = new CashFlowReport.DetailRow(uuid(), "EXP-001", "Salaries",
                List.of("40000000", "42000000", "44000000", "46000000", "48000000"));
        CashFlowReport.DetailRow e2 = new CashFlowReport.DetailRow(uuid(), "EXP-002", "Office Rent",
                List.of("12000000", "12000000", "12000000", "12000000", "12000000"));
        CashFlowReport.Subgroup expSub = new CashFlowReport.Subgroup(uuid(), "비용", "Expenses", List.of(e1, e2));
        CashFlowReport.Group expense = new CashFlowReport.Group(uuid(), "지출", "Expenses",
                List.of(expSub),
                List.of("52000000", "54000000", "56000000", "58000000", "60000000"));

        return new CashFlowReport(
                months,
                opening,
                List.of(revenue, expense),
                List.of("48000000", "53000000", "59000000", "64000000", "70000000"),
                List.of("0", "0", "0", "0", "0"),
                List.of("148000000", "173000000", "194000000", "204000000", "225000000")
        );
    }

    private static String uuid() { return UUID.randomUUID().toString(); }
}
