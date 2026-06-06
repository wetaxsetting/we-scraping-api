package com.kosign.wecms.domain.navigation.controller;

import com.kosign.wecms.common.response.ApiResponse;
import com.kosign.wecms.domain.navigation.dto.NavigationResponse;
import com.kosign.wecms.domain.navigation.service.NavigationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Navigation API — serves the per-product menu tree the frontend renders as
 * the top nav + sidebar.
 *
 * <p>{@code GET /api/v1/navigation} returns the resolved visible tree for the
 * authenticated session: {@code Product Tree ∩ grants} for a normal user, the
 * full product tree for an admin. The product type is read from the signed JWT
 * (via {@code TenantContext}) inside the service — the client never supplies
 * it. Protected by the global {@code anyRequest().authenticated()} rule.
 */
@RestController
@RequestMapping("/api/v1/navigation")
@RequiredArgsConstructor
public class NavigationController {

    private final NavigationService navigationService;

    @GetMapping
    public ApiResponse<NavigationResponse> navigation() {
        return ApiResponse.ok(navigationService.resolve());
    }
}
