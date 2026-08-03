package com.kosign.wecms.domain.payment.dispatch.entity;

import jakarta.persistence.Column;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the resolution of the "null schedule times" legacy-parity question (Decision A).
 *
 * <h2>The question</h2>
 * {@link com.kosign.wecms.domain.payment.dispatch.service.OperatingHoursService#exeDiv} null-guards
 * both window bounds and falls through to {@code Y} (→ {@code DISPATCH}). Legacy, by contrast,
 * tests {@code BATCH_START_TM == null} <em>before</em> reading {@code EXE_DIV} and fails the row:
 *
 * <pre>
 * // NAPAS_REAL_TIME_BATCH.java:125-130
 * if (idoOutBSIR001.get("BATCH_START_TM") == null) {   // "대상 은행 존재여부 확인"
 *     exeErrYN = true; exeYN = false;                  // → TB_PAY_TARGET.PROC_STAT = '3' + alert
 * }
 * </pre>
 *
 * Read naively that is a divergence: legacy fails closed, the refactor dispatches.
 *
 * <h2>The resolution — the case is unreachable, so there is no divergence</h2>
 * Both bound columns are {@code NOT NULL} in the legacy JEX DDL, carried verbatim into the
 * refactor baseline ({@code V96__tb_batch_schedule_info.sql} and
 * {@code V95__tb_batch_schedule_dtl.sql}: {@code batch_start_tm bpchar(6) NOT NULL},
 * {@code batch_end_tm bpchar(6) NOT NULL}). A row that exists therefore <b>cannot</b> carry a
 * null bound.
 *
 * <p>Legacy's null-check is consequently a <b>row-absence test</b>, not a null-value test: the
 * JEX IDO layer returns a {@code JexData} whose columns read null when zero rows matched, and
 * legacy has no other way to distinguish "no schedule configured" from "schedule configured".
 * The refactor expresses the same thing with {@code Optional.empty() → NO_CRITERIA}, which is
 * exactly legacy's fail-closed outcome. The null-guards inside {@code exeDiv} are unreachable
 * defensive code, not a behavioural difference.
 *
 * <p>(Note the SQL agrees and is irrelevant: {@code CASE WHEN BATCH_START_TM > … } would yield
 * {@code EXE_DIV='Y'} for a null bound because {@code NULL > x} is {@code NULL}. Legacy never
 * reaches that branch because the Java null-check short-circuits first.)
 *
 * <h2>What this test protects</h2>
 * The equivalence holds <b>only</b> while the columns stay {@code NOT NULL}. If someone relaxes
 * the entity mapping (or a future DDL change makes the column nullable), the unreachable branch
 * becomes reachable and the refactor would silently start dispatching payments that legacy
 * failed — with no other test objecting. This asserts the precondition the analysis rests on,
 * and points whoever breaks it at that analysis.
 *
 * <p>Asserted against the entity mapping rather than the live DB because the DDL is external
 * ({@code ddl-auto: validate}, Flyway disabled) and no test-time database exists; Hibernate's
 * own {@code validate} pass is what ties the mapping back to the real column at boot.
 */
class BatchScheduleNotNullContractTest {

    @DisplayName("schedule window bounds are non-nullable, so a present row can never have a null bound")
    @ParameterizedTest(name = "{0}#{1}")
    @CsvSource({
            "TbBatchScheduleInfo, batchStartTm, BATCH_START_TM",
            "TbBatchScheduleInfo, batchEndTm,   BATCH_END_TM",
            "TbBatchScheduleDtl,  batchStartTm, BATCH_START_TM",
            "TbBatchScheduleDtl,  batchEndTm,   BATCH_END_TM",
    })
    void windowBoundsAreNotNull(String simpleName, String fieldName, String columnName) throws Exception {
        Class<?> entity = Class.forName(
                "com.kosign.wecms.domain.payment.dispatch.entity." + simpleName.trim());
        Field field = entity.getDeclaredField(fieldName.trim());
        Column column = field.getAnnotation(Column.class);

        assertThat(column)
                .as("%s.%s must map an explicit @Column", simpleName, fieldName)
                .isNotNull();
        assertThat(column.name())
                .as("%s.%s must map the legacy column name", simpleName, fieldName)
                .isEqualTo(columnName.trim());

        assertThat(column.nullable())
                .as("%s.%s (%s) must stay nullable=false. The legacy JEX DDL declares it NOT NULL "
                                + "(V95/V96), which is why OperatingHoursService.exeDiv's null-guard is "
                                + "unreachable and why Optional.empty()->NO_CRITERIA is equivalent to "
                                + "legacy's BATCH_START_TM==null fail-closed branch. Relaxing this makes "
                                + "the refactor DISPATCH where legacy FAILED — see the class Javadoc and "
                                + "docs/testing/integration-test-audit.md before changing it.",
                        simpleName, fieldName, columnName)
                .isFalse();
    }
}
