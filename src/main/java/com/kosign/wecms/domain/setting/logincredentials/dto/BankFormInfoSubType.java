package com.kosign.wecms.domain.setting.logincredentials.dto;

/**
 * One sub-type record returned by the bank form-info endpoint.
 * Mirrors the legacy {@code nxisas_getCertFormInfo} REC shape but
 * flattens its REC1 nested record into named columns. Each row
 * corresponds to one {@code SEQ_NO} value for the bank, populated
 * from {@code BANK_INFM_DETL} rows with codes 12 / 13 / 16 / 19 /
 * 22 / 25.
 *
 * <p>{@code logoUrl} still carries the {@code {LANG_CODE}} placeholder
 * — the frontend substitutes EN / KO / VI at render time based on the
 * user's current locale.
 */
public record BankFormInfoSubType(
        String bankCd,
        int    seqNo,
        /** Sub-type name (e.g. "Corporate Banking", "Personal Banking",
         *  "VCB iB@nking"). Empty string when the bank only has one
         *  sub-type and the dialog should not render radio buttons. */
        String seqNm,
        /** Bank logo path, e.g. "/banklogo/Shinhan_{LANG_CODE}.png".
         *  Null when the bank has no logo configured. */
        String logoUrl,
        /** Localised label for the ID field (e.g. "Tên truy cập",
         *  "Username", "User ID"). Fallback "ID" when null. */
        String idLabel,
        /** Localised label for the Password field. Fallback "Password"
         *  when null. */
        String passwordLabel,
        /** Localised label for the Confirm Password field. Fallback
         *  "Confirm Password" when null. */
        String confirmLabel,
        /** Localised label for the optional extra Corporate-ID input
         *  (Agribank uses this). Empty string when the bank does not
         *  use an extra ID input — frontend hides the field. */
        String extraLabel
) { }
