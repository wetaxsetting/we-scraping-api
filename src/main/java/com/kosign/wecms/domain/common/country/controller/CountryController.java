package com.kosign.wecms.domain.common.country.controller;

import com.kosign.wecms.common.response.ApiResponse;
import com.kosign.wecms.domain.common.country.dto.CountryListResponse;
import com.kosign.wecms.domain.common.country.service.CountryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /api/v1/common/countries} — read-only country master.
 *
 * <p>Refactor counterpart to legacy {@code comm_0110_001_r001} (see
 * {@code WECMS DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/
 * comm_0110_001_r001_act.jsp}). Wire shape lowercase snake_case via the
 * global {@code JacksonConfig} naming strategy; no per-field annotations.
 *
 * <p>Authorization: under {@code /api/v1/*} → authenticated per
 * {@code SecurityConfig}. No tenant filter — the country list is shared
 * across every tenant.
 */
@RestController
@RequestMapping("/api/v1/common/countries")
@RequiredArgsConstructor
public class CountryController {

    private final CountryService countryService;

    @GetMapping
    public ApiResponse<CountryListResponse> list() {
        return ApiResponse.ok(countryService.list());
    }
}
