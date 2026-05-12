package com.kosign.wecms.domain.auth.bootstrap;

import com.kosign.wecms.config.TenantProperties;
import com.kosign.wecms.domain.auth.entity.BusiInfm;
import com.kosign.wecms.domain.auth.entity.EmplInfm;
import com.kosign.wecms.domain.auth.repository.BusiInfmRepository;
import com.kosign.wecms.domain.auth.repository.EmplInfmRepository;
import com.kosign.wecms.domain.setting.environmentsettings.service.TenantEnvironmentSettingsBootstrapService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompanyBootstrapServiceTest {

    private static final String BIZ = "VN_VNM0004_000001";
    private static final String EMPL = "20260429_001";

    @Mock BusiInfmRepository busiInfmRepository;
    @Mock EmplInfmRepository emplInfmRepository;
    @Mock UserBootstrapService userBootstrapService;
    @Mock BusinessKeyGeneratorService businessKeyGenerator;
    @Mock EmployeeNumberGeneratorService employeeNumberGenerator;
    @Mock TenantEnvironmentSettingsBootstrapService environmentSettingsBootstrap;

    CompanyBootstrapService service;

    private final Clock fixedClock = Clock.fixed(Instant.parse("2026-04-29T00:00:00Z"), ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        service = new CompanyBootstrapService(
                busiInfmRepository,
                emplInfmRepository,
                userBootstrapService,
                businessKeyGenerator,
                employeeNumberGenerator,
                environmentSettingsBootstrap,
                new TenantProperties(BIZ),
                fixedClock);
    }

    private CompanyBootstrapCommand commandWithBizKey() {
        return new CompanyBootstrapCommand(
                BIZ, "WeCMS R&D Tenant", "VN", "VNM0004",
                new FirstAdminCommand("rndadmin", "R&D Admin", "Password!1",
                        "rndadmin@example.com", null));
    }

    @Test
    void bootstrap_writesAllFiveTables_andReturnsCreated() {
        when(busiInfmRepository.existsById(BIZ)).thenReturn(false);
        when(employeeNumberGenerator.nextEmplNo(eq(BIZ), any(LocalDate.class))).thenReturn(EMPL);
        when(userBootstrapService.createFirstAdmin(eq(BIZ), eq(EMPL), any(FirstAdminCommand.class), eq(CompanyBootstrapService.SYSTEM_BOOTSTRAP)))
                .thenReturn("rndadmin");

        BootstrapResult result = service.bootstrap(commandWithBizKey());

        assertThat(result.created()).isTrue();
        assertThat(result.bizKey()).isEqualTo(BIZ);
        assertThat(result.userId()).isEqualTo("rndadmin");
        assertThat(result.emplNo()).isEqualTo(EMPL);

        ArgumentCaptor<BusiInfm> busi = ArgumentCaptor.forClass(BusiInfm.class);
        verify(busiInfmRepository).save(busi.capture());
        assertThat(busi.getValue().getBizKey()).isEqualTo(BIZ);
        assertThat(busi.getValue().getCmpnNm()).isEqualTo("WeCMS R&D Tenant");
        assertThat(busi.getValue().getRegId()).isEqualTo(CompanyBootstrapService.SYSTEM_BOOTSTRAP);
        assertThat(busi.getValue().getDelYn()).isEqualTo("N");

        ArgumentCaptor<EmplInfm> empl = ArgumentCaptor.forClass(EmplInfm.class);
        verify(emplInfmRepository).save(empl.capture());
        assertThat(empl.getValue().getEmplNo()).isEqualTo(EMPL);
        assertThat(empl.getValue().getREmplNo()).isEqualTo(EMPL);
        assertThat(empl.getValue().getEmplNm()).isEqualTo("R&D Admin");

        verify(userBootstrapService).createFirstAdmin(eq(BIZ), eq(EMPL), any(FirstAdminCommand.class),
                eq(CompanyBootstrapService.SYSTEM_BOOTSTRAP));

        // Mirrors legacy `wb_init_busi_data` env-set leg — runs after
        // BUSI_INFM is saved (FK requires the parent first).
        verify(environmentSettingsBootstrap).seedDefaultsForTenant(
                eq(BIZ), eq(CompanyBootstrapService.SYSTEM_BOOTSTRAP), any(OffsetDateTime.class));
    }

    @Test
    void bootstrap_idempotent_returnsAlreadyExists_whenBizKeyExists() {
        when(busiInfmRepository.existsById(BIZ)).thenReturn(true);

        BootstrapResult result = service.bootstrap(commandWithBizKey());

        assertThat(result.created()).isFalse();
        assertThat(result.bizKey()).isEqualTo(BIZ);
        verify(busiInfmRepository, never()).save(any());
        verify(emplInfmRepository, never()).save(any());
        verify(userBootstrapService, never()).createFirstAdmin(any(), any(), any(), any());
        verify(environmentSettingsBootstrap, never())
                .seedDefaultsForTenant(any(), any(), any());
    }

    @Test
    void bootstrapIfAbsent_shortCircuits_withoutAnyGeneratorCalls() {
        when(busiInfmRepository.existsById(BIZ)).thenReturn(true);

        BootstrapResult result = service.bootstrapIfAbsent(commandWithBizKey());

        assertThat(result.created()).isFalse();
        verify(businessKeyGenerator, never()).nextBizKey(any(), any());
        verify(employeeNumberGenerator, never()).nextEmplNo(any(), any());
    }

    @Test
    void bootstrap_failsFast_whenBizKeyDoesNotMatchPinnedTenant() {
        CompanyBootstrapCommand cmd = new CompanyBootstrapCommand(
                "VN_VNM9999_000001", "Other", "VN", "VNM9999",
                new FirstAdminCommand("x", "x", "Password!1", null, null));

        assertThatThrownBy(() -> service.bootstrap(cmd))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("does not match wecms.tenant.biz-key");

        verify(busiInfmRepository, never()).save(any());
    }

    @Test
    void bootstrap_generatesBizKey_whenCommandHasNoBizKey() {
        when(businessKeyGenerator.nextBizKey("VN", "VNM0004")).thenReturn(BIZ);
        when(busiInfmRepository.existsById(BIZ)).thenReturn(false);
        when(employeeNumberGenerator.nextEmplNo(eq(BIZ), any(LocalDate.class))).thenReturn(EMPL);
        when(userBootstrapService.createFirstAdmin(eq(BIZ), eq(EMPL), any(FirstAdminCommand.class), eq(CompanyBootstrapService.SYSTEM_BOOTSTRAP)))
                .thenReturn("rndadmin");

        CompanyBootstrapCommand cmd = new CompanyBootstrapCommand(
                null, "WeCMS R&D Tenant", "VN", "VNM0004",
                new FirstAdminCommand("rndadmin", "R&D Admin", "Password!1", null, null));

        BootstrapResult result = service.bootstrap(cmd);

        assertThat(result.created()).isTrue();
        assertThat(result.bizKey()).isEqualTo(BIZ);
        verify(businessKeyGenerator).nextBizKey("VN", "VNM0004");
    }

    @Test
    void isBootstrapped_returnsFalseForNullBizKey() {
        assertThat(service.isBootstrapped(null)).isFalse();
        verify(busiInfmRepository, never()).existsById(any());
    }
}
