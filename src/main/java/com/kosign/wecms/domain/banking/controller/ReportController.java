package com.kosign.wecms.domain.banking.controller;

import com.kosign.wecms.common.response.ApiResponse;
import com.kosign.wecms.domain.banking.dto.DailyCashReport;
import com.kosign.wecms.domain.banking.dto.DailyFinancialCmcReport;
import com.kosign.wecms.domain.banking.dto.DailyFinancialHsEntryUpdate;
import com.kosign.wecms.domain.banking.dto.DailyFinancialHsReport;
import com.kosign.wecms.domain.banking.dto.DailyFinancialTransactionsReport;
import com.kosign.wecms.domain.banking.service.DailyFinancialHsDownloadService;
import com.kosign.wecms.domain.banking.service.DailyFinancialHsWriteService;
import com.kosign.wecms.domain.banking.service.ReportService;
import com.kosign.wecms.domain.banking.service.ReportsDownloadService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
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
    /** PO-requested template-populated Daily Financial Excel Download — see
     *  {@code docs/audits/daily-cash-daily-financial-excel-download-audit.md}. */
    private final ReportsDownloadService reportsDownloadService;
    /** Per-field editable-cell save for the HS ledger (legacy rept_0017_001_u001). */
    private final DailyFinancialHsWriteService dailyFinancialHsWriteService;
    /** Programmatic POI download for the HS ledger (legacy rept_0017_001_r002_excel). */
    private final DailyFinancialHsDownloadService dailyFinancialHsDownloadService;

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

    /**
     * Per-field save of an HS ledger row's editable cells — ports legacy
     * {@code rept_0017_001_u001} (auto-save on blur/Enter). The frontend PATCHes
     * only the changed column(s); the row is keyed by {@code acctSeqNo} in the
     * path plus the JWT tenant. Writes {@code REPT_TRNS_HSTR}; unknown row →
     * {@code WBE_DAILY_FINANCIAL_HS_ENTRY_NOT_FOUND}.
     *
     * @param acctSeqNo {@code ACCT_SEQ_NO} of the target row
     * @param body      partial update (rept_cd / pymn_cd / desc_nm_en /
     *                  desc_nm_ko / memo); {@code null} = unchanged, {@code ""} = clear
     */
    @PatchMapping("/daily-financial-hs/entries/{acctSeqNo}")
    @PreAuthorize("@menuAuthz.canAccess('banking-reports-daily-financial-hs')")
    public ApiResponse<Void> updateDailyFinancialHsEntry(
            @PathVariable String acctSeqNo,
            @Valid @RequestBody DailyFinancialHsEntryUpdate body) {
        dailyFinancialHsWriteService.updateEntry(acctSeqNo, body);
        return ApiResponse.ok(null);
    }

    /**
     * Excel Download for Daily Financial (HS) — ports legacy
     * {@code rept_0017_001_r002_excel}. Re-runs {@link ReportService#dailyFinancialHs}
     * (so the workbook reflects the saved DB data, never unsaved screen edits)
     * and streams a programmatically-built 12-column bank-grouped ledger.
     *
     * @param asOfDt        {@code yyyyMMdd} period date (blank → today)
     * @param accountFilter {@code ALL} (default) or a JSON bank/account array
     * @param lang          optional UI locale (EN/KO/VI; KM/other → EN)
     */
    @GetMapping("/daily-financial-hs/report")
    @PreAuthorize("@menuAuthz.canAccess('banking-reports-daily-financial-hs')")
    public ResponseEntity<ByteArrayResource> downloadDailyFinancialHsReport(
            @RequestParam(value = "asOf", required = false) String asOfDt,
            @RequestParam(value = "accountFilter", required = false, defaultValue = "ALL") String accountFilter,
            @RequestParam(value = "lang", required = false) String lang) {
        ReportsDownloadService.GeneratedReport report =
                dailyFinancialHsDownloadService.generate(asOfDt, accountFilter, lang);
        return DailyCashReportController.reportResponse(report);
    }

    /**
     * Excel Download for Daily Financial (CMC) — the PO-requested
     * template-populated {@code .xlsx} report. Loads the locale template from
     * the backend classpath, populates the Current + Savings account sections
     * (plus each section's per-currency Total rows) preserving all template
     * styling, and streams {@code Daily Financial.xlsx} / {@code 자금일보.xlsx} /
     * {@code Báo cáo tài chính hàng ngày.xlsx}.
     *
     * <p>Dedicated report endpoint — NOT a generic export. Reuses
     * {@link ReportService#dailyFinancial} so the download honours the same
     * filters as the on-screen report ({@code activeAccountsOnly} /
     * {@code includeInternalTransfers} / currency scope).
     *
     * @param asOfDt                   {@code yyyyMMdd} report date (blank → today)
     * @param currencyScope            {@code ALL}/{@code VND}/{@code FCY}; blank → ALL
     * @param activeAccountsOnly       DF-3 filter (screen default true)
     * @param includeInternalTransfers DF-4 toggle (screen default true)
     * @param lang                     optional UI locale (EN/KO/VI; KM/other → EN)
     */
    @GetMapping("/daily-financial/report")
    @PreAuthorize("@menuAuthz.canAccess('banking-reports-daily-financial')")
    public ResponseEntity<ByteArrayResource> downloadDailyFinancialReport(
            @RequestParam(value = "asOf", required = false) String asOfDt,
            @RequestParam(value = "currencyScope", required = false) String currencyScope,
            @RequestParam(value = "activeAccountsOnly", required = false, defaultValue = "true") boolean activeAccountsOnly,
            @RequestParam(value = "includeInternalTransfers", required = false, defaultValue = "true") boolean includeInternalTransfers,
            @RequestParam(value = "lang", required = false) String lang) {
        ReportsDownloadService.GeneratedReport report =
                reportsDownloadService.generateDailyFinancial(
                        asOfDt, currencyScope, activeAccountsOnly, includeInternalTransfers, lang);
        // Reuse the shared XLSX binary-response builder (RFC 5987 filename,
        // OOXML MIME, nosniff) from the sibling report controller.
        return DailyCashReportController.reportResponse(report);
    }

}
