package com.kosign.wecms.domain.setting.erptransactionhistory.dto;

/**
 * Single row in the ERP Transaction History list response. Mirrors the
 * legacy {@code WSVC.comm_0219_001_r001.xml} {@code <subRule>} 1:1 — every
 * field name on the wire snake-cases the camelCase Java field via the
 * global {@code JacksonConfig} naming strategy. No per-field
 * {@code @JsonProperty} required.
 *
 * <p>Wire shape mirrors the legacy IDO output plus two refactor additions:
 * {@code erp_type_key} and {@code trns_type_key} — server-resolved i18n
 * keys, per {@code wecms-api/CLAUDE.md §"Domain enums"} (frontend
 * renders via {@code t(key)} and falls back to the raw legacy code when
 * the key is null). The legacy frontend's hardcoded
 * {@code transactionDescription} switch ({@code comm_0219_001.ts:387-410})
 * + {@code mapErpType} ({@code comm_0219_001.ts:378-384}) retire in favour
 * of these keys.
 *
 * <p>Field semantics per source-row are documented in
 * {@code docs/refactor-wecms/migration/erp-transaction-history.md §4.2}.
 */
public record ErpTrnsHistoryRow(

        /** Per-tenant sequence — {@code ERP_TRNS_HSTR.SEQ_NO} (numeric).
         *  Doubles as the row id for the download endpoint. */
        Long seqNo,

        /** Original filename as supplied at write-time. */
        String orgFileNm,

        /** NAS / storage-root filename (rarely useful to the frontend; kept
         *  on the wire for forensic parity with the legacy IDO). */
        String saveFileNm,

        /** Bytes. {@code 0} for batch rows with no physical file. */
        long fileSize,

        /** Raw direction code — {@code W} / {@code E} / {@code D}. */
        String erpType,

        /** Frontend i18n key for the {@link #erpType} discriminator, e.g.
         *  {@code "settings.erp_transaction_history.direction.wecms_to_erp"}.
         *  {@code null} only when {@code erp_type} is not one of the three
         *  known letters (defensive — the column is CHAR(1) with values
         *  drawn from a fixed set, so this should not happen in practice). */
        String erpTypeKey,

        /** Raw transaction-type code — {@code "01"..."17"}. */
        String trnsTypeCd,

        /** Frontend i18n key for the {@link #trnsTypeCd}, e.g.
         *  {@code "settings.erp_transaction_history.type.09"}. {@code null}
         *  when the code is outside the known range. */
        String trnsTypeKey,

        /** UTC timestamp as {@code yyyyMMddHHmmss} — frontend splits it
         *  into a date + time stack via {@code lib/datetime.ts}. */
        String regDtm,

        /** Login id of the user that triggered the row (or {@code SYSTEM}). */
        String regId,

        /** Resolved {@code USER_INFM.USER_NM} via LEFT JOIN. {@code null}
         *  when {@code REG_ID = 'SYSTEM'} or the user no longer exists —
         *  frontend falls back to {@code reg_id}. Replaces the legacy
         *  {@code WB_GET_USER_NM(REG_ID)} function call. */
        String userNm

) { }
