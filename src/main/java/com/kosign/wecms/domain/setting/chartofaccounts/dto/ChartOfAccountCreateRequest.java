package com.kosign.wecms.domain.setting.chartofaccounts.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Create-chart-of-accounts request body. Wire shape (snake_case):
 * <pre>{@code
 * {
 *   "chart_acct_cd":        "1007009",
 *   "chart_acct_nm_en":     "Collection 4",
 *   "chart_acct_nm_vn":     "회수4",
 *   "chart_acct_nm_ko":     "회수4",
 *   "chart_acct_type":      null,
 *   "parent_chart_acct_cd": "ACC-01",
 *   "cal_rule":             null,
 *   "stts":                 "A"
 * }
 * }</pre>
 *
 * <p>Bean validation guards mirror the entity columns. Service-layer
 * cross-row checks (duplicate code, parent existence, cycle, depth,
 * self-parent) run separately in {@code ChartOfAccountsValidator}.
 *
 * <p>{@code parent_chart_acct_cd} is optional &mdash; {@code null} or
 * blank means "root row". Legacy parity: the JSP marks it required but
 * then guards with {@code isNotBlank} on every read path. Refactor
 * keeps it optional explicitly.
 *
 * <p>{@code stts} is required (CHAR(1) NOT NULL); accepts the legacy
 * letters {@code A} (Active) or {@code I} (Inactive).
 */
public record ChartOfAccountCreateRequest(
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
