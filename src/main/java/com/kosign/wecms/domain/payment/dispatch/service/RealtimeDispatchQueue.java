package com.kosign.wecms.domain.payment.dispatch.service;

import com.kosign.wecms.domain.payment.dispatch.repository.TbPayTargetRepository;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * C-6 · The single pacing point for the real-time (202) rail — a bounded FIFO queue drained by
 * <b>one</b> worker thread, applying the legacy per-bank inter-request delay between rows.
 *
 * <h2>What this replaces, and why it is not an optimisation</h2>
 * {@code RealtimeDispatchRegistrar} used to loop the payment's {@code TRAN_SEQ}s inside its
 * {@code afterCommit} hook and call {@link RealtimeDispatchService#dispatch} for each, sequentially, on
 * the <b>Tomcat request thread</b>. Two consequences, both real:
 * <ul>
 *   <li><b>N unpaced 202s per approval.</b> One approval of an N-line VND payment produced N back-to-back
 *       gateway calls with nothing between them — the burst shape the legacy per-request delay existed to
 *       throttle. {@link DispatchPacer}'s scope note has said so since it was written; this closes it.</li>
 *   <li><b>The user's HTTP request paid for all of them.</b> {@code afterCommit} runs before the response
 *       is returned, so an N-line payment held the request thread for N × (up to
 *       {@code connectTimeoutMs + readTimeoutMs} = 90 s) in the worst case. With the request-thread pool
 *       shared across the whole API, a handful of large payments against a slow gateway was enough to
 *       starve unrelated traffic.</li>
 * </ul>
 * Enqueueing is O(1), so the approval response now returns as soon as the payment commits.
 *
 * <h2>Ordering and pacing</h2>
 * A {@code corePoolSize=maximumPoolSize=1} {@link ThreadPoolExecutor} over an
 * {@link ArrayBlockingQueue}: exactly one thread, strict FIFO, so pacing between consecutive tasks is
 * pacing between consecutive gateway requests. Each task runs
 * {@code dispatch(...)} then — in a {@code finally} — {@code DispatchPacer.pause(outBankCd)} resolved
 * from <b>that row's own</b> bank, never a value carried over from the previous task (the D-A divergence
 * documented on {@link DispatchPacer}).
 *
 * <p>The bank code is re-read from {@code TB_PAY_TARGET} rather than carried on the ticket. It costs one
 * PK lookup on a background thread, and it means the two producers cannot disagree about the pacing key:
 * {@code RealtimeDispatchRegistrar} does not have the bank code in scope, {@code StrandedRecoveryService}
 * does, and a nullable ticket field would have been a second code path through the one place that must
 * stay single-purpose.
 *
 * <h2>Both producers route through here — that is the point</h2>
 * <ul>
 *   <li>{@code RealtimeDispatchRegistrar.registerAfterCommit} — the request-thread rail (the C-6 fix);</li>
 *   <li>{@code StrandedRecoveryService.recoverPayTargets} — the recovery drain, which had its own
 *       {@code pause} call from 2026-07-31 until this class landed. Two pacing points would mean two
 *       threads pacing independently against the same gateway, i.e. no rate limit at all.</li>
 * </ul>
 *
 * <h2>⚠ DEPENDENCY — this drain is only safe while the monitor's {@code TB_PAY_TARGET} '9' sweep is on</h2>
 * <b>The queue is in-memory and is not a persisted outbox.</b> A hard process kill (SIGKILL, OOM, pod
 * eviction) discards every queued ticket. The rows are already durably committed at
 * {@code PROC_STAT='9'}, so nothing is lost or double-sent — but nothing re-drives them either, and the
 * user's request returned successfully minutes ago.
 *
 * <p>The <b>only</b> thing that surfaces those rows is
 * {@code StuckPayFileMonitorService}'s fourth sweep ({@code TB_PAY_TARGET} at {@code PROC_STAT='9'} past
 * {@code stuck-monitor.pay-target-threshold-minutes}, default 5 min), which reports them with a cause and
 * one aggregated ops alert. That sweep is the backstop this design assumes, and it is why moving dispatch
 * off the request thread was deferred until the sweep existed.
 *
 * <p><b>Therefore:</b> do not enable {@code wecms.dispatch.realtime-dispatch=true} in an environment where
 * {@code wecms.dispatch.stuck-monitor.enabled} is false and its cron is unset. Doing so removes the only
 * detection for a dispatch lost on restart. SIT and UAT ship the monitor ON by default; <b>local and prod
 * ship it OFF</b> — so a prod activation must turn the monitor on first, in that order.
 *
 * <h2>Saturation and shutdown</h2>
 * See {@link #enqueue} and {@link #shutdown}. Both fail toward "the row stays at {@code '9'} and the
 * monitor reports it", never toward a lost-but-unreported dispatch or a blocked request thread.
 */
@Slf4j
@Component
public class RealtimeDispatchQueue {

    private final RealtimeDispatchService realtimeDispatchService;
    private final DispatchPacer dispatchPacer;
    private final TbPayTargetRepository payTargetRepository;

    /**
     * Bounded so a runaway producer cannot exhaust the heap. Sized well above any plausible burst: the
     * largest legitimate producer is one payment's line count, and the recovery sweep is capped at
     * {@code recovery.batch-size} (default 50) per pass.
     */
    @Value("${wecms.dispatch.realtime-queue.capacity:1000}")
    private int capacity = 1000;

    /** Bounded shutdown drain — see {@link #shutdown}. */
    @Value("${wecms.dispatch.realtime-queue.shutdown-drain-seconds:30}")
    private long shutdownDrainSeconds = 30;

    private volatile ThreadPoolExecutor worker;
    /**
     * Latched by {@link #shutdown()}. Without it, a {@code shutdown()} that runs before the queue was
     * ever used ({@code worker == null}, so nothing to shut down) would leave a later {@code enqueue}
     * free to create a <b>new</b> worker after the context had closed — accepting work nothing will
     * drain. Cheaper to latch than to reason about bean-destruction ordering.
     */
    private volatile boolean shuttingDown;
    private final AtomicLong enqueued = new AtomicLong();
    private final AtomicLong rejected = new AtomicLong();

    public RealtimeDispatchQueue(RealtimeDispatchService realtimeDispatchService,
                                 DispatchPacer dispatchPacer,
                                 TbPayTargetRepository payTargetRepository) {
        this.realtimeDispatchService = realtimeDispatchService;
        this.dispatchPacer = dispatchPacer;
        this.payTargetRepository = payTargetRepository;
    }

    /**
     * Hand one committed {@code PROC_STAT='9'} pay-target to the drain. Returns immediately.
     *
     * <p><b>On saturation the ticket is dropped, deliberately.</b> The alternatives are worse: a
     * caller-runs policy would put a 90-second gateway call back on the Tomcat request thread — the exact
     * problem this class exists to remove — and a blocking {@code put} would do the same thing more
     * slowly. A dropped ticket leaves the row at {@code '9'}, which the monitor sweep reports and
     * {@code StrandedRecoveryService} can re-drive; a blocked request thread is an outage.
     *
     * @return {@code true} when the ticket was accepted; {@code false} when it was dropped (already
     *         logged at ERROR) or the queue has been shut down
     */
    public boolean enqueue(String bizKey, String tranDt, String tranSeq) {
        ThreadPoolExecutor exec = workerOrNull();
        if (exec == null) {
            log.warn("realtime-dispatch queue is shut down — not enqueueing bizKey={} tranDt={} "
                    + "tranSeq={} (row stays PROC_STAT='9' for the monitor)", bizKey, tranDt, tranSeq);
            return false;
        }
        try {
            exec.execute(() -> drainOne(bizKey, tranDt, tranSeq));
            enqueued.incrementAndGet();
            return true;
        } catch (RejectedExecutionException e) {
            long n = rejected.incrementAndGet();
            log.error("realtime-dispatch queue FULL (capacity {}) — dropped bizKey={} tranDt={} "
                            + "tranSeq={}; the row stays PROC_STAT='9' and the stuck-monitor pay-target "
                            + "sweep will report it. Total dropped since start: {}. If this recurs, the "
                            + "gateway is slower than the inflow — raise "
                            + "wecms.dispatch.realtime-queue.capacity only after checking that",
                    capacity, bizKey, tranDt, tranSeq, n);
            return false;
        }
    }

    /** One row: dispatch, then pace on that row's own bank. Never throws — the worker must survive. */
    private void drainOne(String bizKey, String tranDt, String tranSeq) {
        String outBankCd = null;
        try {
            outBankCd = resolveOutBankCd(bizKey, tranDt, tranSeq);
            realtimeDispatchService.dispatch(bizKey, tranDt, tranSeq);
        } catch (Exception e) {
            // The payment committed long ago and the HTTP response has already been returned, so there is
            // no caller to propagate to. Business failures (cut-off, no-criteria, blocked chars) are
            // recorded as PROC_STAT='3' + an ops alert inside dispatch(); anything that reaches here left
            // the row at '9', where the monitor's pay-target sweep is the surface. See the class Javadoc.
            log.error("realtime-dispatch (202) failed on the drain bizKey={} tranDt={} tranSeq={} — "
                    + "row left for the monitor / recovery", bizKey, tranDt, tranSeq, e);
        } finally {
            // Paced even on failure: a failed dispatch may still have reached the gateway, so the next
            // request must be spaced from it. Null bank falls back to the default delay.
            dispatchPacer.pause(outBankCd);
        }
    }

    /**
     * That row's {@code OUT_BANK_CD}, or null when the row is gone. Null paces at
     * {@code DispatchPacer.PER_DELAY_DEFAULT} — legacy's 100 ms (the 3000 ms default introduced
     * 2026-08-01 was withdrawn 2026-08-05; see that constant's Javadoc for the handover citations).
     * An unknown bank is not a reason to skip pacing; it is paced at the documented default rather
     * than at a bank-specific override we cannot attribute to it.
     */
    private String resolveOutBankCd(String bizKey, String tranDt, String tranSeq) {
        try {
            return payTargetRepository.findByBizKeyAndTranDtAndTranSeq(bizKey, tranDt, tranSeq)
                    .map(t -> t.getOutBankCd())
                    .orElse(null);
        } catch (Exception e) {
            log.warn("realtime-dispatch drain could not resolve OUT_BANK_CD for pacing bizKey={} "
                    + "tranDt={} tranSeq={} — pacing at the default delay", bizKey, tranDt, tranSeq);
            return null;
        }
    }

    /** Lazily create the worker so a disabled dispatch rail costs no thread. */
    private ThreadPoolExecutor workerOrNull() {
        if (shuttingDown) {
            return null;
        }
        ThreadPoolExecutor exec = worker;
        if (exec != null) {
            return exec.isShutdown() ? null : exec;
        }
        synchronized (this) {
            if (shuttingDown) {
                return null;
            }
            if (worker == null) {
                worker = newWorker();
                log.info("realtime-dispatch drain started (single worker, FIFO, capacity {})", capacity);
            }
            return worker.isShutdown() ? null : worker;
        }
    }

    private ThreadPoolExecutor newWorker() {
        ThreadPoolExecutor exec = new ThreadPoolExecutor(
                1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(Math.max(1, capacity)),
                r -> {
                    // DAEMON, deliberately. The tempting choice is non-daemon — "a queued transfer is
                    // worth blocking a JVM exit for" — but it buys nothing and can wedge the process:
                    //
                    //  * Graceful shutdown does not need it. Spring Boot's shutdown hook closes the
                    //    context, which invokes @PreDestroy shutdown() below, which drains within its
                    //    deadline. That path is unaffected by the daemon flag.
                    //  * Any path that MISSES @PreDestroy is exactly where non-daemon hurts: the JVM
                    //    then refuses to exit while this thread lives, turning a missed callback into a
                    //    hung process instead of a clean exit with recoverable '9' rows.
                    //  * A hard kill ignores both flags anyway.
                    //
                    // Daemon therefore loses nothing real and removes a way to hang. (Observed
                    // concretely: with setDaemon(false) a test that did not call shutdown() left the
                    // Gradle test JVM alive holding its output file.)
                    Thread t = new Thread(r, "realtime-dispatch-drain");
                    t.setDaemon(true);
                    return t;
                });
        exec.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        return exec;
    }

    /**
     * Graceful shutdown: stop accepting, drain what is already queued, bounded by
     * {@code realtime-queue.shutdown-drain-seconds} (default 30).
     *
     * <h2>Why bounded, and what the bound costs</h2>
     * An unbounded drain would let shutdown hang for as long as the queue times the worst-case per-row
     * cost — with Woori pacing that is 93 s per row, so a queue of 50 could hold a deployment for over an
     * hour. A bound is therefore required; the consequence is that <b>tickets still queued when the
     * deadline expires are discarded</b>. They are logged with an exact count, and their rows remain at
     * {@code PROC_STAT='9'} for the monitor sweep and {@code StrandedRecoveryService} — the same recovery
     * path as a hard kill, just with a warning attached.
     *
     * <p>The in-flight row is <b>not</b> interrupted before the deadline: {@code shutdown()} lets it
     * finish, so a 202 already at the gateway is never abandoned half-way. After the deadline
     * {@code shutdownNow()} interrupts it, which can only cut the pacing sleep or an idle wait —
     * {@code RestClient} does not respond to interrupts mid-call, so the request either completed or timed
     * out on its own.
     *
     * <p>The worker is a <b>daemon</b> thread (see {@link #newWorker()}), so if this method is somehow
     * never called the JVM still exits cleanly rather than hanging on a queued ticket. That makes the
     * bounded drain the best case and a hard kill the worst, with no third outcome where the process
     * stays alive holding work nobody is watching.
     *
     * <p>A hard kill skips this method entirely; see the class Javadoc's dependency note.
     */
    @PreDestroy
    public void shutdown() {
        shuttingDown = true;   // latch first: no new worker, no new tickets from here on
        ThreadPoolExecutor exec = worker;
        if (exec == null) {
            return;   // never used — nothing to drain
        }
        int queuedAtStart = exec.getQueue().size();
        exec.shutdown();
        log.info("realtime-dispatch drain shutting down — {} ticket(s) queued, draining for up to {}s "
                + "(enqueued={} dropped={} since start)",
                queuedAtStart, shutdownDrainSeconds, enqueued.get(), rejected.get());
        try {
            if (!exec.awaitTermination(Math.max(1, shutdownDrainSeconds), TimeUnit.SECONDS)) {
                int abandoned = exec.shutdownNow().size();
                log.error("realtime-dispatch drain did not finish within {}s — {} queued ticket(s) "
                                + "ABANDONED. Their TB_PAY_TARGET rows are still at PROC_STAT='9' and are "
                                + "recoverable: the stuck-monitor pay-target sweep will report them, and "
                                + "StrandedRecoveryService can re-drive them. No transfer was lost or "
                                + "double-sent — the atomic '9'→'6' claim guarantees that",
                        shutdownDrainSeconds, abandoned);
            } else {
                log.info("realtime-dispatch drain finished cleanly (enqueued={} dropped={})",
                        enqueued.get(), rejected.get());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            int abandoned = exec.shutdownNow().size();
            log.error("realtime-dispatch drain shutdown interrupted — {} queued ticket(s) abandoned; "
                    + "rows remain at PROC_STAT='9' for the monitor", abandoned);
        }
    }

    /** Queued-but-not-started tickets — for tests and diagnostics. */
    int queueDepth() {
        ThreadPoolExecutor exec = worker;
        return exec == null ? 0 : exec.getQueue().size();
    }
}
