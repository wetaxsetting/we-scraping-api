package com.kosign.wecms.domain.banking.service;

import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.banking.dto.DailyCashReportResponse;
import com.kosign.wecms.domain.banking.dto.DailyFinancialCmcReport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.CellRangeAddress;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Banking &gt; Reports &mdash; Excel Download for <b>Daily Cash</b> and
 * <b>Daily Financial (CMC)</b> (the PO-requested template-populated report).
 * One shared service, <b>two layout handlers</b> ({@link #generateDailyCash}
 * / {@link #generateDailyFinancial}) sharing the template-load / style-capture
 * / section-sizing scaffolding &mdash; matching the PO instruction "one shared
 * ReportsDownloadService with separate layout handlers".
 *
 * <p><b>Architecture note.</b> The refactor report screens currently export
 * Excel <em>client-side, from scratch</em> ({@code lib/dailyCashExport.ts}
 * {@code exportDailyCashExcel} / {@code DailyFinancialPage.handleExportExcel}
 * via {@code xlsx-js-style}). Legacy did not template-populate these two
 * reports either (Daily Cash was client-side ExcelJS; the name-similar
 * {@code rept_0017_001} POI template is a different HS transaction ledger).
 * The PO nonetheless decided these downloads must switch to the
 * <b>template-populate</b> architecture used by Internal Transfer / Contacts /
 * Current &amp; Savings &mdash; see
 * {@code docs/audits/daily-cash-daily-financial-excel-download-audit.md}. This
 * service therefore deliberately does <strong>NOT</strong> reuse the shared
 * {@code common/export/ExcelExportService} (which builds a workbook from
 * scratch and cannot preserve the templates' merged title, summary banners,
 * baked column widths and number formats). Instead it loads the styled locale
 * template from the backend classpath via {@link ClassPathResource} and
 * populates the pre-styled section tables in place, mirroring
 * {@link CurrentSavingsDownloadService}.
 *
 * <p><b>Two-section templates.</b> Unlike every prior template-populate
 * precedent (flat single-section templates), these templates carry a
 * <em>Current Accounts</em> section and a <em>Savings Accounts</em> section
 * &mdash; i.e. there is content <em>below</em> the first data region. The
 * populator detects each section's landmarks from the pristine template, then
 * uses {@link Sheet#shiftRows} to size each section to its exact row count,
 * processing <b>bottom-up</b> so a shift never invalidates a not-yet-written
 * section above.
 *
 * <p><b>Scope handling.</b>
 * <ul>
 *   <li>Daily Cash (8 cols, no per-section totals): Current = {@code ACCT_TYPE_CD}
 *       &isin; {03,04,07}, Savings = {05,06,08} (aligned with Daily Financial on
 *       2026-07-01 per the PO/UI grouping change; 01/02 excluded). Data reused
 *       from {@link DailyCashReportService#search}.</li>
 *   <li>Daily Financial (13 cols, per-section Total&nbsp;VND/Total&nbsp;USD rows):
 *       Current = {03,04,07}, Savings = {05,06,08} (mirrors
 *       {@code ReportService.CURRENT/SAVING_ACCOUNT_TYPE_CODES}). Data reused
 *       from {@link ReportService#dailyFinancial}.</li>
 * </ul>
 *
 * <p><b>Summary / status band (populated 2026-07-01).</b> The top band is now
 * fully populated for both reports: As-of, Currency, and a <b>three-currency</b>
 * total band (VND / USD / KRW) &mdash; banner grand total + Current + Savings per
 * currency. A KRW block was added to all six templates (cloned from the USD
 * block). Daily Cash figures come from {@code currency_summary} (or an account
 * rollup when absent, mirroring {@code DailyCashClient}); Daily Financial figures
 * come verbatim from {@code financial_status} &mdash; no separate calculation.
 *
 * <p><b>Locale selection</b> mirrors legacy {@code LocaleHelper.getLanguage}:
 * {@code EN}/{@code KO}/{@code VI} each load their own template; {@code KM} and
 * anything else fall back to {@code EN}. No {@code ERP_TRNS_HSTR} audit row is
 * written (legacy parity &mdash; these reports had none).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportsDownloadService {

    static final String TEMPLATE_ROOT = "templates/reports/";

    /** Daily Cash — Current section data starts at Excel row 22 (index 21),
     *  after the 3-currency summary band (VND/USD/KRW). Identical across
     *  EN/KO/VI. (Was index 17 before the KRW summary block was added
     *  2026-07-01, +4 rows.) */
    static final int DC_CURRENT_DATA_START = 21;
    /** Daily Financial — Current section data starts at Excel row 19 (index 18),
     *  after the 3-currency Financial-Status band (VND/USD/KRW). Identical
     *  across EN/KO/VI. (Was index 14 before the KRW status block, +4 rows.) */
    static final int DF_CURRENT_DATA_START = 18;

    /** Daily Cash Current/Savings split. Aligned with Daily Financial on
     *  2026-07-01 (PO/UI grouping change) — Current now includes {@code 07},
     *  Savings includes {@code 08}, matching {@link #DF_CURRENT_TYPES} /
     *  {@link #DF_SAVING_TYPES} and the on-screen {@code DailyCashClient} split. */
    static final Set<String> DC_CURRENT_TYPES = Set.of("03", "04", "07");
    static final Set<String> DC_SAVING_TYPES = Set.of("05", "06", "08");
    /** Daily Financial Current/Savings split (ReportService parity). */
    static final Set<String> DF_CURRENT_TYPES = Set.of("03", "04", "07");
    static final Set<String> DF_SAVING_TYPES = Set.of("05", "06", "08");

    /** Per-data-row merges: Daily Cash — Account No. (B:C) + Balance (I:J). */
    private static final int[][] DC_MERGES = {{1, 2}, {8, 9}};
    /** Daily Cash physical column count (A..J). */
    private static final int DC_COLS = 10;
    /** Per-data-row merges: Daily Financial — Bank (A:B) + Balance (N:P). */
    private static final int[][] DF_MERGES = {{0, 1}, {13, 15}};
    /** Daily Financial physical column count (A..P). */
    private static final int DF_COLS = 16;

    private final DailyCashReportService dailyCashReportService;
    private final ReportService reportService;

    /** Generated report payload — workbook bytes + the download filename. */
    public record GeneratedReport(byte[] content, String fileName) { }

    /* ================================================================
     *  Daily Cash  (rept_0900_001 / daily-cash-report)
     * ================================================================ */

    /**
     * Generate the Daily Cash download report.
     *
     * @param asOfDate   ISO {@code yyyy-MM-dd} report date (same as the screen's search)
     * @param currency   optional currency scope ({@code ALL}/{@code VND}/{@code FCY}/{@code USD}/{@code KHR}); blank → ALL
     * @param langParam  optional UI locale ({@code EN}/{@code KO}/{@code VI}); blank → JWT langCd; KM/other → EN
     */
    @Transactional(readOnly = true)
    public GeneratedReport generateDailyCash(String asOfDate, String currency, String langParam) {
        String bizKey = requireTenant();
        String lang = resolveLang(langParam);

        // Same query path as the on-screen list.
        DailyCashReportResponse data =
                dailyCashReportService.search(asOfDate, DailyCashCurrencyFilter.parse(currency));
        List<DailyCashReportResponse.AccountRow> all =
                data.accounts() != null ? data.accounts() : List.of();
        List<DailyCashReportResponse.AccountRow> current =
                filter(all, r -> DC_CURRENT_TYPES.contains(trim(r.acctTypeCd())));
        List<DailyCashReportResponse.AccountRow> saving =
                filter(all, r -> DC_SAVING_TYPES.contains(trim(r.acctTypeCd())));

        byte[] bytes = build(
                "daily-cash/daily-cash_" + lang + ".xlsx",
                sheet -> populateDailyCash(sheet, data, asOfDate, currency, current, saving));

        String fileName = dailyCashFileName(lang);
        log.info("feature=daily-cash report-download bizKey={} asOfDate={} currency={} lang={} current={} saving={}",
                bizKey, asOfDate, currency, lang, current.size(), saving.size());
        return new GeneratedReport(bytes, fileName);
    }

    private void populateDailyCash(Sheet sheet,
                                   DailyCashReportResponse data, String asOfDate, String currency,
                                   List<DailyCashReportResponse.AccountRow> current,
                                   List<DailyCashReportResponse.AccountRow> saving) {
        populateDailyCashSummary(sheet, data, asOfDate, currency);

        int currentDataStart = DC_CURRENT_DATA_START;
        int savingTitleIdx = firstLabeledRowAtOrAfter(sheet, currentDataStart);
        int currentProto = savingTitleIdx - currentDataStart;
        int savingHeaderIdx = savingTitleIdx + 2;      // section title spans 2 merged rows
        int savingDataStart = savingHeaderIdx + 1;
        int savingProto = sheet.getLastRowNum() - savingDataStart + 1;

        // Bottom-up: Savings section first (no trailer — it is the last block),
        // then Current section (its trailer = the whole Savings block).
        fillSection(sheet, savingDataStart, savingProto, /*trailerStart*/ -1,
                DC_COLS, DC_MERGES, saving.size(),
                (row, i) -> writeDailyCashRow(row, saving.get(i)));

        fillSection(sheet, currentDataStart, currentProto, /*trailerStart*/ savingTitleIdx,
                DC_COLS, DC_MERGES, current.size(),
                (row, i) -> writeDailyCashRow(row, current.get(i)));
    }

    /** Daily Cash data row — 8 logical cols (A, B:C, D, E, F, G, H, I:J). */
    private static void writeDailyCashRow(Row row, DailyCashReportResponse.AccountRow r) {
        setText(row, 0, bankName(r.bankNm(), r.bankCd()));
        setText(row, 1, r.acctNo());
        setText(row, 3, r.currencyCd());
        setNum(row, 4, r.sendCount());
        setNum(row, 5, r.sendAmount());
        setNum(row, 6, r.receiveCount());
        setNum(row, 7, r.receiveAmount());
        setNum(row, 8, r.balance());
    }

    /* ================================================================
     *  Daily Financial CMC  (rept_0015_001 / daily-financial)
     * ================================================================ */

    /**
     * Generate the Daily Financial (CMC) download report.
     *
     * @param asOf                     {@code yyyyMMdd} report date (blank → today)
     * @param currencyScope            {@code ALL}/{@code VND}/{@code FCY}; blank → ALL
     * @param activeAccountsOnly       DF-3 filter (screen default {@code true})
     * @param includeInternalTransfers DF-4 toggle (screen default {@code true}); when
     *                                 false the internal columns are emitted as 0
     * @param langParam                optional UI locale; KM/other → EN
     */
    @Transactional(readOnly = true)
    public GeneratedReport generateDailyFinancial(
            String asOf, String currencyScope,
            boolean activeAccountsOnly, boolean includeInternalTransfers, String langParam) {
        String bizKey = requireTenant();
        String lang = resolveLang(langParam);

        DailyFinancialCmcReport data = reportService.dailyFinancial(
                asOf, currencyScope, activeAccountsOnly, includeInternalTransfers);
        List<DailyFinancialCmcReport.CurrentAccountRow> all =
                data.currentAccounts() != null ? data.currentAccounts() : List.of();
        List<DailyFinancialCmcReport.CurrentAccountRow> current =
                filter(all, r -> DF_CURRENT_TYPES.contains(trim(r.acctTypeCd())));
        List<DailyFinancialCmcReport.CurrentAccountRow> saving =
                filter(all, r -> DF_SAVING_TYPES.contains(trim(r.acctTypeCd())));

        byte[] bytes = build(
                "daily-financial/daily-financial_" + lang + ".xlsx",
                sheet -> populateDailyFinancial(sheet, data, asOf, currencyScope, current, saving));

        String fileName = dailyFinancialFileName(lang);
        log.info("feature=daily-financial report-download bizKey={} asOf={} scope={} active={} internal={} lang={} current={} saving={}",
                bizKey, asOf, currencyScope, activeAccountsOnly, includeInternalTransfers, lang,
                current.size(), saving.size());
        return new GeneratedReport(bytes, fileName);
    }

    private void populateDailyFinancial(Sheet sheet,
                                        DailyFinancialCmcReport data, String asOf, String currencyScope,
                                        List<DailyFinancialCmcReport.CurrentAccountRow> current,
                                        List<DailyFinancialCmcReport.CurrentAccountRow> saving) {
        populateDailyFinancialSummary(sheet, data, asOf, currencyScope);

        int currentDataStart = DF_CURRENT_DATA_START;
        int currentTotalVnd = firstLabeledRowAtOrAfter(sheet, currentDataStart); // "Total (VND)" row
        int currentProto = currentTotalVnd - currentDataStart;
        int currentTotalUsd = currentTotalVnd + 1;

        int savingTitleIdx = firstLabeledRowAtOrAfter(sheet, currentTotalUsd + 1); // skip blank spacer
        int savingHeaderIdx = savingTitleIdx + 2;
        int savingDataStart = savingHeaderIdx + 1;
        int savingTotalVnd = firstLabeledRowAtOrAfter(sheet, savingDataStart);
        int savingProto = savingTotalVnd - savingDataStart;

        // Bottom-up: Savings section (trailer = its 2 Total rows), then Current.
        int savingDelta = fillSection(sheet, savingDataStart, savingProto,
                /*trailerStart*/ savingTotalVnd, DF_COLS, DF_MERGES, saving.size(),
                (row, i) -> writeDailyFinancialRow(row, saving.get(i)));
        writeDfTotals(sheet, savingTotalVnd + savingDelta, saving);

        int currentDelta = fillSection(sheet, currentDataStart, currentProto,
                /*trailerStart*/ currentTotalVnd, DF_COLS, DF_MERGES, current.size(),
                (row, i) -> writeDailyFinancialRow(row, current.get(i)));
        writeDfTotals(sheet, currentTotalVnd + currentDelta, current);
    }

    /** Daily Financial data row — 13 logical cols (A:B, C, D, E, F..M, N:P). */
    private static void writeDailyFinancialRow(Row row, DailyFinancialCmcReport.CurrentAccountRow r) {
        setText(row, 0, bankName(r.bank(), r.bankCd()));
        setText(row, 2, r.acctNo());
        setText(row, 3, r.acctNickname());
        setText(row, 4, r.ccyCd());
        setNum(row, 5, r.numDebits());
        setNum(row, 6, r.debit());
        setNum(row, 7, r.numInternalDebits());
        setNum(row, 8, r.internalDebit());
        setNum(row, 9, r.numCredits());
        setNum(row, 10, r.credit());
        setNum(row, 11, r.numInternalCredits());
        setNum(row, 12, r.internalCredit());
        setNum(row, 13, r.balance());
    }

    /**
     * Write the per-section {@code Total (VND)} / {@code Total (USD)} rows. The
     * template's label cells (col A, merged A:E) are left verbatim; only the
     * numeric cells (F..N) are set with the currency's summed counters/amounts.
     * VND sums go into {@code totalVndRowIdx}, USD into {@code totalVndRowIdx+1}
     * (the template ships exactly these two total rows per section).
     */
    private static void writeDfTotals(Sheet sheet, int totalVndRowIdx,
                                      List<DailyFinancialCmcReport.CurrentAccountRow> rows) {
        writeDfTotalRow(sheet, totalVndRowIdx, sumByCurrency(rows, "VND"));
        writeDfTotalRow(sheet, totalVndRowIdx + 1, sumByCurrency(rows, "USD"));
    }

    private static void writeDfTotalRow(Sheet sheet, int rowIdx, long[] sums) {
        Row row = sheet.getRow(rowIdx);
        if (row == null) return; // defensive — template ships the row
        // F..M counters/amounts + N balance (indexes 5..13), in the same order
        // the data rows use.
        for (int c = 5; c <= 13; c++) {
            Cell cell = row.getCell(c);
            if (cell == null) continue; // preserve template layout; totals are optional decoration
            cell.setCellValue((double) sums[c - 5]);
        }
    }

    /** Sum a section's rows for one currency → [numDebits, debit, numIntDebit,
     *  intDebit, numCredit, credit, numIntCredit, intCredit, balance]. */
    private static long[] sumByCurrency(
            List<DailyFinancialCmcReport.CurrentAccountRow> rows, String ccy) {
        double numDebits = 0, debit = 0, numIntDebit = 0, intDebit = 0;
        double numCredit = 0, credit = 0, numIntCredit = 0, intCredit = 0, balance = 0;
        for (DailyFinancialCmcReport.CurrentAccountRow r : rows) {
            if (!ccy.equalsIgnoreCase(trim(r.ccyCd()))) continue;
            numDebits += r.numDebits();
            debit += parseAmount(r.debit());
            numIntDebit += r.numInternalDebits();
            intDebit += parseAmount(r.internalDebit());
            numCredit += r.numCredits();
            credit += parseAmount(r.credit());
            numIntCredit += r.numInternalCredits();
            intCredit += parseAmount(r.internalCredit());
            balance += parseAmount(r.balance());
        }
        return new long[]{
                (long) numDebits, (long) debit, (long) numIntDebit, (long) intDebit,
                (long) numCredit, (long) credit, (long) numIntCredit, (long) intCredit, (long) balance};
    }

    /* ================================================================
     *  Summary / status banner population
     * ================================================================ */

    /**
     * Daily Cash summary band — As-of, Currency, and the VND/USD/KRW total
     * boxes (banner grand total + Current Accounts + Savings Accounts). Uses the
     * same per-currency figures the on-screen report shows: {@code currency_summary}
     * when present, else an account-balance rollup by the Current/Savings type
     * split (mirrors {@code DailyCashClient.toSummaryRow} / {@code rollupSummary}
     * — no separate calculation).
     */
    private void populateDailyCashSummary(Sheet sheet, DailyCashReportResponse data,
                                          String asOfDate, String currency) {
        appendToLabel(sheet, 3, 0, formatIsoDmy(asOfDate)); // "As of :" cell (A4)
        appendToLabel(sheet, 3, 2, scopeLabel(currency));   // "Currency :" cell (C4)

        Map<String, DcSummary> byCcy = buildDcSummary(data);
        writeDcSummaryBlock(sheet, "VND", 5, 8, byCcy);   // banner A6, values A9/C9
        writeDcSummaryBlock(sheet, "USD", 9, 12, byCcy);  // banner A10, values A13/C13
        writeDcSummaryBlock(sheet, "KRW", 13, 16, byCcy); // banner A14, values A17/C17
    }

    private static void writeDcSummaryBlock(Sheet sheet, String ccy, int bannerRow, int valueRow,
                                            Map<String, DcSummary> byCcy) {
        DcSummary s = byCcy.getOrDefault(ccy, DcSummary.ZERO);
        appendToLabel(sheet, bannerRow, 0, AmountFormatter.forDisplay(s.total(), ccy));
        Row row = rowAt(sheet, valueRow);
        setNum(row, 0, s.current()); // Current Accounts (merged A:B)
        setNum(row, 2, s.savings()); // Savings Accounts (merged C:D)
    }

    /** Per-currency Daily Cash summary — mirrors {@code DailyCashClient}: prefer
     *  {@code currency_summary} (current = demand deposit, savings = savings,
     *  total = cash+demand+term+loan+savings); else roll up account balances by
     *  the Current/Savings type split. */
    private static Map<String, DcSummary> buildDcSummary(DailyCashReportResponse data) {
        Map<String, DcSummary> map = new LinkedHashMap<>();
        List<DailyCashReportResponse.CurrencySummary> cs = data.currencySummary();
        if (cs != null && !cs.isEmpty()) {
            for (DailyCashReportResponse.CurrencySummary r : cs) {
                BigDecimal current = nzBd(r.totalDemandDeposit());
                BigDecimal savings = nzBd(r.totalSavings());
                BigDecimal total = nzBd(r.totalCash()).add(current)
                        .add(nzBd(r.totalTermDeposit())).add(nzBd(r.totalLoan())).add(savings);
                map.put(trim(r.currencyCd()), new DcSummary(total, current, savings));
            }
            return map;
        }
        List<DailyCashReportResponse.AccountRow> accts =
                data.accounts() != null ? data.accounts() : List.of();
        for (DailyCashReportResponse.AccountRow a : accts) {
            String ccy = trim(a.currencyCd());
            BigDecimal bal = nzBd(a.balance());
            DcSummary prev = map.getOrDefault(ccy, DcSummary.ZERO);
            if (DC_CURRENT_TYPES.contains(trim(a.acctTypeCd()))) {
                map.put(ccy, new DcSummary(prev.total().add(bal), prev.current().add(bal), prev.savings()));
            } else if (DC_SAVING_TYPES.contains(trim(a.acctTypeCd()))) {
                map.put(ccy, new DcSummary(prev.total().add(bal), prev.current(), prev.savings().add(bal)));
            }
        }
        return map;
    }

    private record DcSummary(BigDecimal total, BigDecimal current, BigDecimal savings) {
        static final DcSummary ZERO = new DcSummary(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    /**
     * Daily Financial status band — As-of + Currency (one combined cell) and the
     * VND/USD/KRW Financial-Status boxes (banner total + Current + Savings), taken
     * verbatim from the report's {@code financial_status} rows (no recompute).
     */
    private void populateDailyFinancialSummary(Sheet sheet, DailyFinancialCmcReport data,
                                               String asOf, String currencyScope) {
        injectDfMeta(sheet, 2, 0, formatCompactDmy(asOf), scopeLabel(currencyScope)); // A3 combined cell

        Map<String, DailyFinancialCmcReport.FinancialStatusRow> byCcy = new LinkedHashMap<>();
        List<DailyFinancialCmcReport.FinancialStatusRow> status =
                data.financialStatus() != null ? data.financialStatus() : List.of();
        for (DailyFinancialCmcReport.FinancialStatusRow r : status) byCcy.put(trim(r.ccyCd()), r);

        writeDfStatusBlock(sheet, "VND", 3, 6, byCcy);   // banner A4, values A7/E7
        writeDfStatusBlock(sheet, "USD", 7, 10, byCcy);  // banner A8, values A11/E11
        writeDfStatusBlock(sheet, "KRW", 11, 14, byCcy); // banner A12, values A15/E15
    }

    private static void writeDfStatusBlock(Sheet sheet, String ccy, int bannerRow, int valueRow,
                                           Map<String, DailyFinancialCmcReport.FinancialStatusRow> byCcy) {
        DailyFinancialCmcReport.FinancialStatusRow r = byCcy.get(ccy);
        String total = r != null ? r.total() : "0";
        String current = r != null ? r.currentAccounts() : "0";
        String savings = r != null ? r.savingAccounts() : "0";
        appendToLabel(sheet, bannerRow, 0, AmountFormatter.forDisplay(total, ccy));
        Row row = rowAt(sheet, valueRow);
        setNum(row, 0, current); // Current Accounts (merged A:D)
        setNum(row, 4, savings); // Savings Account (merged E:F)
    }

    /** Append text to an existing localized label cell (trailing whitespace
     *  normalized to one space), preserving the template's localized prefix. */
    private static void appendToLabel(Sheet sheet, int rowIdx, int col, String value) {
        Row row = rowAt(sheet, rowIdx);
        Cell cell = row.getCell(col);
        String prefix = (cell != null && cell.getCellType() == CellType.STRING)
                ? cell.getStringCellValue() : "";
        if (cell == null) cell = row.createCell(col);
        cell.setCellValue(prefix.stripTrailing() + " " + (value == null ? "" : value));
    }

    /** Daily Financial combined "As of : …   Currency : …" cell — split on the
     *  wide whitespace gap between the two localized labels and inject the values. */
    private static void injectDfMeta(Sheet sheet, int rowIdx, int col, String date, String scope) {
        Row row = rowAt(sheet, rowIdx);
        Cell cell = row.getCell(col);
        String tpl = (cell != null && cell.getCellType() == CellType.STRING)
                ? cell.getStringCellValue() : "";
        String[] parts = tpl.split("\\s{3,}");
        String asOfLabel = parts.length > 0 && !parts[0].isBlank() ? parts[0].strip() : "As of :";
        String ccyLabel = parts.length > 1 ? parts[1].strip() : "Currency :";
        if (cell == null) cell = row.createCell(col);
        cell.setCellValue(asOfLabel + " " + date + "     " + ccyLabel + " " + scope);
    }

    /** Currency-scope query value → display label (ALL/blank → "All"). */
    private static String scopeLabel(String raw) {
        if (raw == null || raw.isBlank() || raw.trim().equalsIgnoreCase("ALL")) return "All";
        return raw.trim().toUpperCase();
    }

    /** ISO {@code yyyy-MM-dd} → {@code dd-MM-yyyy}; passthrough otherwise. */
    private static String formatIsoDmy(String iso) {
        if (iso != null && iso.length() == 10 && iso.charAt(4) == '-' && iso.charAt(7) == '-') {
            return iso.substring(8, 10) + "-" + iso.substring(5, 7) + "-" + iso.substring(0, 4);
        }
        return iso == null ? "" : iso;
    }

    /** Compact {@code yyyyMMdd} → {@code dd-MM-yyyy}; passthrough otherwise. */
    private static String formatCompactDmy(String yyyymmdd) {
        String s = trim(yyyymmdd);
        if (s.length() == 8 && s.chars().allMatch(Character::isDigit)) {
            return s.substring(6, 8) + "-" + s.substring(4, 6) + "-" + s.substring(0, 4);
        }
        return s;
    }

    private static BigDecimal nzBd(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    /** Get the row at {@code idx}, creating it if absent. */
    private static Row rowAt(Sheet sheet, int idx) {
        Row row = sheet.getRow(idx);
        return row != null ? row : sheet.createRow(idx);
    }

    /* ================================================================
     *  Shared scaffolding
     * ================================================================ */

    /** Per-row data writer — receives the (possibly newly created) row and the
     *  0-based index into the record list. */
    @FunctionalInterface
    private interface RowWriter {
        void write(Row row, int recordIndex);
    }

    /**
     * Load the locale template, hand the first sheet to {@code populator}, and
     * serialize. The templates are OOXML {@code .xlsx} — {@link WorkbookFactory}
     * auto-detects the format and returns an {@code XSSFWorkbook}, so the report
     * stays {@code .xlsx}.
     */
    private byte[] build(String relativePath, java.util.function.Consumer<Sheet> populator) {
        String resourcePath = TEMPLATE_ROOT + relativePath;
        try (InputStream in = new ClassPathResource(resourcePath).getInputStream();
             Workbook wb = WorkbookFactory.create(in);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = wb.getSheetAt(0);
            populator.accept(sheet);

            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "Failed to generate report download (" + relativePath + ")", e);
        }
    }

    /**
     * Size one section's data region to exactly {@code count} rows and write
     * each row, preserving the template's per-column cell style and per-row
     * merges. Returns the delta ({@code count - protoRows}) so the caller can
     * relocate trailing landmarks.
     *
     * @param dataStart      0-based first data row of the section
     * @param protoRows      number of pre-styled prototype data rows in the template
     * @param trailerStart   0-based first row that must stay immediately below the
     *                       data (shifted to make room); {@code -1} when nothing
     *                       follows (last section)
     * @param colCount       physical column count to style on created rows
     * @param mergeCols      {@code {firstCol,lastCol}} pairs to merge per data row
     * @param count          number of records to write
     * @param writer         per-row writer
     */
    private static int fillSection(Sheet sheet, int dataStart, int protoRows,
                                   int trailerStart, int colCount, int[][] mergeCols,
                                   int count, RowWriter writer) {
        CellStyle[] colStyle = captureColStyles(sheet, dataStart, colCount);
        int delta = count - protoRows;

        // Remove the prototype data-row merges BEFORE shifting. Otherwise a
        // contraction shift would move a below-section merge (e.g. the Savings
        // section title A:J) up into a row that still carries a data-row merge
        // (B:C / A:B), and POI throws on the overlap. Section title / header /
        // total-row merges live outside this range and are relocated cleanly
        // by shiftRows.
        removeMergesInRange(sheet, dataStart, dataStart + protoRows - 1);

        if (delta != 0 && trailerStart >= 0) {
            int lastRow = sheet.getLastRowNum();
            if (trailerStart <= lastRow) {
                sheet.shiftRows(trailerStart, lastRow, delta);
            }
        }

        // Write every data row: ensure the row + cells exist with the captured
        // prototype style, then re-add the per-row merges and write values.
        for (int i = 0; i < count; i++) {
            int idx = dataStart + i;
            Row row = sheet.getRow(idx);
            if (row == null) row = sheet.createRow(idx);
            for (int c = 0; c < colCount; c++) {
                Cell cell = row.getCell(c);
                if (cell == null) cell = row.createCell(c);
                if (colStyle[c] != null) cell.setCellStyle(colStyle[c]);
            }
            for (int[] m : mergeCols) {
                addRowMerge(sheet, idx, m[0], m[1]);
            }
            writer.write(row, i);
        }

        // On contraction with no trailer (last section), blank the leftover
        // prototype rows so the template's sample row never leaks.
        if (delta < 0 && trailerStart < 0) {
            for (int gi = dataStart + count; gi < dataStart + protoRows; gi++) {
                Row row = sheet.getRow(gi);
                if (row != null) {
                    for (int c = 0; c < colCount; c++) {
                        Cell cell = row.getCell(c);
                        if (cell != null) cell.setBlank();
                    }
                }
            }
        }
        return delta;
    }

    /** Remove every merged region wholly or partly within {@code [fromRow, toRow]}. */
    private static void removeMergesInRange(Sheet sheet, int fromRow, int toRow) {
        for (int i = sheet.getNumMergedRegions() - 1; i >= 0; i--) {
            CellRangeAddress r = sheet.getMergedRegion(i);
            if (r.getFirstRow() <= toRow && r.getLastRow() >= fromRow) {
                sheet.removeMergedRegion(i);
            }
        }
    }

    /** Capture the per-column data-row style from the template's first data row. */
    private static CellStyle[] captureColStyles(Sheet sheet, int dataStart, int colCount) {
        CellStyle[] colStyle = new CellStyle[colCount];
        Row proto = sheet.getRow(dataStart);
        if (proto != null) {
            for (int c = 0; c < colCount; c++) {
                Cell cell = proto.getCell(c);
                if (cell != null) colStyle[c] = cell.getCellStyle();
            }
        }
        return colStyle;
    }

    /** Add a single-row merge, removing any existing region that overlaps this
     *  row so {@code addMergedRegion} never throws on an overlap. */
    private static void addRowMerge(Sheet sheet, int rowIdx, int firstCol, int lastCol) {
        for (int i = sheet.getNumMergedRegions() - 1; i >= 0; i--) {
            CellRangeAddress r = sheet.getMergedRegion(i);
            if (r.getFirstRow() <= rowIdx && r.getLastRow() >= rowIdx
                    && r.getFirstColumn() <= lastCol && r.getLastColumn() >= firstCol) {
                sheet.removeMergedRegion(i);
            }
        }
        sheet.addMergedRegion(new CellRangeAddress(rowIdx, rowIdx, firstCol, lastCol));
    }

    /** First row at/after {@code startIdx} whose column-A cell holds a non-blank
     *  string. Data rows have a blank col A; section-title / total rows carry
     *  a label there, so this reliably finds the next structural landmark. */
    private static int firstLabeledRowAtOrAfter(Sheet sheet, int startIdx) {
        int last = sheet.getLastRowNum();
        for (int i = startIdx; i <= last; i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;
            Cell a = row.getCell(0);
            if (a == null) continue;
            if (a.getCellType() == org.apache.poi.ss.usermodel.CellType.STRING
                    && !a.getStringCellValue().trim().isEmpty()) {
                return i;
            }
        }
        // Defensive: should never happen with the bundled templates.
        throw new IllegalStateException("report template landmark not found at/after row " + startIdx);
    }

    private static void setText(Row row, int col, String value) {
        Cell cell = row.getCell(col);
        if (cell == null) cell = row.createCell(col);
        cell.setCellValue(value == null ? "" : value);
    }

    private static void setNum(Row row, int col, BigDecimal value) {
        Cell cell = row.getCell(col);
        if (cell == null) cell = row.createCell(col);
        cell.setCellValue(value == null ? 0d : value.doubleValue());
    }

    private static void setNum(Row row, int col, long value) {
        Cell cell = row.getCell(col);
        if (cell == null) cell = row.createCell(col);
        cell.setCellValue((double) value);
    }

    /** Set a numeric cell from a wire decimal String; blank/unparseable → 0. */
    private static void setNum(Row row, int col, String value) {
        Cell cell = row.getCell(col);
        if (cell == null) cell = row.createCell(col);
        cell.setCellValue(parseAmount(value));
    }

    private static double parseAmount(String value) {
        if (value == null || value.isBlank()) return 0d;
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException ex) {
            return 0d;
        }
    }

    private static String bankName(String nm, String cd) {
        if (nm != null && !nm.isBlank()) return nm;
        return cd == null ? "" : cd;
    }

    private static <T> List<T> filter(List<T> src, Predicate<T> p) {
        List<T> out = new ArrayList<>();
        for (T t : src) if (p.test(t)) out.add(t);
        return out;
    }

    /**
     * Resolve the template locale. Priority: explicit {@code langParam} → JWT
     * {@code langCd} → EN. Only EN/KO/VI have a template; KM and anything else
     * fall back to EN (legacy {@code LocaleHelper} parity — no KM template).
     */
    static String resolveLang(String langParam) {
        LangCd lc = (langParam != null && !langParam.isBlank())
                ? LangCd.fromCodeOrDefault(langParam)
                : TenantContext.currentLangCd();
        if (lc == null) return "EN";
        return switch (lc) {
            case KO -> "KO";
            case VI -> "VI";
            default -> "EN"; // EN + KM (no template) + any unmapped
        };
    }

    /** Localized Daily Cash download filename (template basename). */
    static String dailyCashFileName(String lang) {
        return switch (lang) {
            case "KO" -> "일일시재.xlsx";
            case "VI" -> "Báo cáo dòng tiền hàng ngày.xlsx";
            default -> "Daily Cash.xlsx";
        };
    }

    /** Localized Daily Financial download filename (template basename). */
    static String dailyFinancialFileName(String lang) {
        return switch (lang) {
            case "KO" -> "자금일보.xlsx";
            case "VI" -> "Báo cáo tài chính hàng ngày.xlsx";
            default -> "Daily Financial.xlsx";
        };
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }

    private static String requireTenant() {
        String bizKey = TenantContext.currentBizKey();
        if (bizKey == null) {
            throw new IllegalStateException("tenant context not populated");
        }
        return bizKey;
    }
}
