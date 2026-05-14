package com.kosign.wecms.domain.setting.logincredentials.dto;

/**
 * One row in the Add-Bank picker. Mirrors the legacy
 * {@code nxisas_acctList_r002} response — three fields per bank:
 * the real {@code BANK_CD} (used as {@code bank_cd} on every
 * subsequent API call), a display name, and an icon path.
 *
 * <p>Sourced from {@code BANK_INFM_DETL} via the legacy R004 query
 * (V121 seeds the supporting rows). Scraping-enabled banks only —
 * the WHERE clause filters by {@code BANK_DETL_INFO_CD = '9'} +
 * {@code BANK_DETL_INFO_CONT = 'Y'} (see V121 comment).
 */
public record BankPickerRow(
        String bankCd,
        String bankNm,
        String bankImg
) { }
