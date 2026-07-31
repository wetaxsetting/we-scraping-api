package com.kosign.wecms.domain.payment.dispatch.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Query-contract guard for the API-path dispatch claim — the single mechanism that stops a
 * real-time transfer being sent twice.
 *
 * <p><b>Why a query-text guard rather than a {@code @DataJpaTest}:</b> the project ships no
 * Testcontainers and no embedded database (see the "Not currently in build.gradle" note in
 * {@code CLAUDE.md}), so there is no way to execute two concurrent transactions and observe
 * the affected-row counts. The property that actually matters, however, is <em>static</em>:
 * the claim must be a single conditional {@code UPDATE … WHERE proc_stat = '9'}, not a
 * read-then-write. That is assertable from the annotation, and it is the same idiom the
 * repo already uses for its other repository contracts
 * ({@code ScdulPymnSumrSearchStatusFilterTest}, {@code VendorPaymentSortFieldTest}).
 *
 * <p><b>What breaks without this.</b> If the {@code AND t.procStat = '9'} predicate is ever
 * dropped, or the method is refactored into a load-mutate-save pair, the update stops being
 * a claim: two concurrent drivers (the after-commit dispatch hook and a stranded-recovery
 * sweep, or two nodes) would both "win" and the 202 transfer would be issued twice. Nothing
 * else in the suite would fail — {@code RealtimeDispatchServiceTest.g2_claimLost_isNoOp_noTransfer}
 * stubs the affected-row count, so it verifies the caller's handling, never the query.
 *
 * <p><b>Both rails are covered.</b> The API rail's {@code TbPayTarget.claimForDispatch} and the bulk
 * rail's {@code TbPayFileMst.claimForReception} (added 2026-07-29 to fix H-4) are asserted against the
 * same contract, because a claim that is not a single conditional UPDATE is not a claim. The bulk rail
 * previously had no claim at all — it gated on a read-then-check and could double-dispatch; see
 * {@code docs/architecture/bulk-dispatch-concurrency-fix.md}.
 */
class DispatchClaimQueryContractTest {

    private static Method claimMethod() throws NoSuchMethodException {
        return TbPayTargetRepository.class.getMethod(
                "claimForDispatch", String.class, String.class, String.class, String.class);
    }

    private static Method bulkClaimMethod() throws NoSuchMethodException {
        return TbPayFileMstRepository.class.getMethod(
                "claimForReception", String.class, String.class, String.class, String.class);
    }

    @Test
    @DisplayName("claimForDispatch is annotated @Modifying — it issues an UPDATE, not a select")
    void claimIsModifying() throws Exception {
        assertThat(claimMethod().getAnnotation(Modifying.class))
                .as("claimForDispatch must be @Modifying; without it Spring Data would not "
                        + "execute the UPDATE at all")
                .isNotNull();
    }

    @Test
    @DisplayName("claimForDispatch returns the affected-row count so a lost claim is detectable")
    void claimReturnsAffectedRowCount() throws Exception {
        // A void claim cannot be distinguished from a no-op — the caller must be able to see
        // "0 rows changed" and stand down without dispatching.
        assertThat(claimMethod().getReturnType())
                .as("claimForDispatch must return int (rows affected)")
                .isEqualTo(int.class);
    }

    @Test
    @DisplayName("claimForDispatch is one conditional UPDATE guarded on the WAIT state")
    void claimIsAConditionalUpdateOnWaitState() throws Exception {
        Query query = claimMethod().getAnnotation(Query.class);
        assertThat(query).as("claimForDispatch must carry an explicit @Query").isNotNull();

        String jpql = query.value().replaceAll("\\s+", " ").trim();

        assertThat(jpql)
                .as("must be an UPDATE statement (the atomic claim), not a SELECT")
                .startsWithIgnoringCase("UPDATE TbPayTarget");

        // The claim predicate. Without this the UPDATE is unconditional and both concurrent
        // drivers would report 1 row affected — the exact double-dispatch this guards.
        assertThat(jpql)
                .as("the WAIT-state guard (procStat = '9') is what makes this a claim; "
                        + "removing it re-opens concurrent double-dispatch")
                .containsIgnoringWhitespaces("t.procStat = '9'");

        // Claiming means moving WAIT -> in-flight in the same statement.
        assertThat(jpql)
                .as("the claim must transition to REQ ('6') in the same statement")
                .containsIgnoringWhitespaces("t.procStat = '6'");

        // Fully key-qualified so the claim can never widen beyond one pay-target row.
        assertThat(jpql).containsIgnoringWhitespaces("t.bizKey = :bizKey");
        assertThat(jpql).containsIgnoringWhitespaces("t.tranDt = :tranDt");
        assertThat(jpql).containsIgnoringWhitespaces("t.tranSeq = :tranSeq");
    }

    @Test
    @DisplayName("the claim is tenant-scoped — BIZ_KEY is never optional or wildcarded")
    void claimIsTenantScoped() throws Exception {
        String jpql = claimMethod().getAnnotation(Query.class).value();
        // Workspace rule: every tenant-scoped query constrains on BIZ_KEY; a claim that
        // did not would be able to move another tenant's row.
        assertThat(jpql).contains(":bizKey");
        assertThat(jpql).doesNotContain("LIKE");
        assertThat(jpql).doesNotContain("%");
    }

    // ── bulk rail (H-4) ──────────────────────────────────────────────────────

    @Test
    @DisplayName("bulk claimForReception is @Modifying and returns the affected-row count")
    void bulkClaimIsModifyingAndReturnsCount() throws Exception {
        assertThat(bulkClaimMethod().getAnnotation(Modifying.class))
                .as("claimForReception must be @Modifying")
                .isNotNull();
        assertThat(bulkClaimMethod().getReturnType())
                .as("claimForReception must return int so a lost claim (0 rows) is detectable "
                        + "BEFORE the SFTP upload and the EFS 00301 call")
                .isEqualTo(int.class);
    }

    @Test
    @DisplayName("bulk claimForReception is one conditional UPDATE guarded on the WAIT state")
    void bulkClaimIsAConditionalUpdateOnWaitState() throws Exception {
        Query query = bulkClaimMethod().getAnnotation(Query.class);
        assertThat(query).as("claimForReception must carry an explicit @Query").isNotNull();

        String jpql = query.value().replaceAll("\\s+", " ").trim();

        assertThat(jpql)
                .as("must be an UPDATE — a SELECT-then-save is exactly the read-then-check that "
                        + "allowed two drivers to both reach the egress boundary")
                .startsWithIgnoringCase("UPDATE TbPayFileMst");
        assertThat(jpql)
                .as("the WAIT guard (procStat = '9') is what makes this atomic; without it both "
                        + "concurrent drivers would report 1 row affected and both would dispatch")
                .containsIgnoringWhitespaces("p.procStat = '9'");
        assertThat(jpql)
                .as("the claim must move the row to REQ ('6') in the same statement")
                .containsIgnoringWhitespaces("p.procStat = '6'");

        // Fully key-qualified: the claim must move exactly one logical bulk file, never a range.
        assertThat(jpql).containsIgnoringWhitespaces("p.bizKey = :bizKey");
        assertThat(jpql).containsIgnoringWhitespaces("p.tranDt = :tranDt");
        assertThat(jpql).containsIgnoringWhitespaces("p.fileSeqNo = :fileSeqNo");
    }

    @Test
    @DisplayName("bulk claim is tenant-scoped — FILE_SEQ_NO is global, so BIZ_KEY is mandatory")
    void bulkClaimIsTenantScoped() throws Exception {
        String jpql = bulkClaimMethod().getAnnotation(Query.class).value();
        // FILE_SEQ_NO comes from the GLOBAL firm_file_seq_no sequence, so two tenants can hold the
        // same value. Without BIZ_KEY in the predicate one tenant's claim could move another's row.
        assertThat(jpql).contains(":bizKey");
        assertThat(jpql).doesNotContain("LIKE");
        assertThat(jpql).doesNotContain("%");
    }

    @Test
    @DisplayName("both rails express the claim the same way — neither may drift")
    void bothRailsShareTheSameClaimShape() throws Exception {
        for (Method m : new Method[] {claimMethod(), bulkClaimMethod()}) {
            String jpql = m.getAnnotation(Query.class).value().replaceAll("\\s+", " ");
            assertThat(m.getAnnotation(Modifying.class)).as("%s @Modifying", m.getName()).isNotNull();
            assertThat(m.getReturnType()).as("%s returns int", m.getName()).isEqualTo(int.class);
            assertThat(jpql).as("%s is an UPDATE", m.getName()).containsIgnoringCase("UPDATE");
            assertThat(jpql).as("%s guards on WAIT", m.getName()).contains("= '9'");
            assertThat(jpql).as("%s transitions to REQ", m.getName()).contains("= '6'");
        }
    }
}
