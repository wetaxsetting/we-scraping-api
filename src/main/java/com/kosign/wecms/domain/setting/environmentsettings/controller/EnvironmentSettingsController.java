package com.kosign.wecms.domain.setting.environmentsettings.controller;

import com.kosign.wecms.common.response.ApiResponse;
import com.kosign.wecms.domain.setting.environmentsettings.dto.EnvironmentSettingResponse;
import com.kosign.wecms.domain.setting.environmentsettings.dto.EnvironmentSettingUpdateRequest;
import com.kosign.wecms.domain.setting.environmentsettings.service.EnvironmentSettingsService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Settings &gt; Environment Settings - generic Y/N + free-text
 * tenant flag store.
 *
 * <p>Mirrors two legacy WSVCs that any module may call with any
 * env_set_inf code:
 * <ul>
 *   <li>{@code GET /api/v1/settings/environment-settings/{env_set_inf}}
 *       - read one value with COALESCE(BUSI override, SYS default).
 *       Mirrors legacy {@code comm_0600_001_r001}.</li>
 *   <li>{@code PUT /api/v1/settings/environment-settings/{env_set_inf}}
 *       - update one value (UPDATE only; row pre-seeded by
 *       {@code wb_init_busi_data} / refactor's
 *       {@code TenantEnvironmentSettingsBootstrapService}). Mirrors
 *       legacy {@code comm_0215_001_u001}.</li>
 * </ul>
 *
 * <p>First consumer: Firm Banking Settings panel - codes
 * {@code 00078} (Auto Transfer) / {@code 00079} (Auto Match) per
 * legacy {@code comm_0220_001.ts:34/39/114/120}. The endpoints are
 * intentionally generic so future modules consume the same routes
 * without controller changes.
 *
 * <p>Wire format: lowercase snake_case via the global Jackson
 * SNAKE_CASE strategy. Path variable {@code env_set_inf} is
 * snake_case to match the wire convention; Java parameter is
 * camelCase via the explicit {@code @PathVariable("env_set_inf")}
 * binding.
 */
@RestController
@RequestMapping("/api/v1/settings/environment-settings")
@RequiredArgsConstructor
@Validated
public class EnvironmentSettingsController {

    /** Five-char zero-padded code (e.g. {@code "00078"}). Legacy
     *  schema is {@code VARCHAR(5)}; legacy seed values are all
     *  digits. Pattern accepts any 5-character ASCII identifier so a
     *  future non-numeric code can land without a controller change;
     *  the deeper "is this a known code" check happens in the service
     *  via {@code SysEnvSetInfmRepository}. */
    private static final String ENV_SET_INF_PATTERN = "^[A-Za-z0-9]{5}$";

    private final EnvironmentSettingsService service;

    @GetMapping("/{env_set_inf}")
    public ApiResponse<EnvironmentSettingResponse> get(
            @PathVariable("env_set_inf")
            @Pattern(regexp = ENV_SET_INF_PATTERN, message = "env_set_inf: must be 5 alphanumeric characters")
            String envSetInf
    ) {
        return ApiResponse.ok(service.get(envSetInf));
    }

    @PutMapping("/{env_set_inf}")
    public ApiResponse<EnvironmentSettingResponse> update(
            @PathVariable("env_set_inf")
            @Pattern(regexp = ENV_SET_INF_PATTERN, message = "env_set_inf: must be 5 alphanumeric characters")
            String envSetInf,
            @Valid @RequestBody EnvironmentSettingUpdateRequest body
    ) {
        return ApiResponse.ok(service.update(envSetInf, body.envSetCont()));
    }
}
