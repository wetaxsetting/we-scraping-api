package com.kosign.wecms.domain.payment.dispatch.scheduler;

import com.kosign.wecms.domain.payment.dispatch.service.StrandedRecoveryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled driver for the R-1 stranded-record recovery safety net (Item 4). Mirrors the
 * {@link ResultCheckJob} shape (@Scheduled + @SchedulerLock, ICT zone, shared ShedLock table).
 *
 * <p><b>DISABLED BY DEFAULT (coexistence safety).</b> The cron resolves from
 * {@code wecms.dispatch.recovery.cron} which defaults to {@code "-"} (Spring's "never fire"
 * marker) — and even if a cron is set, {@link StrandedRecoveryService#recoverOnce} is inert until
 * {@code wecms.dispatch.recovery.enabled=true}. Both gates MUST be opened together, and only after
 * the legacy-JEX coexistence rule is resolved: refactor-originated rows cannot be distinguished from
 * JEX-originated rows on a shared DB, so a blind sweep could double-process JEX rows. Keeping the
 * cron off is the safe default the PO/Mr. Kang decision gates.
 *
 * <p>{@link SchedulerLock} serialises the sweep across instances so two nodes never recover the same
 * row concurrently (complementing the per-row atomic {@code '9'→'6'} claim inside the dispatch path).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StrandedRecoveryJob {

    private final StrandedRecoveryService strandedRecoveryService;

    @Scheduled(cron = "${wecms.dispatch.recovery.cron:-}", zone = "Asia/Ho_Chi_Minh")
    @SchedulerLock(name = "StrandedRecoveryJob.run", lockAtMostFor = "PT10M", lockAtLeastFor = "PT10S")
    public void run() {
        log.debug("StrandedRecoveryJob.run fired");
        strandedRecoveryService.recoverOnce();
    }
}
