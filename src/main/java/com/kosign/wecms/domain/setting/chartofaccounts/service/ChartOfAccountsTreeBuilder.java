package com.kosign.wecms.domain.setting.chartofaccounts.service;

import com.kosign.wecms.domain.setting.chartofaccounts.ChartOfAccountsConstants;
import com.kosign.wecms.domain.setting.chartofaccounts.dto.ChartOfAccountRow;
import com.kosign.wecms.domain.setting.chartofaccounts.dto.ChartOfAccountTreeNode;
import com.kosign.wecms.domain.setting.chartofaccounts.entity.ChartOfAccount;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Pure projection helper: given a flat list of {@link ChartOfAccount}
 * rows for one tenant, compute the {@code chart_acct_level},
 * {@code has_children}, and {@code breadcrumb} fields that the wire
 * surfaces alongside the raw columns.
 *
 * <p>Mirrors the legacy {@code IDO.CHART_ACCT_INFM_R002.xml} SQL which
 * inlines the same projection as a 100-line CASE expression. The
 * refactor moves the work to Java so the SQL stays simple and the
 * hierarchy logic can be unit-tested.
 *
 * <p>Depth cap matches the legacy R002 behaviour: rows that resolve to
 * level 1..4 carry the level; rows beyond level 4 OR whose parent
 * chain breaks (orphan, missing parent) carry {@code chart_acct_level
 * = null}. Frontend renders both — the level-null rows are displayed
 * under a "grandfathered" footnote in the planning doc.
 */
public final class ChartOfAccountsTreeBuilder {

    private ChartOfAccountsTreeBuilder() { }

    /**
     * Build list-shaped rows in the order the input slice was given.
     * The service is responsible for fetching the slice already sorted
     * (Spring Data {@code Sort} on the repository) — this method
     * preserves order.
     */
    public static List<ChartOfAccountRow> buildRows(List<ChartOfAccount> tenantSlice) {
        if (tenantSlice.isEmpty()) return List.of();
        Index ix = index(tenantSlice);
        List<ChartOfAccountRow> out = new ArrayList<>(tenantSlice.size());
        for (ChartOfAccount row : tenantSlice) {
            out.add(toRow(row, ix));
        }
        return out;
    }

    /**
     * Build tree-projection nodes for the parent-picker. Output is
     * sorted by {@code CHART_ACCT_CD ASC} (mirrors legacy R002
     * trailer's {@code ORDER BY T.CHART_ACCT_CD ASC}) and filtered to
     * rows whose level resolves to 1..4 (matches the legacy outer
     * {@code WHERE T.CHART_ACCT_LEVEL IS NOT NULL}).
     */
    public static List<ChartOfAccountTreeNode> buildTree(List<ChartOfAccount> tenantSlice) {
        if (tenantSlice.isEmpty()) return List.of();
        Index ix = index(tenantSlice);
        List<ChartOfAccountTreeNode> out = new ArrayList<>(tenantSlice.size());
        for (ChartOfAccount row : tenantSlice) {
            Integer level = ix.levelByCode.get(row.getChartAcctCd());
            if (level == null) continue; // skip orphans / depth>cap
            out.add(new ChartOfAccountTreeNode(
                    row.getChartAcctSeqNo(),
                    row.getChartAcctCd(),
                    row.getChartAcctNmEn(),
                    row.getParentChartAcctCd(),
                    level,
                    ix.hasChildren.contains(row.getChartAcctCd()),
                    ChartOfAccountsConstants.RESTRICTED_CODES.contains(row.getChartAcctCd()),
                    buildBreadcrumb(row, ix)));
        }
        out.sort(Comparator.comparing(ChartOfAccountTreeNode::chartAcctCd));
        return out;
    }

    /* ---------------------------------------------------------------
     *  Internal — index pass + per-row projection
     * --------------------------------------------------------------- */

    private static ChartOfAccountRow toRow(ChartOfAccount row, Index ix) {
        return new ChartOfAccountRow(
                row.getChartAcctSeqNo(),
                row.getChartAcctCd(),
                row.getChartAcctNmEn(),
                row.getChartAcctNmVn(),
                row.getChartAcctNmKo(),
                row.getChartAcctType(),
                row.getParentChartAcctCd(),
                row.getCalRule(),
                row.getStts(),
                ix.levelByCode.get(row.getChartAcctCd()),
                ix.hasChildren.contains(row.getChartAcctCd()),
                ChartOfAccountsConstants.RESTRICTED_CODES.contains(row.getChartAcctCd()),
                row.getRegId(),
                row.getRegDtm(),
                row.getModId(),
                row.getModDtm());
    }

    /**
     * Build the {@code "<rootCd> - <rootNm> / … / <currentCd> -
     * <currentNm>"} chain. Mirrors legacy R002's
     * {@code CHART_ACCT_DIPS_NAME}.
     */
    private static String buildBreadcrumb(ChartOfAccount row, Index ix) {
        List<ChartOfAccount> chain = new ArrayList<>();
        ChartOfAccount cursor = row;
        Set<String> seen = new HashSet<>();
        int hops = 0;
        while (cursor != null) {
            if (!seen.add(cursor.getChartAcctCd())) break; // safety on cycles
            chain.add(0, cursor);
            if (++hops > ChartOfAccountsConstants.MAX_HIERARCHY_DEPTH + 2) break;
            String parentCd = cursor.getParentChartAcctCd();
            if (parentCd == null || parentCd.isBlank()) break;
            cursor = ix.byCode.get(parentCd);
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < chain.size(); i++) {
            if (i > 0) sb.append(" / ");
            ChartOfAccount c = chain.get(i);
            sb.append(c.getChartAcctCd()).append(" - ").append(c.getChartAcctNmEn());
        }
        return sb.toString();
    }

    /**
     * Single-pass build of the three lookups the projection needs:
     * code → entity, code → level (or null), set of codes that have
     * at least one active child.
     */
    private static Index index(List<ChartOfAccount> tenantSlice) {
        Map<String, ChartOfAccount> byCode = new HashMap<>(tenantSlice.size());
        for (ChartOfAccount row : tenantSlice) {
            byCode.put(row.getChartAcctCd(), row);
        }

        Set<String> parentCodesSeen = new HashSet<>();
        for (ChartOfAccount row : tenantSlice) {
            String parentCd = row.getParentChartAcctCd();
            if (parentCd != null && !parentCd.isBlank()
                    && !Objects.equals(parentCd, row.getChartAcctCd())) {
                parentCodesSeen.add(parentCd);
            }
        }

        Map<String, Integer> levelByCode = new HashMap<>(tenantSlice.size());
        for (ChartOfAccount row : tenantSlice) {
            levelByCode.put(row.getChartAcctCd(), resolveLevel(row, byCode));
        }

        return new Index(byCode, levelByCode, parentCodesSeen);
    }

    /**
     * Walk parents from {@code row} until we hit a root (null parent)
     * or the cap. Return {@code null} if the chain breaks (orphan
     * parent) or exceeds {@link ChartOfAccountsConstants#MAX_HIERARCHY_DEPTH}.
     * A self-referencing row (legacy data carries some — see seq 104
     * in DEV) is treated as orphaned because its chain neither
     * terminates nor extends — return null so the frontend renders it
     * under the grandfathered footnote.
     */
    private static Integer resolveLevel(ChartOfAccount row, Map<String, ChartOfAccount> byCode) {
        int level = 1;
        String parentCd = row.getParentChartAcctCd();
        Set<String> seen = new HashSet<>();
        seen.add(row.getChartAcctCd());
        while (parentCd != null && !parentCd.isBlank()) {
            if (!seen.add(parentCd)) return null; // cycle
            level++;
            if (level > ChartOfAccountsConstants.MAX_HIERARCHY_DEPTH) return null;
            ChartOfAccount parent = byCode.get(parentCd);
            if (parent == null) return null; // orphan
            parentCd = parent.getParentChartAcctCd();
        }
        return level;
    }

    private record Index(Map<String, ChartOfAccount> byCode,
                          Map<String, Integer> levelByCode,
                          Set<String> hasChildren) { }
}
