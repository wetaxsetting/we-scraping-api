package com.kosign.wecms.domain.payment.haengsung.controller;

import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.response.ApiResponse;
import com.kosign.wecms.domain.payment.haengsung.dto.HaengsungRefreshRequest;
import com.kosign.wecms.domain.payment.haengsung.dto.HaengsungRefreshResult;
import com.kosign.wecms.domain.payment.haengsung.service.ErpPaymentRefreshService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HAENGSUNG ({@code PRDT_TYPE='H'}) Oracle payment Refresh import
 * ({@code haengsung-payment-refresh}, Phase 1 — manual Refresh only).
 *
 * <p>One shared endpoint backs all four payment types (Vendor / Expense /
 * Payroll / Overseas), mirroring the single legacy {@code ErpDataRefreshHelper}.
 *
 * <p><b>Authorization:</b> menu-gated to any of the four scheduled-payment
 * screens (a HAENGSUNG user who can open any of them may Refresh), satisfying the
 * {@code EndpointAuthorizationCoverageTest}. The real boundary is the
 * <b>product-type {@code H} guard in {@link ErpPaymentRefreshService}</b>, which
 * reads {@code PrdtType} from the signed JWT — never from the request.
 *
 * <p>The Oracle datasource + import service are flag-gated
 * ({@code haengsung.oracle.enabled}); when off the service bean is absent and
 * this controller returns {@code WBE_HS_REFRESH_DISABLED} so the app still boots
 * and the endpoint stays registered.
 */
@RestController
@RequestMapping("/api/v1/payments/haengsung")
@RequiredArgsConstructor
@PreAuthorize("@menuAuthz.canAccessAny('payment-vendor-scheduled','payment-expense-scheduled',"
        + "'payment-payroll-scheduled','payment-overseas-request')")
public class HaengsungPaymentRefreshController {

    /** Optional — present only when {@code haengsung.oracle.enabled=true}. */
    private final ObjectProvider<ErpPaymentRefreshService> refreshService;

    @PostMapping("/refresh")
    public ApiResponse<HaengsungRefreshResult> refresh(@Valid @RequestBody HaengsungRefreshRequest request) {
        ErpPaymentRefreshService service = refreshService.getIfAvailable();
        if (service == null) {
            throw new BusinessException("WBE_HS_REFRESH_DISABLED");
        }
        return ApiResponse.ok(service.refresh(request));
    }
}
