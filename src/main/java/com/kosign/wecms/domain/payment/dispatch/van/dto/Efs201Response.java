package com.kosign.wecms.domain.payment.dispatch.van.dto;

/**
 * {@code WGVN_EFS_201} — depositor name inquiry, response body (decrypted
 * {@code EV} JSON). Fields from REST API guide ver 1.2.2 §5.3 (returned on {@code RC=0000}).
 *
 * @param fincMdaOwnerNm {@code finc_mda_owner_nm} resolved account-holder name
 * @param antDivCd       {@code ant_div_cd}        network — {@code INTER} / {@code CITAD} / {@code NAPAS}
 */
public record Efs201Response(
        String fincMdaOwnerNm,
        String antDivCd
) {
}
