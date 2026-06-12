package com.kosign.wecms.domain.payment.dispatch.service;

/**
 * Input for a {@code WGVN_EFS_203} processing-result inquiry (REST API guide
 * ver 1.2.2 §5.5). Used both by the real-time transfer recovery path
 * ({@link RealtimeTransferService} on {@code RC=G999}) and — once the cron is
 * enabled (D4) — by the scheduled poll over aged {@code PROC_STAT='6'} rows.
 *
 * <p>The caller supplies the per-tenant firm-banking context plus the locator of
 * the original transaction to confirm. This keeps {@link ResultCheckService}
 * decoupled from {@code TB_FIRM_TRAN} row resolution (no DB status mutation in
 * this phase — D2/D3 scope).
 *
 * @param vanOrgCd  VAN org code — looks up the AES/HMAC key ({@code TB_VAN_API_INFO})
 *                  and is the envelope {@code ID} source
 * @param bankCd    outbound bank code ({@code bank_cd})
 * @param prtnId    bank partner id ({@code prtn_id})
 * @param orgTranDt original transaction date ({@code org_tran_dt}, {@code yyyyMMdd})
 * @param orgTno    original transaction number ({@code org_tno} — the prior {@code TNO})
 * @param macctNo   master (withdrawal) account ({@code macct_no})
 */
public record ResultCheckInquiryCommand(
        String vanOrgCd,
        String bankCd,
        String prtnId,
        String orgTranDt,
        String orgTno,
        String macctNo
) {
}
