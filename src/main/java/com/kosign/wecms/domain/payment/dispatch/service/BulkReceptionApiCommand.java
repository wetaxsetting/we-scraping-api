package com.kosign.wecms.domain.payment.dispatch.service;

/**
 * Input for the {@code WGVN_EFS_301} bulk-transfer reception <b>API request leg</b>
 * (REST API guide ver 1.2.2 §5.9) — refactor of the legacy
 * {@code FILE_DEPOSIT_TRANSFER_BATCH1} 00301 call.
 *
 * <p>Carries only the header totals that ride on the API; the actual transactions
 * travel in the SFTP file named by {@code fileNm}. Generating that fixed-length
 * file (K3) and uploading it (B4) are <b>not</b> part of this leg — the caller is
 * responsible for having produced + uploaded the file before invoking the leg.
 *
 * @param vanOrgCd   VAN org code — looks up the AES/HMAC key ({@code TB_VAN_API_INFO})
 *                   and is the envelope {@code ID} source
 * @param bankCd     outbound bank code ({@code bank_cd})
 * @param prtnId     bank partner id ({@code prtn_id})
 * @param fileNm     firm file name ({@code file_nm}) — names the SFTP-uploaded file
 * @param dataCnt    record count ({@code data_cnt})
 * @param dataAmt    total amount, integer part ({@code data_amt})
 * @param dataDcmAmt total amount, decimal portion ({@code data_dcm_amt})
 * @param tno        gateway {@code TNO} — the allocated {@code TB_FIRM_BAT_API_TRAN.TRAN_SEQ}
 *                   (A1 / audit X-2). Legacy {@code FILE_DEPOSIT_TRANSFER_BATCH1} sends the
 *                   reception request's {@code seq} (= its stored {@code TRAN_SEQ}) as the TNO, so
 *                   the gateway record correlates 1:1 to the audit row — never a UUID.
 */
public record BulkReceptionApiCommand(
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
