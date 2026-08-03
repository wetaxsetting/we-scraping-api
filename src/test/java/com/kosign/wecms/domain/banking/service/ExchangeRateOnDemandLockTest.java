package com.kosign.wecms.domain.banking.service;

import com.kosign.wecms.common.lock.JobLockGuard;
import com.kosign.wecms.common.lock.JobLockGuardStubs;
import com.kosign.wecms.common.lock.JobLockNames;
import com.kosign.wecms.domain.auth.repository.BusiInfmRepository;
import com.kosign.wecms.domain.banking.nxisas.NxisasGatewayClient;
import com.kosign.wecms.domain.banking.repository.BankExchRateInfmRepository;
import com.kosign.wecms.common.legacy.LegacyFunctionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Locking contract for the exchange-rate on-demand catch-up
 * (scheduler-audit finding F-3).
 *
 * <p>{@code ExchangeRateService.list} triggers a synchronous catch-up when today has
 * no rows. That path executes the same three bodies the scheduled FX jobs run, but
 * {@code @SchedulerLock} only guards the scheduled invocation — so before this fix a
 * user opening the Exchange Rate page could write {@code BANK_EXCH_RATE_INFM}
 * concurrently with a running scheduled job. The Woori leg is the dangerous one:
 * both sides compute {@code SEQ_NO = MAX+1} cross-tenant and both INSERT only.
 *
 * <p>These tests pin that the catch-up now goes through
 * {@link JobLockGuard#runIfAllFree} holding <em>all three</em> scheduled FX lock
 * identities, and that it declines to run when any of them is held.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExchangeRateOnDemandLockTest {

    @Mock BusiInfmRepository busiInfmRepository;
    @Mock BankExchRateInfmRepository bankExchRateInfmRepository;
    @Mock NxisasGatewayClient gateway;
    @Mock LegacyFunctionRepository legacyFunctions;

    private ExchangeRateBatchService serviceWith(JobLockGuard guard) {
        return new ExchangeRateBatchService(busiInfmRepository, bankExchRateInfmRepository,
                gateway, legacyFunctions, guard);
    }

    @Test
    void acquiresAllThreeScheduledFxLockIdentities() {
        JobLockGuard guard = JobLockGuardStubs.alwaysFree();

        serviceWith(guard).runOnDemandGuarded();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<JobLockGuard.LockKey>> captor = ArgumentCaptor.forClass(List.class);
        verify(guard).runIfAllFree(captor.capture(), any());

        assertThat(captor.getValue())
                .extracting(JobLockGuard.LockKey::name)
                .as("all three scheduled FX identities must be excluded — runOnDemand writes "
                        + "all three banks inside one transaction")
                .containsExactly(
                        JobLockNames.EXCH_SHINHAN_FIRST_TIME,
                        JobLockNames.EXCH_SHINHAN_LAST_TIME,
                        JobLockNames.EXCH_WOORI);
    }

    @Test
    void reportsTrue_whenAllLocksWereFree() {
        assertThat(serviceWith(JobLockGuardStubs.alwaysFree()).runOnDemandGuarded()).isTrue();
    }

    @Test
    void skipsEntirely_whenAScheduledFxJobHoldsALock() {
        JobLockGuard guard = JobLockGuardStubs.alwaysHeld();

        boolean ran = serviceWith(guard).runOnDemandGuarded();

        assertThat(ran).isFalse();
        // Nothing may be read or written — the scheduled job is already doing this work.
        verifyNoInteractions(busiInfmRepository, bankExchRateInfmRepository, gateway, legacyFunctions);
    }

    @Test
    void usesMultiLockAcquisition_notThreeIndependentGuards() {
        JobLockGuard guard = JobLockGuardStubs.alwaysFree();

        serviceWith(guard).runOnDemandGuarded();

        // runIfAllFree (nested, simultaneous) — NOT three sequential runIfFree calls, which
        // would leave gaps between the legs where a scheduled job could interleave.
        verify(guard).runIfAllFree(anyList(), any());
    }
}
