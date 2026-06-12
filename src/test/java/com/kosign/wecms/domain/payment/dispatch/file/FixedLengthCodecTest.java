package com.kosign.wecms.domain.payment.dispatch.file;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * K3 codec tests for {@link FixedLengthCodec}.
 *
 * <p>Layout values asserted here were confirmed byte-for-byte against the six UAT
 * sample files under {@code WECMS_BATCH DEV/samples/} on 2026-06-12. The hermetic
 * tests prove the invariants without any external file; {@link #realSamplesRoundTrip()}
 * additionally proves {@code encode(decode(bytes)) == bytes} against the actual UAT
 * files when the legacy tree is checked out (skipped otherwise).
 */
class FixedLengthCodecTest {

    private final FixedLengthCodec codec = new FixedLengthCodec();

    private static final int REC = 1000;
    private static final int CONTENT = 998;

    /** Header mirroring the UAT send sample S_GV00000006_SHB_001 (LUME1 / 970424). */
    private static DepositHeader header() {
        return new DepositHeader("970424", "LUME1", "20251021", "700005745826", "0", "1", "1");
    }

    /** Data line carrying a decimal amount, mirroring the WRB send sample shape. */
    private static DepositData decimalAmountLine() {
        return new DepositData(
                "1", "100001020691", "USD", "14.00", "O", "V", "Y", "79304001",
                "", "KUK IL VIETNAM CO.,LTD", "", "",
                "", "", "", "USD", "", "");
    }

    /** Data line carrying an integer amount + a populated result (receive-file shape). */
    private static DepositData resultLine() {
        return new DepositData(
                "2", "0681000006680", "USD", "15", "T", "", "Y", "01203001",
                "", "TK VND H2H", "", "",
                "N", "0999", "VN", "USD", "", "");
    }

    private static DepositTrailer trailer() {
        return new DepositTrailer("2", "23.00", "0", "0", "0", "0");
    }

    private static String content(byte[] all, int recIndex) {
        return new String(all, recIndex * REC, CONTENT, FixedLengthCodec.CHARSET);
    }

    // ----------------------------------------------------------- record length

    @Test
    void everyRecordIsExactly1000Bytes() {
        byte[] bytes = codec.encode(header(), List.of(decimalAmountLine(), resultLine()), trailer());

        // H + 2D + T = 4 records.
        assertEquals(4 * REC, bytes.length);
        assertEquals(0, bytes.length % REC);

        // Each record is 998 content bytes terminated by CR LF.
        for (int i = 0; i < bytes.length / REC; i++) {
            assertEquals(0x0D, bytes[i * REC + CONTENT], "CR at record " + i);
            assertEquals(0x0A, bytes[i * REC + CONTENT + 1], "LF at record " + i);
        }
    }

    @Test
    void encodesHeaderAndTrailerWithZeroDataLines() {
        byte[] bytes = codec.encode(header(), List.of(), trailer());
        assertEquals(2 * REC, bytes.length);
        assertEquals('H', (char) bytes[0]);
        assertEquals('T', (char) bytes[REC]);
    }

    // ----------------------------------------------------------- record type

    @Test
    void recordTypeBytesAreHDT() {
        byte[] bytes = codec.encode(header(), List.of(decimalAmountLine(), resultLine()), trailer());
        assertEquals('H', (char) bytes[0]);
        assertEquals('D', (char) bytes[REC]);
        assertEquals('D', (char) bytes[2 * REC]);
        assertEquals('T', (char) bytes[3 * REC]);
    }

    // ----------------------------------------------------------- padding rules

    @Test
    void textFieldsAreRightPaddedWithSpacesNumericFieldsLeftPaddedWithZeros() {
        byte[] bytes = codec.encode(header(), List.of(decimalAmountLine()), trailer());

        String h = content(bytes, 0);
        assertEquals("H", h.substring(0, 1));
        assertEquals("970424         ", h.substring(1, 16));   // bankCode right-pad ' ' (15)
        assertEquals("LUME1               ", h.substring(16, 36)); // institutionCode (20)
        assertEquals("20251021", h.substring(36, 44));         // tranDt (already 8)
        assertEquals("001", h.substring(76, 79));              // iterationInfo left-pad '0' (3)
        assertEquals(" ".repeat(919), h.substring(79, 998));   // filler all spaces

        String d = content(bytes, 1);
        assertEquals("0001", d.substring(1, 5));               // seq left-pad '0' (4)
        assertEquals("USD", d.substring(35, 38));              // currencyCode (3)
        // receiverName right-padded with spaces to 150
        assertEquals("KUK IL VIETNAM CO.,LTD" + " ".repeat(150 - "KUK IL VIETNAM CO.,LTD".length()),
                d.substring(229, 379));
    }

    // ----------------------------------------------------------- numeric amount formatting

    @Test
    void transferAmountIsLeftZeroPaddedToWidth23VerbatimDecimal() {
        byte[] bytes = codec.encode(header(), List.of(decimalAmountLine()), trailer());
        String d = content(bytes, 1);
        // Verbatim decimal form preserved (NOT normalised) — matches UAT WRB sample.
        assertEquals("00000000000000000014.00", d.substring(38, 61));
        assertEquals(23, d.substring(38, 61).length());
    }

    @Test
    void transferAmountIntegerFormIsAlsoLeftZeroPaddedVerbatim() {
        byte[] bytes = codec.encode(header(), List.of(resultLine()), trailer());
        String d = content(bytes, 1);
        // Integer form preserved verbatim — matches UAT VCB receive sample.
        assertEquals("00000000000000000000015", d.substring(38, 61));
    }

    @Test
    void trailerCountsAndAmountsAreLeftZeroPadded() {
        byte[] bytes = codec.encode(header(), List.of(decimalAmountLine()), trailer());
        String t = content(bytes, 2);
        assertEquals("0000002", t.substring(1, 8));                       // requestCnt (7)
        assertEquals("00000000000000000023.00", t.substring(8, 31));      // requestAmt (23)
        assertEquals(" ".repeat(907), t.substring(91, 998));              // filler
    }

    @Test
    void overWidthFieldIsRejectedNotTruncated() {
        // currencyCode field is 3 bytes; a 4-byte value must throw, never truncate.
        DepositData bad = new DepositData(
                "1", "ACC", "USDX", "1", "T", "", "Y", "BANK",
                "", "X", "", "", "", "", "", "", "", "");
        assertThrows(IllegalArgumentException.class,
                () -> codec.encode(header(), List.of(bad), trailer()));
    }

    // ----------------------------------------------------------- encode -> decode round trip

    @Test
    void encodeThenDecodePreservesFieldsAndReEncodesIdentically() {
        byte[] bytes = codec.encode(header(), List.of(decimalAmountLine(), resultLine()), trailer());

        FixedLengthCodec.ParsedFile pf = codec.decode(bytes);
        assertEquals(2, pf.lines().size());
        // decode keeps raw fixed-width fields; trim to compare the logical value.
        assertEquals("970424", pf.header().bankCode().trim());
        assertEquals("20251021", pf.header().tranDt());
        assertEquals("00000000000000000014.00", pf.lines().get(0).transferAmount());
        assertEquals("N", pf.lines().get(1).processingResult());
        assertEquals("0999", pf.lines().get(1).responseCode().trim());

        // Idempotent re-encode: encode(decode(bytes)) == bytes.
        byte[] reEncoded = codec.encode(pf.header(), pf.lines(), pf.trailer());
        assertArrayEquals(bytes, reEncoded);
    }

    // ----------------------------------------------------------- decode safety

    @Test
    void decodeRejectsNullEmptyAndPartialContent() {
        assertThrows(IllegalArgumentException.class, () -> codec.decode(null));
        assertThrows(IllegalArgumentException.class, () -> codec.decode(new byte[0]));
        assertThrows(IllegalArgumentException.class, () -> codec.decode(new byte[500]));
    }

    @Test
    void decodeRejectsUnknownRecordType() {
        byte[] bytes = codec.encode(header(), List.of(decimalAmountLine()), trailer());
        bytes[REC] = 'X'; // corrupt the data record's type byte
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> codec.decode(bytes));
        assertTrue(ex.getMessage().contains("unknown record type"));
    }

    @Test
    void decodeRejectsMissingTrailer() {
        byte[] full = codec.encode(header(), List.of(decimalAmountLine()), trailer());
        // Keep only H + D (drop the trailer record).
        byte[] noTrailer = new byte[2 * REC];
        System.arraycopy(full, 0, noTrailer, 0, 2 * REC);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> codec.decode(noTrailer));
        assertTrue(ex.getMessage().contains("missing 'T'"));
    }

    @Test
    void decodeRejectsRecordNotCrlfTerminated() {
        byte[] bytes = codec.encode(header(), List.of(decimalAmountLine()), trailer());
        bytes[CONTENT] = ' '; // clobber the header record's CR
        assertThrows(IllegalArgumentException.class, () -> codec.decode(bytes));
    }

    // ----------------------------------------------------------- real UAT samples (decode -> encode == identical)

    @Test
    void realSamplesRoundTrip() throws IOException {
        Path samples = resolveSamplesDir();
        assumeTrue(samples != null, "UAT samples not present (legacy tree not checked out) — skipping");

        String[] files = {
                "send/S_GV00000001_WRB_008_20251023",
                "send/S_GV00000006_SHB_001_20251021",
                "send/S_GV00000007_VCB_008_20260602",
                "receive/R_GV00000001_WRB_001_20251223",
                "receive/R_GV00000006_SHB_003_20251021",
                "receive/R_GV00000007_VCB_008_20260602",
        };
        for (String rel : files) {
            Path p = samples.resolve(rel);
            assumeTrue(Files.exists(p), "missing sample " + rel);
            byte[] original = Files.readAllBytes(p);

            assertEquals(0, original.length % REC, rel + " not a multiple of 1000");

            FixedLengthCodec.ParsedFile pf = codec.decode(original);
            byte[] reEncoded = codec.encode(pf.header(), pf.lines(), pf.trailer());

            // Byte-identical reproduction of a real bank file.
            assertArrayEquals(original, reEncoded, "round-trip mismatch for " + rel);
            // Sanity: first/last record types.
            assertEquals('H', (char) original[0], rel);
            assertEquals('T', (char) original[original.length - REC]);
        }
    }

    /** Locate {@code WECMS_BATCH DEV/samples} relative to the wecms-api module, or null. */
    private static Path resolveSamplesDir() {
        List<Path> candidates = new ArrayList<>();
        Path cwd = Path.of("").toAbsolutePath();
        candidates.add(cwd.resolve("../WECMS_BATCH DEV/samples").normalize());        // module dir
        candidates.add(cwd.resolve("WECMS_BATCH DEV/samples").normalize());           // workspace root
        candidates.add(cwd.getParent() == null ? cwd : cwd.getParent()
                .resolve("WECMS_BATCH DEV/samples").normalize());
        for (Path c : candidates) {
            if (Files.isDirectory(c)) {
                return c;
            }
        }
        return null;
    }

    // Guard: keep the charset constant ASCII-stable for the assertions above.
    @Test
    void charsetIsAsciiCompatible() {
        assertEquals("A", new String("A".getBytes(StandardCharsets.US_ASCII), FixedLengthCodec.CHARSET));
    }
}
