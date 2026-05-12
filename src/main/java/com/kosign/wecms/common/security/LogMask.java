package com.kosign.wecms.common.security;

/**
 * Presentation-layer masking helpers for sensitive identifiers shared
 * across features. Currently used by firm-banking-settings (and
 * downstream consumers) to honour the OQ-4 logging rule
 * ("never log full account numbers").
 *
 * <p>Centralised so every caller produces the same masked shape — a
 * mismatch between log lines and API responses is a forensic hazard.
 *
 * <p>Reference:
 * <a href="../../../../../../../../../docs/refactor-wecms/features/settings/firm-banking-settings.md">
 * docs/refactor-wecms/features/settings/firm-banking-settings.md §7.4.1</a>.
 */
public final class LogMask {

    private LogMask() { }

    private static final String NULL_LITERAL = "null";
    private static final String BLANK_MASK   = "***";
    private static final char   MASK_CHAR    = '*';

    /**
     * Bank account number mask. Reveals the first 4 and last 4 digits
     * with the middle replaced by a fixed-width run of asterisks. Short
     * values (≤ 8 chars) are fully masked because partial reveal of a
     * short number leaks proportionally more entropy than for a long
     * one.
     *
     * <p>Examples:
     * <ul>
     *   <li>{@code "100912150384"}    → {@code "1009****0384"}</li>
     *   <li>{@code "700005285280"}    → {@code "7000****5280"}</li>
     *   <li>{@code "12345"}           → {@code "***"}</li>
     *   <li>{@code null}              → {@code "null"}</li>
     *   <li>{@code ""} / blank        → {@code "***"}</li>
     * </ul>
     *
     * <p>Returns {@code "null"} (not {@code null}) so log statements
     * never NPE while formatting.
     */
    public static String acct(String wdrlActNo) {
        if (wdrlActNo == null) return NULL_LITERAL;
        String s = wdrlActNo.trim();
        if (s.isEmpty() || s.length() <= 8) return BLANK_MASK;
        StringBuilder b = new StringBuilder(s.length());
        b.append(s, 0, 4);
        for (int i = 4; i < s.length() - 4; i++) {
            b.append(MASK_CHAR);
        }
        b.append(s, s.length() - 4, s.length());
        return b.toString();
    }
}
