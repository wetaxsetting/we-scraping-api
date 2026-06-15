package com.kosign.wecms.domain.payment.dispatch.van.dto;

/**
 * {@code WGVN_EFS_204} — exchange-transfer (FX-rate) info inquiry, response body
 * (decrypted {@code EV} JSON, returned on {@code RC=0000}). Fields from REST API
 * guide <b>ver 1.2.6 §5.6</b> ({@code 환전이체 정보 조회}).
 *
 * <p>These seven fields are the FX values fed into the subsequent
 * {@code WGVN_EFS_202} request's FX block (mirrors the legacy
 * {@code NAPAS_REAL_TIME_BATCH} {@code rs_EV_204} → {@code param_202} feed at
 * {@code NAPAS_REAL_TIME_BATCH.java:381-397, 553-565}).
 *
 * @param aplExcRateItg {@code apl_exc_rate_itg} applied exchange rate, integer part
 * @param aplExcRateDcm {@code apl_exc_rate_dcm} applied exchange rate, decimal portion
 * @param withdAmtItg   {@code withd_amt_itg}    withdrawal amount, integer part
 * @param withdAmtDcm   {@code withd_amt_dcm}    withdrawal amount, decimal portion (optional)
 * @param depCurrenCd   {@code dep_curren_cd}    deposit currency ({@code VND})
 * @param depAmtItg     {@code dep_amt_itg}      deposit amount, integer part
 * @param depAmtDcm     {@code dep_amt_dcm}      deposit amount, decimal portion (optional)
 */
public record Efs204Response(
        String aplExcRateItg,
        String aplExcRateDcm,
        String withdAmtItg,
        String withdAmtDcm,
        String depCurrenCd,
        String depAmtItg,
        String depAmtDcm
) {
}
