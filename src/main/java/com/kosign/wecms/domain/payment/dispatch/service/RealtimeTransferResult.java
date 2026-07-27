package com.kosign.wecms.domain.payment.dispatch.service;

import com.kosign.wecms.domain.payment.dispatch.van.dto.Efs202Response;

/**
 * Result of a {@code WGVN_EFS_202} real-time transfer (REST API guide ver 1.2.2
 * §5.4). Two shapes, distinguished by {@link #pending()}:
 *
 * <ul>
 *   <li><b>Direct success</b> ({@code RC=0000}, VV-verified, {@code pending=false}) — the
 *       balance fields come straight from the decrypted {@link Efs202Response}.</li>
 *   <li><b>Pending / no definitive response</b> ({@code pending=true}) — the 202 returned
 *       {@code G999}, no response, or an unverifiable ({@code VV}-mismatch) response. Per
 *       §5.4 this is NOT a failure and must NOT be finalized: the caller leaves both
 *       {@code TB_FIRM_TRAN} and {@code TB_PAY_TARGET} at {@code PROC_STAT='6'} and the
 *       scheduled {@link ResultCheckService} confirms the true outcome via a later
 *       {@code WGVN_EFS_203} inquiry (audit B2 / H-4). The balance fields are {@code null}.
 *       This replaces the earlier <i>synchronous</i> inline-203 recovery, which risked a
 *       premature FAIL while the bank was still settling.</li>
 * </ul>
 *
 * <p>A definite gateway business error (non-{@code 0000}, non-{@code G999}) is surfaced as
 * {@code WBE_EFS_GATEWAY_ERROR} by {@link RealtimeTransferService}, not represented here.
 *
 * @param pending        {@code true} = no definitive response (G999 / no-response / VV-fail) → leave '6'
 * @param balAmtSign     {@code bal_amt_sign} balance sign — {@code 0}=+ / {@code 1}=- (direct success only)
 * @param balAmt         {@code bal_amt}      post-transfer balance, integer part (direct success only)
 * @param balDcmAmt      {@code bal_dcm_amt}  balance decimal portion (direct success only)
 * @param fincMdaOwnerNm {@code finc_mda_owner_nm} owner name (direct success only)
 * @param antDivCd       {@code ant_div_cd}   network (direct success only)
 */
public record RealtimeTransferResult(
        boolean pending,
        String balAmtSign,
        String balAmt,
        String balDcmAmt,
        String fincMdaOwnerNm,
        String antDivCd
) {

    /** Direct {@code RC=0000} success — map the decrypted 202 response. */
    static RealtimeTransferResult ofTransfer(Efs202Response r) {
        return new RealtimeTransferResult(
                false, r.balAmtSign(), r.balAmt(), r.balDcmAmt(), r.fincMdaOwnerNm(), r.antDivCd());
    }

    /**
     * No definitive response ({@code G999} / no-response / {@code VV}-verify failure) — leave the
     * transaction {@code PROC_STAT='6'} for the scheduled result-check to reconcile (audit B2).
     */
    static RealtimeTransferResult ofPending() {
        return new RealtimeTransferResult(true, null, null, null, null, null);
    }
}
