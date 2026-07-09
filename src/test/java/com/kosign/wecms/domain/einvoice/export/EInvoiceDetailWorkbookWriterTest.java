package com.kosign.wecms.domain.einvoice.export;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Round-trip structural assertions for {@link EInvoiceDetailWorkbookWriter}:
 * write a small workbook in memory, re-read it with POI, and verify the
 * legacy 63-column layout — sheet name, title/period/totals rows, the
 * two-row grouped header, merges, auto-filter, and the numeric cell format.
 *
 * <p>The refactor DB has no e-Invoice seed locally, so the export is
 * verified against constructed rows rather than live data.
 */
class EInvoiceDetailWorkbookWriterTest {

    private final EInvoiceDetailWorkbookWriter writer = new EInvoiceDetailWorkbookWriter();

    private Workbook writeAndRead(List<Object[]> rows) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writer.write(out, "SALES E-INVOICE",
                "Inquiry Period (28-05-2026 ~ 27-06-2026)",
                "Total (VND) 1 transaction(s)/ 1.260.000₫",
                rows);
        return new XSSFWorkbook(new ByteArrayInputStream(out.toByteArray()));
    }

    private static Object[] sampleLineRow() {
        Object[] r = new Object[EInvoiceDetailWorkbookWriter.COL_COUNT];
        r[0] = "Original";
        r[2] = "1";
        r[3] = "C26TWV";
        r[4] = "230";
        r[5] = "2026-06-25";
        r[6] = "CONG TY A";
        r[7] = "1201496252";
        r[20] = "VND";
        r[23] = 1;                                   // item no
        r[28] = new BigDecimal("1");                 // quantity (numeric)
        r[29] = new BigDecimal("1260000");           // unit price (numeric)
        r[32] = new BigDecimal("1260000");           // supply amount (numeric)
        r[42] = new BigDecimal("1260000");           // total amount in number (numeric)
        // col 57/60/62 deliberately left null (blank in production).
        return r;
    }

    @Test
    void sheetName_isSalesEInvoice() throws Exception {
        try (Workbook wb = writeAndRead(List.<Object[]>of(sampleLineRow()))) {
            assertThat(wb.getSheetAt(0).getSheetName()).isEqualTo("Sales e-Invoice");
        }
    }

    @Test
    void titlePeriodTotals_rowsArePopulated() throws Exception {
        try (Workbook wb = writeAndRead(List.<Object[]>of(sampleLineRow()))) {
            Sheet s = wb.getSheetAt(0);
            assertThat(s.getRow(0).getCell(0).getStringCellValue()).isEqualTo("SALES E-INVOICE");
            assertThat(s.getRow(2).getCell(0).getStringCellValue())
                    .isEqualTo("Inquiry Period (28-05-2026 ~ 27-06-2026)");
            assertThat(s.getRow(3).getCell(0).getStringCellValue())
                    .startsWith("Total (VND) 1 transaction(s)/");
        }
    }

    @Test
    void groupedHeader_row5AndRow6_labelsMatchProduction() throws Exception {
        try (Workbook wb = writeAndRead(List.<Object[]>of(sampleLineRow()))) {
            Sheet s = wb.getSheetAt(0);
            Row r5 = s.getRow(4); // Excel row 5 — group + single headers
            Row r6 = s.getRow(5); // Excel row 6 — group sub-labels

            assertThat(r5.getCell(0).getStringCellValue()).isEqualTo("Invoice Status");
            assertThat(r5.getCell(2).getStringCellValue()).isEqualTo("Form No.");
            assertThat(r5.getCell(4).getStringCellValue()).isEqualTo("Invoice No.");
            assertThat(r5.getCell(6).getStringCellValue()).isEqualTo("Seller");
            assertThat(r5.getCell(12).getStringCellValue()).isEqualTo("Buyer");
            assertThat(r5.getCell(23).getStringCellValue()).isEqualTo("Items");
            assertThat(r5.getCell(44).getStringCellValue()).isEqualTo("CQT code");
            assertThat(r5.getCell(62).getStringCellValue()).isEqualTo("Requestor");

            assertThat(r6.getCell(6).getStringCellValue()).isEqualTo("Company name");
            assertThat(r6.getCell(11).getStringCellValue()).isEqualTo("Phone");
            assertThat(r6.getCell(12).getStringCellValue()).isEqualTo("Buyer name");
            assertThat(r6.getCell(23).getStringCellValue()).isEqualTo("Item No.");
            assertThat(r6.getCell(28).getStringCellValue()).isEqualTo("Quantity");
            assertThat(r6.getCell(33).getStringCellValue()).isEqualTo("Discount amount");
        }
    }

    @Test
    void merges_titleAndGroupsAndVerticalSingles() throws Exception {
        try (Workbook wb = writeAndRead(List.<Object[]>of(sampleLineRow()))) {
            List<CellRangeAddress> merges = wb.getSheetAt(0).getMergedRegions();
            assertThat(merges).contains(
                    new CellRangeAddress(0, 1, 0, 20),  // A1:U2 title
                    new CellRangeAddress(2, 2, 0, 7),   // A3:H3 period
                    new CellRangeAddress(3, 3, 0, 7),   // A4:H4 totals
                    new CellRangeAddress(4, 4, 6, 11),  // G5:L5 Seller group
                    new CellRangeAddress(4, 4, 12, 17), // M5:R5 Buyer group
                    new CellRangeAddress(4, 4, 23, 33), // X5:AH5 Items group
                    new CellRangeAddress(4, 5, 0, 0));  // A5:A6 vertical single
        }
    }

    @Test
    void autoFilter_isSet() throws Exception {
        try (Workbook wb = writeAndRead(List.<Object[]>of(sampleLineRow()))) {
            XSSFSheet s = (XSSFSheet) wb.getSheetAt(0);
            assertThat(s.getCTWorksheet().isSetAutoFilter()).isTrue();
        }
    }

    @Test
    void numericCell_isNumericWithZeroDecimalFormat() throws Exception {
        try (Workbook wb = writeAndRead(List.<Object[]>of(sampleLineRow()))) {
            Cell qty = wb.getSheetAt(0).getRow(6).getCell(28); // first data row, Quantity
            assertThat(qty.getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(qty.getNumericCellValue()).isEqualTo(1.0d);
            assertThat(qty.getCellStyle().getDataFormatString()).contains("#,##0");
        }
    }

    @Test
    void blankProductionColumns_areEmpty() throws Exception {
        try (Workbook wb = writeAndRead(List.<Object[]>of(sampleLineRow()))) {
            Row data = wb.getSheetAt(0).getRow(6);
            // 57 Payment method, 60 Balance, 62 Requestor — blank in production.
            for (int c : new int[] {57, 60, 62}) {
                Cell cell = data.getCell(c);
                String v = cell == null ? "" : (cell.getCellType() == CellType.STRING ? cell.getStringCellValue() : "");
                assertThat(v).isEmpty();
            }
        }
    }

    @Test
    void dataRowCount_matchesInput() throws Exception {
        try (Workbook wb = writeAndRead(List.<Object[]>of(sampleLineRow(), sampleLineRow()))) {
            Sheet s = wb.getSheetAt(0);
            // rows 0..5 are title/period/totals/header; data starts at row index 6.
            assertThat(s.getLastRowNum()).isEqualTo(7); // 6 + 2 data rows - 1
        }
    }
}
