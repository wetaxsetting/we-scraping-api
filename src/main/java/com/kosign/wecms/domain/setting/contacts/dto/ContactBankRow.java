package com.kosign.wecms.domain.setting.contacts.dto;

/**
 * One row in the contacts bank-picker autocomplete.
 *
 * <p>Mirrors the wire shape produced by legacy {@code comm_0109_001_r002}
 * which the popup at {@code comm_0184_002.ts:44} consumes via
 * {@code cardComboDataNapasSearch}. The discriminator is
 * {@code antDivCd} ({@code "NAPAS"} / {@code "CITAD"}); the frontend
 * routes the selected code back into the Save payload as either
 * {@code napas_bank_cd} or {@code citad_bank_cd} based on it.
 *
 * <p>{@code status} encodes <b>NAPAS availability</b> (legacy
 * {@code comm_0109_001_r002.STATUS} = {@code CASE WHEN NAPAS_CD IS NOT
 * NULL THEN 'Y' ELSE 'N'}), NOT the raw {@code BANK_STAS} active flag.
 * {@code ContactsService.listBanks()} emits {@code 'Y'} for NAPAS banks
 * and {@code 'N'} for CITAD-only banks — drives the dialog's
 * recipient-flow toggle (status {@code 'N'} → manual Recipient Name
 * input; status {@code 'Y'} → Lookup Recipient button). The frontend
 * actually gates the Lookup button on {@code antDivCd === "NAPAS"},
 * which is equivalent here since every {@code 'Y'} row is NAPAS.
 *
 * @param bankCd       Raw {@code BANK_CD} — equals the NAPAS code on
 *                     {@code ANT_DIV_CD='NAPAS'} rows, the CITAD code
 *                     on {@code ANT_DIV_CD='CITAD'} rows.
 * @param antDivCd     {@code "NAPAS"} or {@code "CITAD"}.
 * @param bankNm       Localised bank name (Vietnamese); falls back to
 *                     {@code bankEngNm} on the wire when null.
 * @param bankEngNm    English bank name.
 * @param engAbrvatNm  English abbreviation (e.g. {@code "ShinhanBank"})
 *                     — legacy popup uses this as the dropdown label
 *                     prefix.
 * @param status       {@code 'Y'} / {@code 'N'} / null.
 *
 * <p>Wire convention: lowercase snake_case via the global Jackson
 * strategy. Java fields stay camelCase.
 */
public record ContactBankRow(
        String bankCd,
        String antDivCd,
        String bankNm,
        String bankEngNm,
        String engAbrvatNm,
        String status) { }
