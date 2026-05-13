package com.kosign.wecms.domain.setting.chartofaccounts.dto;

import java.util.List;

/** Wrapper for {@code GET /api/v1/settings/chart-of-accounts/tree}. */
public record ChartOfAccountTreeResponse(List<ChartOfAccountTreeNode> nodes) {}
