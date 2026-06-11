package com.kosign.wecms.domain.payment.dispatch.file;

import java.math.BigDecimal;

/**
 * Logical header ('H') record of the firm-banking fixed-length file
 * (refactor of legacy {@code header.Deposit_Header}).
 *
 * <p>This holds the <b>logical</b> fields only. The physical byte layout
 * (offsets, lengths, filler, {@code \r\n}) is intentionally NOT encoded here —
 * see {@link FixedLengthCodec}. <b>TODO(K3):</b> byte layout pending format
 * version + sample (B6).
 *
 * <p>Fields per handover §4.4 / legacy {@code Deposit_Header}: record id 'H',
 * bank/organisation code, transaction date, withdrawal (master) account, fund
 * type, transaction type, file sequence.
 */
public record DepositHeader(
        String bankCd,
        String tranDt,
        String wdrlActNo,
        String fundTp,
        String tranTp,
        String fileSeq,
        BigDecimal reqCnt,
        BigDecimal reqAmt
) {
}
