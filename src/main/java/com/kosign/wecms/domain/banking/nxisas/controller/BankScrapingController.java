package com.kosign.wecms.domain.banking.nxisas.controller;

import com.kosign.wecms.common.response.ApiResponse;
import com.kosign.wecms.domain.banking.nxisas.BankScrapingService;
import com.kosign.wecms.domain.banking.nxisas.dto.ScrapeSessionResponse;
import com.kosign.wecms.domain.banking.nxisas.dto.StartScrapeRequest;
import com.kosign.wecms.domain.banking.nxisas.dto.SubmitChallengeRequest;
import com.kosign.wecms.domain.banking.nxisas.dto.VerifyCredentialsRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Drives the multi-step scraping flow from the frontend:
 *   POST /api/v1/banking/scraping/account-list             — start a scrape from a saved BANK_CERT_INFO row
 *   POST /api/v1/banking/scraping/verify                   — verify inline credentials BEFORE saving
 *   POST /api/v1/banking/scraping/{sessionId}/otp          — submit the OTP
 *
 * <p>Tenant + user are read from {@link com.kosign.wecms.common.tenant.TenantContext}
 * via the JWT; the route does not carry {@code bizKey}. Session id
 * is a server-issued UUID — the registry binds it to the issuing
 * {@code (bizKey, userId)} pair so it can't be replayed across
 * principals.
 */
@RestController
@RequestMapping("/api/v1/banking/scraping")
@RequiredArgsConstructor
public class BankScrapingController {

    private final BankScrapingService service;

    @PostMapping("/account-list")
    public ApiResponse<ScrapeSessionResponse> start(@Valid @RequestBody StartScrapeRequest req) {
        return ApiResponse.ok(service.start(req));
    }

    /**
     * Verify-before-save path used by the Login Credentials "Login"
     * button. Accepts the user's typed credentials inline (no
     * {@code BANK_CERT_INFO} row required) and runs the same Coocon
     * LOGIN → (OTP challenge | ACCT_LIST) sequence as
     * {@link #start(StartScrapeRequest)}. The frontend persists the
     * credential to {@code BANK_CERT_INFO} via
     * {@code POST /api/v1/login-credentials/bank} only after the user
     * picks accounts from the scrape result.
     *
     * <p>Mirrors the legacy enter_login_popup → _v2nxisas.callNxisas
     * flow (comm_0218_nxisas.ts:508).
     */
    @PostMapping("/verify")
    public ApiResponse<ScrapeSessionResponse> verify(@Valid @RequestBody VerifyCredentialsRequest req) {
        return ApiResponse.ok(service.verify(req));
    }

    @PostMapping("/{sessionId}/otp")
    public ApiResponse<ScrapeSessionResponse> submitOtp(
            @PathVariable UUID sessionId,
            @Valid @RequestBody SubmitChallengeRequest req) {
        return ApiResponse.ok(service.submitOtp(sessionId, req));
    }
}
