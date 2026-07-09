package com.kosign.wecms.domain.payment.internaltransfer.repository;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the <b>C2 status-filter</b> predicate of
 * {@link ScdulPymnSumrRepository#searchDispatched} — the Payment Result list
 * query shared by Internal Transfer / Vendor / Expense / Payroll (legacy
 * {@code pymn_0004_001} et al).
 *
 * <p><b>C2 regression guard.</b> The Result list's Completed / In&nbsp;Progress
 * checkboxes must drive a route-aware dispatch {@code PROC_STAT} filter (legacy
 * {@code PymnHelper.getPymentResult}), NOT the old inert {@code TRNF_YN}-shaped
 * {@code (:trnfYn IS NULL OR s.trnfYn = :trnfYn)} predicate that returned every
 * row for "Completed" and zero rows for "In Progress". The wire param is now
 * {@code :procStts} (legacy {@code 'C'} / {@code 'P'}):
 * <ul>
 *   <li>{@code 'P'} In Progress &rarr; any line {@code PROC_STAT IN ('6','9')}
 *       (file route {@code TB_PAY_FILE_MST}, api route {@code TB_PAY_TARGET}).</li>
 *   <li>{@code 'C'} Completed &rarr; a settled-success line ({@code PROC_STAT =
 *       '0'}) AND no line still processing.</li>
 * </ul>
 *
 * <p>The footer aggregate {@link ScdulPymnSumrRepository#searchDispatchedCurrencyTotals}
 * must stay status-UNfiltered (legacy {@code SCDUL_PYMN_SUMR_R008}) — its query
 * must not reference {@code procStat}.
 *
 * <p>Why a reflection / query-text guard rather than {@code @DataJpaTest}: the
 * project ships no H2 / Testcontainers (wecms-api CLAUDE.md "Tech Stack");
 * row-level execution is validated against JEX DEV. Sibling guards:
 * {@link ScdulPymnSumrSearchDispatchedDateFilterTest},
 * {@link ScdulPymnSumrSearchStatusFilterTest}.
 */
class ScdulPymnSumrSearchDispatchedStatusFilterTest {

    private static Query searchDispatchedQueryAnnotation() {
        Method m = null;
        for (Method candidate : ScdulPymnSumrRepository.class.getDeclaredMethods()) {
            if (candidate.getName().equals("searchDispatched")) {
                m = candidate;
                break;
            }
        }
        assertThat(m).as("ScdulPymnSumrRepository.searchDispatched method").isNotNull();
        Query q = m.getAnnotation(Query.class);
        assertThat(q).as("@Query on searchDispatched(...)").isNotNull();
        return q;
    }

    private static String norm(String s) {
        return s.replaceAll("\\s+", " ");
    }

    @Test
    void status_filter_uses_proc_stts_param_not_inert_trnf_yn() {
        String jpql = norm(searchDispatchedQueryAnnotation().value());
        // The new status param drives the filter…
        assertThat(jpql).contains(":procStts = 'P'");
        assertThat(jpql).contains(":procStts = 'C'");
        // …and the old inert TRNF_YN-onto-TRNF_YN predicate is gone.
        assertThat(jpql).doesNotContain("s.trnfYn = :trnfYn");
    }

    @Test
    void in_progress_matches_proc_stat_6_or_9_on_both_routes() {
        String jpql = norm(searchDispatchedQueryAnnotation().value());
        assertThat(jpql).contains("pm.procStat IN ('6','9')");   // file route (TB_PAY_FILE_MST)
        assertThat(jpql).contains("pt.procStat IN ('6','9')");   // api route (TB_PAY_TARGET)
    }

    @Test
    void completed_requires_a_settled_success_line_and_none_processing() {
        String jpql = norm(searchDispatchedQueryAnnotation().value());
        assertThat(jpql).contains("sm.procStat = '0'");          // file route success
        assertThat(jpql).contains("st.procStat = '0'");          // api route success
        assertThat(jpql).contains("AND NOT (");                  // …and not processing
    }

    @Test
    void dispatched_gate_is_preserved() {
        String jpql = norm(searchDispatchedQueryAnnotation().value());
        assertThat(jpql).contains("s.trnfYn = 'Y'");
    }

    @Test
    void has_explicit_count_query_for_the_exists_subqueries() {
        // The EXISTS subqueries make an explicit countQuery the safe choice over
        // Spring's derived count.
        assertThat(searchDispatchedQueryAnnotation().countQuery())
                .as("explicit countQuery on searchDispatched")
                .contains("SELECT COUNT(s)");
    }

    @Test
    void footer_totals_query_is_NOT_status_filtered() {
        Method m = null;
        for (Method candidate : ScdulPymnSumrRepository.class.getDeclaredMethods()) {
            if (candidate.getName().equals("searchDispatchedCurrencyTotals")) {
                m = candidate;
                break;
            }
        }
        assertThat(m).as("searchDispatchedCurrencyTotals method").isNotNull();
        String jpql = norm(m.getAnnotation(Query.class).value());
        // Legacy SCDUL_PYMN_SUMR_R008 sums all dispatched rows regardless of the
        // Completed / In Progress checkboxes — the footer must not status-filter.
        assertThat(jpql).doesNotContain("procStat");
        assertThat(jpql).contains("GROUP BY COALESCE(s.ccyCd, '')");
    }
}
