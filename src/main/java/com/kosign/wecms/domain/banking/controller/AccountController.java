package com.kosign.wecms.domain.banking.controller;

import com.kosign.wecms.common.export.ExcelExportRequest;
import com.kosign.wecms.common.export.ExcelExportService;
import com.kosign.wecms.common.response.ApiResponse;
import com.kosign.wecms.domain.banking.dto.AccountListResponse;
import com.kosign.wecms.domain.banking.dto.AccountLookupListResponse;
import com.kosign.wecms.domain.banking.dto.RegisterAccountsRequest;
import com.kosign.wecms.domain.banking.dto.RegisterAccountsResponse;
import com.kosign.wecms.domain.banking.dto.CreateMatchRequest;
import com.kosign.wecms.domain.banking.dto.MatchCandidateListResponse;
import com.kosign.wecms.domain.banking.dto.MatchResultResponse;
import com.kosign.wecms.domain.banking.dto.TransactionConfirmRequest;
import com.kosign.wecms.domain.banking.dto.TransactionDetailResponse;
import com.kosign.wecms.domain.banking.dto.TransactionListResponse;
import com.kosign.wecms.domain.banking.dto.TransactionSummaryResponse;
import com.kosign.wecms.domain.banking.dto.TransactionsSyncRequest;
import com.kosign.wecms.domain.banking.dto.TransactionsSyncResponse;
import com.kosign.wecms.domain.banking.nxisas.BankTransactionSyncService;
import com.kosign.wecms.domain.banking.nxisas.dto.SubmitChallengeRequest;
import com.kosign.wecms.domain.banking.nxisas.dto.SubmitQnaRequest;
import com.kosign.wecms.domain.banking.service.AccountFilter;
import com.kosign.wecms.domain.banking.service.AccountRegistrationService;
import com.kosign.wecms.domain.banking.service.AccountService;
import com.kosign.wecms.domain.banking.service.MatchService;
import com.kosign.wecms.domain.banking.service.TransactionService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/banking")
@RequiredArgsConstructor
@Validated
public class AccountController {

    private final AccountService accountService;
    private final TransactionService transactionService;
    private final MatchService matchService;
    private final ExcelExportService excelExportService;
    private final AccountRegistrationService accountRegistrationService;
    private final BankTransactionSyncService bankTransactionSyncService;

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
     * Register the accounts a user picked in the Select Bank Accounts
     * dialog after a successful NXISAS scrape. Mirrors legacy
     * {@code fnsh_0101_001_c001} (comm_0218_nxisas.ts:1188): writes
     * one row per picked account into {@code ACCT_MSTR} under the
     * current tenant and the bank resolved from
     * {@code BANK_CERT_INFO.SEQ_NO}.
     *
     * <p>Idempotent — re-registering the same {@code (BANK_CD, ACCT_NO)}
     * is a no-op (skipped count on the response). When ALL picked rows
     * are duplicates, the service raises
     * {@code WBE_BANK_ACCOUNT_REGISTER_ALL_DUP} so the dialog can
     * surface a meaningful message and the user can pick different
     * accounts.
     */
    @PostMapping("/accounts/register")
    public ApiResponse<RegisterAccountsResponse> registerAccounts(
            @Valid @RequestBody RegisterAccountsRequest request) {
        return ApiResponse.ok(accountRegistrationService.register(request));
    }

    /**
     * Bank Transaction Sync — Account Inquiry &gt; Update popup.
     *
     * <p>Drives the real Coocon NXISAS scrape: per-account LOGIN
     * → (challenge?) → TRANSACTION → BALANCE → dedup-INSERT into
     * {@code ACCT_TRNS_HSTR} + refresh {@code ACCT_MSTR.BAL/REAL_AMT}.
     * When a bank LOGIN returns an OTP / QNA / CAPTCHA challenge, the
     * orchestrator persists the in-flight batch on a sync session and
     * returns a {@code *_REQUIRED} response; the frontend opens the
     * matching dialog, calls one of the resume endpoints below with
     * the session id, and the orchestrator picks the loop back up.
     *
     * <p>Mirrors legacy {@code fnsh_0001_001_c001_act.jsp} for the
     * LOGIN → TRANSACTION → BALANCE chain. Smart-Assist auto-match and
     * push-notification dispatch (the rest of the legacy pipeline) are
     * still out of scope.
     */
    @PostMapping("/accounts/transactions-sync")
    public ApiResponse<TransactionsSyncResponse> syncTransactions(
            @Valid @RequestBody TransactionsSyncRequest request) {
        return ApiResponse.ok(accountService.syncTransactions(request));
    }

    /** Resume a sync after a security-question (QNA) answer. The
     *  frontend echoes back the {@code session_id} the
     *  {@code QNA_REQUIRED} response carried plus the chosen
     *  question + user's answer. */
    @PostMapping("/accounts/transactions-sync/{sessionId}/qna")
    public ApiResponse<TransactionsSyncResponse> submitSyncQna(
            @PathVariable java.util.UUID sessionId,
            @Valid @RequestBody SubmitQnaRequest request) {
        return ApiResponse.ok(bankTransactionSyncService.submitQna(sessionId, request));
    }

    /** Resume a sync after an OTP submission. */
    @PostMapping("/accounts/transactions-sync/{sessionId}/otp")
    public ApiResponse<TransactionsSyncResponse> submitSyncOtp(
            @PathVariable java.util.UUID sessionId,
            @Valid @RequestBody SubmitChallengeRequest request) {
        return ApiResponse.ok(bankTransactionSyncService.submitOtp(sessionId, request));
    }

    /** Resume a sync after a CAPTCHA submission. */
    @PostMapping("/accounts/transactions-sync/{sessionId}/captcha")
    public ApiResponse<TransactionsSyncResponse> submitSyncCaptcha(
            @PathVariable java.util.UUID sessionId,
            @Valid @RequestBody SubmitChallengeRequest request) {
        return ApiResponse.ok(bankTransactionSyncService.submitCaptcha(sessionId, request));
    }

    /**
     * Withdrawal-account autocomplete for the firm-banking-settings
     * Create dialog. Mirrors the legacy {@code comm_1004_001} read
     * path: resolve the public NAPAS code (e.g. {@code 970424}) to
     * the internal {@code BANK_CD} via {@code NAPAS_BANK_INFO}, then
     * return every {@code ACCT_MSTR} row for the tenant under that
     * bank.
     *
     * <p>Wire shape per row: {@code acct_no} (raw), {@code
     * acct_no_masked} (server-masked via {@code LogMask.acct}),
     * {@code ccy_cd}, {@code acct_nick_nm} (nullable),
     * {@code acct_nm} (nullable). The frontend dropdown renders
     * {@code <formatted-acct> (<ccy>)} per legacy parity; the
     * masked variant lets the dropdown chip / list-item display
     * the same string the audit log records without a second
     * round-trip.
     *
     * <p>{@code outBankCd} is the only filter — the legacy
     * {@code comm_1004_001} loads the full per-bank list and the
     * frontend filters client-side as the user types. The refactor
     * mirrors that contract — no server-side keyword param, no
     * pagination, no debounce required.
     */
    @GetMapping("/accounts/by-bank")
    public ApiResponse<AccountLookupListResponse> lookupAccountsByBank(
            @RequestParam("out_bank_cd")
            @Size(max = 15)
            @Pattern(regexp = "^[0-9]{1,15}$",
                    message = "out_bank_cd: invalid format")
            String outBankCd) {
        return ApiResponse.ok(accountService.lookupForFirmBanking(outBankCd));
    }

    /**
     * Per-account transaction history. {@code acctSeqNo} is the synthetic
     * {@code BANK_CD + "_" + ACCT_NO} identifier emitted by Account Inquiry.
     * Mirrors legacy {@code fnsh_0002_001_r001} / {@code fnsh_0006_001_r001}.
     */
    @GetMapping("/accounts/{acctSeqNo}/transactions")
    public ApiResponse<TransactionListResponse> listTransactions(
            @PathVariable("acctSeqNo") String acctSeqNo,
            @RequestParam(value = "from",     required = false) String fromDt,
            @RequestParam(value = "to",       required = false) String toDt,
            @RequestParam(value = "type",     required = false) String type,
            @RequestParam(value = "cnfm_yn",  required = false) String cnfmYn,
            @RequestParam(value = "keyword",  required = false) String keyword) {
        return ApiResponse.ok(transactionService.list(acctSeqNo, fromDt, toDt, type, cnfmYn, keyword));
    }

    /**
     * Summary aggregates for the current Transaction History filter set —
     * total count, confirmed / unconfirmed split, and deposit / withdrawal
     * totals. Mirrors legacy {@code trns_0001_001} summary strip.
     */
    @GetMapping("/accounts/{acctSeqNo}/transactions/summary")
    public ApiResponse<TransactionSummaryResponse> transactionsSummary(
            @PathVariable("acctSeqNo") String acctSeqNo,
            @RequestParam(value = "from",     required = false) String fromDt,
            @RequestParam(value = "to",       required = false) String toDt,
            @RequestParam(value = "type",     required = false) String type,
            @RequestParam(value = "cnfm_yn",  required = false) String cnfmYn,
            @RequestParam(value = "keyword",  required = false) String keyword) {
        return ApiResponse.ok(transactionService.summary(acctSeqNo, fromDt, toDt, type, cnfmYn, keyword));
    }

    /**
     * Single-transaction detail — VAS account, counterparty, confirmation
     * metadata in addition to the base fields the list endpoint returns.
     * {@code trnsSeqNo} is the {@code ACCT_SEQ_NO} surrogate from the list row.
     */
    @GetMapping("/accounts/{acctSeqNo}/transactions/{trnsSeqNo}")
    public ApiResponse<TransactionDetailResponse> transactionDetail(
            @PathVariable("acctSeqNo") String acctSeqNo,
            @PathVariable("trnsSeqNo") String trnsSeqNo) {
        return ApiResponse.ok(transactionService.detail(acctSeqNo, trnsSeqNo));
    }

    /**
     * Confirm a transaction — writes {@code ACCT_TRNS_RCV_PAY_INFM} with
     * {@code CNFM_YN='Y'} plus optional classification fields (counterparty,
     * usage code, VAS account). Returns the refreshed detail payload so the
     * frontend can re-render without a second round-trip. Mirrors legacy
     * {@code trns_0001_001_u001}.
     */
    @PutMapping("/accounts/{acctSeqNo}/transactions/{trnsSeqNo}/confirm")
    public ApiResponse<TransactionDetailResponse> confirmTransaction(
            @PathVariable("acctSeqNo") String acctSeqNo,
            @PathVariable("trnsSeqNo") String trnsSeqNo,
            @Valid @RequestBody(required = false) TransactionConfirmRequest request) {
        return ApiResponse.ok(transactionService.confirm(acctSeqNo, trnsSeqNo, request));
    }

    /** Undo a transaction confirmation — flips {@code CNFM_YN} back to {@code 'N'}. */
    @DeleteMapping("/accounts/{acctSeqNo}/transactions/{trnsSeqNo}/confirm")
    public ApiResponse<Void> undoConfirmTransaction(
            @PathVariable("acctSeqNo") String acctSeqNo,
            @PathVariable("trnsSeqNo") String trnsSeqNo) {
        transactionService.undoConfirm(acctSeqNo, trnsSeqNo);
        return ApiResponse.ok(null);
    }

    /**
     * Smart Organizer — list domestic scheduled-payment candidates the
     * user may match the bank transaction to. Mirrors legacy
     * {@code comm_0196_002_r001}. Overseas payments are deferred to a
     * follow-up slice.
     */
    @GetMapping("/accounts/{acctSeqNo}/transactions/{trnsSeqNo}/match-candidates")
    public ApiResponse<MatchCandidateListResponse> listMatchCandidates(
            @PathVariable("acctSeqNo") String acctSeqNo,
            @PathVariable("trnsSeqNo") String trnsSeqNo,
            @RequestParam(value = "from",    required = false) String fromDt,
            @RequestParam(value = "to",      required = false) String toDt,
            @RequestParam(value = "keyword", required = false) String keyword) {
        return ApiResponse.ok(matchService.listCandidates(acctSeqNo, trnsSeqNo, fromDt, toDt, keyword));
    }

    /**
     * Smart Organizer — match the bank transaction to a payment line.
     * Atomically writes {@code BUSI_MATCH_LOG} + flips
     * {@code LINK_MATCH_YN='Y'} on {@code ACCT_TRNS_HSTR} and
     * {@code SCDUL_PYMN_SUMR_DETL}. Mirrors legacy
     * {@code comm_0196_001_c001}.
     */
    @PostMapping("/accounts/{acctSeqNo}/transactions/{trnsSeqNo}/match")
    public ApiResponse<MatchResultResponse> createMatch(
            @PathVariable("acctSeqNo") String acctSeqNo,
            @PathVariable("trnsSeqNo") String trnsSeqNo,
            @Valid @RequestBody CreateMatchRequest request) {
        return ApiResponse.ok(matchService.create(acctSeqNo, trnsSeqNo, request));
    }

    /**
     * Smart Organizer — undo a match (legacy {@code comm_0196_002_u001}).
     * Flips {@code LINK_MATCH_YN='N'} on both sides and
     * {@code LOCKED_YN='N'} on the audit row (preserves history).
     */
    @DeleteMapping("/accounts/{acctSeqNo}/transactions/{trnsSeqNo}/match")
    public ApiResponse<MatchResultResponse> undoMatch(
            @PathVariable("acctSeqNo") String acctSeqNo,
            @PathVariable("trnsSeqNo") String trnsSeqNo) {
        return ApiResponse.ok(matchService.undo(acctSeqNo, trnsSeqNo));
    }

    /**
     * Excel export for Banking &gt; Transaction History. Routes through
     * the shared {@code ExcelExportService} — the body's {@code screenId}
     * must be {@code "banking-transaction-history"} (V128 seed) and
     * {@code filters.acctSeqNo} is required so the export is scoped to
     * one account. Mirrors {@code POST /accounts/export}.
     */
    @PostMapping("/accounts/transactions/export")
    public void exportTransactions(
            @Valid @RequestBody ExcelExportRequest request,
            HttpServletResponse response) {
        excelExportService.exportTo(response, request);
    }
}
