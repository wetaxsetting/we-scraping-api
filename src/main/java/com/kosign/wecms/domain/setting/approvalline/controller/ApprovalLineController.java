package com.kosign.wecms.domain.setting.approvalline.controller;

import com.kosign.wecms.common.response.ApiResponse;
import com.kosign.wecms.domain.setting.approvalline.dto.ApprovalLineCreateRequest;
import com.kosign.wecms.domain.setting.approvalline.dto.ApprovalLineListResponse;
import com.kosign.wecms.domain.setting.approvalline.dto.ApprovalLineRow;
import com.kosign.wecms.domain.setting.approvalline.dto.ApprovalLineUpdateRequest;
import com.kosign.wecms.domain.setting.approvalline.service.ApprovalLineService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * Settings &gt; Approval Line — five REST endpoints under
 * {@code /api/v1/settings/approval-line}, mirroring the legacy
 * {@code comm_0222_*} surface:
 * <ul>
 *   <li>{@code GET    /api/v1/settings/approval-line}
 *       → list (legacy {@code comm_0222_001_r001}).</li>
 *   <li>{@code GET    /api/v1/settings/approval-line/{appr_line_no}}
 *       → detail (legacy {@code comm_0222_003_r001}).</li>
 *   <li>{@code POST   /api/v1/settings/approval-line}
 *       → create (legacy {@code comm_0222_002_c001}).</li>
 *   <li>{@code PUT    /api/v1/settings/approval-line/{appr_line_no}}
 *       → update (legacy {@code comm_0222_003_u001}).</li>
 *   <li>{@code DELETE /api/v1/settings/approval-line/{appr_line_no}}
 *       → hard delete (legacy {@code comm_0222_001_d001}).</li>
 * </ul>
 *
 * <p>Wire format is lowercase snake_case (global Jackson naming
 * strategy from {@code config/JacksonConfig}). Path / query / body
 * parameters use camelCase in Java; {@code @PathVariable("appr_line_no")}
 * pins the segment to its snake_case URI name.
 *
 * <p>Authorization: gated by the F-3 menu-authorization guard
 * {@code @PreAuthorize("@menuAuthz.canAccess('settings-core-approval-line')")}
 * (rolled out 2026-06-06, superseding the earlier OQ-5 "JWT-only"
 * stance). {@code settings-*} codes are product-invariant, so
 * {@code MenuAuthorizationGuard} skips the product-tree gate: an ADMIN
 * passes unconditionally, while a normal user must hold a
 * {@code USE_YN='Y'} grant for {@code settings-core-approval-line} in
 * {@code USER_MENU_AUTH_INFM} (Menu Entitlement universe — see
 * {@code wecms-web/components/settings/users/entitlementTree.ts}). This
 * is stricter than live JEX, which let any authenticated normal user in
 * ({@code NORM_USER_ONLY_YN=Y}); the divergence is the intended uniform
 * Settings-module hardening and is enforced at build time by
 * {@code EndpointAuthorizationCoverageTest} (the picker
 * {@code UserController} is the documented allow-list exception, not this
 * controller). {@code @Validated} at the class level enables the
 * {@code @Positive} guard on the path id.
 *
 * <p>Audit-log: NO writes for any mutation here. Feature § 14 OQ-6
 * resolved 2026-05-13 (legacy parity). The {@code AuditLogService}
 * is intentionally NOT a dependency of {@code ApprovalLineService}.
 */
@RestController
@RequestMapping("/api/v1/settings/approval-line")
@RequiredArgsConstructor
@Validated
@PreAuthorize("@menuAuthz.canAccess('settings-core-approval-line')")
public class ApprovalLineController {

    private final ApprovalLineService service;

    /**
     * List every approval line for the current tenant. Optional
     * case-insensitive name substring search ({@code q}) and sort
     * ({@code sort_field} ∈ {@code REG_DTM} / {@code APPR_LINE_NM} /
     * {@code MOD_DTM} · {@code sort_dir} ∈ {@code ASC} / {@code DESC}).
     * No pagination — feature § 4.3.
     */
    @GetMapping
    public ApiResponse<ApprovalLineListResponse> list(
            @RequestParam(name = "q",          required = false) String q,
            @RequestParam(name = "sort_field", required = false) String sortField,
            @RequestParam(name = "sort_dir",   required = false) String sortDir) {
        return ApiResponse.ok(service.list(q, sortField, sortDir));
    }

    /**
     * Detail by surrogate. Returns the master + full approver chain
     * in the same shape as one element of the list response (the
     * frontend Edit dialog reads this on mount).
     */
    @GetMapping("/{appr_line_no}")
    public ApiResponse<ApprovalLineRow> detail(
            @PathVariable("appr_line_no") @Positive Long apprLineNo) {
        return ApiResponse.ok(service.detail(apprLineNo));
    }

    /**
     * Create a new approval line with 1..5 approvers for the current
     * tenant. The service allocates {@code APPR_LINE_NO} via the
     * legacy {@code wb_get_next_val} branch (V91) and assigns
     * {@code apprvr_disp_ord} from request-array position.
     *
     * <p>Errors: {@code WBE_APPROVAL_LINE_NAME_BLANK},
     * {@code WBE_APPROVAL_LINE_EMPTY_APPROVERS},
     * {@code WBE_APPROVAL_LINE_TOO_MANY_APPROVERS},
     * {@code WBE_APPROVAL_LINE_DUP_APPROVER},
     * {@code WBE_APPROVAL_LINE_APPROVER_NOT_IN_TENANT}.
     */
    @PostMapping
    public ApiResponse<ApprovalLineRow> create(
            @Valid @RequestBody ApprovalLineCreateRequest req) {
        return ApiResponse.ok(service.create(req));
    }

    /**
     * Rename + diff-sync the approver chain for an existing approval
     * line. Path {@code appr_line_no} is immutable — the body MUST
     * NOT carry it.
     *
     * <p>Errors: {@code WBE_APPROVAL_LINE_NOT_FOUND} plus every code
     * from {@link #create(ApprovalLineCreateRequest)}.
     */
    @PutMapping("/{appr_line_no}")
    public ApiResponse<ApprovalLineRow> update(
            @PathVariable("appr_line_no") @Positive Long apprLineNo,
            @Valid @RequestBody ApprovalLineUpdateRequest req) {
        return ApiResponse.ok(service.update(apprLineNo, req));
    }

    /**
     * Hard-delete an approval line and its detail rows for the
     * current tenant. Returns an empty success envelope.
     *
     * <p>Errors: {@code WBE_APPROVAL_LINE_NOT_FOUND}.
     */
    @DeleteMapping("/{appr_line_no}")
    public ApiResponse<Void> delete(
            @PathVariable("appr_line_no") @Positive Long apprLineNo) {
        service.delete(apprLineNo);
        return ApiResponse.ok(null);
    }
}
