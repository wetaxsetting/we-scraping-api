package com.kosign.wecms.domain.weinvoice.common;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Translation between the engine's GDT codes and the WeInvoice / WeCMS vocabulary
 * (UI Spec 02 §3.1, §3.3; Partner API).
 *
 * <p>Engine facts this relies on (we-scraping {@code db/schema.sql}, {@code invoice_list_excel.py}):
 * <ul>
 *   <li>{@code invoices.direction}: {@code mua} = purchase, {@code ban} = sales.</li>
 *   <li>{@code invoices.tthai}: 1 new, 2 replacement, 3 adjustment, 4 replaced, 5 adjusted,
 *       6 cancelled. Rows read from the GDT Excel file keep the original wording in
 *       {@code list_raw._tthai_text}; the 19 rows worded "Hóa đơn đã bị xóa bỏ/hủy bỏ" have
 *       {@code tthai} NULL because the engine mapping lacks that phrase, so the text is the
 *       fallback.</li>
 *   <li>{@code list_raw.ttxly}: 5 coded, 6 without code, 8 cash register (the values the GDT
 *       purchase queries filter on); Excel rows keep the text in {@code _ttxly_text}.</li>
 * </ul>
 */
public final class InvoiceCodes {

    /** Wording of a cancelled invoice when {@code tthai} is NULL (Excel import). */
    private static final Pattern CANCELLED_TEXT = Pattern.compile("(hủy|huỷ|xóa bỏ|xoá bỏ)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /**
     * SQL twin of {@link #effectiveTthai}: effective {@code tthai} of alias {@code i}. Kept next to
     * the Java rule so the two cannot drift apart unnoticed.
     */
    public static final String SQL_TTHAI_EFF =
            "COALESCE(i.tthai, CASE WHEN i.list_raw->>'_tthai_text' ~* '(hủy|huỷ|xóa bỏ|xoá bỏ)' THEN 6 END, 1)";

    /** Invoice-status phrases WeCMS stores verbatim ({@code EInvoiceScrapeService} void sets). */
    private static final Map<Integer, String> STATUS_TEXT = Map.of(
            1, "Hóa đơn mới",
            2, "Hóa đơn thay thế",
            3, "Hóa đơn điều chỉnh",
            4, "Hóa đơn đã bị thay thế",
            5, "Hóa đơn đã bị điều chỉnh",
            6, "Hóa đơn đã bị hủy");

    /**
     * Check-result phrases WeCMS accepts ({@code EInvoiceScrapeService.CHECK_RESULT_BRANCH_A/B});
     * any other phrase makes WeCMS skip the row, so codes 5 / 6 / 8 are always rewritten to
     * these exact strings, whatever wording the GDT export used.
     */
    private static final Map<Integer, String> CHECK_RESULT_TEXT = Map.of(
            0, "Tổng cục Thuế đã nhận",
            4, "Hóa đơn không đủ điều kiện cấp mã",
            5, "Đã cấp mã hóa đơn",
            6, "Cục thuế đã nhận không mã",
            8, "Cục Thuế đã nhận hóa đơn có mã khởi tạo từ máy tính tiền");

    private static final Pattern RATE_PERCENT = Pattern.compile("(-?\\d+(?:[.,]\\d+)?)\\s*%");

    private InvoiceCodes() {
    }

    /** {@code tthai} as stored, or derived from the Excel wording; never {@code null}. */
    public static int effectiveTthai(Integer tthai, String tthaiText) {
        if (tthai != null) return tthai;
        if (tthaiText != null && CANCELLED_TEXT.matcher(tthaiText).find()) return 6;
        return 1;
    }

    /** {@code tthai} → UI Spec {@code InvoiceStatus}. */
    public static String status(int tthai) {
        return switch (tthai) {
            case 2 -> "replacement";
            case 3 -> "adjustment";
            case 4 -> "replaced";
            case 5 -> "adjusted";
            case 6 -> "cancelled";
            default -> "original";
        };
    }

    /** UI Spec {@code InvoiceStatus} → {@code tthai}. */
    public static int tthai(String status) {
        return switch (status == null ? "" : status.trim().toLowerCase(Locale.ROOT)) {
            case "original" -> 1;
            case "replacement" -> 2;
            case "adjustment" -> 3;
            case "replaced" -> 4;
            case "adjusted" -> 5;
            case "cancelled" -> 6;
            default -> throw new IllegalArgumentException("statuses: unknown value " + status);
        };
    }

    /** Engine {@code direction} → {@code purchase} / {@code sales}. */
    public static String direction(String engineDirection) {
        return "ban".equals(engineDirection) ? "sales" : "purchase";
    }

    /** {@code purchase} / {@code sales} → engine {@code direction}. */
    public static String engineDirection(String direction) {
        return switch (direction == null ? "" : direction.trim().toLowerCase(Locale.ROOT)) {
            case "purchase" -> "mua";
            case "sales" -> "ban";
            default -> throw new IllegalArgumentException("direction: must be purchase or sales");
        };
    }

    /** {@code no_code} (symbol starts with K), {@code cash_register} (4th char M) or {@code normal}. */
    public static String kind(String symbol) {
        if (symbol == null) return "normal";
        String s = symbol.trim().toUpperCase(Locale.ROOT);
        if (s.startsWith("K")) return "no_code";
        if (s.length() >= 4 && s.charAt(3) == 'M') return "cash_register";
        return "normal";
    }

    /** {@code DVTTe} → ISO 4217 code; {@code VNĐ} / blank → {@code VND}. */
    public static String currency(String raw) {
        if (raw == null) return "VND";
        String s = raw.trim().toUpperCase(Locale.ROOT);
        if (s.isEmpty() || s.equals("VNĐ") || s.equals("Đ") || s.equals("VND")) return "VND";
        return s;
    }

    /**
     * Line VAT rate as the UI Spec number: 0 / 5 / 8 / 10, -1 = KCT, -2 = KKKNT, other percent
     * values (e.g. {@code KHAC:5.26%}) as their number; {@code null} when the XML has none or the
     * value is unreadable.
     */
    public static BigDecimal vatRate(String thueSuat) {
        if (thueSuat == null || thueSuat.isBlank()) return null;
        String s = thueSuat.trim().toUpperCase(Locale.ROOT);
        if (s.equals("KCT")) return BigDecimal.valueOf(-1);
        if (s.equals("KKKNT")) return BigDecimal.valueOf(-2);
        Matcher m = RATE_PERCENT.matcher(s);
        if (m.find()) {
            return new BigDecimal(m.group(1).replace(',', '.')).stripTrailingZeros();
        }
        try {
            return new BigDecimal(s).stripTrailingZeros();
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Vietnamese invoice-status wording WeCMS stores ({@code invoiceStatus}). */
    public static String invoiceStatusText(int tthai) {
        return STATUS_TEXT.getOrDefault(tthai, STATUS_TEXT.get(1));
    }

    /**
     * Check-result wording for WeCMS ({@code checkResult}). Known codes use the exact phrase WeCMS
     * accepts; otherwise the GDT text is passed through; with neither, the code is inferred from
     * the engine list type ({@code *-khma} = 6, {@code *-mtt} = 8, else 5).
     */
    public static String checkResultText(Integer ttxly, String gdtText, String subType) {
        if (ttxly != null && CHECK_RESULT_TEXT.containsKey(ttxly)) return CHECK_RESULT_TEXT.get(ttxly);
        if (gdtText != null && !gdtText.isBlank()) return gdtText.trim();
        if (subType != null && subType.endsWith("-khma")) return CHECK_RESULT_TEXT.get(6);
        if (subType != null && subType.endsWith("-mtt")) return CHECK_RESULT_TEXT.get(8);
        return CHECK_RESULT_TEXT.get(5);
    }

    /** WeCMS {@code einvTestResults}: {@code ""} coded, {@code "1"} without code, {@code "2"} cash register. */
    public static String testResult(String symbol) {
        return switch (kind(symbol)) {
            case "no_code" -> "1";
            case "cash_register" -> "2";
            default -> "";
        };
    }
}
