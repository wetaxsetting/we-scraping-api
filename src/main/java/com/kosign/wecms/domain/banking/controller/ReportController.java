package com.kosign.wecms.domain.banking.controller;

import com.kosign.wecms.common.response.ApiResponse;
import com.kosign.wecms.domain.banking.dto.DailyCashReport;
import com.kosign.wecms.domain.banking.dto.DailyFinancialCmcReport;
import com.kosign.wecms.domain.banking.dto.DailyFinancialHsReport;
import com.kosign.wecms.domain.banking.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/banking/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @GetMapping("/daily-cash")
    public ApiResponse<DailyCashReport> dailyCash(
            @RequestParam(value = "asOf", required = false) String asOfDt,
            @RequestParam(value = "currencyScope", required = false) String currencyScope) {
        return ApiResponse.ok(reportService.dailyCash(asOfDt, currencyScope));
    }

    @GetMapping("/daily-financial")
    public ApiResponse<DailyFinancialCmcReport> dailyFinancial(
            @RequestParam(value = "asOf", required = false) String asOfDt,
            @RequestParam(value = "currencyScope", required = false) String currencyScope,
            @RequestParam(value = "activeAccountsOnly", required = false, defaultValue = "true") boolean activeAccountsOnly,
            @RequestParam(value = "includeInternalTransfers", required = false, defaultValue = "true") boolean includeInternalTransfers) {
        return ApiResponse.ok(reportService.dailyFinancial(asOfDt, currencyScope, activeAccountsOnly, includeInternalTransfers));
    }

    /** STUB — see ReportService#dailyFinancialHs. */
    @GetMapping("/daily-financial-hs")
    public ApiResponse<DailyFinancialHsReport> dailyFinancialHs(
            @RequestParam(value = "asOf", required = false) String asOfDt,
            @RequestParam(value = "bankCd", required = false) String bankCd) {
        return ApiResponse.ok(reportService.dailyFinancialHs(asOfDt, bankCd));
    }

}
