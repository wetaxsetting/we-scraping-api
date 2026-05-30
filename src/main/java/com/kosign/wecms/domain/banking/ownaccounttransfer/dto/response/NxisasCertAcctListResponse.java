package com.kosign.wecms.domain.banking.ownaccounttransfer.dto.response;

/**
 * Response body for {@code POST /api/v1/banking/own-account-transfer/nxisas/cert-acct-list}.
 * Mirrors the legacy {@code nxisas_acctList_r003} output shape — five
 * cert fields keyed by {@code BANK_CERT_INFO.SEQ_NO}. The bundle's
 * {@code _v2nxisas.getCertInfo(seqNo)} consumes these UPPER_SNAKE
 * field names verbatim (legacy at {@code trns_0001_007.ts:1582-1599}),
 * so we keep that shape on the wire — the global Jackson SNAKE_CASE
 * strategy would otherwise emit camelCase and the bundle would read
 * undefined.
 *
 * <p>{@code certInfoDetlCon} is the decrypted plaintext password.
 * See security note on {@link SourceAccountRow} — exposure is gated
 * by tenant + W-grant.
 */
public record NxisasCertAcctListResponse(
        String CERT_INFO_CON,
        String CERT_INFO_DETL_CON,
        String CERT_INFO_DETL_CON2,
        String CERT_INFO_DETL_CON3,
        String NXISAS_GB_SEQ_NO
) { }
