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
import com.kosign.wecms.domain.banking.repository.DailyFinancialCmcFunctionRepository;
import com.kosign.wecms.domain.banking.repository.DailyFinancialCmcRec1Row;
import com.kosign.wecms.domain.banking.repository.DailyFinancialHsReportRepository;
import com.kosign.wecms.domain.banking.repository.DailyFinancialHsRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Banking Reports service.
 *
 * <p><b>Daily Financial CMC</b> — DB-function backed (2026-06-14): reads the
 * legacy read-only function {@code rept_0015_001_r001_rec1} through
 * {@link DailyFinancialCmcFunctionRepository} (the function is the source the
 * legacy {@code DAILY_FINCL_REPT_DAILY_SUMR_DETL} summary table is materialised
 * from). The prior Java live-recompute (per-account aggregation from
 * {@code ACCT_TRNS_HSTR} + {@code ACCT_TRNS_RCV_PAY_INFM} + as-of-balance walk)
 * is removed. No write-on-read, no summary-table read.
 *
 * <p><b>Daily Financial HS</b> — real DB-backed (DF-2): calls
 * {@code fn_get_haengsung_daily_financial_report} through
 * {@link DailyFinancialHsReportRepository}.
 *
 * <p><b>Daily Cash</b> — this method is a Sprint-1 stub for the per-currency
 * summary (account ledger is a real {@code ACCT_MSTR} read). The dedicated
 * {@code DailyCashReportService} is the real, DB-function-backed Daily Cash
 * path; this {@code dailyCash(...)} helper is legacy scaffolding.
 */
@Service
@RequiredArgsConstructor
public class ReportService {

    private final AcctMstrRepository acctRepo;
    private final AcctTrnsHstrRepository trnsRepo;
    private final BankCdResolver bankResolver;
    private final DailyFinancialHsReportRepository hsReportRepo;
    private final DailyFinancialCmcFunctionRepository cmcFnRepo;

    private static final DateTimeFormatter YMD = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** Tenant local currency (VND today). FCY scope excludes it. */
    private static final String LOCAL_CCY = "VND";

    /**
     * ACCT_TYPE_CD partition for the Daily Financial Status header row.
     * Mirrors the legacy {@code AcctTypeCd} enum
     * ({@code WECMS DEV/WECMS/src/wabooks/enums/AcctTypeCd.java}):
     * 03 / 04 / 07 are the three "current" variants (KHR / USD / KRW),
     * 05 / 06 / 08 are the three "savings" variants. Code 02 (Fixed /
     * Time Deposit) and 01 (Loan) are intentionally excluded — neither
     * the legacy Financial Status block nor the live old-project
     * screenshot surfaces them.
     *
     * <p>07/08 are kept by product direction (2026-06-14): the source
     * function {@code rept_0015_001_r001_rec1} returns KRW current/savings
     * rows, and they are folded into the current/savings totals here.
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

    /* ----- Daily Financial (CMC) — legacy DB function source of truth ----- */

    /**
     * Daily Financial CMC report — reads the legacy read-only function
     * {@code rept_0015_001_r001_rec1(biz_key, req_date, req_date2)} (via
     * {@link DailyFinancialCmcFunctionRepository}) and projects its rows onto
     * the wire shape. The function performs the per-account computation legacy
     * relies on (as-of balance = latest {@code BAL ≤ req_date} else
     * {@code ACCT_MSTR.BAL}; same-day SND/RCV non-internal counts/amounts;
     * disjoint INTL_SND/INTL_RCV from {@code ACCT_TRNS_RCV_PAY_INFM}; bank name
     * via {@code wb_get_bank_nm}; amounts via {@code wb_get_amt}).
     *
     * <ul>
     *   <li><b>Per-account ledger</b> — current + savings accounts
     *       ({@code 03/04/07} + {@code 05/06/08}); 07/08 KRW kept by product
     *       direction.</li>
     *   <li><b>Financial Status</b> — per-currency current + savings totals of
     *       the function's as-of balances; {@code total = current + saving}
     *       (in-scope: no cash / time-deposit / loan).</li>
     * </ul>
     *
     * @param activeAccountsOnly maps to legacy {@code ACTV_ACCT_ONLY_YN}. The
     *                           function returns all accounts (active + inactive);
     *                           when {@code true} the repository filters to
     *                           {@code ACTV_YN='Y'} (DF-3); when {@code false}
     *                           inactive accounts are included.
     * @param includeInternalTransfers maps to legacy {@code INCL_INTL_TRNS_YN}.
     *                           When {@code false} the internal-transfer columns
     *                           are emitted as zero so they neither display nor
     *                           count (DF-4), mirroring legacy's exclusion of
     *                           internal rows when the toggle is off. The
     *                           Financial Status is balance-based and unaffected
     *                           on both sides.
     */
    @Transactional(readOnly = true)
    public DailyFinancialCmcReport dailyFinancial(
            String asOfDt,
            String currencyScope,
            boolean activeAccountsOnly,
            boolean includeInternalTransfers) {
        final String bizKey = TenantContext.currentBizKey();
        final String reqDate = normaliseAsOf(asOfDt);
        final String ccyExact = ccyExact(currencyScope);
        final String ccyExclude = ccyExclude(currencyScope);
        final String actvFilter = activeAccountsOnly ? "Y" : null; // DF-3

        List<DailyFinancialCmcRec1Row> rows =
                cmcFnRepo.rec1(bizKey, reqDate, reqDate, ccyExact, ccyExclude, actvFilter);

        Map<String, BigDecimal> currentByCcy = new LinkedHashMap<>();
        Map<String, BigDecimal> savingByCcy = new LinkedHashMap<>();
        List<DailyFinancialCmcReport.CurrentAccountRow> ledger = new ArrayList<>();

        for (DailyFinancialCmcRec1Row r : rows) {
            String type = trim(r.getAcctTypeCd());
            boolean isCurrent = CURRENT_ACCOUNT_TYPE_CODES.contains(type);
            boolean isSaving = SAVING_ACCOUNT_TYPE_CODES.contains(type);
            if (!isCurrent && !isSaving) continue; // in-scope: current + saving only (07/08 kept)

            String ccy = trim(r.getCcyCd());
            BigDecimal bal = nz(r.getBal());
            if (isCurrent) currentByCcy.merge(ccy, bal, BigDecimal::add);
            else           savingByCcy.merge(ccy, bal, BigDecimal::add);

            // DF-4: when internal transfers are excluded, zero the internal columns
            // so they neither render nor contribute (legacy parity for the OFF branch).
            int    intlSndCnt = includeInternalTransfers ? nz(r.getIntlSndCnt()).intValue() : 0;
            String intlSndAmt = includeInternalTransfers ? AmountFormatter.wire(nz(r.getIntlSndAmt())) : "0";
            int    intlRcvCnt = includeInternalTransfers ? nz(r.getIntlRcvCnt()).intValue() : 0;
            String intlRcvAmt = includeInternalTransfers ? AmountFormatter.wire(nz(r.getIntlRcvAmt())) : "0";

            ledger.add(new DailyFinancialCmcReport.CurrentAccountRow(
                    r.getBankCd() + "_" + r.getAcctNo(),
                    r.getBankNm(),
                    r.getBankCd(),
                    r.getAcctNo(),
                    r.getAcctNickNm(),
                    ccy,
                    type,
                    r.getActvYn(),
                    nz(r.getSndCnt()).intValue(), AmountFormatter.wire(nz(r.getSndAmt())),
                    intlSndCnt, intlSndAmt,
                    nz(r.getRcvCnt()).intValue(), AmountFormatter.wire(nz(r.getRcvAmt())),
                    intlRcvCnt, intlRcvAmt,
                    AmountFormatter.wire(bal),
                    List.of()
            ));
        }

        // Financial Status — per currency, total = current + saving (in-scope).
        Set<String> currencies = new LinkedHashSet<>();
        currencies.addAll(currentByCcy.keySet());
        currencies.addAll(savingByCcy.keySet());
        List<DailyFinancialCmcReport.FinancialStatusRow> status = new ArrayList<>();
        for (String ccy : currencies) {
            BigDecimal current = currentByCcy.getOrDefault(ccy, BigDecimal.ZERO);
            BigDecimal saving = savingByCcy.getOrDefault(ccy, BigDecimal.ZERO);
            status.add(new DailyFinancialCmcReport.FinancialStatusRow(
                    ccy,
                    AmountFormatter.wire(current.add(saving)),
                    AmountFormatter.wire(current),
                    AmountFormatter.wire(saving)
            ));
        }

        return new DailyFinancialCmcReport(
                reqDate,
                currencyScope == null ? "ALL" : currencyScope,
                activeAccountsOnly,
                includeInternalTransfers,
                status,
                ledger);
    }

    /* ---- Daily Financial CMC helpers ---- */

    /** Blank / null {@code asOf} defaults to today (legacy requires REF_DATE). */
    private static String normaliseAsOf(String asOfDt) {
        if (asOfDt == null || asOfDt.isBlank()) return LocalDate.now().format(YMD);
        return asOfDt.trim();
    }

    /** Exact-currency pin: {@code VND} → "VND"; ALL / FCY / null → null
     *  (legacy CMC scope is ALL / FCY / VND). */
    private static String ccyExact(String currencyScope) {
        return "VND".equalsIgnoreCase(currencyScope) ? "VND" : null;
    }

    /** Currency to exclude: {@code FCY} → local currency; otherwise null. */
    private static String ccyExclude(String currencyScope) {
        return "FCY".equalsIgnoreCase(currencyScope) ? LOCAL_CCY : null;
    }

    private static String trim(String s) { return s == null ? "" : s.trim(); }

    private static BigDecimal nz(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }

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

    /* -------------------- Daily Financial HS (real DB function) -------------------- */

    /** Account filter sentinel meaning "every active account" (legacy
     *  {@code ACCT_NO='ALL'} contract from {@code rept_0017_001.js}). */
    private static final String HS_ACCOUNT_FILTER_ALL = "ALL";

    /**
     * HAENGSUNG Daily Financial report — mirrors legacy {@code rept_0017_001}
     * (IDO {@code DAILY_FINCL_REPT_R001}). Delegates the whole computation to
     * the legacy DB function
     * {@code fn_get_haengsung_daily_financial_report(biz_key, as_of_date,
     * account_filter)} via {@link DailyFinancialHsReportRepository}; rows are
     * returned in the function's own row-type + order (HEADER →
     * BEFORE_BALANCE → TRANSACTION → ACCOUNT_TOTAL → CURRENCY_TOTAL,
     * VND → USD → KRW). No data is computed in Java — the function is the
     * single source of truth, so the refactor stays byte-aligned with the
     * legacy ledger.
     *
     * @param asOfDt        period date; {@code yyyyMMdd} (blank → today). The
     *                      function validates {@code ^\d{8}$} and raises on a
     *                      bad format.
     * @param accountFilter {@code ALL} (default) or a JSON array of
     *                      {@code {"BANK_CD":..,"ACCT_NO":..}} (legacy bank
     *                      multi-select contract). Blank → {@code ALL}.
     */
    @Transactional(readOnly = true)
    public DailyFinancialHsReport dailyFinancialHs(String asOfDt, String accountFilter) {
        final String bizKey = TenantContext.currentBizKey();
        if (bizKey == null) throw new IllegalStateException("missing tenant context");

        final String periodDate = normaliseAsOf(asOfDt);
        final String filter = (accountFilter == null || accountFilter.isBlank())
                ? HS_ACCOUNT_FILTER_ALL
                : accountFilter.trim();

        List<DailyFinancialHsRow> raw = hsReportRepo.fetchReport(bizKey, periodDate, filter);

        List<DailyFinancialHsReport.LedgerRow> rows = new ArrayList<>(raw.size());
        for (DailyFinancialHsRow r : raw) {
            rows.add(new DailyFinancialHsReport.LedgerRow(
                    r.getRowType(),
                    r.getBankAccount(),
                    r.getCurrency(),
                    r.getReptCd(),
                    r.getPymnCd(),
                    r.getDescriptionEnglish(),
                    r.getDescriptionKorean(),
                    r.getCustomerSupplier(),
                    nullableWire(r.getBeforeBalance()),
                    nullableWire(r.getReceipt()),
                    nullableWire(r.getPayment()),
                    nullableWire(r.getBalance()),
                    nullableWire(r.getBalanceCompanyActual()),
                    r.getAccountNumber(),
                    r.getAccountTypeOrder(),
                    r.getAcctSeqNo(),
                    r.getTransactionDate(),
                    r.getTransactionTime(),
                    r.getTransactionSeq(),
                    r.getMemo(),
                    r.getBankCdOut(),
                    r.getBankNameSort()
            ));
        }
        return new DailyFinancialHsReport(periodDate, filter, rows);
    }

    /** Wire-format an amount, preserving {@code null} (the function leaves
     *  amount cells empty on rows that don't carry that figure — render
     *  blank, never {@code "0"}). */
    private static String nullableWire(java.math.BigDecimal v) {
        return v == null ? null : AmountFormatter.wire(v);
    }

}
