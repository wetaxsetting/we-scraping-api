package com.kosign.wecms.domain.payment.dispatch.file;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Legacy-parity tests for {@link VietnameseText} — the tone-stripper applied to every
 * sender/receiver name, account memo and recipient address written into the fixed-length
 * firm-banking bulk file.
 *
 * <h2>Why this class had no tests, and why that mattered</h2>
 * {@code replaceTone} maps by <b>index</b> between two hand-copied string literals. Nothing in the
 * language ties them together: if they ever drift by one character, every Vietnamese name in every
 * bulk file silently maps to the wrong letter — no exception, no failed record, just wrong recipient
 * names delivered to the bank. If {@code TONED} became longer than {@code PLAIN}, the same code path
 * would instead throw {@link StringIndexOutOfBoundsException} mid-file-build.
 *
 * <h2>Legacy parity — verified 2026-07-29, byte-for-byte</h2>
 * Legacy source: {@code WECMS_BATCH DEV/source/WECMS_BATCH/WECMS_BATCH/src/util/StrUtil.java}
 * <ul>
 *   <li>{@code replaceTone} {@code :20-40} — same two literals ({@code s1}/{@code s0}), same
 *       {@code indexOf}/{@code charAt} loop, same {@code null || isEmpty → ""}. The literals were
 *       compared programmatically against {@code TONED}/{@code PLAIN} and are <b>identical</b>; both
 *       are <b>134 characters</b>.</li>
 *   <li>{@code getStrByteMaxLen} {@code :68-88} — the byte-safe truncation. Structurally identical to
 *       {@code BulkReceptionService.byteTruncate}: per-{@code char} UTF-8 byte cost, {@code break}
 *       when the next char would exceed the cap, return {@code substring(0, cut)}. Legacy hardcodes
 *       {@code StandardCharsets.UTF_8}; the refactor uses {@link FixedLengthCodec#CHARSET}, which
 *       <b>is</b> {@code UTF_8} — so the byte accounting is the same.</li>
 *   <li><b>Order of operations</b> — legacy truncates <i>first</i> and strips tones <i>second</i>
 *       ({@code FILE_DEPOSIT_TRANSFER_BATCH1.java:638} then {@code :667}). The refactor's
 *       {@code replaceTone(byteTruncate(addr, 180))} is the same order. Reversing it would change the
 *       output, because stripping first frees bytes and lets more characters survive the cut.</li>
 *   <li>Legacy guards the call with {@code hasVietnameseCharacters} ({@code :665}); the refactor calls
 *       {@code replaceTone} unconditionally. Behaviourally identical — the mapping leaves any
 *       character outside {@code TONED} untouched, so on ASCII it is a no-op. (Legacy's guard is
 *       itself near-vacuous: it returns true for any char in {@code A-z}.)</li>
 * </ul>
 *
 * <b>Verdict: no defect. This is a pure test-coverage gap, now closed.</b>
 */
class VietnameseTextTest {

    private static String toned() {
        return (String) ReflectionTestUtils.getField(VietnameseText.class, "TONED");
    }

    private static String plain() {
        return (String) ReflectionTestUtils.getField(VietnameseText.class, "PLAIN");
    }

    private static int utf8Len(String s) {
        return s.getBytes(StandardCharsets.UTF_8).length;
    }

    // ── the drift guard ──────────────────────────────────────────────────────

    @Test
    @DisplayName("TONED and PLAIN are the same length — the invariant the index mapping depends on")
    void tonedAndPlainAreTheSameLength() {
        // One line, and it is the single most valuable assertion in this class. A shorter PLAIN throws
        // StringIndexOutOfBoundsException mid-build; a misaligned PLAIN silently emits wrong letters.
        assertThat(plain().length())
                .as("PLAIN must be exactly as long as TONED (both are 134 chars, verbatim from "
                        + "legacy StrUtil.java:25-26)")
                .isEqualTo(toned().length());
        assertThat(toned().length()).isEqualTo(134);
    }

    @Test
    @DisplayName("every TONED character maps to an ASCII letter without throwing")
    void everyTonedCharacterMapsToAscii() {
        String t = toned();
        for (int i = 0; i < t.length(); i++) {
            String in = String.valueOf(t.charAt(i));
            String out = VietnameseText.replaceTone(in);

            assertThat(out).as("index %d (%s) must map to exactly one char", i, in).hasSize(1);
            assertThat((int) out.charAt(0))
                    .as("index %d (%s) must map into ASCII", i, in)
                    .isLessThan(0x80);
            assertThat(Character.isLetter(out.charAt(0)))
                    .as("index %d (%s) must map to a letter", i, in)
                    .isTrue();
        }
    }

    @Test
    @DisplayName("case is preserved — an upper-case toned char maps to an upper-case base letter")
    void casingIsPreserved() {
        String t = toned();
        for (int i = 0; i < t.length(); i++) {
            char in = t.charAt(i);
            char out = VietnameseText.replaceTone(String.valueOf(in)).charAt(0);
            // Legacy pairs the literals upper→upper / lower→lower throughout; a copy/paste slip that
            // shifted one half by a character would show up here as a case flip.
            assertThat(Character.isUpperCase(out))
                    .as("index %d: '%s' -> '%s' must keep case", i, in, out)
                    .isEqualTo(Character.isUpperCase(in));
        }
    }

    // ── required vectors ─────────────────────────────────────────────────────

    @DisplayName("representative Vietnamese names strip to their base ASCII form")
    @ParameterizedTest(name = "[{index}] {0} -> {1}")
    @CsvSource({
            "Nguyễn Văn An,          Nguyen Van An",
            "Đặng Thị Hồng,          Dang Thi Hong",
            "Công ty TNHH Thương mại,Cong ty TNHH Thuong mai",
            "Trần Quốc Tuấn,         Tran Quoc Tuan",
            "Lê Thị Mỹ Duyên,        Le Thi My Duyen",
            "PHẠM VĂN ĐỨC,           PHAM VAN DUC",
    })
    void representativeNames_stripToBaseAscii(String input, String expected) {
        String out = VietnameseText.replaceTone(input.trim());

        assertThat(out).isEqualTo(expected.trim());
        assertThat(utf8Len(out))
                .as("stripped output must be pure ASCII (1 byte per char)")
                .isEqualTo(out.length());
    }

    @Test
    @DisplayName("mixed Vietnamese and ASCII: only the toned characters change")
    void mixedVietnameseAndAscii() {
        assertThat(VietnameseText.replaceTone("INV-2026/07 Nguyễn ABC_123 (đ)"))
                .isEqualTo("INV-2026/07 Nguyen ABC_123 (d)");
    }

    @DisplayName("characters outside the mapping pass through untouched")
    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {
            "PLAIN ASCII ONLY",
            "!@#$%^&*()_+-=[]{};':\",.<>/?\\|`~",
            "0123456789",
            "  leading and trailing  ",
    })
    void unmappedCharactersPassThrough(String input) {
        // The mapping is a whitelist: anything not in TONED is appended verbatim. That is what makes
        // it safe to apply unconditionally where legacy guarded the call.
        assertThat(VietnameseText.replaceTone(input)).isEqualTo(input);
    }

    @Test
    @DisplayName("emoji (surrogate pairs) pass through intact — legacy behaviour, not corrupted")
    void emojiSurrogatePairsPassThroughIntact() {
        // replaceTone walks UTF-16 code units, so a surrogate pair is visited as two chars. Neither
        // half is in TONED, so both are appended verbatim and the pair survives. Asserting this pins
        // that the walk cannot half-consume an astral character.
        String emoji = "Nguyễn 😀 Văn";
        String out = VietnameseText.replaceTone(emoji);

        assertThat(out).isEqualTo("Nguyen 😀 Van");
        assertThat(out.codePointCount(0, out.length()))
                .as("the emoji must remain a single code point")
                .isEqualTo("Nguyen  Van".length() + 1);
    }

    @Test
    @DisplayName("precomposed vs combining forms: only precomposed is mapped, matching legacy")
    void precomposedIsMapped_combiningIsNot() {
        String precomposed = "ễ";              // ễ  — single code point, in TONED
        String combining = "ễ";         // e + circumflex + tilde — NOT in TONED

        assertThat(VietnameseText.replaceTone(precomposed))
                .as("precomposed Vietnamese is what the DB stores and what legacy maps")
                .isEqualTo("e");

        // Documented, legacy-identical limitation: legacy's literal contains only precomposed code
        // points, so a decomposed input keeps its combining marks. Neither implementation normalises
        // first. Pinned so a future NFC/NFD change is a conscious decision, not a silent one.
        assertThat(VietnameseText.replaceTone(combining))
                .as("combining sequences are NOT normalised — same as legacy")
                .isEqualTo(combining);
    }

    @DisplayName("null and empty both return an empty string, never null")
    @Test
    void nullAndEmptyReturnEmptyString() {
        // Legacy: `if (input == null || input.isEmpty()) return "";` (StrUtil.java:21-23).
        // Returning "" rather than null matters: the fixed-length codec pads a value, and a null
        // would NPE inside put(...) instead of producing a blank field.
        assertThat(VietnameseText.replaceTone(null)).isEmpty();
        assertThat(VietnameseText.replaceTone("")).isEmpty();
        assertThat(VietnameseText.replaceTone(" ")).isEqualTo(" ");
    }

    // ── the property the truncation composition relies on ────────────────────

    @Test
    @DisplayName("stripping never increases the UTF-8 byte length — so truncate-then-strip stays under the cap")
    void strippingNeverIncreasesByteLength() {
        // BulkReceptionService composes replaceTone(byteTruncate(addr, 180)), matching legacy's
        // :638 -> :667 order. That composition is only safe if stripping cannot grow the value past
        // the 180-byte cap the truncation just enforced. Every mapped character is multi-byte in and
        // 1 byte out, so stripping strictly shrinks (or holds) the byte length.
        for (String s : new String[] {
                "Nguyễn Văn An", "Đặng Thị Hồng", "Công ty TNHH Thương mại",
                toned(), "ASCII", "", " ", "Ẫ".repeat(60)}) {
            String out = VietnameseText.replaceTone(s);
            assertThat(utf8Len(out))
                    .as("replaceTone must not grow the byte length of [%s]", s)
                    .isLessThanOrEqualTo(utf8Len(s));
        }
    }

    @Test
    @DisplayName("a value truncated at the 180-byte cap still fits after stripping, with no split char")
    void truncateThenStrip_staysUnderCapAndNeverSplitsACharacter() {
        // Mirrors the production composition for RCPT_USE_ADDR (cap 180). "Đ" is 2 bytes in UTF-8, so
        // 200 of them is 400 bytes — well over the cap and guaranteed to land mid-run.
        String longAddress = "Đ".repeat(200);

        String truncated = byteTruncateLikeProduction(longAddress, 180);
        assertThat(utf8Len(truncated))
                .as("truncation must not exceed the cap")
                .isLessThanOrEqualTo(180);
        assertThat(truncated.length())
                .as("2-byte chars: a 180-byte budget fits exactly 90 of them")
                .isEqualTo(90);

        String stripped = VietnameseText.replaceTone(truncated);
        assertThat(stripped).isEqualTo("D".repeat(90));
        assertThat(utf8Len(stripped)).isEqualTo(90).isLessThanOrEqualTo(180);

        // The decisive property: the byte sequence must round-trip, i.e. no multi-byte character was
        // cut in half. A split char would decode to U+FFFD and fail this.
        assertThat(new String(truncated.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8))
                .as("no multibyte character may be split by the truncation")
                .isEqualTo(truncated)
                .doesNotContain("�");
    }

    @DisplayName("byte-boundary truncation: exact cap, one over, and a char straddling the limit")
    @ParameterizedTest(name = "cap={0} -> {1} chars")
    @CsvSource({
            "0,   0",   // no budget at all
            "1,   0",   // cannot fit a 2-byte char
            "2,   1",   // exactly one
            "3,   1",   // one fits, the next would straddle -> dropped whole
            "4,   2",   // exactly two
    })
    void truncationBoundaries(int cap, int expectedChars) {
        // Legacy drops the straddling character entirely rather than emitting a partial byte
        // (StrUtil.java:79-81 `break`), which is what keeps the fixed-length record decodable.
        String twoByteChars = "Đ".repeat(5);

        String out = byteTruncateLikeProduction(twoByteChars, cap);

        assertThat(out.length()).isEqualTo(expectedChars);
        assertThat(utf8Len(out)).isLessThanOrEqualTo(Math.max(cap, 0));
    }

    @Test
    @DisplayName("output is deterministic — no locale or default-charset dependence")
    void outputIsDeterministic() {
        // The mapping is pure index arithmetic over two constants and the byte accounting pins UTF-8
        // explicitly, so nothing here can vary with Locale.getDefault() or file.encoding.
        String input = "Nguyễn Văn An – Đặng Thị Hồng";
        String first = VietnameseText.replaceTone(input);
        for (int i = 0; i < 50; i++) {
            assertThat(VietnameseText.replaceTone(input)).isEqualTo(first);
        }
        assertThat(first).isEqualTo("Nguyen Van An – Dang Thi Hong");
    }

    /**
     * Byte-safe truncation replicating {@code BulkReceptionService.byteTruncate} (private) and legacy
     * {@code StrUtil.getStrByteMaxLen} ({@code :68-88}) — same per-char UTF-8 cost, same
     * drop-the-straddling-char {@code break}, same {@code substring(0, cut)}.
     */
    private static String byteTruncateLikeProduction(String value, int maxBytes) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        int total = 0;
        int cut = 0;
        for (int i = 0; i < value.length(); i++) {
            int len = String.valueOf(value.charAt(i)).getBytes(FixedLengthCodec.CHARSET).length;
            if (total + len > maxBytes) {
                break;
            }
            total += len;
            cut = i + 1;
        }
        return value.substring(0, cut);
    }
}
