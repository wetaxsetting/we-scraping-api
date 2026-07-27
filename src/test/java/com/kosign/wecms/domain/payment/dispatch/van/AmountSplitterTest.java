package com.kosign.wecms.domain.payment.dispatch.van;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies {@link AmountSplitter} reproduces legacy {@code StrUtil.BigDecPaser} exactly
 * (audit finding H-6). Covers the cases enumerated in the parity-fix request: integer VND,
 * 0 / 1 / 2 / 3 decimal digits, small fractional, and the whole-amount {@code "000"} form —
 * and confirms the previous refactor behaviour ({@code ".00" → "0"}) is gone.
 */
class AmountSplitterTest {

    @Test
    void wholeAmountString_noDot_emitsLegacyTripleZero() {
        // Legacy BigDecPaser else-branch: no '.' → dcm = "000".
        assertThat(AmountSplitter.split("19886030287"))
                .containsExactly("19886030287", "000");
    }

    @Test
    void twoDecimalDigits_keepsLiteralIncludingLeadingZero() {
        // The exact case the old refactor got wrong (".00" → "0"): must be "00".
        assertThat(AmountSplitter.split("15.00")).containsExactly("15", "00");
        assertThat(AmountSplitter.split("15.05")).containsExactly("15", "05");
        assertThat(AmountSplitter.split("1234.50")).containsExactly("1234", "50");
    }

    @Test
    void oneDecimalDigit_keptVerbatim() {
        assertThat(AmountSplitter.split("15.5")).containsExactly("15", "5");
    }

    @Test
    void threeDecimalDigits_keptVerbatim() {
        assertThat(AmountSplitter.split("12.345")).containsExactly("12", "345");
    }

    @Test
    void smallFractionalValue_keepsLeadingZero() {
        assertThat(AmountSplitter.split("0.05")).containsExactly("0", "05");
    }

    @Test
    void trailingDot_treatedAsWholeAmount() {
        // Legacy guard `dot < len-1`: a trailing dot is NOT a usable decimal → "000".
        assertThat(AmountSplitter.split("15.")).containsExactly("15", "000");
    }

    @Test
    void negativeSign_stripped() {
        assertThat(AmountSplitter.split("-15.00")).containsExactly("15", "00");
    }

    @Test
    void nullAndBlank_treatedAsZeroWhole() {
        assertThat(AmountSplitter.split((String) null)).containsExactly("0", "000");
        assertThat(AmountSplitter.split("   ")).containsExactly("0", "000");
    }

    @Test
    void bigDecimal_scaleZero_isWholeAmount() {
        // A scale-0 sum (integer VND) → whole-amount "000", matching legacy DB integer FILE_AMT.
        assertThat(AmountSplitter.split(new BigDecimal("31"))).containsExactly("31", "000");
    }

    @Test
    void bigDecimal_scaleTwo_keepsTwoDigits() {
        // A scale-2 sum renders "31.00" via toPlainString → dcm "00".
        assertThat(AmountSplitter.split(new BigDecimal("31.00"))).containsExactly("31", "00");
        assertThat(AmountSplitter.split(new BigDecimal("0.05"))).containsExactly("0", "05");
    }

    @Test
    void bigDecimal_null_isZeroWhole() {
        assertThat(AmountSplitter.split((BigDecimal) null)).containsExactly("0", "000");
    }
}
