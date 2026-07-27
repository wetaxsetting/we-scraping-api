package com.kosign.wecms.domain.payment.dispatch.callback.service;

/**
 * Input for the {@code WGVN_EFS_302} bulk-transfer execution <b>API request leg</b>
 * (REST API guide ver 1.2.2 §5.11) — refactor of the legacy
 * {@code FILE_DEPOSIT_TRANSFER_BATCH2} 00302 call.
 *
 * <p>Same header-totals shape as 301; the per-line detail rides in the SFTP file.
 * <b>When</b> to issue 302 (idempotently, inline from the 01301 callback) and its
 * recovery backstop are the open <b>K9</b> decision — this command only describes
 * <i>what</i> the API leg sends, not <i>when</i> it fires.
 *
 * @param vanOrgCd   VAN org code — looks up the AES/HMAC key ({@code TB_VAN_API_INFO})
 *                   and is the envelope {@code ID} source
 * @param bankCd     outbound bank code ({@code bank_cd})
 * @param prtnId     bank partner id ({@code prtn_id})
 * @param fileNm     firm file name ({@code file_nm})
 * @param dataCnt    record count ({@code data_cnt})
 * @param dataAmt    total amount, integer part ({@code data_amt})
 * @param dataDcmAmt total amount, decimal portion ({@code data_dcm_amt})
 * @param tno        gateway {@code TNO} — the allocated {@code TB_FIRM_BAT_API_TRAN.TRAN_SEQ}
 *                   (A1 / audit X-2). Legacy {@code FILE_DEPOSIT_TRANSFER_BATCH2} sends the
 *                   execution request's {@code seq} (= its stored {@code TRAN_SEQ}) as the TNO.
 */
public record BulkExecutionApiCommand(
        String vanOrgCd,
        String bankCd,
        String prtnId,
        String fileNm,
        String dataCnt,
        String dataAmt,
        String dataDcmAmt,
        String tno
) {
}
