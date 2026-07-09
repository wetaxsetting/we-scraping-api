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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Tests for {@link DispatchAutoReceiveRegistrar} — the shared post-commit 00301
 * hand-off used by every File-path dispatch executor.
 */
@ExtendWith(MockitoExtension.class)
class DispatchAutoReceiveRegistrarTest {

    @Mock
    BulkReceptionService bulkReceptionService;

    private DispatchAutoReceiveRegistrar registrar() {
        return new DispatchAutoReceiveRegistrar(bulkReceptionService);
    }

    @AfterEach
    void cleanup() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void autoReceiveFalse_registersNothing_andNeverCallsReceive() {
        DispatchAutoReceiveRegistrar reg = registrar();
        ReflectionTestUtils.setField(reg, "enabled", false);
        TransactionSynchronizationManager.initSynchronization();

        reg.registerAfterCommit("20260612", "VN_VNM0004_000001", "0000001");

        assertEquals(0, TransactionSynchronizationManager.getSynchronizations().size());
        verify(bulkReceptionService, never()).receive(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void autoReceiveTrue_registersHook_andAfterCommitCallsReceive() {
        DispatchAutoReceiveRegistrar reg = registrar();
        ReflectionTestUtils.setField(reg, "enabled", true);
        TransactionSynchronizationManager.initSynchronization();

        reg.registerAfterCommit("20260612", "VN_VNM0004_000001", "0000001");

        List<TransactionSynchronization> syncs = TransactionSynchronizationManager.getSynchronizations();
        assertEquals(1, syncs.size());
        // Simulate the commit completing.
        syncs.get(0).afterCommit();
        verify(bulkReceptionService).receive("20260612", "VN_VNM0004_000001", "0000001");
    }

    @Test
    void receiveFailureAfterCommit_isSwallowed_neverPropagates() {
        DispatchAutoReceiveRegistrar reg = registrar();
        ReflectionTestUtils.setField(reg, "enabled", true);
        doThrow(new IllegalStateException("TB_FIRM_CONF missing"))
                .when(bulkReceptionService).receive(org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
        TransactionSynchronizationManager.initSynchronization();

        reg.registerAfterCommit("20260612", "VN_VNM0004_000001", "0000001");
        TransactionSynchronization sync = TransactionSynchronizationManager.getSynchronizations().get(0);

        // afterCommit runs AFTER the payment is durably committed; a receive() failure
        // must never bubble out (and therefore cannot affect the committed payment).
        assertDoesNotThrow(sync::afterCommit);
        verify(bulkReceptionService).receive("20260612", "VN_VNM0004_000001", "0000001");
    }

    @Test
    void autoReceiveTrue_butNoActiveTransaction_skipsSafely() {
        DispatchAutoReceiveRegistrar reg = registrar();
        ReflectionTestUtils.setField(reg, "enabled", true);
        // no initSynchronization() → no active transaction synchronization

        assertDoesNotThrow(() -> reg.registerAfterCommit("20260612", "VN_VNM0004_000001", "0000001"));
        verify(bulkReceptionService, never()).receive(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }
}
