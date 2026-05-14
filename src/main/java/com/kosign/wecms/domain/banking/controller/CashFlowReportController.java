package com.kosign.wecms.domain.banking.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.export.ExcelExportRequest;
import com.kosign.wecms.common.export.ExcelExportService;
import com.kosign.wecms.common.response.ApiResponse;
import com.kosign.wecms.domain.banking.dto.BankFilter;
import com.kosign.wecms.domain.banking.dto.CashFlowReportResponse;
import com.kosign.wecms.domain.banking.service.CashFlowReportService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/**
 * Cash Flow report (refactor route slug {@code cash-flow-report}).
 * Replaces legacy WSVC {@code cash_0001_001_r001}. See
 * {@code docs/refactor-wecms/features/cash-flow-report.md}.
 *
 * <p>The legacy WSVC ships {@code login=N} (public); the refactor
 * requires authentication via the default JWT path. Documented
 * divergence — Sonny override, 2026-05-13.
 */
@RestController
@RequestMapping("/api/v1/banking/reports/cash-flow-report")
@RequiredArgsConstructor
@Validated
public class CashFlowReportController {

    private final CashFlowReportService service;
    private final ExcelExportService excelExportService;
    private final ObjectMapper objectMapper;

    @GetMapping
    public ApiResponse<CashFlowReportResponse> search(
            @RequestParam("year") int year,
            @RequestParam(value = "quarter", required = false) Integer quarter,
            @RequestParam("baseCurrency") String baseCurrency,
            @RequestParam(value = "usdRate", required = false) BigDecimal usdRate,
            @RequestParam(value = "krwRate", required = false) BigDecimal krwRate,
            @RequestParam(value = "bankFilters", required = false) String bankFiltersJson) {
        List<BankFilter> bankFilters = parseBankFilters(bankFiltersJson);
        return ApiResponse.ok(service.search(year, quarter, baseCurrency, usdRate, krwRate, bankFilters));
    }

    @PostMapping("/export")
    public void export(@Valid @RequestBody ExcelExportRequest request, HttpServletResponse response) {
        excelExportService.exportTo(response, request);
    }

    private List<BankFilter> parseBankFilters(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<BankFilter>>() {});
        } catch (Exception e) {
            throw new BusinessException("WBE_VALIDATION", "bankFilters: malformed JSON");
        }
    }
}
