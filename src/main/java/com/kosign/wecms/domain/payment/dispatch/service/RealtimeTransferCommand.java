package com.kosign.wecms.domain.payment.dispatch.service;

/**
 * Input for a {@code WGVN_EFS_202} real-time single transfer (REST API guide
 * ver 1.2.2 §5.4) — refactor of the legacy {@code NAPAS_REAL_TIME_BATCH} request.
 *
 * <p>The caller supplies the per-tenant firm-banking context. This command keeps
 * {@link RealtimeTransferService} decoupled from {@code TB_FIRM_TRAN} row
 * resolution so D2 does not write or read DB status in this phase.
 *
 * @param vanOrgCd       VAN org code — looks up the AES/HMAC key ({@code TB_VAN_API_INFO})
 *                       and is the envelope {@code ID} source
 * @param bankCd         outbound bank code ({@code bank_cd})
 * @param prtnId         bank partner id ({@code prtn_id})
 * @param currenCd       currency ({@code curren_cd})
 * @param amt            transfer amount, integer part ({@code amt})
 * @param dcmAmt         transfer amount, decimal portion ({@code dcm_amt})
 * @param tarFincMdaDiv  target media division — {@code A}=account / {@code C}=card
 * @param macctNo        master (withdrawal) account ({@code macct_no})
 * @param tarBankCd      target bank code ({@code tar_bank_cd})
 * @param tarFincMdaNo   target account/card number ({@code tar_finc_mda_no})
 * @param fincMdaOwnerNm target owner name (optional; {@code finc_mda_owner_nm})
 * @param tranUse        transfer purpose/memo (optional; {@code tran_use})
 * @param antDivCd       network ({@code ant_div_cd}) — {@code INTER} / {@code CITAD} / {@code NAPAS}
 * @param withdCurrenCd  withdrawal currency ({@code withd_curren_cd}; legacy {@code WITHD_CCY_CD}).
 *                       Drives the Woori {@code WGVN_EFS_204} FX pre-inquiry (USD on that path) and
 *                       is echoed into the 202 FX block. May be null/blank on the domestic-VND path.
 */
public record RealtimeTransferCommand(
        String vanOrgCd,
        String bankCd,
        String prtnId,
        String currenCd,
        String amt,
        String dcmAmt,
        String tarFincMdaDiv,
        String macctNo,
        String tarBankCd,
        String tarFincMdaNo,
        String fincMdaOwnerNm,
        String tranUse,
        String antDivCd,
        String withdCurrenCd
) {
}
