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
 *       value's <i>byte</i> length. A value wider than its field is rejected — see the
 *       overflow note below, which is a <b>deliberate divergence</b> from legacy, not parity.</li>
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
 *
 * <h2>Over-width fields: this codec throws; legacy silently corrupted the record</h2>
 *
 * <p><b>Do not "restore parity" here.</b> An earlier revision of this Javadoc claimed legacy
 * {@code rightPadWith}/{@code leftPadWith} "also threw on overflow". That is <b>false</b>, and
 * acting on it would reintroduce a silent data-corruption bug. What legacy actually does
 * ({@code WECMS_BATCH DEV/source/WECMS_BATCH/WECMS_BATCH/src/util/StrUtil.java:182-230}):
 * <ol>
 *   <li>{@code rightPadWith} computes {@code totLen - source.getBytes().length}; when that is
 *       negative it {@code throw new Exception(...)} — <b>inside its own try block</b>;</li>
 *   <li>its own {@code catch} swallows that exception, logs it, and {@code return null};</li>
 *   <li>the caller ({@code Deposit_Data.getData():201-221}) appends the result straight into a
 *       {@code StringBuffer}, so {@code null} is rendered as the four literal characters
 *       {@code "null"}.</li>
 * </ol>
 *
 * <p>Concretely, one over-width {@code senderName} (150-byte slot) contributes 4 bytes instead of
 * 150. The {@code D} record becomes 998 − 146 = <b>852 content bytes</b>, every field after the
 * overflowing one is shifted 146 bytes left, and <b>nothing downstream detects it</b>: legacy has no
 * record-length assertion on the write path, so the malformed record is written to the file, SFTP'd
 * and parsed by the bank against the correct offsets. The 146-byte figure is the same for a
 * {@code receiverName} overflow; a memo (210-byte slot) overflow shifts by 206.
 *
 * <p>{@link #put} therefore throws {@link IllegalArgumentException}, and {@link #writeRecord}
 * additionally asserts the 998-byte content length — two guards legacy does not have. This is the
 * <b>last-resort backstop</b>: the reachable over-width path (non-Latin text surviving
 * tone-stripping) is caught at the entry/validation layer by
 * {@code com.kosign.wecms.domain.payment.common.PaymentWireWidthGuard}, where the user can still fix
 * the row. By the time a value reaches this codec the batch is mid-dispatch and the only safe
 * outcome is to refuse to build the file.
 */
@Component
@lombok.extern.slf4j.Slf4j
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
     * One field written to the wire carrying at least one byte above {@code 0x7F} (C-13).
     *
     * @param recordType   {@code H} / {@code D} / {@code T}
     * @param recordIndex  0-based record position in the file
     * @param fieldName    the {@link DepositData} / {@link DepositHeader} component name
     * @param offset       byte offset of the field within its 998-byte record
     * @param nonAsciiBytes how many of the field's bytes are &gt; {@code 0x7F}
     * @param totalBytes   the field value's total UTF-8 byte length
     */
    public record NonAsciiField(String recordType, int recordIndex, String fieldName,
                                int offset, int nonAsciiBytes, int totalBytes) {
        @Override
        public String toString() {
            return recordType + "[" + recordIndex + "]." + fieldName
                    + " offset=" + offset + " nonAsciiBytes=" + nonAsciiBytes + "/" + totalBytes;
        }
    }

    /**
     * Encode header + lines + trailer into the fixed-length byte stream
     * ({@code H} record, one {@code D} record per line, then {@code T} record).
     *
     * @return the full file bytes; {@code records * 1000} long.
     * @throws IllegalArgumentException if a required record is null or a field value
     *                                  exceeds its fixed width.
     */
    public byte[] encode(DepositHeader header, List<DepositData> lines, DepositTrailer trailer) {
        return encode(header, lines, trailer, null);
    }

    /**
     * As {@link #encode(DepositHeader, List, DepositTrailer)}, additionally reporting every field that
     * puts a non-ASCII byte on the wire (C-13).
     *
     * <h2>Why this exists: the width check is not a charset check</h2>
     * {@link #put} verifies only that a value <b>fits</b> its slot. A value of {@code <= width} bytes
     * containing multibyte characters passes silently — {@code 한국전자} is 12 bytes and clears the
     * 150-byte {@code senderName} slot without complaint. All six UAT sample files are pure ASCII
     * (0 bytes &gt; {@code 0x7F}), and tone-stripping exists precisely because the bank does not accept
     * composed Vietnamese, so a multibyte byte reaching this codec is a strong signal that something
     * upstream is wrong — but nothing was detecting it.
     *
     * <p>{@code PaymentWireWidthGuard} rejects non-Latin text at the entry layer, but it only sees
     * values that pass through entry validation. <b>Server-derived, admin-written and legacy-JEX-written
     * values bypass it entirely</b> — {@code SENDER_NM} from {@code ACCT_MSTR.ACCT_NM} being the
     * canonical example, plus HAENGSUNG ERP passthrough rows and anything the live JEX system wrote.
     * This is the only place all of those converge.
     *
     * <h2>TODO — harden to a reject once the bank's charset is confirmed (K3 / C-13)</h2>
     * This deliberately does <b>not</b> reject. The bank's accepted charset on this channel is
     * <b>unconfirmed</b>: the UAT samples prove only that real files have been ASCII, not that
     * non-ASCII is refused. Two things must be established first:
     * <ol>
     *   <li><b>The bank's actual accepted encoding</b> for the {@code D}-record text fields.</li>
     *   <li><b>What legacy actually put on the wire.</b> Legacy encodes with the JVM
     *       <i>platform-default</i> charset ({@code String.getBytes()} with no argument, legacy
     *       {@code StrUtil.rightPadWith:186}). If PROD {@code file.encoding} resolved to
     *       {@code US-ASCII} / {@code ISO-8859-1}, every multibyte character was silently replaced with
     *       {@code '?'} (or mangled) before it ever reached the file — meaning <b>the bank may never
     *       have received a multibyte byte on this channel</b>, and "reject" would be the true parity
     *       behaviour rather than a tightening. This codec pins UTF-8, so it would send the real bytes
     *       where legacy sent {@code '?'}. Resolving PROD {@code file.encoding} is the blocker; it is
     *       also why {@link #CHARSET} is on the do-not-touch list (repinning it also changes
     *       {@code BulkReceptionService.byteTruncate}, which reads the same constant while legacy's
     *       truncator hardcodes UTF-8).
     * </ol>
     * Until both are answered, observe and alert. Do not silently change what goes on the wire.
     *
     * @param nonAsciiSink optional collector; when non-null, every offending field is appended so the
     *                     caller can raise <b>one</b> alert per file (this class has no {@code VAN_ORG_CD}
     *                     and {@code OpsAlertService.notify} needs one to resolve its API key, so the
     *                     alert belongs to the caller). Pass {@code null} to log only.
     */
    public byte[] encode(DepositHeader header, List<DepositData> lines, DepositTrailer trailer,
                         List<NonAsciiField> nonAsciiSink) {
        if (header == null) {
            throw new IllegalArgumentException("DepositHeader is required");
        }
        if (trailer == null) {
            throw new IllegalArgumentException("DepositTrailer is required");
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int index = 0;
        writeRecord(out, encodeHeader(header, scan(ID_HEADER, index++, nonAsciiSink)), ID_HEADER);
        if (lines != null) {
            for (DepositData d : lines) {
                if (d == null) {
                    throw new IllegalArgumentException("DepositData line must not be null");
                }
                writeRecord(out, encodeData(d, scan(ID_DATA, index++, nonAsciiSink)), ID_DATA);
            }
        }
        writeRecord(out, encodeTrailer(trailer, scan(ID_TRAILER, index, nonAsciiSink)), ID_TRAILER);
        return out.toByteArray();
    }

    /**
     * Per-record non-ASCII scanner. Stateless class + method-local scanner keeps {@link FixedLengthCodec}
     * thread-safe as a singleton {@code @Component}.
     */
    private NonAsciiScanner scan(String recordType, int recordIndex, List<NonAsciiField> sink) {
        return new NonAsciiScanner(recordType, recordIndex, sink);
    }

    /** Collects non-ASCII findings for one record and logs each at WARN. */
    private static final class NonAsciiScanner {
        private final String recordType;
        private final int recordIndex;
        private final List<NonAsciiField> sink;

        NonAsciiScanner(String recordType, int recordIndex, List<NonAsciiField> sink) {
            this.recordType = recordType;
            this.recordIndex = recordIndex;
            this.sink = sink;
        }

        /** Inspect one field's encoded bytes; record + log when any byte exceeds {@code 0x7F}. */
        void inspect(String fieldName, int offset, byte[] encoded) {
            int nonAscii = 0;
            for (byte b : encoded) {
                if ((b & 0xFF) > 0x7F) {
                    nonAscii++;
                }
            }
            if (nonAscii == 0) {
                return;
            }
            NonAsciiField finding =
                    new NonAsciiField(recordType, recordIndex, fieldName, offset, nonAscii, encoded.length);
            // WARN, not ERROR: the file is still structurally valid and is still sent. This is an
            // observation about content the bank may not accept — see the encode(...) TODO.
            // The VALUE is deliberately not logged: these fields carry payer / payee names and memos.
            log.warn("firm-banking file field carries non-ASCII bytes on the wire — the bank's accepted "
                            + "charset is unconfirmed (K3 / C-13), so this is NOT rejected. {}",
                    finding);
            if (sink != null) {
                sink.add(finding);
            }
        }
    }

    private byte[] encodeHeader(DepositHeader h, NonAsciiScanner scan) {
        ByteArrayOutputStream rec = new ByteArrayOutputStream(CONTENT_LEN);
        put(rec, ID_HEADER, 1, Pad.RIGHT, SPACE, "identifierCode", scan);
        put(rec, h.bankCode(), 15, Pad.RIGHT, SPACE, "bankCode", scan);
        put(rec, h.institutionCode(), 20, Pad.RIGHT, SPACE, "institutionCode", scan);
        put(rec, h.tranDt(), 8, Pad.LEFT, ZERO, "tranDt", scan);
        put(rec, h.withdrawalMoAccountNumber(), 30, Pad.RIGHT, SPACE, "withdrawalMoAccountNumber", scan);
        put(rec, h.fundType(), 1, Pad.RIGHT, SPACE, "fundType", scan);
        put(rec, h.transactionType(), 1, Pad.RIGHT, SPACE, "transactionType", scan);
        put(rec, h.iterationInfo(), 3, Pad.LEFT, ZERO, "iterationInfo", scan);
        put(rec, "", 919, Pad.RIGHT, SPACE, "filler", scan); // filler
        return rec.toByteArray();
    }

    private byte[] encodeData(DepositData d, NonAsciiScanner scan) {
        ByteArrayOutputStream rec = new ByteArrayOutputStream(CONTENT_LEN);
        put(rec, ID_DATA, 1, Pad.RIGHT, SPACE, "identifierCode", scan);
        put(rec, d.seq(), 4, Pad.LEFT, ZERO, "seq", scan);
        put(rec, d.depositAccountNumber(), 30, Pad.RIGHT, SPACE, "depositAccountNumber", scan);
        put(rec, d.currencyCode(), 3, Pad.RIGHT, SPACE, "currencyCode", scan);
        put(rec, d.transferAmount(), 23, Pad.LEFT, ZERO, "transferAmount", scan);
        put(rec, d.sameBankFlag(), 1, Pad.RIGHT, SPACE, "sameBankFlag", scan);
        put(rec, d.otherBankNetworkCode(), 1, Pad.RIGHT, SPACE, "otherBankNetworkCode", scan);
        put(rec, d.cardAccountType(), 1, Pad.RIGHT, SPACE, "cardAccountType", scan);
        put(rec, d.depositBankCode(), 15, Pad.RIGHT, SPACE, "depositBankCode", scan);
        put(rec, d.senderName(), 150, Pad.RIGHT, SPACE, "senderName", scan);
        put(rec, d.receiverName(), 150, Pad.RIGHT, SPACE, "receiverName", scan);
        put(rec, d.depositAccountMemo(), 210, Pad.RIGHT, SPACE, "depositAccountMemo", scan);
        put(rec, d.withdrawalAccountMemo(), 210, Pad.RIGHT, SPACE, "withdrawalAccountMemo", scan);
        put(rec, d.processingResult(), 1, Pad.RIGHT, SPACE, "processingResult", scan);
        put(rec, d.responseCode(), 10, Pad.RIGHT, SPACE, "responseCode", scan);
        put(rec, d.receiptBankCountryCode(), 2, Pad.RIGHT, SPACE, "receiptBankCountryCode", scan);
        put(rec, d.remittanceCurrencyCode(), 3, Pad.RIGHT, SPACE, "remittanceCurrencyCode", scan);
        put(rec, d.feeBrdnClsCd(), 3, Pad.RIGHT, SPACE, "feeBrdnClsCd", scan);
        put(rec, d.rcptUseAddr(), 180, Pad.RIGHT, SPACE, "rcptUseAddr", scan);
        return rec.toByteArray();
    }

    private byte[] encodeTrailer(DepositTrailer t, NonAsciiScanner scan) {
        ByteArrayOutputStream rec = new ByteArrayOutputStream(CONTENT_LEN);
        put(rec, ID_TRAILER, 1, Pad.RIGHT, SPACE, "identifierCode", scan);
        put(rec, t.requestCnt(), 7, Pad.LEFT, ZERO, "requestCnt", scan);
        put(rec, t.requestAmt(), 23, Pad.LEFT, ZERO, "requestAmt", scan);
        put(rec, t.succProcessingCnt(), 7, Pad.LEFT, ZERO, "succProcessingCnt", scan);
        put(rec, t.succProcessingAmt(), 23, Pad.LEFT, ZERO, "succProcessingAmt", scan);
        put(rec, t.nonProcessingCnt(), 7, Pad.LEFT, ZERO, "nonProcessingCnt", scan);
        put(rec, t.nonProcessingAmt(), 23, Pad.LEFT, ZERO, "nonProcessingAmt", scan);
        put(rec, "", 907, Pad.RIGHT, SPACE, "filler", scan); // filler
        return rec.toByteArray();
    }

    /**
     * Pad {@code value} to {@code width} bytes and append; reject over-width values.
     *
     * <p>C-13: also reports a value carrying bytes above {@code 0x7F} via {@code scan}. Note the
     * asymmetry, and that it is deliberate — over-width <b>throws</b>, non-ASCII only <b>warns</b>.
     * Over-width would corrupt the record's byte alignment (a structural fault, provably wrong);
     * non-ASCII produces a structurally valid record whose acceptability depends on the bank's charset,
     * which is unconfirmed. See the {@link #encode(DepositHeader, List, DepositTrailer, List)} TODO.
     */
    private void put(ByteArrayOutputStream out, String value, int width, Pad side, char pad,
                     String fieldName, NonAsciiScanner scan) {
        byte[] v = (value == null ? "" : value).getBytes(CHARSET);
        if (v.length > width) {
            throw new IllegalArgumentException(
                    "field value exceeds fixed width " + width + " (was " + v.length
                            + " bytes): \"" + value + "\"");
        }
        // Offset BEFORE this field is written — i.e. the field's own position in the 998-byte record.
        if (scan != null) {
            scan.inspect(fieldName, out.size(), v);
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
