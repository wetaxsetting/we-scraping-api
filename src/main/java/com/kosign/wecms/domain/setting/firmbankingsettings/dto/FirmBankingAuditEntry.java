package com.kosign.wecms.domain.setting.firmbankingsettings.dto;

/**
 * One row of the firm-banking-settings audit history. Mirrors the
 * legacy {@code WSVC.comm_0220_003_r001} {@code REC} sub-rule fields.
 * Returned by {@code GET …/{out_bank_cd}/{wdrl_act_no}/audit}.
 *
 * <p>Wire field names auto-derived from camelCase via the global
 * Jackson SNAKE_CASE strategy.
 *
 * <p>Activity / detail strings are pre-localized to the caller's
 * locale at write time (legacy and refactor both fan out to one row
 * per locale; the read filters by the caller's {@code lang_cd}).
 */
public record FirmBankingAuditEntry(
        String auditLogsActv,
        String auditLogsActvDtl,
        String regId,
        String regNm,
        String regDtm
) { }
