package com.kosign.wecms.domain.banking.nxisas;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Dedicated bounded executor that runs Bank Transaction Sync scrape segments
 * off the request thread (Phase 2). Mirrors the established
 * {@code OpsAlertAsyncConfig} pattern — a small, explicitly-bounded
 * {@link ThreadPoolTaskExecutor}, NOT the common {@code ForkJoinPool}.
 *
 * <p>Each scrape segment is a blocking, I/O-bound unit of work (sequential
 * Coocon gateway calls). One worker thread is held per in-flight scrape
 * segment for its duration, so the pool size caps concurrent scrapes. The
 * queue is bounded; on saturation the task is <b>rejected</b> (AbortPolicy) and
 * {@code BankTransactionSyncService} marks that session FAILED rather than
 * silently queueing unbounded work or running the scrape on the request
 * thread (CallerRuns would defeat the async contract).
 *
 * <p>Not gated by a feature flag — the sync feature itself is the gate. The
 * pool is created lazily on first use by Spring.
 */
@Configuration
public class BankSyncExecutorConfig {

    /** Bean name injected into {@code BankTransactionSyncService}. */
    public static final String EXECUTOR = "bankSyncExecutor";

    @Bean(EXECUTOR)
    public Executor bankSyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("bank-sync-");
        // Reject on saturation — the service catches RejectedExecutionException
        // and marks the session FAILED (a bounded, observable failure) instead
        // of blocking a request thread or growing the queue without limit.
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        // Let in-flight scrape segments finish on shutdown (bounded by the
        // gateway timeouts) so a graceful stop doesn't strand a bank session.
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
