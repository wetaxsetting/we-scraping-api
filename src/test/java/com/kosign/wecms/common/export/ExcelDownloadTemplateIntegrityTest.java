package com.kosign.wecms.common.export;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression guard for the 2026-07-01 "unused pre-styled bordered blank rows"
 * cleanup (see {@code docs/audits/excel-template-unused-bordered-rows-audit.md}).
 *
 * <p>The affected download services
 * ({@code CurrentSavingsDownloadService}, {@code InternalTransferDownloadService},
 * {@code ExpensePaymentDownloadService}, {@code VendorPaymentDownloadService},
 * {@code PayrollPaymentDownloadService}, {@code OverseasPaymentDownloadService})
 * populate the pre-styled template rows starting at their first-data-row index
 * and <b>do not</b> trim leftover rows at runtime. Any extra pre-styled bordered
 * rows shipped in the template therefore leak into the generated file as empty
 * bordered rows below the real data (the reported Current &amp; Savings FCY
 * Account Inquiry symptom).
 *
 * <p>The fix keeps exactly one styled data row (needed for POI per-column style
 * cloning) at the first-data-row index and removes every row below it. This test
 * asserts, for each cleaned template, that:
 * <ul>
 *   <li>the workbook opens;</li>
 *   <li>the title/section merged regions are preserved;</li>
 *   <li>the header row (first-data-row − 1) keeps its borders;</li>
 *   <li>the single style-template data row is present and bordered;</li>
 *   <li>{@code lastRowNum == firstDataRow} — i.e. NO trailing bordered blank rows.</li>
 * </ul>
 *
 * <p>Templates whose services already trim leftovers at runtime
 * ({@code Approval Box}, {@code Contacts}) or that are fixed-layout financial
 * reports populated by cell reference ({@code reports/daily-cash},
 * {@code reports/daily-financial}) are intentionally out of scope here.
 */
class ExcelDownloadTemplateIntegrityTest {

    /** path, firstDataRowIndex, columnCount. */
    static Stream<Arguments> cleanedTemplates() {
        return Stream.of(
                // Current & Savings — FCY / VND, Account Inquiry (idx 4) + Transaction History (idx 4)
                tpl("templates/current-savings-fcy/account-inquiry/account-inquiry_", ".xls", 4, 6),
                tpl("templates/current-savings-fcy/transaction-history/transaction-history_", ".xls", 4, 10),
                tpl("templates/current-savings-vnd/account-inquiry/account-inquiry_", ".xls", 4, 9),
                tpl("templates/current-savings-vnd/transaction-history/transaction-history_", ".xls", 4, 10),
                // Payment downloads (idx 3), Overseas (idx 4)
                tpl("templates/internal-transfer/WeCMS_Internal_Transfer_download_", ".xlsx", 3, 21),
                tpl("templates/expense-payment/WeCMS_Expense_Payment_download_", ".xlsx", 3, 22),
                tpl("templates/vendor-payment/WeCMS_Vendor_Payment_download_", ".xlsx", 3, 22),
                tpl("templates/payroll-payment/WeCMS_Payroll_download_", ".xlsx", 3, 20),
                tpl("templates/overseas-payment/WeCMS_Overseas_Payment_download_", ".xlsx", 4, 22)
        ).flatMap(a -> a);
    }

    private static Stream<Arguments> tpl(String prefix, String ext, int firstDataRow, int cols) {
        return Stream.of("EN", "KO", "VI")
                .map(lang -> Arguments.of(prefix + lang + ext, firstDataRow, cols));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cleanedTemplates")
    void template_ends_at_single_styled_data_row_with_no_trailing_bordered_rows(
            String resourcePath, int firstDataRow, int columnCount) throws Exception {
        ClassPathResource res = new ClassPathResource(resourcePath);
        assertThat(res.exists()).as("template on classpath: %s", resourcePath).isTrue();

        try (InputStream in = res.getInputStream();
             Workbook wb = WorkbookFactory.create(in)) {
            Sheet sheet = wb.getSheetAt(0);

            // (1) Title / section merges preserved.
            assertThat(sheet.getMergedRegions())
                    .as("merged regions preserved in %s", resourcePath)
                    .isNotEmpty();

            // (2) Header row (just above the data area) keeps borders.
            Row header = sheet.getRow(firstDataRow - 1);
            assertThat(header).as("header row present in %s", resourcePath).isNotNull();
            assertThat(borderedCellCount(header, columnCount))
                    .as("header row keeps borders in %s", resourcePath)
                    .isGreaterThan(0);

            // (3) The single style-template data row is present and bordered
            //     (services clone its per-column style onto every written row).
            Row styleRow = sheet.getRow(firstDataRow);
            assertThat(styleRow).as("style-template data row present in %s", resourcePath).isNotNull();
            assertThat(borderedCellCount(styleRow, columnCount))
                    .as("style-template data row keeps borders in %s", resourcePath)
                    .isGreaterThan(0);

            // (4) NO trailing bordered blank rows: the sheet ends at the style row.
            assertThat(sheet.getLastRowNum())
                    .as("no rows below the single style-template data row in %s", resourcePath)
                    .isEqualTo(firstDataRow);
        }
    }

    private static int borderedCellCount(Row row, int columnCount) {
        int n = 0;
        for (int c = 0; c < columnCount; c++) {
            Cell cell = row.getCell(c);
            if (cell == null) continue;
            CellStyle s = cell.getCellStyle();
            if (s.getBorderTop() != BorderStyle.NONE || s.getBorderBottom() != BorderStyle.NONE
                    || s.getBorderLeft() != BorderStyle.NONE || s.getBorderRight() != BorderStyle.NONE) {
                n++;
            }
        }
        return n;
    }

    /** Sanity: the classpath list above is complete (27 = 9 templates × 3 locales). */
    @org.junit.jupiter.api.Test
    void covers_all_cleaned_templates() {
        List<Arguments> all = cleanedTemplates().toList();
        assertThat(all).hasSize(27);
    }
}
