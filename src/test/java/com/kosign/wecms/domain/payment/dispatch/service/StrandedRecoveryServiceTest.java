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
    @Mock RealtimeDispatchService realtimeDispatchService;
    @Mock BulkReceptionService bulkReceptionService;
    @Mock ResultCheckService resultCheckService;

    private StrandedRecoveryService service;

    @BeforeEach
    void setUp() {
        // alwaysFree: no contention, so the result-check delegation runs exactly as it did
        // before locking was introduced. The contended branch is covered by
        // StrandedRecoveryResultCheckLockTest.
        service = new StrandedRecoveryService(payTargetRepository, payFileMstRepository,
                realtimeDispatchService, bulkReceptionService, resultCheckService,
                com.kosign.wecms.common.lock.JobLockGuardStubs.alwaysFree());
        ReflectionTestUtils.setField(service, "graceMinutes", 30L);
    }

    private void enable() {
        ReflectionTestUtils.setField(service, "enabled", true);
        // The aged-'6' delegation has a second gate: the result-check body's own master flag. Recovery
        // being enabled does NOT enable it — see resultCheckDisabled_skipsOnlyTheDelegation below.
        when(resultCheckService.isEnabled()).thenReturn(true);
    }

    private TbPayTarget payTarget(String tranSeq) {
        TbPayTarget p = new TbPayTarget();
        p.setBizKey(BIZ);
        p.setTranDt("20260612");
        p.setTranSeq(tranSeq);
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
                realtimeDispatchService, bulkReceptionService, resultCheckService);
    }

    @Test
    void enabled_redrivesAgedPayTargets_viaDispatch() {
        enable();
        when(payTargetRepository.findStrandedPayTargets(eq("9"), anyString(), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(List.of(payTarget("1"), payTarget("2")));
        when(payFileMstRepository.findStrandedPayFiles(eq("9"), anyString(), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(List.of());

        service.recoverOnce();

        verify(realtimeDispatchService).dispatch(BIZ, "20260612", "1");
        verify(realtimeDispatchService).dispatch(BIZ, "20260612", "2");
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
    void oneFailedRow_doesNotBlockLaterRows() {
        enable();
        when(payTargetRepository.findStrandedPayTargets(eq("9"), anyString(), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(List.of(payTarget("1"), payTarget("2")));
        when(payFileMstRepository.findStrandedPayFiles(eq("9"), anyString(), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(List.of());
        doThrow(new RuntimeException("EFS down")).when(realtimeDispatchService).dispatch(BIZ, "20260612", "1");

        assertDoesNotThrow(() -> service.recoverOnce());

        // Second row still re-driven despite the first throwing; the delegation still runs.
        verify(realtimeDispatchService).dispatch(BIZ, "20260612", "2");
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

        // The pay-target leg DID run — the pass is partial, not aborted.
        verify(realtimeDispatchService).dispatch(BIZ, "20260612", "1");
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
}
