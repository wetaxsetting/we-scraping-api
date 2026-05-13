package com.kosign.wecms.domain.setting.contacts.dto;

/**
 * One overseas-beneficiary sub-row inside a contact's list / detail
 * response (Overseas contact only).
 */
public record ContactOverseasRow(
        String trcoOvrsKey,
        String beneName,
        String beneAcctNo,
        String bicCode,
        String beneBankName,
        String beneBankCode,
        String beneAddr) { }
