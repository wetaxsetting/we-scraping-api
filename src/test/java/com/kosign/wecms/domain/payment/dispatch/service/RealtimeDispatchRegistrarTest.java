package com.kosign.wecms.domain.payment.dispatch.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Tests for {@link RealtimeDispatchRegistrar} — the post-commit 202 hand-off for the
 * VND/API path. Mirrors {@link DispatchAutoReceiveRegistrarTest}: gating, the
 * after-commit fan-out across multiple {@code tranSeq}s, and per-target failure
 * isolation.
 */
@ExtendWith(MockitoExtension.class)
class RealtimeDispatchRegistrarTest {

    private static final String BIZ_KEY = "VN_VNM0004_000001";
    private static final String TRAN_DT = "20260613";

    @Mock
    RealtimeDispatchService realtimeDispatchService;

    private RealtimeDispatchRegistrar registrar(boolean enabled) {
        RealtimeDispatchRegistrar reg = new RealtimeDispatchRegistrar(realtimeDispatchService);
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
    void disabled_registersNothing_andNeverDispatches() {
        RealtimeDispatchRegistrar reg = registrar(false);
        TransactionSynchronizationManager.initSynchronization();

        reg.registerAfterCommit(BIZ_KEY, TRAN_DT, List.of("1", "2"));

        assertEquals(0, TransactionSynchronizationManager.getSynchronizations().size());
        verify(realtimeDispatchService, never()).dispatch(anyString(), anyString(), anyString());
    }

    @Test
    void enabled_registersHook_andAfterCommitDispatchesEachSeq() {
        RealtimeDispatchRegistrar reg = registrar(true);
        TransactionSynchronizationManager.initSynchronization();

        reg.registerAfterCommit(BIZ_KEY, TRAN_DT, List.of("1", "2", "3"));

        List<TransactionSynchronization> syncs = TransactionSynchronizationManager.getSynchronizations();
        assertEquals(1, syncs.size());
        syncs.get(0).afterCommit();

        verify(realtimeDispatchService).dispatch(BIZ_KEY, TRAN_DT, "1");
        verify(realtimeDispatchService).dispatch(BIZ_KEY, TRAN_DT, "2");
        verify(realtimeDispatchService).dispatch(BIZ_KEY, TRAN_DT, "3");
    }

    @Test
    void oneDispatchFailure_doesNotBlockSiblings_norPropagate() {
        RealtimeDispatchRegistrar reg = registrar(true);
        doThrow(new IllegalStateException("TB_FIRM_CONF missing"))
                .when(realtimeDispatchService).dispatch(BIZ_KEY, TRAN_DT, "1");
        TransactionSynchronizationManager.initSynchronization();

        reg.registerAfterCommit(BIZ_KEY, TRAN_DT, List.of("1", "2"));
        TransactionSynchronization sync = TransactionSynchronizationManager.getSynchronizations().get(0);

        // The payment is already committed; a per-target failure must never bubble out,
        // and must not stop the next target from dispatching.
        assertDoesNotThrow(sync::afterCommit);
        verify(realtimeDispatchService).dispatch(BIZ_KEY, TRAN_DT, "1");
        verify(realtimeDispatchService).dispatch(BIZ_KEY, TRAN_DT, "2");
    }

    @Test
    void enabled_butNoActiveTransaction_skipsSafely() {
        RealtimeDispatchRegistrar reg = registrar(true);
        // no initSynchronization() → no active transaction

        assertDoesNotThrow(() -> reg.registerAfterCommit(BIZ_KEY, TRAN_DT, List.of("1")));
        verify(realtimeDispatchService, never()).dispatch(anyString(), anyString(), anyString());
    }

    @Test
    void enabled_emptyList_registersNothing() {
        RealtimeDispatchRegistrar reg = registrar(true);
        TransactionSynchronizationManager.initSynchronization();

        reg.registerAfterCommit(BIZ_KEY, TRAN_DT, List.of());

        assertEquals(0, TransactionSynchronizationManager.getSynchronizations().size());
        verify(realtimeDispatchService, never()).dispatch(anyString(), anyString(), anyString());
    }
}
