package com.kosign.wecms.domain.weinvoice.service;

import com.kosign.wecms.domain.weinvoice.dto.InvoiceRow;
import com.kosign.wecms.domain.weinvoice.repository.InvoiceDetailRepository.ItemRow;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Workbooks of the invoice list (UI Spec v1.1 file 02 §3.11) and the "failed invoices" list of the bulk PDF pack
 * (§3.12). Texts are the spec's Vietnamese (the spec defines the file in Vietnamese, as the prototype does).
 *
 * <p>Every sheet: row 1 = merged disclaimer note, row 2 = header with auto-filter, data from row 3; dates are real
 * dates {@code dd/mm/yyyy}, money {@code #,##0} ({@code #,##0.00} when the amount has decimals, e.g. USD). Streaming
 * workbook — a large period does not sit in memory.
 *
 * <p>UI Spec v1.2 §3.11 / §3.13 (2026-11-27): amounts stay in the invoice currency, so the money headers carry no "(₫)"
 * and the sheets add Loại tiền, Tỷ giá, Phí / lệ phí, Tổng quy đổi (VND) and Số PO; a detail sheet adds one fee line
 * (no VAT) per invoice that has fees.
 */
public final class InvoiceWorkbook {

    public static final String NOTE_SUMMARY = "⚠️ Ghi chú: Số liệu tổng hợp lấy từ Cục Thuế qua kết nối chính thức. "
            + "Đề nghị Quý khách đối chiếu với hoá đơn gốc — WeInvoice không chịu trách nhiệm về sai số nguồn dữ liệu.";
    public static final String NOTE_DETAIL = "⚠️ Ghi chú: Dữ liệu tiền trên sheet này đã được làm tròn theo chuẩn Cục Thuế. "
            + "Đề nghị Quý khách kiểm tra kỹ, đối chiếu với hoá đơn gốc để tránh lệch vài đồng.";
    public static final String NO_LINES = "(không có dòng hàng trong XML)";
    /** Last data row an XLSX sheet can hold, minus the two header rows and one "truncated" row. */
    static final int MAX_SHEET_ROWS = 1_048_576 - 3;

    static final List<String> SUMMARY_HEADER = List.of("STT", "Ngày lập", "Hướng", "Ký hiệu mẫu", "Ký hiệu HĐ", "Số HĐ",
            "MST người bán", "Tên người bán", "MST người mua", "Tên người mua", "Loại tiền", "Tỷ giá", "Trước VAT", "VAT",
            "Phí / lệ phí", "Tổng tiền", "Tổng quy đổi (VND)", "Mã tra cứu", "Ghi chú", "Trạng thái", "Mã CQT", "XML", "PDF",
            "Phát hành qua", "Số PO");
    private static final int[] SUMMARY_WIDTHS = {6, 12, 9, 8, 10, 11, 16, 44, 16, 44, 9, 10, 15, 13, 13, 15, 17, 30, 14,
            13, 36, 7, 7, 14, 18};

    private static final int[] DETAIL_WIDTHS = {6, 12, 8, 10, 11, 16, 44, 8, 50, 8, 10, 14, 18, 10, 16, 16, 30, 9};
    public static final String FEE_LINE = "Phí / lệ phí (không chịu thuế)";

    private static final Map<String, String> STATUS_VI = Map.of(
            "original", "Hoá đơn gốc", "replacement", "Thay thế", "replaced", "Bị thay thế",
            "adjustment", "Điều chỉnh", "adjusted", "Bị điều chỉnh", "cancelled", "Đã huỷ");

    private InvoiceWorkbook() {
    }

    /** One sheet: note, header, rows (String / Number / LocalDate / null), widths, which columns are money. */
    public record SheetData(String name, String note, List<String> header, List<List<Object>> rows, int[] widths,
                            Set<Integer> moneyCols) {
    }

    // ------------------------------------------------------------------ §3.11

    /** Sheets Tổng hợp + Chi tiết bán ra / mua vào (a detail sheet only for a direction present in {@code rows}). */
    public static List<SheetData> invoiceSheets(List<InvoiceRow> rows, Map<Long, List<ItemRow>> items) {
        List<SheetData> out = new ArrayList<>();
        out.add(summary(rows));
        if (rows.stream().anyMatch(r -> "sales".equals(r.direction()))) out.add(detail(rows, items, "sales"));
        if (rows.stream().anyMatch(r -> !"sales".equals(r.direction()))) out.add(detail(rows, items, "purchase"));
        return out;
    }

    static SheetData summary(List<InvoiceRow> rows) {
        List<List<Object>> data = new ArrayList<>(rows.size());
        int i = 0;
        for (InvoiceRow r : rows) {
            List<Object> row = new ArrayList<>(20);
            row.add(++i);
            row.add(date(r.issueDate()));
            row.add("sales".equals(r.direction()) ? "Bán ra" : "Mua vào");
            row.add(number(r.templateNo()));
            row.add(r.symbol());
            row.add(number(r.number()));
            row.add(r.sellerTaxId());
            row.add(r.sellerName());
            row.add(r.buyerTaxId());
            row.add(r.buyerName());
            row.add(blankToEmpty(r.currency()));
            row.add("VND".equals(r.currency()) ? null : r.exchangeRate());
            row.add(r.preTax());
            row.add(r.tax());
            row.add(r.fee());
            row.add(r.total());
            row.add(r.totalVnd());
            row.add(blankToEmpty(r.lookupCode()));
            row.add("");
            row.add(STATUS_VI.getOrDefault(r.status(), r.status()));
            row.add(r.cqtCode() == null || r.cqtCode().isBlank() ? "(không mã)" : r.cqtCode());
            row.add(r.hasXml() ? "Có" : "Thiếu");
            row.add(r.hasPdf() ? "Có" : "Thiếu");
            row.add(r.provider() != null ? r.provider() : blankToEmpty(r.providerTaxId()));
            row.add(blankToEmpty(r.poNumber()));
            data.add(row);
        }
        return new SheetData("Tổng hợp", NOTE_SUMMARY, SUMMARY_HEADER, data, SUMMARY_WIDTHS, Set.of(12, 13, 14, 15, 16));
    }

    /** One row per XML line of the invoices of one direction ({@code sales} / {@code purchase}). */
    static SheetData detail(List<InvoiceRow> rows, Map<Long, List<ItemRow>> items, String direction) {
        boolean sales = "sales".equals(direction);
        String who = sales ? "người mua" : "người bán";
        List<String> header = List.of("STT", "Ngày HĐ", "Ký hiệu mẫu", "Ký hiệu HĐ", "Số HĐ", "MST " + who, "Tên " + who,
                "STT dòng", "Tên hàng hoá / dịch vụ", "ĐVT", "Số lượng", "Đơn giá", "Thành tiền chưa thuế", "Thuế suất",
                "Tiền thuế GTGT", "Tổng tiền", "Mã tra cứu", "Loại tiền");
        List<List<Object>> data = new ArrayList<>();
        int stt = 0;
        for (InvoiceRow r : rows) {
            if (sales != "sales".equals(r.direction())) continue;
            List<Object> head = List.of(date(r.issueDate()), number(r.templateNo()), r.symbol(), number(r.number()),
                    blankToEmpty(sales ? r.buyerTaxId() : r.sellerTaxId()), blankToEmpty(sales ? r.buyerName() : r.sellerName()));
            List<ItemRow> lines = items.getOrDefault(Long.parseLong(r.id()), List.of());
            if (lines.isEmpty()) {
                List<Object> row = new ArrayList<>(17);
                row.add(++stt);
                row.addAll(head);
                row.add("");
                row.add(NO_LINES);
                row.add("");
                row.add(null);
                row.add(null);
                row.add(r.preTax());
                row.add(impliedRate(r.preTax(), r.tax()));
                row.add(r.tax());
                row.add(r.total());
                row.add(blankToEmpty(r.lookupCode()));
                row.add(blankToEmpty(r.currency()));
                data.add(row);
                continue;
            }
            int lineNo = 0;
            for (ItemRow it : lines) {
                boolean discount = it.lineKind() != null && it.lineKind() == 3;
                BigDecimal amount = signed(it.amount(), discount);
                BigDecimal tax = it.taxAmount() != null ? signed(it.taxAmount(), discount) : lineTax(amount, it.vatRate());
                BigDecimal total = it.totalAmount() != null ? signed(it.totalAmount(), discount)
                        : amount == null ? null : amount.add(tax == null ? BigDecimal.ZERO : tax);
                List<Object> row = new ArrayList<>(17);
                row.add(++stt);
                row.addAll(head);
                row.add(++lineNo);
                row.add(blankToEmpty(it.name()));
                row.add(blankToEmpty(it.unit()));
                row.add(it.qty());
                row.add(it.price());
                row.add(amount);
                row.add(rateLabel(it.vatRate()));
                row.add(tax);
                row.add(total);
                row.add(blankToEmpty(r.lookupCode()));
                row.add(blankToEmpty(r.currency()));
                data.add(row);
            }
            if (r.fee() != null && r.fee().signum() != 0) {
                // Fees / charges are outside VAT (UI Spec v1.2 §3.14): own line, no rate, tax 0.
                List<Object> row = new ArrayList<>(18);
                row.add(++stt);
                row.addAll(head);
                row.add(++lineNo);
                row.add(FEE_LINE);
                row.add("");
                row.add(null);
                row.add(null);
                row.add(r.fee());
                row.add("");
                row.add(BigDecimal.ZERO);
                row.add(r.fee());
                row.add(blankToEmpty(r.lookupCode()));
                row.add(blankToEmpty(r.currency()));
                data.add(row);
            }
        }
        return new SheetData(sales ? "Chi tiết bán ra" : "Chi tiết mua vào", NOTE_DETAIL, header, data, DETAIL_WIDTHS,
                Set.of(11, 12, 14, 15));
    }

    /** {@code 10} / {@code 10%} / {@code 10.0} → {@code 10%}; {@code KCT}, {@code KKKNT}, {@code KHAC:x%} kept. */
    static String rateLabel(String raw) {
        if (raw == null || raw.isBlank()) return "";
        String s = raw.trim();
        String num = s.endsWith("%") ? s.substring(0, s.length() - 1).trim() : s;
        try {
            return new BigDecimal(num).stripTrailingZeros().toPlainString() + "%";
        } catch (NumberFormatException notNumber) {
            return s.toUpperCase(java.util.Locale.ROOT);
        }
    }

    private static String impliedRate(BigDecimal preTax, BigDecimal tax) {
        if (preTax == null || tax == null || preTax.signum() == 0 || tax.signum() == 0) return "";
        return tax.multiply(BigDecimal.valueOf(100)).divide(preTax, 0, RoundingMode.HALF_UP).toPlainString() + "%";
    }

    private static BigDecimal lineTax(BigDecimal amount, String rate) {
        if (amount == null) return null;
        String l = rateLabel(rate);
        if (!l.endsWith("%") || l.startsWith("KHAC")) return BigDecimal.ZERO;
        BigDecimal r = new BigDecimal(l.substring(0, l.length() - 1));
        return amount.multiply(r).divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);
    }

    private static BigDecimal signed(BigDecimal v, boolean negative) {
        if (v == null) return null;
        return negative ? v.abs().negate() : v;
    }

    // ------------------------------------------------------------------ §3.12 failed list

    /** One failed invoice of the bulk PDF pack. */
    public record FailedRow(String issueDate, String symbol, String number, String partnerTaxId, String partnerName,
                            String provider, String lookupCode, String lookupUrl, String reason) {
    }

    public static SheetData failedSheet(List<FailedRow> rows) {
        List<List<Object>> data = new ArrayList<>();
        int i = 0;
        for (FailedRow f : rows) {
            data.add(java.util.Arrays.asList(++i, date(f.issueDate()), f.symbol(), number(f.number()),
                    blankToEmpty(f.partnerTaxId()), blankToEmpty(f.partnerName()), blankToEmpty(f.provider()),
                    blankToEmpty(f.lookupCode()), blankToEmpty(f.lookupUrl()), f.reason()));
        }
        return new SheetData("HĐ thất bại", "Hoá đơn không tải được PDF — thử lại sau hoặc tải thủ công từ link tra cứu.",
                List.of("STT", "Ngày lập", "Ký hiệu", "Số HĐ", "MST đối tác", "Tên đối tác", "Phát hành qua", "Mã tra cứu",
                        "Link tra cứu", "Lý do"), data, new int[]{6, 12, 11, 11, 16, 44, 14, 30, 50, 40}, Set.of());
    }

    // ------------------------------------------------------------------ writer

    public static void write(OutputStream out, List<SheetData> sheets) throws IOException {
        SXSSFWorkbook wb = new SXSSFWorkbook(200);
        try {
            Styles st = new Styles(wb);
            for (SheetData s : sheets) {
                writeSheet(wb, st, s);
            }
            wb.write(out);
        } finally {
            wb.dispose();
            wb.close();
        }
    }

    private static void writeSheet(SXSSFWorkbook wb, Styles st, SheetData s) {
        SXSSFSheet sheet = wb.createSheet(s.name());
        int cols = s.header().size();
        for (int c = 0; c < cols; c++) {
            int w = c < s.widths().length ? s.widths()[c] : 14;
            sheet.setColumnWidth(c, Math.min(255, w + 1) * 256);
        }
        Row note = sheet.createRow(0);
        note.setHeightInPoints(30);
        Cell n = note.createCell(0);
        n.setCellValue(s.note());
        n.setCellStyle(st.note);
        if (cols > 1) sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, cols - 1));
        Row h = sheet.createRow(1);
        for (int c = 0; c < cols; c++) {
            Cell cell = h.createCell(c);
            cell.setCellValue(s.header().get(c));
            cell.setCellStyle(st.header);
        }
        int r = 2;
        for (List<Object> values : s.rows()) {
            if (r - 2 >= MAX_SHEET_ROWS) {
                sheet.createRow(r++).createCell(0).setCellValue("… đã cắt bớt: vượt số dòng tối đa của Excel");
                break;
            }
            Row row = sheet.createRow(r++);
            for (int c = 0; c < values.size() && c < cols; c++) {
                setCell(row.createCell(c), values.get(c), s.moneyCols().contains(c), st);
            }
        }
        sheet.setAutoFilter(new CellRangeAddress(1, Math.max(1, r - 1), 0, cols - 1));
        sheet.createFreezePane(0, 2);
    }

    private static void setCell(Cell cell, Object v, boolean money, Styles st) {
        if (v == null) return;
        if (v instanceof LocalDate d) {
            cell.setCellValue(d);
            cell.setCellStyle(st.date);
        } else if (v instanceof BigDecimal b) {
            cell.setCellValue(b.doubleValue());
            if (money) cell.setCellStyle(b.stripTrailingZeros().scale() > 0 ? st.moneyDecimal : st.money);
        } else if (v instanceof Number num) {
            cell.setCellValue(num.doubleValue());
            if (money) cell.setCellStyle(st.money);
        } else {
            cell.setCellValue(v.toString());
        }
    }

    private static final class Styles {
        final CellStyle note;
        final CellStyle header;
        final CellStyle date;
        final CellStyle money;
        final CellStyle moneyDecimal;

        Styles(Workbook wb) {
            Font bold = wb.createFont();
            bold.setBold(true);
            Font noteFont = wb.createFont();
            noteFont.setItalic(true);
            noteFont.setColor(org.apache.poi.ss.usermodel.IndexedColors.DARK_RED.getIndex());
            note = wb.createCellStyle();
            note.setFont(noteFont);
            note.setWrapText(true);
            note.setVerticalAlignment(VerticalAlignment.CENTER);
            header = wb.createCellStyle();
            header.setFont(bold);
            header.setFillForegroundColor(org.apache.poi.ss.usermodel.IndexedColors.GREY_25_PERCENT.getIndex());
            header.setFillPattern(org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND);
            date = wb.createCellStyle();
            date.setDataFormat(wb.createDataFormat().getFormat("dd/mm/yyyy"));
            money = wb.createCellStyle();
            money.setDataFormat(wb.createDataFormat().getFormat("#,##0"));
            moneyDecimal = wb.createCellStyle();
            moneyDecimal.setDataFormat(wb.createDataFormat().getFormat("#,##0.00"));
        }
    }

    // ------------------------------------------------------------------ helpers

    static LocalDate date(String iso) {
        if (iso == null || iso.length() < 10) return null;
        try {
            return LocalDate.parse(iso.substring(0, 10));
        } catch (java.time.format.DateTimeParseException e) {
            return null;
        }
    }

    /** Digits only → a number cell (sortable); anything else stays text. */
    static Object number(String s) {
        if (s == null) return null;
        String t = s.trim();
        if (t.matches("\\d{1,15}")) return Long.parseLong(t);
        return t;
    }

    private static String blankToEmpty(String s) {
        return s == null ? "" : s;
    }
}
