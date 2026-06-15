package com.kosign.wecms.domain.payment.dispatch.file;

import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Fixed-length firm-banking file codec (K3) — refactor of the legacy
 * {@code header.Deposit_Header} / {@code data.Deposit_Data} /
 * {@code trailer.Deposit_Trailer} + {@code util.StrUtil} (L/R padding).
 *
 * <p>BATCH1 wrote this file (1000-byte {@code H}/{@code D}/{@code T} records,
 * {@code \r\n}-terminated) for SFTP upload; the 01302 / API2 path parsed the bank
 * result file with the <b>same</b> byte layout. {@link #encode} and {@link #decode}
 * are therefore mutual inverses and must be byte-identical to the bank's active
 * format version.
 *
 * <h2>Confirmed layout (2026-06-12)</h2>
 * The field offsets/lengths/pad rules below were verified byte-for-byte against the
 * six UAT sample files under {@code WECMS_BATCH DEV/samples/} (3 send + 3 receive)
 * and match the legacy classes line-by-line. See {@link DepositHeader} /
 * {@link DepositData} / {@link DepositTrailer} for the per-field tables.
 * <ul>
 *   <li><b>Record length</b> = 1000 bytes = 998 content + {@code \r\n} (CR LF).</li>
 *   <li><b>Sequence</b> = exactly one {@code H}, zero-or-more {@code D}, one {@code T}.</li>
 *   <li><b>Padding</b> mirrors legacy {@code StrUtil}: numeric fields left-padded with
 *       {@code '0'}; text fields right-padded with {@code ' '}; computed on the
 *       value's <i>byte</i> length. A value wider than its field is rejected (the
 *       codec never silently truncates — legacy {@code rightPadWith}/{@code leftPadWith}
 *       also threw on overflow).</li>
 *   <li><b>Filler</b> (header 919 / trailer 907) and the identifier byte are constants
 *       owned here, not record fields.</li>
 * </ul>
 *
 * <h2>Charset assumption (documented)</h2>
 * All six UAT samples are <b>pure ASCII</b> (0 bytes &gt; 0x7F), so the on-wire bytes
 * are charset-independent for real legacy files. This codec uses {@link #CHARSET}
 * ({@code UTF-8}) for both width computation and slicing, which reproduces the samples
 * byte-for-byte. The legacy code used the JVM <i>platform-default</i> charset (no
 * explicit charset on {@code getBytes()}/{@code new String(...)}); for ASCII the two
 * are identical. Vietnamese names/addresses are tone-stripped to ASCII upstream
 * ({@code StrUtil.replaceTone} in BATCH1, out of K3 scope) before reaching the codec.
 * If a genuinely non-ASCII value were ever passed, width is measured in UTF-8 bytes
 * and an over-width value is rejected rather than mis-aligned — see the assumptions
 * note in the K3 report.
 */
@Component
public class FixedLengthCodec {

    /** On-wire charset. UTF-8 reproduces the (pure-ASCII) UAT samples byte-for-byte. */
    public static final Charset CHARSET = StandardCharsets.UTF_8;

    static final int CONTENT_LEN = 998;
    static final byte[] CRLF = {0x0D, 0x0A};
    static final int RECORD_LEN = CONTENT_LEN + CRLF.length; // 1000

    private static final char SPACE = ' ';
    private static final char ZERO = '0';

    private static final String ID_HEADER = "H";
    private static final String ID_DATA = "D";
    private static final String ID_TRAILER = "T";

    private enum Pad { LEFT, RIGHT }

    // ---------------------------------------------------------------- encode

    /**
     * Encode header + lines + trailer into the fixed-length byte stream
     * ({@code H} record, one {@code D} record per line, then {@code T} record).
     *
     * @return the full file bytes; {@code records * 1000} long.
     * @throws IllegalArgumentException if a required record is null or a field value
     *                                  exceeds its fixed width.
     */
    public byte[] encode(DepositHeader header, List<DepositData> lines, DepositTrailer trailer) {
        if (header == null) {
            throw new IllegalArgumentException("DepositHeader is required");
        }
        if (trailer == null) {
            throw new IllegalArgumentException("DepositTrailer is required");
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeRecord(out, encodeHeader(header), ID_HEADER);
        if (lines != null) {
            for (DepositData d : lines) {
                if (d == null) {
                    throw new IllegalArgumentException("DepositData line must not be null");
                }
                writeRecord(out, encodeData(d), ID_DATA);
            }
        }
        writeRecord(out, encodeTrailer(trailer), ID_TRAILER);
        return out.toByteArray();
    }

    private byte[] encodeHeader(DepositHeader h) {
        ByteArrayOutputStream rec = new ByteArrayOutputStream(CONTENT_LEN);
        put(rec, ID_HEADER, 1, Pad.RIGHT, SPACE);
        put(rec, h.bankCode(), 15, Pad.RIGHT, SPACE);
        put(rec, h.institutionCode(), 20, Pad.RIGHT, SPACE);
        put(rec, h.tranDt(), 8, Pad.LEFT, ZERO);
        put(rec, h.withdrawalMoAccountNumber(), 30, Pad.RIGHT, SPACE);
        put(rec, h.fundType(), 1, Pad.RIGHT, SPACE);
        put(rec, h.transactionType(), 1, Pad.RIGHT, SPACE);
        put(rec, h.iterationInfo(), 3, Pad.LEFT, ZERO);
        put(rec, "", 919, Pad.RIGHT, SPACE); // filler
        return rec.toByteArray();
    }

    private byte[] encodeData(DepositData d) {
        ByteArrayOutputStream rec = new ByteArrayOutputStream(CONTENT_LEN);
        put(rec, ID_DATA, 1, Pad.RIGHT, SPACE);
        put(rec, d.seq(), 4, Pad.LEFT, ZERO);
        put(rec, d.depositAccountNumber(), 30, Pad.RIGHT, SPACE);
        put(rec, d.currencyCode(), 3, Pad.RIGHT, SPACE);
        put(rec, d.transferAmount(), 23, Pad.LEFT, ZERO);
        put(rec, d.sameBankFlag(), 1, Pad.RIGHT, SPACE);
        put(rec, d.otherBankNetworkCode(), 1, Pad.RIGHT, SPACE);
        put(rec, d.cardAccountType(), 1, Pad.RIGHT, SPACE);
        put(rec, d.depositBankCode(), 15, Pad.RIGHT, SPACE);
        put(rec, d.senderName(), 150, Pad.RIGHT, SPACE);
        put(rec, d.receiverName(), 150, Pad.RIGHT, SPACE);
        put(rec, d.depositAccountMemo(), 210, Pad.RIGHT, SPACE);
        put(rec, d.withdrawalAccountMemo(), 210, Pad.RIGHT, SPACE);
        put(rec, d.processingResult(), 1, Pad.RIGHT, SPACE);
        put(rec, d.responseCode(), 10, Pad.RIGHT, SPACE);
        put(rec, d.receiptBankCountryCode(), 2, Pad.RIGHT, SPACE);
        put(rec, d.remittanceCurrencyCode(), 3, Pad.RIGHT, SPACE);
        put(rec, d.feeBrdnClsCd(), 3, Pad.RIGHT, SPACE);
        put(rec, d.rcptUseAddr(), 180, Pad.RIGHT, SPACE);
        return rec.toByteArray();
    }

    private byte[] encodeTrailer(DepositTrailer t) {
        ByteArrayOutputStream rec = new ByteArrayOutputStream(CONTENT_LEN);
        put(rec, ID_TRAILER, 1, Pad.RIGHT, SPACE);
        put(rec, t.requestCnt(), 7, Pad.LEFT, ZERO);
        put(rec, t.requestAmt(), 23, Pad.LEFT, ZERO);
        put(rec, t.succProcessingCnt(), 7, Pad.LEFT, ZERO);
        put(rec, t.succProcessingAmt(), 23, Pad.LEFT, ZERO);
        put(rec, t.nonProcessingCnt(), 7, Pad.LEFT, ZERO);
        put(rec, t.nonProcessingAmt(), 23, Pad.LEFT, ZERO);
        put(rec, "", 907, Pad.RIGHT, SPACE); // filler
        return rec.toByteArray();
    }

    /** Pad {@code value} to {@code width} bytes and append; reject over-width values. */
    private void put(ByteArrayOutputStream out, String value, int width, Pad side, char pad) {
        byte[] v = (value == null ? "" : value).getBytes(CHARSET);
        if (v.length > width) {
            throw new IllegalArgumentException(
                    "field value exceeds fixed width " + width + " (was " + v.length
                            + " bytes): \"" + value + "\"");
        }
        int padCount = width - v.length;
        byte padByte = (byte) pad;
        if (side == Pad.LEFT) {
            for (int i = 0; i < padCount; i++) out.write(padByte);
            out.writeBytes(v);
        } else {
            out.writeBytes(v);
            for (int i = 0; i < padCount; i++) out.write(padByte);
        }
    }

    private void writeRecord(ByteArrayOutputStream out, byte[] content, String type) {
        if (content.length != CONTENT_LEN) {
            // Guard against field-spec drift — should be unreachable.
            throw new IllegalStateException(
                    type + " record content was " + content.length + " bytes, expected " + CONTENT_LEN);
        }
        out.writeBytes(content);
        out.writeBytes(CRLF);
    }

    // ---------------------------------------------------------------- decode

    /**
     * Parse a fixed-length file (1000-byte {@code H}/{@code D}/{@code T} records) into
     * typed records. Field values are returned <b>verbatim</b> (padding preserved), so
     * {@code encode(decode(bytes))} reproduces {@code bytes} exactly.
     *
     * @throws IllegalArgumentException if the content is null/empty, not a multiple of
     *                                  the record length, a record is not
     *                                  CRLF-terminated, an unknown record type appears,
     *                                  or the mandatory {@code H}/{@code T} is missing.
     */
    public ParsedFile decode(byte[] content) {
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("file content is empty");
        }

        DepositHeader header = null;
        DepositTrailer trailer = null;
        List<DepositData> lines = new ArrayList<>();

        int offset = 0;
        int index = 0;
        while (offset < content.length) {
            int remaining = content.length - offset;
            byte[] rec;
            if (remaining >= RECORD_LEN) {
                if (content[offset + CONTENT_LEN] != CRLF[0] || content[offset + CONTENT_LEN + 1] != CRLF[1]) {
                    throw new IllegalArgumentException(
                            "record " + index + " is not CR/LF-terminated at byte " + (offset + CONTENT_LEN));
                }
                rec = Arrays.copyOfRange(content, offset, offset + CONTENT_LEN);
                offset += RECORD_LEN;
            } else if (remaining == CONTENT_LEN) {
                // Tolerate a final record without a trailing CRLF.
                rec = Arrays.copyOfRange(content, offset, offset + CONTENT_LEN);
                offset += CONTENT_LEN;
            } else {
                throw new IllegalArgumentException(
                        "trailing partial record: " + remaining + " bytes left (not a multiple of "
                                + RECORD_LEN + ")");
            }

            String type = slice(rec, 0, 1);
            switch (type) {
                case ID_HEADER -> {
                    if (header != null) {
                        throw new IllegalArgumentException("multiple 'H' records at index " + index);
                    }
                    header = decodeHeader(rec);
                }
                case ID_DATA -> lines.add(decodeData(rec));
                case ID_TRAILER -> {
                    if (trailer != null) {
                        throw new IllegalArgumentException("multiple 'T' records at index " + index);
                    }
                    trailer = decodeTrailer(rec);
                }
                default -> throw new IllegalArgumentException(
                        "unknown record type '" + type + "' at record index " + index);
            }
            index++;
        }

        if (header == null) {
            throw new IllegalArgumentException("missing 'H' header record");
        }
        if (trailer == null) {
            throw new IllegalArgumentException("missing 'T' trailer record");
        }
        return new ParsedFile(header, lines, trailer);
    }

    private DepositHeader decodeHeader(byte[] r) {
        return new DepositHeader(
                slice(r, 1, 15),   // bankCode
                slice(r, 16, 20),  // institutionCode
                slice(r, 36, 8),   // tranDt
                slice(r, 44, 30),  // withdrawalMoAccountNumber
                slice(r, 74, 1),   // fundType
                slice(r, 75, 1),   // transactionType
                slice(r, 76, 3));  // iterationInfo
        // bytes 79..998 = filler (ignored)
    }

    private DepositData decodeData(byte[] r) {
        return new DepositData(
                slice(r, 1, 4),     // seq
                slice(r, 5, 30),    // depositAccountNumber
                slice(r, 35, 3),    // currencyCode
                slice(r, 38, 23),   // transferAmount
                slice(r, 61, 1),    // sameBankFlag
                slice(r, 62, 1),    // otherBankNetworkCode
                slice(r, 63, 1),    // cardAccountType
                slice(r, 64, 15),   // depositBankCode
                slice(r, 79, 150),  // senderName
                slice(r, 229, 150), // receiverName
                slice(r, 379, 210), // depositAccountMemo
                slice(r, 589, 210), // withdrawalAccountMemo
                slice(r, 799, 1),   // processingResult
                slice(r, 800, 10),  // responseCode
                slice(r, 810, 2),   // receiptBankCountryCode
                slice(r, 812, 3),   // remittanceCurrencyCode
                slice(r, 815, 3),   // feeBrdnClsCd
                slice(r, 818, 180));// rcptUseAddr
    }

    private DepositTrailer decodeTrailer(byte[] r) {
        return new DepositTrailer(
                slice(r, 1, 7),    // requestCnt
                slice(r, 8, 23),   // requestAmt
                slice(r, 31, 7),   // succProcessingCnt
                slice(r, 38, 23),  // succProcessingAmt
                slice(r, 61, 7),   // nonProcessingCnt
                slice(r, 68, 23)); // nonProcessingAmt
        // bytes 91..998 = filler (ignored)
    }

    private String slice(byte[] rec, int offset, int len) {
        return new String(rec, offset, len, CHARSET);
    }

    /** Parsed-file holder (header + zero-or-more lines + trailer). */
    public record ParsedFile(DepositHeader header, List<DepositData> lines, DepositTrailer trailer) {
    }
}
