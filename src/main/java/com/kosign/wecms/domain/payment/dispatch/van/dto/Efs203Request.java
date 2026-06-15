package com.kosign.wecms.domain.payment.dispatch.van.dto;

/**
 * {@code WGVN_EFS_203} — processing-result inquiry, request body (decrypted
 * {@code EV} JSON). Fields from REST API guide ver 1.2.2 §5.5. Used to confirm
 * the true outcome of a 202 transfer that returned {@code G999}.
 *
 * @param bankCd    {@code bank_cd}
 * @param prtnId    {@code prtn_id}
 * @param orgTranDt {@code org_tran_dt} original transaction date
 * @param orgTno    {@code org_tno}     original transaction number (the prior {@code TNO})
 * @param macctNo   {@code macct_no}    master account
 */
public record Efs203Request(
        String bankCd,
        String prtnId,
        String orgTranDt,
        String orgTno,
        String macctNo
) {
}
