package com.kosign.wecms.domain.payment.dispatch.service;

import com.kosign.wecms.common.lock.JobLockGuard;
import com.kosign.wecms.common.lock.JobLockGuardStubs;
import com.kosign.wecms.common.lock.JobLockNames;
import com.kosign.wecms.domain.payment.dispatch.repository.TbPayFileMstRepository;
import com.kosign.wecms.domain.payment.dispatch.repository.TbPayTargetRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Concurrency-correction tests for the recovery → result-check delegation
 * (scheduler-audit finding F-8 / parity-audit F-9).
 *
 * <p><b>The defect.</b> {@code StrandedRecoveryService.recoverOnce()} calls
 * {@code ResultCheckService.runOnce()} — the identical body that
 * {@code ResultCheckJob} guards with {@code @SchedulerLock}. The recovery job held
 * only its <em>own</em> lock name, so with both jobs enabled the same body could run
 * twice concurrently under two different identities, double-processing live
 * {@code PROC_STAT='6'} payment rows.
 *
 * <p><b>The fix these tests pin.</b> The delegation now acquires
 * {@link JobLockNames#DISPATCH_RESULT_CHECK} — the identity {@code ResultCheckJob}
 * itself uses — and skips when it cannot get it.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StrandedRecoveryResultCheckLockTest {

    @Mock TbPayTargetRepository payTargetRepository;
    @Mock TbPayFileMstRepository payFileMstRepository;
    @Mock BulkReceptionService bulkReceptionService;
    @Mock ResultCheckService resultCheckService;

    private StrandedRecoveryService serviceWith(JobLockGuard guard) {
        StrandedRecoveryService service = new StrandedRecoveryService(
                payTargetRepository, payFileMstRepository,
                bulkReceptionService, resultCheckService, guard, mock(RealtimeDispatchQueue.class));
        ReflectionTestUtils.setField(service, "graceMinutes", 30L);
        ReflectionTestUtils.setField(service, "enabled", true);
        // The delegation has a SECOND gate of its own — the result-check body's master flag. These tests
        // are about lock identity, so open it; StrandedRecoveryServiceTest covers the gate itself.
        when(resultCheckService.isEnabled()).thenReturn(true);
        return service;
    }

    @Test
    void delegatesToResultCheck_whenTheResultCheckLockIsFree() {
        when(payTargetRepository.findStrandedPayTargets(any(), any(), any(org.springframework.data.domain.Pageable.class))).thenReturn(List.of());
        when(payFileMstRepository.findStrandedPayFiles(any(), any(), any(org.springframework.data.domain.Pageable.class))).thenReturn(List.of());

        serviceWith(JobLockGuardStubs.alwaysFree()).recoverOnce();

        verify(resultCheckService).runOnce();
    }

    @Test
    void skipsResultCheck_whenResultCheckJobAlreadyHoldsTheLock() {
        when(payTargetRepository.findStrandedPayTargets(any(), any(), any(org.springframework.data.domain.Pageable.class))).thenReturn(List.of());
        when(payFileMstRepository.findStrandedPayFiles(any(), any(), any(org.springframework.data.domain.Pageable.class))).thenReturn(List.of());

        serviceWith(JobLockGuardStubs.alwaysHeld()).recoverOnce();

        // The whole point: the body must NOT execute a second time concurrently. Asserted on runOnce
        // specifically rather than verifyNoInteractions — the gate check (isEnabled) is itself an
        // interaction now, and reading a flag is not executing the body.
        verify(resultCheckService, never()).runOnce();
    }

    @Test
    void acquiresTheSharedResultCheckIdentity_notTheRecoveryJobIdentity() {
        when(payTargetRepository.findStrandedPayTargets(any(), any(), any(org.springframework.data.domain.Pageable.class))).thenReturn(List.of());
        when(payFileMstRepository.findStrandedPayFiles(any(), any(), any(org.springframework.data.domain.Pageable.class))).thenReturn(List.of());

        JobLockGuard guard = JobLockGuardStubs.alwaysFree();
        serviceWith(guard).recoverOnce();

        ArgumentCaptor<JobLockGuard.LockKey> keyCaptor =
                ArgumentCaptor.forClass(JobLockGuard.LockKey.class);
        verify(guard).runIfFree(keyCaptor.capture(), any());

        JobLockGuard.LockKey key = keyCaptor.getValue();
        assertThat(key.name())
                .as("must borrow the result-check body's identity so ResultCheckJob excludes it")
                .isEqualTo(JobLockNames.DISPATCH_RESULT_CHECK);
        assertThat(key.name()).isNotEqualTo(JobLockNames.DISPATCH_STRANDED_RECOVERY);

        // Bounds must match ResultCheckJob's so neither entry point can outlive the other's
        // crash-recovery ceiling.
        assertThat(key.atMostFor()).isEqualTo(Duration.ofMinutes(30));
        assertThat(key.atLeastFor()).isEqualTo(Duration.ofSeconds(5));
    }

    @Test
    void masterGateOff_neverTouchesTheLockOrTheResultCheck() {
        JobLockGuard guard = mock(JobLockGuard.class);
        StrandedRecoveryService service = new StrandedRecoveryService(
                payTargetRepository, payFileMstRepository,
                bulkReceptionService, resultCheckService, guard, mock(RealtimeDispatchQueue.class));
        ReflectionTestUtils.setField(service, "graceMinutes", 30L);
        // `enabled` left false — the coexistence master gate.

        service.recoverOnce();

        verifyNoInteractions(guard, resultCheckService, payTargetRepository, payFileMstRepository);
    }
}
