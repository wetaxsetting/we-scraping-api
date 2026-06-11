package com.kosign.wecms.domain.payment.dispatch.file;

import org.springframework.stereotype.Component;

/**
 * Fixed-length firm-banking file codec — refactor seam for the legacy
 * {@code header.Deposit_Header} / {@code data.Deposit_Data} /
 * {@code trailer.Deposit_Trailer} + {@code util.StrUtil} (VN diacritics
 * {@code replaceTone}, L/R padding, BigDecimal integer/decimal split).
 *
 * <p>BATCH1 wrote this file (1000-byte H/D/T records, {@code \r\n}-terminated)
 * for SFTP upload; the 01302 / API2 path parsed the bank result file with the
 * <b>same</b> byte layout, so encode and decode must be byte-identical to the
 * bank's active format version.
 *
 * <p><b>TODO(K3) — NOT IMPLEMENTED.</b> The exact field offsets/lengths depend on
 * the production format version (handover §4.4 records changes 2025.07
 * country/currency, 2025.10.29 fee-burden + {@code filler(183)} removal). The
 * production version, any post-2025.10.29 change, and the padding charset are
 * unconfirmed (K3), and a real sample file is required to byte-verify (B6).
 * Encoding/parsing therefore throw until K3 + a sample are in hand — emitting a
 * wrong-version file would be rejected by the bank or, worse, mis-execute.
 */
@Component
public class FixedLengthCodec {

    /**
     * Encode header + lines + trailer into the fixed-length byte stream.
     * TODO(K3): implement once the format version + offsets + charset are confirmed
     * and gated against a real sample file (B6).
     */
    public byte[] encode(DepositHeader header, java.util.List<DepositData> lines, DepositTrailer trailer) {
        throw new UnsupportedOperationException(
                "FixedLengthCodec.encode not implemented — TODO(K3): file format version/offsets unconfirmed; needs sample (B6).");
    }

    /**
     * Parse a fixed-length result file (1000-byte records) into typed records.
     * TODO(K3): implement once the format version + offsets + charset are confirmed.
     */
    public ParsedFile decode(byte[] content) {
        throw new UnsupportedOperationException(
                "FixedLengthCodec.decode not implemented — TODO(K3): file format version/offsets unconfirmed; needs sample (B6).");
    }

    /** Parsed-file holder (header + lines + trailer). Shape may extend once K3 is confirmed. */
    public record ParsedFile(DepositHeader header, java.util.List<DepositData> lines, DepositTrailer trailer) {
    }
}
