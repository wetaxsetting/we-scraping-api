package com.kosign.wecms.domain.banking.service;

import com.kosign.wecms.common.legacy.LegacyFunctionRepository;
import com.kosign.wecms.domain.auth.repository.BusiInfmRepository;
import com.kosign.wecms.domain.banking.nxisas.NxisasGatewayClient;
import com.kosign.wecms.domain.banking.repository.BankExchRateInfmRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * G1 tenant fan-out parity tests for {@link ExchangeRateBatchService}.
 *
 * <p>The fan-out filter is delegated to the repository derived query
 * {@code findByDelYnAndBusiSttsAndPrdtTypeIn}, so these unit tests assert the
 * service asks the DB for <em>exactly</em> the legacy {@code BUSI_INFM_R001}
 * filter ({@code del_yn='N' AND busi_stts='N' AND prdt_type IN ('F','M','H')})
 * and never falls back to {@code findAll()}. The row-level exclusion semantics
 * (drop {@code del_yn='Y'}, non-NORMAL {@code busi_stts} W/S/C, and non-F/M/H
 * product types) are enforced by Spring Data's derived query and are covered by
 * the method-name contract; a DB-level {@code @DataJpaTest} is deferred (no
 * embedded DB is wired in this module — Mockito-only per project policy).
 *
 * <p>Each run method returns early on an empty tenant list (before any gateway
 * call), so mocking the filter query to return empty isolates the fan-out
 * assertion without needing to stub the Coocon gateway.
 */
@ExtendWith(MockitoExtension.class)
class ExchangeRateBatchServiceTest {

    @Mock private BusiInfmRepository busiInfmRepository;
    @Mock private BankExchRateInfmRepository bankExchRateInfmRepository;
    @Mock private NxisasGatewayClient gateway;
    @Mock private LegacyFunctionRepository legacyFunctions;

    @InjectMocks private ExchangeRateBatchService service;

    private void stubEmptyFanOut() {
        when(busiInfmRepository.findByDelYnAndBusiSttsAndPrdtTypeIn(eq("N"), eq("N"), org.mockito.ArgumentMatchers.anyCollection()))
                .thenReturn(List.of());
    }

    @SuppressWarnings("unchecked")
    private void assertLegacyFanOutFilterUsed() {
        ArgumentCaptor<Collection<String>> ccyCaptor = ArgumentCaptor.forClass(Collection.class);
        verify(busiInfmRepository).findByDelYnAndBusiSttsAndPrdtTypeIn(eq("N"), eq("N"), ccyCaptor.capture());
        // Exactly the refactor-recognised product types — F, M, H (no W/R/B/... ).
        assertThat(ccyCaptor.getValue()).containsExactlyInAnyOrder("F", "M", "H");
        // Never the over-broad findAll() fan-out (the pre-G1 behaviour).
        verify(busiInfmRepository, never()).findAll();
    }

    @Test
    void shinhanFirstTime_usesLegacyFanOutFilter_notFindAll() {
        stubEmptyFanOut();
        service.runShinhanFirstTime();
        assertLegacyFanOutFilterUsed();
    }

    @Test
    void shinhanLastTime_usesLegacyFanOutFilter_notFindAll() {
        stubEmptyFanOut();
        service.runShinhanLastTime();
        assertLegacyFanOutFilterUsed();
    }

    @Test
    void woori_usesLegacyFanOutFilter_notFindAll() {
        stubEmptyFanOut();
        service.runWoori();
        assertLegacyFanOutFilterUsed();
    }
}
