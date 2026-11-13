package com.kosign.wecms.domain.weinvoice.controller;

import com.kosign.wecms.common.response.ApiResponse;
import com.kosign.wecms.domain.weinvoice.dto.DashboardResponse;
import com.kosign.wecms.domain.weinvoice.dto.EngineRequestResponse;
import com.kosign.wecms.domain.weinvoice.dto.MeResponse;
import com.kosign.wecms.domain.weinvoice.dto.PreferenceRequest;
import com.kosign.wecms.domain.weinvoice.service.DashboardService;
import com.kosign.wecms.domain.weinvoice.service.EngineRequestService;
import com.kosign.wecms.domain.weinvoice.service.MeService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * WeInvoice app frame (screens 01–03): who is signed in, preferences, the dashboard, and the
 * progress of any engine request ({@code request_id} returned by the other endpoints).
 * Login / logout are the existing {@code /api/v1/auth/*}.
 */
@RestController
@RequestMapping("/api/v1/weinvoice")
@RequiredArgsConstructor
@Validated
public class WeInvoiceAppController {

    private final MeService me;
    private final DashboardService dashboard;
    private final EngineRequestService requests;

    @GetMapping("/me")
    public ApiResponse<MeResponse> me() {
        return ApiResponse.ok(me.me());
    }

    @PutMapping("/me/preferences")
    public ApiResponse<MeResponse> preferences(@Valid @RequestBody PreferenceRequest request) {
        return ApiResponse.ok(me.savePreferences(request));
    }

    @GetMapping("/requests/{requestId}")
    public ApiResponse<EngineRequestResponse> request(@PathVariable("requestId") String requestId) {
        return ApiResponse.ok(requests.get(requestId));
    }

    @GetMapping("/dashboard")
    public ApiResponse<DashboardResponse> dashboard(
            @RequestParam("company_mst") @NotBlank String companyMst,
            @RequestParam(value = "year", required = false) Integer year) {
        return ApiResponse.ok(dashboard.get(companyMst, year));
    }
}
