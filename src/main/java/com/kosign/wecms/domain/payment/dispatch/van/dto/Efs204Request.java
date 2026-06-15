package com.kosign.wecms.domain.payment.dispatch.van.dto;

/**
 * {@code WGVN_EFS_204} — exchange-transfer (FX-rate) info inquiry, request body
 * (decrypted {@code EV} JSON). Fields from REST API guide <b>ver 1.2.6 §5.6</b>
 * ({@code 환전이체 정보 조회}).
 *
 * <p><b>Woori-only, pre-transfer step.</b> Per §5.6 this is the applied-rate
 * inquiry the caller runs <i>before</i> a {@code WGVN_EFS_202} transfer when the
 * outbound bank is Woori ({@code bank_cd = 970457}) and the transfer currency is
 * not {@code VND}. The returned FX fields feed the 202 request's FX block.
 * Refactor of the legacy {@code NAPAS_REAL_TIME_BATCH} {@code param_204} build
 * (mirrors {@code NAPAS_REAL_TIME_BATCH.java:335-343}).
 *
 * <p>Plain camelCase record → global {@code SNAKE_CASE} business keys. All fields
 * {@code String} (consistent with the sibling 201/202/203 DTOs); numeric
 * formatting, if any, is a real-adapter concern.
 *
 * @param bankCd        {@code bank_cd}         Woori bank code ({@code 970457})
 * @param prtnId        {@code prtn_id}         bank partner id
 * @param withdAcctNo   {@code withd_acct_no}   withdrawal (master) account — legacy {@code WDRL_ACT_NO}
 * @param withdCurrenCd {@code withd_curren_cd} withdrawal currency ({@code USD} fixed) — legacy {@code WITHD_CCY_CD}
 * @param depAcctNo     {@code dep_acct_no}     deposit (target) account — legacy {@code FINC_MDA_NO}
 * @param tranAmtItg    {@code tran_amt_itg}    transfer amount, integer part
 * @param tranAmtDcm    {@code tran_amt_dcm}    transfer amount, decimal portion (optional)
 * @param tranCurrenCd  {@code tran_curren_cd}  transfer currency ({@code VND}) — legacy {@code TRSC_CCY_CD}
 */
public record Efs204Request(
        String bankCd,
        String prtnId,
        String withdAcctNo,
        String withdCurrenCd,
        String depAcctNo,
        String tranAmtItg,
        String tranAmtDcm,
        String tranCurrenCd
) {
}
