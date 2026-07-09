package com.kosign.wecms.domain.operation.scheduler;

import com.kosign.wecms.domain.operation.service.OperationStatusBatchService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Schedule-gate + cron-placeholder tests for {@link OperationStatusBatchScheduler}.
 *
 * <p>Gate: the scheduler bean (sole carrier of the Operation-Status batch
 * {@code @Scheduled} triggers) is created ONLY when
 * {@code wecms.operation-status.batch.enabled=true}; OFF by default so the refactor
 * cannot auto-write / race the legacy JEX batch on the shared DB.
 *
 * <p>Cron: the schedule is intentionally a PLACEHOLDER (real cadence lives in the
 * JEX batch-admin tables, audit OQ-1). The test only asserts that the externalised
 * {@code @Scheduled} placeholder defaults are syntactically valid Spring crons — it
 * does NOT pin a fire schedule, because the placeholder is provisional.
 */
class OperationStatusBatchSchedulerTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(OperationStatusBatchService.class, () -> mock(OperationStatusBatchService.class))
            .withUserConfiguration(OperationStatusBatchScheduler.class);

    // ── Gate (OFF by default) ──────────────────────────────────────────────────

    @Test
    void schedulerAbsent_whenPropertyMissing() {
        runner.run(ctx ->
                assertThat(ctx).doesNotHaveBean(OperationStatusBatchScheduler.class));
    }

    @Test
    void schedulerAbsent_whenPropertyFalse() {
        runner.withPropertyValues("wecms.operation-status.batch.enabled=false")
                .run(ctx -> assertThat(ctx).doesNotHaveBean(OperationStatusBatchScheduler.class));
    }

    @Test
    void schedulerPresent_whenPropertyTrue() {
        runner.withPropertyValues("wecms.operation-status.batch.enabled=true")
                .run(ctx -> assertThat(ctx).hasSingleBean(OperationStatusBatchScheduler.class));
    }

    // ── Cron placeholder defaults are valid Spring crons ───────────────────────

    @Test
    void seedCronPlaceholderDefault_isValid() throws Exception {
        assertThat(CronExpression.isValidExpression(defaultCron("seed"))).isTrue();
    }

    @Test
    void refreshCronPlaceholderDefault_isValid() throws Exception {
        assertThat(CronExpression.isValidExpression(defaultCron("refresh"))).isTrue();
    }

    /**
     * Extract the inline default from the {@code @Scheduled(cron = "${prop:default}")}
     * placeholder on the named method, so we can validate the provisional cron without
     * a running Spring context to resolve the property.
     */
    private static String defaultCron(String methodName) throws Exception {
        Method m = OperationStatusBatchScheduler.class.getDeclaredMethod(methodName);
        String raw = m.getAnnotation(Scheduled.class).cron();   // "${wecms...:0 0 2 * * ?}"
        int colon = raw.indexOf(':');
        return raw.substring(colon + 1, raw.length() - 1);       // strip "${...:" and trailing "}"
    }
}
