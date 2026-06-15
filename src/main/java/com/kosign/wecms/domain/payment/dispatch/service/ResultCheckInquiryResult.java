package com.kosign.wecms.domain.payment.dispatch.service;

import com.kosign.wecms.domain.payment.dispatch.van.dto.Efs203Response;

/**
 * Result of a successful ({@code RC=0000}) {@code WGVN_EFS_203} processing-result
 * inquiry — the decrypted {@link Efs203Response} mapped to a typed carrier
 * (REST API guide ver 1.2.2 §5.5).
 *
 * <p>{@link #orgRespCd} / {@link #orgRespMsg} carry the <i>true</i> outcome of the
 * original transaction (the whole point of the 203 call after a 202 {@code G999}).
 * The decision to map a given {@code org_resp_cd} onto a {@code PROC_STAT} value
 * stays with the caller — this phase (D2/D3) does NOT write any DB status.
 *
 * @param orgTranDt      {@code org_tran_dt}     original transaction date
 * @param orgTno         {@code org_tno}         original transaction number
 * @param orgProcTm      {@code org_proc_tm}     original processing time
 * @param orgRespCd      {@code org_resp_cd}     original response code (true outcome)
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
public record ResultCheckInquiryResult(
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

    /** Map a decrypted {@code WGVN_EFS_203} response onto the typed result. */
    static ResultCheckInquiryResult from(Efs203Response r) {
        return new ResultCheckInquiryResult(
                r.orgTranDt(), r.orgTno(), r.orgProcTm(), r.orgRespCd(), r.orgRespMsg(),
                r.macctNo(), r.currenCd(), r.amt(), r.dcmAmt(), r.tarFincMdaNo(),
                r.fincMdaOwnerNm(), r.withdAcctRmrk(), r.tarAcctRmrk());
    }
}
