package com.kosign.wecms.domain.banking.nxisas.controller;

import com.kosign.wecms.common.response.ApiResponse;
import com.kosign.wecms.domain.banking.nxisas.BankScrapingService;
import com.kosign.wecms.domain.banking.nxisas.dto.ScrapeSessionResponse;
import com.kosign.wecms.domain.banking.nxisas.dto.StartScrapeRequest;
import com.kosign.wecms.domain.banking.nxisas.dto.SubmitChallengeRequest;
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
 *   POST /api/v1/banking/scraping/account-list             — start a scrape
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

    @PostMapping("/{sessionId}/otp")
    public ApiResponse<ScrapeSessionResponse> submitOtp(
            @PathVariable UUID sessionId,
            @Valid @RequestBody SubmitChallengeRequest req) {
        return ApiResponse.ok(service.submitOtp(sessionId, req));
    }
}
