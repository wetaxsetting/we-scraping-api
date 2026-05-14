package com.kosign.wecms.domain.banking.controller;

import com.kosign.wecms.common.response.ApiResponse;
import com.kosign.wecms.domain.banking.dto.ExchangeRateBankItem;
import com.kosign.wecms.domain.banking.dto.ExchangeRateListResponse;
import com.kosign.wecms.domain.banking.service.ExchangeRateService;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Exchange Rate Inquiry endpoints.
 *
 * <p>Data is populated by batch {@code EXCH_0001_001} (Coocon Shinhan API)
 * and consumed by the frontend {@code ExchangeRateInquiryPage}.</p>
 *
 * <p>JEX reference: {@code bcs.jex.wabooks.exch.EXCH_0001_001} (write path).
 * Slug: {@code banking-exchange-rate-inquiry}.</p>
 *
 * <p>Swagger: no {@code @Tag}/{@code @Operation} annotations are used anywhere
 * in the banking controllers — Springdoc OpenAPI is not wired in this project.
 * Add annotations here when/if it is enabled project-wide.</p>
 *
 * <p>Audit logging: these are read-only inquiry endpoints.  The project's audit
 * system is service-call based ({@code AuditLogService}) and read-only endpoints
 * are exempt from audit logging — no {@code AuditLogService} call is needed here.</p>
 */
@Validated
@RestController
@RequestMapping("/api/v1/banking")
@RequiredArgsConstructor
public class ExchangeRateController {

    private final ExchangeRateService exchangeRateService;

    /**
     * List exchange rates for the authenticated tenant.
     *
     * <pre>
     * GET /api/v1/banking/exchange-rates
     *   ?ref_date=20250702   (optional — defaults to today Vietnam time)
     *   &bank_cd=11704088    (optional — defaults to Shinhan Bank)
     *   &seq_no=2            (optional — "1" morning, "2" afternoon; omit → latest)
     * </pre>
     */
    @GetMapping("/exchange-rates")
    public ApiResponse<ExchangeRateListResponse> list(
            @RequestParam(value = "ref_date", required = false)
            @Pattern(regexp = "^[0-9]{8}$", message = "ref_date must be 8 digits in YYYYMMDD format")
            String refDate,

            @RequestParam(value = "bank_cd", required = false)
            @Size(max = 20, message = "bank_cd must not exceed 20 characters")
            String bankCd,

            @RequestParam(value = "seq_no", required = false)
            @Pattern(regexp = "^[0-9]{1,2}$", message = "seq_no must be a 1-2 digit number")
            String seqNo
    ) {
        ExchangeRateListResponse response = exchangeRateService.list(refDate, bankCd, seqNo);
        return ApiResponse.ok(response);
    }

    /**
     * List bank codes that have exchange rate data for the authenticated tenant.
     *
     * <pre>
     * GET /api/v1/banking/exchange-rates/banks
     * </pre>
     */
    @GetMapping("/exchange-rates/banks")
    public ApiResponse<List<ExchangeRateBankItem>> listBanks() {
        return ApiResponse.ok(exchangeRateService.listBanks());
    }

    /**
     * List all distinct display-currency codes that have ever had exchange rate
     * data for the authenticated tenant.
     *
     * <pre>
     * GET /api/v1/banking/exchange-rates/currencies
     * </pre>
     *
     * <p>The frontend uses this to build the full currency row list so every
     * known currency always appears in the table, even when no rates exist for
     * the currently selected date or bank.</p>
     *
     * <p>Response example:
     * <pre>{@code
     * {
     *   "status": { "error": false, "code": "WBI0000", "message": "Success" },
     *   "data": ["AUD", "CAD", "EUR", "GBP", "HKD", "JPY", "KRW", "SGD", "USD"]
     * }
     * }</pre>
     */
    @GetMapping("/exchange-rates/currencies")
    public ApiResponse<List<String>> listCurrencies() {
        return ApiResponse.ok(exchangeRateService.listCurrencies());
    }


}
