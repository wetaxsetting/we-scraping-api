package com.kosign.wecms.domain.payment.validation;

/**
 * Field code keying {@code BLOCKED_CHARS_RULE.FIELD_CD}. Mirrors legacy
 * {@code wabooks.enums.FieldCd}.
 *
 * <ul>
 *   <li>{@link #REMARKS}  — TRAN_USAG / WDRL_ACCT_RMRK</li>
 *   <li>{@link #RECIPIENT_NAME} — RCPT_NM / FINC_MDA_OWNER_NM</li>
 *   <li>{@link #MEMO} — Overseas MEMO field</li>
 *   <li>{@link #BENEFICIARY_NAME} — Overseas BNFC_NM</li>
 *   <li>{@link #BENEFICIARY_ADDRESS} — Overseas BNFC_ADDR</li>
 * </ul>
 */
public enum FieldCd {
    REMARKS             ("RMKS"),
    RECIPIENT_NAME      ("RCPT"),
    MEMO                ("MEMO"),
    BENEFICIARY_NAME    ("BENA"),
    BENEFICIARY_ADDRESS ("BEAD");

    private final String code;

    FieldCd(String code) { this.code = code; }

    public String code() { return code; }
}
