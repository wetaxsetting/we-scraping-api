package com.kosign.wecms.domain.payment.dispatch.van.dto;

/**
 * {@code WGVN_EFS_301} — bulk-transfer reception request, request body
 * (decrypted {@code EV} JSON). Fields from REST API guide ver 1.2.2 §5.9. The
 * actual transactions ride in the SFTP file named by {@code file_nm}; this API
 * carries only the header totals.
 *
 * <p><b>Note:</b> this DTO models the <b>API request leg only</b> — generating
 * the fixed-length file (K3) and the SFTP upload (B4) are out of scope here.
 *
 * @param bankCd     {@code bank_cd}
 * @param prtnId     {@code prtn_id}
 * @param fileNm     {@code file_nm}      firm file name
 * @param dataCnt    {@code data_cnt}     record count
 * @param dataAmt    {@code data_amt}     total amount (integer part)
 * @param dataDcmAmt {@code data_dcm_amt} total decimal portion
 */
public record Efs301Request(
        String bankCd,
        String prtnId,
        String fileNm,
        String dataCnt,
        String dataAmt,
        String dataDcmAmt
) {
}
