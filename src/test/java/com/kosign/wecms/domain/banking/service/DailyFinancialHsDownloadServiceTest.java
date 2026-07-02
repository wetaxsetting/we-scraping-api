package com.kosign.wecms.domain.banking.service;

import com.kosign.wecms.domain.banking.dto.DailyFinancialHsReport;
import com.kosign.wecms.domain.banking.dto.DailyFinancialHsReport.LedgerRow;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Smoke test for the HS Excel download (legacy {@code rept_0017_001_r002_excel}):
 * the generator loads the locale template
 * ({@code WeCMS_Daily_Financial_Report_{EN|KO|VI}.xlsx}), re-uses
 * {@link ReportService#dailyFinancialHs}, writes the ledger into the template's
 * data area (row 5+) with the SUB TOTAL / TOTAL labels + numeric figures, and
 * streams it under the legacy filename — never throwing on an empty report.
 */
@ExtendWith(MockitoExtension.class)
class DailyFinancialHsDownloadServiceTest {

    @Mock ReportService reportService;

    private DailyFinancialHsDownloadService service() {
        return new DailyFinancialHsDownloadService(reportService);
    }

    /**
     * Multi-currency report — the function sorts CURRENCY_TOTAL rows
     * (account_type_order 999990/1/2) to the very end, so a VND + USD report
     * ends with TWO consecutive CURRENCY_TOTAL rows after the last account.
     * This is the exact shape that overlapped the Bank/Currency vertical merge
     * with a CURRENCY_TOTAL's A–F merge (POI "Cannot add merged region … it
     * overlaps" — the reported bug); the per-account merge bound must exclude
     * both trailing totals.
     */
    private DailyFinancialHsReport multiCurrencyReport() {
        List<LedgerRow> rows = List.of(
                // VND account (HEADER → BEFORE_BALANCE → TRANSACTION → ACCOUNT_TOTAL)
                row("HEADER", "Vietcom Bank\n(Current account)", "VND", null, null, null, null),
                row("BEFORE_BALANCE", "3711429999", "VND", "17000000000", null, null, null),
                txn("TRANSACTION", "VND", "RC001", "PC001", "5000000", null, "22000000000", "Customer A"),
                row("ACCOUNT_TOTAL", "SUB TOTAL", "VND", "17000000000", "5000000", "20000", "22000000000"),
                // USD account (no transactions → BEFORE_BALANCE → ACCOUNT_TOTAL)
                row("HEADER", "Shinhan(Vietnam)\n(Current account)", "USD", null, null, null, null),
                row("BEFORE_BALANCE", "703012794576", "USD", "1000.50", null, null, null),
                row("ACCOUNT_TOTAL", "SUB TOTAL", "USD", "1000.50", "0", "0", "1000.50"),
                // Trailing CURRENCY_TOTAL cluster (VND then USD) — sorted to the end.
                row("CURRENCY_TOTAL", "TOTAL", "VND", "17000000000", "5000000", "20000", "22000000000"),
                row("CURRENCY_TOTAL", "TOTAL", "USD", "1000.50", "0", "0", "1000.50")
        );
        return new DailyFinancialHsReport("20260615", "ALL", rows);
    }

    @Test
    void populatesTemplateWithDataAndTotals_enFilename() throws Exception {
        when(reportService.dailyFinancialHs("20260615", "ALL")).thenReturn(multiCurrencyReport());

        ReportsDownloadService.GeneratedReport report = service().generate("20260615", "ALL", "EN");

        assertThat(report.content()).isNotEmpty();
        assertThat(report.fileName()).isEqualTo("WeCMS_Daily_Financial_Report_EN.xlsx");

        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(report.content()))) {
            Sheet sheet = wb.getSheetAt(0);
            // Data written into the template's data area (row index 5+).
            assertThat(cellTextsFrom(sheet, 5)).contains("SUB TOTAL", "TOTAL", "RC001");
            // A numeric figure landed as a real number (re-summable in Excel).
            assertThat(hasNumericCell(sheet, 5_000_000d)).isTrue();
        }
    }

    @Test
    void koAndViLocalesUseLegacyFilenames() {
        when(reportService.dailyFinancialHs("20260615", "ALL")).thenReturn(multiCurrencyReport());

        assertThat(service().generate("20260615", "ALL", "ko").fileName())
                .isEqualTo("WeCMS_Daily_Financial_Report_KO.xlsx");
        assertThat(service().generate("20260615", "ALL", "vi").fileName())
                .isEqualTo("WeCMS_Daily_Financial_Report_VI.xlsx");
    }

    /**
     * Template-title contract (PO localization override, 2026-07-02): the
     * legacy templates were byte-identical with the Korean A1:L1 heading in
     * every language; the business then requested per-language titles, applied
     * INSIDE the refactor templates (shared string 0 only — sheet XML, styles,
     * merges untouched). Java must never write the title: the runtime
     * ({@code DailyFinancialHsDownloadService}, mirroring the legacy JSP)
     * writes only the period date — A2 (merged A2:L2, centred) + L3 (right
     * slot), KO as {@code yyyy년 M월 d일}, EN/VI as {@code M/d/yyyy}. This test
     * pins the contract per locale: template title intact, both date slots
     * populated, template merges intact.
     */
    @Test
    void templateTitleAndDateSlots_perLocale() throws Exception {
        when(reportService.dailyFinancialHs("20260615", "ALL")).thenReturn(multiCurrencyReport());

        assertTitleAndDate("EN", "Daily Financial Report", "6/15/2026");
        assertTitleAndDate("KO", "일 일 자 금 수 지 현 황", "2026년 6월 15일");
        assertTitleAndDate("VI", "Báo cáo tài chính hằng ngày", "6/15/2026");
    }

    private void assertTitleAndDate(String lang, String expectedTitle, String expectedDate) throws Exception {
        ReportsDownloadService.GeneratedReport report = service().generate("20260615", "ALL", lang);
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(report.content()))) {
            Sheet sheet = wb.getSheetAt(0);
            // Title stays the template's (never overwritten, never shifted).
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue())
                    .as("A1 title (%s)", lang)
                    .isEqualTo(expectedTitle);
            // Date slots: A2 (centred under the title) + L3 (right edge).
            assertThat(sheet.getRow(1).getCell(0).getStringCellValue())
                    .as("A2 date (%s)", lang)
                    .isEqualTo(expectedDate);
            assertThat(sheet.getRow(2).getCell(11).getStringCellValue())
                    .as("L3 date (%s)", lang)
                    .isEqualTo(expectedDate);
            // Template merges (title + date banner) must survive generation.
            List<String> merges = sheet.getMergedRegions().stream()
                    .map(org.apache.poi.ss.util.CellRangeAddress::formatAsString)
                    .toList();
            assertThat(merges).as("template merges (%s)", lang).contains("A1:L1", "A2:L2");
        }
    }

    @Test
    void emptyReportDoesNotThrow() {
        when(reportService.dailyFinancialHs("20260101", "ALL"))
                .thenReturn(new DailyFinancialHsReport("20260101", "ALL", List.of()));

        ReportsDownloadService.GeneratedReport report = service().generate("20260101", "ALL", "EN");
        assertThat(report.content()).isNotEmpty(); // template-only workbook
        assertThat(report.fileName()).isEqualTo("WeCMS_Daily_Financial_Report_EN.xlsx");
    }

    /* ------------------------------------------------------------------ */

    private static List<String> cellTextsFrom(Sheet sheet, int fromRow) {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (Row r : sheet) {
            if (r.getRowNum() < fromRow) continue;
            for (Cell c : r) {
                if (c.getCellType() == CellType.STRING) out.add(c.getStringCellValue());
            }
        }
        return out;
    }

    private static boolean hasNumericCell(Sheet sheet, double value) {
        for (Row r : sheet) {
            for (Cell c : r) {
                if (c.getCellType() == CellType.NUMERIC && c.getNumericCellValue() == value) return true;
            }
        }
        return false;
    }

    /** HEADER / BEFORE_BALANCE / ACCOUNT_TOTAL / CURRENCY_TOTAL row (no counterparty). */
    private static LedgerRow row(String type, String bankAccount, String ccy,
                                 String before, String receipt, String payment, String balance) {
        return new LedgerRow(type, bankAccount, ccy, null, null, null, null, null,
                before, receipt, payment, balance, balance,
                "3711429999", 1, "AC0001", "20260615", "090000", 1000, "", "11704020", bankAccount);
    }

    /** TRANSACTION row with editable codes + counterparty. */
    private static LedgerRow txn(String type, String ccy, String reptCd, String pymnCd,
                                 String receipt, String payment, String balance, String counterparty) {
        return new LedgerRow(type, "", ccy, reptCd, pymnCd, "wire in", "송금", counterparty,
                null, receipt, payment, balance, null,
                "3711429999", 1, "AC0002", "20260615", "091500", 1001, "memo", "11704020", "Vietcom Bank");
    }
}
