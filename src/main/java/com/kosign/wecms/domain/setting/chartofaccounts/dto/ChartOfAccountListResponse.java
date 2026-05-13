package com.kosign.wecms.domain.setting.chartofaccounts.dto;

import java.util.List;
import java.util.Set;

/**
 * Response shape for {@code GET /api/v1/settings/chart-of-accounts}.
 *
 * <p>{@code restricted_codes} ships the static set
 * {@code {"ACC-01", "ACC-02", "ACC-03"}} so the frontend can render
 * the localized REVENUE / EXPENSES / INVESTMENT labels without
 * special-casing per-row data. Wire shape (snake_case):
 *
 * <pre>{@code
 * {
 *   "items": [ ... ChartOfAccountRow[] ... ],
 *   "total_count": 29,
 *   "restricted_codes": ["ACC-01", "ACC-02", "ACC-03"]
 * }
 * }</pre>
 */
public record ChartOfAccountListResponse(
        List<ChartOfAccountRow> items,
        int                     totalCount,
        Set<String>             restrictedCodes
) {}
