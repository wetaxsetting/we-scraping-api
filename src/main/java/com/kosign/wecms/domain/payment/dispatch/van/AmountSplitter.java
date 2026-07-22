package com.kosign.wecms.domain.payment.dispatch.van;

import java.math.BigDecimal;

/**
 * Splits a money amount into the {@code data_amt} (integer part) + {@code data_dcm_amt}
 * (decimal portion) strings the EFS 00301 / 00302 / 202 / 204 requests carry.
 *
 * <p><b>Byte-for-byte reproduction of legacy {@code util.StrUtil.BigDecPaser}</b>
 * ({@code WECMS_BATCH DEV/source/WECMS_BATCH/WECMS_BATCH/src/util/StrUtil.java:98-139}).
 * The legacy contract operates on the amount's <i>string</i> form (the DB {@code FILE_AMT}
 * / {@code TRAN_AMT} value), NOT on a recomputed {@code BigDecimal}:
 * <ul>
 *   <li>the sign (if any) is stripped;</li>
 *   <li>when the string contains a {@code '.'} that is not the last character, the integer
 *       part is the substring before the dot and the decimal portion is the <b>literal
 *       substring after the dot</b> — so {@code "15.00" → dcm "00"}, {@code "15.5" → "5"},
 *       {@code "12.345" → "345"} (no normalisation, no fixed width);</li>
 *   <li>when there is no usable dot, the integer part is the whole value (dots removed) and
 *       the decimal portion is the literal string {@code "000"}.</li>
 * </ul>
 *
 * <p>This deliberately preserves the legacy quirk that {@code "0"} and {@code "000"} are
 * <b>different</b> wire values — the EFS guide fixes only the max length
 * ({@code data_dcm_amt = number(3)}) and is silent on padding, so the safe behaviour is to
 * emit exactly what the proven legacy batch emits. Whether the gateway strictly requires
 * this form (vs. tolerating {@code "0"}) is an open SIT/Webcash confirmation item; it does
 * not change this implementation, which mirrors legacy.
 *
 * <p>The old refactor helper computed the decimal via
 * {@code amt.subtract(intPart).movePointRight(2).toBigInteger().toString()}, which dropped
 * leading zeros ({@code ".00" → "0"}, {@code ".05" → "5"}) and always used the recomputed
 * integer — diverging from legacy on essentially every request. This class replaces it.
 */
public final class AmountSplitter {

    private AmountSplitter() {
    }

    /** Whole-amount (no decimal point) legacy sentinel for the decimal portion. */
    public static final String WHOLE_AMOUNT_DCM = "000";

    /**
     * Split the amount's string form into {@code [integer, decimalPortion]}, mirroring
     * legacy {@code BigDecPaser}. A {@code null}/blank input is treated as {@code "0"}
     * (→ {@code ["0", "000"]}).
     */
    public static String[] split(String amount) {
        String raw = (amount == null || amount.trim().isEmpty()) ? "0" : amount.trim();
        String bigTmp = raw.replace("-", "");
        int dot = bigTmp.indexOf('.');
        if (dot != -1 && dot < bigTmp.length() - 1) {
            return new String[] {bigTmp.substring(0, dot), bigTmp.substring(dot + 1)};
        }
        return new String[] {bigTmp.replace(".", ""), WHOLE_AMOUNT_DCM};
    }

    /**
     * Split a {@link BigDecimal} amount. The value is rendered with
     * {@link BigDecimal#toPlainString()} first — its scale therefore decides the decimal
     * portion exactly as the legacy DB {@code FILE_AMT}/{@code TRAN_AMT} string form would
     * (a scale-2 sum → {@code "…​.NN"}; a scale-0 sum → the whole-amount {@code "000"} form).
     */
    public static String[] split(BigDecimal amount) {
        return split(amount == null ? "0" : amount.toPlainString());
    }
}
