package com.kosign.wecms.domain.setting.controller;

import com.kosign.wecms.common.response.ApiResponse;
import com.kosign.wecms.domain.setting.dto.SaveScreenOptionRequest;
import com.kosign.wecms.domain.setting.dto.ScreenAreaListResponse;
import com.kosign.wecms.domain.setting.dto.ScreenOptionListResponse;
import com.kosign.wecms.domain.setting.service.ScreenOptionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * View Setting endpoints. End-to-end design lives in
 * {@code docs/features/view-setting.md}; legacy mapping is
 * {@code comm_0108_001_*_osmc}.
 *
 * <ul>
 *   <li>GET    {@code /api/v1/screen-options/{webSvcId}/{dymAreaId}}     — load merged config</li>
 *   <li>GET    {@code /api/v1/screen-options/{webSvcId}/areas}           — list configured areas</li>
 *   <li>PUT    {@code /api/v1/screen-options/{webSvcId}/{dymAreaId}}     — save user overrides</li>
 *   <li>DELETE {@code /api/v1/screen-options/{webSvcId}/{dymAreaId}}     — reset (delete user rows)</li>
 * </ul>
 *
 * <p>Both path variables are validated against the semantic-id convention
 * locked in by the design doc: lowercase kebab-case, ASCII only.
 * {@code webSvcId} requires ≥ 2 segments (module + page); {@code dymAreaId}
 * accepts a single segment so {@code "default"} passes. All endpoints
 * are authenticated; identity is read from {@code TenantContext}.
 */
@RestController
@RequestMapping("/api/v1/screen-options")
@RequiredArgsConstructor
@Validated
public class ScreenOptionController {

    /** Refactor screenId convention: lowercase kebab-case
     *  {@code <module>-<page>[-<type>]} — at least two segments
     *  (e.g. {@code banking-account-inquiry-vnd}). Legacy JEX ids
     *  ({@code fnsh_0001_001}) are explicitly rejected. The single
     *  source of truth is the frontend registry at
     *  {@code lib/viewSettingScreens.ts}; backend rejects payloads
     *  that don't fit the convention so unknown screenIds fail fast
     *  instead of orphaning rows in {@code SYS_SCR_OPTION_WECMS}. */
    private static final String SCREEN_ID_REGEX = "^[a-z][a-z0-9]*(-[a-z0-9]+)+$";

    /** Single-segment area names allowed (e.g. {@code default}); kebab style. */
    private static final String AREA_ID_REGEX = "^[a-z][a-z0-9]*(-[a-z0-9]+)*$";

    private final ScreenOptionService screenOptionService;

    @GetMapping("/{webSvcId}/areas")
    public ApiResponse<ScreenAreaListResponse> listAreas(
            @PathVariable("webSvcId")
            @Pattern(regexp = SCREEN_ID_REGEX) String webSvcId) {
        return ApiResponse.ok(screenOptionService.listAreas(webSvcId));
    }

    @GetMapping("/{webSvcId}/{dymAreaId}")
    public ApiResponse<ScreenOptionListResponse> load(
            @PathVariable("webSvcId")
            @Pattern(regexp = SCREEN_ID_REGEX) String webSvcId,
            @PathVariable("dymAreaId")
            @Pattern(regexp = AREA_ID_REGEX) String dymAreaId) {
        return ApiResponse.ok(screenOptionService.load(webSvcId, dymAreaId));
    }

    @PutMapping("/{webSvcId}/{dymAreaId}")
    public ApiResponse<ScreenOptionListResponse> save(
            @PathVariable("webSvcId")
            @Pattern(regexp = SCREEN_ID_REGEX) String webSvcId,
            @PathVariable("dymAreaId")
            @Pattern(regexp = AREA_ID_REGEX) String dymAreaId,
            @Valid @RequestBody SaveScreenOptionRequest request) {
        return ApiResponse.ok(screenOptionService.save(webSvcId, dymAreaId, request.items()));
    }

    @DeleteMapping("/{webSvcId}/{dymAreaId}")
    public ApiResponse<ScreenOptionListResponse> reset(
            @PathVariable("webSvcId")
            @Pattern(regexp = SCREEN_ID_REGEX) String webSvcId,
            @PathVariable("dymAreaId")
            @Pattern(regexp = AREA_ID_REGEX) String dymAreaId) {
        return ApiResponse.ok(screenOptionService.reset(webSvcId, dymAreaId));
    }
}
