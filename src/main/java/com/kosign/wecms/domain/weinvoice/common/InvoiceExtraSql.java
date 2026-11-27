package com.kosign.wecms.domain.weinvoice.common;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * SQL for the UI Spec v1.2 invoice columns that live only in the engine's parsed XML / GDT list JSON, over the aliases
 * {@code i} (invoices) and {@code x} (invoice_xml): fees (3.14), trade discount, PO number and payment due date (TTKhac,
 * 00_THAY_DOI §D), related-invoice keys (3.15) and accent-insensitive search (3.3).
 *
 * <p>The engine stores the whole TTKhac of each XML ({@code parsed.tt_khac}); PO and due date have no standard tag and
 * appear under the names counted in the shared engine DB on 2026-11-27 (most frequent first). Record:
 * docs/features/addon-service/weinvoice/weinvoice-invoice-screen-v1-2-2026-11-27.md.
 */
public final class InvoiceExtraSql {

    private InvoiceExtraSql() {
    }

    /** {@code expr} as numeric when it is a plain decimal, else NULL. */
    public static String num(String expr) {
        return "(CASE WHEN " + expr + " ~ '^-?[0-9]+(\\.[0-9]+)?$' THEN (" + expr + ")::numeric END)";
    }

    /** Σ XML fees ({@code DSLPhi/LPhi/TPhi}), else the GDT list {@code tgtphi}. */
    public static final String FEE = "COALESCE((SELECT sum(" + num("ph->>'tien'") + ") FROM jsonb_array_elements("
            + "CASE WHEN jsonb_typeof(x.parsed->'thanh_toan'->'phi') = 'array' THEN x.parsed->'thanh_toan'->'phi'"
            + " ELSE '[]'::jsonb END) ph), " + num("i.list_raw->>'tgtphi'") + ")";

    /** Trade discount {@code TTCKTMai}: XML, else the GDT list. */
    public static final String TRADE_DISCOUNT = "COALESCE(" + num("x.parsed->'thanh_toan'->>'chiet_khau'") + ", "
            + num("i.list_raw->>'ttcktmai'") + ")";

    /** TTKhac names carrying the PO / order number. */
    public static final List<String> PO_KEYS = List.of("Số PO", "PO", "PONO", "PONumber", "SoPo", "purchaseOrder", "RPO",
            "Số đơn hàng", "Đơn đặt hàng số", "Đơn đặt hàng số:", "Đơn đặt hàng", "Số đơn hàng hoặc hợp đồng", "DO");

    /** TTKhac names carrying the payment due date / term. */
    public static final List<String> DUE_KEYS = List.of("Hạn thanh toán", "Hạn thanh toán:", "Thời hạn thanh toán",
            "DueDate", "dueDate", "Duedate", "Kỳ hạn thanh toán", "Ngày thanh toán", "PAYMENT_TERM", "PaymentTerm");

    public static final String PO = firstTtKhac(PO_KEYS);
    public static final String DUE = firstTtKhac(DUE_KEYS);

    private static String firstTtKhac(List<String> keys) {
        return "COALESCE(" + keys.stream()
                .map(k -> "NULLIF(btrim(x.parsed->'tt_khac'->>'" + k.replace("'", "''") + "'), '')")
                .collect(Collectors.joining(", ")) + ")";
    }

    // ---------------------------------------------------------------- related invoice (TTHDLQuan / GDT list)

    /** Related-invoice symbol of a {@code hoa_don_lien_quan} JSON: a 7-character "1C26TAA" loses its template digit. */
    public static String refSymbol(String lq) {
        return "upper(CASE WHEN (" + lq + "->>'ky_hieu') ~ '^[0-9][A-Za-z0-9]{6}$' THEN substr(" + lq
                + "->>'ky_hieu', 2) ELSE " + lq + "->>'ky_hieu' END)";
    }

    /** Related-invoice template number, from {@code ky_hieu_mau} or the leading digit of a 7-character symbol. */
    public static String refTemplate(String lq) {
        return "COALESCE(" + num(lq + "->>'ky_hieu_mau'") + ", CASE WHEN (" + lq
                + "->>'ky_hieu') ~ '^[0-9][A-Za-z0-9]{6}$' THEN left(" + lq + "->>'ky_hieu', 1)::numeric END)";
    }

    /** Related-invoice number without leading zeros, as numeric. */
    public static String refNumber(String lq) {
        return "NULLIF(ltrim(regexp_replace(COALESCE(" + lq + "->>'so_hd', ''), '[^0-9]', '', 'g'), '0'), '')::numeric";
    }

    // ---------------------------------------------------------------- accent-insensitive search

    private static final String VN_LOWER = "àáảãạăằắẳẵặâầấẩẫậèéẻẽẹêềếểễệìíỉĩịòóỏõọôồốổỗộơờớởỡợùúủũụưừứửữựỳýỷỹỵđ";
    /** Base letter → every accented form (both cases), e.g. {@code a} → {@code àáảãạăằ…ÀÁ…}. */
    private static final java.util.Map<Character, String> VARIANTS = new java.util.HashMap<>();

    static {
        for (char ch : VN_LOWER.toCharArray()) {
            char base = foldChar(ch);
            VARIANTS.merge(base, "" + ch + Character.toUpperCase(ch), String::concat);
        }
    }

    private static char foldChar(char ch) {
        if (ch == 'đ' || ch == 'Đ') return 'd';
        String base = Normalizer.normalize(String.valueOf(ch), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return Character.toLowerCase(base.charAt(0));
    }

    /** The keyword without Vietnamese accents, lower case. */
    public static String foldKeyword(String keyword) {
        if (keyword == null) return null;
        String s = keyword.replace('đ', 'd').replace('Đ', 'D');
        s = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return s.toLowerCase(Locale.ROOT);
    }

    /**
     * POSIX regex (PostgreSQL {@code ~}) matching the keyword regardless of accents and case: every letter becomes a
     * class of its cases and accented forms ("mi an lien" → {@code [mM][iIìÌíÍ…] …}). No {@code unaccent} extension on the
     * engine DB, and a {@code translate()} on every item line was ~20× slower (1.36 s vs 0.06 s, 1201496252 over 3 years,
     * measured 2026-11-27).
     */
    public static String searchPattern(String keyword) {
        String folded = foldKeyword(keyword == null ? "" : keyword.trim());
        StringBuilder re = new StringBuilder();
        for (char ch : folded.toCharArray()) {
            if (Character.isLetter(ch)) {
                re.append('[').append(ch).append(Character.toUpperCase(ch))
                        .append(VARIANTS.getOrDefault(ch, "")).append(']');
            } else if (Character.isDigit(ch) || ch == ' ') {
                re.append(ch);
            } else {
                re.append('\\').append(ch);
            }
        }
        return re.toString();
    }
}
