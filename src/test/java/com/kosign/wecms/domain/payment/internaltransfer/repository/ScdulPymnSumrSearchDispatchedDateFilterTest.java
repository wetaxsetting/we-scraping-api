package com.kosign.wecms.domain.payment.internaltransfer.repository;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the date-range predicate of
 * {@link ScdulPymnSumrRepository#searchDispatched} — the Internal Transfer
 * <b>Transfer Result</b> list query (legacy {@code pymn_0010_001}).
 *
 * <p><b>F-1 regression guard.</b> Legacy
 * {@code PymnHelper.getPymentResult} / {@code getTotPymentResult} /
 * {@code getTotRecPymentResult} all build
 * {@code AND LEFT(sps.trnf_dtm, 8) BETWEEN ? AND ?} — the period filter
 * constrains {@code TRNF_DTM} (transfer / dispatch datetime), not
 * {@code REG_DTM} (batch creation). The legacy IDO
 * {@code SCDUL_PYMN_SUMR_R007} additionally aliases {@code TRNF_DTM AS
 * REG_DTM} on output, which earlier caused the refactor to filter on the
 * genuine {@code REG_DTM} column by mistake. Verified read-only against the
 * JEX DEV DB during the F-1 audit: of 23 dispatched Internal Transfer
 * batches every one has {@code reg_dtm <> trnf_dtm}, 7 differ at the day
 * level, and batch {@code SCDUL_PYMN_NO=275} (reg {@code 2025-12-18}, trnf
 * {@code 2026-01-07}) appears in a January search under the legacy
 * {@code TRNF_DTM} filter but vanished under the buggy {@code REG_DTM} one.
 *
 * <p>Why a reflection / query-text guard rather than {@code @DataJpaTest}:
 * the project intentionally ships no H2 / Testcontainers (see wecms-api
 * CLAUDE.md "Tech Stack"); row-level execution is validated against JEX DEV.
 * This test pins the predicate string so a future edit cannot silently
 * revert the column to {@code REG_DTM}. Sibling guard:
 * {@link ScdulPymnSumrSearchStatusFilterTest}.
 */
class ScdulPymnSumrSearchDispatchedDateFilterTest {

    private static String searchDispatchedQuery() throws NoSuchMethodException {
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
        return q.value().replaceAll("\\s+", " ");
    }

    @Test
    void date_range_constrains_trnf_dtm() throws Exception {
        String jpql = searchDispatchedQuery();
        assertThat(jpql).contains("s.trnfDtm BETWEEN :fromDtm AND :toDtm");
    }

    @Test
    void dispatched_flag_is_preserved() throws Exception {
        String jpql = searchDispatchedQuery();
        assertThat(jpql).contains("s.trnfYn = 'Y'");
    }

    @Test
    void date_range_does_not_constrain_reg_dtm() throws Exception {
        // The bug was `s.regDtm BETWEEN :fromDtm AND :toDtm`. It must not return.
        String jpql = searchDispatchedQuery();
        assertThat(jpql).doesNotContain("s.regDtm BETWEEN");
    }
}
