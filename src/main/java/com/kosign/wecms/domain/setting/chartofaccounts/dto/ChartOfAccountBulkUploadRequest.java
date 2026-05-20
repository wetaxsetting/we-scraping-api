package com.kosign.wecms.domain.setting.chartofaccounts.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Request body for {@code POST /api/v1/settings/chart-of-accounts/bulk-upload}.
 * Each element in {@code items} is validated as a normal create request;
 * the service processes them independently so a failure on one row does
 * not roll back rows that already succeeded.
 */
public record ChartOfAccountBulkUploadRequest(
        @NotEmpty @Size(max = 500)
        List<@Valid ChartOfAccountCreateRequest> items
) {}
