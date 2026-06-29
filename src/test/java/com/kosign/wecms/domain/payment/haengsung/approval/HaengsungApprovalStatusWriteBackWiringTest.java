package com.kosign.wecms.domain.payment.haengsung.approval;

import com.kosign.wecms.domain.auth.repository.BusiInfmRepository;
import com.kosign.wecms.domain.payment.haengsung.oracle.HaengsungErpOracleRepository;
import com.kosign.wecms.domain.payment.internaltransfer.repository.ScdulPymnSumrDetlRepository;
import com.kosign.wecms.domain.payment.internaltransfer.repository.ScdulPymnSumrRepository;
import com.kosign.wecms.domain.payment.overseas.repository.OvrsPymnDtlsRepository;
import com.kosign.wecms.domain.payment.overseas.repository.OvrsPymnSumrRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Verifies the approval-status write-back listener is gated on
 * {@code haengsung.oracle.enabled}: when off (default), the bean does not exist
 * so a published {@code PaymentApprovalStatusChangedEvent} has no subscriber —
 * i.e. "Oracle disabled → approval sync skipped".
 */
class HaengsungApprovalStatusWriteBackWiringTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(HaengsungErpOracleRepository.class, () -> mock(HaengsungErpOracleRepository.class))
            .withBean(ScdulPymnSumrRepository.class, () -> mock(ScdulPymnSumrRepository.class))
            .withBean(ScdulPymnSumrDetlRepository.class, () -> mock(ScdulPymnSumrDetlRepository.class))
            .withBean(OvrsPymnSumrRepository.class, () -> mock(OvrsPymnSumrRepository.class))
            .withBean(OvrsPymnDtlsRepository.class, () -> mock(OvrsPymnDtlsRepository.class))
            .withBean(BusiInfmRepository.class, () -> mock(BusiInfmRepository.class))
            .withUserConfiguration(HaengsungApprovalStatusWriteBackService.class);

    @Test
    void oracleDisabledByDefault_noListenerBean() {
        runner.run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThat(ctx).doesNotHaveBean(HaengsungApprovalStatusWriteBackService.class);
        });
    }

    @Test
    void oracleExplicitlyDisabled_noListenerBean() {
        runner.withPropertyValues("haengsung.oracle.enabled=false")
                .run(ctx -> assertThat(ctx).doesNotHaveBean(HaengsungApprovalStatusWriteBackService.class));
    }

    @Test
    void oracleEnabled_listenerBeanCreated() {
        runner.withPropertyValues("haengsung.oracle.enabled=true")
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(ctx).hasSingleBean(HaengsungApprovalStatusWriteBackService.class);
                });
    }
}
