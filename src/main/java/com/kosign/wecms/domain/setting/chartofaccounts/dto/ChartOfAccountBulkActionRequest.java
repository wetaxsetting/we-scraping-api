package com.kosign.wecms.domain.setting.chartofaccounts.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Request body for {@code POST /api/v1/settings/chart-of-accounts/bulk-delete}
 * and {@code POST /api/v1/settings/chart-of-accounts/bulk-deactivate}.
 *
 * <p>Carries the list of per-tenant surrogates
 * ({@code chart_acct_seq_no}) selected from the panel's row checkboxes.
 * Each entry is processed independently server-side — a failure on one
 * row does NOT roll back rows that already committed. Mirrors the
 * partial-failure shape used by {@code /bulk-upload}.
 *
 * <p>The wire shape is intentionally simple — only seq_no list. The
 * backend looks up restricted-code / not-found / cycle / etc. errors
 * per-row and tallies them into
 * {@link ChartOfAccountBulkActionResponse}.
 */
public record ChartOfAccountBulkActionRequest(
        @NotNull @NotEmpty
        @Size(max = 500)
        List<@Pattern(regexp = "\\d{1,14}") String> chartAcctSeqNos
) {}
