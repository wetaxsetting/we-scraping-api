package com.kosign.wecms.domain.setting.vasaccount.dto;

/**
 * One row in the VAS Account list response — also the response shape for
 * the detail endpoint. Wire field names auto-derived from camelCase via
 * the global Jackson {@code SNAKE_CASE} naming strategy.
 *
 * <p>{@code vasAcctSeqNo} ships as a {@code String} on the wire (legacy
 * {@code NUMERIC(10,0)} PK; ships as string to dodge JS-number precision
 * loss in the browser and so the frontend treats it as an opaque id).
 *
 * <p>{@code vasAcctType} / {@code stts} ship the verbatim DB code; the
 * accompanying {@code vasAcctTypeKey} / {@code sttsKey} ship the i18n key
 * the frontend resolves via {@code t(key)}. Mirrors the
 * {@code acct_type_cd + acct_type_key} precedent — server is the source
 * of truth for which key applies to which code.
 */
public record VasAccountRow(

        /** Per-tenant sequence as a string. */
        String vasAcctSeqNo,

        /** Short tenant-unique numeric code, e.g. {@code "111"}. */
        String vasAcctCd,

        /** Display name. */
        String vasAcctNm,

        /** Optional English name. */
        String vasAcctEnm,

        /** Raw type code — {@code "0"} / {@code "1"} / {@code "2"}. */
        String vasAcctType,

        /** i18n key for the type label, e.g.
         *  {@code "settings.vas_account.type.debit"}. {@code null} when the
         *  code is outside the known set (defensive — should never happen). */
        String vasAcctTypeKey,

        /** Raw status code — {@code "A"} / {@code "I"}. */
        String stts,

        /** i18n key for the status label, e.g.
         *  {@code "settings.vas_account.status.active"}. */
        String sttsKey,

        /** Optional description / notes (max 200 chars). */
        String cont,

        /** Audit — last modifier id. */
        String modId,

        /** Audit — last modification timestamp, {@code yyyyMMddHHmmss} UTC. */
        String modDtm

) { }
