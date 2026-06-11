package com.kosign.wecms.domain.payment.dispatch.service;

import com.kosign.wecms.domain.payment.dispatch.van.dto.Efs202Response;

/**
 * Result of a {@code WGVN_EFS_202} real-time transfer (REST API guide ver 1.2.2
 * §5.4). Two shapes, distinguished by {@link #recovered()}:
 *
 * <ul>
 *   <li><b>Direct success</b> ({@code RC=0000}, {@code recovered=false}) — the
 *       balance fields come straight from the decrypted {@link Efs202Response};
 *       {@link #recovery()} is {@code null}.</li>
 *   <li><b>G999 recovery</b> ({@code recovered=true}) — the 202 returned no
 *       definitive response, so the true outcome was confirmed via a follow-up
 *       {@code WGVN_EFS_203} ({@link ResultCheckService#inquire}). The balance
 *       fields are {@code null} and {@link #recovery()} carries the 203 result
 *       (whose {@code org_resp_cd} is the real outcome).</li>
 * </ul>
 *
 * <p>This phase (D2/D3) deliberately does NOT translate the outcome into a
 * {@code PROC_STAT} write — that mapping (and the no-response⇒{@code '6'} legacy
 * semantics) lands when the {@code TB_FIRM_TRAN} status seam is wired.
 *
 * @param recovered      {@code true} when resolved via the {@code G999}→203 recovery path
 * @param balAmtSign     {@code bal_amt_sign} balance sign — {@code 0}=+ / {@code 1}=- (direct success only)
 * @param balAmt         {@code bal_amt}      post-transfer balance, integer part (direct success only)
 * @param balDcmAmt      {@code bal_dcm_amt}  balance decimal portion (direct success only)
 * @param fincMdaOwnerNm {@code finc_mda_owner_nm} owner name (direct success only)
 * @param antDivCd       {@code ant_div_cd}   network (direct success only)
 * @param recovery       the confirming 203 result (recovery only); {@code null} on direct success
 */
public record RealtimeTransferResult(
        boolean recovered,
        String balAmtSign,
        String balAmt,
        String balDcmAmt,
        String fincMdaOwnerNm,
        String antDivCd,
        ResultCheckInquiryResult recovery
) {

    /** Direct {@code RC=0000} success — map the decrypted 202 response. */
    static RealtimeTransferResult ofTransfer(Efs202Response r) {
        return new RealtimeTransferResult(
                false, r.balAmtSign(), r.balAmt(), r.balDcmAmt(), r.fincMdaOwnerNm(), r.antDivCd(), null);
    }

    /** {@code RC=G999} resolved via a follow-up 203 inquiry. */
    static RealtimeTransferResult ofRecovery(ResultCheckInquiryResult recovery) {
        return new RealtimeTransferResult(true, null, null, null, null, null, recovery);
    }
}
