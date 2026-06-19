package com.kosign.wecms.domain.banking.bankcredentials.controller;

import com.kosign.wecms.common.response.ApiResponse;
import com.kosign.wecms.domain.banking.bankcredentials.dto.BankCredentialCreateRequest;
import com.kosign.wecms.domain.banking.bankcredentials.dto.BankCredentialListResponse;
import com.kosign.wecms.domain.banking.bankcredentials.dto.BankCredentialRow;
import com.kosign.wecms.domain.banking.bankcredentials.dto.BankCredentialUpdateRequest;
import com.kosign.wecms.domain.banking.bankcredentials.service.BankCredentialService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

/**
 * REST CRUD over {@code BANK_CERT_INFO}. Tenant scope rides on the
 * JWT via {@link com.kosign.wecms.common.tenant.TenantContext}; the
 * route does not carry {@code bizKey}.
 *
 * <p>The password ciphertext is never returned on the wire — list
 * responses surface only the username and inquiry status fields. The
 * plaintext password is only ever read by the scraping flow
 * server-side via
 * {@link com.kosign.wecms.domain.banking.bankcredentials.service.BankCredentialService#loadForScraping}.
 *
 * <p><b>Authorization (audit X-1 fix, 2026-06-19):</b> this controller manages
 * bank login credentials ({@code BANK_CERT_INFO}) and is consumed only by the
 * Bank Credentials page, which is login-credential management. It is therefore
 * gated on the same entitlement as the Login Credentials settings panel —
 * {@code settings-core-login-credentials}. A normal user without that grant is
 * denied (403 / {@code WBE1003}); an admin bypasses the per-user grant via
 * {@link com.kosign.wecms.domain.navigation.service.MenuAuthorizationGuard}.
 */
@RestController
@RequestMapping("/api/v1/banking/credentials")
@RequiredArgsConstructor
@PreAuthorize("@menuAuthz.canAccess('settings-core-login-credentials')")
public class BankCredentialController {

    private final BankCredentialService service;

    @GetMapping
    public ApiResponse<BankCredentialListResponse> list() {
        return ApiResponse.ok(service.list());
    }

    @PostMapping
    public ApiResponse<BankCredentialRow> create(@Valid @RequestBody BankCredentialCreateRequest req) {
        return ApiResponse.ok(service.create(req));
    }

    @PutMapping("/{seqNo}")
    public ApiResponse<BankCredentialRow> update(
            @PathVariable BigDecimal seqNo,
            @Valid @RequestBody BankCredentialUpdateRequest req) {
        return ApiResponse.ok(service.update(seqNo, req));
    }

    @DeleteMapping("/{seqNo}")
    public ApiResponse<Void> delete(@PathVariable BigDecimal seqNo) {
        service.delete(seqNo);
        return ApiResponse.ok(null);
    }
}
