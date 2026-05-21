package com.kosign.wecms.domain.setting.chartofaccounts.dto;

/**
 * Wire row for {@code GET /api/v1/settings/chart-of-accounts} (list +
 * single-row detail). Java field names are camelCase; the global
 * Jackson {@code SNAKE_CASE} naming strategy converts to snake_case on
 * the wire (see {@code wecms-api/CLAUDE.md §"API Naming &
 * Serialization Standards"}).
 *
 * <p>Three computed fields ride alongside the raw entity columns:
 * <ul>
 *   <li>{@code chart_acct_level} — depth in the tree (1..4) or
 *       {@code null} if the row is orphaned (parent missing or
 *       depth&gt;4). Computed server-side in
 *       {@code ChartOfAccountsTreeBuilder}.</li>
 *   <li>{@code has_children} — true iff at least one active row in
 *       the tenant points its parent to this row's
 *       {@code chart_acct_cd}. Computed via a single grouping query.</li>
 *   <li>{@code restricted} — true iff the code is in
 *       {@link com.kosign.wecms.domain.setting.chartofaccounts.ChartOfAccountsConstants#RESTRICTED_CODES}.
 *       Frontend uses this to disable Edit / Delete buttons.</li>
 * </ul>
 *
 * <p>{@code DEL_YN} and {@code BIZ_KEY} are intentionally NOT on the
 * wire — they're filtered server-side via tenant context. Leaking
 * either would be a data-isolation regression.
 */
public record ChartOfAccountRow(
        String  chartAcctSeqNo,
        String  chartAcctCd,
        String  chartAcctNmEn,
        String  chartAcctNmVn,
        String  chartAcctNmKo,
        String  chartAcctType,
        String  parentChartAcctCd,
        String  calRule,
        String  stts,
        Integer chartAcctLevel,
        Boolean hasChildren,
        Boolean restricted,
        String  regId,
        String  regDtm,
        String  modId,
        String  modDtm
) {}
