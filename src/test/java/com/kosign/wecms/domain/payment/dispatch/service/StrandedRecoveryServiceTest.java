package com.kosign.wecms.domain.payment.dispatch.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.kosign.wecms.domain.payment.dispatch.entity.TbPayFileMst;
import com.kosign.wecms.domain.payment.dispatch.entity.TbPayTarget;
import org.junit.jupiter.api.BeforeEach;
import org.slf4j.LoggerFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link StrandedRecoveryService} — the R-1 recovery safety net (Item 4).
 * Verifies the master gate (no-op when disabled), that aged {@code '9'} rows are re-driven
 * through the existing dispatch/reception paths, that firm-tran {@code '6'} recovery delegates
 * to the result-check, and that one failed row never blocks the rest of the sweep.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StrandedRecoveryServiceTest {

    private static final String BIZ = "VN_VNM0004_000001";

    @Mock com.kosign.wecms.domain.payment.dispatch.repository.TbPayTargetRepository payTargetRepository;
    @Mock com.kosign.wecms.domain.payment.dispatch.repository.TbPayFileMstRepository payFileMstRepository;
    @Mock BulkReceptionService bulkReceptionService;
    @Mock ResultCheckService resultCheckService;
    /**
     * C-6: the sweep no longer dispatches (or paces) inline — it hands each aged {@code '9'} row to the
     * shared paced drain. Pacing itself is {@link RealtimeDispatchQueue}'s property and is asserted in
     * its own test; what this test pins is the hand-off: one {@code enqueue} per aged row, and none for
     * the bulk pay-file leg.
     *
     * <p><b>{@code RealtimeDispatchService} is no longer a mock here (2026-08-01)</b> because it is no
     * longer a collaborator — the constructor does not take it. The guards that used to read
     * {@code verifyNoInteractions(realtimeDispatchService)} are now expressed as "the queue is the only
     * outbound collaborator", which is a stronger statement: the old assertion could only prove the sweep
     * did not use a dependency it still held, whereas the dependency is now absent by construction and
     * re-adding it would have to change the constructor.
     */
    @Mock RealtimeDispatchQueue realtimeDispatchQueue;

    private StrandedRecoveryService service;

    @BeforeEach
    void setUp() {
        // alwaysFree: no contention, so the result-check delegation runs exactly as it did
        // before locking was introduced. The contended branch is covered by
        // StrandedRecoveryResultCheckLockTest.
        service = new StrandedRecoveryService(payTargetRepository, payFileMstRepository,
                bulkReceptionService, resultCheckService,
                com.kosign.wecms.common.lock.JobLockGuardStubs.alwaysFree(), realtimeDispatchQueue);
        ReflectionTestUtils.setField(service, "graceMinutes", 30L);
    }

    private void enable() {
        ReflectionTestUtils.setField(service, "enabled", true);
        // The aged-'6' delegation has a second gate: the result-check body's own master flag. Recovery
        // being enabled does NOT enable it — see resultCheckDisabled_skipsOnlyTheDelegation below.
        when(resultCheckService.isEnabled()).thenReturn(true);
    }

    private TbPayTarget payTarget(String tranSeq) {
        return payTarget(tranSeq, "970424");
    }

    private TbPayTarget payTarget(String tranSeq, String outBankCd) {
        TbPayTarget p = new TbPayTarget();
        p.setBizKey(BIZ);
        p.setTranDt("20260612");
        p.setTranSeq(tranSeq);
        p.setOutBankCd(outBankCd);
        p.setProcStat("9");
        return p;
    }

    private TbPayFileMst payFile(String fileSeqNo) {
        TbPayFileMst p = new TbPayFileMst();
        p.setBizKey(BIZ);
        p.setTranDt("20260626");
        p.setFileSeqNo(fileSeqNo);
        p.setProcStat("9");
        return p;
    }

    @Test
    void disabled_isNoOp() {
        // enabled defaults false — nothing is queried, re-driven, or delegated.
        service.recoverOnce();

        verifyNoInteractions(payTargetRepository, payFileMstRepository,
                bulkReceptionService, resultCheckService);
    }

    @Test
    void enabled_redrivesAgedPayTargets_viaThePacedDrain() {
        enable();
        when(payTargetRepository.findStrandedPayTargets(eq("9"), anyString(), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(List.of(payTarget("1"), payTarget("2")));
        when(payFileMstRepository.findStrandedPayFiles(eq("9"), anyString(), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(List.of());

        service.recoverOnce();

        // C-6: enqueue, don't dispatch. Oldest-first order is preserved into the FIFO drain.
        org.mockito.InOrder inOrder = org.mockito.Mockito.inOrder(realtimeDispatchQueue);
        inOrder.verify(realtimeDispatchQueue).enqueue(BIZ, "20260612", "1");
        inOrder.verify(realtimeDispatchQueue).enqueue(BIZ, "20260612", "2");
        // The queue is the ONLY outbound collaborator: two enqueues and nothing else on it, and no
        // second dispatch path exists to check because the constructor no longer accepts one.
        verify(realtimeDispatchQueue, org.mockito.Mockito.times(2))
                .enqueue(anyString(), anyString(), anyString());
        org.mockito.Mockito.verifyNoMoreInteractions(realtimeDispatchQueue);
    }

    @Test
    void enabled_redrivesAgedPayFiles_viaReceive() {
        enable();
        when(payTargetRepository.findStrandedPayTargets(eq("9"), anyString(), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(List.of());
        when(payFileMstRepository.findStrandedPayFiles(eq("9"), anyString(), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(List.of(payFile("4")));

        service.recoverOnce();

        // NB: receive(tranDt, bizKey, fileSeqNo) — argument order differs from dispatch().
        verify(bulkReceptionService).receive("20260626", BIZ, "4");
    }

    @Test
    void enabled_delegatesFirmTranSixRecovery_toResultCheck() {
        enable();
        when(payTargetRepository.findStrandedPayTargets(eq("9"), anyString(), any(org.springframework.data.domain.Pageable.class))).thenReturn(List.of());
        when(payFileMstRepository.findStrandedPayFiles(eq("9"), anyString(), any(org.springframework.data.domain.Pageable.class))).thenReturn(List.of());

        service.recoverOnce();

        verify(resultCheckService).runOnce();
    }

    @Test
    void oneRejectedRow_doesNotBlockLaterRows() {
        enable();
        when(payTargetRepository.findStrandedPayTargets(eq("9"), anyString(), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(List.of(payTarget("1"), payTarget("2")));
        when(payFileMstRepository.findStrandedPayFiles(eq("9"), anyString(), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(List.of());
        // C-6: the per-row failure mode moved from "dispatch threw" to "the drain refused the ticket"
        // (queue full / shut down). Either way one bad row must not cost the rest of the sweep.
        when(realtimeDispatchQueue.enqueue(BIZ, "20260612", "1")).thenReturn(false);
        when(realtimeDispatchQueue.enqueue(BIZ, "20260612", "2")).thenReturn(true);

        assertDoesNotThrow(() -> service.recoverOnce());

        verify(realtimeDispatchQueue).enqueue(BIZ, "20260612", "2");
        verify(resultCheckService).runOnce();
    }

    @Test
    void disabled_doesNotDelegateToResultCheck() {
        // Regression guard: the result-check delegation must also respect the master gate.
        service.recoverOnce();
        verify(resultCheckService, never()).runOnce();
    }

    /**
     * The partial-recovery trap. The sweep covers four strand classes and delegates one of them — aged
     * firm transactions at {@code 202}/{@code PROC_STAT='6'} — to the result-check body, which carries
     * its own master gate ({@code wecms.dispatch.result-check.enabled}, default false). So enabling
     * recovery alone recovers the two {@code '9'} classes and silently skips the {@code '6'} one, and
     * the pass looks complete.
     *
     * <p>Two things are asserted: the other legs still run (this is a <i>partial</i> pass, not an
     * aborted one), and the skip is announced at <b>WARN</b> naming the flag — a recovery that quietly
     * recovers three quarters of what it claims is worse than one that refuses to start.
     */
    @Test
    void resultCheckDisabled_skipsOnlyTheDelegation_andWarnsWithTheFlagName() {
        ReflectionTestUtils.setField(service, "enabled", true);
        when(resultCheckService.isEnabled()).thenReturn(false);   // the second gate is shut
        when(payTargetRepository.findStrandedPayTargets(eq("9"), anyString(),
                any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(List.of(payTarget("1")));
        when(payFileMstRepository.findStrandedPayFiles(eq("9"), anyString(),
                any(org.springframework.data.domain.Pageable.class))).thenReturn(List.of());

        Logger serviceLogger = (Logger) LoggerFactory.getLogger(StrandedRecoveryService.class);
        ListAppender<ILoggingEvent> logs = new ListAppender<>();
        logs.start();
        serviceLogger.addAppender(logs);
        try {
            service.recoverOnce();
        } finally {
            serviceLogger.detachAppender(logs);
        }

        // The pay-target leg DID run — the pass is partial, not aborted. (C-6: "ran" now means the row
        // reached the shared drain, not that this thread called the gateway.)
        verify(realtimeDispatchQueue).enqueue(BIZ, "20260612", "1");
        // ...and the skipped class is announced loudly enough to notice, naming the flag to set.
        verify(resultCheckService, never()).runOnce();
        assertThat(logs.list)
                .as("a silently partial recovery is the trap this WARN exists to close")
                .anySatisfy(e -> {
                    assertThat(e.getLevel()).isEqualTo(Level.WARN);
                    assertThat(e.getFormattedMessage())
                            .contains("SKIPPED")
                            .contains("wecms.dispatch.result-check.enabled=false");
                });
    }

    /* ─────────────── hand-off to the shared paced drain (C-6) ─────────────── */

    /**
     * Every aged row goes onto {@link RealtimeDispatchQueue}, in oldest-first order, and the sweep itself
     * never touches the gateway.
     *
     * <p><b>What this test does NOT assert:</b> pacing. That moved to the drain when C-6 put the
     * request-thread rail behind the same worker — two pacing points would not be a rate limit, so there
     * is one, and {@link RealtimeDispatchQueueTest} is where it is tested. Asserting it here as well would
     * re-pin the arrangement C-6 replaced.
     *
     * <p>The rows are still deliberately Woori-then-Shinhan: the ordering is what the FIFO drain inherits,
     * and the drain's per-row bank resolution (the D-A divergence from legacy's leaked
     * {@code per_request_delay}) depends on receiving them in a defined order.
     */
    @Test
    void enqueuesEveryAgedRow_ontoTheSharedPacedDrain() {
        enable();
        when(realtimeDispatchQueue.enqueue(anyString(), anyString(), anyString())).thenReturn(true);
        when(payTargetRepository.findStrandedPayTargets(eq("9"), anyString(),
                any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(List.of(payTarget("1", "970457"), payTarget("2", "970424")));
        when(payFileMstRepository.findStrandedPayFiles(eq("9"), anyString(),
                any(org.springframework.data.domain.Pageable.class))).thenReturn(List.of());

        service.recoverOnce();

        // Oldest-first order is preserved into the FIFO drain, so submission order IS gateway order.
        org.mockito.InOrder inOrder = org.mockito.Mockito.inOrder(realtimeDispatchQueue);
        inOrder.verify(realtimeDispatchQueue).enqueue(BIZ, "20260612", "1");
        inOrder.verify(realtimeDispatchQueue).enqueue(BIZ, "20260612", "2");
        // Exactly one ticket per row, and nothing else on the queue — the sweep must not dispatch on its
        // own thread, and since 2026-08-01 it has no dispatch collaborator to do so with.
        verify(realtimeDispatchQueue, org.mockito.Mockito.times(2))
                .enqueue(anyString(), anyString(), anyString());
        org.mockito.Mockito.verifyNoMoreInteractions(realtimeDispatchQueue);
    }

    /**
     * A rejected ticket (drain saturated or shut down) leaves that row at {@code PROC_STAT='9'} for the
     * next pass and must not abort the sweep — otherwise one drop costs every later aged row too.
     */
    @Test
    void oneRejectedTicket_doesNotStopTheSweep() {
        enable();
        when(payTargetRepository.findStrandedPayTargets(eq("9"), anyString(),
                any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(List.of(payTarget("1", "970457"), payTarget("2", "970424")));
        when(payFileMstRepository.findStrandedPayFiles(eq("9"), anyString(),
                any(org.springframework.data.domain.Pageable.class))).thenReturn(List.of());
        when(realtimeDispatchQueue.enqueue(BIZ, "20260612", "1")).thenReturn(false);   // dropped
        when(realtimeDispatchQueue.enqueue(BIZ, "20260612", "2")).thenReturn(true);

        service.recoverOnce();

        verify(realtimeDispatchQueue).enqueue(BIZ, "20260612", "2");
    }

    /** The bulk (pay-file) leg does NOT go through the drain — one 00301 per file, not a per-line burst. */
    @Test
    void doesNotEnqueueTheBulkPayFileLeg() {
        enable();
        when(payTargetRepository.findStrandedPayTargets(eq("9"), anyString(),
                any(org.springframework.data.domain.Pageable.class))).thenReturn(List.of());
        when(payFileMstRepository.findStrandedPayFiles(eq("9"), anyString(),
                any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(List.of(payFile("1"), payFile("2")));

        service.recoverOnce();

        verifyNoInteractions(realtimeDispatchQueue);
    }
}
