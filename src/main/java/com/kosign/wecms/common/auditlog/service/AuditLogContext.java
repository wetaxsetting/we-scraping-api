package com.kosign.wecms.common.auditlog.service;

import lombok.Builder;

/**
 * Caller-supplied context for one audit-log append. Bundles the
 * {@code AUDIT_LOGS_INFM} fields that vary per business event (entity
 * name, PK components, the i18n keys for activity / detail strings)
 * so {@link AuditLogService#append(AuditLogContext)} stays a single
 * argument. Other fields ({@code biz_key}, {@code reg_id},
 * {@code reg_nm}, {@code reg_dtm}, {@code lang_cd}, {@code seq_no})
 * are derived by the writer — callers never set them.
 *
 * <p>Use the {@link Builder} for readability:
 * <pre>{@code
 * auditLogService.append(AuditLogContext.builder()
 *     .wsvcId("firm-banking-settings")
 *     .refNo("TB_FIRM_CONF")
 *     .refNo1(req.outBankCd())
 *     .refNo2(req.wdrlActNo())
 *     .activityKey("audit.firm_banking_settings.activity")
 *     .detailKey("audit.firm_banking_settings.update.detail")
 *     .build());
 * }</pre>
 *
 * <p>The {@code wsvcId} field carries the per-feature stable
 * identifier — for the refactor that is the route slug
 * (e.g. {@code "firm-banking-settings"}). OQ-6 was overridden on
 * 2026-05-09 (legacy JEX page-id reuse retired); see workspace
 * {@code CLAUDE.md} §"Refactor identifier naming".
 *
 * <p>{@code activityKey} / {@code detailKey} are message-bundle keys
 * (e.g. {@code audit.firm_banking_settings.activity}). The writer
 * resolves them via {@link com.kosign.wecms.common.i18n.MessageResolver}
 * once per supported locale, so each key MUST exist in every locale
 * file the project supports (en, ko, vi, km). A missing key in a
 * locale file falls back to the key string itself — so users in that
 * locale would see {@code "audit.firm_banking_settings.activity"}
 * instead of "Firm Banking Details". That's a translation gap, not a
 * crash; fix by adding the missing entry to the locale file.
 *
 * <p>{@code msgArgs} are forwarded to {@code MessageFormat.format(...)}
 * at message-resolution time, so keys can carry placeholders like
 * {@code "Updated firm banking for {0}"}. Most callers pass none.
 */
@Builder
public record AuditLogContext(
        /** Per-feature stable identifier (refactor route slug) — see
         *  Javadoc above. Required. */
        String     wsvcId,
        /** Entity name (e.g. {@code "TB_FIRM_CONF"}). Required. */
        String     refNo,
        /** First PK component (e.g. {@code OUT_BANK_CD} value). Required. */
        String     refNo1,
        /** Second PK component (e.g. {@code WDRL_ACT_NO} value).
         *  Nullable — the legacy schema permits NULL here for entities
         *  with a single-column natural PK. */
        String     refNo2,
        /** Optional activity-category message key (resolves to e.g.
         *  "Firm Banking Details"). When {@code null} or blank the
         *  audit row's {@code AUDIT_LOGS_ACTV} column is left null. */
        String     activityKey,
        /** Required activity-detail message key (resolves to e.g.
         *  "Update firmbanking details"). Maps to the NOT-NULL
         *  {@code AUDIT_LOGS_ACTV_DTL} column. */
        String     detailKey,
        /** Optional MessageFormat args for {@link #activityKey} and
         *  {@link #detailKey} — applied to both. */
        Object[]   msgArgs
) { }
