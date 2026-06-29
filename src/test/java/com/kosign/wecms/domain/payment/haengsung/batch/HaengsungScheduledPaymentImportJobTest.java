package com.kosign.wecms.domain.payment.haengsung.batch;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.auth.entity.BusiInfm;
import com.kosign.wecms.domain.auth.repository.BusiInfmRepository;
import com.kosign.wecms.domain.payment.haengsung.dto.HaengsungRefreshResult;
import com.kosign.wecms.domain.payment.haengsung.service.ErpPaymentRefreshService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HaengsungScheduledPaymentImportJobTest {

    @Mock org.springframework.beans.factory.ObjectProvider<ErpPaymentRefreshService> provider;
    @Mock ErpPaymentRefreshService service;
    @Mock BusiInfmRepository busiInfmRepository;

    private final HaengsungScheduledPaymentImportProperties props =
            new HaengsungScheduledPaymentImportProperties(true, null);

    private HaengsungScheduledPaymentImportJob job() {
        return new HaengsungScheduledPaymentImportJob(provider, busiInfmRepository, props);
    }

    private BusiInfm hBusi(String bizKey) {
        BusiInfm b = new BusiInfm();
        b.setBizKey(bizKey); b.setPrdtType("H"); b.setDelYn("N"); b.setTaxId("tax-" + bizKey);
        return b;
    }

    @AfterEach void tearDown() { TenantContext.clear(); }

    /* ---------------- Oracle feature off → no-op ---------------- */

    @Test void oracleDisabled_noOp_doesNotQueryBusinesses() {
        when(provider.getIfAvailable()).thenReturn(null);

        HaengsungScheduledPaymentImportJob.Summary s = job().runOnce();

        assertThat(s).isEqualTo(HaengsungScheduledPaymentImportJob.Summary.EMPTY);
        verifyNoInteractions(busiInfmRepository, service);
    }

    /* ---------------- only HAENGSUNG businesses + shared service + aggregation ---------------- */

    @Test void selectsHaengsungOnly_callsSharedServicePerBusiness_aggregatesCounts() {
        when(provider.getIfAvailable()).thenReturn(service);
        when(busiInfmRepository.findByPrdtTypeAndDelYn("H", "N"))
                .thenReturn(List.of(hBusi("BIZ1"), hBusi("BIZ2")));
        when(service.importUnreceivedForCurrentTenant())
                .thenReturn(new HaengsungRefreshResult(2, 1, 1, 0, 5))
                .thenReturn(new HaengsungRefreshResult(3, 2, 0, 1, 7));

        HaengsungScheduledPaymentImportJob.Summary s = job().runOnce();

        // H-only selection: the finder is the (H, N) one — never findAll()
        verify(busiInfmRepository).findByPrdtTypeAndDelYn(eq("H"), eq("N"));
        // shared Step-2 service called once per business
        verify(service, times(2)).importUnreceivedForCurrentTenant();

        assertThat(s.businesses()).isEqualTo(2);
        assertThat(s.okBusinesses()).isEqualTo(2);
        assertThat(s.failedBusinesses()).isZero();
        assertThat(s.mastersFound()).isEqualTo(5);
        assertThat(s.imported()).isEqualTo(3);
        assertThat(s.skipped()).isEqualTo(1);
        assertThat(s.failed()).isEqualTo(1);
        assertThat(s.detailsImported()).isEqualTo(12);
    }

    /* ---------------- one business failure does not stop the next ---------------- */

    @Test void oneBusinessFailure_continuesWithNext() {
        when(provider.getIfAvailable()).thenReturn(service);
        when(busiInfmRepository.findByPrdtTypeAndDelYn("H", "N"))
                .thenReturn(List.of(hBusi("BIZ1"), hBusi("BIZ2")));
        when(service.importUnreceivedForCurrentTenant())
                .thenThrow(new RuntimeException("oracle blip"))
                .thenReturn(new HaengsungRefreshResult(3, 2, 0, 1, 7));

        HaengsungScheduledPaymentImportJob.Summary s = job().runOnce();

        verify(service, times(2)).importUnreceivedForCurrentTenant();   // second still ran
        assertThat(s.businesses()).isEqualTo(2);
        assertThat(s.okBusinesses()).isEqualTo(1);
        assertThat(s.failedBusinesses()).isEqualTo(1);
        assertThat(s.imported()).isEqualTo(2);                          // only the successful one
        assertThat(s.detailsImported()).isEqualTo(7);
    }

    /* ---------------- TenantContext cleared after the run ---------------- */

    @Test void tenantContext_clearedAfterRun() {
        when(provider.getIfAvailable()).thenReturn(service);
        when(busiInfmRepository.findByPrdtTypeAndDelYn("H", "N")).thenReturn(List.of(hBusi("BIZ1")));
        when(service.importUnreceivedForCurrentTenant())
                .thenReturn(new HaengsungRefreshResult(0, 0, 0, 0, 0));

        job().runOnce();

        assertThat(TenantContext.current()).isNull();
    }

    /* ---------------- no credential logging ---------------- */

    @Test void neverLogsOracleCredentialsOrUrl() {
        Logger logger = (Logger) LoggerFactory.getLogger(HaengsungScheduledPaymentImportJob.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            when(provider.getIfAvailable()).thenReturn(service);
            when(busiInfmRepository.findByPrdtTypeAndDelYn("H", "N")).thenReturn(List.of(hBusi("BIZ1")));
            when(service.importUnreceivedForCurrentTenant())
                    .thenReturn(new HaengsungRefreshResult(1, 1, 0, 0, 1));

            job().runOnce();

            assertThat(appender.list).isNotEmpty();
            for (ILoggingEvent e : appender.list) {
                String msg = e.getFormattedMessage().toLowerCase();
                assertThat(msg).doesNotContain("jdbc:");
                assertThat(msg).doesNotContain("password");
                assertThat(msg).doesNotContain("oracle.net");
            }
        } finally {
            logger.detachAppender(appender);
        }
    }
}
