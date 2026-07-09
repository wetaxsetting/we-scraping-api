package com.kosign.wecms.domain.payment.haengsung.batch;

import com.kosign.wecms.domain.auth.repository.BusiInfmRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Verifies the scheduled-import job is gated by
 * {@code haengsung.scheduled-payment-import.enabled}: absent/false → no bean
 * (so no cron is registered); true → bean present. The Oracle service is left
 * unregistered on purpose — the job injects it via {@code ObjectProvider}, which
 * resolves to empty without a candidate bean, so wiring does not require the
 * Step-1 Oracle datasource.
 */
class HaengsungScheduledPaymentImportJobWiringTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(BusiInfmRepository.class, () -> mock(BusiInfmRepository.class))
            .withUserConfiguration(HaengsungScheduledPaymentImportJob.class);

    @Test
    void disabledByDefault_noBean() {
        runner.run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThat(ctx).doesNotHaveBean(HaengsungScheduledPaymentImportJob.class);
        });
    }

    @Test
    void explicitlyDisabled_noBean() {
        runner.withPropertyValues("haengsung.scheduled-payment-import.enabled=false")
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(ctx).doesNotHaveBean(HaengsungScheduledPaymentImportJob.class);
                });
    }

    @Test
    void enabled_createsScheduledJobBean() {
        runner.withPropertyValues("haengsung.scheduled-payment-import.enabled=true")
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(ctx).hasSingleBean(HaengsungScheduledPaymentImportJob.class);
                    // properties bound (cron defaulted) and available for injection
                    assertThat(ctx).hasSingleBean(HaengsungScheduledPaymentImportProperties.class);
                });
    }
}
