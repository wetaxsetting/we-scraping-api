package com.kosign.wecms.domain.payment.dispatch.van.dto;

/**
 * {@code WGVN_EFS_302} — bulk-transfer execution, response body (decrypted
 * {@code EV} JSON). Echoes the request header totals (REST API guide ver 1.2.2 §5.11).
 *
 * @param bankCd     {@code bank_cd}
 * @param prtnId     {@code prtn_id}
 * @param fileNm     {@code file_nm}
 * @param dataCnt    {@code data_cnt}
 * @param dataAmt    {@code data_amt}
 * @param dataDcmAmt {@code data_dcm_amt}
 */
public record Efs302Response(
        String bankCd,
        String prtnId,
        String fileNm,
        String dataCnt,
        String dataAmt,
        String dataDcmAmt
) {
}
