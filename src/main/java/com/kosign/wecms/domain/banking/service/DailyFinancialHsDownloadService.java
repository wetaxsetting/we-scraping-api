package com.kosign.wecms.domain.banking.service;

import com.kosign.wecms.domain.banking.dto.DailyFinancialHsReport;
import com.kosign.wecms.domain.banking.dto.DailyFinancialHsReport.LedgerRow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.CellRangeAddress;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * HAENGSUNG Daily Financial Excel download — ports legacy
 * {@code rept_0017_001_r002_excel_view.jsp}.
 *
 * <p><b>Template-populate</b> (not programmatic): loads the locale template
 * {@code WeCMS_Daily_Financial_Report_{EN|KO|VI}.xlsx} — copied from legacy
 * {@code /file/excel/VN/} into {@code templates/reports/daily-financial-hs/}
 * — so the branding, header rows, and styling match the legacy file. One
 * PO-approved divergence (2026-07-02): the A1:L1 title is localized per
 * template (EN {@code Daily Financial Report} · KO {@code 일 일 자 금 수 지 현 황}
 * · VI {@code Báo cáo tài chính hằng ngày}); legacy shipped the Korean title
 * in all three. The title lives in the TEMPLATE — this service never writes
 * A1. The report data (re-run through {@link ReportService#dailyFinancialHs}
 * so the workbook reflects the <b>saved DB data</b>) is written into the data
 * area from row index {@value #DATA_START_ROW}, reproducing the legacy layout:
 * <ul>
 *   <li>Period date → (row 1, col 0) centred + (row 2, col 11) right.</li>
 *   <li>12 columns: Bank · Currency · Receipt Code · Payment Code · Desc(EN) ·
 *       Desc(KO) · Customer/Supplier · Before Balance · Receipt · Payment ·
 *       Balance · Memo — ALWAYS all 12, independent of View Setting.</li>
 *   <li>Bank/Currency merged vertically per account group; {@code ACCOUNT_TOTAL}
 *       merges C–F for the "SUB TOTAL" label; {@code CURRENCY_TOTAL} merges A–F
 *       for "TOTAL"; BEFORE_BALANCE shows empty code/description cells.</li>
 *   <li>Number format VND/KRW/JPY → {@code #,##0}; else {@code #,##0.00}.</li>
 * </ul>
 * Output filename mirrors legacy: {@code WeCMS_Daily_Financial_Report_{LANG}.xlsx}.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DailyFinancialHsDownloadService {

    private final ReportService reportService;

    /** First data row (0-based) — legacy writes the ledger from row 5; rows
     *  0–4 are the template's title / period-date / column-header area. */
    private static final int DATA_START_ROW = 5;

    private static final int COL_BANK = 0, COL_CCY = 1, COL_REPT = 2, COL_PYMN = 3,
            COL_DESC_EN = 4, COL_DESC_KO = 5, COL_CUST = 6, COL_BEFORE = 7,
            COL_RECEIPT = 8, COL_PAYMENT = 9, COL_BALANCE = 10, COL_MEMO = 11;

    /** Currencies with no fractional part (legacy: VND/KRW/JPY → {@code #,##0}). */
    private static final Set<String> ZERO_FRACTION_CCY = Set.of("VND", "KRW", "JPY");

    /** Generate the HS Daily Financial workbook from the locale template.
     *
     * @param asOfDt        {@code yyyyMMdd} period date (blank → today)
     * @param accountFilter {@code ALL} or a JSON bank/account array
     * @param lang          UI locale (EN/KO/VI; KM/other → EN)
     */
    public ReportsDownloadService.GeneratedReport generate(String asOfDt, String accountFilter, String lang) {
        DailyFinancialHsReport report = reportService.dailyFinancialHs(asOfDt, accountFilter);
        Lang loc = Lang.of(lang);
        String templatePath = "templates/reports/daily-financial-hs/" + loc.templateFile;

        try (InputStream in = new ClassPathResource(templatePath).getInputStream();
             Workbook wb = WorkbookFactory.create(in);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = wb.getSheetAt(0);
            writePeriodDate(wb, sheet, report.asOfDt(), loc);
            setColumnWidths(sheet);
            populate(wb, sheet, report.rows());

            wb.write(out);
            log.info("feature=daily-financial-hs report-download template={} asOf={} filter={} rows={}",
                    loc.templateFile, report.asOfDt(), report.accountFilter(), report.rows().size());
            return new ReportsDownloadService.GeneratedReport(out.toByteArray(), loc.fileName);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to generate Daily Financial (HS) workbook", e);
        }
    }

    /* -------------------- period date header -------------------- */

    private void writePeriodDate(Workbook wb, Sheet sheet, String yyyymmdd, Lang loc) {
        String formatted = formatPeriodDate(yyyymmdd, loc);
        if (formatted.isEmpty()) return;

        CellStyle dateStyle = wb.createCellStyle();
        dateStyle.setAlignment(HorizontalAlignment.CENTER);
        dateStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        dateStyle.setFont(font(wb, true, (short) 200));

        // Centre date under the title (row 1, col 0) + right-side date (row 2, col 11).
        setCell(cellAt(sheet, 1, COL_BANK), formatted, dateStyle);
        setCell(cellAt(sheet, 2, COL_MEMO), formatted, dateStyle);
    }

    private void setColumnWidths(Sheet sheet) {
        int[] widths = {30, 10, 20, 20, 25, 25, 30, 18, 18, 18, 18, 30};
        for (int c = 0; c < widths.length; c++) {
            sheet.setColumnWidth(c, widths[c] * 256);
        }
    }

    /* -------------------- data area (row 5+) -------------------- */

    private void populate(Workbook wb, Sheet sheet, List<LedgerRow> rows) {
        Styles s = new Styles(wb);

        // Bank/Currency vertical merge ranges — one per account, bounded by the
        // account's own ACCOUNT_TOTAL row. CURRENCY_TOTAL rows are NEVER part of
        // a vertical merge (they get their own A–F horizontal merge), so the
        // range must end at ACCOUNT_TOTAL — not absorb a CURRENCY_TOTAL row.
        // The repository orders rows currency-first (legacy IDO parity), so each
        // CURRENCY_TOTAL sits inline right after its currency's last account
        // block; absorbing it would overlap the A–F merge (POI throws).
        List<int[]> bankMergeRanges = new ArrayList<>();
        int accountStartRow = -1;

        int rowIndex = DATA_START_ROW;
        String currentBankInfo = "";
        String currentCcy = "";

        for (LedgerRow r : rows) {
            String type = r.rowType();
            if ("HEADER".equals(type)) {
                currentBankInfo = buildBankInfo(r);
                currentCcy = safe(r.currency());
                accountStartRow = -1; // new account block begins at its first data row
                continue;
            }

            boolean isSubTotal = "ACCOUNT_TOTAL".equals(type);
            boolean isCurrencyTotal = "CURRENCY_TOTAL".equals(type);

            // Track the account's row span (BEFORE_BALANCE .. ACCOUNT_TOTAL) for
            // the Bank/Currency vertical merge. CURRENCY_TOTAL rows are excluded.
            if (!isCurrencyTotal && accountStartRow == -1) {
                accountStartRow = rowIndex;
            }

            Row row = sheet.getRow(rowIndex);
            if (row == null) row = sheet.createRow(rowIndex);
            if (isCurrencyTotal) {
                row.setHeightInPoints(20);
            } else {
                int lineCount = currentBankInfo.split("\n").length;
                row.setHeightInPoints(Math.max(20, lineCount * 7));
            }

            CellStyle dataStyle = s.left;
            CellStyle numberStyle = s.right;
            CellStyle centerStyle = s.center;
            if (isSubTotal) {
                dataStyle = s.cyanLeft;
                numberStyle = s.cyanRight;
                centerStyle = s.cyanCenter;
            } else if (isCurrencyTotal) {
                dataStyle = s.grayLeft;
                numberStyle = s.grayRight;
                centerStyle = s.grayCenter;
            }

            if (isCurrencyTotal) {
                // A–F empty gray, "TOTAL" in col G (col 6).
                for (int c = COL_BANK; c <= COL_CUST - 1; c++) {
                    setCell(row.createCell(c), "", s.grayCenter);
                }
                setCell(row.createCell(COL_CUST), "TOTAL", s.grayCenter);
            } else {
                setCell(row.createCell(COL_BANK), currentBankInfo, s.mergedLeft);
                setCell(row.createCell(COL_CCY), currentCcy, s.mergedCenter);

                if (isSubTotal) {
                    for (int c = COL_REPT; c <= COL_DESC_KO; c++) {
                        setCell(row.createCell(c), "", dataStyle);
                    }
                    setCell(row.createCell(COL_CUST), "SUB TOTAL", centerStyle);
                } else {
                    boolean isBefore = "BEFORE_BALANCE".equals(type);
                    setCell(row.createCell(COL_REPT), safe(r.reptCd()), dataStyle);
                    setCell(row.createCell(COL_PYMN), safe(r.pymnCd()), dataStyle);
                    // Legacy writes empty Desc(EN/KO) + Customer/Supplier for BEFORE_BALANCE.
                    setCell(row.createCell(COL_DESC_EN), isBefore ? "" : safe(r.descriptionEn()), dataStyle);
                    setCell(row.createCell(COL_DESC_KO), isBefore ? "" : safe(r.descriptionKo()), dataStyle);
                    setCell(row.createCell(COL_CUST), isBefore ? "" : safe(r.customerSupplier()), dataStyle);
                }
            }

            // Before Balance — only BEFORE_BALANCE / ACCOUNT_TOTAL / CURRENCY_TOTAL carry it.
            if ("BEFORE_BALANCE".equals(type) || isSubTotal || isCurrencyTotal) {
                numberCell(wb, s, row, COL_BEFORE, r.beforeBalance(), currentCcy, numberStyle);
            } else {
                setCell(row.createCell(COL_BEFORE), "", numberStyle);
            }
            numberCell(wb, s, row, COL_RECEIPT, r.receipt(), currentCcy, numberStyle);
            numberCell(wb, s, row, COL_PAYMENT, r.payment(), currentCcy, numberStyle);
            numberCell(wb, s, row, COL_BALANCE, r.balance(), currentCcy, numberStyle);

            // Memo — CURRENCY_TOTAL shows the actual company balance instead.
            if (isCurrencyTotal) {
                numberCell(wb, s, row, COL_MEMO, r.balanceCompanyActual(), currentCcy, numberStyle);
            } else {
                setCell(row.createCell(COL_MEMO), safe(r.memo()), dataStyle);
            }

            // ACCOUNT_TOTAL closes the account block → record its vertical merge.
            if (isSubTotal) {
                if (accountStartRow != -1 && rowIndex > accountStartRow) {
                    bankMergeRanges.add(new int[]{accountStartRow, rowIndex});
                }
                accountStartRow = -1;
            }

            rowIndex++;
        }

        applyMerges(sheet, rows, bankMergeRanges);
    }

    private void applyMerges(Sheet sheet, List<LedgerRow> rows, List<int[]> bankMergeRanges) {
        // 1. Bank Account (col 0) + Currency (col 1) vertical merge per account.
        //    Ranges are already bounded to [first-data-row .. ACCOUNT_TOTAL], so
        //    they never touch a CURRENCY_TOTAL row's A–F merge.
        for (int[] range : bankMergeRanges) {
            int startRow = range[0];
            int endRow = range[1];
            if (endRow <= startRow) continue;
            sheet.addMergedRegion(new CellRangeAddress(startRow, endRow, COL_BANK, COL_BANK));
            sheet.addMergedRegion(new CellRangeAddress(startRow, endRow, COL_CCY, COL_CCY));
        }

        // 2. SUB TOTAL merges C–F; TOTAL merges A–F.
        int rowIndex = DATA_START_ROW;
        for (LedgerRow r : rows) {
            String type = r.rowType();
            if ("HEADER".equals(type)) continue;
            if ("ACCOUNT_TOTAL".equals(type)) {
                sheet.addMergedRegion(new CellRangeAddress(rowIndex, rowIndex, COL_REPT, COL_DESC_KO));
            } else if ("CURRENCY_TOTAL".equals(type)) {
                sheet.addMergedRegion(new CellRangeAddress(rowIndex, rowIndex, COL_BANK, COL_DESC_KO));
            }
            rowIndex++;
        }
    }

    /** Bank cell text: bank name block + account number (+ due date), mirroring
     *  legacy — splits the HEADER's BANK_ACCT on "Due date:". */
    private String buildBankInfo(LedgerRow header) {
        String bankAcct = safe(header.bankAccount());
        String acctNo = safe(header.accountNumber());
        String dueDate = "";
        int idx = bankAcct.indexOf("Due date:");
        if (idx >= 0) {
            dueDate = formatDueDate(bankAcct.substring(idx + "Due date:".length()).trim());
            bankAcct = bankAcct.substring(0, idx).trim();
        }
        String info = bankAcct + "\n" + acctNo;
        if (!dueDate.isEmpty()) info += "\nDue Date\n" + dueDate;
        return info;
    }

    /* -------------------- cell helpers -------------------- */

    private static Cell cellAt(Sheet sheet, int rowIdx, int colIdx) {
        Row row = sheet.getRow(rowIdx);
        if (row == null) row = sheet.createRow(rowIdx);
        Cell cell = row.getCell(colIdx);
        if (cell == null) cell = row.createCell(colIdx);
        return cell;
    }

    private static void setCell(Cell cell, String value, CellStyle style) {
        cell.setCellValue(value == null ? "" : value);
        cell.setCellStyle(style);
    }

    /** Write a numeric cell from a wire amount string. Blank/unparseable →
     *  empty cell with the base style (legacy renders blank, never "0"). */
    private void numberCell(Workbook wb, Styles s, Row row, int col, String wire, String ccy, CellStyle baseStyle) {
        Cell cell = row.createCell(col);
        if (wire == null || wire.isBlank()) {
            cell.setCellValue("");
            cell.setCellStyle(baseStyle);
            return;
        }
        try {
            double n = Double.parseDouble(wire.replace(",", "").trim());
            cell.setCellValue(n);
            cell.setCellStyle(s.numberStyle(wb, baseStyle, isZeroFraction(ccy)));
        } catch (NumberFormatException e) {
            cell.setCellValue(wire);
            cell.setCellStyle(baseStyle);
        }
    }

    private static boolean isZeroFraction(String ccy) {
        return ccy != null && ZERO_FRACTION_CCY.contains(ccy.trim().toUpperCase(Locale.ROOT));
    }

    private static String safe(String v) {
        return v == null ? "" : v;
    }

    private static String formatPeriodDate(String yyyymmdd, Lang loc) {
        if (yyyymmdd == null || yyyymmdd.length() != 8) return yyyymmdd == null ? "" : yyyymmdd;
        try {
            String year = yyyymmdd.substring(0, 4);
            int month = Integer.parseInt(yyyymmdd.substring(4, 6));
            int day = Integer.parseInt(yyyymmdd.substring(6, 8));
            if (loc == Lang.KO) return year + "년 " + month + "월 " + day + "일";
            return month + "/" + day + "/" + year;
        } catch (NumberFormatException e) {
            return yyyymmdd;
        }
    }

    /** "19.Sep.2025" / "19.9.2025" → "19 Sep 2025" (legacy formatDueDateFromBankAcct). */
    private static String formatDueDate(String raw) {
        if (raw == null || raw.isBlank()) return "";
        String[] parts = raw.trim().split("\\.");
        if (parts.length < 3) return raw.trim();
        String day = parts[0].trim();
        String month = parts[1].trim();
        String year = parts[2].trim();
        String[] names = {"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};
        try {
            int m = Integer.parseInt(month);
            if (m >= 1 && m <= 12) month = names[m - 1];
        } catch (NumberFormatException ignore) {
            // already text
        }
        try {
            return Integer.parseInt(day) + " " + month + " " + year;
        } catch (NumberFormatException e) {
            return raw.trim();
        }
    }

    /* -------------------- styles (ported from the legacy JSP helpers) -------------------- */

    /** Pooled cell styles for the data area — thin black borders, vertical
     *  centre, Arial 9pt (legacy font height 180). Bold + fill on totals. */
    private static final class Styles {
        final Workbook wb;
        final CellStyle left;
        final CellStyle right;
        final CellStyle center;
        final CellStyle mergedLeft;
        final CellStyle mergedCenter;
        final CellStyle cyanLeft;
        final CellStyle cyanRight;
        final CellStyle cyanCenter;
        final CellStyle grayLeft;
        final CellStyle grayRight;
        final CellStyle grayCenter;
        // Number-style cache keyed by "<fill>:<zeroFraction>" so we don't
        // clone a fresh CellStyle per numeric cell (POI's 64k-style cap).
        private final java.util.Map<String, CellStyle> numberCache = new java.util.HashMap<>();

        Styles(Workbook wb) {
            this.wb = wb;
            left = base(wb, HorizontalAlignment.LEFT, null, false);
            right = base(wb, HorizontalAlignment.RIGHT, null, false);
            center = base(wb, HorizontalAlignment.CENTER, null, false);
            mergedLeft = base(wb, HorizontalAlignment.LEFT, null, true);
            mergedLeft.setWrapText(true);
            mergedCenter = base(wb, HorizontalAlignment.CENTER, null, true);
            cyanLeft = base(wb, HorizontalAlignment.LEFT, IndexedColors.AQUA, true);
            cyanRight = base(wb, HorizontalAlignment.RIGHT, IndexedColors.AQUA, true);
            cyanCenter = base(wb, HorizontalAlignment.CENTER, IndexedColors.AQUA, true);
            grayLeft = base(wb, HorizontalAlignment.LEFT, IndexedColors.GREY_25_PERCENT, true);
            grayRight = base(wb, HorizontalAlignment.RIGHT, IndexedColors.GREY_25_PERCENT, true);
            grayCenter = base(wb, HorizontalAlignment.CENTER, IndexedColors.GREY_25_PERCENT, true);
        }

        CellStyle numberStyle(Workbook wb, CellStyle baseStyle, boolean zeroFraction) {
            String fill = baseStyle == cyanRight ? "cyan" : baseStyle == grayRight ? "gray" : "none";
            String key = fill + ":" + zeroFraction;
            return numberCache.computeIfAbsent(key, k -> {
                CellStyle cs = wb.createCellStyle();
                cs.cloneStyleFrom(baseStyle);
                DataFormat fmt = wb.createDataFormat();
                cs.setDataFormat(fmt.getFormat(zeroFraction ? "#,##0" : "#,##0.00"));
                return cs;
            });
        }

        private static CellStyle base(Workbook wb, HorizontalAlignment align, IndexedColors fill, boolean bold) {
            CellStyle cs = wb.createCellStyle();
            cs.setAlignment(align);
            cs.setVerticalAlignment(VerticalAlignment.CENTER);
            cs.setBorderTop(BorderStyle.THIN);
            cs.setBorderBottom(BorderStyle.THIN);
            cs.setBorderLeft(BorderStyle.THIN);
            cs.setBorderRight(BorderStyle.THIN);
            cs.setTopBorderColor(IndexedColors.BLACK1.getIndex());
            cs.setBottomBorderColor(IndexedColors.BLACK1.getIndex());
            cs.setLeftBorderColor(IndexedColors.BLACK1.getIndex());
            cs.setRightBorderColor(IndexedColors.BLACK1.getIndex());
            cs.setFont(font(wb, bold, (short) 180));
            if (fill != null) {
                cs.setFillForegroundColor(fill.getIndex());
                cs.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            }
            return cs;
        }
    }

    private static Font font(Workbook wb, boolean bold, short height) {
        Font f = wb.createFont();
        f.setBold(bold);
        f.setFontHeight(height);
        f.setFontName("Arial");
        f.setColor(IndexedColors.BLACK1.getIndex());
        return f;
    }

    /* -------------------- locale (template + filename + date format) -------------------- */

    private enum Lang {
        EN("WeCMS_Daily_Financial_Report_EN.xlsx"),
        KO("WeCMS_Daily_Financial_Report_KO.xlsx"),
        VI("WeCMS_Daily_Financial_Report_VI.xlsx");

        final String templateFile;
        /** Output filename == template basename (legacy parity). */
        final String fileName;

        Lang(String file) {
            this.templateFile = file;
            this.fileName = file;
        }

        static Lang of(String lang) {
            if (lang == null) return EN;
            return switch (lang.trim().toLowerCase(Locale.ROOT)) {
                case "ko" -> KO;
                case "vi" -> VI;
                default -> EN;
            };
        }
    }
}
