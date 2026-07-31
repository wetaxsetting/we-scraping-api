package com.kosign.wecms.domain.operation.scheduler;

import com.kosign.wecms.common.lock.JobLockNames;
import com.kosign.wecms.domain.operation.service.OperationStatusBatchService;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.annotation.Scheduled;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Schedule-gate, ordering and cron-default tests for {@link OperationStatusBatchScheduler}.
 *
 * <p>Gate: the scheduler bean (sole carrier of the Operation-Status batch
 * {@code @Scheduled} trigger) is created ONLY when
 * {@code wecms.operation-status.batch.enabled=true}; OFF by default so the refactor
 * cannot auto-write / race the legacy JEX batch on the shared DB.
 *
 * <p>Ordering: seed and refresh used to be two {@code @Scheduled} methods with two
 * independent locks and two crons defaulting to the same instant — so refresh could
 * run first and silently drop a day of new tax codes (seed is create-only, refresh
 * is update-only). They are now one method under one lock, which these tests pin.
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

    // ── Single trigger: seed and refresh can no longer fire independently ──────

    @Test
    void exactlyOneScheduledMethod_soSeedAndRefreshCannotCollide() {
        long scheduled = Arrays.stream(OperationStatusBatchScheduler.class.getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(Scheduled.class))
                .count();
        assertThat(scheduled)
                .as("seed+refresh must share ONE @Scheduled method so they cannot fire at the same instant")
                .isEqualTo(1);
    }

    @Test
    void theSingleScheduledMethod_usesTheSharedOperationStatusLock() throws Exception {
        Method m = OperationStatusBatchScheduler.class.getDeclaredMethod("runSeedThenRefresh");
        assertThat(m.getAnnotation(SchedulerLock.class).name())
                .isEqualTo(JobLockNames.OPRT_STATUS_BATCH);
    }

    @Test
    void noLegacySeedOrRefreshScheduledMethodsRemain() {
        assertThat(Arrays.stream(OperationStatusBatchScheduler.class.getDeclaredMethods())
                .map(Method::getName))
                .as("the separately-scheduled seed/refresh methods must be gone")
                .doesNotContain("seed", "refresh");
    }

    // ── Deterministic order: seed BEFORE refresh ──────────────────────────────

    @Test
    void runsSeedBeforeRefresh() {
        OperationStatusBatchService svc = mock(OperationStatusBatchService.class);
        new OperationStatusBatchScheduler(svc).runSeedThenRefresh();

        InOrder order = inOrder(svc);
        order.verify(svc).seedAllTenants();
        order.verify(svc).refreshAllTenants();
        order.verifyNoMoreInteractions();
    }

    @Test
    void seedFailure_stillRunsRefresh() {
        OperationStatusBatchService svc = mock(OperationStatusBatchService.class);
        doThrow(new IllegalStateException("seed boom")).when(svc).seedAllTenants();

        // A seed failure must not suppress refresh — skipping refresh because one tenant
        // failed during seed would lose a day of status updates for every healthy tenant.
        new OperationStatusBatchScheduler(svc).runSeedThenRefresh();

        verify(svc).refreshAllTenants();
    }

    @Test
    void refreshFailure_isContained() {
        OperationStatusBatchService svc = mock(OperationStatusBatchService.class);
        doThrow(new IllegalStateException("refresh boom")).when(svc).refreshAllTenants();

        // The scheduler must never propagate to the scheduler thread.
        new OperationStatusBatchScheduler(svc).runSeedThenRefresh();

        verify(svc).seedAllTenants();
    }

    // ── Cron default is never-fire (the legacy instant is unresolved, OQ-3) ────

    @Test
    void cronDefault_isSpringNeverFireMarker() throws Exception {
        Method m = OperationStatusBatchScheduler.class.getDeclaredMethod("runSeedThenRefresh");
        String raw = m.getAnnotation(Scheduled.class).cron();   // "${wecms...cron:-}"

        assertThat(raw).isEqualTo("${wecms.operation-status.batch.cron:-}");

        // Inline default must be "-": the real legacy clock time is UNRESOLVED (OQ-3), so the
        // job must not fire on a guessed schedule even if someone flips `enabled=true`.
        assertThat(raw.substring(raw.indexOf(':') + 1, raw.length() - 1)).isEqualTo("-");
    }
}
