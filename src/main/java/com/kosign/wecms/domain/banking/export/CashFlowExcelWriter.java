package com.kosign.wecms.domain.banking.export;

import com.kosign.wecms.domain.banking.dto.CashFlowReportResponse;
import com.kosign.wecms.domain.banking.dto.CashFlowReportResponse.CashFlowRow;
import com.kosign.wecms.domain.banking.dto.CashFlowReportResponse.MonthlyAmounts;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Dedicated Apache POI workbook writer for the Cash Flow report.
 *
 * <p>The generic {@code ExcelExportService} pipeline emits flat
 * row-per-record output and can't produce the legacy grouped layout
 * (merged L1/L2 cells with rowSpan, colored subtotal bands, two-row
 * header with merged "Opening Balance" cell). This writer mirrors the
 * legacy {@code cash_0001_001} XLSX output exactly:
 *
 * <pre>
 *   Row 1   — "Cash Flow" title (merged across all columns, centered)
 *   Row 2   — "Year: YYYY - Currency: CCY - Exchange Rate: NNN.NNN" (merged, centered)
 *   Row 3-4 — Two-row header:
 *               Row 3: [Opening Balance (merged A-C, rows 3-4)] [        ] [        ] Jan Feb ... Dec
 *               Row 4: (covered by merge)                       Item code  Item name  (covered by Jan-row merge)
 *   Row 5+  — Body: L1 cell merged across all its L2/L3 child rows,
 *             L2 cell merged across its L3 children, L3 rows show
 *             KO name + item code + item name + 12 month amounts.
 *             S.TOTAL subtotal bands tinted blue;
 *             SALES PROFIT / NON SALES PROFIT bands tinted green;
 *             END BALANCE band tinted solid green with white text.
 * </pre>
 */
@Slf4j
@Component
public class CashFlowExcelWriter {

    private static final String[] MONTH_HEADERS = {
            "Jan", "Feb", "Mar", "Apr", "May", "Jun",
            "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
    };

    private static final int COL_OUTER = 0;
    private static final int COL_MID = 1;
    private static final int COL_KO = 2;
    private static final int COL_ITEM_CD = 3;
    private static final int COL_ITEM_NM = 4;
    private static final int COL_MONTH_START = 5;
    private static final int TOTAL_COLS = COL_MONTH_START + 12;

    public void write(HttpServletResponse response,
                      CashFlowReportResponse report,
                      String fileName) {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Cash Flow");

            // Column widths (Excel units = 1/256 of a character)
            sheet.setColumnWidth(COL_OUTER, 18 * 256);
            sheet.setColumnWidth(COL_MID, 22 * 256);
            sheet.setColumnWidth(COL_KO, 24 * 256);
            sheet.setColumnWidth(COL_ITEM_CD, 12 * 256);
            sheet.setColumnWidth(COL_ITEM_NM, 22 * 256);
            for (int m = 0; m < 12; m++) {
                sheet.setColumnWidth(COL_MONTH_START + m, 14 * 256);
            }

            Styles styles = new Styles(wb);
            int row = 0;

            // Row 1 — Title "Cash Flow"
            Row titleRow = sheet.createRow(row);
            titleRow.setHeightInPoints(22f);
            XSSFCell titleCell = (XSSFCell) titleRow.createCell(0);
            titleCell.setCellValue("Cash Flow");
            titleCell.setCellStyle(styles.title);
            sheet.addMergedRegion(new CellRangeAddress(row, row, 0, TOTAL_COLS - 1));
            row++;

            // Row 2 — Subtitle "Year: YYYY - Currency: CCY - Exchange Rate: ..."
            Row subRow = sheet.createRow(row);
            subRow.setHeightInPoints(16f);
            XSSFCell subCell = (XSSFCell) subRow.createCell(0);
            subCell.setCellValue(buildSubtitle(report));
            subCell.setCellStyle(styles.subtitle);
            sheet.addMergedRegion(new CellRangeAddress(row, row, 0, TOTAL_COLS - 1));
            row++;

            // Rows 3-4 — Two-row header.
            Row hdr1 = sheet.createRow(row);
            Row hdr2 = sheet.createRow(row + 1);
            hdr1.setHeightInPoints(18f);
            hdr2.setHeightInPoints(18f);

            // "Opening Balance" merged across cols A-C (outer + mid + ko), rows 3-4
            setHeaderCell(hdr1, COL_OUTER, "Opening Balance", styles.headerCenter);
            sheet.addMergedRegion(new CellRangeAddress(row, row + 1, COL_OUTER, COL_KO));
            // Header row 2 fillers for the merged area
            fillMerged(hdr2, COL_OUTER, COL_KO, styles.headerCenter);

            // "Item code" / "Item name" in row 4 only — empty cells in row 3 above them
            setHeaderCell(hdr1, COL_ITEM_CD, "", styles.headerCenter);
            setHeaderCell(hdr1, COL_ITEM_NM, "", styles.headerCenter);
            setHeaderCell(hdr2, COL_ITEM_CD, "Item code", styles.headerCenter);
            setHeaderCell(hdr2, COL_ITEM_NM, "Item name", styles.headerCenter);

            // Month headers in row 3, row 4 below stays empty (or shows opening balance values)
            for (int m = 0; m < 12; m++) {
                setHeaderCell(hdr1, COL_MONTH_START + m, MONTH_HEADERS[m], styles.headerCenter);
                setHeaderCell(hdr2, COL_MONTH_START + m, "", styles.headerCenter);
            }
            row += 2;

            // Body — walk grouped rows
            Grouped grouped = groupRows(report.rows());
            row = writeBody(sheet, row, grouped, styles, report.baseCurrency());

            stream(response, wb, fileName);
        } catch (IOException e) {
            log.error("CashFlow Excel export failed", e);
            throw new RuntimeException("Cash Flow Excel export failed", e);
        }
    }

    /* =========================== Body =========================== */

    private int writeBody(Sheet sheet, int startRow, Grouped g, Styles styles, String baseCcy) {
        int row = startRow;
        boolean salesProfitEmitted = false;
        boolean nonSalesProfitEmitted = false;
        boolean withinAcc03 = false;

        for (Group group : g.groups) {
            boolean isAcc03 = "ACC-03".equals(group.l1.displayCd());

            if (isAcc03 && !salesProfitEmitted && g.salesProfit != null) {
                row = writeProfitBand(sheet, row, g.salesProfit, styles);
                salesProfitEmitted = true;
                withinAcc03 = true;
            }
            if (withinAcc03 && !isAcc03 && !nonSalesProfitEmitted && g.nonSalesProfit != null) {
                row = writeProfitBand(sheet, row, g.nonSalesProfit, styles);
                nonSalesProfitEmitted = true;
                withinAcc03 = false;
            }

            row = writeGroup(sheet, row, group, isAcc03, styles, baseCcy);
        }

        // Trailing NON SALES PROFIT when ACC-03 is the last group
        if (withinAcc03 && !nonSalesProfitEmitted && g.nonSalesProfit != null) {
            row = writeProfitBand(sheet, row, g.nonSalesProfit, styles);
        }

        // End Balance
        if (g.endBalance != null) {
            row = writeEndBalance(sheet, row, g.endBalance, styles);
        }

        return row;
    }

    private int writeGroup(Sheet sheet, int startRow, Group group, boolean suppressSubtotal,
                            Styles styles, String baseCcy) {
        int row = startRow;
        int l1StartRow = row;

        // Empty group: emit one row so the L1 cell has something to wrap.
        if (group.children.isEmpty()) {
            Row r = sheet.createRow(row);
            setBodyCell(r, COL_OUTER, displayName(group.l1), styles.l1);
            for (int i = COL_MID; i < TOTAL_COLS; i++) setBodyCell(r, i, "", styles.body);
            row++;
            if (!suppressSubtotal && group.subtotal != null) {
                row = writeSubtotal(sheet, row, group.subtotal, styles, baseCcy);
            }
            return row;
        }

        for (L2Group l2g : group.children) {
            int l2StartRow = row;
            List<CashFlowRow> l3s = l2g.l3s.isEmpty()
                    ? List.of(emptyL3Placeholder())
                    : l2g.l3s;

            for (CashFlowRow l3 : l3s) {
                Row r = sheet.createRow(row);
                // outer + mid placeholders (will be overwritten by merge value below)
                setBodyCell(r, COL_OUTER, "", styles.l1);
                setBodyCell(r, COL_MID, "", styles.l2);
                setBodyCell(r, COL_KO, nullSafe(l3.displayNm()), styles.body);
                setBodyCell(r, COL_ITEM_CD, nullSafe(l3.displayCd()), styles.body);
                setBodyCell(r, COL_ITEM_NM, nullSafe(l3.itemNm()), styles.body);
                writeMonthCells(r, l3.monthly(), styles.amount);
                row++;
            }

            // Merge L2 cell across its L3 rows
            int l2EndRow = row - 1;
            if (l2EndRow > l2StartRow) {
                sheet.addMergedRegion(new CellRangeAddress(l2StartRow, l2EndRow, COL_MID, COL_MID));
            }
            // Populate the L2 cell at l2StartRow with the label (the merge picks
            // up the top-left cell's value).
            Row l2Row = sheet.getRow(l2StartRow);
            l2Row.getCell(COL_MID).setCellValue(displayName(l2g.l2));
        }

        // Merge L1 cell across all the rows this group emitted (L3 rows only;
        // subtotal stays outside the L1 merge so the legacy `cash_0001_001`
        // visual is preserved).
        int l1EndRow = row - 1;
        if (l1EndRow > l1StartRow) {
            sheet.addMergedRegion(new CellRangeAddress(l1StartRow, l1EndRow, COL_OUTER, COL_OUTER));
        }
        Row l1Row = sheet.getRow(l1StartRow);
        l1Row.getCell(COL_OUTER).setCellValue(displayName(group.l1));

        if (!suppressSubtotal && group.subtotal != null) {
            row = writeSubtotal(sheet, row, group.subtotal, styles, baseCcy);
        }
        return row;
    }

    private int writeSubtotal(Sheet sheet, int rowIdx, CashFlowRow sub, Styles styles, String baseCcy) {
        Row r = sheet.createRow(rowIdx);
        r.setHeightInPoints(18f);
        // Col A stays blank (continues the L1 visual flow); the band spans B-E
        setBodyCell(r, COL_OUTER, "", styles.subtotalBlank);
        setBodyCell(r, COL_MID, displayName(sub), styles.subtotalLabel);
        sheet.addMergedRegion(new CellRangeAddress(rowIdx, rowIdx, COL_MID, COL_ITEM_NM));
        // Fillers for the merged region (so styles apply uniformly)
        for (int c = COL_MID + 1; c <= COL_ITEM_NM; c++) {
            setBodyCell(r, c, "", styles.subtotalLabel);
        }
        writeMonthCells(r, sub.monthly(), styles.subtotalAmount);
        return rowIdx + 1;
    }

    private int writeProfitBand(Sheet sheet, int rowIdx, CashFlowRow profit, Styles styles) {
        Row r = sheet.createRow(rowIdx);
        r.setHeightInPoints(18f);
        // The band spans the full 5-col sticky-left area; col A is included
        // (unlike the subtotal band) so the green band reads as a single
        // full-width row, matching legacy.
        setBodyCell(r, COL_OUTER, displayName(profit), styles.profitLabel);
        sheet.addMergedRegion(new CellRangeAddress(rowIdx, rowIdx, COL_OUTER, COL_ITEM_NM));
        for (int c = COL_OUTER + 1; c <= COL_ITEM_NM; c++) {
            setBodyCell(r, c, "", styles.profitLabel);
        }
        writeMonthCells(r, profit.monthly(), styles.profitAmount);
        return rowIdx + 1;
    }

    private int writeEndBalance(Sheet sheet, int rowIdx, CashFlowRow eb, Styles styles) {
        Row r = sheet.createRow(rowIdx);
        r.setHeightInPoints(20f);
        setBodyCell(r, COL_OUTER, "End Balance", styles.endBalanceLabel);
        sheet.addMergedRegion(new CellRangeAddress(rowIdx, rowIdx, COL_OUTER, COL_ITEM_NM));
        for (int c = COL_OUTER + 1; c <= COL_ITEM_NM; c++) {
            setBodyCell(r, c, "", styles.endBalanceLabel);
        }
        writeMonthCells(r, eb.monthly(), styles.endBalanceAmount);
        return rowIdx + 1;
    }

    private void writeMonthCells(Row r, MonthlyAmounts m, CellStyle style) {
        BigDecimal[] vals = monthArray(m);
        for (int i = 0; i < 12; i++) {
            XSSFCell c = (XSSFCell) r.createCell(COL_MONTH_START + i, CellType.NUMERIC);
            BigDecimal v = vals[i];
            if (v != null && v.signum() != 0) {
                c.setCellValue(v.doubleValue());
            }
            c.setCellStyle(style);
        }
    }

    private static BigDecimal[] monthArray(MonthlyAmounts m) {
        if (m == null) return new BigDecimal[12];
        return new BigDecimal[] {
                m.jan(), m.feb(), m.mar(), m.apr(), m.may(), m.jun(),
                m.jul(), m.aug(), m.sep(), m.oct(), m.nov(), m.dec()
        };
    }

    /* =========================== Helpers =========================== */

    private static void setHeaderCell(Row row, int col, String value, CellStyle style) {
        XSSFCell c = (XSSFCell) row.createCell(col, CellType.STRING);
        c.setCellValue(value);
        c.setCellStyle(style);
    }

    private static void fillMerged(Row row, int colStart, int colEnd, CellStyle style) {
        for (int c = colStart; c <= colEnd; c++) {
            setHeaderCell(row, c, "", style);
        }
    }

    private static void setBodyCell(Row row, int col, String value, CellStyle style) {
        XSSFCell c = (XSSFCell) row.createCell(col, CellType.STRING);
        c.setCellValue(value);
        c.setCellStyle(style);
    }

    private static String displayName(CashFlowRow r) {
        if (r == null) return "";
        String n = r.displayNm();
        if (n != null && !n.isBlank()) return n;
        return nullSafe(r.displayCd());
    }

    private static String nullSafe(String s) { return s == null ? "" : s; }

    private static CashFlowRow emptyL3Placeholder() {
        return new CashFlowRow("", "", "", "", "", "Level 3", "", "", "",
                MonthlyAmounts.zero(), BigDecimal.ZERO);
    }

    private String buildSubtitle(CashFlowReportResponse r) {
        StringBuilder sb = new StringBuilder();
        sb.append("Year: ").append(r.year());
        sb.append(" - Currency: ").append(nullSafe(r.baseCurrency()));
        BigDecimal rate = r.usdRate() != null ? r.usdRate() : r.krwRate();
        if (rate != null && rate.signum() != 0) {
            sb.append(" - Exchange Rate: ").append(rate.toPlainString());
        }
        return sb.toString();
    }

    private void stream(HttpServletResponse response, XSSFWorkbook wb, String fileName) throws IOException {
        String filename = fileName == null || fileName.isBlank() ? "CashFlow" : fileName;
        if (!filename.toLowerCase(Locale.ROOT).endsWith(".xlsx")) filename = filename + ".xlsx";
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");

        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition",
                "attachment; filename=\"" + filename + "\"; filename*=UTF-8''" + encoded);
        try (OutputStream out = response.getOutputStream()) {
            wb.write(out);
            out.flush();
        }
    }

    /* =========================== Grouping =========================== */

    private static Grouped groupRows(List<CashFlowRow> rows) {
        Grouped out = new Grouped();
        Group current = null;
        L2Group currentL2 = null;
        Map<String, Group> byL1 = new HashMap<>();

        for (CashFlowRow row : rows) {
            String type = nullSafe(row.rowType());
            switch (type) {
                case "Opening Balance":
                    out.opening = row;
                    break;
                case "Level 1":
                    current = new Group(row);
                    out.groups.add(current);
                    byL1.put(nullSafe(row.displayCd()), current);
                    currentL2 = null;
                    break;
                case "Level 2":
                    if (current != null) {
                        currentL2 = new L2Group(row);
                        current.children.add(currentL2);
                    }
                    break;
                case "Level 3":
                    if (currentL2 != null) {
                        currentL2.l3s.add(row);
                    } else if (current != null) {
                        L2Group orphan = new L2Group(row);
                        orphan.l3s.add(row);
                        current.children.add(orphan);
                        currentL2 = orphan;
                    }
                    break;
                case "Level 1 Subtotal":
                    if ("NON SALES PROFIT".equals(row.displayCd())) {
                        out.nonSalesProfit = row;
                    } else if (current != null) {
                        current.subtotal = row;
                    }
                    break;
                case "Sales Profit":
                case "SALES PROFIT":
                    out.salesProfit = row;
                    break;
                case "End Balance":
                case "Grand Total":
                    out.endBalance = row;
                    break;
                default:
                    break;
            }
        }
        return out;
    }

    private static class Grouped {
        CashFlowRow opening;
        List<Group> groups = new ArrayList<>();
        CashFlowRow salesProfit;
        CashFlowRow nonSalesProfit;
        CashFlowRow endBalance;
    }

    private static class Group {
        final CashFlowRow l1;
        CashFlowRow subtotal;
        List<L2Group> children = new ArrayList<>();
        Group(CashFlowRow l1) { this.l1 = l1; this.subtotal = l1; }
    }

    private static class L2Group {
        final CashFlowRow l2;
        List<CashFlowRow> l3s = new ArrayList<>();
        L2Group(CashFlowRow l2) { this.l2 = l2; }
    }

    /* =========================== Styles =========================== */

    private static class Styles {
        final CellStyle title;
        final CellStyle subtitle;
        final CellStyle headerCenter;
        final CellStyle l1;
        final CellStyle l2;
        final CellStyle body;
        final CellStyle amount;
        final CellStyle subtotalBlank;
        final CellStyle subtotalLabel;
        final CellStyle subtotalAmount;
        final CellStyle profitLabel;
        final CellStyle profitAmount;
        final CellStyle endBalanceLabel;
        final CellStyle endBalanceAmount;

        Styles(XSSFWorkbook wb) {
            Font titleFont = wb.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 14);

            Font subtitleFont = wb.createFont();
            subtitleFont.setFontHeightInPoints((short) 10);

            Font hdrFont = wb.createFont();
            hdrFont.setBold(true);
            hdrFont.setFontHeightInPoints((short) 10);

            Font body = wb.createFont();
            body.setFontHeightInPoints((short) 10);

            Font bodyBold = wb.createFont();
            bodyBold.setBold(true);
            bodyBold.setFontHeightInPoints((short) 10);

            Font white = wb.createFont();
            white.setBold(true);
            white.setColor(IndexedColors.WHITE.getIndex());
            white.setFontHeightInPoints((short) 10);

            title = baseStyle(wb, titleFont);
            title.setAlignment(HorizontalAlignment.CENTER);
            title.setVerticalAlignment(VerticalAlignment.CENTER);

            subtitle = baseStyle(wb, subtitleFont);
            subtitle.setAlignment(HorizontalAlignment.CENTER);
            subtitle.setVerticalAlignment(VerticalAlignment.CENTER);

            headerCenter = boxedStyle(wb, hdrFont);
            headerCenter.setAlignment(HorizontalAlignment.CENTER);
            headerCenter.setVerticalAlignment(VerticalAlignment.CENTER);
            setFill((XSSFCellStyle) headerCenter, "F2F2F2");

            l1 = boxedStyle(wb, bodyBold);
            l1.setAlignment(HorizontalAlignment.CENTER);
            l1.setVerticalAlignment(VerticalAlignment.CENTER);
            setFill((XSSFCellStyle) l1, "FAFAFA");

            l2 = boxedStyle(wb, body);
            l2.setAlignment(HorizontalAlignment.CENTER);
            l2.setVerticalAlignment(VerticalAlignment.CENTER);

            this.body = boxedStyle(wb, body);
            this.body.setAlignment(HorizontalAlignment.LEFT);
            this.body.setVerticalAlignment(VerticalAlignment.CENTER);

            amount = boxedStyle(wb, body);
            amount.setAlignment(HorizontalAlignment.RIGHT);
            amount.setVerticalAlignment(VerticalAlignment.CENTER);
            amount.setDataFormat(wb.createDataFormat().getFormat("#,##0.##"));

            subtotalBlank = boxedStyle(wb, body);

            subtotalLabel = boxedStyle(wb, bodyBold);
            subtotalLabel.setAlignment(HorizontalAlignment.CENTER);
            subtotalLabel.setVerticalAlignment(VerticalAlignment.CENTER);
            setFill((XSSFCellStyle) subtotalLabel, "E9EFFF");

            subtotalAmount = boxedStyle(wb, bodyBold);
            subtotalAmount.setAlignment(HorizontalAlignment.RIGHT);
            subtotalAmount.setVerticalAlignment(VerticalAlignment.CENTER);
            subtotalAmount.setDataFormat(wb.createDataFormat().getFormat("#,##0.##"));
            setFill((XSSFCellStyle) subtotalAmount, "E9EFFF");

            profitLabel = boxedStyle(wb, bodyBold);
            profitLabel.setAlignment(HorizontalAlignment.CENTER);
            profitLabel.setVerticalAlignment(VerticalAlignment.CENTER);
            setFill((XSSFCellStyle) profitLabel, "E9F5EE");

            profitAmount = boxedStyle(wb, bodyBold);
            profitAmount.setAlignment(HorizontalAlignment.RIGHT);
            profitAmount.setVerticalAlignment(VerticalAlignment.CENTER);
            profitAmount.setDataFormat(wb.createDataFormat().getFormat("#,##0.##"));
            setFill((XSSFCellStyle) profitAmount, "E9F5EE");

            endBalanceLabel = boxedStyle(wb, white);
            endBalanceLabel.setAlignment(HorizontalAlignment.CENTER);
            endBalanceLabel.setVerticalAlignment(VerticalAlignment.CENTER);
            setFill((XSSFCellStyle) endBalanceLabel, "16A34A");

            endBalanceAmount = boxedStyle(wb, white);
            endBalanceAmount.setAlignment(HorizontalAlignment.RIGHT);
            endBalanceAmount.setVerticalAlignment(VerticalAlignment.CENTER);
            endBalanceAmount.setDataFormat(wb.createDataFormat().getFormat("#,##0.##"));
            setFill((XSSFCellStyle) endBalanceAmount, "16A34A");
        }

        private static CellStyle baseStyle(XSSFWorkbook wb, Font font) {
            CellStyle s = wb.createCellStyle();
            s.setFont(font);
            return s;
        }

        private static CellStyle boxedStyle(XSSFWorkbook wb, Font font) {
            CellStyle s = wb.createCellStyle();
            s.setFont(font);
            s.setBorderTop(BorderStyle.THIN);
            s.setBorderBottom(BorderStyle.THIN);
            s.setBorderLeft(BorderStyle.THIN);
            s.setBorderRight(BorderStyle.THIN);
            return s;
        }

        private static void setFill(XSSFCellStyle s, String hex) {
            byte[] rgb = new byte[] {
                    (byte) Integer.parseInt(hex.substring(0, 2), 16),
                    (byte) Integer.parseInt(hex.substring(2, 4), 16),
                    (byte) Integer.parseInt(hex.substring(4, 6), 16),
            };
            s.setFillForegroundColor(new XSSFColor(rgb, null));
            s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        }
    }
}
