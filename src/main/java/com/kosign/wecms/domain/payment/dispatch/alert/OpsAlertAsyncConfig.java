package com.kosign.wecms.domain.payment.dispatch.alert;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Enables Spring async support and provides the dedicated executor that backs the
 * fire-and-forget {@link OpsAlertService#notify}. This replaces the legacy raw
 * {@code new Thread(...)} in {@code MsgSendUtil.SendMsg} while keeping the same
 * "never block the caller" guarantee.
 *
 * <p>The pool is intentionally small and bounded — ops alerts are low-volume and
 * must never accumulate unbounded work. On saturation the caller-runs policy would
 * push the alert onto the calling thread, so instead we <b>discard</b> (see below):
 * a dropped alert is strictly better than blocking a payment-dispatch thread. The
 * whole subsystem is inert until {@code wecms.dispatch.alert.enabled=true}, so this
 * bean does no work by default.
 */
@Configuration
@EnableAsync
public class OpsAlertAsyncConfig {

    /** Bean name referenced by {@code @Async("opsAlertExecutor")}. */
    public static final String EXECUTOR = "opsAlertExecutor";

    @Bean(EXECUTOR)
    public Executor opsAlertExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("ops-alert-");
        // Never block the dispatch thread; if the small pool + queue are full, drop
        // the alert (ops observability is best-effort, transfers are not).
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.initialize();
        return executor;
    }
}
