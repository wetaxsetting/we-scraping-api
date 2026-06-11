package com.kosign.wecms.domain.payment.dispatch.van.dto;

/**
 * {@code WGVN_EFS_201} — account-holder (depositor) name inquiry, request body
 * (the decrypted {@code EV} JSON). Fields from REST API guide ver 1.2.2 §5.3.
 *
 * <p>Plain camelCase record: the production global {@code SNAKE_CASE} strategy
 * serialises these as the vendor's snake_case business keys
 * ({@code bankCd → bank_cd}, {@code tarFincMdaDiv → tar_finc_mda_div}, …) — no
 * {@code @JsonProperty} needed. All fields {@code String} (consistent with the
 * sibling callback DTOs); numeric wire-formatting, if any, is a real-adapter / B5 concern.
 *
 * @param bankCd          {@code bank_cd}          outbound bank code
 * @param prtnId          {@code prtn_id}          partner id
 * @param tarBankCd       {@code tar_bank_cd}      target bank code
 * @param tarFincMdaDiv   {@code tar_finc_mda_div} target media division — {@code A}=account / {@code C}=card
 * @param tarFincMdaNo    {@code tar_finc_mda_no}  target account/card number
 * @param fincMdaOwnerNm  {@code finc_mda_owner_nm} owner name (optional)
 * @param withdAcctNo     {@code withd_acct_no}    withdrawal account (optional)
 */
public record Efs201Request(
        String bankCd,
        String prtnId,
        String tarBankCd,
        String tarFincMdaDiv,
        String tarFincMdaNo,
        String fincMdaOwnerNm,
        String withdAcctNo
) {
}
