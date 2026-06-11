package com.kosign.wecms.domain.payment.dispatch.van.dto;

/**
 * {@code WGVN_EFS_203} — processing-result inquiry, response body (decrypted
 * {@code EV} JSON, returned on {@code RC=0000}). Fields from REST API guide
 * ver 1.2.2 §5.5.
 *
 * @param orgTranDt      {@code org_tran_dt}     original transaction date
 * @param orgTno         {@code org_tno}         original transaction number
 * @param orgProcTm      {@code org_proc_tm}     original processing time
 * @param orgRespCd      {@code org_resp_cd}     original response code
 * @param orgRespMsg     {@code org_resp_msg}    original response message
 * @param macctNo        {@code macct_no}        master account
 * @param currenCd       {@code curren_cd}       currency
 * @param amt            {@code amt}             amount (integer part)
 * @param dcmAmt         {@code dcm_amt}         decimal portion
 * @param tarFincMdaNo   {@code tar_finc_mda_no} target account/card number
 * @param fincMdaOwnerNm {@code finc_mda_owner_nm} owner name
 * @param withdAcctRmrk  {@code withd_acct_rmrk} withdrawal-side remark
 * @param tarAcctRmrk    {@code tar_acct_rmrk}   target-side remark
 */
public record Efs203Response(
        String orgTranDt,
        String orgTno,
        String orgProcTm,
        String orgRespCd,
        String orgRespMsg,
        String macctNo,
        String currenCd,
        String amt,
        String dcmAmt,
        String tarFincMdaNo,
        String fincMdaOwnerNm,
        String withdAcctRmrk,
        String tarAcctRmrk
) {
}
