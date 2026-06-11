package com.kosign.wecms.domain.payment.dispatch.van.dto;

/**
 * {@code WGVN_EFS_302} — bulk-transfer execution request, request body
 * (decrypted {@code EV} JSON). Fields from REST API guide ver 1.2.2 §5.11 (same
 * shape as 301: header totals; details are in the SFTP file).
 *
 * <p><b>Note:</b> this DTO models the <b>API request leg only</b>. <i>When</i> to
 * issue 302 (inline from the 01301 callback) and its recovery are the open
 * <b>K9</b> decision — not modelled here.
 *
 * @param bankCd     {@code bank_cd}
 * @param prtnId     {@code prtn_id}
 * @param fileNm     {@code file_nm}
 * @param dataCnt    {@code data_cnt}
 * @param dataAmt    {@code data_amt}
 * @param dataDcmAmt {@code data_dcm_amt}
 */
public record Efs302Request(
        String bankCd,
        String prtnId,
        String fileNm,
        String dataCnt,
        String dataAmt,
        String dataDcmAmt
) {
}
