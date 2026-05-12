package com.kosign.wecms.domain.setting.firmbankingsettings.dto;

import java.util.List;

/**
 * Response shape for the audit-read endpoint
 * ({@code GET …/{out_bank_cd}/{wdrl_act_no}/audit}). Wraps the audit
 * rows under {@code list} for envelope consistency across refactor
 * endpoints.
 *
 * <p>Wire field names auto-derived from camelCase via the global
 * Jackson SNAKE_CASE strategy.
 */
public record FirmBankingAuditListResponse(
        List<FirmBankingAuditEntry> list
) { }
