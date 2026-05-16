package com.kosign.wecms.domain.banking.dto;

/**
 * Result of {@code POST /api/v1/banking/accounts/register}. Returns
 * the count of newly-inserted {@code ACCT_MSTR} rows plus the count
 * of rows that were skipped because the {@code (BIZ_KEY, BANK_CD,
 * ACCT_NO)} PK already existed in the tenant — gives the frontend
 * enough to compose a localised success message without a second
 * round-trip.
 */
public record RegisterAccountsResponse(
        int inserted,
        int skipped) { }
