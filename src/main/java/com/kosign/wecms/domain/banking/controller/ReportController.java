package com.kosign.wecms.domain.banking.controller;

import com.kosign.wecms.common.response.ApiResponse;
import com.kosign.wecms.domain.banking.dto.DailyCashReport;
import com.kosign.wecms.domain.banking.dto.DailyFinancialCmcReport;
import com.kosign.wecms.domain.banking.dto.DailyFinancialHsReport;
import com.kosign.wecms.domain.banking.dto.DailyFinancialTransactionsReport;
import com.kosign.wecms.domain.banking.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Banking Report endpoints.
 *
 * <p>F-3 Phase B-1: every endpoint is gated server-side by
 * {@code @menuAuthz.canAccess('<permission_cd>')} (see {@link
 * com.kosign.wecms.domain.navigation.service.MenuAuthorizationGuard}). The cd
 * mirrors the navigation product-tree leaf so the guard's allow/deny matches
 * exactly what the nav would or wouldn't render:
 * <ul>
 *   <li>Daily Cash → {@code banking-reports-daily-cash} (F/M only — not in H's tree)</li>
 *   <li>Daily Financial (CMC) + its per-account transactions popup →
 *       {@code banking-reports-daily-financial} (F/M)</li>
 *   <li>Daily Financial HS → {@code banking-reports-daily-financial-hs} (H)</li>
 * </ul>
 * An H caller is therefore denied Daily Cash and the standard Daily Financial;
 * an F/M caller is denied the HS report. Admins still pass the product-tree gate
 * but skip the per-user grant; normal users need the {@code USER_MENU_AUTH_INFM}
 * grant for the cd.
 */
@RestController
@RequestMapping("/api/v1/banking/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @GetMapping("/daily-cash")
    @PreAuthorize("@menuAuthz.canAccess('banking-reports-daily-cash')")
    public ApiResponse<DailyCashReport> dailyCash(
            @RequestParam(value = "asOf", required = false) String asOfDt,
            @RequestParam(value = "currencyScope", required = false) String currencyScope) {
        return ApiResponse.ok(reportService.dailyCash(asOfDt, currencyScope));
    }

    @GetMapping("/daily-financial")
    @PreAuthorize("@menuAuthz.canAccess('banking-reports-daily-financial')")
    public ApiResponse<DailyFinancialCmcReport> dailyFinancial(
            @RequestParam(value = "asOf", required = false) String asOfDt,
            @RequestParam(value = "currencyScope", required = false) String currencyScope,
            @RequestParam(value = "activeAccountsOnly", required = false, defaultValue = "true") boolean activeAccountsOnly,
            @RequestParam(value = "includeInternalTransfers", required = false, defaultValue = "true") boolean includeInternalTransfers) {
        return ApiResponse.ok(reportService.dailyFinancial(asOfDt, currencyScope, activeAccountsOnly, includeInternalTransfers));
    }

    /**
     * Per-account transaction history popup for Daily Financial — mirrors
     * legacy {@code rept_0015_002_r001}. Opens when the user clicks
     * Bank / Account No. / Account Nickname on the parent ledger.
     */
    @GetMapping("/daily-financial/transactions")
    @PreAuthorize("@menuAuthz.canAccess('banking-reports-daily-financial')")
    public ApiResponse<DailyFinancialTransactionsReport> dailyFinancialTransactions(
            // `asOf` wire param maps to `asOfDt` Java arg — names diverge,
            // explicit @RequestParam("asOf") is required.
            @RequestParam("asOf") String asOfDt,
            @RequestParam String bankCd,
            @RequestParam String acctNo,
            @RequestParam(required = false, defaultValue = "true") boolean includeInternalTransfers,
            @RequestParam(required = false, defaultValue = "1") int page,
            @RequestParam(required = false, defaultValue = "15") int size) {
        return ApiResponse.ok(reportService.dailyFinancialTransactions(
                asOfDt, bankCd, acctNo, includeInternalTransfers, page, size));
    }

    /**
     * HAENGSUNG Daily Financial report (legacy {@code rept_0017_001}) — real
     * DB-backed via {@code fn_get_haengsung_daily_financial_report}.
     *
     * @param asOfDt        {@code yyyyMMdd} period date (blank → today)
     * @param accountFilter {@code ALL} (default) or a JSON array of
     *                      {@code {"BANK_CD":..,"ACCT_NO":..}} bank selections
     */
    @GetMapping("/daily-financial-hs")
    @PreAuthorize("@menuAuthz.canAccess('banking-reports-daily-financial-hs')")
    public ApiResponse<DailyFinancialHsReport> dailyFinancialHs(
            @RequestParam(value = "asOf", required = false) String asOfDt,
            @RequestParam(value = "accountFilter", required = false, defaultValue = "ALL") String accountFilter) {
        return ApiResponse.ok(reportService.dailyFinancialHs(asOfDt, accountFilter));
    }

}
