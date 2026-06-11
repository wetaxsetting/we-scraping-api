package com.kosign.wecms.domain.payment.internaltransfer.repository;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Locks the numeric-ordering contract for {@code SCDUL_PYMN_DETL_NO} introduced
 * in MR-2. After MR-2 the column holds JEX-style <em>unpadded</em> per-tenant
 * integers (e.g. {@code 9999}, {@code 10000}) instead of the old zero-padded
 * {@code 000001}, so the per-line grid must order numerically — a plain string
 * sort would place {@code "10000"} before {@code "9999"}.
 *
 * <p>{@link ScdulPymnSumrDetlRepository#DETL_NO_NUMERIC_ASC} encodes that as
 * {@code ORDER BY length(scdulPymnDetlNo), scdulPymnDetlNo}. These tests pin the
 * constant's shape and the ordering rule it generates. (The end-to-end SQL
 * ordering is exercised by the DB-backed integration test recommended in the
 * MR — this project has no H2/Testcontainers harness for a unit-level DB test.)
 */
class ScdulPymnSumrDetlSortTest {

    @Test
    void detlNoNumericAsc_isLengthThenValueAscending() {
        List<Sort.Order> orders = ScdulPymnSumrDetlRepository.DETL_NO_NUMERIC_ASC.stream().toList();
        assertThat(orders).hasSize(2);
        assertThat(orders.get(0).getProperty()).isEqualTo("length(scdulPymnDetlNo)");
        assertThat(orders.get(0).getDirection()).isEqualTo(Sort.Direction.ASC);
        assertThat(orders.get(1).getProperty()).isEqualTo("scdulPymnDetlNo");
        assertThat(orders.get(1).getDirection()).isEqualTo(Sort.Direction.ASC);
    }

    @Test
    void lengthThenValueRule_ordersNumericallyAcrossDigitBoundary() {
        // Mirrors the SQL the constant generates — ORDER BY length(x), x — which
        // orders non-negative integer strings numerically. A naive string sort
        // would place "10000" before "9999"; this rule does not.
        Comparator<String> rule = Comparator
                .comparingInt(String::length)
                .thenComparing(Comparator.naturalOrder());
        List<String> values = new ArrayList<>(List.of("10000", "9999", "100", "2", "10"));
        values.sort(rule);
        assertThat(values).containsExactly("2", "10", "100", "9999", "10000");
    }
}
