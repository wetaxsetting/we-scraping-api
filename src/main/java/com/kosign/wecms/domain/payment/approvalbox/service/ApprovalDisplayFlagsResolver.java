package com.kosign.wecms.domain.payment.approvalbox.service;

import com.kosign.wecms.domain.setting.environmentsettings.entity.BusiEnvSetInfm;
import com.kosign.wecms.domain.setting.environmentsettings.entity.BusiEnvSetInfmId;
import com.kosign.wecms.domain.setting.environmentsettings.repository.BusiEnvSetInfmRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * M-2 (legacy parity): resolves the {@code EXPT_RESOL_MSTR} print/display
 * flags from {@code BUSI_ENV_SET_INFM} instead of hardcoding them.
 *
 * <p>Legacy {@code comm_0192_001_c005_act.jsp:316-320} and
 * {@code card_0001_005_c001_act.jsp:256-260} both source these flags via
 * {@code SzonHelper.getEnvSetCont(bizKey, "000NN")} →
 * {@code YesOrNo.fromValue(...)}:
 * <ul>
 *   <li>{@code 00014} → {@code APPR_BOX_USE_YN}</li>
 *   <li>{@code 00016} → {@code APPR_DAT_DISP_YN}</li>
 *   <li>{@code 00017} → {@code CMPN_LOGO_DISP_YN}</li>
 *   <li>{@code 00018} → {@code REG_DISP_YN}</li>
 * </ul>
 * ({@code 00015} → {@code APPR_BOX_DISP_CNT} is consumed by the approval-line
 * snapshot count in {@link ApprovalBoxFanoutService#writeApprovalLineSnapshot};
 * the {@code EXPT_RESOL_MSTR.APPR_BOX_DISP_CNT} column itself is not mapped on
 * the refactor entity, so it is not stamped here — a documented minor residual.)
 *
 * <p>Normalisation mirrors legacy {@code YesOrNo.fromValue}: the stored
 * value is {@code "Y"} only when the env row holds {@code "Y"}; every other
 * value — including an unseeded (absent) env row — resolves to {@code "N"}.
 * This is the faithful legacy behaviour for a tenant whose environment
 * settings have not been configured.
 */
@Component
@RequiredArgsConstructor
public class ApprovalDisplayFlagsResolver {

    private final BusiEnvSetInfmRepository busiEnvSetInfmRepository;

    /** Immutable snapshot of the four mapped display flags. */
    public record DisplayFlags(
            String apprBoxUseYn,
            String apprDatDispYn,
            String cmpnLogoDispYn,
            String regDispYn) { }

    /** Read the four display flags for the tenant; absent rows default to {@code "N"}. */
    public DisplayFlags resolve(String bizKey) {
        return new DisplayFlags(
                yn(readEnv(bizKey, "00014")),
                yn(readEnv(bizKey, "00016")),
                yn(readEnv(bizKey, "00017")),
                yn(readEnv(bizKey, "00018")));
    }

    private String readEnv(String bizKey, String key) {
        return busiEnvSetInfmRepository.findById(new BusiEnvSetInfmId(bizKey, key))
                .map(BusiEnvSetInfm::getEnvSetCont)
                .orElse(null);
    }

    /** Legacy {@code YesOrNo.fromValue} semantics — {@code "Y"} or {@code "N"}. */
    private static String yn(String raw) {
        return raw != null && "Y".equalsIgnoreCase(raw.trim()) ? "Y" : "N";
    }
}
