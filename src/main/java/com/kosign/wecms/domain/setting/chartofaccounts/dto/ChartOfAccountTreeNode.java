package com.kosign.wecms.domain.setting.chartofaccounts.dto;

/**
 * Tree-projection row for {@code GET /api/v1/settings/chart-of-accounts/tree}.
 * Powers the inline parent-account picker on the Settings panel.
 *
 * <p>Order matches legacy {@code IDO.CHART_ACCT_INFM_R002.xml:197}:
 * {@code ORDER BY CHART_ACCT_CD ASC}. The {@code breadcrumb} field is a
 * pre-rendered display string in the legacy
 * {@code "<rootCd> - <rootNm> / <parentCd> - <parentNm> / <currentCd>
 * - <currentNm>"} shape — frontend can render it without rebuilding
 * the chain client-side. Rows with {@code chart_acct_level} {@code 1}
 * carry just {@code "<cd> - <name_en>"}.
 *
 * <p>Only rows with a resolved level (1..4) appear in this projection
 * — orphaned rows are filtered out server-side, mirroring legacy
 * R002's outer {@code WHERE T.CHART_ACCT_LEVEL IS NOT NULL}.
 */
public record ChartOfAccountTreeNode(
        String  chartAcctSeqNo,
        String  chartAcctCd,
        String  chartAcctNmEn,
        String  parentChartAcctCd,
        Integer chartAcctLevel,
        Boolean hasChildren,
        Boolean restricted,
        String  breadcrumb
) {}
