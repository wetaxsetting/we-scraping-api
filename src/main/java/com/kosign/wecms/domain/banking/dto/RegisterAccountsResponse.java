package com.kosign.wecms.domain.banking.dto;

/**
 * Result of {@code POST /api/v1/banking/accounts/register}. Returns
 * the count of newly-inserted {@code ACCT_MSTR} rows plus the count of
 * existing rows that were updated (legacy {@code fnsh_0101_001_c001}
 * upsert) — gives the frontend enough to compose a localised
 * "X created, Y updated" message without a second round-trip.
 */
public record RegisterAccountsResponse(
        int inserted,
        int updated) { }
