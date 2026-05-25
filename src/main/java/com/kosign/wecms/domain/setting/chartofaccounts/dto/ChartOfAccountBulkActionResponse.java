package com.kosign.wecms.domain.setting.chartofaccounts.dto;

/**
 * Response for the bulk delete / bulk deactivate endpoints.
 *
 * <p>{@code processed} rows succeeded; {@code failed} rows surfaced a
 * per-row error (restricted code, not found, etc.) and were skipped.
 * Matches the partial-failure shape used by
 * {@link ChartOfAccountBulkUploadResponse} — the frontend renders one
 * success / partial-success dialog based on the totals.
 */
public record ChartOfAccountBulkActionResponse(
        int processed,
        int failed
) {}
