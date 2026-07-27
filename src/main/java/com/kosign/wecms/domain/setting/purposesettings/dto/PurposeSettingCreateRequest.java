package com.kosign.wecms.domain.setting.purposesettings.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /api/v1/settings/purpose-settings}.
 * Mirrors legacy {@code comm_0187_001_c001_act.jsp:31-34}:
 * {@code handUseUsagCd} and {@code useUsagNm} are required; the rest
 * are optional.
 *
 * <p>Wire format: lowercase snake_case via the global Jackson
 * SNAKE_CASE strategy ({@code JacksonConfig}). Java fields stay
 * camelCase.
 *
 * <p>Server-derived (NOT on the wire): {@code biz_key} (from
 * {@link com.kosign.wecms.common.tenant.TenantContext}),
 * {@code use_usag_cd} (via {@code wb_get_next_val}), {@code disp_ord}
 * ({@code MAX+1}), {@code use_yn} ({@code 'Y'}), {@code usag_grp_cd}
 * ({@code NULL}), {@code mod_id} / {@code mod_dtm}.
 */
public record PurposeSettingCreateRequest(

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

        /** {@code 'Y'} or {@code 'N'}. Optional; defaults to {@code 'Y'}
         *  when omitted (service-layer fallback). */
        @Pattern(regexp = "^[YN]$", message = "disp_yn: must be Y or N")
        String dispYn,

        /** Target tab group — {@code '4'} (Banking Deposit) or
         *  {@code '6'} (Banking Withdrawal). Optional; when omitted the
         *  row lands in the historic refactor {@code usag_grp_cd IS NULL}
         *  scope (backward compatible). Mirrors legacy
         *  {@code comm_0187_001_view.jsp} tab {@code data-usag-grp-cd}. */
        @Pattern(regexp = "^[46]$", message = "usag_grp_cd: must be 4 or 6")
        String usagGrpCd
) { }
