package com.kosign.wecms.domain.payment.haengsung.controller;

import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.lock.JobLockGuardStubs;
import com.kosign.wecms.common.response.ApiResponse;
import com.kosign.wecms.domain.payment.haengsung.dto.HaengsungRefreshRequest;
import com.kosign.wecms.domain.payment.haengsung.dto.HaengsungRefreshResult;
import com.kosign.wecms.domain.payment.haengsung.service.ErpPaymentRefreshService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HaengsungPaymentRefreshControllerTest {

    @Mock ObjectProvider<ErpPaymentRefreshService> provider;
    @Mock ErpPaymentRefreshService service;

    private final HaengsungRefreshRequest req = new HaengsungRefreshRequest("ALL", "20260628");

    @Test void oracleDisabled_returnsBusinessError() {
        // Flag OFF → the gated service bean is absent → provider yields null.
        when(provider.getIfAvailable()).thenReturn(null);
        HaengsungPaymentRefreshController controller = new HaengsungPaymentRefreshController(provider, JobLockGuardStubs.alwaysFree());

        assertThatThrownBy(() -> controller.refresh(req))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo("WBE_HS_REFRESH_DISABLED"));
    }

    @Test void enabled_delegatesToService() {
        HaengsungRefreshResult result = new HaengsungRefreshResult(2, 1, 1, 0, 5);
        when(provider.getIfAvailable()).thenReturn(service);
        when(service.refresh(req)).thenReturn(result);
        HaengsungPaymentRefreshController controller = new HaengsungPaymentRefreshController(provider, JobLockGuardStubs.alwaysFree());

        ApiResponse<HaengsungRefreshResult> response = controller.refresh(req);

        assertThat(response.getData()).isSameAs(result);
    }

    @Test void importAlreadyRunning_returnsRetryableBusinessError() {
        // The scheduled HaengsungScheduledPaymentImportJob holds the shared import lock.
        // refresh(request) and the job's importUnreceivedForCurrentTenant() both reach
        // ErpPaymentRefreshService.processMasters(...), so the manual path must not proceed
        // (scheduler-audit F-3). Failing fast with a retryable code is deliberate: returning
        // a zero-row result would read as "nothing to import", which is a different claim.
        when(provider.getIfAvailable()).thenReturn(service);
        HaengsungPaymentRefreshController controller =
                new HaengsungPaymentRefreshController(provider, JobLockGuardStubs.alwaysHeld());

        assertThatThrownBy(() -> controller.refresh(req))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo("WBE_HS_REFRESH_IN_PROGRESS"));

        verifyNoInteractions(service);
    }
}
