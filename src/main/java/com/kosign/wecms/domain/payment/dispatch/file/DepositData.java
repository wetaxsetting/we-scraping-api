package com.kosign.wecms.domain.payment.dispatch.file;

import java.math.BigDecimal;

/**
 * Logical detail ('D') record of the firm-banking fixed-length file
 * (refactor of legacy {@code data.Deposit_Data}). One per beneficiary line.
 *
 * <p>Logical fields only — the physical byte layout lives in
 * {@link FixedLengthCodec}. <b>TODO(K3):</b> the offsets/lengths AND which of the
 * version-added fields are present depend on the bank's active format version
 * (2025.07 added {@code rcptBankCntryCd}/{@code remitCcyCd}; 2025.10.29 added
 * {@code feeBrdnClsCd} and removed {@code filler(183)}). Confirm against a sample
 * (B6) before encoding/parsing.
 *
 * <p>Field set mirrors {@code TB_FIRM_FILE_DATA} (verified column list,
 * 2026-06-08). Names tone-removal ({@code StrUtil.replaceTone}) applies to
 * {@code senderNm}/{@code fincMdaOwnerNm} during encode — also TODO(K3).
 */
public record DepositData(
        BigDecimal seq,
        String tarBankCd,
        String fincMdaDiv,
        String fincMdaNo,
        String trscCcyCd,
        BigDecimal tranAmt,
        String bankChnlDiv,
        String antDivCd,
        String senderNm,
        String fincMdaOwnerNm,
        String tarAcctRmrk,
        String wdrlAcctRmrk,
        String rcptBankCntryCd,
        String remitCcyCd,
        String rcptUseAddr,
        String feeBrdnClsCd
) {
}
