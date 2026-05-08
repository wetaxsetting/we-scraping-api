package com.kosign.wecms.domain.banking.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * Wire-format + display-format helper for monetary values.
 *
 * <p>Two contracts:
 * <ul>
 *   <li>{@link #wire(BigDecimal)} / {@link #wire(long)} emit raw numeric
 *       strings (no separators, no currency symbol). Sprint 1 wire
 *       payloads carry these — the frontend re-formats for display via
 *       its locale layer ({@code lib/currency.ts formatAmount}).</li>
 *   <li>{@link #forDisplay(BigDecimal, String)} /
 *       {@link #forDisplay(String, String)} return the human-readable
 *       form (thousand separators + currency symbol) — locale + symbol
 *       rules mirror the frontend {@code formatAmount} 1:1 so the same
 *       value renders identically in the page, the print popup, and
 *       the Excel subheader. Used by the shared export pipeline
 *       ({@code common/export/}) for the workbook subHeader and any
 *       other free-text amount string. Numeric data cells stay raw —
 *       the writer applies POI's {@code #,##0.##} cell-level format.</li>
 * </ul>
 *
 * <p>Currency rules (mirror {@code lib/currency.ts}):
 * <ul>
 *   <li>{@code VND} → {@code vi-VN} locale (period thousands), 0 fraction
 *       digits, suffix {@code ₫} (legacy {@code POS_NUM_DISP_TYPE 002}).</li>
 *   <li>{@code USD} / {@code EUR} → {@code en-US} (comma thousands), 2
 *       fraction digits, prefix {@code $} / {@code €}.</li>
 *   <li>{@code KRW} / {@code JPY} → {@code en-US}, 0 fraction digits,
 *       prefix {@code ₩} / {@code ¥}.</li>
 *   <li>Other currencies → {@code vi-VN} locale, 0 fraction digits, no
 *       symbol (matches the frontend's "no entry on the symbol map"
 *       fallback).</li>
 * </ul>
 *
 * Null / blank / unparseable input degrades gracefully to {@code "0"}
 * with the configured currency formatting — the export must never
 * render a blank cell or a raw stack trace in the spreadsheet.
 */
public final class AmountFormatter {

    private static final Locale VI_VN = Locale.forLanguageTag("vi-VN");

    private AmountFormatter() { }

    /* ---------- wire (Sprint 1 raw numeric payload) ---------- */

    public static String wire(BigDecimal value) {
        if (value == null) return "0";
        // Strip trailing zeros so 5000000.00 → 5000000 — keeps the wire
        // payload deterministic against seed data.
        return value.setScale(2, RoundingMode.HALF_UP)
                    .stripTrailingZeros()
                    .toPlainString();
    }

    public static String wire(long value) {
        return Long.toString(value);
    }

    /* ---------- forDisplay (formatted with separators + symbol) ---------- */

    /**
     * Display-format a {@link BigDecimal} amount per the rules above.
     * Used by the shared Excel export pipeline so the subHeader (e.g.
     * {@code "20 account(s) · Balance: 19.886.030.287₫"}) reads the
     * same way the on-screen page header does.
     */
    public static String forDisplay(BigDecimal value, String ccyCd) {
        BigDecimal n = value != null ? value : BigDecimal.ZERO;
        String ccy = ccyCd == null ? "" : ccyCd.trim().toUpperCase(Locale.ROOT);

        Locale locale;
        int fractionDigits;
        String symbol;
        boolean prefix;
        switch (ccy) {
            case "VND" -> { locale = VI_VN;   fractionDigits = 0; symbol = "₫"; prefix = false; }
            case "USD" -> { locale = Locale.US; fractionDigits = 2; symbol = "$"; prefix = true;  }
            case "EUR" -> { locale = Locale.US; fractionDigits = 2; symbol = "€"; prefix = true;  }
            case "KRW" -> { locale = Locale.US; fractionDigits = 0; symbol = "₩"; prefix = true;  }
            case "JPY" -> { locale = Locale.US; fractionDigits = 0; symbol = "¥"; prefix = true;  }
            default     -> { locale = VI_VN;   fractionDigits = 0; symbol = "";  prefix = false; }
        }

        NumberFormat nf = NumberFormat.getInstance(locale);
        nf.setMinimumFractionDigits(fractionDigits);
        nf.setMaximumFractionDigits(fractionDigits);
        nf.setRoundingMode(RoundingMode.HALF_UP);

        String formatted = nf.format(n);
        if (symbol.isEmpty()) return formatted;
        return prefix ? symbol + formatted : formatted + symbol;
    }

    /**
     * Display-format the wire string the backend already emits in
     * DTOs (e.g. {@link com.kosign.wecms.domain.banking.dto.AccountListResponse#balance()}).
     * Saves the caller a round-trip through {@code new BigDecimal(...)}
     * with the same null / blank / parse-error handling we apply
     * everywhere else.
     */
    public static String forDisplay(String wireValue, String ccyCd) {
        if (wireValue == null || wireValue.isBlank()) {
            return forDisplay(BigDecimal.ZERO, ccyCd);
        }
        try {
            return forDisplay(new BigDecimal(wireValue.trim()), ccyCd);
        } catch (NumberFormatException e) {
            // Pass-through on unparseable input — better than throwing
            // mid-export with bytes already partially streamed. The
            // caller (AccountInquiryExportProvider) only ever hands us
            // strings produced by `wire(...)` so this branch is
            // defensive, not expected.
            return wireValue;
        }
    }
}
