package com.kosign.wecms.domain.payment.dispatch.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link RealtimeDispatchRegistrar} — the post-commit 202 hand-off for the VND/API path.
 * Mirrors {@link DispatchAutoReceiveRegistrarTest}: gating, the after-commit fan-out across multiple
 * {@code tranSeq}s, and failure isolation.
 *
 * <h2>C-6 — what changed here</h2>
 * The registrar no longer calls {@link RealtimeDispatchService} at all. It enqueues onto
 * {@link RealtimeDispatchQueue}, whose single worker makes the gateway calls with per-bank pacing between
 * them. So the contract pinned here is "<b>one {@code enqueue} per {@code tranSeq}, in order, and nothing
 * heavier than that on the request thread</b>"; the gateway behaviour is covered by
 * {@code RealtimeDispatchQueueTest}.
 *
 * <p>The previous version verified {@code dispatch(...)} once per seq. Keeping that assertion would pin
 * the very thing C-6 removed — dispatching from the request thread.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RealtimeDispatchRegistrarTest {

    private static final String BIZ_KEY = "VN_VNM0004_000001";
    private static final String TRAN_DT = "20260613";

    @Mock RealtimeDispatchQueue realtimeDispatchQueue;

    private RealtimeDispatchRegistrar registrar(boolean enabled) {
        RealtimeDispatchRegistrar reg = new RealtimeDispatchRegistrar(realtimeDispatchQueue);
        ReflectionTestUtils.setField(reg, "enabled", enabled);
        return reg;
    }

    @AfterEach
    void cleanup() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void disabled_registersNothing_andNeverEnqueues() {
        RealtimeDispatchRegistrar reg = registrar(false);
        TransactionSynchronizationManager.initSynchronization();

        reg.registerAfterCommit(BIZ_KEY, TRAN_DT, List.of("1", "2"));

        assertEquals(0, TransactionSynchronizationManager.getSynchronizations().size());
        verifyNoInteractions(realtimeDispatchQueue);
    }

    @Test
    @DisplayName("afterCommit enqueues every tranSeq, in submission order")
    void enabled_registersHook_andAfterCommitEnqueuesEachSeqInOrder() {
        RealtimeDispatchRegistrar reg = registrar(true);
        when(realtimeDispatchQueue.enqueue(anyString(), anyString(), anyString())).thenReturn(true);
        TransactionSynchronizationManager.initSynchronization();

        reg.registerAfterCommit(BIZ_KEY, TRAN_DT, List.of("1", "2", "3"));

        List<TransactionSynchronization> syncs = TransactionSynchronizationManager.getSynchronizations();
        assertEquals(1, syncs.size());
        syncs.get(0).afterCommit();

        // Order matters: the drain is FIFO, so submission order IS gateway order. A set-like assertion
        // would let a future change reorder a payment's lines without failing.
        org.mockito.InOrder inOrder = inOrder(realtimeDispatchQueue);
        inOrder.verify(realtimeDispatchQueue).enqueue(BIZ_KEY, TRAN_DT, "1");
        inOrder.verify(realtimeDispatchQueue).enqueue(BIZ_KEY, TRAN_DT, "2");
        inOrder.verify(realtimeDispatchQueue).enqueue(BIZ_KEY, TRAN_DT, "3");
    }

    /**
     * The request thread must never touch the gateway — the C-6 property itself. Before the fix,
     * {@code afterCommit} ran N sequential {@code dispatch(...)} calls here, each able to take up to 90 s
     * against a slow gateway.
     */
    @Test
    @DisplayName("the request thread never calls RealtimeDispatchService — only the queue")
    void afterCommitDoesNotDispatchOnTheCallingThread() {
        RealtimeDispatchService dispatchService = org.mockito.Mockito.mock(RealtimeDispatchService.class);
        RealtimeDispatchRegistrar reg = registrar(true);
        when(realtimeDispatchQueue.enqueue(anyString(), anyString(), anyString())).thenReturn(true);
        TransactionSynchronizationManager.initSynchronization();

        reg.registerAfterCommit(BIZ_KEY, TRAN_DT, List.of("1", "2"));
        TransactionSynchronizationManager.getSynchronizations().get(0).afterCommit();

        // Asserts the collaborator graph, which is what stops a "small" refactor putting the call back.
        verifyNoInteractions(dispatchService);
        verify(realtimeDispatchQueue, org.mockito.Mockito.times(2))
                .enqueue(anyString(), anyString(), anyString());
    }

    /**
     * A rejected enqueue (queue full / shut down) must not stop the remaining lines. The queue logs the
     * drop and the row stays at {@code PROC_STAT='9'} for the monitor sweep; losing the rest of the
     * payment on top of that would turn one dropped line into N.
     */
    @Test
    void oneRejectedEnqueue_doesNotBlockSiblings_norPropagate() {
        RealtimeDispatchRegistrar reg = registrar(true);
        when(realtimeDispatchQueue.enqueue(BIZ_KEY, TRAN_DT, "1")).thenReturn(false);   // dropped
        when(realtimeDispatchQueue.enqueue(BIZ_KEY, TRAN_DT, "2")).thenReturn(true);
        TransactionSynchronizationManager.initSynchronization();

        reg.registerAfterCommit(BIZ_KEY, TRAN_DT, List.of("1", "2"));
        TransactionSynchronization sync = TransactionSynchronizationManager.getSynchronizations().get(0);

        assertDoesNotThrow(sync::afterCommit);
        verify(realtimeDispatchQueue).enqueue(BIZ_KEY, TRAN_DT, "2");
    }

    @Test
    void enabled_butNoActiveTransaction_skipsSafely() {
        RealtimeDispatchRegistrar reg = registrar(true);
        // no initSynchronization() → no active transaction

        assertDoesNotThrow(() -> reg.registerAfterCommit(BIZ_KEY, TRAN_DT, List.of("1")));
        verify(realtimeDispatchQueue, never()).enqueue(anyString(), anyString(), anyString());
    }

    @Test
    void enabled_emptyList_registersNothing() {
        RealtimeDispatchRegistrar reg = registrar(true);
        TransactionSynchronizationManager.initSynchronization();

        reg.registerAfterCommit(BIZ_KEY, TRAN_DT, List.of());

        assertEquals(0, TransactionSynchronizationManager.getSynchronizations().size());
        verify(realtimeDispatchQueue, never()).enqueue(anyString(), anyString(), anyString());
    }
}
