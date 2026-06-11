package com.kosign.wecms.domain.payment.dispatch.recipient;

/**
 * Input for a {@code WGVN_EFS_201} account-holder (depositor) name inquiry.
 *
 * <p>The caller supplies the per-tenant firm-banking context. In the legacy
 * flow these come from the active {@code TB_FIRM_CONF} row (see
 * {@link com.kosign.wecms.common.firmbanking.FirmBankingInquiryService}); this
 * command keeps {@link RecipientLookupService} decoupled from that resolution so
 * D1 does not change the existing seam's behaviour.
 *
 * @param vanOrgCd       VAN org code — used both to look up the AES/HMAC key
 *                       ({@code TB_VAN_API_INFO}) and as the envelope {@code ID}
 *                       source (legacy "ID (VAN_ORG_CD)")
 * @param bankCd         outbound bank code ({@code bank_cd} / {@code OUT_BANK_CD})
 * @param prtnId         bank partner id ({@code prtn_id} / {@code BANK_PRTN_ID})
 * @param tarBankCd      target NAPAS/CITAD bank code being inquired ({@code tar_bank_cd})
 * @param tarFincMdaDiv  target media division — {@code A}=account / {@code C}=card
 * @param tarFincMdaNo   target account/card number being inquired
 * @param withdAcctNo    firm's own withdrawal account (optional; {@code withd_acct_no})
 */
public record RecipientLookupCommand(
        String vanOrgCd,
        String bankCd,
        String prtnId,
        String tarBankCd,
        String tarFincMdaDiv,
        String tarFincMdaNo,
        String withdAcctNo
) {
}
