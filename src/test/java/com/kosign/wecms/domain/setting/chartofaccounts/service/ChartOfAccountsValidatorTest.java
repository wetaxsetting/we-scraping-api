package com.kosign.wecms.domain.setting.chartofaccounts.service;

import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.domain.setting.chartofaccounts.entity.ChartOfAccount;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Pure-logic coverage for {@link ChartOfAccountsValidator}. */
class ChartOfAccountsValidatorTest {

    @Test
    void assertNotRestricted_blocksAcc01Acc02Acc03() {
        assertThatThrownBy(() -> ChartOfAccountsValidator.assertNotRestricted("ACC-01"))
                .isInstanceOf(BusinessException.class)
                .extracting(b -> ((BusinessException) b).getCode())
                .isEqualTo("WBE_CHART_OF_ACCOUNTS_RESTRICTED_CODE");
        assertThatThrownBy(() -> ChartOfAccountsValidator.assertNotRestricted("ACC-02"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> ChartOfAccountsValidator.assertNotRestricted("ACC-03"))
                .isInstanceOf(BusinessException.class);
        assertThatCode(() -> ChartOfAccountsValidator.assertNotRestricted("1007009"))
                .doesNotThrowAnyException();
        assertThatCode(() -> ChartOfAccountsValidator.assertNotRestricted(null))
                .doesNotThrowAnyException();
    }

    @Test
    void assertNotRenamingToRestricted_blocksRenameToReserved() {
        assertThatThrownBy(() ->
                ChartOfAccountsValidator.assertNotRenamingToRestricted("CUSTOM-01", "ACC-01"))
                .isInstanceOf(BusinessException.class);
        assertThatCode(() ->
                ChartOfAccountsValidator.assertNotRenamingToRestricted("CUSTOM-01", "ACC-01-RENAMED"))
                .doesNotThrowAnyException();
        // Same code → no rename → no rejection.
        assertThatCode(() ->
                ChartOfAccountsValidator.assertNotRenamingToRestricted("ACC-01", "ACC-01"))
                .doesNotThrowAnyException();
    }

    @Test
    void assertNotSelfParent_rejectsExactMatch() {
        assertThatThrownBy(() -> ChartOfAccountsValidator.assertNotSelfParent("X-1", "X-1"))
                .isInstanceOf(BusinessException.class)
                .extracting(b -> ((BusinessException) b).getCode())
                .isEqualTo("WBE_CHART_OF_ACCOUNTS_SELF_PARENT");
        assertThatCode(() -> ChartOfAccountsValidator.assertNotSelfParent("X-1", "X-2"))
                .doesNotThrowAnyException();
        assertThatCode(() -> ChartOfAccountsValidator.assertNotSelfParent("X-1", null))
                .doesNotThrowAnyException();
    }

    @Test
    void assertParentExists_blocksMissingParent() {
        Map<String, ChartOfAccount> byCode = new HashMap<>();
        byCode.put("ROOT", entity("1", "ROOT", null));
        assertThatThrownBy(() ->
                ChartOfAccountsValidator.assertParentExists("UNKNOWN", byCode))
                .isInstanceOf(BusinessException.class)
                .extracting(b -> ((BusinessException) b).getCode())
                .isEqualTo("WBE_CHART_OF_ACCOUNTS_PARENT_NOT_FOUND");
        assertThatCode(() ->
                ChartOfAccountsValidator.assertParentExists("ROOT", byCode))
                .doesNotThrowAnyException();
        // null / blank parent is a legal root row.
        assertThatCode(() ->
                ChartOfAccountsValidator.assertParentExists(null, byCode))
                .doesNotThrowAnyException();
        assertThatCode(() ->
                ChartOfAccountsValidator.assertParentExists("", byCode))
                .doesNotThrowAnyException();
    }

    @Test
    void assertNoCycle_detectsAncestralLoop() {
        // A → B → A (loop) — moving X under B and trying to set X as
        // an ancestor would loop. Validator catches when rowCode is in
        // the ancestor chain of the proposed new parent.
        Map<String, ChartOfAccount> byCode = new HashMap<>();
        byCode.put("A", entity("1", "A", null));
        byCode.put("B", entity("2", "B", "A"));
        byCode.put("X", entity("3", "X", null));

        // Re-parent X under B → no cycle (X is not in B's chain).
        assertThatCode(() ->
                ChartOfAccountsValidator.assertNoCycle("X", "B", byCode))
                .doesNotThrowAnyException();

        // Re-parent A under B → cycle (B's ancestor chain is A,
        // and we're trying to make A's new parent B; walking up from
        // B reaches A which equals rowCode).
        assertThatThrownBy(() ->
                ChartOfAccountsValidator.assertNoCycle("A", "B", byCode))
                .isInstanceOf(BusinessException.class)
                .extracting(b -> ((BusinessException) b).getCode())
                .isEqualTo("WBE_CHART_OF_ACCOUNTS_CYCLE");
    }

    @Test
    void assertDepthCap_rejectsWritePushingSubtreeBeyondFour() {
        // Tree:        ROOT (1) → MID (2) → LEAF (3)
        // Edit LEAF.parent = LEAF_GROUP under a level-3 node would make
        // LEAF level 4. Then adding another level-5 descendant would
        // be rejected at write time. Here we directly test the helper:
        // setting LEAF as a child of a hypothetical chain that already
        // makes its new depth > 4.
        Map<String, ChartOfAccount> byCode = new HashMap<>();
        byCode.put("ROOT",  entity("1", "ROOT",  null));
        byCode.put("L2",    entity("2", "L2",    "ROOT"));
        byCode.put("L3",    entity("3", "L3",    "L2"));
        byCode.put("L4",    entity("4", "L4",    "L3"));
        byCode.put("NEW",   entity("5", "NEW",   null));

        // Re-parent NEW under L4 — new depth = 5 → rejected.
        assertThatThrownBy(() ->
                ChartOfAccountsValidator.assertDepthCap("NEW", "L4", byCode))
                .isInstanceOf(BusinessException.class)
                .extracting(b -> ((BusinessException) b).getCode())
                .isEqualTo("WBE_CHART_OF_ACCOUNTS_DEPTH_EXCEEDED");

        // Re-parent NEW under L3 — new depth = 4 → permitted.
        assertThatCode(() ->
                ChartOfAccountsValidator.assertDepthCap("NEW", "L3", byCode))
                .doesNotThrowAnyException();
    }

    @Test
    void indexActiveByCode_dropsSoftDeletedRows() {
        ChartOfAccount active  = entity("1", "ACTIVE",  null);
        ChartOfAccount deleted = entity("2", "DELETED", null);
        deleted.setDelYn("Y");

        Map<String, ChartOfAccount> byCode =
                ChartOfAccountsValidator.indexActiveByCode(List.of(active, deleted));

        org.assertj.core.api.Assertions.assertThat(byCode).containsOnlyKeys("ACTIVE");
    }

    /* ---------------------------------------------------------------
     *  Helpers
     * --------------------------------------------------------------- */

    private static ChartOfAccount entity(String seqNo, String code, String parentCd) {
        ChartOfAccount e = new ChartOfAccount();
        e.setBizKey("VN_VNM0004_000001");
        e.setChartAcctSeqNo(seqNo);
        e.setChartAcctCd(code);
        e.setChartAcctNmEn(code);
        e.setParentChartAcctCd(parentCd);
        e.setStts("A");
        e.setDelYn("N");
        e.setRegId("tester");
        e.setRegDtm("20260513000000");
        e.setModId("tester");
        e.setModDtm("20260513000000");
        return e;
    }
}
