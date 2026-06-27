package com.kosign.wecms.domain.einvoice.export;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.util.List;

/**
 * Dedicated Apache POI writer for the "Download Excel with Details"
 * e-Invoice export — a faithful reproduction of the legacy
 * {@code einv_0001_001_r002_excel} 63-column layout (production file
 * {@code e_Invoice_EN.xlsx}).
 *
 * <p><b>Why a separate writer, not the shared {@link
 * com.kosign.wecms.common.export.ExcelWorkbookWriter}:</b> the legacy
 * details layout needs structures the shared writer cannot express and
 * which must not be forced onto the ~20 other export screens —
 * a two-row <em>grouped</em> header (merged Seller / Buyer / Items group
 * cells over per-field sub-labels), a title merged across {@code A1:U2},
 * separate inquiry-period and totals rows, an auto-filter, the
 * production sheet name {@code "Sales e-Invoice"}, and one row per invoice
 * line item with master values repeated. This class is e-Invoice-only.
 *
 * <p>The legacy generator loaded a pre-built {@code .xlsx} template via
 * {@code WorkbookFactory.create(...)} and filled data rows. Per the
 * project's "no Excel templates" rule, this writer constructs that same
 * static header (title, period, totals, grouped header, widths,
 * auto-filter) entirely in code with POI.
 *
 * <p>Layout (1-based Excel rows):
 * <pre>
 *   Row 1-2 : "SALES E-INVOICE" (or the supplied title), merged A1:U2, Arial 20 bold
 *   Row 3   : "Inquiry Period (...)"                     , merged A3:H3
 *   Row 4   : "Total (VND) N transaction(s)/ amount"     , merged A4:H4
 *   Row 5-6 : two-row grouped header (grey #A6A6A6, bordered, Arial 11)
 *   Row 7+  : data — one row per line item, master cols repeated, Arial 9, bordered
 *   Auto-filter: A6:BL{last}
 * </pre>
 *
 * <p>Each data row is an {@code Object[63]} keyed by column index (see
 * {@link EInvoiceDetailExportService} for the column contract). Cell type
 * is decided by the column descriptor: {@link #NUMERIC_COLS} render as
 * numeric ({@code #,##0;-#,##0}, right-aligned); {@link #RIGHT_TEXT_COLS}
 * render right-aligned text; everything else is left-aligned text. A
 * {@code null} cell renders empty (still bordered, matching production).
 */
@Component
public class EInvoiceDetailWorkbookWriter {

    /** 63 data columns (A–BK). The legacy loop is {@code for(i=0;i<63;i++)}. */
    public static final int COL_COUNT = 63;
    /** Auto-filter extends one column past the data (legacy A6:BL). */
    private static final int FILTER_LAST_COL = 63; // BL

    /* ---- Grouped-header structure (rows 5-6) ---- */

    private record Group(int start, int end, String label, String[] sub) { }

    private static final Group SELLER = new Group(6, 11, "Seller",
            new String[] {"Company name", "Tax ID", "Address", "Bank account", "Bank name", "Phone"});
    private static final Group BUYER = new Group(12, 17, "Buyer",
            new String[] {"Buyer name", "Company name", "Address", "Tax ID", "Bank account", "Bank name"});
    private static final Group ITEMS = new Group(23, 33, "Items",
            new String[] {"Item No.", "Nature", "Item code", "Item name", "UOM", "Quantity",
                    "Unit price", "Discount ratio", "Tax rate", "Supply amount", "Discount amount"});
    private static final Group[] GROUPS = {SELLER, BUYER, ITEMS};

    /** Single-column header labels (row 5, vertically merged across rows 5-6).
     *  {@code null} entries are covered by a {@link #GROUPS} entry instead. */
    private static final String[] SINGLE = new String[COL_COUNT];
    static {
        SINGLE[0]  = "Invoice Status";
        SINGLE[1]  = "Invoice Type";
        SINGLE[2]  = "Form No.";
        SINGLE[3]  = "Serial No.";
        SINGLE[4]  = "Invoice No.";
        SINGLE[5]  = "Invoice date";
        SINGLE[18] = "Attached list no";
        SINGLE[19] = "Attached list date";
        SINGLE[20] = "Currency";
        SINGLE[21] = "Payments";
        SINGLE[22] = "Exchange Rate";
        SINGLE[34] = "VAT rate";
        SINGLE[35] = "Total Amount by tax rate";
        SINGLE[36] = "VAT amount by tax rate";
        SINGLE[37] = "Total Amount (not include VAT)";
        SINGLE[38] = "Total VAT";
        SINGLE[39] = "Fee name";
        SINGLE[40] = "Fee amount";
        SINGLE[41] = "Total discount amount";
        SINGLE[42] = "Total amount in number";
        SINGLE[43] = "Amount in word";
        SINGLE[44] = "CQT code";
        SINGLE[45] = "Signing time in SELLER";
        SINGLE[46] = "Sign of Buyer";
        SINGLE[47] = "Signing time in CQT";
        SINGLE[48] = "Nature of invoice\n (1: replace, 2: adjust)";
        SINGLE[49] = "Related type invoice";
        SINGLE[50] = "Related Form No";
        SINGLE[51] = "Related Serial No";
        SINGLE[52] = "Related Invoice No";
        SINGLE[53] = "Related Invoice Date";
        SINGLE[54] = "Purpose/Account";
        SINGLE[55] = "Description";
        SINGLE[56] = "Project/site";
        SINGLE[57] = "Payment method";
        SINGLE[58] = "Payment term";
        SINGLE[59] = "Due date";
        SINGLE[60] = "Balance";
        SINGLE[61] = "Approval Ref.";
        SINGLE[62] = "Requestor";
    }

    /** Numeric columns — {@code #,##0;-#,##0}, right-aligned. */
    private static final boolean[] NUMERIC_COLS = new boolean[COL_COUNT];
    static {
        for (int c : new int[] {28, 29, 32, 33, 35, 36, 37, 38, 40, 41, 42}) NUMERIC_COLS[c] = true;
    }

    /** Text columns that are right-aligned (legacy {@code styleHoriRight}). */
    private static final boolean[] RIGHT_TEXT_COLS = new boolean[COL_COUNT];
    static {
        for (int c : new int[] {2, 4, 5, 7, 15, 22, 23, 30, 31, 34, 48, 49, 50, 52}) RIGHT_TEXT_COLS[c] = true;
    }

    /** Column widths in Excel char units (production {@code wch}), A–BL. */
    private static final double[] WIDTHS = {
            26.5, 23.8, 11.9, 12.5, 17.2, 18.6, 25.4, 16.9, 12.8, 13.8, 15.1, 15.5, 12.9, 32.2,
            15.4, 11.9, 12.8, 11.2, 15.5, 19.8, 15.5, 16.5, 15.9, 9.1, 19.4, 10.6, 18.1, 8.5, 8.5,
            10.1, 14.8, 8.9, 15.9, 18.1, 9.4, 25.5, 24.5, 26.1, 16.8, 15.2, 19.5, 23.5, 27.1, 22.1,
            17.6, 25.4, 24.9, 20.8, 27.4, 34.6, 24.5, 18.5, 19.8, 22.9, 22.4, 19.6, 20.6, 20.6, 18.6,
            16.1, 20.4, 27.1, 28.2, 27.9
    };

    private static final byte[] HEADER_GREY = {(byte) 0xA6, (byte) 0xA6, (byte) 0xA6};

    /**
     * Stream the details workbook.
     *
     * @param title  in-sheet row-1 title (merged A1:U2)
     * @param period inquiry-period line (merged A3:H3); empty → blank cell
     * @param totals totals line (merged A4:H4); empty → blank cell
     * @param rows   data rows, each {@code Object[63]} indexed by column
     */
    public void write(OutputStream out, String title, String period, String totals,
                      List<Object[]> rows) throws IOException {
        try (SXSSFWorkbook wb = new SXSSFWorkbook(100)) {
            wb.setCompressTempFiles(true);
            SXSSFSheet sheet = wb.createSheet("Sales e-Invoice");

            Styles st = buildStyles(wb);

            // Column widths (A–BL).
            for (int c = 0; c <= FILTER_LAST_COL && c < WIDTHS.length; c++) {
                sheet.setColumnWidth(c, (int) Math.round(WIDTHS[c] * 256));
            }

            // Row 1-2: title merged A1:U2.
            Row titleRow = sheet.createRow(0);
            sheet.createRow(1);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue(title == null ? "" : title);
            titleCell.setCellStyle(st.title);
            sheet.addMergedRegion(new CellRangeAddress(0, 1, 0, 20)); // A1:U2

            // Row 3: inquiry period, merged A3:H3.
            Cell periodCell = sheet.createRow(2).createCell(0);
            periodCell.setCellValue(period == null ? "" : period);
            periodCell.setCellStyle(st.plain);
            sheet.addMergedRegion(new CellRangeAddress(2, 2, 0, 7));

            // Row 4: totals, merged A4:H4.
            Cell totalCell = sheet.createRow(3).createCell(0);
            totalCell.setCellValue(totals == null ? "" : totals);
            totalCell.setCellStyle(st.plain);
            sheet.addMergedRegion(new CellRangeAddress(3, 3, 0, 7));

            // Rows 5-6: grouped header.
            writeHeader(sheet, st);

            // Rows 7+: data.
            int rowIdx = 6;
            if (rows != null) {
                for (Object[] data : rows) {
                    Row row = sheet.createRow(rowIdx++);
                    for (int c = 0; c < COL_COUNT; c++) {
                        Cell cell = row.createCell(c);
                        Object v = (data != null && c < data.length) ? data[c] : null;
                        writeDataCell(cell, v, c, st);
                    }
                }
            }

            // Auto-filter A6:BL{last} — anchored on the row-6 sub-header
            // (row index 5), matching legacy A6:BL516.
            int lastRow = Math.max(5, rowIdx - 1);
            sheet.setAutoFilter(new CellRangeAddress(5, lastRow, 0, FILTER_LAST_COL));

            wb.write(out);
            out.flush();
            wb.dispose();
        }
    }

    private void writeHeader(SXSSFSheet sheet, Styles st) {
        Row r5 = sheet.createRow(4);
        Row r6 = sheet.createRow(5);
        // Pre-create every header cell so empty group-spanned cells in r6
        // still carry the grey bordered style.
        for (int c = 0; c < COL_COUNT; c++) {
            r5.createCell(c).setCellStyle(st.header);
            r6.createCell(c).setCellStyle(st.header);
        }
        // Single columns: label in row 5, vertically merged across 5-6.
        for (int c = 0; c < COL_COUNT; c++) {
            if (SINGLE[c] != null) {
                r5.getCell(c).setCellValue(SINGLE[c]);
                sheet.addMergedRegion(new CellRangeAddress(4, 5, c, c));
            }
        }
        // Groups: label horizontally merged on row 5, sub-labels on row 6.
        for (Group g : GROUPS) {
            r5.getCell(g.start()).setCellValue(g.label());
            sheet.addMergedRegion(new CellRangeAddress(4, 4, g.start(), g.end()));
            for (int i = 0; i < g.sub().length; i++) {
                r6.getCell(g.start() + i).setCellValue(g.sub()[i]);
            }
        }
    }

    private void writeDataCell(Cell cell, Object v, int col, Styles st) {
        if (NUMERIC_COLS[col]) {
            cell.setCellStyle(st.number);
            BigDecimal n = toDecimal(v);
            if (n != null) {
                cell.setCellValue(n.doubleValue());
            } else if (v != null && !v.toString().isBlank()) {
                // Unparseable but present — degrade to text (never throw).
                cell.setCellValue(v.toString());
            }
            return;
        }
        cell.setCellStyle(RIGHT_TEXT_COLS[col] ? st.textRight : st.textLeft);
        if (v != null) {
            String s = v.toString();
            if (!s.isEmpty()) cell.setCellValue(s);
        }
    }

    private static BigDecimal toDecimal(Object v) {
        if (v == null) return null;
        if (v instanceof BigDecimal bd) return bd;
        if (v instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        String s = v.toString().trim();
        if (s.isEmpty()) return null;
        try {
            return new BigDecimal(s.replace(",", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /* ---------------- Styles ---------------- */

    private record Styles(XSSFCellStyle title, XSSFCellStyle plain, XSSFCellStyle header,
                          XSSFCellStyle textLeft, XSSFCellStyle textRight, XSSFCellStyle number) { }

    private Styles buildStyles(SXSSFWorkbook wb) {
        Font titleFont = wb.createFont();
        titleFont.setBold(true);
        titleFont.setFontHeightInPoints((short) 20);
        titleFont.setFontName("Arial");
        XSSFCellStyle title = (XSSFCellStyle) wb.createCellStyle();
        title.setFont(titleFont);
        title.setAlignment(HorizontalAlignment.CENTER);
        title.setVerticalAlignment(VerticalAlignment.CENTER);

        Font plainFont = wb.createFont();
        plainFont.setFontHeightInPoints((short) 11);
        plainFont.setFontName("Arial");
        XSSFCellStyle plain = (XSSFCellStyle) wb.createCellStyle();
        plain.setFont(plainFont);
        plain.setAlignment(HorizontalAlignment.LEFT);
        plain.setVerticalAlignment(VerticalAlignment.CENTER);

        Font headerFont = wb.createFont();
        headerFont.setFontHeightInPoints((short) 11);
        headerFont.setFontName("Arial");
        XSSFCellStyle header = (XSSFCellStyle) wb.createCellStyle();
        header.setFont(headerFont);
        header.setAlignment(HorizontalAlignment.CENTER);
        header.setVerticalAlignment(VerticalAlignment.CENTER);
        header.setWrapText(true);
        header.setFillForegroundColor(new XSSFColor(HEADER_GREY, null));
        header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        applyThinBorder(header);

        Font dataFont = wb.createFont();
        dataFont.setFontHeightInPoints((short) 9);
        dataFont.setFontName("Arial");

        XSSFCellStyle textLeft = (XSSFCellStyle) wb.createCellStyle();
        textLeft.setFont(dataFont);
        textLeft.setAlignment(HorizontalAlignment.LEFT);
        textLeft.setVerticalAlignment(VerticalAlignment.CENTER);
        applyThinBorder(textLeft);

        XSSFCellStyle textRight = (XSSFCellStyle) wb.createCellStyle();
        textRight.setFont(dataFont);
        textRight.setAlignment(HorizontalAlignment.RIGHT);
        textRight.setVerticalAlignment(VerticalAlignment.CENTER);
        applyThinBorder(textRight);

        XSSFCellStyle number = (XSSFCellStyle) wb.createCellStyle();
        number.setFont(dataFont);
        number.setAlignment(HorizontalAlignment.RIGHT);
        number.setVerticalAlignment(VerticalAlignment.CENTER);
        number.setDataFormat(wb.createDataFormat().getFormat("#,##0;-#,##0"));
        applyThinBorder(number);

        return new Styles(title, plain, header, textLeft, textRight, number);
    }

    private static void applyThinBorder(XSSFCellStyle s) {
        s.setBorderTop(BorderStyle.THIN);
        s.setBorderBottom(BorderStyle.THIN);
        s.setBorderLeft(BorderStyle.THIN);
        s.setBorderRight(BorderStyle.THIN);
    }
}
