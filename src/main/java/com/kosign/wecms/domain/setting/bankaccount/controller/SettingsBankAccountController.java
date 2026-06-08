package com.kosign.wecms.domain.setting.bankaccount.controller;

import com.kosign.wecms.common.response.ApiResponse;
import com.kosign.wecms.domain.setting.bankaccount.dto.BankAccountListResponse;
import com.kosign.wecms.domain.setting.bankaccount.dto.BankAccountPermissionResponse;
import com.kosign.wecms.domain.setting.bankaccount.dto.BankAccountPermissionUpdateRequest;
import com.kosign.wecms.domain.setting.bankaccount.dto.BankAccountRegisterRequest;
import com.kosign.wecms.domain.setting.bankaccount.dto.BankAccountRegisterResponse;
import com.kosign.wecms.domain.setting.bankaccount.dto.BankAccountUpdateRequest;
import com.kosign.wecms.domain.setting.bankaccount.service.BankAccountPermissionService;
import com.kosign.wecms.domain.setting.bankaccount.service.SettingsBankAccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * Settings &gt; Bank Account REST surface. Mirrors the legacy
 * {@code comm_0183_*} module — list / update / bulk-register over
 * {@code ACCT_MSTR}. Tenant scope rides on the JWT via
 * {@link com.kosign.wecms.common.tenant.TenantContext}; routes carry
 * no {@code bizKey}.
 *
 * <p>VAS-mapping CRUD, permission management
 * ({@code ACCT_AUTH_INFO}), and the per-row audit-log read endpoint
 * ship in Batch B.
 */
@RestController
@RequestMapping("/api/v1/settings/bank-account")
@RequiredArgsConstructor
@PreAuthorize("@menuAuthz.canAccess('settings-core-bank-account')")
public class SettingsBankAccountController {

    private final SettingsBankAccountService service;
    private final BankAccountPermissionService permissionService;

    /** List all bank accounts under the current tenant.
     *
     *  <p>{@code sortField} is a View Setting {@code FIELD_ID} (e.g.
     *  {@code BANK}, {@code BALANCE}) — resolved by
     *  {@link com.kosign.wecms.domain.setting.bankaccount.service.BankAccountSortField}.
     *  Unknown / blank values fall back to the legacy default ORDER BY
     *  ({@code PRN_SEQ ASC, BANK_CD ASC, ACCT_NO ASC}). */
    @GetMapping
    public ApiResponse<BankAccountListResponse> list(
            @RequestParam(value = "sortField",     required = false) String sortField,
            @RequestParam(value = "sortDirection", required = false) String sortDirection) {
        return ApiResponse.ok(service.list(sortField, sortDirection));
    }

    /** Patch one bank-account master row. Mirrors legacy
     *  {@code comm_0183_003_u001_act.jsp}. */
    @PatchMapping("/{bankCd}/{acctNo}")
    public ApiResponse<Void> update(
            @PathVariable String bankCd,
            @PathVariable String acctNo,
            @Valid @RequestBody BankAccountUpdateRequest req) {
        service.update(bankCd, acctNo, req);
        return ApiResponse.ok(null);
    }

    /** Bulk-INSERT the accounts the user picked from a successful
     *  Coocon scrape. Closes the Login Credentials / Bank Account
     *  Add-chain. */
    @PostMapping("/register")
    public ApiResponse<BankAccountRegisterResponse> register(
            @Valid @RequestBody BankAccountRegisterRequest req) {
        return ApiResponse.ok(service.register(req));
    }

    /* -----------------------------------------------------------------
     *  Permissions sub-resource — Settings > Bank Account > View /
     *  Withdrawal Permission dialogs (Figma 166:24840 / 166:25059).
     * ----------------------------------------------------------------- */

    /** Full roster for one (account, kind). Returns every active user
     *  in the tenant with per-row selected/locked flags so the dialog
     *  can render checkboxes in one paint. */
    @GetMapping("/{bankCd}/{acctNo}/permissions")
    public ApiResponse<BankAccountPermissionResponse> listPermissions(
            @PathVariable String bankCd,
            @PathVariable String acctNo,
            @RequestParam("type") String type) {
        return ApiResponse.ok(permissionService.list(bankCd, acctNo, type));
    }

    /** Replace-semantics save. Wire ships the full desired user-id
     *  set; service computes grant/revoke diff and writes a single
     *  audit event for the change. */
    @PutMapping("/{bankCd}/{acctNo}/permissions")
    public ApiResponse<Void> updatePermissions(
            @PathVariable String bankCd,
            @PathVariable String acctNo,
            @Valid @RequestBody BankAccountPermissionUpdateRequest req) {
        permissionService.replace(bankCd, acctNo, req);
        return ApiResponse.ok(null);
    }
}
