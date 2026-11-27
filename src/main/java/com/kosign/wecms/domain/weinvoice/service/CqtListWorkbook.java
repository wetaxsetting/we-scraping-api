package com.kosign.wecms.domain.weinvoice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kosign.wecms.domain.weinvoice.repository.PackRepository.CqtRow;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * "Bảng kê gốc (CQT)" (UI Spec v1.1 §3.12): one workbook per month in the layout of the GDT portal export
 * (Tra cứu hoá đơn › Xuất Excel). GDT exports each list type separately and the cash-register list has its own
 * columns, so the month workbook holds one sheet per list type, each sheet exactly like the GDT file: rows 1–2 empty,
 * row 3 title, row 4 "Từ ngày … đến ngày …", row 6 header, data from row 7; dates and codes as text, amounts as numbers.
 *
 * <p>Rows are rebuilt from {@code invoices.list_raw} — the engine keeps every column of the GDT Excel row (keys of the
 * GDT JSON list, plus {@code _tthai_text} / {@code _ttxly_text}); rows the engine took from the JSON list carry the
 * same keys with numeric status codes, mapped back to the portal texts.
 */
public final class CqtListWorkbook {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    /** Amount format of the GDT file. */
    private static final String GDT_AMOUNT = "#,###.0#####;-#,###.0#####;0";

    /** Sheet order and names per engine list type. */
    static final Map<String, String> SHEETS = new LinkedHashMap<>();

    static {
        SHEETS.put("mua", "Mua vào - có mã");
        SHEETS.put("mua-khma", "Mua vào - không mã");
        SHEETS.put("mua-mtt", "Mua vào - máy tính tiền");
        SHEETS.put("ban", "Bán ra - có mã");
        SHEETS.put("ban-khma", "Bán ra - không mã");
        SHEETS.put("ban-mtt", "Bán ra - máy tính tiền");
    }

    /** Column = GDT header + list_raw key ({@code #} = row counter). */
    private record Col(String header, String key, boolean amount) {
    }

    private static final List<Col> NORMAL = List.of(
            new Col("STT", "#", false),
            new Col("Ký hiệu mẫu số", "khmshdon", false),
            new Col("Ký hiệu hóa đơn", "khhdon", false),
            new Col("Số hóa đơn", "shdon", false),
            new Col("Ngày lập", "tdlap", false),
            new Col("MST người bán/MST người xuất hàng", "nbmst", false),
            new Col("Tên người bán/Tên người xuất hàng", "nbten", false),
            new Col("MST người mua/MST người nhận hàng", "nmmst", false),
            new Col("Tên người mua/Tên người nhận hàng", "nmten", false),
            new Col("Địa chỉ người mua", "nmdchi", false),
            new Col("Tổng tiền chưa thuế", "tgtcthue", true),
            new Col("Tổng tiền thuế", "tgtthue", true),
            new Col("Tổng tiền chiết khấu thương mại", "ttcktmai", true),
            new Col("Tổng tiền phí", "tgtphi", true),
            new Col("Tổng tiền thanh toán", "tgtttbso", true),
            new Col("Đơn vị tiền tệ", "dvtte", false),
            new Col("Tỷ giá", "tgia", false),
            new Col("Trạng thái hóa đơn", "tthai", false),
            new Col("Kết quả kiểm tra hóa đơn", "ttxly", false));

    private static final List<Col> SALES_MTT = List.of(
            new Col("STT", "#", false),
            new Col("Ký hiệu mẫu số", "khmshdon", false),
            new Col("Ký hiệu hóa đơn", "khhdon", false),
            new Col("Số hóa đơn", "shdon", false),
            new Col("Ngày lập", "tdlap", false),
            new Col("MST người bán/MST người xuất hàng", "nbmst", false),
            new Col("Tên người bán/Tên người xuất hàng", "nbten", false),
            new Col("MST người mua/MST người nhận hàng", "nmmst", false),
            new Col("Tên người mua/Tên người nhận hàng", "nmten", false),
            new Col("Địa chỉ người mua", "nmdchi", false),
            new Col("Căn cước công dân", "nmcccd", false),
            new Col("Tổng tiền chưa thuế", "tgtcthue", true),
            new Col("Tổng tiền thuế", "tgtthue", true),
            new Col("Tổng tiền chiết khấu thương mại", "ttcktmai", true),
            new Col("Tổng tiền thanh toán", "tgtttbso", true),
            new Col("Trạng thái hóa đơn", "tthai", false),
            new Col("Kết quả kiểm tra hóa đơn", "ttxly", false));

    private static final List<Col> PURCHASE_MTT = List.of(
            new Col("STT", "#", false),
            new Col("Ký hiệu mẫu số", "khmshdon", false),
            new Col("Ký hiệu hóa đơn", "khhdon", false),
            new Col("Số hóa đơn", "shdon", false),
            new Col("Ngày lập", "tdlap", false),
            new Col("MST người bán/MST người xuất hàng", "nbmst", false),
            new Col("Tên người bán/Tên người xuất hàng", "nbten", false),
            new Col("Địa chỉ người bán", "nbdchi", false),
            new Col("MST người mua/MST người nhận hàng", "nmmst", false),
            new Col("Tên người mua/Tên người nhận hàng", "nmten", false),
            new Col("Căn cước công dân", "nmcccd", false),
            new Col("Tổng tiền chưa thuế", "tgtcthue", true),
            new Col("Tổng tiền thuế", "tgtthue", true),
            new Col("Tổng tiền chiết khấu thương mại", "ttcktmai", true),
            new Col("Tổng tiền thanh toán", "tgtttbso", true),
            new Col("Trạng thái hóa đơn", "tthai", false),
            new Col("Kết quả kiểm tra hóa đơn", "ttxly", false));

    private static final Map<Integer, String> TTHAI_TEXT = Map.of(
            1, "Hóa đơn mới", 2, "Hóa đơn thay thế", 3, "Hóa đơn điều chỉnh",
            4, "Hóa đơn đã bị thay thế", 5, "Hóa đơn đã bị điều chỉnh", 6, "Hóa đơn đã bị hủy");
    private static final Map<Integer, String> TTXLY_TEXT = Map.of(
            5, "Đã cấp mã hóa đơn",
            6, "Tổng cục thuế đã nhận không mã",
            8, "Cục Thuế đã nhận hóa đơn có mã khởi tạo từ máy tính tiền");

    private CqtListWorkbook() {
    }

    /** Engine list type of a row; rows without one are placed by direction and symbol. */
    static String listType(CqtRow r) {
        if (r.subType() != null && SHEETS.containsKey(r.subType())) return r.subType();
        String dir = "ban".equals(r.direction()) ? "ban" : "mua";
        String sym = r.symbol() == null ? "" : r.symbol().toUpperCase(java.util.Locale.ROOT);
        if (sym.length() >= 4 && sym.charAt(3) == 'M') return dir + "-mtt";
        if (sym.startsWith("K")) return dir + "-khma";
        return dir;
    }

    /** Rows grouped by month {@code yyyy-MM} (sorted), then by list type in sheet order. */
    public static Map<String, Map<String, List<CqtRow>>> byMonth(List<CqtRow> rows) {
        Map<String, Map<String, List<CqtRow>>> out = new TreeMap<>();
        List<CqtRow> sorted = new ArrayList<>(rows);
        sorted.sort(Comparator.comparing((CqtRow r) -> r.issueDate() == null ? "" : r.issueDate())
                .thenComparingLong(CqtRow::number));
        for (CqtRow r : sorted) {
            String ym = r.issueDate() == null || r.issueDate().length() < 7 ? "0000-00" : r.issueDate().substring(0, 7);
            out.computeIfAbsent(ym, k -> {
                Map<String, List<CqtRow>> m = new LinkedHashMap<>();
                SHEETS.keySet().forEach(t -> m.put(t, new ArrayList<>()));
                return m;
            }).get(listType(r)).add(r);
        }
        out.values().forEach(m -> m.values().removeIf(List::isEmpty));
        return out;
    }

    /** File name of one month: {@code bang-ke-goc-CQT_<yyyy-mm>[__THIEU].xlsx}. */
    public static String monthFileName(String ym, boolean incomplete) {
        return "bang-ke-goc-CQT_" + ym + (incomplete ? "__THIEU" : "") + ".xlsx";
    }

    /** Writes the workbook of one month; {@code from} / {@code to} clip the "Từ ngày … đến ngày …" line. */
    public static void writeMonth(OutputStream out, String ym, Map<String, List<CqtRow>> types, LocalDate from,
                                  LocalDate to) throws IOException {
        YearMonth m = YearMonth.parse(ym);
        LocalDate start = from != null && from.isAfter(m.atDay(1)) ? from : m.atDay(1);
        LocalDate end = to != null && to.isBefore(m.atEndOfMonth()) ? to : m.atEndOfMonth();
        SXSSFWorkbook wb = new SXSSFWorkbook(200);
        try {
            Font bold = wb.createFont();
            bold.setBold(true);
            Font titleFont = wb.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 14);
            CellStyle title = wb.createCellStyle();
            title.setFont(titleFont);
            title.setAlignment(HorizontalAlignment.CENTER);
            CellStyle center = wb.createCellStyle();
            center.setAlignment(HorizontalAlignment.CENTER);
            CellStyle header = wb.createCellStyle();
            header.setFont(bold);
            header.setWrapText(true);
            header.setAlignment(HorizontalAlignment.CENTER);
            CellStyle amount = wb.createCellStyle();
            amount.setDataFormat(wb.createDataFormat().getFormat(GDT_AMOUNT));
            for (Map.Entry<String, List<CqtRow>> e : types.entrySet()) {
                List<Col> cols = layout(e.getKey());
                SXSSFSheet sheet = wb.createSheet(SHEETS.get(e.getKey()));
                for (int c = 0; c < cols.size(); c++) {
                    sheet.setColumnWidth(c, (c == 0 ? 8 : c < 4 ? 12 : c == 4 ? 20 : 24) * 256);
                }
                Row t = sheet.createRow(2);
                Cell tc = t.createCell(0);
                tc.setCellValue(e.getKey().endsWith("-mtt") ? "DANH SÁCH HÓA ĐƠN MÁY TÍNH TIỀN" : "DANH SÁCH HÓA ĐƠN");
                tc.setCellStyle(title);
                Row d = sheet.createRow(3);
                Cell dc = d.createCell(0);
                dc.setCellValue("Từ ngày " + start.format(DMY) + " đến ngày " + end.format(DMY));
                dc.setCellStyle(center);
                sheet.addMergedRegion(new CellRangeAddress(2, 2, 0, cols.size() - 1));
                sheet.addMergedRegion(new CellRangeAddress(3, 3, 0, cols.size() - 1));
                Row h = sheet.createRow(5);
                for (int c = 0; c < cols.size(); c++) {
                    Cell hc = h.createCell(c);
                    hc.setCellValue(cols.get(c).header());
                    hc.setCellStyle(header);
                }
                int r = 6;
                int stt = 0;
                for (CqtRow row : e.getValue()) {
                    Map<String, Object> v = values(row);
                    Row xr = sheet.createRow(r++);
                    stt++;
                    for (int c = 0; c < cols.size(); c++) {
                        Col col = cols.get(c);
                        Object value = "#".equals(col.key()) ? (Object) stt : v.get(col.key());
                        if (value == null) continue;
                        Cell cell = xr.createCell(c);
                        if (value instanceof Number n) {
                            cell.setCellValue(n.doubleValue());
                            if (col.amount()) cell.setCellStyle(amount);
                        } else {
                            cell.setCellValue(value.toString());
                        }
                    }
                }
            }
            wb.write(out);
        } finally {
            wb.dispose();
            wb.close();
        }
    }

    private static List<Col> layout(String type) {
        return switch (type) {
            case "ban-mtt" -> SALES_MTT;
            case "mua-mtt" -> PURCHASE_MTT;
            default -> NORMAL;
        };
    }

    /** Cell values of one row by list_raw key: GDT texts for codes, dates as {@code dd/MM/yyyy} text. */
    static Map<String, Object> values(CqtRow r) {
        JsonNode raw = parse(r.listRawJson());
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("khmshdon", String.valueOf(r.templateNo()));
        v.put("khhdon", r.symbol());
        v.put("shdon", String.valueOf(r.number()));
        v.put("tdlap", r.issueDate() == null ? null : LocalDate.parse(r.issueDate().substring(0, 10)).format(DMY));
        v.put("nbmst", text(raw, "nbmst", r.sellerTaxId()));
        v.put("nbten", text(raw, "nbten", r.sellerName()));
        v.put("nbdchi", text(raw, "nbdchi", null));
        v.put("nmmst", text(raw, "nmmst", r.buyerTaxId()));
        v.put("nmten", text(raw, "nmten", r.buyerName()));
        v.put("nmdchi", text(raw, "nmdchi", null));
        v.put("nmcccd", text(raw, "nmcccd", null));
        v.put("tgtcthue", number(raw, "tgtcthue", r.preTax()));
        v.put("tgtthue", number(raw, "tgtthue", r.tax()));
        // UI Spec v1.2 B13: real trade discount / fee totals from the XML (the Excel list leaves them 0 / blank).
        v.put("ttcktmai", r.tradeDiscount() != null ? r.tradeDiscount() : number(raw, "ttcktmai", null));
        v.put("tgtphi", r.fee() != null ? r.fee() : number(raw, "tgtphi", null));
        v.put("tgtttbso", number(raw, "tgtttbso", r.total()));
        v.put("dvtte", text(raw, "dvtte", null));
        Object rate = number(raw, "tgia", null);
        v.put("tgia", rate == null ? null : new BigDecimal(rate.toString()).stripTrailingZeros().scale() <= 0
                ? new BigDecimal(rate.toString()).setScale(1).toPlainString() : rate.toString());
        String tthaiText = text(raw, "_tthai_text", null);
        Integer tthai = r.tthai() != null ? r.tthai() : intOf(raw, "tthai");
        v.put("tthai", tthaiText != null ? tthaiText : tthai == null ? null : TTHAI_TEXT.get(tthai));
        String ttxlyText = text(raw, "_ttxly_text", null);
        Integer ttxly = intOf(raw, "ttxly");
        v.put("ttxly", ttxlyText != null ? ttxlyText : ttxly == null ? null : TTXLY_TEXT.get(ttxly));
        return v;
    }

    private static JsonNode parse(String json) {
        if (json == null || json.isBlank()) return JSON.createObjectNode();
        try {
            return JSON.readTree(json);
        } catch (IOException e) {
            return JSON.createObjectNode();
        }
    }

    private static String text(JsonNode raw, String key, String fallback) {
        JsonNode n = raw.get(key);
        if (n == null || n.isNull()) return fallback;
        String s = n.isTextual() ? n.asText() : n.toString();
        return s.isBlank() ? fallback : s;
    }

    private static Object number(JsonNode raw, String key, BigDecimal fallback) {
        JsonNode n = raw.get(key);
        if (n == null || n.isNull()) return fallback;
        if (n.isNumber()) return n.decimalValue();
        try {
            return new BigDecimal(n.asText().trim().replace(",", ""));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static Integer intOf(JsonNode raw, String key) {
        JsonNode n = raw.get(key);
        if (n == null || n.isNull()) return null;
        if (n.isNumber()) return n.intValue();
        try {
            return Integer.parseInt(n.asText().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
