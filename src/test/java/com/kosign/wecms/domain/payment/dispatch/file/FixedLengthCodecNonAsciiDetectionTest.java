package com.kosign.wecms.domain.payment.dispatch.file;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * C-13 — the codec must <b>detect and report</b> non-ASCII bytes reaching the wire, without rejecting.
 *
 * <h2>The gap this closes</h2>
 * {@code FixedLengthCodec.put} checked byte <b>width</b> only. A value of {@code <= width} bytes
 * containing multibyte characters passed silently: {@code 한국전자} is 12 bytes and clears the 150-byte
 * {@code senderName} slot. {@code PaymentWireWidthGuard} rejects non-Latin text, but only for values that
 * go through entry validation — server-derived ({@code ACCT_MSTR.ACCT_NM} → {@code SENDER_NM}),
 * admin-written, ERP-passthrough and legacy-JEX-written values bypass it. The codec is where they all
 * converge, and nothing was watching.
 *
 * <h2>Deliberately not a rejection</h2>
 * The bank's accepted charset is unconfirmed (K3 / C-13). The UAT samples prove real files have been
 * ASCII, not that non-ASCII is refused — and legacy encoded with the platform-default charset, so if PROD
 * {@code file.encoding} was {@code US-ASCII} the bank may never have received a multibyte byte at all.
 * These tests therefore pin that the file is <b>still produced</b> and <b>byte-identical</b>, and that
 * only the reporting changes.
 */
class FixedLengthCodecNonAsciiDetectionTest {

    private final FixedLengthCodec codec = new FixedLengthCodec();

    // ── the negative case: clean files must stay silent ──────────────────────

    @Test
    @DisplayName("an ASCII-only file reports nothing")
    void asciiOnlyFileReportsNothing() {
        List<FixedLengthCodec.NonAsciiField> found = new ArrayList<>();

        codec.encode(header(), List.of(line("NGUYEN VAN A", "NGUYEN VAN B", "PAYMENT", "PAYMENT")),
                trailer(), found);

        // The signal only means something if it is silent on healthy data — every real UAT sample is
        // pure ASCII, so any finding at all is meant to be actionable.
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("tone-stripped Vietnamese reports nothing — the normal path stays quiet")
    void toneStrippedVietnameseReportsNothing() {
        // What the dispatch path actually produces: replaceTone has already run.
        String stripped = VietnameseText.replaceTone("Nguyễn Văn Đức");
        List<FixedLengthCodec.NonAsciiField> found = new ArrayList<>();

        codec.encode(header(), List.of(line(stripped, stripped, "Thanh toan luong", "Chi phi")),
                trailer(), found);

        assertThat(stripped).isEqualTo("Nguyen Van Duc");
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("padding and the filler never trip the detector")
    void paddingAndFillerNeverTrip() {
        // The 919/907-byte fillers and all the space/zero padding are ASCII by construction; a detector
        // that flagged them would be useless. Empty-everything is the sharpest form of this check.
        List<FixedLengthCodec.NonAsciiField> found = new ArrayList<>();

        codec.encode(header(), List.of(line("", "", "", "")), trailer(), found);

        assertThat(found).isEmpty();
    }

    // ── the positive case: exactly one finding, correctly attributed ─────────

    @Test
    @DisplayName("one non-ASCII field produces exactly one finding, naming field / offset / byte count")
    void oneNonAsciiFieldProducesExactlyOneFinding() {
        List<FixedLengthCodec.NonAsciiField> found = new ArrayList<>();

        // 한국전자 = 4 CJK chars = 12 UTF-8 bytes — comfortably INSIDE the 150-byte slot, which is
        // precisely why the width check could never see it.
        codec.encode(header(), List.of(line("한국전자", "NGUYEN VAN B", "PAYMENT", "PAYMENT")),
                trailer(), found);

        assertThat(found).hasSize(1);
        FixedLengthCodec.NonAsciiField f = found.get(0);
        assertThat(f.recordType()).isEqualTo("D");
        assertThat(f.recordIndex()).isEqualTo(1);          // H is 0, this D is 1
        assertThat(f.fieldName()).isEqualTo("senderName");
        assertThat(f.offset()).isEqualTo(79);              // D-record senderName offset
        assertThat(f.nonAsciiBytes()).isEqualTo(12);       // all 12 bytes are > 0x7F
        assertThat(f.totalBytes()).isEqualTo(12);
    }

    @Test
    @DisplayName("the offset matches the documented D-record layout for each of the four text slots")
    void offsetsMatchTheDocumentedLayout() {
        List<FixedLengthCodec.NonAsciiField> found = new ArrayList<>();

        codec.encode(header(), List.of(line("김", "김", "김", "김")), trailer(), found);

        // Cross-checks the scanner's offset arithmetic against decodeData's slice offsets — if the two
        // ever disagree, an operator would be sent to the wrong byte range.
        assertThat(found).extracting(FixedLengthCodec.NonAsciiField::fieldName,
                        FixedLengthCodec.NonAsciiField::offset)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("senderName", 79),
                        org.assertj.core.groups.Tuple.tuple("receiverName", 229),
                        org.assertj.core.groups.Tuple.tuple("depositAccountMemo", 379),
                        org.assertj.core.groups.Tuple.tuple("withdrawalAccountMemo", 589));
    }

    @Test
    @DisplayName("mixed ASCII + non-ASCII counts only the offending bytes")
    void mixedContentCountsOnlyOffendingBytes() {
        List<FixedLengthCodec.NonAsciiField> found = new ArrayList<>();

        // "INV 円" → 'I','N','V',' ' = 4 ASCII + 円 = 3 bytes.
        codec.encode(header(), List.of(line("ASCII ONLY", "ASCII ONLY", "INV 円", "PAYMENT")),
                trailer(), found);

        assertThat(found).hasSize(1);
        assertThat(found.get(0).fieldName()).isEqualTo("depositAccountMemo");
        assertThat(found.get(0).nonAsciiBytes()).isEqualTo(3);
        assertThat(found.get(0).totalBytes()).isEqualTo(7);
    }

    @Test
    @DisplayName("findings are per field per record, so a multi-line file attributes each one")
    void findingsAreAttributedPerRecord() {
        List<FixedLengthCodec.NonAsciiField> found = new ArrayList<>();

        codec.encode(header(), List.of(
                        line("CLEAN", "CLEAN", "CLEAN", "CLEAN"),
                        line("한국", "CLEAN", "CLEAN", "CLEAN"),
                        line("CLEAN", "CLEAN", "円", "CLEAN")),
                trailer(), found);

        // recordIndex is what tells the operator WHICH line of a 500-row file to look at.
        assertThat(found).hasSize(2);
        assertThat(found).extracting(FixedLengthCodec.NonAsciiField::recordIndex,
                        FixedLengthCodec.NonAsciiField::fieldName)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(2, "senderName"),
                        org.assertj.core.groups.Tuple.tuple(3, "depositAccountMemo"));
    }

    @Test
    @DisplayName("the header and trailer are scanned too, not just data lines")
    void headerAndTrailerAreScanned() {
        List<FixedLengthCodec.NonAsciiField> found = new ArrayList<>();

        // Not a realistic value, but the scan must not be data-record-only: a corrupted conf row could
        // put a multibyte byte in institutionCode.
        DepositHeader h = new DepositHeader("970457", "우리은행", "20260730", "100001020691", "0", "1", "001");
        codec.encode(h, List.of(line("CLEAN", "CLEAN", "CLEAN", "CLEAN")), trailer(), found);

        assertThat(found).hasSize(1);
        assertThat(found.get(0).recordType()).isEqualTo("H");
        assertThat(found.get(0).fieldName()).isEqualTo("institutionCode");
    }

    // ── the invariant: detection must not change the bytes or the outcome ────

    @Test
    @DisplayName("detection does NOT reject — the file is still built, still 1000 bytes per record")
    void detectionDoesNotReject() {
        // The whole premise of C-13: observe, do not change what goes on the wire, until the bank's
        // charset is confirmed. A throw here would block payments on an unproven assumption.
        assertThatCode(() -> codec.encode(header(),
                List.of(line("한국전자", "김철수", "円", "円")), trailer(), new ArrayList<>()))
                .doesNotThrowAnyException();

        byte[] bytes = codec.encode(header(),
                List.of(line("한국전자", "김철수", "円", "円")), trailer(), new ArrayList<>());
        assertThat(bytes).hasSize(3 * 1000);
    }

    @Test
    @DisplayName("the emitted bytes are identical with and without a sink")
    void bytesAreIdenticalWithAndWithoutASink() {
        // Guards against the scanner accidentally consuming or mutating the encoded field bytes.
        DepositData d = line("한국전자", "김철수", "円", "PAYMENT");

        byte[] withSink = codec.encode(header(), List.of(d), trailer(), new ArrayList<>());
        byte[] withoutSink = codec.encode(header(), List.of(d), trailer(), null);
        byte[] legacyArity = codec.encode(header(), List.of(d), trailer());

        assertThat(withSink).isEqualTo(withoutSink).isEqualTo(legacyArity);
    }

    @Test
    @DisplayName("a null sink is safe — the 3-arg overload still logs but collects nothing")
    void nullSinkIsSafe() {
        // Every pre-existing caller and test uses the 3-arg form; it must not NPE now that a scanner
        // runs on every field.
        assertThatCode(() -> codec.encode(header(),
                List.of(line("한국전자", "CLEAN", "CLEAN", "CLEAN")), trailer()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("over-width still throws — width and charset are handled asymmetrically on purpose")
    void overWidthStillThrows() {
        // Over-width corrupts record alignment (provably wrong → throw); non-ASCII yields a
        // structurally valid record whose acceptability is unconfirmed (→ warn). Pinning both together
        // documents that the asymmetry is intentional.
        assertThatCode(() -> codec.encode(header(),
                List.of(line("A".repeat(151), "CLEAN", "CLEAN", "CLEAN")), trailer(), new ArrayList<>()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exceeds fixed width 150");
    }

    @Test
    @DisplayName("round-trip: a non-ASCII value still decodes back to itself")
    void nonAsciiRoundTrips() {
        // encode/decode remain mutual inverses — the detector is observational only.
        DepositData d = line("한국전자", "CLEAN", "CLEAN", "CLEAN");
        byte[] bytes = codec.encode(header(), List.of(d), trailer(), new ArrayList<>());

        FixedLengthCodec.ParsedFile parsed = codec.decode(bytes);

        assertThat(parsed.lines()).hasSize(1);
        assertThat(parsed.lines().get(0).senderName().trim()).isEqualTo("한국전자");
        assertThat("한국전자".getBytes(StandardCharsets.UTF_8)).hasSize(12);
    }

    /* ───────────────────────────── fixtures ───────────────────────────── */

    private static DepositHeader header() {
        return new DepositHeader("970457", "WRB001", "20260730", "100001020691", "0", "1", "001");
    }

    private static DepositTrailer trailer() {
        return new DepositTrailer("1", "1000", "0", "0", "0", "0");
    }

    private static DepositData line(String sender, String receiver, String depMemo, String wdrlMemo) {
        return new DepositData("1", "0681000001", "VND", "1000", "T", "N", "Y", "01203001",
                sender, receiver, depMemo, wdrlMemo, "", "", "VN", "VND", "BEN", "");
    }
}
