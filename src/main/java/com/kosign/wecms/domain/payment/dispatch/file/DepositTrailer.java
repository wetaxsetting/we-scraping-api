package com.kosign.wecms.domain.payment.dispatch.file;

import java.math.BigDecimal;

/**
 * Logical trailer ('T') record of the firm-banking fixed-length file
 * (refactor of legacy {@code trailer.Deposit_Trailer}).
 *
 * <p>Logical fields only; physical layout in {@link FixedLengthCodec}.
 * <b>TODO(K3):</b> byte layout pending format version + sample (B6).
 *
 * <p>Per handover §4.4: request / normal / unprocessed counts and amounts.
 */
public record DepositTrailer(
        BigDecimal reqCnt,
        BigDecimal reqAmt,
        BigDecimal succCnt,
        BigDecimal succAmt,
        BigDecimal unprocCnt,
        BigDecimal unprocAmt
) {
}
