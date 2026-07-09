package com.kosign.wecms.domain.payment.approvalbox.service;

import com.kosign.wecms.domain.setting.environmentsettings.entity.BusiEnvSetInfm;
import com.kosign.wecms.domain.setting.environmentsettings.entity.BusiEnvSetInfmId;
import com.kosign.wecms.domain.setting.environmentsettings.repository.BusiEnvSetInfmRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * M-2 (legacy parity): the {@code EXPT_RESOL_MSTR} display flags are read from
 * {@code BUSI_ENV_SET_INFM} (00014/16/17/18) and normalised with legacy
 * {@code YesOrNo.fromValue} semantics — {@code "Y"} only when the env row holds
 * {@code "Y"}, else {@code "N"} (including an unseeded tenant).
 */
@ExtendWith(MockitoExtension.class)
class ApprovalDisplayFlagsResolverTest {

    private static final String BIZ_KEY = "VN_VNM0004_000001";

    @Mock private BusiEnvSetInfmRepository busiEnvSetInfmRepository;
    @InjectMocks private ApprovalDisplayFlagsResolver resolver;

    private void env(String key, String value) {
        BusiEnvSetInfm row = new BusiEnvSetInfm();
        row.setBizKey(BIZ_KEY);
        row.setEnvSetInf(key);
        row.setEnvSetCont(value);
        when(busiEnvSetInfmRepository.findById(new BusiEnvSetInfmId(BIZ_KEY, key)))
                .thenReturn(Optional.of(row));
    }

    @Test
    void resolve_readsEachFlagFromEnv_caseInsensitiveY() {
        env("00014", "Y");   // APPR_BOX_USE_YN
        env("00016", "N");   // APPR_DAT_DISP_YN
        env("00017", "Y");   // CMPN_LOGO_DISP_YN
        env("00018", "y");   // REG_DISP_YN — lower-case still maps to "Y"

        var flags = resolver.resolve(BIZ_KEY);

        assertThat(flags.apprBoxUseYn()).isEqualTo("Y");
        assertThat(flags.apprDatDispYn()).isEqualTo("N");
        assertThat(flags.cmpnLogoDispYn()).isEqualTo("Y");
        assertThat(flags.regDispYn()).isEqualTo("Y");
    }

    @Test
    void resolve_defaultsToN_whenEnvUnseeded() {
        // No env rows stubbed → findById returns empty → every flag "N"
        // (legacy YesOrNo.fromValue("") parity).
        var flags = resolver.resolve(BIZ_KEY);

        assertThat(flags.apprBoxUseYn()).isEqualTo("N");
        assertThat(flags.apprDatDispYn()).isEqualTo("N");
        assertThat(flags.cmpnLogoDispYn()).isEqualTo("N");
        assertThat(flags.regDispYn()).isEqualTo("N");
    }
}
