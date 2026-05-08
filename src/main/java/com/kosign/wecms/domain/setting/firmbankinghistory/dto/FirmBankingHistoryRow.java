package com.kosign.wecms.domain.setting.firmbankinghistory.dto;

/**
 * Single row in the Firm Banking History list response. Mirrors the
 * legacy {@code WSVC.comm_0221_001_r001.xml} {@code <subRule>}
 * 1:1 — every field name on the wire snake-cases the camelCase Java
 * field via the global {@code JacksonConfig} naming strategy. No
 * per-field {@code @JsonProperty} required.
 *
 * <p>Field semantics per source-leg are documented in
 * {@code docs/refactor-wecms/migration/firm-banking-history-mapping.md
 * §3.2}.
 */
public record FirmBankingHistoryRow(
        /** VN-localized timestamp {@code YYYYMMDDHHMMSS}. */
        String tarnDtm,
        /** {@code F} / {@code S} / {@code G} / {@code B}. */
        String svcType,
        /** Frontend i18n key for the {@link #svcType} discriminator,
         *  resolved server-side via
         *  {@link com.kosign.wecms.common.enums.FirmBankingSvcType}.
         *  Resolves to one of the four
         *  {@code settings.firm_banking_history.service_type.*} keys
         *  the existing locale files already register
         *  ({@code firm_banking_transaction} /
         *  {@code scraping_transfer} / {@code vietnam_gdt_inquiry} /
         *  {@code vietnam_business_inquiry}). {@code null} only when
         *  the wire {@code svc_type} is not one of the four known
         *  letters — frontend renders the raw character in that case.
         *
         *  <p>Replaces the previous frontend-side
         *  {@code coerceFirmBankingServiceType} switch in
         *  {@code mappers.ts:540-553}, lifting domain-code meaning
         *  back to the backend (added 2026-05-08). */
        String svcTypeKey,
        /** Service name (per-leg semantics — see migration doc).
         *  F leg: raw {@code TRAN_TP} value ({@code "201"} / {@code "202"} /
         *  …). S / G / B legs: stable English literal emitted by the
         *  V60 view's CASE-WHEN. The frontend should NOT consume this
         *  field directly for display — use {@link #svcNameKey} instead
         *  and fall back to {@code svcNm} only when the key is null. */
        String svcNm,
        /** Frontend i18n key under the {@code fb_history.service.*}
         *  namespace, resolved server-side from
         *  {@code (svcType, svcNm)} via
         *  {@link com.kosign.wecms.domain.setting.firmbankinghistory.service.FirmBankingServiceNameResolver}.
         *
         *  <p>{@code null} means "no localised name registered for this
         *  pair" — frontend falls back to {@code svcNm}. The legacy
         *  {@code SERVICE_NAME_MAP} in {@code comm_0221_001.ts:40-52}
         *  also rendered the raw value when its lookup missed; this
         *  field preserves that behaviour while moving authoritative
         *  ownership server-side per the
         *  {@code TranTp.java}-rooted refactor decision. */
        String svcNameKey,
        /** Source-row sequence identifier. Keyword search target. */
        String seqNo,
        /** Bank/institution display string ({@code "<name> - <code>"} for
         *  bank-bound legs; {@code NXISAS_MODULE} for GDT/Business). */
        String depBankCd,
        /** Result code (legacy semantics — frontend translates via
         *  {@code DICTIONARY_VAN} for the F leg + a fixed 16-code map). */
        String rsltCd,
        /** Result message (raw — F leg sends {@code RS_CD} again, others
         *  send a free-text message). */
        String rsltMsg,
        /** Per-leg job identifier — {@code TRAN_TP} (F),
         *  {@code "TRANSFER"} (NAPAS), {@code "BALANCE"}/{@code "TRANSACTION"}/
         *  {@code "CARD_USING"} (EXT_TRAN), {@code NXISAS_JOB} (G/B). */
        String job,
        /** Original request payload. Only G/B legs populate; null otherwise. */
        String reqData,
        /** VAN organization code. F leg only; null otherwise. */
        String vanOrgCd
) { }
