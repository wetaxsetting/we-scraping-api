package com.kosign.wecms.domain.setting.chartofaccounts.dto;

/**
 * Response for {@code POST /api/v1/settings/chart-of-accounts/bulk-upload}.
 * {@code created} is the count of rows successfully persisted;
 * {@code failed} is the count of rows that threw any exception (duplicate
 * code, parent not found, depth exceeded, etc.) — the frontend shows a
 * partial-failure dialog when {@code failed > 0}.
 */
public record ChartOfAccountBulkUploadResponse(
        int created,
        int failed
) {}
