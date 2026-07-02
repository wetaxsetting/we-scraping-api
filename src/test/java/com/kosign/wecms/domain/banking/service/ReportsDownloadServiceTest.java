package com.kosign.wecms.domain.banking.service;

import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.banking.dto.DailyCashReportResponse;
import com.kosign.wecms.domain.banking.dto.DailyFinancialCmcReport;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.CellRangeAddress;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test for {@link ReportsDownloadService}. Drives the real classpath
 * {@code .xlsx} templates ({@code templates/reports/**}) through Apache POI and
 * asserts the produced workbook — locale template selection (EN/KO/VI), KM→EN +
 * blank→JWT-langCd fallback, localized filename, the two-section account
 * layouts (Daily Cash 8 cols; Daily Financial 13 cols + per-section totals), the
 * Current/Savings split, the **fully-populated summary/status band** (As-of,
 * Currency, and the VND/USD/KRW total boxes), populated + empty + overflow
 * datasets, preserved merged cells + template styles, numeric amount cells, and
 * the {@code includeInternalTransfers} pass-through.
 */
class ReportsDownloadServiceTest {

    /** Daily Cash: Current section header idx 20 (Excel 21), data idx 21 (Excel 22). */
    private static final int DC_HEADER = 20;
    private static final int DC_DATA = 21;
    /** Daily Financial: Current section header idx 17 (Excel 18), data idx 18 (Excel 19). */
    private static final int DF_HEADER = 17;
    private static final int DF_DATA = 18;

    private DailyCashReportService dailyCashReportService;
    private ReportService reportService;
    private ReportsDownloadService service;

    @BeforeEach
    void setUp() {
        dailyCashReportService = mock(DailyCashReportService.class);
        reportService = mock(ReportService.class);
        service = new ReportsDownloadService(dailyCashReportService, reportService);
        TenantContext.set("tester", "VN_VNM0004_000001", LangCd.EN);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    /* ----------------------------------------------------------------- */

    private void stubDailyCash(List<DailyCashReportResponse.AccountRow> accounts) {
        stubDailyCash(accounts, List.of());
    }

    private void stubDailyCash(List<DailyCashReportResponse.AccountRow> accounts,
                               List<DailyCashReportResponse.CurrencySummary> summary) {
        when(dailyCashReportService.search(anyString(), any()))
                .thenReturn(new DailyCashReportResponse(
                        "2026-06-24", "ALL", summary, accounts, List.of()));
    }

    private void stubDailyFinancial(List<DailyFinancialCmcReport.CurrentAccountRow> rows) {
        stubDailyFinancial(rows, List.of());
    }

    private void stubDailyFinancial(List<DailyFinancialCmcReport.CurrentAccountRow> rows,
                                    List<DailyFinancialCmcReport.FinancialStatusRow> status) {
        when(reportService.dailyFinancial(any(), any(), anyBoolean(), anyBoolean()))
                .thenReturn(new DailyFinancialCmcReport(
                        "20260624", "ALL", true, true, status, rows));
    }

    private static DailyCashReportResponse.CurrencySummary dcSummary(
            String ccy, String cash, String demand, String term, String loan, String savings) {
        return new DailyCashReportResponse.CurrencySummary(
                ccy, new BigDecimal(cash), new BigDecimal(demand), new BigDecimal(term),
                new BigDecimal(loan), new BigDecimal(savings));
    }

    private static DailyFinancialCmcReport.FinancialStatusRow dfStatus(
            String ccy, String total, String current, String savings) {
        return new DailyFinancialCmcReport.FinancialStatusRow(ccy, total, current, savings);
    }

    private static DailyCashReportResponse.AccountRow dcRow(
            String acctTypeCd, String bankCd, String bankNm, String acctNo, String ccy,
            long sendCount, String sendAmount, long recvCount, String recvAmount, String balance) {
        return new DailyCashReportResponse.AccountRow(
                "VN_VNM0004_000001", null, "20260624", ccy, acctTypeCd, bankCd, bankNm, acctNo,
                new BigDecimal(balance), null, null, null,
                BigDecimal.valueOf(sendCount), new BigDecimal(sendAmount),
                BigDecimal.valueOf(recvCount), new BigDecimal(recvAmount));
    }

    private static DailyFinancialCmcReport.CurrentAccountRow dfRow(
            String acctTypeCd, String bankCd, String bank, String acctNo, String nick, String ccy,
            int numDebits, String debit, int numIntDebit, String intDebit,
            int numCredits, String credit, int numIntCredit, String intCredit, String balance) {
        return new DailyFinancialCmcReport.CurrentAccountRow(
                bankCd + "_" + acctNo, bank, bankCd, acctNo, nick, ccy, acctTypeCd, "Y",
                numDebits, debit, numIntDebit, intDebit, numCredits, credit,
                numIntCredit, intCredit, balance, List.of());
    }

    private static Sheet readSheet(ReportsDownloadService.GeneratedReport report) throws Exception {
        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(report.content()))) {
            return wb.getSheetAt(0);
        }
    }

    private static String cell(Sheet sheet, int rowIdx, int colIdx) {
        Row row = sheet.getRow(rowIdx);
        if (row == null) return null;
        Cell c = row.getCell(colIdx);
        return c == null ? null : c.toString();
    }

    private static double num(Sheet sheet, int rowIdx, int colIdx) {
        return sheet.getRow(rowIdx).getCell(colIdx).getNumericCellValue();
    }

    private static int findRowByColAFrom(Sheet sheet, String needle, int startIdx) {
        for (int i = startIdx; i <= sheet.getLastRowNum(); i++) {
            Row r = sheet.getRow(i);
            if (r == null) continue;
            Cell a = r.getCell(0);
            if (a != null && a.getCellType() == CellType.STRING
                    && a.getStringCellValue().contains(needle)) {
                return i;
            }
        }
        return -1;
    }

    private static int findRowByColA(Sheet sheet, String needle) {
        return findRowByColAFrom(sheet, needle, 0);
    }

    private static boolean anyRowHas(Sheet sheet, int col, String value) {
        for (int i = 0; i <= sheet.getLastRowNum(); i++) {
            Row r = sheet.getRow(i);
            if (r == null) continue;
            Cell c = r.getCell(col);
            if (c != null && c.getCellType() == CellType.STRING
                    && c.getStringCellValue().equals(value)) {
                return true;
            }
        }
        return false;
    }

    private static List<DailyCashReportResponse.AccountRow> manyCurrent(int n) {
        List<DailyCashReportResponse.AccountRow> rows = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            rows.add(dcRow("03", "9704" + i, "BANK" + i, "ACC" + i, "VND",
                    1, "100", 1, "200", "1000"));
        }
        return rows;
    }

    /* =================================================================
     *  Daily Cash — summary band
     * ================================================================= */

    @Test
    void dailyCash_summaryBand_asOfCurrencyAndVndUsdKrwPopulated() throws Exception {
        stubDailyCash(List.of(), List.of(
                dcSummary("VND", "0", "1000", "0", "0", "200"),
                dcSummary("USD", "0", "50", "0", "0", "10"),
                dcSummary("KRW", "0", "700", "0", "0", "80")));

        Sheet sheet = readSheet(service.generateDailyCash("2026-06-24", "ALL", "EN"));

        // As of (A4) + Currency (C4) populated from the request.
        assertThat(cell(sheet, 3, 0)).contains("As of").contains("24-06-2026");
        assertThat(cell(sheet, 3, 2)).contains("Currency").contains("All");

        // VND block — banner (A6) carries a value; Current (A9) + Savings (C9).
        assertThat(cell(sheet, 5, 0)).contains("VND").containsPattern("\\d");
        assertThat(num(sheet, 8, 0)).isEqualTo(1000d);
        assertThat(num(sheet, 8, 2)).isEqualTo(200d);
        // USD block — banner (A10), Current (A13), Savings (C13).
        assertThat(cell(sheet, 9, 0)).contains("USD").containsPattern("\\d");
        assertThat(num(sheet, 12, 0)).isEqualTo(50d);
        assertThat(num(sheet, 12, 2)).isEqualTo(10d);
        // KRW block (the newly added third block) — banner (A14), Current (A17), Savings (C17).
        assertThat(cell(sheet, 13, 0)).contains("KRW").containsPattern("\\d");
        assertThat(num(sheet, 16, 0)).isEqualTo(700d);
        assertThat(num(sheet, 16, 2)).isEqualTo(80d);
    }

    @Test
    void dailyCash_summaryBand_rollsUpFromAccountsWhenNoCurrencySummary() throws Exception {
        // No currency_summary → rollup from account balances by the Current/Savings
        // split (03/04/07 → Current, 05/06/08 → Savings), mirroring DailyCashClient.
        stubDailyCash(List.of(
                dcRow("03", "1", "B1", "A1", "VND", 0, "0", 0, "0", "600"),
                dcRow("07", "2", "B2", "A2", "VND", 0, "0", 0, "0", "400"),  // 07 → Current
                dcRow("08", "3", "B3", "A3", "VND", 0, "0", 0, "0", "150")), // 08 → Savings
                List.of());
        Sheet sheet = readSheet(service.generateDailyCash("2026-06-24", "VND", "EN"));
        assertThat(num(sheet, 8, 0)).isEqualTo(1000d); // VND Current = 600 + 400 (03 + 07)
        assertThat(num(sheet, 8, 2)).isEqualTo(150d);  // VND Savings = 150 (08)
        assertThat(cell(sheet, 3, 2)).contains("VND"); // Currency scope label
    }

    @Test
    void dailyCash_summaryBand_absentCurrencyRendersZero() throws Exception {
        stubDailyCash(List.of(), List.of(dcSummary("VND", "0", "1000", "0", "0", "200")));
        Sheet sheet = readSheet(service.generateDailyCash("2026-06-24", "ALL", "EN"));
        // USD + KRW have no data → zero, but the blocks still render.
        assertThat(num(sheet, 12, 0)).isEqualTo(0d);
        assertThat(num(sheet, 16, 0)).isEqualTo(0d);
        assertThat(cell(sheet, 13, 0)).contains("KRW");
    }

    /* =================================================================
     *  Daily Cash — account sections
     * ================================================================= */

    @Test
    void dailyCash_en_fullColumnMappingAndSplit() throws Exception {
        stubDailyCash(List.of(
                dcRow("03", "970436", "VCB", "700005285280", "VND", 2, "4000000", 3, "5000000", "1659685693"),
                dcRow("05", "970418", "BIDV", "12100999", "VND", 1, "100", 1, "200", "555")));

        var report = service.generateDailyCash("2026-06-24", "ALL", "EN");
        assertThat(report.fileName()).isEqualTo("Daily Cash.xlsx");

        Sheet sheet = readSheet(report);
        assertThat(cell(sheet, 0, 0)).isEqualTo("Daily Cash");
        assertThat(cell(sheet, DC_HEADER, 0)).isEqualTo("Bank");
        // First Current account (type 03) at the first data row.
        assertThat(cell(sheet, DC_DATA, 0)).isEqualTo("VCB");
        assertThat(cell(sheet, DC_DATA, 1)).isEqualTo("700005285280");
        assertThat(cell(sheet, DC_DATA, 3)).isEqualTo("VND");
        assertThat(num(sheet, DC_DATA, 5)).isEqualTo(4000000d);
        assertThat(num(sheet, DC_DATA, 8)).isEqualTo(1659685693d);
        // Savings account (type 05) appears in its section below.
        assertThat(anyRowHas(sheet, 0, "BIDV")).isTrue();
        assertThat(anyRowHas(sheet, 1, "12100999")).isTrue();
    }

    @Test
    void dailyCash_groupsTypes07CurrentAnd08Saving() throws Exception {
        stubDailyCash(List.of(
                dcRow("07", "970111", "BANK07", "A07", "KRW", 1, "1", 1, "2", "700"),
                dcRow("03", "970222", "BANK03", "A03", "VND", 1, "1", 1, "2", "300"),
                dcRow("08", "970333", "BANK08", "A08", "KRW", 1, "1", 1, "2", "800"),
                dcRow("02", "970444", "BANK02", "A02", "VND", 1, "1", 1, "2", "0")));
        Sheet sheet = readSheet(service.generateDailyCash("2026-06-24", "ALL", "EN"));
        assertThat(cell(sheet, DC_DATA, 0)).isEqualTo("BANK07");
        assertThat(cell(sheet, DC_DATA + 1, 0)).isEqualTo("BANK03");
        assertThat(anyRowHas(sheet, 0, "BANK08")).isTrue();
        assertThat(anyRowHas(sheet, 0, "BANK02")).isFalse();
    }

    @Test
    void dailyCash_ko_selectsKoreanTemplate() throws Exception {
        stubDailyCash(List.of());
        var report = service.generateDailyCash("2026-06-24", "ALL", "KO");
        assertThat(report.fileName()).isEqualTo("일일시재.xlsx");
        Sheet sheet = readSheet(report);
        assertThat(cell(sheet, 0, 0)).isEqualTo("일일시재");
        // KRW summary block present in the Korean template too.
        assertThat(findRowByColA(sheet, "KRW")).isGreaterThanOrEqualTo(0);
    }

    @Test
    void dailyCash_vi_selectsVietnameseTemplate() throws Exception {
        stubDailyCash(List.of());
        var report = service.generateDailyCash("2026-06-24", "ALL", "VI");
        assertThat(report.fileName()).isEqualTo("Báo cáo dòng tiền hàng ngày.xlsx");
        assertThat(cell(readSheet(report), 0, 0)).isEqualTo("Báo cáo dòng tiền hàng ngày");
    }

    @Test
    void dailyCash_km_fallsBackToEn() throws Exception {
        stubDailyCash(List.of());
        var report = service.generateDailyCash("2026-06-24", "ALL", "KM");
        assertThat(report.fileName()).isEqualTo("Daily Cash.xlsx");
        assertThat(cell(readSheet(report), 0, 0)).isEqualTo("Daily Cash");
    }

    @Test
    void dailyCash_blankLang_usesJwtLangCd() throws Exception {
        TenantContext.clear();
        TenantContext.set("tester", "VN_VNM0004_000001", LangCd.VI);
        stubDailyCash(List.of());
        var report = service.generateDailyCash("2026-06-24", "ALL", null);
        assertThat(report.fileName()).isEqualTo("Báo cáo dòng tiền hàng ngày.xlsx");
    }

    @Test
    void dailyCash_emptyDataset_producesReadableWorkbook() throws Exception {
        stubDailyCash(List.of());
        Sheet sheet = readSheet(service.generateDailyCash("2026-06-24", "ALL", "EN"));
        assertThat(cell(sheet, 0, 0)).isEqualTo("Daily Cash");
        assertThat(cell(sheet, DC_HEADER, 0)).isEqualTo("Bank");
        // Summary still populated (zeros) — banners intact.
        assertThat(cell(sheet, 5, 0)).contains("VND");
        assertThat(cell(sheet, 13, 0)).contains("KRW");
        assertThat(findRowByColAFrom(sheet, "●", DC_DATA)).isGreaterThanOrEqualTo(DC_DATA);
    }

    @Test
    void dailyCash_overflow_writesAllRowsAndKeepsSavingsSection() throws Exception {
        stubDailyCash(manyCurrent(8));
        Sheet sheet = readSheet(service.generateDailyCash("2026-06-24", "VND", "EN"));
        for (int i = 0; i < 8; i++) {
            assertThat(cell(sheet, DC_DATA + i, 0)).isEqualTo("BANK" + i);
        }
        int gapRow = DC_DATA + 6;
        assertThat(sheet.getMergedRegions())
                .anyMatch(r -> r.equals(new CellRangeAddress(gapRow, gapRow, 1, 2)));
        assertThat(findRowByColAFrom(sheet, "●", DC_DATA + 8)).isGreaterThanOrEqualTo(DC_DATA + 8);
    }

    @Test
    void dailyCash_preservesMergedTitleAndDataCellStyle() throws Exception {
        stubDailyCash(List.of(dcRow("04", "970436", "VCB", "111", "USD", 1, "1", 1, "2", "100")));
        Sheet sheet = readSheet(service.generateDailyCash("2026-06-24", "ALL", "EN"));
        assertThat(sheet.getMergedRegions())
                .anyMatch(r -> r.equals(CellRangeAddress.valueOf("A1:J2")))
                // KRW banner merge added by the template generator.
                .anyMatch(r -> r.equals(CellRangeAddress.valueOf("A14:J15")));
        assertThat(sheet.getRow(DC_DATA).getCell(0).getCellStyle()).isNotNull();
        assertThat(sheet.getMergedRegions())
                .anyMatch(r -> r.equals(new CellRangeAddress(DC_DATA, DC_DATA, 1, 2)))
                .anyMatch(r -> r.equals(new CellRangeAddress(DC_DATA, DC_DATA, 8, 9)));
    }

    /* =================================================================
     *  Daily Financial — status band
     * ================================================================= */

    @Test
    void dailyFinancial_statusBand_asOfCurrencyAndVndUsdKrwPopulated() throws Exception {
        stubDailyFinancial(List.of(), List.of(
                dfStatus("VND", "1200", "1000", "200"),
                dfStatus("USD", "60", "50", "10"),
                dfStatus("KRW", "780", "700", "80")));

        Sheet sheet = readSheet(service.generateDailyFinancial("20260624", "ALL", true, true, "EN"));

        // As of + Currency (combined cell A3) populated.
        assertThat(cell(sheet, 2, 0)).contains("As of").contains("24-06-2026")
                .contains("Currency").contains("All");
        // VND status — banner A4, Current A7, Savings E7.
        assertThat(cell(sheet, 3, 0)).contains("VND").containsPattern("\\d");
        assertThat(num(sheet, 6, 0)).isEqualTo(1000d);
        assertThat(num(sheet, 6, 4)).isEqualTo(200d);
        // USD status — banner A8, Current A11, Savings E11.
        assertThat(cell(sheet, 7, 0)).contains("USD").containsPattern("\\d");
        assertThat(num(sheet, 10, 0)).isEqualTo(50d);
        assertThat(num(sheet, 10, 4)).isEqualTo(10d);
        // KRW status (newly added block) — banner A12, Current A15, Savings E15.
        assertThat(cell(sheet, 11, 0)).contains("KRW").containsPattern("\\d");
        assertThat(num(sheet, 14, 0)).isEqualTo(700d);
        assertThat(num(sheet, 14, 4)).isEqualTo(80d);
    }

    /* =================================================================
     *  Daily Financial — account sections
     * ================================================================= */

    @Test
    void dailyFinancial_en_fullColumnMappingAndSplit() throws Exception {
        stubDailyFinancial(List.of(
                dfRow("03", "970436", "VCB", "700005285280", "MAIN", "VND",
                        2, "4000000", 1, "100", 3, "5000000", 1, "200", "1659685693"),
                dfRow("05", "970418", "BIDV", "12100999", "SAV", "VND",
                        1, "10", 0, "0", 1, "20", 0, "0", "555")));

        var report = service.generateDailyFinancial("20260624", "ALL", true, true, "EN");
        assertThat(report.fileName()).isEqualTo("Daily Financial.xlsx");

        Sheet sheet = readSheet(report);
        assertThat(cell(sheet, 0, 0)).isEqualTo("Daily Financial");
        assertThat(cell(sheet, DF_HEADER, 0)).isEqualTo("Bank");
        assertThat(cell(sheet, DF_DATA, 0)).isEqualTo("VCB");
        assertThat(cell(sheet, DF_DATA, 2)).isEqualTo("700005285280");
        assertThat(cell(sheet, DF_DATA, 3)).isEqualTo("MAIN");
        assertThat(cell(sheet, DF_DATA, 4)).isEqualTo("VND");
        assertThat(num(sheet, DF_DATA, 6)).isEqualTo(4000000d);
        assertThat(num(sheet, DF_DATA, 13)).isEqualTo(1659685693d);
        assertThat(anyRowHas(sheet, 2, "12100999")).isTrue();
    }

    @Test
    void dailyFinancial_populatesCurrentSectionTotals() throws Exception {
        stubDailyFinancial(List.of(
                dfRow("03", "970436", "VCB", "111", "A", "VND",
                        2, "100", 0, "0", 1, "50", 0, "0", "1000"),
                dfRow("07", "970418", "BIDV", "222", "B", "VND",
                        3, "200", 0, "0", 2, "70", 0, "0", "2000")));
        Sheet sheet = readSheet(service.generateDailyFinancial("20260624", "VND", true, true, "EN"));
        int totalVnd = findRowByColA(sheet, "Total (VND)");
        assertThat(totalVnd).isGreaterThan(DF_DATA);
        assertThat(num(sheet, totalVnd, 5)).isEqualTo(5d);      // sum numDebits
        assertThat(num(sheet, totalVnd, 13)).isEqualTo(3000d);  // sum balance
    }

    @Test
    void dailyFinancial_ko_selectsKoreanTemplate() throws Exception {
        stubDailyFinancial(List.of());
        var report = service.generateDailyFinancial("20260624", "ALL", true, true, "KO");
        assertThat(report.fileName()).isEqualTo("자금일보.xlsx");
        Sheet sheet = readSheet(report);
        assertThat(cell(sheet, 0, 0)).isEqualTo("자금일보");
        assertThat(findRowByColA(sheet, "KRW")).isGreaterThanOrEqualTo(0);
    }

    @Test
    void dailyFinancial_vi_selectsVietnameseTemplate() throws Exception {
        stubDailyFinancial(List.of());
        var report = service.generateDailyFinancial("20260624", "ALL", true, true, "VI");
        assertThat(report.fileName()).isEqualTo("Báo cáo tài chính hàng ngày.xlsx");
        assertThat(cell(readSheet(report), 0, 0)).isEqualTo("Báo cáo tài chính hàng ngày");
    }

    @Test
    void dailyFinancial_km_fallsBackToEn() throws Exception {
        stubDailyFinancial(List.of());
        var report = service.generateDailyFinancial("20260624", "ALL", true, true, "KM");
        assertThat(report.fileName()).isEqualTo("Daily Financial.xlsx");
        assertThat(cell(readSheet(report), 0, 0)).isEqualTo("Daily Financial");
    }

    @Test
    void dailyFinancial_emptyDataset_producesReadableWorkbook() throws Exception {
        stubDailyFinancial(List.of());
        Sheet sheet = readSheet(service.generateDailyFinancial("20260624", "ALL", true, true, "EN"));
        assertThat(cell(sheet, 0, 0)).isEqualTo("Daily Financial");
        assertThat(cell(sheet, DF_HEADER, 0)).isEqualTo("Bank");
        // Status band still present (zeros); KRW block intact.
        assertThat(cell(sheet, 11, 0)).contains("KRW");
        assertThat(findRowByColA(sheet, "Total (VND)")).isGreaterThan(0);
    }

    @Test
    void dailyFinancial_preservesMergedTitleAndDataRowMerges() throws Exception {
        stubDailyFinancial(List.of(dfRow("03", "970436", "VCB", "111", "A", "VND",
                1, "1", 0, "0", 1, "2", 0, "0", "100")));
        Sheet sheet = readSheet(service.generateDailyFinancial("20260624", "ALL", true, true, "EN"));
        assertThat(sheet.getMergedRegions())
                .anyMatch(r -> r.equals(CellRangeAddress.valueOf("A1:P2")))
                // KRW status banner merge added by the template generator.
                .anyMatch(r -> r.equals(CellRangeAddress.valueOf("A12:P13")));
        assertThat(sheet.getMergedRegions())
                .anyMatch(r -> r.equals(new CellRangeAddress(DF_DATA, DF_DATA, 0, 1)))
                .anyMatch(r -> r.equals(new CellRangeAddress(DF_DATA, DF_DATA, 13, 15)));
        assertThat(sheet.getRow(DF_DATA).getCell(0).getCellStyle()).isNotNull();
    }

    @Test
    void dailyFinancial_passesFiltersThroughToReportService() {
        stubDailyFinancial(List.of());
        service.generateDailyFinancial("20260624", "VND", false, false, "EN");
        verify(reportService).dailyFinancial(eq("20260624"), eq("VND"), eq(false), eq(false));
    }

    @Test
    void dailyFinancial_overflow_writesAllRowsAndKeepsSavingsSection() throws Exception {
        List<DailyFinancialCmcReport.CurrentAccountRow> rows = new ArrayList<>();
        for (int i = 0; i < 9; i++) {  // > 6 EN prototype rows
            rows.add(dfRow("03", "970" + i, "BANK" + i, "ACC" + i, "N" + i, "VND",
                    1, "100", 0, "0", 1, "200", 0, "0", "1000"));
        }
        rows.add(dfRow("05", "980000", "SAVINGBANK", "S1", "SN", "VND",
                1, "1", 0, "0", 1, "2", 0, "0", "50"));
        stubDailyFinancial(rows);
        Sheet sheet = readSheet(service.generateDailyFinancial("20260624", "VND", true, true, "EN"));
        for (int i = 0; i < 9; i++) {
            assertThat(cell(sheet, DF_DATA + i, 0)).isEqualTo("BANK" + i);
        }
        assertThat(anyRowHas(sheet, 2, "S1")).isTrue();
        int firstTotal = findRowByColA(sheet, "Total (VND)");
        assertThat(firstTotal).isGreaterThan(DF_DATA + 8);
    }
}
