package com.kosign.wecms.domain.setting.environmentsettings.service;

import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.setting.environmentsettings.dto.EnvironmentSettingResponse;
import com.kosign.wecms.domain.setting.environmentsettings.entity.BusiEnvSetInfm;
import com.kosign.wecms.domain.setting.environmentsettings.entity.BusiEnvSetInfmId;
import com.kosign.wecms.domain.setting.environmentsettings.entity.SysEnvSetInfm;
import com.kosign.wecms.domain.setting.environmentsettings.repository.BusiEnvSetInfmRepository;
import com.kosign.wecms.domain.setting.environmentsettings.repository.SysEnvSetInfmRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EnvironmentSettingsServiceTest {

    private static final String BIZ = "VN_VNM0004_000001";
    private static final String USER = "rndadmin";

    @Mock SysEnvSetInfmRepository sysRepo;
    @Mock BusiEnvSetInfmRepository busiRepo;

    private EnvironmentSettingsService service;

    @BeforeEach
    void setUp() {
        service = new EnvironmentSettingsService(sysRepo, busiRepo);
        TenantContext.set(USER, USER, BIZ, LangCd.EN);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private SysEnvSetInfm sys(String code, String value) {
        SysEnvSetInfm row = new SysEnvSetInfm();
        row.setEnvSetInf(code);
        row.setEnvSetCont(value);
        return row;
    }

    private BusiEnvSetInfm busi(String code, String value) {
        BusiEnvSetInfm row = new BusiEnvSetInfm();
        row.setBizKey(BIZ);
        row.setEnvSetInf(code);
        row.setEnvSetCont(value);
        return row;
    }

    @Test
    void get_returnsBusiOverride_whenPresent() {
        when(sysRepo.findById("00078")).thenReturn(Optional.of(sys("00078", "N")));
        when(busiRepo.findById(new BusiEnvSetInfmId(BIZ, "00078")))
                .thenReturn(Optional.of(busi("00078", "Y")));

        EnvironmentSettingResponse res = service.get("00078");

        assertThat(res.envSetInf()).isEqualTo("00078");
        assertThat(res.envSetCont()).isEqualTo("Y");
    }

    @Test
    void get_fallsBackToSysDefault_whenBusiRowMissing() {
        // Mirrors legacy IDO BUSI_ENV_SET_INFM_R002 LEFT JOIN +
        // COALESCE(BUSI, SYS): tenant has not been bootstrapped for
        // this code yet, so SYS default wins.
        when(sysRepo.findById("00078")).thenReturn(Optional.of(sys("00078", "N")));
        when(busiRepo.findById(new BusiEnvSetInfmId(BIZ, "00078")))
                .thenReturn(Optional.empty());

        EnvironmentSettingResponse res = service.get("00078");

        assertThat(res.envSetCont()).isEqualTo("N");
    }

    @Test
    void get_throwsWBE2030_whenSysCodeUnknown() {
        when(sysRepo.findById("99999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get("99999"))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("WBE2030");
    }

    @Test
    void update_writesNewValue_andStampsAuditFields() {
        when(sysRepo.existsById("00078")).thenReturn(true);
        when(busiRepo.findById(new BusiEnvSetInfmId(BIZ, "00078")))
                .thenReturn(Optional.of(busi("00078", "N")));

        EnvironmentSettingResponse res = service.update("00078", "Y");

        assertThat(res.envSetInf()).isEqualTo("00078");
        assertThat(res.envSetCont()).isEqualTo("Y");

        ArgumentCaptor<BusiEnvSetInfm> saved = ArgumentCaptor.forClass(BusiEnvSetInfm.class);
        verify(busiRepo).save(saved.capture());
        assertThat(saved.getValue().getEnvSetCont()).isEqualTo("Y");
        assertThat(saved.getValue().getModId()).isEqualTo(USER);
        // yyyyMMddHHmmss UTC (14 chars, all digits)
        assertThat(saved.getValue().getModDtm()).matches("\\d{14}");
    }

    @Test
    void update_throwsWBE2030_whenSysCodeUnknown() {
        when(sysRepo.existsById("99999")).thenReturn(false);

        assertThatThrownBy(() -> service.update("99999", "Y"))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("WBE2030");
        verify(busiRepo, never()).save(any());
    }

    @Test
    void update_throwsWBE2031_whenBusiRowMissing() {
        // Tenant not bootstrapped for this code - mirrors legacy
        // validateBusiEnvSetInfm row-not-found error path. Re-running
        // tenant bootstrap via TenantEnvironmentSettingsBootstrapService
        // resolves it.
        when(sysRepo.existsById("00078")).thenReturn(true);
        when(busiRepo.findById(new BusiEnvSetInfmId(BIZ, "00078")))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update("00078", "Y"))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("WBE2031");
        verify(busiRepo, never()).save(any());
    }

    @Test
    void update_acceptsFreeTextForNonToggleCodes() {
        // Legacy validateInputEnvYesNo silently skips Y/N enforcement
        // for codes outside its 31-code whitelist. Refactor matches:
        // a code like "00056" (doc-id prefix) accepts "IV-" etc.
        when(sysRepo.existsById("00056")).thenReturn(true);
        when(busiRepo.findById(new BusiEnvSetInfmId(BIZ, "00056")))
                .thenReturn(Optional.of(busi("00056", "IV-")));

        EnvironmentSettingResponse res = service.update("00056", "INV-");

        assertThat(res.envSetCont()).isEqualTo("INV-");
    }
}
