package com.kosign.wecms.domain.payment.dispatch.van.dto;

/**
 * {@code WGVN_EFS_202} — real-time single transfer, request body (decrypted
 * {@code EV} JSON). Base fields from REST API guide ver 1.2.2 §5.4; the FX block
 * ({@code withd_curren_cd} … {@code dep_amt_dcm}) was added in <b>ver 1.2.6 §5.4</b>
 * and is populated from a preceding {@code WGVN_EFS_204} inquiry on the Woori /
 * non-VND path (see {@link com.kosign.wecms.domain.payment.dispatch.service.RealtimeTransferService}).
 *
 * <p>Mirrors the legacy {@code NAPAS_REAL_TIME_BATCH} {@code param_202} build
 * ({@code NAPAS_REAL_TIME_BATCH.java:528-565}): the FX fields are always present
 * on the wire — empty strings on the ordinary domestic-VND path, the 204
 * response values on the Woori-FX path. The optional {@code tar_bank_nm} field is
 * intentionally NOT modelled: legacy omits it and the gateway auto-matches the
 * VCB target bank when it is absent (guide §5.4 memo).
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
 * @param withdCurrenCd   {@code withd_curren_cd}  withdrawal currency (FX block, v1.2.6; {@code USD} on Woori-FX)
 * @param depCurrenCd     {@code dep_curren_cd}    deposit currency (FX block, v1.2.6; {@code VND}) — from 204
 * @param aplExcRateItg   {@code apl_exc_rate_itg} applied exchange rate, integer part — from 204
 * @param aplExcRateDcm   {@code apl_exc_rate_dcm} applied exchange rate, decimal portion — from 204
 * @param withdAmtItg     {@code withd_amt_itg}    withdrawal amount, integer part — from 204
 * @param withdAmtDcm     {@code withd_amt_dcm}    withdrawal amount, decimal portion — from 204
 * @param depAmtItg       {@code dep_amt_itg}      deposit amount, integer part — from 204
 * @param depAmtDcm       {@code dep_amt_dcm}      deposit amount, decimal portion — from 204
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
        String antDivCd,
        String withdCurrenCd,
        String depCurrenCd,
        String aplExcRateItg,
        String aplExcRateDcm,
        String withdAmtItg,
        String withdAmtDcm,
        String depAmtItg,
        String depAmtDcm
) {
}
