package com.kosign.wecms.domain.setting.zalooa.scheduler;

import com.kosign.wecms.common.lock.JobLockNames;
import com.kosign.wecms.domain.setting.zalooa.service.ZaloTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * The only {@code @Scheduled} trigger for the Zalo refresh-token keep-alive sweep.
 *
 * <p><b>What it is for.</b> Zalo's refresh token lasts roughly three months and
 * <b>rotates on every use</b>. A tenant whose Zalo messaging sits idle for a
 * quarter would therefore lose its authorisation outright — and recovery is not
 * automatic: a human has to walk back through the OAuth consent popup. This sweep
 * exercises the rotation on connections that have gone quiet so that never
 * happens.
 *
 * <p>It is <b>not</b> an access-token refresher. Access tokens are refreshed on
 * demand inside {@code ZaloTokenService.accessTokenFor}, where the caller actually
 * needs one. A job that refreshed those on a timer would either run pointlessly or
 * lag behind real traffic.
 *
 * <p><b>Bean exists only when {@code wecms.zalo.enabled=true}</b>
 * ({@code matchIfMissing=false} → off by default), so no cron fires and no vendor
 * call is possible in an environment that has not provisioned Zalo credentials.
 * Mirrors {@code OperationStatusBatchScheduler}.
 *
 * <p><b>Cron defaults to never-fire.</b> {@code "-"} is Spring's never-fire marker.
 * There is no legacy schedule to inherit — this is a refactor-native feature — and
 * the right hour depends on when a deployment prefers to spend vendor calls, so it
 * is set per environment through {@code WECMS_ZALO_TOKEN_REFRESH_CRON} rather than
 * guessed here. Note the repo-wide rule: a <em>blank</em> value throws at scheduler
 * initialisation, so the variable must be present and must be either a valid cron
 * or {@code "-"}.
 *
 * <p>Daily is the natural cadence even though the staleness threshold is 29 days:
 * each pass only touches connections that crossed the threshold, so a daily run
 * catches each one shortly after it goes stale instead of leaving a gap where a
 * refresh token could expire before the next sweep.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "wecms.zalo",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = false)
public class ZaloTokenRefreshScheduler {

    private static final String VIETNAM_TZ_ID = "Asia/Ho_Chi_Minh";

    private final ZaloTokenService tokenService;

    /**
     * One sweep pass.
     *
     * <p>{@code lockAtMostFor} bounds the whole cross-tenant fan-out: one vendor
     * round-trip per stale connection, each with the configured read timeout. The
     * lock guarantees a single instance sweeps at a time — see
     * {@link JobLockNames#ZALO_TOKEN_REFRESH} for why that is a correctness
     * requirement and not just tidiness.
     *
     * <p>Per-connection failures are handled inside the service and do not abort
     * the pass. Anything escaping to here is logged rather than rethrown: letting it
     * propagate would only mark the scheduled task failed without telling anyone
     * more than this line already does.
     */
    @Scheduled(cron = "${wecms.zalo.token-refresh-cron:-}", zone = VIETNAM_TZ_ID)
    @SchedulerLock(name = JobLockNames.ZALO_TOKEN_REFRESH,
                   lockAtMostFor = "PT30M",
                   lockAtLeastFor = "PT1M")
    public void run() {
        try {
            ZaloTokenService.SweepResult result = tokenService.refreshStaleConnections();
            log.info("feature=zalo-oa token refresh sweep pass done {}", result);
        } catch (RuntimeException ex) {
            log.error("feature=zalo-oa token refresh sweep pass failed", ex);
        }
    }
}
