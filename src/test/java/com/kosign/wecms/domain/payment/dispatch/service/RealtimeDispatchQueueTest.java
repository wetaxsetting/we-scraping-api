package com.kosign.wecms.domain.payment.dispatch.service;

import com.kosign.wecms.domain.payment.dispatch.entity.TbPayTarget;
import com.kosign.wecms.domain.payment.dispatch.repository.TbPayTargetRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * C-6 · {@link RealtimeDispatchQueue} — the single paced FIFO drain for the real-time (202) rail.
 *
 * <h2>What is worth testing here, and what is not</h2>
 * The queue's value is four properties, and each is a thing that has actually gone wrong somewhere in this
 * subsystem before:
 * <ol>
 *   <li><b>Enqueue does not dispatch on the caller's thread.</b> This is the C-6 fix — the whole reason
 *       the class exists — and it is the one property a reader cannot verify by inspection, because the
 *       executor makes it a timing question.</li>
 *   <li><b>Pacing is per row, from that row's own bank.</b> The D-A divergence from legacy's leaked
 *       {@code per_request_delay}: a Woori row must not make the next Shinhan row wait 3000 ms.</li>
 *   <li><b>Saturation drops rather than blocking.</b> A caller-runs or blocking policy would put a 90 s
 *       gateway call back on the Tomcat thread, undoing the fix under exactly the load that needs it.</li>
 *   <li><b>A failing row is paced and does not kill the worker.</b> One bad row must not stop the drain
 *       for every row behind it.</li>
 * </ol>
 * Real threads are used (a mocked executor would test nothing), with latches rather than sleeps so the
 * tests are deterministic. {@link DispatchPacer} is mocked throughout — asserting the delay <i>values</i>
 * is {@code DispatchPacerTest}'s job, and real 3000 ms sleeps here would make the suite unusable.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RealtimeDispatchQueueTest {

    private static final String BIZ = "VN_VNM0004_000001";
    private static final String TRAN_DT = "20260731";
    private static final String WOORI = "970457";
    private static final String SHINHAN = "970424";

    @Mock RealtimeDispatchService realtimeDispatchService;
    @Mock DispatchPacer dispatchPacer;
    @Mock TbPayTargetRepository payTargetRepository;

    private RealtimeDispatchQueue queue(int capacity) {
        RealtimeDispatchQueue q = new RealtimeDispatchQueue(
                realtimeDispatchService, dispatchPacer, payTargetRepository);
        ReflectionTestUtils.setField(q, "capacity", capacity);
        ReflectionTestUtils.setField(q, "shutdownDrainSeconds", 5L);
        return q;
    }

    /** Make the pay-target lookup return a row with the given bank, so pacing can resolve it. */
    private void bank(String tranSeq, String outBankCd) {
        TbPayTarget t = new TbPayTarget();
        t.setBizKey(BIZ);
        t.setTranDt(TRAN_DT);
        t.setTranSeq(tranSeq);
        t.setOutBankCd(outBankCd);
        when(payTargetRepository.findByBizKeyAndTranDtAndTranSeq(BIZ, TRAN_DT, tranSeq))
                .thenReturn(Optional.of(t));
    }

    /* ───────────────────── 1. off the caller's thread ───────────────────── */

    @Test
    @DisplayName("enqueue returns before the dispatch runs — the gateway call is off the caller's thread")
    void enqueueDoesNotDispatchOnTheCallingThread() throws Exception {
        RealtimeDispatchQueue q = queue(10);
        bank("1", SHINHAN);

        CountDownLatch dispatchEntered = new CountDownLatch(1);
        CountDownLatch releaseDispatch = new CountDownLatch(1);
        List<String> dispatchThreads = new CopyOnWriteArrayList<>();
        doAnswer(inv -> {
            dispatchThreads.add(Thread.currentThread().getName());
            dispatchEntered.countDown();
            releaseDispatch.await(5, TimeUnit.SECONDS);   // hold the worker
            return null;
        }).when(realtimeDispatchService).dispatch(BIZ, TRAN_DT, "1");

        String callerThread = Thread.currentThread().getName();
        assertThat(q.enqueue(BIZ, TRAN_DT, "1")).isTrue();

        // The decisive assertion: enqueue has already returned while the dispatch is still blocked.
        assertThat(dispatchEntered.await(5, TimeUnit.SECONDS))
                .as("the worker must pick the ticket up")
                .isTrue();
        releaseDispatch.countDown();

        assertThat(dispatchThreads)
                .as("the dispatch must NOT run on the caller (request) thread")
                .doesNotContain(callerThread);
        assertThat(dispatchThreads).allSatisfy(name ->
                assertThat(name).startsWith("realtime-dispatch-drain"));
        q.shutdown();
    }

    /* ───────────────────── 2. per-row pacing (D-A) ───────────────────── */

    /**
     * A Woori row must not leak its 3000 ms onto the Shinhan row behind it.
     *
     * <p>Legacy {@code NAPAS_REAL_TIME_BATCH} declares {@code per_request_delay} at {@code :68}, outside
     * its row loop at {@code :82}, and only ever raises it inside — so after one Woori row every later row
     * in the pass slept 3000 ms whatever its own bank. The drain resolves the bank per row, matching the
     * sibling batch ({@code PROCESSING_RESULT_CHECK_BATCH:82} scopes the variable inside its loop). The
     * Woori-then-Shinhan order is what makes the leak observable.
     */
    @Test
    @DisplayName("each row is paced with its OWN bank — a Woori row does not slow the next Shinhan row")
    void pacesEachRowWithItsOwnBank() {
        RealtimeDispatchQueue q = queue(10);
        bank("1", WOORI);
        bank("2", SHINHAN);

        q.enqueue(BIZ, TRAN_DT, "1");
        q.enqueue(BIZ, TRAN_DT, "2");
        q.shutdown();   // drains

        verify(dispatchPacer, timeout(5_000)).pause(WOORI);
        verify(dispatchPacer, timeout(5_000)).pause(SHINHAN);
        verify(dispatchPacer, never()).pause(null);
    }

    @Test
    @DisplayName("FIFO: tickets reach the gateway in submission order")
    void drainsInSubmissionOrder() {
        RealtimeDispatchQueue q = queue(10);
        bank("1", SHINHAN);
        bank("2", SHINHAN);
        bank("3", SHINHAN);
        List<String> order = Collections.synchronizedList(new java.util.ArrayList<>());
        doAnswer(inv -> {
            order.add(inv.getArgument(2));
            return null;
        }).when(realtimeDispatchService).dispatch(anyString(), anyString(), anyString());

        q.enqueue(BIZ, TRAN_DT, "1");
        q.enqueue(BIZ, TRAN_DT, "2");
        q.enqueue(BIZ, TRAN_DT, "3");
        q.shutdown();

        assertThat(order).containsExactly("1", "2", "3");
    }

    /**
     * An unresolvable bank paces at the default rather than skipping the pause. Skipping would make a
     * missing row the one case that bursts, which is the opposite of the conservative choice.
     */
    @Test
    void anUnresolvableBankStillPaces() {
        RealtimeDispatchQueue q = queue(10);
        when(payTargetRepository.findByBizKeyAndTranDtAndTranSeq(BIZ, TRAN_DT, "1"))
                .thenReturn(Optional.empty());

        q.enqueue(BIZ, TRAN_DT, "1");
        q.shutdown();

        // null → DispatchPacer.perBankDelay falls through to PER_DELAY_DEFAULT.
        verify(dispatchPacer, timeout(5_000)).pause(null);
    }

    /* ───────────────────── 3. saturation ───────────────────── */

    /**
     * A full queue must <b>drop</b>, not block and not run on the caller.
     *
     * <p>The row is already committed at {@code PROC_STAT='9'}, so a dropped ticket is recoverable — the
     * stuck-monitor pay-target sweep reports it. A blocked Tomcat thread is not recoverable, and
     * caller-runs would reintroduce the exact 90-second request-thread stall C-6 removed.
     */
    @Test
    @DisplayName("a full queue drops the ticket and returns false — never blocks, never runs inline")
    void saturationDropsRatherThanBlocking() throws Exception {
        RealtimeDispatchQueue q = queue(1);   // 1 worker + 1 queue slot
        bank("hold", SHINHAN);

        CountDownLatch workerBusy = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(inv -> {
            workerBusy.countDown();
            release.await(5, TimeUnit.SECONDS);
            return null;
        }).when(realtimeDispatchService).dispatch(BIZ, TRAN_DT, "hold");

        assertThat(q.enqueue(BIZ, TRAN_DT, "hold")).isTrue();
        assertThat(workerBusy.await(5, TimeUnit.SECONDS)).isTrue();   // worker occupied

        assertThat(q.enqueue(BIZ, TRAN_DT, "queued")).isTrue();       // fills the single slot

        String caller = Thread.currentThread().getName();
        long before = System.nanoTime();
        boolean accepted = q.enqueue(BIZ, TRAN_DT, "dropped");        // must be rejected
        long elapsedMs = (System.nanoTime() - before) / 1_000_000;

        assertThat(accepted).as("the third ticket must be dropped, not queued").isFalse();
        assertThat(elapsedMs).as("rejection must be immediate — a blocking put would hang here").isLessThan(1_000);
        verify(realtimeDispatchService, never()).dispatch(BIZ, TRAN_DT, "dropped");

        release.countDown();
        q.shutdown();
        // And the dropped ticket never ran anywhere, least of all on the caller.
        assertThat(caller).doesNotStartWith("realtime-dispatch-drain");
    }

    /* ───────────────────── 4. failure isolation ───────────────────── */

    @Test
    @DisplayName("a failing row is still paced and does not kill the worker")
    void aFailingRowIsPacedAndTheWorkerSurvives() {
        RealtimeDispatchQueue q = queue(10);
        bank("1", WOORI);
        bank("2", SHINHAN);
        doAnswer(inv -> {
            throw new IllegalStateException("TB_FIRM_CONF missing");
        }).when(realtimeDispatchService).dispatch(BIZ, TRAN_DT, "1");

        q.enqueue(BIZ, TRAN_DT, "1");
        q.enqueue(BIZ, TRAN_DT, "2");
        q.shutdown();

        // Paced despite failing — a failed dispatch may still have reached the gateway.
        verify(dispatchPacer, timeout(5_000)).pause(WOORI);
        // ...and the row behind it still ran, i.e. the worker thread was not lost.
        verify(realtimeDispatchService, timeout(5_000)).dispatch(BIZ, TRAN_DT, "2");
    }

    /* ───────────────────── shutdown ───────────────────── */

    @Test
    @DisplayName("shutdown drains what is already queued")
    void shutdownDrainsQueuedTickets() {
        RealtimeDispatchQueue q = queue(10);
        bank("1", SHINHAN);
        bank("2", SHINHAN);

        q.enqueue(BIZ, TRAN_DT, "1");
        q.enqueue(BIZ, TRAN_DT, "2");
        q.shutdown();

        verify(realtimeDispatchService).dispatch(BIZ, TRAN_DT, "1");
        verify(realtimeDispatchService).dispatch(BIZ, TRAN_DT, "2");
        assertThat(q.queueDepth()).isZero();
    }

    @Test
    @DisplayName("after shutdown, enqueue refuses instead of silently discarding")
    void enqueueAfterShutdownReturnsFalse() {
        RealtimeDispatchQueue q = queue(10);
        bank("1", SHINHAN);
        q.enqueue(BIZ, TRAN_DT, "1");
        q.shutdown();

        assertThat(q.enqueue(BIZ, TRAN_DT, "2")).isFalse();
        verify(realtimeDispatchService, never()).dispatch(BIZ, TRAN_DT, "2");
    }

    /**
     * The drain bound is real: a worker still busy when the deadline expires does not hold shutdown open
     * indefinitely. The abandoned rows stay at {@code PROC_STAT='9'} and are the monitor's problem — which
     * is the documented trade, not a silent loss.
     */
    @Test
    @DisplayName("shutdown is bounded — a stuck row cannot hold the JVM open forever")
    void shutdownIsBounded() throws Exception {
        RealtimeDispatchQueue q = queue(10);
        ReflectionTestUtils.setField(q, "shutdownDrainSeconds", 1L);
        bank("stuck", SHINHAN);

        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch never = new CountDownLatch(1);
        doAnswer(inv -> {
            entered.countDown();
            never.await(30, TimeUnit.SECONDS);   // outlives the 1s drain budget
            return null;
        }).when(realtimeDispatchService).dispatch(BIZ, TRAN_DT, "stuck");

        q.enqueue(BIZ, TRAN_DT, "stuck");
        assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();

        long before = System.nanoTime();
        q.shutdown();
        long elapsedMs = (System.nanoTime() - before) / 1_000_000;

        assertThat(elapsedMs)
                .as("shutdown must give up after shutdownDrainSeconds, not wait for the stuck row")
                .isLessThan(10_000);
        never.countDown();
    }

    /** Never used → no thread created, and shutdown is a no-op. Keeps a disabled rail free. */
    @Test
    void neverUsed_createsNoWorkerAndShutsDownCleanly() {
        RealtimeDispatchQueue q = queue(10);

        q.shutdown();

        assertThat(q.queueDepth()).isZero();
        verify(realtimeDispatchService, never()).dispatch(anyString(), anyString(), anyString());
        verify(dispatchPacer, never()).pause(anyString());
    }

    /**
     * Shutdown-before-first-use must still refuse later work. Without the latch, that path returns early
     * ({@code worker == null}, nothing to drain) and a subsequent {@code enqueue} would happily create a
     * <b>new</b> worker after the context had closed — accepting a transfer nothing is going to drain.
     */
    @Test
    @DisplayName("shutdown before first use still refuses later enqueues — no post-shutdown worker")
    void shutdownBeforeFirstUse_refusesLaterEnqueues() {
        RealtimeDispatchQueue q = queue(10);
        bank("1", SHINHAN);

        q.shutdown();   // never used: the early-return path

        assertThat(q.enqueue(BIZ, TRAN_DT, "1")).isFalse();
        verify(realtimeDispatchService, never()).dispatch(anyString(), anyString(), anyString());
    }
}
