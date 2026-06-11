package com.kosign.wecms.domain.payment.dispatch.van.dto;

/**
 * {@code WGVN_EFS_202} — real-time transfer, response body (decrypted {@code EV}
 * JSON, returned on {@code RC=0000}). Fields from REST API guide ver 1.2.2 §5.4.
 *
 * <p>Per §5.4, if the call returns {@code RC=G999} (no definitive response) the
 * caller must confirm via {@code WGVN_EFS_203} — that recovery is service-layer
 * logic (a later phase), not modelled here.
 *
 * @param balAmtSign     {@code bal_amt_sign} balance sign — {@code 0}=+ / {@code 1}=-
 * @param balAmt         {@code bal_amt}      post-transfer balance (integer part)
 * @param balDcmAmt      {@code bal_dcm_amt}  balance decimal portion
 * @param fincMdaOwnerNm {@code finc_mda_owner_nm} owner name
 * @param antDivCd       {@code ant_div_cd}   {@code INTER} / {@code CITAD} / {@code NAPAS}
 */
public record Efs202Response(
        String balAmtSign,
        String balAmt,
        String balDcmAmt,
        String fincMdaOwnerNm,
        String antDivCd
) {
}
