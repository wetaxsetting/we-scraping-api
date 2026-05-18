package com.kosign.wecms.domain.banking.service.cashflow;

import com.kosign.wecms.domain.setting.chartofaccounts.entity.ChartOfAccount;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Tenant-scoped view of {@code CHART_ACCT_INFM} used by the Cash Flow
 * report. Built once per request from a single {@code findByBizKey} call;
 * exposes O(1) lookups for {@code levelOf}, parent walk, and active-row
 * filtering.
 *
 * <p>Replaces the legacy {@code wb_get_chart_acct_level_text(biz_key,
 * seq_no, code)} PL/pgSQL helper — the level is computed by walking
 * {@code parent_chart_acct_cd} chains in Java.
 *
 * <p>Mirrors {@code level3_with_hierarchy} CTE's filters: rows where
 * {@code del_yn = 'N'} AND {@code stts = 'A'} AND the L1 ancestor is one
 * of {@code ACC-01 / ACC-02 / ACC-03}.
 *
 * <p>The hierarchy is built with these rules (verified against the
 * function body):
 * <ul>
 *   <li>L1 = the three restricted roots {@code ACC-01 / ACC-02 / ACC-03}.
 *       Identified by {@code parent_chart_acct_cd IS NULL} on the seed,
 *       but the safer check is membership in the fixed set.</li>
 *   <li>L2 = direct child of a L1 root
 *       ({@code parent_chart_acct_cd ∈ {ACC-01, ACC-02, ACC-03}}).</li>
 *   <li>L3 = grandchild of a L1 root
 *       ({@code parent_chart_acct_cd} is the {@code chart_acct_cd} of an L2 row).</li>
 *   <li>Anything else (orphans, self-referencing rows, rows whose parent
 *       chain doesn't terminate at one of the three roots) is treated as
 *       {@code level = null} — they are excluded from {@link #l3Rows}.</li>
 * </ul>
 *
 * <p>Inactive-ancestor filter: a row whose L1 or L2 ancestor has
 * {@code stts != 'A'} is dropped from {@link #l3Rows} (matches
 * {@code filtered_hierarchy} CTE).
 *
 * <p>Reference: docs/refactor-wecms/features/cash-flow-report.md §3.0.1
 * + §3.2 + §3.8.
 */
public class ChartOfAccountsHierarchy {

    /** The three restricted Level-1 roots seeded by V96 for every tenant. */
    public static final Set<String> LEVEL1_ROOTS = Set.of("ACC-01", "ACC-02", "ACC-03");

    private final Map<String, ChartOfAccount> byCode;
    private final List<ChartOfAccount> l3Active;
    private final Map<String, ChartOfAccount> l2ByCode;

    /** Build from a tenant's full chart_of_accounts row set (active only). */
    public ChartOfAccountsHierarchy(List<ChartOfAccount> tenantRows) {
        this.byCode = new HashMap<>();
        for (ChartOfAccount r : tenantRows) {
            if (!"N".equals(r.getDelYn())) continue;
            byCode.put(r.getChartAcctCd(), r);
        }

        // L2 lookup: direct children of an ACC-* root.
        this.l2ByCode = new HashMap<>();
        for (ChartOfAccount r : byCode.values()) {
            if (LEVEL1_ROOTS.contains(r.getParentChartAcctCd())) {
                l2ByCode.put(r.getChartAcctCd(), r);
            }
        }

        // L3 active set: rows whose parent is an L2 AND whose chain
        // reaches an active L1 AND whose own STTS is Active AND whose
        // ancestors are also Active. Self-referencing or orphan rows
        // are excluded.
        this.l3Active = new java.util.ArrayList<>();
        for (ChartOfAccount r : byCode.values()) {
            if (!"A".equals(r.getStts())) continue;
            ChartOfAccount l2 = l2ByCode.get(r.getParentChartAcctCd());
            if (l2 == null) continue;
            if (!"A".equals(l2.getStts())) continue;
            // Self-reference guard: a row where parent == self is the
            // legacy DEV quirk (seq 104) — treat as not-L3.
            if (r.getChartAcctCd().equals(r.getParentChartAcctCd())) continue;
            ChartOfAccount l1 = byCode.get(l2.getParentChartAcctCd());
            if (l1 == null) continue;
            if (!"A".equals(l1.getStts())) continue;
            if (!LEVEL1_ROOTS.contains(l1.getChartAcctCd())) continue;
            l3Active.add(r);
        }
    }

    /** All Level-3 rows that contribute to the report's aggregation. */
    public List<ChartOfAccount> l3Rows() {
        return l3Active;
    }

    /** Lookup by {@code chart_acct_cd}. Returns {@code null} if unknown. */
    public ChartOfAccount byCode(String chartAcctCd) {
        if (chartAcctCd == null) return null;
        return byCode.get(chartAcctCd);
    }

    /** Direct L2 parent of an L3 row. */
    public ChartOfAccount l2Of(ChartOfAccount l3Row) {
        return byCode.get(l3Row.getParentChartAcctCd());
    }

    /** L1 ancestor of an L3 row (the {@code ACC-*} root). */
    public ChartOfAccount l1Of(ChartOfAccount l3Row) {
        ChartOfAccount l2 = l2Of(l3Row);
        if (l2 == null) return null;
        return byCode.get(l2.getParentChartAcctCd());
    }

    /**
     * All Level-2 rows belonging to a Level-1 root. Sorted by
     * {@code chart_acct_cd} for deterministic emission order.
     */
    public List<ChartOfAccount> l2sUnderRoot(String rootCode) {
        return l2ByCode.values().stream()
                .filter(l2 -> rootCode.equals(l2.getParentChartAcctCd()))
                .filter(l2 -> "A".equals(l2.getStts()))
                .sorted(java.util.Comparator.comparing(ChartOfAccount::getChartAcctCd))
                .toList();
    }

    /** All Level-3 rows whose direct parent is the supplied L2 code. */
    public List<ChartOfAccount> l3sUnderL2(String l2Code) {
        return l3Active.stream()
                .filter(r -> l2Code.equals(r.getParentChartAcctCd()))
                .sorted(java.util.Comparator.comparing(ChartOfAccount::getChartAcctCd))
                .toList();
    }
}
