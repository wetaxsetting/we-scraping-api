package com.kosign.wecms.domain.banking.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.export.ExcelExportRequest;
import com.kosign.wecms.common.response.ApiResponse;
import com.kosign.wecms.domain.banking.dto.BankFilter;
import com.kosign.wecms.domain.banking.dto.CashFlowReportResponse;
import com.kosign.wecms.domain.banking.export.CashFlowExcelWriter;
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
import java.util.Map;

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
    private final CashFlowExcelWriter excelWriter;
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
        // Cash Flow uses a dedicated POI writer (not the generic
        // ExcelExportService) so the workbook can produce the legacy
        // grouped layout — merged L1/L2 cells with rowSpan, colored
        // subtotal bands, two-row header. The request's filter map
        // drives the data fetch; columns / sort fields from the
        // generic shape are intentionally ignored — this writer owns
        // the output structure end-to-end.
        Map<String, Object> filters = request.filters() != null ? request.filters() : Map.of();
        int year = parseInt(filters.get("year"), 2026);
        Integer quarter = parseQuarter(filters.get("quarter"));
        String baseCurrency = string(filters.get("baseCurrency"), "VND");
        BigDecimal usdRate = parseDecimal(filters.get("usdRate"));
        BigDecimal krwRate = parseDecimal(filters.get("krwRate"));
        List<BankFilter> bankFilters = parseBankFilters(string(filters.get("bankFilters"), ""));

        CashFlowReportResponse report = service.search(
                year, quarter, baseCurrency, usdRate, krwRate, bankFilters);

        excelWriter.write(response, report, request.fileName());
    }

    private static String string(Object raw, String fallback) {
        if (raw == null) return fallback;
        String s = raw.toString().trim();
        return s.isEmpty() ? fallback : s;
    }

    private static int parseInt(Object raw, int fallback) {
        if (raw == null) return fallback;
        try { return Integer.parseInt(raw.toString().trim()); }
        catch (NumberFormatException e) { return fallback; }
    }

    private static Integer parseQuarter(Object raw) {
        if (raw == null) return null;
        try {
            int q = Integer.parseInt(raw.toString().trim());
            return q >= 1 && q <= 4 ? q : null;
        } catch (NumberFormatException e) { return null; }
    }

    private static BigDecimal parseDecimal(Object raw) {
        if (raw == null) return null;
        try { return new BigDecimal(raw.toString().trim()); }
        catch (NumberFormatException e) { return null; }
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
