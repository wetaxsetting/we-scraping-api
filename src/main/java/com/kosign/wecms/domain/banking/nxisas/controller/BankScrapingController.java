package com.kosign.wecms.domain.banking.nxisas.controller;

import com.kosign.wecms.common.response.ApiResponse;
import com.kosign.wecms.domain.banking.nxisas.BankScrapingService;
import com.kosign.wecms.domain.banking.nxisas.dto.ScrapeSessionResponse;
import com.kosign.wecms.domain.banking.nxisas.dto.StartScrapeRequest;
import com.kosign.wecms.domain.banking.nxisas.dto.SubmitChallengeRequest;
import com.kosign.wecms.domain.banking.nxisas.dto.SubmitQnaRequest;
import com.kosign.wecms.domain.banking.nxisas.dto.VerifyCredentialsRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
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
 *   POST /api/v1/banking/scraping/{sessionId}/qna          — submit a security-question answer
 *   POST /api/v1/banking/scraping/{sessionId}/captcha      — submit a typed CAPTCHA value
 *
 * <p>Tenant + user are read from {@link com.kosign.wecms.common.tenant.TenantContext}
 * via the JWT; the route does not carry {@code bizKey}. Session id
 * is a server-issued UUID — the registry binds it to the issuing
 * {@code (bizKey, userId)} pair so it can't be replayed across
 * principals.
 *
 * <p><b>Authorization (audit fix, 2026-06-19):</b> the scraping flow is shared
 * by three legitimate parent screens — the Login Credentials panel and the
 * Bank Credentials page (verify-before-save + saved-row scrape), the Bank
 * Account panel (scrape at account registration), and the Corporate Card
 * add-card flow (verify-before-save). It is therefore gated on
 * {@code canAccessAny('settings-core-login-credentials',
 * 'settings-core-bank-account', 'payment-corporate-card-transactions')} — a
 * user holding ANY of those entitlements may scrape; a user holding none is
 * denied (403 / {@code WBE1003}); an admin bypasses the per-user grant. Gating
 * to a single cd would 403 the other two legitimate flows.
 */
@RestController
@RequestMapping("/api/v1/banking/scraping")
@RequiredArgsConstructor
@PreAuthorize("@menuAuthz.canAccessAny('settings-core-login-credentials', 'settings-core-bank-account', 'payment-corporate-card-transactions')")
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

    /**
     * Resume a scrape after a QNA (security-question) challenge.
     * The user picks ONE of the LOGIN response's questions and types
     * its answer; the gateway may complete the login or come back
     * with another challenge.
     */
    @PostMapping("/{sessionId}/qna")
    public ApiResponse<ScrapeSessionResponse> submitQna(
            @PathVariable UUID sessionId,
            @Valid @RequestBody SubmitQnaRequest req) {
        return ApiResponse.ok(service.submitQna(sessionId, req));
    }

    /**
     * Resume a scrape after a CAPTCHA challenge. Wire body reuses
     * {@link SubmitChallengeRequest} (same {@code {"value":"..."}}
     * shape as OTP); the path segment is the discriminator.
     */
    @PostMapping("/{sessionId}/captcha")
    public ApiResponse<ScrapeSessionResponse> submitCaptcha(
            @PathVariable UUID sessionId,
            @Valid @RequestBody SubmitChallengeRequest req) {
        return ApiResponse.ok(service.submitCaptcha(sessionId, req));
    }
}
