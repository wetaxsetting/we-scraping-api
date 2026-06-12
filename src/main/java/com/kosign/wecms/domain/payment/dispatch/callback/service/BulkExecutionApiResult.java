package com.kosign.wecms.domain.payment.dispatch.callback.service;

import com.kosign.wecms.domain.payment.dispatch.van.dto.Efs302Response;

/**
 * Result of a successful ({@code RC=0000}) {@code WGVN_EFS_302} execution request —
 * the decrypted {@link Efs302Response} echoed header totals (REST API guide
 * ver 1.2.2 §5.11).
 *
 * <p>This is the synchronous <i>execution acknowledgement</i> only. The
 * per-line transfer outcomes arrive later via the 01302 Return-URL callback +
 * the SFTP result file ({@code ~/rdata/}); parsing those (K3 + B4) is out of
 * this phase's scope.
 *
 * @param bankCd     {@code bank_cd}
 * @param prtnId     {@code prtn_id}
 * @param fileNm     {@code file_nm}
 * @param dataCnt    {@code data_cnt}
 * @param dataAmt    {@code data_amt}
 * @param dataDcmAmt {@code data_dcm_amt}
 */
public record BulkExecutionApiResult(
        String bankCd,
        String prtnId,
        String fileNm,
        String dataCnt,
        String dataAmt,
        String dataDcmAmt
) {

    /** Map a decrypted {@code WGVN_EFS_302} response onto the typed result. */
    static BulkExecutionApiResult from(Efs302Response r) {
        return new BulkExecutionApiResult(
                r.bankCd(), r.prtnId(), r.fileNm(), r.dataCnt(), r.dataAmt(), r.dataDcmAmt());
    }
}
