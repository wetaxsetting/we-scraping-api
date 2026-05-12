package com.kosign.wecms.domain.setting.firmbankingsettings.dto;

import java.util.List;

/**
 * Response shape for the firm-banking-settings list endpoint.
 *
 * <p>The legacy WSVC ({@code WSVC.comm_0220_001_r001}) returned
 * {@code REC} with no pagination metadata. The refactor wraps the
 * row collection as {@link #list} for consistency with other refactor
 * endpoints (e.g. firm-banking-history); the legacy list is unpaged
 * in JEX too, so the refactor preserves "no pagination" while still
 * exposing the rows under the new envelope name.
 *
 * <p>Wire field names auto-derived from camelCase via the global
 * Jackson SNAKE_CASE strategy.
 */
public record FirmBankingSettingListResponse(
        List<FirmBankingSettingRow> list
) { }
