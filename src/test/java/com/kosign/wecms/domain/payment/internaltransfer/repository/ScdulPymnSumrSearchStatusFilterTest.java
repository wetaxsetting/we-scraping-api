package com.kosign.wecms.domain.payment.internaltransfer.repository;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the approval-status filter predicate of
 * {@link ScdulPymnSumrRepository#search} — the Internal Transfer
 * Scheduled-Transfer list query.
 *
 * <p>Why a reflection / query-text guard rather than a {@code @DataJpaTest}:
 * the live status branches call the Postgres function
 * {@code wb_get_appr_stts(...)}, which has no H2 equivalent, and the project
 * intentionally ships no Testcontainers (see wecms-api CLAUDE.md "Tech Stack").
 * A true row-level parity check therefore runs against the JEX DEV DB
 * (validated read-only in the F-2 audit: legacy "Not Requested" = 3,
 * post-fix refactor = 3, including orphan SCDUL_PYMN_NO=272 / APPR_SEQ_NO=1394).
 * This test instead pins the predicate string so a future edit cannot silently
 * drop the F-2 orphan-inclusion leg or alter the other five filter branches.
 *
 * <p><b>F-2 fix:</b> the "00" (Not Requested) branch must include
 * {@code erm.apprTypeCd IS NULL} so an orphaned back-stamp (a schedule whose
 * {@code APPR_SEQ_NO} points at a deleted {@code EXPT_RESOL_MSTR}) is bucketed
 * as Not Requested, matching legacy {@code pymn_0009_001}
 * ({@code LEFT JOIN EXPT_RESOL_MSTR} + {@code ERM.APPR_TYPE_CD IS NULL}).
 */
class ScdulPymnSumrSearchStatusFilterTest {

    private static String queryOf(String methodName) throws NoSuchMethodException {
        Method target = null;
        for (Method m : ScdulPymnSumrRepository.class.getDeclaredMethods()) {
            if (m.getName().equals(methodName)) {
                target = m;
                break;
            }
        }
        assertThat(target).as("ScdulPymnSumrRepository." + methodName + " method").isNotNull();
        Query q = target.getAnnotation(Query.class);
        assertThat(q).as("@Query on " + methodName + "(...)").isNotNull();
        // Collapse whitespace so the assertions are insensitive to formatting.
        return q.value().replaceAll("\\s+", " ");
    }

    private static String searchQuery() throws NoSuchMethodException {
        return queryOf("search");
    }

    @Test
    void notRequested_branch_includes_true_drafts_and_orphan_references() throws Exception {
        String jpql = searchQuery();

        // F-2: the "00" branch must accept null/blank apprSeqNo (true draft)
        // AND a dangling back-stamp (erm.apprTypeCd IS NULL via the LEFT JOIN).
        assertThat(jpql).contains(
                ":apprType = '00' AND (s.apprSeqNo IS NULL OR s.apprSeqNo = '' OR erm.apprTypeCd IS NULL)");
    }

    @Test
    void other_status_branches_remain_unchanged() throws Exception {
        String jpql = searchQuery();

        // All — short-circuit, no filter.
        assertThat(jpql).contains(":apprType IS NULL");
        assertThat(jpql).contains(":apprType = ''");

        // Approved (01): trnf_yn='Y' OR wb_get_appr_stts = 'C'.
        assertThat(jpql).contains(":apprType = '01' AND (s.trnfYn = 'Y'");
        assertThat(jpql).contains("s.apprSeqNo, erm.apprTypeCd) = 'C'");

        // Rejected (02) → 'R', In Progress (03) → 'I', Pending (04) → 'P'.
        assertThat(jpql).contains("s.apprSeqNo, erm.apprTypeCd) = 'R'");
        assertThat(jpql).contains("s.apprSeqNo, erm.apprTypeCd) = 'I'");
        assertThat(jpql).contains("s.apprSeqNo, erm.apprTypeCd) = 'P'");

        // Defensive pass-through for unknown codes.
        assertThat(jpql).contains(":apprType NOT IN ('00','01','02','03','04')");

        // The orphan leg must NOT have leaked into any submitted-status branch.
        assertThat(jpql).doesNotContain(":apprType = '01' AND (s.trnfYn = 'Y' OR erm.apprTypeCd IS NULL");
        assertThat(jpql).doesNotContain(":apprType = '02' AND erm.apprTypeCd IS NULL");
    }

    /**
     * F-1: the footer-totals aggregate ({@code searchCurrencyTotals}) must use
     * the SAME status predicate as {@code search} — including the F-2
     * Not-Requested orphan leg — and must group by currency without
     * pagination, so the footer sums the full filtered set, not a page slice.
     */
    @Test
    void footerTotalsAggregate_sharesSearchPredicate_andGroupsByCurrency() throws Exception {
        String agg = queryOf("searchCurrencyTotals");

        // Same Not-Requested logic as search (F-2 orphan leg present).
        assertThat(agg).contains(
                ":apprType = '00' AND (s.apprSeqNo IS NULL OR s.apprSeqNo = '' OR erm.apprTypeCd IS NULL)");
        // Same submitted-status branches.
        assertThat(agg).contains("s.apprSeqNo, erm.apprTypeCd) = 'C'");
        assertThat(agg).contains("s.apprSeqNo, erm.apprTypeCd) = 'R'");
        assertThat(agg).contains("s.apprSeqNo, erm.apprTypeCd) = 'I'");
        assertThat(agg).contains("s.apprSeqNo, erm.apprTypeCd) = 'P'");
        // Same date / tenant / type / search filters.
        assertThat(agg).contains("s.regDtm BETWEEN :fromDtm AND :toDtm");
        assertThat(agg).contains("s.scdulPymnTypeCd = :scdulPymnTypeCd");

        // Aggregates the full set grouped by currency — SUM + COUNT, GROUP BY ccy.
        assertThat(agg).contains("SUM(COALESCE(s.totalTranAmt, 0))");
        assertThat(agg).contains("COUNT(s)");
        assertThat(agg).contains("SUM(COALESCE(s.totTrnsCnt, 0))");
        assertThat(agg).contains("GROUP BY COALESCE(s.ccyCd, '')");
    }
}
