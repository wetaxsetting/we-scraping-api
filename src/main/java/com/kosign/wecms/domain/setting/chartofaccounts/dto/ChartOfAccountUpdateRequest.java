package com.kosign.wecms.domain.setting.chartofaccounts.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Update-chart-of-accounts request body. Same shape as
 * {@link ChartOfAccountCreateRequest}; the path variable
 * {@code {chart_acct_seq_no}} is authoritative for the row identity.
 * The body MUST NOT echo {@code chart_acct_seq_no} or {@code biz_key}
 * — those are server-managed.
 *
 * <p>Service rejects renames TO a restricted code
 * (ACC-01/02/03) via {@code WBE_CHART_OF_ACCOUNTS_RESTRICTED_CODE} and
 * rejects edits to a restricted row via the same code.
 */
public record ChartOfAccountUpdateRequest(
        @NotBlank @Size(max = 20) @Pattern(regexp = "[A-Za-z0-9_\\-]+")
        String chartAcctCd,

        @NotBlank @Size(max = 255)
        String chartAcctNmEn,

        @Size(max = 255)
        String chartAcctNmVn,

        @Size(max = 255)
        String chartAcctNmKo,

        @Size(max = 100)
        String chartAcctType,

        @Size(max = 20)
        String parentChartAcctCd,

        @Pattern(regexp = "[PM]")
        String calRule,

        @NotBlank @Pattern(regexp = "[AI]")
        String stts
) {}
