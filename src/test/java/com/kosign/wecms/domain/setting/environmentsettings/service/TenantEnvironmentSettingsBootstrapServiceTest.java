package com.kosign.wecms.domain.setting.environmentsettings.service;

import com.kosign.wecms.domain.setting.environmentsettings.entity.BusiEnvSetInfm;
import com.kosign.wecms.domain.setting.environmentsettings.entity.SysEnvSetInfm;
import com.kosign.wecms.domain.setting.environmentsettings.repository.BusiEnvSetInfmRepository;
import com.kosign.wecms.domain.setting.environmentsettings.repository.SysEnvSetInfmRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantEnvironmentSettingsBootstrapServiceTest {

    private static final String BIZ = "VN_VNM0004_000001";
    private static final OffsetDateTime NOW =
            OffsetDateTime.parse("2026-05-10T03:14:15Z");

    @Mock SysEnvSetInfmRepository sysRepo;
    @Mock BusiEnvSetInfmRepository busiRepo;

    private TenantEnvironmentSettingsBootstrapService service() {
        return new TenantEnvironmentSettingsBootstrapService(sysRepo, busiRepo);
    }

    private SysEnvSetInfm sys(String code, String value) {
        SysEnvSetInfm row = new SysEnvSetInfm();
        row.setEnvSetInf(code);
        row.setEnvSetCont(value);
        return row;
    }

    @Test
    void copiesEverySysRow_whenTenantHasNoneYet() {
        when(sysRepo.findAll()).thenReturn(List.of(
                sys("00078", "N"),
                sys("00079", "N")));
        when(busiRepo.findEnvSetInfsByBizKey(BIZ)).thenReturn(Set.of());

        int inserted = service().seedDefaultsForTenant(BIZ, "SYSTEM_BOOTSTRAP", NOW);

        assertThat(inserted).isEqualTo(2);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<BusiEnvSetInfm>> captor = ArgumentCaptor.forClass(List.class);
        verify(busiRepo).saveAll(captor.capture());
        List<BusiEnvSetInfm> saved = captor.getValue();
        assertThat(saved).extracting(BusiEnvSetInfm::getEnvSetInf)
                .containsExactlyInAnyOrder("00078", "00079");
        assertThat(saved).allSatisfy(row -> {
            assertThat(row.getBizKey()).isEqualTo(BIZ);
            assertThat(row.getEnvSetCont()).isEqualTo("N");
            assertThat(row.getModId()).isEqualTo("SYSTEM_BOOTSTRAP");
            // 2026-05-10 03:14:15 UTC → "20260510031415"
            assertThat(row.getModDtm()).isEqualTo("20260510031415");
        });
    }

    @Test
    void skipsRowsAlreadyPresent_preservingCustomisedValues() {
        when(sysRepo.findAll()).thenReturn(List.of(
                sys("00078", "N"),
                sys("00079", "N")));
        // 00078 already customised by tenant (e.g. flipped to Y) — must
        // be left alone. Mirrors legacy wb_init_busi_data WHERE NOT
        // EXISTS guard.
        when(busiRepo.findEnvSetInfsByBizKey(BIZ)).thenReturn(Set.of("00078"));

        int inserted = service().seedDefaultsForTenant(BIZ, "SYSTEM_BOOTSTRAP", NOW);

        assertThat(inserted).isEqualTo(1);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<BusiEnvSetInfm>> captor = ArgumentCaptor.forClass(List.class);
        verify(busiRepo).saveAll(captor.capture());
        assertThat(captor.getValue())
                .extracting(BusiEnvSetInfm::getEnvSetInf)
                .containsExactly("00079");
    }

    @Test
    void noOp_whenAllSysRowsAlreadyPresent() {
        when(sysRepo.findAll()).thenReturn(List.of(
                sys("00078", "N"),
                sys("00079", "N")));
        when(busiRepo.findEnvSetInfsByBizKey(BIZ)).thenReturn(Set.of("00078", "00079"));

        int inserted = service().seedDefaultsForTenant(BIZ, "SYSTEM_BOOTSTRAP", NOW);

        assertThat(inserted).isZero();
        verify(busiRepo, never()).saveAll(anyList());
    }

    @Test
    void noOp_whenSysIsEmpty() {
        when(sysRepo.findAll()).thenReturn(List.of());

        int inserted = service().seedDefaultsForTenant(BIZ, "SYSTEM_BOOTSTRAP", NOW);

        assertThat(inserted).isZero();
        verify(busiRepo, never()).findEnvSetInfsByBizKey(any());
        verify(busiRepo, never()).saveAll(anyList());
    }

    @Test
    void formatsModDtmInUtc_regardlessOfInputOffset() {
        // Same instant as NOW but expressed in +07:00 offset; UTC
        // conversion must yield the same yyyyMMddHHmmss as the UTC
        // offset case.
        OffsetDateTime sameInstantInVietnam =
                NOW.withOffsetSameInstant(ZoneOffset.ofHours(7));

        when(sysRepo.findAll()).thenReturn(List.of(sys("00078", "N")));
        when(busiRepo.findEnvSetInfsByBizKey(BIZ)).thenReturn(Set.of());

        service().seedDefaultsForTenant(BIZ, "SYSTEM_BOOTSTRAP", sameInstantInVietnam);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<BusiEnvSetInfm>> captor = ArgumentCaptor.forClass(List.class);
        verify(busiRepo).saveAll(captor.capture());
        assertThat(captor.getValue().get(0).getModDtm()).isEqualTo("20260510031415");
    }

    @Test
    void rejectsBlankBizKey() {
        assertThatThrownBy(() -> service().seedDefaultsForTenant("", "SYSTEM_BOOTSTRAP", NOW))
                .isInstanceOf(IllegalArgumentException.class);
        verify(sysRepo, never()).findAll();
    }

    @Test
    void rejectsBlankModId() {
        assertThatThrownBy(() -> service().seedDefaultsForTenant(BIZ, "  ", NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNullTimestamp() {
        assertThatThrownBy(() -> service().seedDefaultsForTenant(BIZ, "SYSTEM_BOOTSTRAP", null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
