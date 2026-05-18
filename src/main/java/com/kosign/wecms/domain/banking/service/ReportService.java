package com.kosign.wecms.domain.banking.service;

import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.banking.dto.DailyCashReport;
import com.kosign.wecms.domain.banking.dto.DailyFinancialCmcReport;
import com.kosign.wecms.domain.banking.dto.DailyFinancialHsReport;
import com.kosign.wecms.domain.banking.dto.DailyFinancialTransactionsReport;
import com.kosign.wecms.domain.banking.entity.AcctMstr;
import com.kosign.wecms.domain.banking.entity.AcctTrnsHstr;
import com.kosign.wecms.domain.banking.repository.AcctMstrRepository;
import com.kosign.wecms.domain.banking.repository.AcctTrnsHstrRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    private final AcctTrnsHstrRepository trnsRepo;
    private final BankCdResolver bankResolver;

    /**
     * ACCT_TYPE_CD partition for the Daily Financial Status header row.
     * Mirrors the legacy {@code AcctTypeCd} enum
     * ({@code WECMS DEV/WECMS/src/wabooks/enums/AcctTypeCd.java}):
     * 03 / 04 / 07 are the three "current" variants (KHR / USD / KRW),
     * 05 / 06 / 08 are the three "savings" variants. Code 02 (Fixed /
     * Time Deposit) and 01 (Loan) are intentionally excluded — neither
     * the legacy Financial Status block nor the live old-project
     * screenshot surfaces them.
     */
    private static final Set<String> CURRENT_ACCOUNT_TYPE_CODES = Set.of("03", "04", "07");
    private static final Set<String> SAVING_ACCOUNT_TYPE_CODES = Set.of("05", "06", "08");

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

    /**
     * Daily Financial CMC report. Reads account balances from
     * {@code ACCT_MSTR}; per-account transactions ({@code REC2_1} in the
     * legacy WSVC contract) and the per-currency flow rollup
     * ({@code REC3}) are emitted as empty / zero-valued skeletons —
     * Sprint 2 ports the real {@code ACCT_TRNS_HSTR} + summary-table reads.
     *
     * @param activeAccountsOnly       maps to legacy {@code ACTV_ACCT_ONLY_YN}
     *                                 — when true, only {@code ACTV_YN='Y'}
     *                                 rows are returned (the existing
     *                                 repository finder already filters).
     * @param includeInternalTransfers maps to legacy
     *                                 {@code INCL_INTL_TRNS_YN}; reserved
     *                                 for the Sprint 2 transactions read.
     */
    @Transactional(readOnly = true)
    public DailyFinancialCmcReport dailyFinancial(
            String asOfDt,
            String currencyScope,
            boolean activeAccountsOnly,
            boolean includeInternalTransfers) {
        final String bizKey = TenantContext.currentBizKey();
        List<AcctMstr> accts = activeAccountsOnly
                ? acctRepo.findByBizKeyAndActvYnOrderByBankCdAscAcctNoAsc(bizKey, "Y")
                : acctRepo.findByBizKeyAndActvYnOrderByBankCdAscAcctNoAsc(bizKey, "Y");
        // NOTE: when activeAccountsOnly=false the legacy panel widens to
        // include inactive accounts. The current Sprint 1 repository only
        // exposes the active-filtered finder; the wider read lands when
        // the repository grows a `findAllByBizKeyOrderBy…` variant.

        // Financial Status — per-currency totals across accounts.
        Map<String, BigDecimal> totalByCcy = new LinkedHashMap<>();
        Map<String, BigDecimal> currentByCcy = new LinkedHashMap<>();
        Map<String, BigDecimal> savingByCcy = new LinkedHashMap<>();
        for (AcctMstr a : accts) {
            String ccy = a.getCcyCd().trim();
            BigDecimal bal = a.getBal() != null ? a.getBal() : BigDecimal.ZERO;
            totalByCcy.merge(ccy, bal, BigDecimal::add);
            String type = a.getAcctTypeCd() == null ? "" : a.getAcctTypeCd().trim();
            if (CURRENT_ACCOUNT_TYPE_CODES.contains(type)) {
                currentByCcy.merge(ccy, bal, BigDecimal::add);
            } else if (SAVING_ACCOUNT_TYPE_CODES.contains(type)) {
                savingByCcy.merge(ccy, bal, BigDecimal::add);
            }
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

        // Accounts ledger — one row per active CURRENT or SAVINGS account.
        // The legacy Daily Financial panel renders two tables ("Current
        // Accounts" + "Saving Accounts"); both share the same row shape so
        // the backend ships them together and the frontend partitions on
        // `accountTypeCd` ∈ CURRENT_ACCOUNT_TYPE_CODES vs.
        // SAVING_ACCOUNT_TYPE_CODES. Per-account TRANSACTIONS list is an
        // empty skeleton in Sprint 1.
        List<DailyFinancialCmcReport.CurrentAccountRow> ledger = new ArrayList<>();
        for (AcctMstr a : accts) {
            String type = a.getAcctTypeCd() == null ? "" : a.getAcctTypeCd().trim();
            if (!CURRENT_ACCOUNT_TYPE_CODES.contains(type)
                    && !SAVING_ACCOUNT_TYPE_CODES.contains(type)) continue;
            String ccy = a.getCcyCd().trim();
            if (currencyScope != null && "VND".equalsIgnoreCase(currencyScope) && !"VND".equals(ccy)) continue;
            if (currencyScope != null && "FCY".equalsIgnoreCase(currencyScope) && "VND".equals(ccy)) continue;
            ledger.add(new DailyFinancialCmcReport.CurrentAccountRow(
                    a.getBankCd() + "_" + a.getAcctNo(),
                    bankResolver.resolveBankNm(a.getBankCd()),
                    a.getBankCd(),
                    a.getAcctNo(),
                    a.getAcctNickNm() != null ? a.getAcctNickNm() : a.getAcctNm(),
                    ccy,
                    type,
                    a.getActvYn(),
                    0, "0",
                    0, "0",
                    0, "0",
                    0, "0",
                    AmountFormatter.wire(a.getBal()),
                    List.of()
            ));
        }

        // CURRENCY_FLOW (REC3 in the legacy WSVC contract) is intentionally
        // not emitted — the live JEX `rept_0015_001` panel doesn't render
        // the per-currency flow rollup, so the refactor frontend drops the
        // section and the backend stops computing it to match production.

        return new DailyFinancialCmcReport(
                asOfDt,
                currencyScope == null ? "ALL" : currencyScope,
                activeAccountsOnly,
                includeInternalTransfers,
                status,
                ledger);
    }

    /* -------------- Daily Financial — Transaction History popup -------------- */

    /**
     * Per-account transaction history for the Daily Financial popup.
     * Mirrors legacy {@code rept_0015_002_r001} — the user clicks Bank
     * / Account No. / Account Nickname in the parent ledger and the
     * popup lists every transaction on that account on the selected
     * as-of date, with running balance, sums, and pagination.
     *
     * <p>The legacy panel filters internal-transfer rows
     * ({@code RCV_PAY_USAG_CD = '09'}) when the parent's "Include
     * internal transfers" checkbox is off; that flag flows through as
     * {@code includeInternalTransfers}. Sprint 1 entity does not yet
     * carry {@code RCV_PAY_USAG_CD} (lives on {@code ACCT_TRNS_RCV_PAY_INFM}),
     * so internal-transfer filtering is a no-op today and lands when
     * the join is wired.
     *
     * <p>Rows are ordered ASC by transaction time so the running balance
     * column reads chronologically (legacy parity — Time column is in
     * ascending order on screen).
     *
     * @param asOfDt                   yyyyMMdd
     * @param bankCd                   bank code of the clicked row
     * @param acctNo                   account number of the clicked row
     * @param includeInternalTransfers reserved — flows through from the
     *                                 parent checkbox; will gate the
     *                                 {@code RCV_PAY_USAG_CD = '09'}
     *                                 filter once the join is added
     * @param page                     1-based page index (default 1)
     * @param size                     rows per page (default 15)
     */
    @Transactional(readOnly = true)
    public DailyFinancialTransactionsReport dailyFinancialTransactions(
            String asOfDt,
            String bankCd,
            String acctNo,
            boolean includeInternalTransfers,
            int page,
            int size) {
        final String bizKey = TenantContext.currentBizKey();
        final int safePage = Math.max(1, page);
        final int safeSize = Math.max(1, size);

        /* Use the existing date-range finder with from == to == asOfDt so
           the result is exactly the transactions for the selected day.
           The finder orders TRSC_DT DESC, TRSC_TM DESC; we re-sort in
           ASC order in memory so the running balance column reads
           chronologically (matches the legacy popup). */
        List<AcctTrnsHstr> day = trnsRepo
                .findByBizKeyAndBankCdAndAcctNoAndTrscDtBetweenOrderByTrscDtDescTrscTmDesc(
                        bizKey, bankCd, acctNo, asOfDt, asOfDt);
        List<AcctTrnsHstr> sorted = new ArrayList<>(day);
        sorted.sort(Comparator.comparing(
                AcctTrnsHstr::getTrscTm, Comparator.nullsFirst(Comparator.naturalOrder())));

        // Totals are computed across all rows for the date — not paginated.
        BigDecimal totIn = BigDecimal.ZERO;
        BigDecimal totOut = BigDecimal.ZERO;
        BigDecimal closingBal = BigDecimal.ZERO;
        for (AcctTrnsHstr r : sorted) {
            BigDecimal amt = r.getTrscAmt() == null ? BigDecimal.ZERO : r.getTrscAmt();
            String dv = r.getInoutDv() == null ? "" : r.getInoutDv().trim();
            if ("1".equals(dv)) totIn = totIn.add(amt);
            else if ("2".equals(dv)) totOut = totOut.add(amt);
        }
        if (!sorted.isEmpty()) {
            // Closing balance for the day = BAL on the last row (chronologically).
            BigDecimal bal = sorted.get(sorted.size() - 1).getBal();
            closingBal = bal == null ? BigDecimal.ZERO : bal;
        }

        // Paginate the chronological list.
        long totCnt = sorted.size();
        int from = Math.min((safePage - 1) * safeSize, sorted.size());
        int to = Math.min(from + safeSize, sorted.size());
        List<AcctTrnsHstr> pageRows = sorted.subList(from, to);

        List<DailyFinancialTransactionsReport.TransactionRow> rec = new ArrayList<>(pageRows.size());
        for (AcctTrnsHstr r : pageRows) {
            rec.add(new DailyFinancialTransactionsReport.TransactionRow(
                    r.getTrscTm(),
                    r.getCcyCd(),
                    AmountFormatter.wire(r.getTrscAmt()),
                    AmountFormatter.wire(r.getBal()),
                    r.getInoutDv(),
                    r.getRmrk()
            ));
        }

        return new DailyFinancialTransactionsReport(
                totCnt,
                AmountFormatter.wire(totIn),
                AmountFormatter.wire(totOut),
                AmountFormatter.wire(closingBal),
                safePage,
                safeSize,
                rec);
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

}
