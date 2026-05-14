package com.kosign.wecms.domain.setting.logincredentials.controller;

import com.kosign.wecms.common.response.ApiResponse;
import com.kosign.wecms.domain.setting.logincredentials.dto.BankFormInfoResponse;
import com.kosign.wecms.domain.setting.logincredentials.dto.BankPickerResponse;
import com.kosign.wecms.domain.setting.logincredentials.dto.BankCredentialCreateRequest;
import com.kosign.wecms.domain.setting.logincredentials.dto.BankCredentialUpdateRequest;
import com.kosign.wecms.domain.setting.logincredentials.dto.CredentialCreateResponse;
import com.kosign.wecms.domain.setting.logincredentials.dto.GdtCredentialRequest;
import com.kosign.wecms.domain.setting.logincredentials.dto.GdtCredentialUpdateRequest;
import com.kosign.wecms.domain.setting.logincredentials.dto.LoginCredentialAuditResponse;
import com.kosign.wecms.domain.setting.logincredentials.dto.LoginCredentialListResponse;
import com.kosign.wecms.domain.setting.logincredentials.dto.RevealRequest;
import com.kosign.wecms.domain.setting.logincredentials.dto.RevealResponse;
import com.kosign.wecms.domain.setting.logincredentials.service.LoginCredentialsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

/**
 * Unified Login Credentials API — list (banking ∪ GDT), delete,
 * password reveal, and audit-log read. Mirrors the legacy
 * {@code comm_0218_*} screen group (refactor slug
 * {@code "login-credentials"}). Tenant scope rides on the JWT via
 * {@link com.kosign.wecms.common.tenant.TenantContext}; routes carry
 * no {@code bizKey}.
 *
 * <p>Bank Create / Update over BANK_CERT_INFO is exposed here for the
 * Login Credentials popup chain. The older NXISAS scraping spike's
 * controller at {@code /api/v1/banking/credentials} remains in place
 * for its own use — both write paths share the underlying
 * BANK_CERT_INFO table but live behind different REST surfaces (the
 * scraping spike's POST predates audit-log integration; this slice's
 * POST writes the matching audit-log rows).
 */
@RestController
@RequestMapping("/api/v1/login-credentials")
@RequiredArgsConstructor
public class LoginCredentialsController {

    private final LoginCredentialsService service;

    /** Grouped list: one row per BANK_CD (with N accounts) + one
     *  GDT row when configured. */
    @GetMapping
    public ApiResponse<LoginCredentialListResponse> list() {
        return ApiResponse.ok(service.list());
    }

    /** Banks available to add — sourced from {@code BANK_INFM_DETL}
     *  (scraping-capable rows only). Mirrors the legacy
     *  {@code nxisas_acctList_r002} response. Returns real
     *  {@code BANK_CD} values (e.g. {@code "11704088"}) so the
     *  downstream POST → NXISAS scrape lands on the right bank
     *  adapter. {@code search} (optional) does a case-insensitive
     *  substring match on {@code BANK_NM}. */
    @GetMapping("/banks")
    public ApiResponse<BankPickerResponse> banks(
            @RequestParam(value = "search", required = false) String search) {
        return ApiResponse.ok(service.banks(search));
    }

    /** Per-bank form-info — drives the dynamic Login ID/PW dialog's
     *  radio buttons (when the bank has multiple sub-types) and the
     *  per-sub-type localised field labels (mirrors legacy
     *  {@code nxisas_getCertFormInfo}). Returns an empty
     *  {@code sub_types} list for unknown banks. */
    @GetMapping("/banks/{bankCd}/form-info")
    public ApiResponse<BankFormInfoResponse> bankFormInfo(
            @PathVariable String bankCd) {
        return ApiResponse.ok(service.formInfo(bankCd));
    }

    /** Create one BANK_CERT_INFO row. Writes one audit-log row per
     *  supported locale via the shared writer. Returns the freshly
     *  allocated SEQ_NO so the frontend can refresh and key its new
     *  row. */
    @PostMapping("/bank")
    public ApiResponse<CredentialCreateResponse> createBank(
            @Valid @RequestBody BankCredentialCreateRequest req) {
        return ApiResponse.ok(service.createBank(req));
    }

    /** Update one existing BANK_CERT_INFO row by SEQ_NO. {@code loginId}
     *  / {@code password} / {@code extra} are individually optional —
     *  null means "leave unchanged"; the service rejects the all-null
     *  case with {@code WBE_VALIDATION}. */
    @PutMapping("/bank/{seqNo}")
    public ApiResponse<Void> updateBank(
            @PathVariable BigDecimal seqNo,
            @Valid @RequestBody BankCredentialUpdateRequest req) {
        service.updateBank(seqNo, req);
        return ApiResponse.ok(null);
    }

    /** Create the GDT credential row for the current tenant. The
     *  legacy upsert behaviour ({@code comm_0218_007_c001}) splits
     *  cleanly into POST (new tenant) + PUT (existing row). */
    @PostMapping("/gdt")
    public ApiResponse<CredentialCreateResponse> createGdt(
            @Valid @RequestBody GdtCredentialRequest req) {
        return ApiResponse.ok(service.createGdt(req));
    }

    /** Update the GDT credential row. Same null-means-unchanged
     *  semantics as {@link #updateBank}. */
    @PutMapping("/gdt/{seqNo}")
    public ApiResponse<Void> updateGdt(
            @PathVariable BigDecimal seqNo,
            @Valid @RequestBody GdtCredentialUpdateRequest req) {
        service.updateGdt(seqNo, req);
        return ApiResponse.ok(null);
    }

    /** Delete one credential row. {@code type} is {@code "bank"} or
     *  {@code "gdt"}. Writes audit rows via the shared
     *  {@link com.kosign.wecms.common.auditlog.service.AuditLogService}
     *  with {@code wsvcId = "login-credentials"}. */
    @DeleteMapping("/{type}/{seqNo}")
    public ApiResponse<Void> delete(
            @PathVariable String type,
            @PathVariable BigDecimal seqNo) {
        service.delete(type, seqNo);
        return ApiResponse.ok(null);
    }

    /** Re-auth + decrypt one credential. The body carries the user's
     *  own WeCMS login password as a confirmation step. */
    @PostMapping("/{type}/{seqNo}/reveal")
    public ApiResponse<RevealResponse> reveal(
            @PathVariable String type,
            @PathVariable BigDecimal seqNo,
            @Valid @RequestBody RevealRequest req) {
        return ApiResponse.ok(service.reveal(type, seqNo, req.userPassword()));
    }

    /** Audit history popup — rows latest-first, already localized to
     *  the caller's locale. */
    @GetMapping("/{type}/{seqNo}/audit-logs")
    public ApiResponse<LoginCredentialAuditResponse> auditLogs(
            @PathVariable String type,
            @PathVariable BigDecimal seqNo) {
        return ApiResponse.ok(service.auditLogs(type, seqNo));
    }
}
