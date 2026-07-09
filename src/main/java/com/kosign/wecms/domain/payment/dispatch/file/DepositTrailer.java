package com.kosign.wecms.domain.payment.dispatch.file;

/**
 * Trailer ('T') record of the firm-banking fixed-length file — refactor of legacy
 * {@code trailer.Deposit_Trailer}. One per file.
 *
 * <p><b>Fields are raw strings in wire order</b> (see {@link DepositHeader} for the
 * rationale): counts/amounts are kept verbatim so {@code decode → encode} is
 * byte-identical. {@link FixedLengthCodec} owns padding, the constant identifier
 * byte, the 907-byte filler, and the {@code \r\n} terminator.
 *
 * <p>Byte layout (998 content bytes + {@code \r\n} = 1000), verified against the UAT
 * samples on 2026-06-12 and matching legacy {@code Deposit_Trailer.getTrailer()}:
 * <pre>
 *   off  len  pad        field
 *     0    1  right ' '  identifier (constant 'T')           — owned by codec
 *     1    7  left  '0'  requestCnt
 *     8   23  left  '0'  requestAmt
 *    31    7  left  '0'  succProcessingCnt
 *    38   23  left  '0'  succProcessingAmt
 *    61    7  left  '0'  nonProcessingCnt
 *    68   23  left  '0'  nonProcessingAmt
 *    91  907  right ' '  filler (constant spaces)            — owned by codec
 * </pre>
 */
public record DepositTrailer(
        String requestCnt,
        String requestAmt,
        String succProcessingCnt,
        String succProcessingAmt,
        String nonProcessingCnt,
        String nonProcessingAmt
) {
}
