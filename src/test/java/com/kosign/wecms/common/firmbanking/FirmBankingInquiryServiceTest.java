package com.kosign.wecms.common.firmbanking;

import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.payment.dispatch.recipient.DepositorInquiryService;
import com.kosign.wecms.domain.payment.dispatch.recipient.RecipientLookupCommand;
import com.kosign.wecms.domain.setting.firmbankingsettings.entity.TbFirmConf;
import com.kosign.wecms.domain.setting.firmbankingsettings.repository.FirmBankingSettingRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the production caller wiring (C1) — {@link FirmBankingInquiryService}
 * delegating to {@link DepositorInquiryService}. Verifies the feature gate, the
 * TB_FIRM_CONF gate, and the command built from the firm-config row.
 */
@ExtendWith(MockitoExtension.class)
class FirmBankingInquiryServiceTest {

    private static final String BIZ_KEY = "VN_VNM0004_000001";

    @Mock FirmBankingSettingRepository firmBankingSettingRepo;
    @Mock DepositorInquiryService depositorInquiryService;
    @InjectMocks FirmBankingInquiryService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "recipientInquiryEnabled", true);
        // M: real pass-through override (no UAT profile) so the inquiry command is built from the
        // firm-config values verbatim — the UAT-active behaviour is covered by UatShinhanWooriOverrideTest.
        ReflectionTestUtils.setField(service, "uatOverride",
                new UatShinhanWooriOverride(new org.springframework.mock.env.MockEnvironment()));
        TenantContext.set("u1", BIZ_KEY, LangCd.EN);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private static TbFirmConf conf() {
        TbFirmConf c = new TbFirmConf();
        c.setBizKey(BIZ_KEY);
        c.setVanOrgCd("ORG000001");
        c.setOutBankCd("970424");
        c.setBankPrtnId("PRTN1");
        c.setWdrlActNo("9988776655");
        c.setOrgStat("1");
        return c;
    }

    @Test
    void blank_napas_returns_empty_without_delegating() {
        assertThat(service.inquiryAccount("  ", "123")).isEmpty();
        verifyNoInteractions(depositorInquiryService, firmBankingSettingRepo);
    }

    @Test
    void feature_flag_off_returns_empty_without_delegating() {
        ReflectionTestUtils.setField(service, "recipientInquiryEnabled", false);
        assertThat(service.inquiryAccount("970436", "123")).isEmpty();
        verifyNoInteractions(depositorInquiryService, firmBankingSettingRepo);
    }

    @Test
    void no_active_firm_config_returns_empty_without_delegating() {
        when(firmBankingSettingRepo.findFirstByBizKeyAndOrgStatOrderByRegDttmAsc(BIZ_KEY, "1"))
                .thenReturn(Optional.empty());

        assertThat(service.inquiryAccount("970436", "123")).isEmpty();
        verify(depositorInquiryService, never()).inquire(any(), any());
    }

    @Test
    void delegates_with_command_built_from_firm_config() {
        when(firmBankingSettingRepo.findFirstByBizKeyAndOrgStatOrderByRegDttmAsc(BIZ_KEY, "1"))
                .thenReturn(Optional.of(conf()));
        ArgumentCaptor<RecipientLookupCommand> captor = ArgumentCaptor.forClass(RecipientLookupCommand.class);
        when(depositorInquiryService.inquire(eq(BIZ_KEY), captor.capture()))
                .thenReturn(Optional.of("NGUYEN VAN A"));

        Optional<String> result = service.inquiryAccount("970436", " 12345678901111 ");

        assertThat(result).contains("NGUYEN VAN A");
        RecipientLookupCommand cmd = captor.getValue();
        assertThat(cmd.vanOrgCd()).isEqualTo("ORG000001");
        assertThat(cmd.bankCd()).isEqualTo("970424");
        assertThat(cmd.prtnId()).isEqualTo("PRTN1");
        assertThat(cmd.tarBankCd()).isEqualTo("970436");
        assertThat(cmd.tarFincMdaDiv()).isEqualTo("A");
        assertThat(cmd.tarFincMdaNo()).isEqualTo("12345678901111"); // trimmed
        assertThat(cmd.withdAcctNo()).isEqualTo("9988776655");
    }
}
