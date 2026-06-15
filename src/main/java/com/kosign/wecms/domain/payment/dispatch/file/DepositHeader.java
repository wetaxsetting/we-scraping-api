package com.kosign.wecms.domain.payment.dispatch.file;

/**
 * Header ('H') record of the firm-banking fixed-length file — refactor of legacy
 * {@code header.Deposit_Header}. One per file.
 *
 * <p><b>Fields are raw strings in wire order</b>, deliberately mirroring the legacy
 * class (which used {@code String} for every field). A pre-formatted textual form
 * must be preserved verbatim so that {@code decode → encode} is byte-identical to
 * the bank's file (an amount/date/seq cannot be a {@code BigDecimal}/{@code int}
 * without losing the exact on-wire representation — e.g. {@code "20251021"} or a
 * zero-padded sequence). {@link FixedLengthCodec} owns padding, the constant
 * identifier byte, the 919-byte filler, and the {@code \r\n} terminator.
 *
 * <p>Byte layout (998 content bytes + {@code \r\n} = 1000), verified against the
 * UAT samples under {@code WECMS_BATCH DEV/samples/} on 2026-06-12 and matching
 * legacy {@code Deposit_Header.getHeader()}:
 * <pre>
 *   off  len  pad        field
 *     0    1  right ' '  identifier (constant 'H')           — owned by codec
 *     1   15  right ' '  bankCode                            (970424…)
 *    16   20  right ' '  institutionCode                     (LUME1 / HGSNG …)
 *    36    8  left  '0'  tranDt (YYYYMMDD)
 *    44   30  right ' '  withdrawalMoAccountNumber
 *    74    1  right ' '  fundType
 *    75    1  right ' '  transactionType
 *    76    3  left  '0'  iterationInfo (file round)
 *    79  919  right ' '  filler (constant spaces)            — owned by codec
 * </pre>
 */
public record DepositHeader(
        String bankCode,
        String institutionCode,
        String tranDt,
        String withdrawalMoAccountNumber,
        String fundType,
        String transactionType,
        String iterationInfo
) {
}
