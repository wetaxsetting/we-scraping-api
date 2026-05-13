package com.kosign.wecms.domain.auth.controller;

import com.kosign.wecms.common.response.ApiResponse;
import com.kosign.wecms.domain.auth.dto.TenantUserListResponse;
import com.kosign.wecms.domain.auth.service.UserDirectoryService;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tenant-user directory endpoint &mdash; drives every approver /
 * signer picker in the refactor. Mirrors the read path of legacy
 * {@code comm_0192_001_r001} ("Inquiry Approver").
 *
 * <ul>
 *   <li>{@code GET /api/v1/users}
 *       &mdash; list active users for the current tenant (optional
 *       case-insensitive name substring filter).</li>
 * </ul>
 *
 * <p>Wire format is lowercase snake_case. The {@code q} query
 * parameter caps at 100 chars (legacy {@code USER_NM} length cap)
 * via the class-level {@link Validated} + {@link Size}; missing /
 * blank values fetch the entire tenant directory.
 *
 * <p>JWT authentication is the only gate. No {@code @PreAuthorize}
 * &mdash; legacy WSVC carries {@code NORM_USER_ONLY_YN = Y} only,
 * and WTA users never reach this app surface anyway.
 *
 * <p>Legacy reference + design rationale:
 * {@code docs/refactor-wecms/migration/approval-line-add-flow-analysis.md}.
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Validated
public class UserController {

    private final UserDirectoryService userDirectoryService;

    /**
     * Tenant-user directory for picker / lookup surfaces.
     *
     * @param q optional case-insensitive substring filter against
     *          {@code USER_INFM.USER_NM}. {@code null} / blank returns
     *          the entire active directory for the tenant.
     */
    @GetMapping
    public ApiResponse<TenantUserListResponse> list(
            @RequestParam(name = "q", required = false)
            @Size(max = 100)
            String q) {
        return ApiResponse.ok(userDirectoryService.list(q));
    }
}
