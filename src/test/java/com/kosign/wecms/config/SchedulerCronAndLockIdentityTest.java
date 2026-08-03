package com.kosign.wecms.config;

import com.kosign.wecms.common.lock.JobLockNames;
import com.kosign.wecms.domain.payment.dispatch.scheduler.ResultCheckJob;
import com.kosign.wecms.domain.payment.dispatch.scheduler.StrandedRecoveryJob;
import com.kosign.wecms.domain.payment.dispatch.scheduler.StuckPayFileMonitorJob;
import com.kosign.wecms.domain.payment.haengsung.batch.HaengsungScheduledPaymentImportProperties;
import com.kosign.wecms.domain.payment.haengsung.cardaccount.HaengsungCardAccountSyncProperties;
import com.kosign.wecms.domain.payment.haengsung.exchangerate.HaengsungExchangeRateSyncProperties;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;

import java.lang.reflect.Method;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cron-default parity with the legacy {@code .batch} schedules, and ShedLock
 * identity/bound correctness.
 *
 * <p>The HAENGSUNG assertions exist because the previous defaults were guesses made
 * on a false premise: two property Javadocs claimed the legacy cadence was
 * "Jex-Studio-defined and not in source", when in fact each legacy job ships a
 * committed {@code .batch} file under
 * {@code WECMS DEV/WECMS/web/WEB-INF/jex/simpleBatch/WECMS/}. Card/account sync was
 * running 288 times a day against legacy's 13. These tests pin the corrected values
 * to the legacy files so the regression cannot recur silently.
 */
class SchedulerCronAndLockIdentityTest {

    // ── HAENGSUNG cron defaults must match the legacy .batch schedules ────────

    /**
     * Legacy {@code ERP_0100_001.batch}: {@code schedule=*}{@code /10 7-19 * * *}
     * → every 10 minutes, hours 07–19.
     */
    @Test
    void haengsungExchangeRateSyncCron_matchesLegacyErp0100001() {
        assertThat(HaengsungExchangeRateSyncProperties.DEFAULT_CRON)
                .isEqualTo("0 */10 7-19 * * ?");
        assertValidCron(HaengsungExchangeRateSyncProperties.DEFAULT_CRON);

        List<LocalDateTime> fires = firesOnDay(HaengsungExchangeRateSyncProperties.DEFAULT_CRON);
        assertThat(fires).as("13 hours x 6 fires/hour").hasSize(78);
        assertThat(fires.get(0).getHour()).isEqualTo(7);
        assertThat(fires.get(0).getMinute()).isZero();
        assertThat(fires.get(fires.size() - 1).getHour())
                .as("last fire must be inside hour 19, never 20").isEqualTo(19);
        assertThat(minuteGap(fires)).as("every 10 minutes").isEqualTo(10);
    }

    /**
     * Legacy {@code ERP_0100_002.batch}: {@code schedule=*}{@code /5 7-19 * * *}
     * → every 5 minutes, hours 07–19.
     */
    @Test
    void haengsungScheduledPaymentImportCron_matchesLegacyErp0100002() {
        assertThat(HaengsungScheduledPaymentImportProperties.DEFAULT_CRON)
                .isEqualTo("0 */5 7-19 * * ?");
        assertValidCron(HaengsungScheduledPaymentImportProperties.DEFAULT_CRON);

        List<LocalDateTime> fires = firesOnDay(HaengsungScheduledPaymentImportProperties.DEFAULT_CRON);
        assertThat(fires).as("13 hours x 12 fires/hour").hasSize(156);
        assertThat(fires.get(0).getHour()).isEqualTo(7);
        assertThat(fires.get(fires.size() - 1).getHour()).isEqualTo(19);
        assertThat(minuteGap(fires)).as("every 5 minutes").isEqualTo(5);
    }

    /**
     * Legacy {@code ERP_0100_003.batch}: {@code schedule=0 7-19 * * *}
     * → hourly on the hour, hours 07–19. This is the one that was ~22x too fast.
     */
    @Test
    void haengsungCardAccountSyncCron_matchesLegacyErp0100003() {
        assertThat(HaengsungCardAccountSyncProperties.DEFAULT_CRON)
                .isEqualTo("0 0 7-19 * * ?");
        assertValidCron(HaengsungCardAccountSyncProperties.DEFAULT_CRON);

        List<LocalDateTime> fires = firesOnDay(HaengsungCardAccountSyncProperties.DEFAULT_CRON);
        assertThat(fires).as("hourly across 13 hours").hasSize(13);
        assertThat(fires).allSatisfy(f -> assertThat(f.getMinute()).isZero());
        assertThat(fires.get(0).getHour()).isEqualTo(7);
        assertThat(fires.get(12).getHour()).isEqualTo(19);
    }

    @Test
    void haengsungCronDefaults_areNotThe24HourGuess() {
        // Regression guard for the exact value that was wrong in all three jobs.
        assertThat(List.of(
                HaengsungExchangeRateSyncProperties.DEFAULT_CRON,
                HaengsungScheduledPaymentImportProperties.DEFAULT_CRON,
                HaengsungCardAccountSyncProperties.DEFAULT_CRON))
                .doesNotContain("0 */5 * * * *");
    }

    @Test
    void blankCronFallsBackToTheLegacyDefault() {
        // The compact constructors coerce null/blank -> DEFAULT_CRON, so an empty env var
        // yields the legacy cadence rather than an invalid expression.
        assertThat(new HaengsungCardAccountSyncProperties(false, "  ").cron())
                .isEqualTo(HaengsungCardAccountSyncProperties.DEFAULT_CRON);
        assertThat(new HaengsungExchangeRateSyncProperties(false, null).cron())
                .isEqualTo(HaengsungExchangeRateSyncProperties.DEFAULT_CRON);
        assertThat(new HaengsungScheduledPaymentImportProperties(false, "").cron())
                .isEqualTo(HaengsungScheduledPaymentImportProperties.DEFAULT_CRON);
    }

    // ── Lock identity: result-check body has ONE identity ────────────────────

    @Test
    void resultCheckJob_usesTheSharedResultCheckLockIdentity() throws Exception {
        SchedulerLock lock = ResultCheckJob.class.getDeclaredMethod("run").getAnnotation(SchedulerLock.class);
        assertThat(lock.name())
                .as("must be the shared body identity, so StrandedRecoveryService's programmatic "
                        + "acquisition of the same constant excludes it")
                .isEqualTo(JobLockNames.DISPATCH_RESULT_CHECK);
    }

    @Test
    void resultCheckJob_lockAtMostFor_isWideEnoughForAValidPass() throws Exception {
        SchedulerLock lock = ResultCheckJob.class.getDeclaredMethod("run").getAnnotation(SchedulerLock.class);
        Duration atMostFor = Duration.parse(lock.lockAtMostFor());

        // Derivation (see the method Javadoc): the aged-row set is UNBOUNDED
        // (findByTranTpAndProcStat has no Limit/Pageable), processing is sequential, worst-case
        // per row is EFS connect 3s + read 20s = 23s plus Woori pacing 3s. PT2M covered only
        // ~4 Woori rows before expiring mid-pass and letting a second node start a concurrent
        // pass over live PROC_STAT='6' payment rows.
        assertThat(atMostFor)
                .as("PT2M was too tight for a valid pass — see scheduler-audit F-9")
                .isGreaterThanOrEqualTo(Duration.ofMinutes(30));

        Duration atLeastFor = Duration.parse(lock.lockAtLeastFor());
        assertThat(atLeastFor).isLessThan(atMostFor);
    }

    @Test
    void dispatchJobs_useDistinctLockIdentitiesExceptTheSharedResultCheck() throws Exception {
        String recovery = StrandedRecoveryJob.class.getDeclaredMethod("run")
                .getAnnotation(SchedulerLock.class).name();
        String monitor = StuckPayFileMonitorJob.class.getDeclaredMethod("run")
                .getAnnotation(SchedulerLock.class).name();

        assertThat(recovery).isEqualTo(JobLockNames.DISPATCH_STRANDED_RECOVERY);
        assertThat(monitor).isEqualTo(JobLockNames.DISPATCH_STUCK_PAY_FILE_MONITOR);
        assertThat(recovery)
                .as("the recovery sweep keeps its own identity; only its result-check "
                        + "delegation borrows the result-check identity")
                .isNotEqualTo(JobLockNames.DISPATCH_RESULT_CHECK);
        assertThat(monitor).isNotEqualTo(recovery);
    }

    @Test
    void dispatchJobCrons_defaultToNeverFire() throws Exception {
        for (Method m : List.of(
                ResultCheckJob.class.getDeclaredMethod("run"),
                StrandedRecoveryJob.class.getDeclaredMethod("run"),
                StuckPayFileMonitorJob.class.getDeclaredMethod("run"))) {
            String raw = m.getAnnotation(Scheduled.class).cron();
            String inlineDefault = raw.substring(raw.indexOf(':') + 1, raw.length() - 1);
            assertThat(inlineDefault)
                    .as("%s.%s must never fire by default",
                            m.getDeclaringClass().getSimpleName(), m.getName())
                    .isEqualTo("-");
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static void assertValidCron(String cron) {
        assertThat(CronExpression.isValidExpression(cron))
                .as("%s must be a valid Spring 6-field cron", cron)
                .isTrue();
    }

    /** All fire times a cron produces across one full day. */
    private static List<LocalDateTime> firesOnDay(String cron) {
        CronExpression expr = CronExpression.parse(cron);
        LocalDateTime cursor = LocalDateTime.of(2026, 7, 28, 0, 0);
        LocalDateTime endOfDay = cursor.plusDays(1);
        List<LocalDateTime> fires = new ArrayList<>();
        for (LocalDateTime next = expr.next(cursor);
             next != null && next.isBefore(endOfDay);
             next = expr.next(next)) {
            fires.add(next);
        }
        return fires;
    }

    /** Minute gap between the first two fires (constant for these expressions). */
    private static long minuteGap(List<LocalDateTime> fires) {
        return Duration.between(fires.get(0), fires.get(1)).toMinutes();
    }
}
