package com.kosign.wecms.domain.banking.controller;

import com.kosign.wecms.common.export.ExcelExportRequest;
import com.kosign.wecms.common.export.ExcelExportService;
import com.kosign.wecms.common.response.ApiResponse;
import com.kosign.wecms.domain.banking.dto.AccountListResponse;
import com.kosign.wecms.domain.banking.dto.TransactionListResponse;
import com.kosign.wecms.domain.banking.service.AccountFilter;
import com.kosign.wecms.domain.banking.service.AccountService;
import com.kosign.wecms.domain.banking.service.TransactionService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/banking")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;
    private final TransactionService transactionService;
    private final ExcelExportService excelExportService;

    /**
     * Account Inquiry — VND/FCY share one endpoint with a {@code currency}
     * query param ({@code VND}, {@code FCY}, {@code ALL}, or a literal
     * code). Mirrors legacy {@code fnsh_0001_001_r001} / {@code fnsh_0005_001_r001}.
     *
     * <p>Common-sort parameters {@code sortField} (View Setting {@code
     * FIELD_ID} — e.g. {@code BANK}, {@code BALANCE}) and {@code
     * sortDirection} ({@code ASC} / {@code DESC}) are validated against
     * the {@link com.kosign.wecms.domain.banking.service.AccountSortField}
     * whitelist; unknown / blank values fall back to the legacy default
     * ORDER BY ({@code PRN_SEQ ASC, BANK_CD ASC, ACCT_NO ASC}).
     *
     * <p>Server-side filters (Sprint 2):
     * <ul>
     *   <li>{@code bankCd} — exact-match {@code BANK_CD} (mirrors the
     *       JEX {@code BANK_CD} input). Blank / omitted = no bank filter.</li>
     *   <li>{@code keyword} — case-insensitive partial match on
     *       {@code ACCT_NO}, {@code ACCT_NICK_NM}, {@code ACCT_NM}
     *       (mirrors the JEX {@code SRCH_WD} input).</li>
     *   <li>{@code page} — zero-based page index (default {@code 0}).</li>
     *   <li>{@code size} — rows per page; defaults to
     *       {@link AccountService#DEFAULT_PAGE_SIZE}, clamped to
     *       {@link AccountService#MAX_PAGE_SIZE}.</li>
     * </ul>
     *
     * <p>Per-currency totals + scalar {@code BALANCE}/{@code AVAILABLE_BAL}
     * are computed over the FULL filtered result, not the page slice —
     * the summary card stays stable across pagination.
     */
    @GetMapping("/accounts")
    public ApiResponse<AccountListResponse> listAccounts(
            @RequestParam(value = "currency",      required = false) String currency,
            @RequestParam(value = "bankCd",        required = false) String bankCd,
            @RequestParam(value = "keyword",       required = false) String keyword,
            @RequestParam(value = "sortField",     required = false) String sortField,
            @RequestParam(value = "sortDirection", required = false) String sortDirection,
            @RequestParam(value = "page", defaultValue = "0")  int page,
            @RequestParam(value = "size", defaultValue = "0")  int size) {
        AccountFilter filter = new AccountFilter(currency, bankCd, keyword);
        return ApiResponse.ok(accountService.list(filter, sortField, sortDirection, page, size));
    }

    /**
     * Excel export for Account Inquiry — shared common pipeline (see
     * {@link com.kosign.wecms.common.export.ExcelExportService}). The
     * controller is intentionally thin: validate the body, hand it to
     * the service, return nothing (the service streams XLSX bytes
     * straight into {@code response}).
     *
     * <p>Visible / ordered columns, current filters, and current sort
     * all flow through the request body; the service intersects the
     * column list with the persisted View Setting visibility for the
     * caller and re-runs {@link AccountService#list} so export and list
     * share one query path. {@code POST} is used (not {@code GET}) so
     * the column spec + filter state can travel as a JSON body without
     * query-string length limits.
     */
    @PostMapping("/accounts/export")
    public void exportAccounts(
            @Valid @RequestBody ExcelExportRequest request,
            HttpServletResponse response) {
        excelExportService.exportTo(response, request);
    }

    /**
     * Per-account transaction history. {@code acctSeqNo} is the synthetic
     * {@code BANK_CD + "_" + ACCT_NO} identifier emitted by Account Inquiry.
     * Mirrors legacy {@code fnsh_0002_001_r001} / {@code fnsh_0006_001_r001}.
     */
    @GetMapping("/accounts/{acctSeqNo}/transactions")
    public ApiResponse<TransactionListResponse> listTransactions(
            @PathVariable("acctSeqNo") String acctSeqNo,
            @RequestParam(value = "from", required = false) String fromDt,
            @RequestParam(value = "to",   required = false) String toDt,
            @RequestParam(value = "type", required = false) String type) {
        return ApiResponse.ok(transactionService.list(acctSeqNo, fromDt, toDt, type));
    }
}
