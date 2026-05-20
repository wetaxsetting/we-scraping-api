package com.kosign.wecms.domain.banking.service.cashflow;

import com.kosign.wecms.domain.banking.dto.BankFilter;
import com.kosign.wecms.domain.banking.entity.AcctTrnsHstr;
import com.kosign.wecms.domain.banking.entity.CardTrnsHstr;
import com.kosign.wecms.domain.banking.repository.AcctTrnsHstrRepository;
import com.kosign.wecms.domain.banking.repository.CardTrnsHstrRepository;
import com.kosign.wecms.domain.payment.entity.OvrsPymnMstr;
import com.kosign.wecms.domain.payment.internaltransfer.repository.ScdulPymnCashFlowRow;
import com.kosign.wecms.domain.payment.internaltransfer.repository.ScdulPymnSumrDetlRepository;
import com.kosign.wecms.domain.payment.overseas.repository.OvrsPymnMstrRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Runs the legacy {@code all_payment_data} UNION + the
 * {@code filtered_payment_data} CTE in Java. Produces a flat list of
 * {@link PaymentDataRow}s grouped by chart code and year-month later by
 * the caller.
 *
 * <p>Pipeline for one year:
 * <ol>
 *   <li>Pull rows from each of the four source repositories (bank,
 *       card, overseas, scheduled), filtered to the year + tenant +
 *       classified ({@code chart_acct_cd <> ''}).</li>
 *   <li>Project each row to {@link PaymentDataRow}, attaching the row's
 *       {@code apprSeqNo} (null for bank rows).</li>
 *   <li>Collect every non-null {@code apprSeqNo} across the 3 non-bank
 *       sources, batch-resolve through {@link ApprovalStatusResolver},
 *       and drop rows whose {@code apprSeqNo} is NOT in the Completed
 *       set. Rows with a {@code null} {@code apprSeqNo} are dropped
 *       too — the legacy LEFT JOIN +
 *       {@code wb_get_appr_stts(...) = 'C'} predicate filters them
 *       (no row in {@code expt_resol_mstr} → helper returns NULL → not
 *       'C').</li>
 *   <li>Apply quarter filter (per-row month membership in the quarter's
 *       months — legacy {@code filtered_payment_data} predicate).</li>
 *   <li>Apply bank filter (legacy "any of BANK_CD, ACCT_NO, or both
 *       matches any entry" — see §3.10).</li>
 *   <li>Apply FX conversion via {@link FxConverter}. Bank rows pass
 *       through as-is; FX is applied to the projected
 *       {@code originalAmount} field via {@link #applyFx} on the caller
 *       side (we return raw amounts here so the caller can re-use the
 *       same converter for prior-year carry-forward).</li>
 * </ol>
 *
 * <p>Returns rows with {@code originalAmount} and
 * {@code originalCurrency} populated; the caller converts to the
 * report's base currency. This split lets the prior-year aggregation
 * (for {@code opening_balance_calc}) reuse the same pipeline.
 *
 * <p>Reference: docs/refactor-wecms/features/cash-flow-report.md §3.1
 * + §3.10.
 */
@Component
@RequiredArgsConstructor
public class PaymentDataAggregator {

    private final AcctTrnsHstrRepository bankRepo;
    private final CardTrnsHstrRepository cardRepo;
    private final OvrsPymnMstrRepository overseasRepo;
    private final ScdulPymnSumrDetlRepository scheduledRepo;
    private final ApprovalStatusResolver approvalResolver;

    /**
     * Pull, project, and approval-filter all rows for the year.
     * Quarter + bank filter are applied by the caller via
     * {@link #applyFilters} so this method can serve the prior-year
     * carry-forward path which never quarter-filters.
     */
    public List<PaymentDataRow> collect(String bizKey, int year) {
        String yearStr = String.valueOf(year);

        List<PaymentDataRow> rows = new ArrayList<>();

        // Source 1: scheduled payments (joined to header for date + ccy).
        for (ScdulPymnCashFlowRow r : scheduledRepo.findCashFlowSourceRows(bizKey, yearStr)) {
            int month = monthOfRegDtm(r.getRegDtm());
            if (month < 1) continue;
            rows.add(new PaymentDataRow(
                    r.getChartAcctSeqNo(),
                    r.getChartAcctCd(),
                    r.getTranAmt(),
                    coalesceVnd(r.getCcyCd()),
                    r.getOutBankCd(),
                    r.getWdrlActNo(),
                    year,
                    month,
                    r.getApprSeqNo()
            ));
        }

        // Source 2: overseas payments.
        for (OvrsPymnMstr o : overseasRepo.findCashFlowSourceRows(bizKey, yearStr)) {
            int month = monthOfRegDtm(o.getRegDtm());
            if (month < 1) continue;
            rows.add(new PaymentDataRow(
                    o.getChartAcctSeqNo(),
                    o.getChartAcctCd(),
                    o.getTrnfAmt(),
                    coalesceVnd(o.getTrnfCcyCd()),
                    o.getDbtAcctCd(),
                    o.getDbtAcctNo(),
                    year,
                    month,
                    o.getApprSeqNo()
            ));
        }

        // Source 3: corporate card.
        for (CardTrnsHstr c : cardRepo.findClassifiedByBizKeyAndYear(bizKey, yearStr)) {
            int month = monthOfTrscDt(c.getTrscDt());
            if (month < 1) continue;
            rows.add(new PaymentDataRow(
                    c.getChartAcctSeqNo(),
                    c.getChartAcctCd(),
                    c.getTrscAmt(),
                    coalesceVnd(c.getCcyCd()),
                    c.getBankCd(),
                    c.getCardNo(),
                    year,
                    month,
                    c.getApprSeqNo()
            ));
        }

        // Source 4: bank transactions. apprSeqNo intentionally null — the
        // bank source is gated by cnfm_yn='Y' (built into the repo query),
        // NOT by the approval-status resolver.
        for (AcctTrnsHstr b : bankRepo.findCashFlowSourceRows(bizKey, yearStr)) {
            int month = monthOfTrscDt(b.getTrscDt());
            if (month < 1) continue;
            rows.add(new PaymentDataRow(
                    b.getChartAcctSeqNo(),
                    b.getChartAcctCd(),
                    b.getTrscAmt(),
                    coalesceVnd(b.getCcyCd()),
                    b.getBankCd(),
                    b.getAcctNo(),
                    year,
                    month,
                    null
            ));
        }

        // Approval-status filter — applied to the 3 non-bank sources only.
        // Bank rows (apprSeqNo == null) always pass.
        Set<String> candidateApprs = rows.stream()
                .map(PaymentDataRow::apprSeqNo)
                .filter(java.util.Objects::nonNull)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toSet());
        Set<String> completed = approvalResolver.completedSeqNos(bizKey, candidateApprs);

        return rows.stream()
                .filter(r -> r.apprSeqNo() == null         // bank row — always passes
                          || completed.contains(r.apprSeqNo()))
                .toList();
    }

    /**
     * Apply quarter + bank filter, then FX-convert each row's amount to
     * the base currency. Returns rows whose {@code originalAmount} has
     * been replaced with the converted value via a new
     * {@link PaymentDataRow} (record immutability).
     */
    public List<PaymentDataRow> applyFilters(List<PaymentDataRow> rows,
                                              Integer quarter,
                                              Collection<BankFilter> bankFilters,
                                              String baseCurrency,
                                              BigDecimal usdRate,
                                              BigDecimal krwRate,
                                              FxConverter fx) {
        Set<Integer> visibleMonths = monthsOfQuarter(quarter);
        boolean hasBankFilter = bankFilters != null && !bankFilters.isEmpty();

        List<PaymentDataRow> out = new ArrayList<>(rows.size());
        for (PaymentDataRow r : rows) {
            if (visibleMonths != null && !visibleMonths.contains(r.paymentMonth())) continue;
            if (hasBankFilter && !matchesBankFilter(r, bankFilters)) continue;
            BigDecimal converted = fx.convert(
                    r.originalAmount(), r.originalCurrency(),
                    baseCurrency, usdRate, krwRate);
            out.add(new PaymentDataRow(
                    r.chartAcctSeqNo(), r.chartAcctCd(),
                    converted, baseCurrency,
                    r.bankCd(), r.acctNo(),
                    r.paymentYear(), r.paymentMonth(),
                    r.apprSeqNo()
            ));
        }
        return out;
    }

    /**
     * Sum prior-year amounts for the opening-balance carry-forward.
     * No quarter / bank filter applied (legacy parity — see §3.6).
     */
    public List<PaymentDataRow> applyFxOnly(List<PaymentDataRow> rows,
                                             String baseCurrency,
                                             BigDecimal usdRate,
                                             BigDecimal krwRate,
                                             FxConverter fx) {
        List<PaymentDataRow> out = new ArrayList<>(rows.size());
        for (PaymentDataRow r : rows) {
            BigDecimal converted = fx.convert(
                    r.originalAmount(), r.originalCurrency(),
                    baseCurrency, usdRate, krwRate);
            out.add(new PaymentDataRow(
                    r.chartAcctSeqNo(), r.chartAcctCd(),
                    converted, baseCurrency,
                    r.bankCd(), r.acctNo(),
                    r.paymentYear(), r.paymentMonth(),
                    r.apprSeqNo()
            ));
        }
        return out;
    }

    /* ------------------------------------------------------------------ */

    private static String coalesceVnd(String ccy) {
        return (ccy == null || ccy.isBlank()) ? "VND" : ccy.trim();
    }

    private static int monthOfRegDtm(String regDtm) {
        // reg_dtm is yyyyMMddHHmmss; month is chars 5-6 (1-indexed: 5,6).
        if (regDtm == null || regDtm.length() < 6) return -1;
        try { return Integer.parseInt(regDtm.substring(4, 6)); }
        catch (NumberFormatException e) { return -1; }
    }

    private static int monthOfTrscDt(String trscDt) {
        // trsc_dt is yyyyMMdd; month is chars 5-6 (1-indexed: 5,6).
        if (trscDt == null || trscDt.length() < 6) return -1;
        try { return Integer.parseInt(trscDt.substring(4, 6)); }
        catch (NumberFormatException e) { return -1; }
    }

    /**
     * Visible months for a quarter. Returns {@code null} when
     * {@code quarter} is {@code null} (no filter — every month passes).
     */
    private static Set<Integer> monthsOfQuarter(Integer quarter) {
        if (quarter == null) return null;
        return switch (quarter) {
            case 1 -> Set.of(1, 2, 3);
            case 2 -> Set.of(4, 5, 6);
            case 3 -> Set.of(7, 8, 9);
            case 4 -> Set.of(10, 11, 12);
            default -> null;
        };
    }

    /**
     * Bank filter matching. Legacy semantics from {@code filtered_payment_data}:
     * a row passes if ANY filter entry matches via OR of three
     * sub-conditions — bank-only, acct-only, or both. The third
     * condition is redundant (1 OR 2 cover it).
     */
    private static boolean matchesBankFilter(PaymentDataRow row, Collection<BankFilter> filters) {
        for (BankFilter f : filters) {
            if (f.bankCd() != null && !f.bankCd().isBlank()
                    && java.util.Objects.equals(row.bankCd(), f.bankCd())) {
                return true;
            }
            if (f.accountNo() != null && !f.accountNo().isBlank()
                    && java.util.Objects.equals(row.acctNo(), f.accountNo())) {
                return true;
            }
        }
        return false;
    }
}
