package com.kosign.wecms.domain.banking.ownaccounttransfer.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response body for {@code POST /api/v1/banking/own-account-transfer/nxisas/cert-acct-list}.
 * Mirrors the legacy {@code nxisas_acctList_r003} output shape — five
 * cert fields keyed by {@code BANK_CERT_INFO.SEQ_NO}. The bundle's
 * {@code _v2nxisas.getCertInfo(seqNo)} consumes these UPPER_SNAKE field names
 * verbatim (legacy at {@code trns_0001_007.ts:1582-1599}), so we keep that
 * shape on the wire.
 *
 * <p><b>The UPPER_SNAKE wire names are pinned with {@code @JsonProperty}.</b>
 * The caller is the vendor bundle itself (routed through the JEX adapter's
 * {@code nxisas_acctList_r003} endpoint mapping), so these keys are an external
 * contract, exempt from the lowercase snake_case rule for the same reason the
 * {@code nxisas/gateway/} DTOs are.
 *
 * <p>Fixed 2026-08-18: the components carried no {@code @JsonProperty}, so the
 * global {@code PropertyNamingStrategies.SNAKE_CASE} strategy
 * ({@code JacksonConfig:25}) emitted {@code cert_info_con} etc. while the bundle
 * read {@code CERT_INFO_CON} — every field came back {@code undefined}. The
 * Javadoc already asserted the UPPER shape was preserved; nothing implemented
 * it. Do not "normalise" these to camelCase components — that would re-break
 * the bundle's read.
 *
 * <p>{@code CERT_INFO_DETL_CON} is the decrypted plaintext password.
 * See security note on {@link SourceAccountRow} — exposure is gated
 * by tenant + W-grant.
 */
public record NxisasCertAcctListResponse(
        @JsonProperty("CERT_INFO_CON")       String CERT_INFO_CON,
        @JsonProperty("CERT_INFO_DETL_CON")  String CERT_INFO_DETL_CON,
        @JsonProperty("CERT_INFO_DETL_CON2") String CERT_INFO_DETL_CON2,
        @JsonProperty("CERT_INFO_DETL_CON3") String CERT_INFO_DETL_CON3,
        @JsonProperty("NXISAS_GB_SEQ_NO")    String NXISAS_GB_SEQ_NO
) { }
