package com.kosign.wecms.domain.banking.controller;

import com.kosign.wecms.common.export.ExcelExportRequest;
import com.kosign.wecms.common.export.ExcelExportService;
import com.kosign.wecms.common.response.ApiResponse;
import com.kosign.wecms.domain.banking.dto.DailyCashReportResponse;
import com.kosign.wecms.domain.banking.service.DailyCashCurrencyFilter;
import com.kosign.wecms.domain.banking.service.DailyCashReportService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Daily Cash report (refactor route slug {@code daily-cash-report}).
 *
 * Replaces the legacy WSVC {@code rept_0009_001_r001}. See
 * {@code docs/refactor-wecms/features/daily-cash-report.md}.
 *
 * <p>F-3 Phase B-1: gated by {@code @menuAuthz.canAccess('banking-reports-daily-cash')}
 * (Daily Cash is an F/M product-tree leaf — H is denied). The export endpoint
 * shares the same cd as the report it serialises.
 */
@RestController
@RequestMapping("/api/v1/banking/reports/daily-cash-report")
@RequiredArgsConstructor
@Validated
public class DailyCashReportController {

    private final DailyCashReportService service;
    private final ExcelExportService excelExportService;

    @GetMapping
    @PreAuthorize("@menuAuthz.canAccess('banking-reports-daily-cash')")
    public ApiResponse<DailyCashReportResponse> search(
            @RequestParam("asOfDate")
            @NotBlank
            @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$") String asOfDate,
            @RequestParam(value = "currency", required = false) String currency) {
        return ApiResponse.ok(service.search(asOfDate, DailyCashCurrencyFilter.parse(currency)));
    }

    @PostMapping("/export")
    @PreAuthorize("@menuAuthz.canAccess('banking-reports-daily-cash')")
    public void export(@Valid @RequestBody ExcelExportRequest request, HttpServletResponse response) {
        excelExportService.exportTo(response, request);
    }
}
