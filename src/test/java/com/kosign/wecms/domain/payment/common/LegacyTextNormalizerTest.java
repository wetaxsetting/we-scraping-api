package com.kosign.wecms.domain.payment.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link LegacyTextNormalizer} (audit task-44508 F1/F2). Verifies
 * the verbatim legacy behaviour: CR/LF stripping (tabs preserved), Vietnamese
 * tone removal, other characters preserved (never replaced with spaces), and
 * the combined {@code normalize} used by the save-path guard.
 */
class LegacyTextNormalizerTest {

    @Test
    void stripCrLf_removesCarriageReturnAndNewline_keepsTabs() {
        assertThat(LegacyTextNormalizer.stripCrLf("a\r\nb")).isEqualTo("ab");
        assertThat(LegacyTextNormalizer.stripCrLf("a\nb\rc")).isEqualTo("abc");
        assertThat(LegacyTextNormalizer.stripCrLf("a\tb")).isEqualTo("a\tb"); // tab kept
        assertThat(LegacyTextNormalizer.stripCrLf(null)).isNull();
    }

    @Test
    void hasVietnameseCharacters_detectsAccentedLatin() {
        assertThat(LegacyTextNormalizer.hasVietnameseCharacters("Nguyễn")).isTrue();  // ễ U+1EC5
        assertThat(LegacyTextNormalizer.hasVietnameseCharacters("Đức")).isTrue();     // Đ U+0110, ứ U+1EE9
        assertThat(LegacyTextNormalizer.hasVietnameseCharacters("tháng")).isTrue();   // á U+00E1
        assertThat(LegacyTextNormalizer.hasVietnameseCharacters("ABC 123")).isFalse();
        assertThat(LegacyTextNormalizer.hasVietnameseCharacters("")).isFalse();
        assertThat(LegacyTextNormalizer.hasVietnameseCharacters(null)).isFalse();
        // Legacy quirk faithfully ported: the range check MISSES bare ơ/ư
        // (U+01A1 / U+01B0 sit in the gap between its ranges), so a string whose
        // only accents are ơ/ư is NOT detected by the guard on its own. When any
        // other in-range accent is present, replaceTone still maps ơ/ư too (see
        // normalize test).
        assertThat(LegacyTextNormalizer.hasVietnameseCharacters("ươ")).isFalse();
    }

    @Test
    void replaceTone_transliteratesVietnameseToAscii() {
        assertThat(LegacyTextNormalizer.replaceTone("Nguyễn Văn Đức")).isEqualTo("Nguyen Van Duc");
        assertThat(LegacyTextNormalizer.replaceTone("lương tháng 6")).isEqualTo("luong thang 6");
    }

    @Test
    void stripVietnameseTone_leavesNonVietnameseUntouched() {
        assertThat(LegacyTextNormalizer.stripVietnameseTone("홍길동")).isEqualTo("홍길동"); // Korean kept
        assertThat(LegacyTextNormalizer.stripVietnameseTone("A&B Co")).isEqualTo("A&B Co"); // '&' kept
        assertThat(LegacyTextNormalizer.stripVietnameseTone(null)).isNull();
    }

    @Test
    void normalize_stripsCrLfThenTone_preservesOtherSpecialChars() {
        // Embedded newline stripped, accents removed, '@' and '#' PRESERVED
        // (never converted to spaces — that legacy behaviour was removed).
        assertThat(LegacyTextNormalizer.normalize("Thanh toán\nlương @#"))
                .isEqualTo("Thanh toanluong @#");
        assertThat(LegacyTextNormalizer.normalize(null)).isNull();
    }
}
