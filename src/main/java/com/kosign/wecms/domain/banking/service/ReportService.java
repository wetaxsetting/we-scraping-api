package com.kosign.wecms.domain.banking.service;

import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.banking.dto.DailyCashReport;
import com.kosign.wecms.domain.banking.dto.DailyFinancialCmcReport;
import com.kosign.wecms.domain.banking.dto.DailyFinancialHsReport;
import com.kosign.wecms.domain.banking.dto.DailyFinancialTransactionsReport;
import com.kosign.wecms.domain.banking.entity.AcctMstr;
import com.kosign.wecms.domain.banking.entity.AcctTrnsHstr;
import com.kosign.wecms.domain.banking.entity.AcctTrnsRcvPayInfm;
import com.kosign.wecms.domain.banking.repository.AcctMstrRepository;
import com.kosign.wecms.domain.banking.repository.AcctTrnsHstrRepository;
import com.kosign.wecms.domain.banking.repository.AcctTrnsRcvPayInfmRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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
    private final AcctTrnsRcvPayInfmRepository rcvPayRepo;
    private final BankCdResolver bankResolver;

    private static final DateTimeFormatter YMD = DateTimeFormatter.ofPattern("yyyyMMdd");

    /**
     * Legacy internal-transfer receipt/payment usage codes — verbatim
     * from {@code rept_0015_001_r001_act.jsp:264-267}
     * ({@code getDynamic0ForRec2}). A day transaction whose
     * {@code ACCT_TRNS_RCV_PAY_INFM.RCV_PAY_USAG_CD} is one of these is
     * an internal transfer; it lands in the INTL_SND / INTL_RCV buckets
     * instead of the plain SND / RCV buckets (which are disjoint —
     * legacy footer math {@code INTL_SND_CNT + SND_CNT}).
     */
    private static final List<String> INTERNAL_TRANSFER_USAGE_CODES =
            List.of("4000000008", "6000000062", "4000000104", "6000000061");

    /** Legacy {@code INOUT_DV}: {@code 2} = debit/out, {@code 1} = credit/in. */
    private static final String INOUT_DEBIT = "2";
    private static final String INOUT_CREDIT = "1";

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
     * Daily Financial CMC report — date-aware, live-computed (Strategy B,
     * see {@code docs/features/banking/reports/daily-financial/P0-fix-plan.md}).
     *
     * <p>The legacy report reads the batch-materialised
     * {@code DAILY_FINCL_REPT_DAILY_SUMR_DETL}; that table is not in the
     * refactor DB and the recompute functions are not ported, so the
     * refactor live-computes the same figures from the in-scope
     * operational tables:
     * <ul>
     *   <li><b>Per-account daily aggregates</b> (SND/RCV count+amount and
     *       the internal variants) — grouped from {@code ACCT_TRNS_HSTR}
     *       for {@code TRSC_DT = asOf}, with internal classification from
     *       {@code ACCT_TRNS_RCV_PAY_INFM.RCV_PAY_USAG_CD}.</li>
     *   <li><b>As-of end-of-day balance</b> — the last {@code BAL} on or
     *       before {@code asOf} (falls back to {@code ACCT_MSTR.BAL} when
     *       the account has no history that early).</li>
     *   <li><b>Financial Status</b> — per-currency current + savings
     *       totals of those as-of balances; {@code total = current + saving}
     *       (in-scope: no cash / time-deposit / loan).</li>
     * </ul>
     *
     * <p>Per-account {@code transactions[]} ({@code REC2_1}) stays empty
     * (the inline grid is P1/G-3; the transaction-history popup serves the
     * drill-down). The {@code REC3} currency-flow / cash rollup is
     * intentionally absent (out of PROD scope).
     *
     * @param activeAccountsOnly       maps to legacy {@code ACTV_ACCT_ONLY_YN}.
     *                                 The active-only finder is always used
     *                                 today — widening to inactive accounts
     *                                 when {@code false} is P1/G-6.
     * @param includeInternalTransfers maps to legacy {@code INCL_INTL_TRNS_YN}.
     *                                 The internal columns are now populated;
     *                                 the toggle's exclusion-from-totals
     *                                 semantics are P1/G-4 (frontend uses the
     *                                 flag for column visibility only).
     */
    @Transactional(readOnly = true)
    public DailyFinancialCmcReport dailyFinancial(
            String asOfDt,
            String currencyScope,
            boolean activeAccountsOnly,
            boolean includeInternalTransfers) {
        final String bizKey = TenantContext.currentBizKey();
        final String asOf = normaliseAsOf(asOfDt);

        // Active current/savings accounts (legacy panel scope). G-6
        // (widen to inactive when activeAccountsOnly=false) is P1.
        List<AcctMstr> accts = acctRepo.findByBizKeyAndActvYnOrderByBankCdAscAcctNoAsc(bizKey, "Y");

        // Per-account daily debit/credit aggregates for the as-of date.
        Map<String, DailyAggregate> aggByAccount = computeDailyAggregates(bizKey, asOf);

        Map<String, BigDecimal> currentByCcy = new LinkedHashMap<>();
        Map<String, BigDecimal> savingByCcy = new LinkedHashMap<>();
        List<DailyFinancialCmcReport.CurrentAccountRow> ledger = new ArrayList<>();

        for (AcctMstr a : accts) {
            String type = a.getAcctTypeCd() == null ? "" : a.getAcctTypeCd().trim();
            boolean isCurrent = CURRENT_ACCOUNT_TYPE_CODES.contains(type);
            boolean isSaving = SAVING_ACCOUNT_TYPE_CODES.contains(type);
            if (!isCurrent && !isSaving) continue; // in-scope: current + saving only

            String ccy = a.getCcyCd().trim();
            if (!matchesScope(currencyScope, ccy)) continue;

            BigDecimal asOfBal = asOfBalance(bizKey, a, asOf);
            if (isCurrent) currentByCcy.merge(ccy, asOfBal, BigDecimal::add);
            else           savingByCcy.merge(ccy, asOfBal, BigDecimal::add);

            DailyAggregate agg = aggByAccount.getOrDefault(
                    accountKey(a.getBankCd(), a.getAcctNo()), DailyAggregate.EMPTY);

            ledger.add(new DailyFinancialCmcReport.CurrentAccountRow(
                    a.getBankCd() + "_" + a.getAcctNo(),
                    bankResolver.resolveBankNm(a.getBankCd()),
                    a.getBankCd(),
                    a.getAcctNo(),
                    a.getAcctNickNm() != null ? a.getAcctNickNm() : a.getAcctNm(),
                    ccy,
                    type,
                    a.getActvYn(),
                    agg.sndCnt, AmountFormatter.wire(agg.sndAmt),
                    agg.intlSndCnt, AmountFormatter.wire(agg.intlSndAmt),
                    agg.rcvCnt, AmountFormatter.wire(agg.rcvAmt),
                    agg.intlRcvCnt, AmountFormatter.wire(agg.intlRcvAmt),
                    AmountFormatter.wire(asOfBal),
                    List.of()
            ));
        }

        // Financial Status — per currency, total = current + saving
        // (in-scope). Iterate the union of currencies that carry a
        // current or saving account so the order is deterministic.
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
                asOf,
                currencyScope == null ? "ALL" : currencyScope,
                activeAccountsOnly,
                includeInternalTransfers,
                status,
                ledger);
    }

    /* ---- Daily Financial helpers (Strategy B live-compute) ---- */

    /** Blank / null {@code asOf} defaults to today (legacy requires REF_DATE). */
    private static String normaliseAsOf(String asOfDt) {
        if (asOfDt == null || asOfDt.isBlank()) return LocalDate.now().format(YMD);
        return asOfDt.trim();
    }

    /** Currency-scope predicate — {@code ALL}/null = any, {@code VND} =
     *  local only, {@code FCY} = everything except VND. */
    private static boolean matchesScope(String currencyScope, String ccy) {
        if (currencyScope == null) return true;
        if ("VND".equalsIgnoreCase(currencyScope)) return "VND".equals(ccy);
        if ("FCY".equalsIgnoreCase(currencyScope)) return !"VND".equals(ccy);
        return true; // ALL or unknown → no narrowing
    }

    private static String accountKey(String bankCd, String acctNo) {
        return (bankCd == null ? "" : bankCd) + ' ' + (acctNo == null ? "" : acctNo);
    }

    /**
     * Group the day's {@code ACCT_TRNS_HSTR} rows into per-account
     * SND/RCV (+ internal) counts and amounts. A transaction is internal
     * when its {@code ACCT_SEQ_NO} appears in {@code ACCT_TRNS_RCV_PAY_INFM}
     * with an internal-transfer usage code. SND/RCV and INTL_SND/INTL_RCV
     * are disjoint (legacy parity).
     */
    private Map<String, DailyAggregate> computeDailyAggregates(String bizKey, String asOf) {
        List<AcctTrnsHstr> dayRows = trnsRepo.findByBizKeyAndTrscDt(bizKey, asOf);
        if (dayRows.isEmpty()) return Map.of();

        List<String> acctSeqNos = dayRows.stream()
                .map(AcctTrnsHstr::getAcctSeqNo)
                .filter(s -> s != null && !s.isBlank())
                .distinct()
                .collect(Collectors.toList());

        Set<String> internalSeqNos = acctSeqNos.isEmpty()
                ? Set.of()
                : rcvPayRepo.findByBizKeyAndAcctSeqNoInAndRcvPayUsagCdIn(
                                bizKey, acctSeqNos, INTERNAL_TRANSFER_USAGE_CODES)
                        .stream()
                        .map(AcctTrnsRcvPayInfm::getAcctSeqNo)
                        .collect(Collectors.toSet());

        Map<String, DailyAggregate> byAccount = new HashMap<>();
        for (AcctTrnsHstr t : dayRows) {
            DailyAggregate agg = byAccount.computeIfAbsent(
                    accountKey(t.getBankCd(), t.getAcctNo()), k -> new DailyAggregate());
            boolean internal = internalSeqNos.contains(t.getAcctSeqNo());
            agg.add(t.getInoutDv(), t.getTrscAmt(), internal);
        }
        return byAccount;
    }

    /**
     * End-of-day balance for {@code account} as of {@code asOf}: the
     * {@code BAL} of the latest {@code ACCT_TRNS_HSTR} row on or before
     * {@code asOf}. Falls back to the account's current {@code ACCT_MSTR.BAL}
     * when there is no transaction history that early (no in-scope opening
     * balance source — documented limitation).
     */
    private BigDecimal asOfBalance(String bizKey, AcctMstr account, String asOf) {
        BigDecimal histBal = trnsRepo
                .findFirstByBizKeyAndBankCdAndAcctNoAndTrscDtLessThanEqualOrderByTrscDtDescTrscTmDescAcctSeqNoDesc(
                        bizKey, account.getBankCd(), account.getAcctNo(), asOf)
                .map(AcctTrnsHstr::getBal)
                .orElse(null);
        if (histBal != null) return histBal;
        return account.getBal() != null ? account.getBal() : BigDecimal.ZERO;
    }

    /**
     * Mutable per-account accumulator for one day's debit/credit activity.
     * SND = non-internal debit, INTL_SND = internal debit; RCV =
     * non-internal credit, INTL_RCV = internal credit (disjoint).
     */
    private static final class DailyAggregate {
        static final DailyAggregate EMPTY = new DailyAggregate();

        int sndCnt, rcvCnt, intlSndCnt, intlRcvCnt;
        BigDecimal sndAmt = BigDecimal.ZERO, rcvAmt = BigDecimal.ZERO,
                   intlSndAmt = BigDecimal.ZERO, intlRcvAmt = BigDecimal.ZERO;

        void add(String inoutDv, BigDecimal amt, boolean internal) {
            String dv = inoutDv == null ? "" : inoutDv.trim();
            BigDecimal a = amt == null ? BigDecimal.ZERO : amt;
            if (INOUT_DEBIT.equals(dv)) {
                if (internal) { intlSndCnt++; intlSndAmt = intlSndAmt.add(a); }
                else          { sndCnt++;     sndAmt = sndAmt.add(a); }
            } else if (INOUT_CREDIT.equals(dv)) {
                if (internal) { intlRcvCnt++; intlRcvAmt = intlRcvAmt.add(a); }
                else          { rcvCnt++;     rcvAmt = rcvAmt.add(a); }
            }
            // Other INOUT_DV values (e.g. '0' ETC) contribute to no bucket.
        }
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
