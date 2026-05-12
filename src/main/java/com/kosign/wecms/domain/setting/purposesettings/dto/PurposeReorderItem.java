package com.kosign.wecms.domain.setting.purposesettings.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * One {@code (use_usag_cd, disp_ord)} pair in a Reorder payload.
 * Mirrors legacy {@code comm_0187_001_u004_act.jsp:37-40}: both fields
 * required per item.
 */
public record PurposeReorderItem(

        @NotBlank
        @Size(max = 20)
        String useUsagCd,

        @NotNull
        @Positive
        Integer dispOrd
) { }
