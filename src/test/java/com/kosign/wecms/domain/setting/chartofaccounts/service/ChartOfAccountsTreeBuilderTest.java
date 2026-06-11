package com.kosign.wecms.domain.setting.chartofaccounts.service;

import com.kosign.wecms.domain.setting.chartofaccounts.dto.ChartOfAccountRow;
import com.kosign.wecms.domain.setting.chartofaccounts.dto.ChartOfAccountTreeNode;
import com.kosign.wecms.domain.setting.chartofaccounts.entity.ChartAcctInfm;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure-logic coverage for {@link ChartOfAccountsTreeBuilder}. No
 * Mockito, no Spring — exercises the level / has_children / breadcrumb
 * projection against hand-built {@link ChartAcctInfm} graphs.
 */
class ChartOfAccountsTreeBuilderTest {

    private static final String TENANT = "VN_VNM0004_000001";

    @Test
    void rows_emptySliceReturnsEmptyList() {
        assertThat(ChartOfAccountsTreeBuilder.buildRows(List.of())).isEmpty();
        assertThat(ChartOfAccountsTreeBuilder.buildTree(List.of())).isEmpty();
    }

    @Test
    void rows_resolvesLevelHasChildrenAndRestrictedFlags() {
        ChartAcctInfm root  = row("70",  "ACC-01",   "REVENUE",      null,    "A");
        ChartAcctInfm child = row("99",  "1007009",  "Collection 4", "ACC-01","A");
        ChartAcctInfm loose = row("90",  "10070090", "Collection 45",null,    "A");

        List<ChartOfAccountRow> projected =
                ChartOfAccountsTreeBuilder.buildRows(List.of(root, child, loose));

        Map<String, ChartOfAccountRow> byCode = byCode(projected);
        assertThat(byCode.get("ACC-01").chartAcctLevel()).isEqualTo(1);
        assertThat(byCode.get("ACC-01").hasChildren()).isTrue();
        assertThat(byCode.get("ACC-01").restricted()).isTrue();

        assertThat(byCode.get("1007009").chartAcctLevel()).isEqualTo(2);
        assertThat(byCode.get("1007009").hasChildren()).isFalse();
        assertThat(byCode.get("1007009").restricted()).isFalse();

        // root with no children
        assertThat(byCode.get("10070090").chartAcctLevel()).isEqualTo(1);
        assertThat(byCode.get("10070090").hasChildren()).isFalse();
    }

    @Test
    void rows_orphanedParentChainResolvesToNullLevel() {
        // CDX-001 → Code009 (parent missing) → null level
        ChartAcctInfm orphan = row("112", "CDX-001", "asdfasd", "Code009", "A");
        List<ChartOfAccountRow> projected =
                ChartOfAccountsTreeBuilder.buildRows(List.of(orphan));
        assertThat(projected.get(0).chartAcctLevel()).isNull();
    }

    @Test
    void rows_selfReferenceResolvesToNullLevel() {
        // seq 104 in DEV — chart_acct_cd '2001001' with parent '2001001'.
        // The tree builder treats this as a cycle and returns null
        // level so the frontend renders the row under the grandfathered
        // footnote.
        ChartAcctInfm self = row("104", "2001001", "Payment K1", "2001001", "A");
        List<ChartOfAccountRow> projected =
                ChartOfAccountsTreeBuilder.buildRows(List.of(self));
        assertThat(projected.get(0).chartAcctLevel()).isNull();
    }

    @Test
    void rows_depthCapDropsRowsBeyondLevelThree() {
        // PO directive 2026-05-25: MAX_HIERARCHY_DEPTH = 3. Only
        // level 1 (seeded roots), level 2, and level 3 are valid
        // levels in the tree projection; anything deeper surfaces as
        // null so the frontend can footnote it as grandfathered data.
        //
        // Build a 4-deep chain: A → B → C → D. D must surface as null.
        ChartAcctInfm a = row("1", "A", "A", null, "A");
        ChartAcctInfm b = row("2", "B", "B", "A",  "A");
        ChartAcctInfm c = row("3", "C", "C", "B",  "A");
        ChartAcctInfm d = row("4", "D", "D", "C",  "A");
        List<ChartOfAccountRow> projected =
                ChartOfAccountsTreeBuilder.buildRows(List.of(a, b, c, d));
        Map<String, ChartOfAccountRow> byCode = byCode(projected);
        assertThat(byCode.get("A").chartAcctLevel()).isEqualTo(1);
        assertThat(byCode.get("B").chartAcctLevel()).isEqualTo(2);
        assertThat(byCode.get("C").chartAcctLevel()).isEqualTo(3);
        // D exceeds the 3-level cap.
        assertThat(byCode.get("D").chartAcctLevel()).isNull();
    }

    @Test
    void tree_filtersOrphansAndDeeperRows() {
        ChartAcctInfm root   = row("70", "ACC-01",  "REVENUE",      null,    "A");
        ChartAcctInfm child  = row("99", "1007009", "Collection 4", "ACC-01","A");
        ChartAcctInfm orphan = row("112","CDX-001", "asdfasd",      "Code009","A");

        List<ChartOfAccountTreeNode> nodes =
                ChartOfAccountsTreeBuilder.buildTree(List.of(root, child, orphan));

        assertThat(nodes).extracting(ChartOfAccountTreeNode::chartAcctCd)
                .containsExactly("1007009", "ACC-01");        // sorted by code ASC
        // Orphan was filtered out.
        assertThat(nodes).extracting(ChartOfAccountTreeNode::chartAcctCd)
                .doesNotContain("CDX-001");

        ChartOfAccountTreeNode childNode = nodes.stream()
                .filter(n -> n.chartAcctCd().equals("1007009"))
                .findFirst().orElseThrow();
        assertThat(childNode.breadcrumb()).isEqualTo("ACC-01 - REVENUE / 1007009 - Collection 4");
        assertThat(childNode.chartAcctLevel()).isEqualTo(2);
        assertThat(childNode.hasChildren()).isFalse();
    }

    /* ---------------------------------------------------------------
     *  Helpers
     * --------------------------------------------------------------- */

    private static ChartAcctInfm row(String seqNo, String code, String nameEn,
                                       String parentCd, String stts) {
        ChartAcctInfm e = new ChartAcctInfm();
        e.setBizKey(TENANT);
        e.setChartAcctSeqNo(seqNo);
        e.setChartAcctCd(code);
        e.setChartAcctNmEn(nameEn);
        e.setParentChartAcctCd(parentCd);
        e.setStts(stts);
        e.setDelYn("N");
        e.setRegId("tester");
        e.setRegDtm("20260513000000");
        e.setModId("tester");
        e.setModDtm("20260513000000");
        return e;
    }

    private static Map<String, ChartOfAccountRow> byCode(List<ChartOfAccountRow> rows) {
        return rows.stream().collect(
                java.util.stream.Collectors.toMap(ChartOfAccountRow::chartAcctCd, r -> r));
    }
}
