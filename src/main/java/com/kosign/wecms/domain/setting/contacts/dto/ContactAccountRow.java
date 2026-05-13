package com.kosign.wecms.domain.setting.contacts.dto;

/**
 * One bank-account sub-row inside a contact's list / detail response
 * (Local contact only). {@code bank_nm} is the freshly resolved name
 * from the legacy {@code wb_get_napas_bank_nm_or_citad_bank_nm}
 * function (OQ-7 resolved 2026-05-12); when the function returns null
 * for the requested locale, the field is null and the frontend falls
 * back to the row's stored display copy if it has one.
 */
public record ContactAccountRow(
        String trcoAcctKey,
        String acctNo,
        Integer dispOrd,
        String bankNm,
        String napasBankCd,
        String citadBankCd,
        String acctCcyCd,
        String fincMdaOwnerNm,
        String owacAddr) { }
