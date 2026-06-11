package com.kosign.wecms.domain.payment.dispatch.service;

import com.kosign.wecms.domain.payment.dispatch.van.dto.Efs301Response;

/**
 * Result of a successful ({@code RC=0000}) {@code WGVN_EFS_301} reception request —
 * the decrypted {@link Efs301Response} echoed header totals (REST API guide
 * ver 1.2.2 §5.9).
 *
 * <p>This is the synchronous <i>reception acknowledgement</i> only. The
 * definitive per-file outcome arrives later via the 01301 Return-URL callback
 * ({@code BulkReceptionResultService}); reconciling that against these totals and
 * advancing {@code PROC_STEP}/{@code PROC_STAT} is out of this phase's scope.
 *
 * @param bankCd     {@code bank_cd}
 * @param prtnId     {@code prtn_id}
 * @param fileNm     {@code file_nm}
 * @param dataCnt    {@code data_cnt}
 * @param dataAmt    {@code data_amt}
 * @param dataDcmAmt {@code data_dcm_amt}
 */
public record BulkReceptionApiResult(
        String bankCd,
        String prtnId,
        String fileNm,
        String dataCnt,
        String dataAmt,
        String dataDcmAmt
) {

    /** Map a decrypted {@code WGVN_EFS_301} response onto the typed result. */
    static BulkReceptionApiResult from(Efs301Response r) {
        return new BulkReceptionApiResult(
                r.bankCd(), r.prtnId(), r.fileNm(), r.dataCnt(), r.dataAmt(), r.dataDcmAmt());
    }
}
