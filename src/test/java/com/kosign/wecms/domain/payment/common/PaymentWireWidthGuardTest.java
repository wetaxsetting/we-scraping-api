package com.kosign.wecms.domain.payment.common;

import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.domain.payment.dispatch.file.DepositData;
import com.kosign.wecms.domain.payment.dispatch.file.DepositHeader;
import com.kosign.wecms.domain.payment.dispatch.file.DepositTrailer;
import com.kosign.wecms.domain.payment.dispatch.file.FixedLengthCodec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * C-14 — entry-layer guard for the four fixed-length wire slots.
 *
 * <p>The property that matters is <b>agreement with the codec</b>: anything this guard accepts,
 * {@link FixedLengthCodec} must also accept. {@link #guardAndCodecAgree_onEveryBoundary()} asserts that
 * directly rather than trusting two independent width constants to stay in step.
 */
class PaymentWireWidthGuardTest {

    private final PaymentWireWidthGuard guard = WireWidthGuards.real();

    // ── the reachable failure: multibyte surviving tone-stripping ────────────

    @Test
    @DisplayName("Korean text is rejected as non-Latin, not as too long")
    void koreanIsRejectedAsNonLatin() {
        // The primary reachable path. 60 Hangul syllables = 180 UTF-8 bytes, which is both non-Latin
        // AND over the 150-byte receiverName slot — so this also pins the ORDER of the two rules.
        // Reporting it as "too long" would be true and useless: shortening Korean does not make it
        // sendable.
        String korean = "김".repeat(60);
        Optional<String> msg = guard.check(WireField.RECEIVER_NAME, korean);

        assertThat(msg).isPresent();
        assertThat(msg.get())
                .contains("cannot contain non-Latin text")
                .doesNotContain("too long");
    }

    @Test
    @DisplayName("a SHORT Korean name is still rejected — width is not the point")
    void shortKoreanIsStillRejected() {
        // 3 syllables = 9 bytes, far inside every slot. It is rejected because the channel cannot
        // carry it at all, which is the distinction the error message exists to make.
        assertThat(guard.check(WireField.RECEIVER_NAME, "김철수"))
                .isPresent()
                .get().asString().contains("cannot contain non-Latin text");
    }

    @Test
    @DisplayName("the message names the offending character and its position")
    void messageNamesTheOffendingCharacter() {
        // A 200-character memo with one CJK character in the middle is unfindable by eye; the message
        // has to point at it.
        String memo = "PAYMENT FOR INVOICE ".repeat(5) + "円" + " THANK YOU";
        String msg = guard.check(WireField.DEPOSIT_ACCOUNT_MEMO, memo).orElseThrow();

        assertThat(msg).contains("円");
        assertThat(msg).contains(String.valueOf(memo.indexOf('円') + 1));
    }

    // ── NFC: the secondary path, and the false positive it removes ───────────

    @Test
    @DisplayName("NFD-decomposed Vietnamese passes — NFC makes tone-stripping effective")
    void decomposedVietnamesePasses() {
        String precomposed = "Nguyễn Văn Đức";
        String decomposed = Normalizer.normalize(precomposed, Normalizer.Form.NFD);

        // Sanity: the two really are different byte sequences, and the decomposed form really is wider.
        assertThat(decomposed).isNotEqualTo(precomposed);
        assertThat(utf8(decomposed)).isGreaterThan(utf8(precomposed));

        // Without the NFC step this would be a false positive: replaceTone maps precomposed code
        // points only, so the combining marks would survive and read as non-Latin.
        assertThat(guard.check(WireField.RECEIVER_NAME, decomposed))
                .as("a decomposed Vietnamese name is legitimate input and must not be rejected")
                .isEmpty();
        assertThat(guard.check(WireField.RECEIVER_NAME, precomposed)).isEmpty();
    }

    @Test
    @DisplayName("NFC does NOT rescue Korean — the byte-width/non-Latin check is the real guard")
    void nfcDoesNotRescueKorean() {
        // Stated explicitly because it is the limit of what normalization can do: NFC composes Hangul
        // jamo into syllables, which is still 3 bytes per syllable and still non-Latin.
        String decomposedKorean = Normalizer.normalize("김철수", Normalizer.Form.NFD);
        assertThat(guard.check(WireField.RECEIVER_NAME, decomposedKorean)).isPresent();
        assertThat(guard.check(WireField.RECEIVER_NAME, "김철수")).isPresent();
    }

    // ── the "too long" branch ────────────────────────────────────────────────

    @Test
    @DisplayName("plain Latin over the slot is reported as too long, with the byte counts")
    void latinOverTheSlotIsReportedAsTooLong() {
        String msg = guard.check(WireField.RECEIVER_NAME, "A".repeat(151)).orElseThrow();

        assertThat(msg)
                .contains("too long")
                .contains("151")   // bytes used
                .contains("150");  // allowed
        assertThat(msg).doesNotContain("non-Latin");
    }

    @Test
    @DisplayName("exactly at the slot width passes; one byte over fails — per field")
    void exactBoundaryPerField() {
        for (WireField f : WireField.values()) {
            assertThat(guard.check(f, "A".repeat(f.wireBytes())))
                    .as("%s: exactly %d bytes must pass", f, f.wireBytes())
                    .isEmpty();
            assertThat(guard.check(f, "A".repeat(f.wireBytes() + 1)))
                    .as("%s: %d bytes must fail", f, f.wireBytes() + 1)
                    .isPresent();
        }
    }

    @Test
    @DisplayName("tone-stripping is credited: 150 accented characters fit the 150-byte slot")
    void toneStrippingIsCreditedTowardsTheBudget() {
        // 150 precomposed "ế" is 450 UTF-8 bytes raw, but strips to 150 ASCII bytes — so measuring the
        // RAW value would reject a perfectly valid Vietnamese name. The guard must model the pipeline,
        // not the input.
        String accented = "ế".repeat(150);
        assertThat(utf8(accented)).isEqualTo(450);

        assertThat(guard.check(WireField.RECEIVER_NAME, accented)).isEmpty();
        assertThat(guard.check(WireField.RECEIVER_NAME, "ế".repeat(151))).isPresent();
    }

    @Test
    @DisplayName("the memo slots are 210 bytes — wider than the varchar(150) column that feeds them")
    void memoSlotsAreWiderThanTheirColumn() {
        // Documents why the column width cannot stand in for the wire width, in either direction.
        assertThat(WireField.DEPOSIT_ACCOUNT_MEMO.wireBytes()).isEqualTo(210);
        assertThat(WireField.WITHDRAWAL_ACCOUNT_MEMO.wireBytes()).isEqualTo(210);
        assertThat(WireField.RECEIVER_NAME.wireBytes()).isEqualTo(150);
        assertThat(WireField.SENDER_NAME.wireBytes()).isEqualTo(150);

        // 200 ASCII chars: over the varchar(150) column, under the 210-byte slot. The guard passes it
        // (correctly — it guards the wire, not the column); the column is the DB's job.
        assertThat(guard.check(WireField.DEPOSIT_ACCOUNT_MEMO, "A".repeat(200))).isEmpty();
    }

    // ── null / blank / passthrough ───────────────────────────────────────────

    @DisplayName("null and blank always pass — required-field checks belong to the caller")
    @ParameterizedTest(name = "{0}")
    @EnumSource(WireField.class)
    void nullAndBlankPass(WireField field) {
        assertThat(guard.check(field, null)).isEmpty();
        assertThat(guard.check(field, "")).isEmpty();
        assertThat(guard.check(field, "   ")).isEmpty();
    }

    @Test
    @DisplayName("idempotent: an already-normalized value gives the same verdict")
    void idempotentOverNormalization() {
        // Callers pass raw values in some places and LegacyTextNormalizer.normalize(...) output in
        // others; both must behave identically or the save path and the preview would disagree.
        String raw = "Nguyễn Văn Đức";
        assertThat(guard.check(WireField.RECEIVER_NAME, raw))
                .isEqualTo(guard.check(WireField.RECEIVER_NAME, LegacyTextNormalizer.normalize(raw)));
    }

    @Test
    @DisplayName("embedded CR/LF is stripped before measuring, matching the persistence pipeline")
    void crLfIsStrippedBeforeMeasuring() {
        // F2 strips CR/LF at cell read, so counting them would over-measure by the number of newlines.
        assertThat(guard.check(WireField.DEPOSIT_ACCOUNT_MEMO, "A".repeat(210) + "\r\n")).isEmpty();
    }

    // ── assertClean (save path) ──────────────────────────────────────────────

    @Test
    @DisplayName("assertClean throws WBE_PAYMENT_WIRE_FIELD carrying the composed message")
    void assertCleanThrowsWithTheComposedMessage() {
        assertThatThrownBy(() -> guard.assertClean(List.of(
                PaymentWireWidthGuard.field(WireField.RECEIVER_NAME, "김".repeat(60)))))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.getCode()).isEqualTo("WBE_PAYMENT_WIRE_FIELD");
                    assertThat(be.getArgs()).hasSize(1);
                    assertThat(String.valueOf(be.getArgs()[0])).contains("cannot contain non-Latin text");
                });
    }

    @Test
    @DisplayName("assertClean passes clean values and tolerates a null list")
    void assertCleanPassesCleanValues() {
        assertThatCode(() -> guard.assertClean(List.of(
                PaymentWireWidthGuard.field(WireField.RECEIVER_NAME, "NGUYEN VAN A"),
                PaymentWireWidthGuard.field(WireField.DEPOSIT_ACCOUNT_MEMO, "Thanh toán lương")))
        ).doesNotThrowAnyException();
        assertThatCode(() -> guard.assertClean(null)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("assertClean fails on the FIRST offending field, so the message is specific")
    void assertCleanFailsOnTheFirstOffender() {
        assertThatThrownBy(() -> guard.assertClean(List.of(
                PaymentWireWidthGuard.field(WireField.DEPOSIT_ACCOUNT_MEMO, "A".repeat(300)),
                PaymentWireWidthGuard.field(WireField.RECEIVER_NAME, "김철수"))))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(String.valueOf(((BusinessException) e).getArgs()[0]))
                        .contains("too long"));
    }

    // ── the invariant that ties the guard to the codec ───────────────────────

    @Test
    @DisplayName("anything the guard accepts, FixedLengthCodec also accepts — at every boundary")
    void guardAndCodecAgree_onEveryBoundary() {
        // The decisive test. Two independent notions of "150" and "210" exist (this enum and
        // FixedLengthCodec.encodeData); if they ever diverge, the guard would green-light a value that
        // aborts the dispatch. Driving the real codec is the only way to assert they agree.
        FixedLengthCodec codec = new FixedLengthCodec();

        for (int nameLen : new int[] {0, 1, 149, 150}) {
            for (int memoLen : new int[] {0, 1, 209, 210}) {
                String name = "A".repeat(nameLen);
                String memo = "B".repeat(memoLen);

                // Precondition: the guard accepts this shape.
                assertThat(guard.check(WireField.SENDER_NAME, name)).isEmpty();
                assertThat(guard.check(WireField.RECEIVER_NAME, name)).isEmpty();
                assertThat(guard.check(WireField.DEPOSIT_ACCOUNT_MEMO, memo)).isEmpty();
                assertThat(guard.check(WireField.WITHDRAWAL_ACCOUNT_MEMO, memo)).isEmpty();

                assertThatCode(() -> codec.encode(header(), List.of(line(name, memo)), trailer()))
                        .as("codec must accept name=%d bytes, memo=%d bytes", nameLen, memoLen)
                        .doesNotThrowAnyException();
            }
        }
    }

    @Test
    @DisplayName("one byte past what the guard allows, the codec throws — the guard is not slack")
    void oneBytePastTheGuard_theCodecThrows() {
        // The other half of the agreement: the guard's limit is the codec's limit, not a value
        // conservatively below it. If the guard were slack, over-width values would reach dispatch.
        FixedLengthCodec codec = new FixedLengthCodec();

        assertThat(guard.check(WireField.RECEIVER_NAME, "A".repeat(151))).isPresent();
        assertThatThrownBy(() -> codec.encode(header(), List.of(line("A".repeat(151), "")), trailer()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exceeds fixed width 150");

        assertThat(guard.check(WireField.DEPOSIT_ACCOUNT_MEMO, "B".repeat(211))).isPresent();
        assertThatThrownBy(() -> codec.encode(header(), List.of(line("", "B".repeat(211))), trailer()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exceeds fixed width 210");
    }

    /* ───────────────────────────── fixtures ───────────────────────────── */

    private static int utf8(String s) {
        return s.getBytes(StandardCharsets.UTF_8).length;
    }

    private static DepositHeader header() {
        return new DepositHeader("970457", "WRB001", "20260730", "100001020691", "0", "1", "001");
    }

    private static DepositTrailer trailer() {
        return new DepositTrailer("1", "1000", "0", "0", "0", "0");
    }

    private static DepositData line(String name, String memo) {
        return new DepositData("1", "0681000001", "VND", "1000", "T", "N", "Y", "01203001",
                name, name, memo, memo, "", "", "VN", "VND", "BEN", "");
    }
}
