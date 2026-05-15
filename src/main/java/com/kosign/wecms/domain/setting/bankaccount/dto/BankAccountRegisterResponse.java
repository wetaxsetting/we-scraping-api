package com.kosign.wecms.domain.setting.bankaccount.dto;

/** Returned by the bulk-register endpoint. {@code inserted} is the
 *  count actually written; {@code skipped} is the count of items that
 *  were silently skipped because a row with the same `(bankCd, acctNo)`
 *  already existed for this tenant — legacy behaviour, mirrors how
 *  the JEX scrape flow avoids duplicate-key errors when a user
 *  re-saves the same picked account set. */
public record BankAccountRegisterResponse(int inserted, int skipped) { }
