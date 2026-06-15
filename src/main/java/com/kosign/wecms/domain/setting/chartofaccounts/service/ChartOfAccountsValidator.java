package com.kosign.wecms.domain.setting.chartofaccounts.service;

import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.domain.setting.chartofaccounts.ChartOfAccountsConstants;
import com.kosign.wecms.domain.setting.chartofaccounts.entity.ChartAcctInfm;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Cross-row validations for Chart of Accounts writes. Bean validation
 * (record component annotations) guards single-field shape; this class
 * guards rules that need the rest of the tenant's tree:
 *
 * <ul>
 *   <li>{@link #assertNotRestricted} — block edits / deletes against
 *       {@code ACC-01}, {@code ACC-02}, {@code ACC-03}.</li>
 *   <li>{@link #assertNotRenamingToRestricted} — block Update renames
 *       TO any restricted code.</li>
 *   <li>{@link #assertNotSelfParent} — refactor addition (legacy DEV
 *       data contains self-references; refactor blocks at write).</li>
 *   <li>{@link #assertParentExists} — block writes whose
 *       {@code parent_chart_acct_cd} doesn't resolve to an active row
 *       in the same tenant.</li>
 *   <li>{@link #assertNoCycle} — refactor addition. Walks the ancestor
 *       chain from the proposed new parent; rejects if the row being
 *       edited appears in the chain.</li>
 *   <li>{@link #assertDepthCap} — refactor addition. Computes the
 *       depth of the subtree rooted at the row after the edit; rejects
 *       if any leaf would exceed {@link ChartOfAccountsConstants#MAX_HIERARCHY_DEPTH}.</li>
 * </ul>
 *
 * <p>Every check is pure — input is the proposed write + the current
 * tenant's full row slice (loaded once by the service per write). No
 * DB round-trips inside the validator.
 *
 * <p>OQ-3 boundary: the legacy {@code u001:237-256} "parent cleanup"
 * branch is NOT ported. The cleanup nullifies an unrelated row's
 * parent in self-referencing edits, which looks like leftover debug
 * instrumentation. Direction pending PO confirmation; refactor
 * recommendation is to drop it.
 */
public final class ChartOfAccountsValidator {

    private ChartOfAccountsValidator() { }

    /**
     * Throw {@code WBE_CHART_OF_ACCOUNTS_RESTRICTED_CODE} when the row
     * being edited / deleted has a code in
     * {@link ChartOfAccountsConstants#RESTRICTED_CODES}. Matches the
     * legacy {@code restrictedCodes} guard at
     * {@code comm_0223_001_u001_act.jsp:213-217} +
     * {@code comm_0223_001_d001_act.jsp:172-176}.
     */
    public static void assertNotRestricted(String existingCode) {
        if (existingCode != null
                && ChartOfAccountsConstants.RESTRICTED_CODES.contains(existingCode)) {
            throw new BusinessException("WBE_CHART_OF_ACCOUNTS_RESTRICTED_CODE", existingCode);
        }
    }

    /**
     * Throw {@code WBE_CHART_OF_ACCOUNTS_RESTRICTED_CODE} when an
     * Update renames a non-restricted row TO a restricted code.
     * Refactor addition — legacy is silent on the rename direction.
     */
    public static void assertNotRenamingToRestricted(String oldCode, String newCode) {
        if (oldCode == null || newCode == null || oldCode.equals(newCode)) return;
        if (ChartOfAccountsConstants.RESTRICTED_CODES.contains(newCode)) {
            throw new BusinessException("WBE_CHART_OF_ACCOUNTS_RESTRICTED_CODE", newCode);
        }
    }

    /**
     * Throw {@code WBE_CHART_OF_ACCOUNTS_SELF_PARENT} when a write
     * sets {@code parent_chart_acct_cd} equal to the row's own
     * {@code chart_acct_cd}. Refactor addition — legacy DEV's seq 104
     * row {@code 2001001} parents itself, so the check is grandfathered
     * (the refactor catches new writes only).
     */
    public static void assertNotSelfParent(String code, String newParentCd) {
        if (newParentCd != null && newParentCd.equals(code)) {
            throw new BusinessException("WBE_CHART_OF_ACCOUNTS_SELF_PARENT");
        }
    }

    /**
     * Throw {@code WBE_CHART_OF_ACCOUNTS_PARENT_NOT_FOUND} when
     * {@code newParentCd} is non-blank but doesn't resolve to an
     * active row in the supplied tenant slice. Null / blank parent is
     * legal at this layer; {@link #assertParentRequired} is what
     * enforces the user-facing rule that every user-created row needs
     * a parent.
     */
    public static void assertParentExists(String newParentCd, Map<String, ChartAcctInfm> activeByCode) {
        if (newParentCd == null || newParentCd.isBlank()) return;
        if (!activeByCode.containsKey(newParentCd)) {
            throw new BusinessException("WBE_CHART_OF_ACCOUNTS_PARENT_NOT_FOUND", newParentCd);
        }
    }

    /**
     * Throw {@code WBE_CHART_OF_ACCOUNTS_PARENT_REQUIRED} when the
     * caller tries to create / update a row without a parent code.
     *
     * <p><b>Business rule (PO directive 2026-05-25):</b> only level 2
     * and level 3 are user-creatable; level 1 (root rows with NULL
     * parent) is reserved for the three seeded
     * {@link ChartOfAccountsConstants#RESTRICTED_CODES} (ACC-01,
     * ACC-02, ACC-03). The combination of this check + the
     * {@link ChartOfAccountsConstants#MAX_HIERARCHY_DEPTH}=3 cap
     * confines every user-driven write to level 2 or level 3 exactly.
     *
     * <p>This rule applies to the dialog single-create, the Excel
     * upload bulk path (which routes through {@code create()}), and
     * the inline parent rename on update. The DB schema still permits
     * NULL parent because the seed itself populates the three roots
     * with NULL parent; the rule lives at the service layer so seeds
     * stay legal.
     */
    public static void assertParentRequired(String parentCd) {
        if (parentCd == null || parentCd.isBlank()) {
            throw new BusinessException("WBE_CHART_OF_ACCOUNTS_PARENT_REQUIRED");
        }
    }

    /**
     * Throw {@code WBE_CHART_OF_ACCOUNTS_CAL_RULE_LEVEL3_ONLY} when a
     * non-blank {@code calRule} is set on a row that wouldn't be at
     * level 3.
     *
     * <p><b>Business rule (PO directive 2026-05-25):</b> the Calc Rule
     * ({@code +} / {@code -}, stored on the wire as {@code P} /
     * {@code M}) is meaningful only on leaf rollups at level 3. Levels
     * 1 and 2 act as grouping headers and must not carry a sign — the
     * display in the table screenshot showed level-2 rows under
     * ACC-03 incorrectly rendering a {@code +} / {@code -} badge,
     * which this guard blocks on the write side.
     *
     * <p>A null / blank {@code calRule} is always permitted (level 1
     * and 2 rows just don't carry one). This rule complements
     * {@link #assertParentRequired} + the level-3 cap so the three
     * user-facing rules ride together on every write.
     */
    public static void assertCalRuleLevel3Only(String parentCd, String calRule,
                                               Map<String, ChartAcctInfm> activeByCode) {
        if (calRule == null || calRule.isBlank()) return;
        int level = computeNewDepth(parentCd, activeByCode);
        if (level != 3) {
            throw new BusinessException("WBE_CHART_OF_ACCOUNTS_CAL_RULE_LEVEL3_ONLY");
        }
    }

    /**
     * Throw {@code WBE_CHART_OF_ACCOUNTS_CYCLE} when walking the
     * ancestor chain from {@code newParentCd} would reach
     * {@code rowCode}. Caps the walk at {@code MAX_HIERARCHY_DEPTH + 2}
     * iterations so an already-corrupt tenant slice (cycle pre-existing
     * in seeded data) cannot loop the validator forever.
     */
    public static void assertNoCycle(String rowCode, String newParentCd,
                                     Map<String, ChartAcctInfm> activeByCode) {
        if (rowCode == null || newParentCd == null || newParentCd.isBlank()) return;
        String cursor = newParentCd;
        int hops = 0;
        while (cursor != null && !cursor.isBlank()) {
            if (rowCode.equals(cursor)) {
                throw new BusinessException("WBE_CHART_OF_ACCOUNTS_CYCLE");
            }
            if (++hops > ChartOfAccountsConstants.MAX_HIERARCHY_DEPTH + 2) {
                // Pre-existing cycle higher up in the tree. Don't let
                // the walk loop forever — surface as a generic cycle
                // error so the user sees an actionable message.
                throw new BusinessException("WBE_CHART_OF_ACCOUNTS_CYCLE");
            }
            ChartAcctInfm node = activeByCode.get(cursor);
            if (node == null) return; // orphan — no further ancestry
            cursor = node.getParentChartAcctCd();
        }
    }

    /**
     * Throw {@code WBE_CHART_OF_ACCOUNTS_DEPTH_EXCEEDED} when applying
     * the edit would push any descendant beyond the depth cap. Two
     * pieces:
     *
     * <ol>
     *   <li>Compute the row's <b>new depth</b> by walking from
     *       {@code newParentCd} up to root (cap-bound walk).</li>
     *   <li>Compute the <b>height</b> of the subtree rooted at
     *       {@code rowCode} (longest path down through descendants).</li>
     * </ol>
     *
     * The resulting deepest leaf depth is {@code newDepth + height
     * - 1}. If &gt; {@link ChartOfAccountsConstants#MAX_HIERARCHY_DEPTH},
     * throw.
     */
    public static void assertDepthCap(String rowCode, String newParentCd,
                                      Map<String, ChartAcctInfm> activeByCode) {
        int newDepth = computeNewDepth(newParentCd, activeByCode);
        int subtreeHeight = computeSubtreeHeight(rowCode, activeByCode);
        int deepestLeaf = newDepth + subtreeHeight - 1;
        if (deepestLeaf > ChartOfAccountsConstants.MAX_HIERARCHY_DEPTH) {
            throw new BusinessException("WBE_CHART_OF_ACCOUNTS_DEPTH_EXCEEDED");
        }
    }

    /**
     * Group the tenant slice by {@code CHART_ACCT_CD} so the
     * validators get O(1) lookups. Soft-deleted rows are dropped here
     * — the validators operate only on the active set. When the slice
     * contains duplicate codes (DB UK violation), the later row wins;
     * this should be impossible given the UK but is defensive.
     */
    public static Map<String, ChartAcctInfm> indexActiveByCode(List<ChartAcctInfm> tenantSlice) {
        Map<String, ChartAcctInfm> out = new HashMap<>(tenantSlice.size());
        for (ChartAcctInfm row : tenantSlice) {
            if (!ChartOfAccountsConstants.DEL_YN_ACTIVE.equals(row.getDelYn())) continue;
            out.put(row.getChartAcctCd(), row);
        }
        return out;
    }

    /* ---------------------------------------------------------------
     *  Internal helpers
     * --------------------------------------------------------------- */

    /** Depth of a row whose parent is {@code parentCd}. A row with
     *  {@code parentCd == null} is depth 1. */
    private static int computeNewDepth(String parentCd, Map<String, ChartAcctInfm> byCode) {
        int depth = 1;
        String cursor = parentCd;
        int hops = 0;
        Set<String> seen = new HashSet<>();
        while (cursor != null && !cursor.isBlank()) {
            if (!seen.add(cursor)) break; // pre-existing cycle — cap walk
            depth++;
            if (++hops > ChartOfAccountsConstants.MAX_HIERARCHY_DEPTH + 2) break;
            ChartAcctInfm node = byCode.get(cursor);
            if (node == null) break; // orphan
            cursor = node.getParentChartAcctCd();
        }
        return depth;
    }

    /** Height (longest descendant chain + 1) of the subtree rooted at
     *  {@code rowCode}. A leaf row has height 1. Capped at
     *  {@code MAX_HIERARCHY_DEPTH + 1} to bound corrupt-data walks. */
    private static int computeSubtreeHeight(String rowCode, Map<String, ChartAcctInfm> byCode) {
        if (rowCode == null) return 1;
        int maxHeight = 1;
        for (ChartAcctInfm candidate : byCode.values()) {
            if (Objects.equals(candidate.getParentChartAcctCd(), rowCode)
                    && !Objects.equals(candidate.getChartAcctCd(), rowCode)) {
                int childHeight = 1 + computeSubtreeHeight(candidate.getChartAcctCd(), byCode);
                if (childHeight > maxHeight) maxHeight = childHeight;
                if (maxHeight > ChartOfAccountsConstants.MAX_HIERARCHY_DEPTH + 1) break;
            }
        }
        return maxHeight;
    }
}
