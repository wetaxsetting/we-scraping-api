package com.kosign.wecms.domain.payment.dispatch.van.dto;

/**
 * {@code WGVN_EFS_202} — real-time single transfer, request body (decrypted
 * {@code EV} JSON). Fields from REST API guide ver 1.2.2 §5.4.
 *
 * <p>Plain camelCase record → global {@code SNAKE_CASE} business keys. All fields
 * {@code String} (numeric formatting deferred to the real adapter / B5).
 *
 * @param bankCd          {@code bank_cd}
 * @param prtnId          {@code prtn_id}
 * @param currenCd        {@code curren_cd}        currency
 * @param amt             {@code amt}              transfer amount (integer part)
 * @param dcmAmt          {@code dcm_amt}          decimal portion
 * @param tarFincMdaDiv   {@code tar_finc_mda_div} {@code A}=account / {@code C}=card
 * @param macctNo         {@code macct_no}         master (withdrawal) account
 * @param tarBankCd       {@code tar_bank_cd}      target bank code
 * @param tarFincMdaNo    {@code tar_finc_mda_no}  target account/card number
 * @param fincMdaOwnerNm  {@code finc_mda_owner_nm} owner name (optional)
 * @param tranUse         {@code tran_use}         transfer purpose/memo (optional)
 * @param antDivCd        {@code ant_div_cd}       {@code INTER} / {@code CITAD} / {@code NAPAS}
 */
public record Efs202Request(
        String bankCd,
        String prtnId,
        String currenCd,
        String amt,
        String dcmAmt,
        String tarFincMdaDiv,
        String macctNo,
        String tarBankCd,
        String tarFincMdaNo,
        String fincMdaOwnerNm,
        String tranUse,
        String antDivCd
) {
}
