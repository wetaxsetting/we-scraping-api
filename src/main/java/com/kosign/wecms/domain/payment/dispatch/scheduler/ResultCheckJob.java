package com.kosign.wecms.domain.payment.dispatch.scheduler;

import com.kosign.wecms.domain.payment.dispatch.service.ResultCheckService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * The single firm-banking scheduled job in the confirmed TOBE — refactor of
 * legacy {@code PROCESSING_RESULT_CHECK_BATCH}. Mirrors the proven
 * {@code banking/service/ExchangeRateBatchService} shape (@Scheduled +
 * @SchedulerLock, ICT zone) and reuses the existing {@code SchedulerLockConfig}
 * + ShedLock {@code shedlock} table.
 *
 * <p><b>DISABLED BY DEFAULT (R&D safety, D4/B7).</b> The cron resolves from
 * {@code wecms.dispatch.result-check.cron} which defaults to {@code "-"} —
 * Spring's "never fire" marker. The wiring (bean, lock, cadence) is therefore
 * registered and verifiable, but the job does not run until an environment
 * explicitly sets a cron. This prevents the refactor from co-processing live
 * {@code PROC_STAT} rows on the shared dev DB alongside the live JEX batch
 * (workspace rule: refactor is local-only R&D, no coexistence).
 *
 * <p>Single transfer (real-time 202) and bulk reception (request-time) /
 * execution (01301-triggered) are intentionally NOT scheduled here.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ResultCheckJob {

    private final ResultCheckService resultCheckService;

    /**
     * Result-check pass. Cron disabled by default ({@code "-"}); when enabled it
     * runs every minute at ICT. ShedLock serialises it across instances.
     */
    @Scheduled(cron = "${wecms.dispatch.result-check.cron:-}", zone = "Asia/Ho_Chi_Minh")
    @SchedulerLock(name = "ResultCheckJob.run", lockAtMostFor = "PT2M", lockAtLeastFor = "PT5S")
    public void run() {
        log.debug("ResultCheckJob.run fired");
        resultCheckService.runOnce();
    }
}
