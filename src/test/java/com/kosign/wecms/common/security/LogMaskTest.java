package com.kosign.wecms.common.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit coverage for {@link LogMask}. Pinned to the cases enumerated in
 * the helper's Javadoc and the OQ-4 logging rule
 * (docs/refactor-wecms/features/settings/firm-banking-settings.md §7.4.1).
 */
class LogMaskTest {

    @Test
    void acct_normalLength_revealsFirst4AndLast4() {
        assertThat(LogMask.acct("100912150384")).isEqualTo("1009****0384");
        assertThat(LogMask.acct("700005285280")).isEqualTo("7000****5280");
    }

    @Test
    void acct_minimalRevealLength_isExactly9Chars() {
        // 9 chars is the smallest length that reveals: 4 + 1 mask + 4
        assertThat(LogMask.acct("123456789")).isEqualTo("1234*6789");
    }

    @Test
    void acct_eightOrFewerChars_fullyMasked() {
        // Short numbers leak too much entropy from a partial reveal —
        // the helper falls back to a fixed mask.
        assertThat(LogMask.acct("12345678")).isEqualTo("***");
        assertThat(LogMask.acct("12345"))   .isEqualTo("***");
        assertThat(LogMask.acct("ab"))      .isEqualTo("***");
    }

    @Test
    void acct_blankInputs_returnFixedMask() {
        assertThat(LogMask.acct(""))    .isEqualTo("***");
        assertThat(LogMask.acct("   ")) .isEqualTo("***");
    }

    @Test
    void acct_null_returnsLiteralNullString() {
        // "null" (not Java null) so log statements never NPE while formatting.
        assertThat(LogMask.acct(null)).isEqualTo("null");
    }

    @Test
    void acct_longInput_padsMaskToFullMiddleLength() {
        String input  = "12345678901234567890";              // 20 chars
        String masked = LogMask.acct(input);
        assertThat(masked).startsWith("1234");
        assertThat(masked).endsWith("7890");
        assertThat(masked).hasSameSizeAs(input);
        assertThat(masked.substring(4, 16)).isEqualTo("************");
    }
}
