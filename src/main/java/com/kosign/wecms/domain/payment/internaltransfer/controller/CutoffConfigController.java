package com.kosign.wecms.domain.payment.internaltransfer.controller;

import com.kosign.wecms.common.response.ApiResponse;
import com.kosign.wecms.domain.payment.internaltransfer.dto.CutoffConfigResponse;
import com.kosign.wecms.domain.payment.internaltransfer.service.CutoffConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only endpoint that exposes the resolved bank cut-off configuration
 * (windows + ≥500M threshold) to the frontend. Lets every payment module's
 * pre-flight check consume the same {@code application.yml} table the backend
 * {@link com.kosign.wecms.domain.payment.internaltransfer.service.CutoffValidator}
 * enforces — removing the frontend's duplicated hard-coded window table
 * (2026-07-09, PO "no hard-coding for cut-off time" directive).
 *
 * <p>Module-neutral — Internal Transfer / Vendor / Expense / Payroll /
 * Overseas all read the same config, so it is not gated to a single menu
 * {@code permission_cd} (authenticated-only; documented on the
 * {@code EndpointAuthorizationCoverageTest} allow-list, exactly like
 * {@code BlockedCharsRuleController}). The backend {@code CutoffValidator}
 * remains the authoritative gate; this is a UX hint.
 */
@RestController
@RequestMapping("/api/v1/payment/cutoff-config")
@RequiredArgsConstructor
public class CutoffConfigController {

    private final CutoffConfigService cutoffConfigService;

    @GetMapping
    public ApiResponse<CutoffConfigResponse> get() {
        return ApiResponse.ok(cutoffConfigService.build());
    }
}
