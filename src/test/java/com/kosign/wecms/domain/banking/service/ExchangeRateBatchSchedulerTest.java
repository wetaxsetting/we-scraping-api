package com.kosign.wecms.domain.banking.service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;

import java.lang.reflect.Method;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Schedule-gate (G0) + cron-correctness tests for {@link ExchangeRateBatchScheduler}.
 *
 * <p>Gate: the scheduler bean (sole carrier of FX {@code @Scheduled} triggers) is
 * created ONLY when {@code wecms.exchange-rate.batch.enabled=true}; off by default
 * so the refactor cannot auto-collect / race the legacy JEX batch.
 *
 * <p>Cron: verified against the confirmed JEX Studio schedule —
 * Shinhan first 1/day @08:30, Shinhan last 11/day 08:30–18:30,
 * Woori 25/day every 30 min 08:30–20:30 (last fire 20:30).
 */
class ExchangeRateBatchSchedulerTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(ExchangeRateBatchService.class, () -> mock(ExchangeRateBatchService.class))
            .withUserConfiguration(ExchangeRateBatchScheduler.class);

    // ── G0 gate ───────────────────────────────────────────────────────────────

    @Test
    void schedulerAbsent_whenPropertyMissing() {
        runner.run(ctx ->
                assertThat(ctx).doesNotHaveBean(ExchangeRateBatchScheduler.class));
    }

    @Test
    void schedulerAbsent_whenPropertyFalse() {
        runner.withPropertyValues("wecms.exchange-rate.batch.enabled=false")
                .run(ctx -> assertThat(ctx).doesNotHaveBean(ExchangeRateBatchScheduler.class));
    }

    @Test
    void schedulerPresent_whenPropertyTrue() {
        runner.withPropertyValues("wecms.exchange-rate.batch.enabled=true")
                .run(ctx -> assertThat(ctx).hasSingleBean(ExchangeRateBatchScheduler.class));
    }

    // ── Cron correctness (confirmed JEX Studio schedule) ──────────────────────

    @Test
    void shinhanFirstTime_runsOncePerDay_at0830() throws Exception {
        DayFires f = fires("shinhanFirstTime");
        assertThat(f.count).isEqualTo(1);
        assertThat(f.first).isEqualTo(LocalDateTime.of(2026, 6, 19, 8, 30));
        assertThat(f.last).isEqualTo(LocalDateTime.of(2026, 6, 19, 8, 30));
    }

    @Test
    void shinhanLastTime_runsElevenTimesPerDay_0830to1830() throws Exception {
        DayFires f = fires("shinhanLastTime");
        assertThat(f.count).isEqualTo(11);                                  // hourly :30, 08..18
        assertThat(f.first).isEqualTo(LocalDateTime.of(2026, 6, 19, 8, 30));
        assertThat(f.last).isEqualTo(LocalDateTime.of(2026, 6, 19, 18, 30));
    }

    @Test
    void woori_runsTwentyFiveTimesPerDay_through2030_notStoppingAt1930() throws Exception {
        // The cron set must include the 09–20 hour range (not 09–19).
        assertThat(cronStrings("woori")).contains("0 0,30 9-20 * * ?");
        assertThat(cronStrings("woori")).doesNotContain("0 0,30 9-19 * * ?");

        DayFires f = fires("woori");
        assertThat(f.count).isEqualTo(25);                                  // every 30 min 08:30–20:30
        assertThat(f.first).isEqualTo(LocalDateTime.of(2026, 6, 19, 8, 30));
        assertThat(f.last).isEqualTo(LocalDateTime.of(2026, 6, 19, 20, 30)); // ← runs through 20:30
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private record DayFires(int count, LocalDateTime first, LocalDateTime last) { }

    private static String[] cronStrings(String method) throws NoSuchMethodException {
        Method m = ExchangeRateBatchScheduler.class.getMethod(method);
        Scheduled[] anns = m.getAnnotationsByType(Scheduled.class);
        String[] crons = new String[anns.length];
        for (int i = 0; i < anns.length; i++) crons[i] = anns[i].cron();
        return crons;
    }

    /** Enumerates every fire of all {@code @Scheduled} crons on the method within
     *  a single calendar day (2026-06-19), summing across the repeated triggers. */
    private static DayFires fires(String method) throws NoSuchMethodException {
        LocalDateTime dayStart = LocalDateTime.of(2026, 6, 19, 0, 0, 0);
        LocalDateTime dayEnd = dayStart.plusDays(1);
        int count = 0;
        LocalDateTime first = null;
        LocalDateTime last = null;
        for (String cron : cronStrings(method)) {
            CronExpression ce = CronExpression.parse(cron);
            LocalDateTime t = ce.next(dayStart.minusSeconds(1));
            while (t != null && t.isBefore(dayEnd)) {
                count++;
                if (first == null || t.isBefore(first)) first = t;
                if (last == null || t.isAfter(last)) last = t;
                t = ce.next(t);
            }
        }
        return new DayFires(count, first, last);
    }
}
