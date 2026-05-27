package com.kosign.wecms.common.export;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Streaming XLSX writer for the shared export feature. Uses Apache POI
 * SXSSF so a 50k-row export keeps a bounded heap footprint (default
 * window of 100 rows in memory, the rest spooled to a temp file).
 *
 * <p>Layout:
 * <pre>
 *   Row 0:  [TITLE              ] (merged across all columns, bold, 14pt, centered)
 *   Row 1:  [subHeader summary  ] (merged, 11pt, centered) — only when present
 *   Row 2:  (blank)
 *   Row 3:  | Header A | Header B | … |  (bold, grey-25 background, thin borders)
 *   Row 4+: data rows, one per source row, formatted per-column
 * </pre>
 *
 * Format mapping (per {@link ExcelColumnFormat}):
 * <ul>
 *   <li>{@code TEXT}     — string passthrough.</li>
 *   <li>{@code AMOUNT}   — wire decimal parsed to a numeric cell with
 *       format {@code #,##0.##}, right aligned.</li>
 *   <li>{@code NUMBER}   — wire decimal parsed to a numeric cell with
 *       format {@code #,##0}.</li>
 *   <li>{@code DATE}     — legacy {@code YYYYMMDD} → {@code yyyy-MM-dd} text.</li>
 *   <li>{@code DATETIME} — legacy {@code YYYYMMDDHHmmss} → {@code yyyy-MM-dd HH:mm:ss} text.</li>
 * </ul>
 *
 * Coercion failures degrade to text — never throw — so a single bad row
 * doesn't blow up a 50k-row export.
 */
@Component
public class ExcelWorkbookWriter {

    private static final DateTimeFormatter LEGACY_DATETIME =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final DateTimeFormatter LEGACY_DATE =
            DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter DISPLAY_DATETIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DISPLAY_DATE =
            DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** Legacy single-cell subheader (centered, merged across columns). */
    public void write(
            OutputStream out,
            String title,
            String subHeader,
            List<ExcelColumnSpec> columns,
            List<Map<String, Object>> rows
    ) throws IOException {
        write(out, title, subHeader, null, columns, rows);
    }

    /**
     * @param subHeaderRight Optional right-aligned half of the subheader
     *   row, mirroring the legacy {@code {left_title, right_title}}
     *   composition used by Approval Box. When non-null, the row splits
     *   into two merged halves: left-aligned {@code subHeader} on
     *   columns {@code [0 .. mid-1]}, right-aligned {@code subHeaderRight}
     *   on columns {@code [mid .. last]}. When null, falls back to the
     *   legacy centered merged cell.
     */
    public void write(
            OutputStream out,
            String title,
            String subHeader,
            String subHeaderRight,
            List<ExcelColumnSpec> columns,
            List<Map<String, Object>> rows
    ) throws IOException {
        try (SXSSFWorkbook wb = new SXSSFWorkbook(100)) {
            wb.setCompressTempFiles(true);
            SXSSFSheet sheet = wb.createSheet("Export");
            // Intentionally NOT calling sheet.trackAllColumnsForAutoSizing():
            // SXSSF's auto-size only sees rows in the in-memory window
            // so a 50k-row export under-sizes everything past the
            // first 100 rows. Instead, the data-write loop below
            // tracks each column's max formatted display width
            // (header + every row) and we set explicit widths in a
            // single pass after writing — see `maxColWidth` and
            // `displayWidth` further down. Costs nothing for small
            // exports and stays bounded for the row cap.

            Styles styles = buildStyles(wb);

            int colCount = columns.size();
            int rowIdx = 0;

            // Title row
            Row titleRow = sheet.createRow(rowIdx);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue(title == null ? "" : title);
            titleCell.setCellStyle(styles.title);
            mergeIfMulti(sheet, rowIdx, 0, colCount - 1);
            titleRow.setHeightInPoints(22f);
            rowIdx++;

            // Sub-header row (count + totals).
            boolean hasLeft  = subHeader != null && !subHeader.isBlank();
            boolean hasRight = subHeaderRight != null && !subHeaderRight.isBlank();
            if (hasLeft || hasRight) {
                Row subRow = sheet.createRow(rowIdx);
                if (hasRight && colCount > 1) {
                    // Two-cell split — mirrors legacy `{left_title, right_title}`.
                    // Left half = columns [0 .. mid-1], left-aligned.
                    // Right half = columns [mid .. last], right-aligned.
                    int mid = colCount / 2;
                    Cell leftCell = subRow.createCell(0);
                    leftCell.setCellValue(hasLeft ? subHeader : "");
                    leftCell.setCellStyle(styles.subHeaderLeft);
                    mergeIfMulti(sheet, rowIdx, 0, mid - 1);

                    Cell rightCell = subRow.createCell(mid);
                    rightCell.setCellValue(subHeaderRight);
                    rightCell.setCellStyle(styles.subHeaderRight);
                    mergeIfMulti(sheet, rowIdx, mid, colCount - 1);
                } else {
                    // Single merged centered cell (legacy single-line shape).
                    Cell subCell = subRow.createCell(0);
                    subCell.setCellValue(hasLeft ? subHeader : subHeaderRight);
                    subCell.setCellStyle(styles.subHeader);
                    mergeIfMulti(sheet, rowIdx, 0, colCount - 1);
                }
                // Grow the row to fit the wrapped content. Single-line
                // subheaders keep the legacy 18pt height; multi-line
                // subheaders allocate ~14pt per visual line plus a
                // small padding so descenders don't clip.
                String leftStr  = hasLeft  ? subHeader      : "";
                String rightStr = hasRight ? subHeaderRight : "";
                int leftLines  = 1 + (int) leftStr.chars().filter(ch -> ch == '\n').count();
                int rightLines = 1 + (int) rightStr.chars().filter(ch -> ch == '\n').count();
                int lineCount  = Math.max(leftLines, rightLines);
                subRow.setHeightInPoints(
                        lineCount == 1 ? 18f : Math.max(18f, lineCount * 14f + 4f));
                rowIdx++;
            }

            // Blank spacer row
            sheet.createRow(rowIdx);
            rowIdx++;

            // Header row
            Row headerRow = sheet.createRow(rowIdx);
            for (int c = 0; c < colCount; c++) {
                ExcelColumnSpec col = columns.get(c);
                Cell cell = headerRow.createCell(c);
                cell.setCellValue(headerLabel(col));
                cell.setCellStyle(styles.header);
            }
            headerRow.setHeightInPoints(20f);
            rowIdx++;

            // Per-column max display width — initialised from the
            // header label, then expanded by each row's formatted
            // value so numeric / datetime / long-text columns size to
            // their actual content rather than the (often short)
            // header. Prevents the legacy "########" symptom on
            // amount columns when the header is shorter than the
            // formatted number.
            int[] maxColWidth = new int[colCount];
            for (int c = 0; c < colCount; c++) {
                maxColWidth[c] = visualWidth(headerLabel(columns.get(c)));
            }

            // Data rows
            for (Map<String, Object> row : rows) {
                Row dataRow = sheet.createRow(rowIdx);
                for (int c = 0; c < colCount; c++) {
                    ExcelColumnSpec col = columns.get(c);
                    Cell cell = dataRow.createCell(c);
                    Object raw = row == null ? null : row.get(col.fieldId());
                    writeCell(cell, raw, col, styles);
                    int displayLen = displayWidth(raw, col.resolvedFormat());
                    if (displayLen > maxColWidth[c]) {
                        maxColWidth[c] = displayLen;
                    }
                }
                rowIdx++;
            }

            // Apply final column widths. Two paths:
            //   1. Caller supplied `widthChars` on the column spec —
            //      use it verbatim (only clamp at MAX_COLUMN_WIDTH_CHARS
            //      so a typo can't blow the column to 1000 chars). This
            //      is the recommended path: every page declares its
            //      widths once in its export config registry, no
            //      surprises across releases.
            //   2. No explicit width — fall back to the auto-measure
            //      we tracked above (header + every data row). Add a
            //      per-format minimum (so empty columns still render)
            //      plus padding so text doesn't kiss the cell border.
            // SXSSF's `autoSizeColumn` is intentionally NOT used (only
            // sees the in-memory window, under-sizes 50k exports).
            for (int c = 0; c < colCount; c++) {
                ExcelColumnSpec col = columns.get(c);
                int chars;
                Integer override = col.widthChars();
                if (override != null && override > 0) {
                    chars = Math.min(MAX_COLUMN_WIDTH_CHARS, override);
                } else {
                    int formatMin = formatMinWidth(col.resolvedFormat());
                    chars = Math.max(formatMin, maxColWidth[c] + COLUMN_WIDTH_PADDING);
                    if (chars > MAX_COLUMN_WIDTH_CHARS) chars = MAX_COLUMN_WIDTH_CHARS;
                }
                sheet.setColumnWidth(c, charsToPoiWidth(chars));
            }

            wb.write(out);
            out.flush();
            wb.dispose(); // delete the streaming temp file
        }
    }

    private static String headerLabel(ExcelColumnSpec col) {
        String h = col.header() == null ? col.fieldId() : col.header();
        // Append the currency code to AMOUNT column headers when the
        // caller supplied one — matches the legacy "Balance (VND)"
        // header convention without polluting the cell value.
        if (col.resolvedFormat() == ExcelColumnFormat.AMOUNT
                && col.ccyCd() != null && !col.ccyCd().isBlank()) {
            return h + " (" + col.ccyCd() + ")";
        }
        return h;
    }

    private static void mergeIfMulti(SXSSFSheet sheet, int row, int firstCol, int lastCol) {
        if (lastCol > firstCol) {
            sheet.addMergedRegion(new CellRangeAddress(row, row, firstCol, lastCol));
        }
    }

    private void writeCell(Cell cell, Object raw, ExcelColumnSpec col, Styles styles) {
        ExcelColumnFormat format = col.resolvedFormat();
        ExcelAlignment align = col.resolvedAlign();
        switch (format) {
            case AMOUNT -> writeAmount(cell, raw, styles.amountFor(align));
            case NUMBER -> writeNumber(cell, raw, styles.numberFor(align));
            case DATE -> writeDate(cell, raw, styles.textFor(align));
            case DATETIME -> writeDatetime(cell, raw, styles.textFor(align));
            case TEXT -> writeText(cell, raw, styles.textFor(align));
        }
    }

    private static void writeText(Cell cell, Object raw, CellStyle style) {
        cell.setCellValue(raw == null ? "" : raw.toString());
        cell.setCellStyle(style);
    }

    private static void writeAmount(Cell cell, Object raw, CellStyle style) {
        BigDecimal val = parseDecimal(raw);
        if (val == null) {
            // Fallback: write as text so the user still sees the value.
            cell.setCellValue(raw == null ? "" : raw.toString());
        } else {
            cell.setCellValue(val.doubleValue());
        }
        cell.setCellStyle(style);
    }

    private static void writeNumber(Cell cell, Object raw, CellStyle style) {
        BigDecimal val = parseDecimal(raw);
        if (val == null) {
            cell.setCellValue(raw == null ? "" : raw.toString());
        } else {
            cell.setCellValue(val.doubleValue());
        }
        cell.setCellStyle(style);
    }

    private static void writeDate(Cell cell, Object raw, CellStyle style) {
        cell.setCellValue(coerceLegacyDate(raw, false));
        cell.setCellStyle(style);
    }

    private static void writeDatetime(Cell cell, Object raw, CellStyle style) {
        cell.setCellValue(coerceLegacyDate(raw, true));
        cell.setCellStyle(style);
    }

    private static String coerceLegacyDate(Object raw, boolean withTime) {
        if (raw == null) return "";
        String s = raw.toString().trim();
        if (s.isEmpty()) return "";
        try {
            if (withTime && s.length() >= 14) {
                LocalDateTime dt = LocalDateTime.parse(s.substring(0, 14), LEGACY_DATETIME);
                return dt.format(DISPLAY_DATETIME);
            }
            if (s.length() >= 8) {
                LocalDate d = LocalDate.parse(s.substring(0, 8), LEGACY_DATE);
                return d.format(DISPLAY_DATE);
            }
        } catch (RuntimeException ignored) {
            // fall through to raw passthrough
        }
        return s;
    }

    /* ---------- Column-width helpers ---------- */

    /** Hard cap so a pathologically long cell value (e.g. multi-line
     *  description pasted into a TEXT column) does not blow the column
     *  out to a screen-wide width — Excel users can resize manually
     *  past this if they really need to. */
    private static final int MAX_COLUMN_WIDTH_CHARS = 60;

    /** Padding added to every column's measured max width so text
     *  doesn't sit flush against the cell border + leaves room for
     *  the header's auto-filter / sort glyph. */
    private static final int COLUMN_WIDTH_PADDING = 2;

    /** Per-format minimum widths in '0'-character units. Sparse columns
     *  (mostly blank rows) still render readably; AMOUNT picks 22 so
     *  values up to {@code 999,999,999,999.99} (18 chars) clear the
     *  default minimum even when the header is just "Balance" (7) and
     *  even after POI's right-alignment inset eats a couple of cells. */
    private static int formatMinWidth(ExcelColumnFormat format) {
        return switch (format) {
            case TEXT -> 18;
            case AMOUNT -> 22;
            case NUMBER -> 16;
            case DATE -> 14;
            case DATETIME -> 22;
        };
    }

    /**
     * Convert a desired visible-character count to the value POI's
     * {@code setColumnWidth} expects.
     *
     * <p>POI's unit is 1/256 of the default font's '0' character width,
     * but Excel reserves ~5 pixels of cell-internal padding that the
     * naive {@code chars * 256} doesn't account for. The result is a
     * column that's roughly 0.71 chars narrower than the caller asked
     * for — which is exactly what was producing {@code ########} on
     * {@code 8,000,000,000} in a 13-char column sized at "13". The
     * formula below restores parity: caller asks for N visible chars,
     * Excel renders N visible chars.
     *
     * <p>Reference: Excel's column-width spec is documented in
     * <a href="https://learn.microsoft.com/en-us/dotnet/api/documentformat.openxml.spreadsheet.column.width">
     * the OOXML column width section</a>: <code>width = Truncate({Number
     * of Characters} * {Maximum Digit Width} + {5 pixel padding}) /
     * {Maximum Digit Width} * 256</code>. With Arial 10pt's MDW ≈ 7px
     * the padding is roughly 5/7 ≈ 0.71 chars worth of POI units,
     * which we round up to 200 (≈ 0.78 chars) so single-pixel
     * sub-rendering rounding doesn't tip back into truncation.
     */
    private static int charsToPoiWidth(int chars) {
        return chars * 256 + 200;
    }

    /**
     * Approximate visual width of a string in '0'-character units —
     * the unit POI's {@code setColumnWidth} is denominated in. CJK /
     * fullwidth code points are counted as 2 because they render at
     * roughly twice the width of a Latin digit in the default Arial
     * font; ASCII is counted as 1.
     *
     * <p>This is intentionally a heuristic, not a font-metric lookup.
     * Excel reflows the column at open time anyway when the user has
     * a different default font; we just need a value close enough
     * that the cell fits its content out of the box.
     */
    private static int visualWidth(String s) {
        if (s == null) return 0;
        int w = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            // Hangul syllables (AC00-D7AF), CJK unified ideographs
            // (4E00-9FFF), Hiragana/Katakana (3040-30FF), Hangul Jamo
            // (1100-11FF), CJK symbols & punctuation (3000-303F),
            // fullwidth forms (FF00-FFEF) — all count as 2 cells.
            boolean wide =
                    (c >= 0x1100 && c <= 0x115F)
                    || (c >= 0x2E80 && c <= 0x303E)
                    || (c >= 0x3041 && c <= 0x33FF)
                    || (c >= 0x3400 && c <= 0x4DBF)
                    || (c >= 0x4E00 && c <= 0x9FFF)
                    || (c >= 0xA000 && c <= 0xA4CF)
                    || (c >= 0xAC00 && c <= 0xD7A3)
                    || (c >= 0xF900 && c <= 0xFAFF)
                    || (c >= 0xFE30 && c <= 0xFE4F)
                    || (c >= 0xFF00 && c <= 0xFF60)
                    || (c >= 0xFFE0 && c <= 0xFFE6);
            w += wide ? 2 : 1;
        }
        return w;
    }

    /**
     * Reproduce — without writing to the workbook — the visual width
     * of a value as it will appear in its cell. Mirrors {@link #writeCell}
     * exactly so the column-width pass stays in sync with the
     * cell-write pass. Cheap (no POI calls): we only build short
     * strings.
     */
    private static int displayWidth(Object raw, ExcelColumnFormat format) {
        if (raw == null) return 0;
        return switch (format) {
            case TEXT -> visualWidth(raw.toString());
            case AMOUNT -> formattedNumberWidth(raw, FORMATTED_AMOUNT_PATTERN);
            case NUMBER -> formattedNumberWidth(raw, FORMATTED_NUMBER_PATTERN);
            case DATE -> visualWidth(coerceLegacyDate(raw, false));
            case DATETIME -> visualWidth(coerceLegacyDate(raw, true));
        };
    }

    /** Cached formatters for the width pass — the actual cell uses
     *  POI's data format, but the width estimator needs a Java-side
     *  formatter so we don't double-write. {@link DecimalFormat} is
     *  not thread-safe, hence {@link ThreadLocal}. */
    private static final String FORMATTED_AMOUNT_PATTERN = "#,##0.##";
    private static final String FORMATTED_NUMBER_PATTERN = "#,##0";

    private static final ThreadLocal<DecimalFormat> AMOUNT_FORMATTER =
            ThreadLocal.withInitial(() ->
                    new DecimalFormat(FORMATTED_AMOUNT_PATTERN,
                            DecimalFormatSymbols.getInstance(Locale.US)));
    private static final ThreadLocal<DecimalFormat> NUMBER_FORMATTER =
            ThreadLocal.withInitial(() ->
                    new DecimalFormat(FORMATTED_NUMBER_PATTERN,
                            DecimalFormatSymbols.getInstance(Locale.US)));

    private static int formattedNumberWidth(Object raw, String pattern) {
        BigDecimal val = parseDecimal(raw);
        if (val == null) return visualWidth(raw == null ? "" : raw.toString());
        DecimalFormat fmt = pattern.equals(FORMATTED_NUMBER_PATTERN)
                ? NUMBER_FORMATTER.get()
                : AMOUNT_FORMATTER.get();
        return visualWidth(fmt.format(val));
    }

    private static BigDecimal parseDecimal(Object raw) {
        if (raw == null) return null;
        if (raw instanceof BigDecimal bd) return bd;
        if (raw instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        String s = raw.toString().trim();
        if (s.isEmpty() || "-".equals(s)) return null;
        // Strip thousand separators / spaces / common currency prefixes
        // so an already-formatted display string still parses back to a
        // number cell. Negatives in parentheses → minus sign.
        String cleaned = s.replace(",", "").replace(" ", "");
        if (cleaned.startsWith("(") && cleaned.endsWith(")")) {
            cleaned = "-" + cleaned.substring(1, cleaned.length() - 1);
        }
        try {
            return new BigDecimal(cleaned);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /* ---------- Styles ---------- */

    private static final class Styles {
        final CellStyle title;
        final CellStyle subHeader;
        // Left- and right-aligned subheader styles for the legacy
        // {left_title, right_title} two-cell layout (Approval Box).
        final CellStyle subHeaderLeft;
        final CellStyle subHeaderRight;
        final CellStyle header;
        // Per-alignment text / number / amount styles — POI CellStyles
        // are pooled (not per-cell) so a 50k export uses 9 styles total.
        final Map<ExcelAlignment, CellStyle> text = new HashMap<>();
        final Map<ExcelAlignment, CellStyle> number = new HashMap<>();
        final Map<ExcelAlignment, CellStyle> amount = new HashMap<>();

        Styles(CellStyle title, CellStyle subHeader,
               CellStyle subHeaderLeft, CellStyle subHeaderRight,
               CellStyle header) {
            this.title = title;
            this.subHeader = subHeader;
            this.subHeaderLeft = subHeaderLeft;
            this.subHeaderRight = subHeaderRight;
            this.header = header;
        }

        CellStyle textFor(ExcelAlignment a)   { return text.get(a); }
        CellStyle numberFor(ExcelAlignment a) { return number.get(a); }
        CellStyle amountFor(ExcelAlignment a) { return amount.get(a); }
    }

    private Styles buildStyles(Workbook wb) {
        // Title
        Font titleFont = wb.createFont();
        titleFont.setBold(true);
        titleFont.setFontHeightInPoints((short) 14);
        titleFont.setFontName("Arial");
        CellStyle titleStyle = wb.createCellStyle();
        titleStyle.setFont(titleFont);
        titleStyle.setAlignment(HorizontalAlignment.CENTER);
        titleStyle.setVerticalAlignment(VerticalAlignment.CENTER);

        // Subheader (summary line) — centered (legacy single-cell shape)
        Font subFont = wb.createFont();
        subFont.setFontHeightInPoints((short) 11);
        subFont.setFontName("Arial");
        CellStyle subStyle = wb.createCellStyle();
        subStyle.setFont(subFont);
        subStyle.setAlignment(HorizontalAlignment.CENTER);
        subStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        // Enable wrap so `\n` in the subheader string renders as a
        // line break (multi-line subheaders are emitted by the FCY
        // export provider's per-CCY block format). No-op for single-
        // line subheaders that fit the merged cell width.
        subStyle.setWrapText(true);

        // Subheader (left-aligned half) — used when the provider supplies
        // a separate right-aligned half (legacy `{left_title, right_title}`
        // shape — e.g. Approval Box).
        CellStyle subStyleLeft = wb.createCellStyle();
        subStyleLeft.setFont(subFont);
        subStyleLeft.setAlignment(HorizontalAlignment.LEFT);
        subStyleLeft.setVerticalAlignment(VerticalAlignment.CENTER);
        subStyleLeft.setWrapText(true);

        // Subheader (right-aligned half)
        CellStyle subStyleRight = wb.createCellStyle();
        subStyleRight.setFont(subFont);
        subStyleRight.setAlignment(HorizontalAlignment.RIGHT);
        subStyleRight.setVerticalAlignment(VerticalAlignment.CENTER);
        subStyleRight.setWrapText(true);

        // Header row
        Font headerFont = wb.createFont();
        headerFont.setBold(true);
        headerFont.setFontHeightInPoints((short) 10);
        headerFont.setFontName("Arial");
        CellStyle headerStyle = wb.createCellStyle();
        headerStyle.setFont(headerFont);
        headerStyle.setAlignment(HorizontalAlignment.CENTER);
        headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        applyThinBorder(headerStyle);

        Styles styles = new Styles(titleStyle, subStyle, subStyleLeft, subStyleRight, headerStyle);

        // Body styles — text / number / amount × LEFT / RIGHT / CENTER
        short amountFmt = wb.createDataFormat().getFormat("#,##0.##");
        short numberFmt = wb.createDataFormat().getFormat("#,##0");

        for (ExcelAlignment a : ExcelAlignment.values()) {
            styles.text.put(a, bodyStyle(wb, a, (short) 0));
            styles.number.put(a, bodyStyle(wb, a, numberFmt));
            styles.amount.put(a, bodyStyle(wb, a, amountFmt));
        }
        return styles;
    }

    private static CellStyle bodyStyle(Workbook wb, ExcelAlignment a, short dataFormat) {
        Font font = wb.createFont();
        font.setFontHeightInPoints((short) 10);
        font.setFontName("Arial");
        CellStyle s = wb.createCellStyle();
        s.setFont(font);
        s.setAlignment(toPoi(a));
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        if (dataFormat != 0) s.setDataFormat(dataFormat);
        applyThinBorder(s);
        return s;
    }

    private static HorizontalAlignment toPoi(ExcelAlignment a) {
        return switch (a) {
            case RIGHT -> HorizontalAlignment.RIGHT;
            case CENTER -> HorizontalAlignment.CENTER;
            case LEFT -> HorizontalAlignment.LEFT;
        };
    }

    private static void applyThinBorder(CellStyle style) {
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
    }
}
