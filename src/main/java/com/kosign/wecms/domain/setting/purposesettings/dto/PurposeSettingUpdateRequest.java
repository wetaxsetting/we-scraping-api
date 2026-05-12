package com.kosign.wecms.domain.setting.purposesettings.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code PUT /api/v1/settings/purpose-settings/{useUsagCd}}.
 * Mirrors legacy {@code comm_0187_001_u002_act.jsp:33-42} (REC sub-rule):
 * {@code useUsagCd} (path), {@code handUseUsagCd}, {@code useUsagNm}
 * are required.
 *
 * <p>Per OQ-4 (legacy parity): the Update path does NOT duplicate-check
 * {@code handUseUsagCd} — only {@code useUsagNm} is checked
 * (excluding self). This DTO shape is the same as Create for ease of
 * frontend wiring; the duplicate-check difference lives in the service.
 */
public record PurposeSettingUpdateRequest(

        @NotBlank
        @Size(max = 20)
        String handUseUsagCd,

        @NotBlank
        @Size(max = 255)
        String useUsagNm,

        @Size(max = 500)
        String useUsagCont,

        @Size(max = 20)
        String parentUseUsagCd,

        @Pattern(regexp = "^[YN]$", message = "disp_yn: must be Y or N")
        String dispYn
) { }
