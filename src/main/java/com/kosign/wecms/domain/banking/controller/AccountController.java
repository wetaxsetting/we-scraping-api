package com.kosign.wecms.domain.banking.controller;

import com.kosign.wecms.common.export.ExcelExportRequest;
import com.kosign.wecms.common.export.ExcelExportService;
import com.kosign.wecms.common.memo.dto.MemoListResponse;
import com.kosign.wecms.common.memo.dto.MemoRow;
import com.kosign.wecms.common.response.ApiResponse;
import com.kosign.wecms.domain.banking.dto.TransactionMemoColorRequest;
import com.kosign.wecms.domain.banking.dto.TransactionMemoCreateRequest;
import com.kosign.wecms.domain.banking.dto.TransactionMemoUpdateRequest;
import com.kosign.wecms.domain.banking.dto.AccountListResponse;
import com.kosign.wecms.domain.banking.dto.AccountLookupListResponse;
import com.kosign.wecms.domain.banking.dto.RegisterAccountsRequest;
import com.kosign.wecms.domain.banking.dto.RegisterAccountsResponse;
import com.kosign.wecms.domain.banking.dto.CreateMatchRequest;
import com.kosign.wecms.domain.banking.dto.MatchCandidateListResponse;
import com.kosign.wecms.domain.banking.dto.MatchResultResponse;
import com.kosign.wecms.domain.banking.dto.TransactionConfirmRequest;
import com.kosign.wecms.domain.banking.dto.TransactionContactRequest;
import com.kosign.wecms.domain.banking.dto.TransactionDetailResponse;
import com.kosign.wecms.domain.banking.dto.TransactionPurposeRequest;
import com.kosign.wecms.domain.banking.dto.TransactionReportingAccountRequest;
import com.kosign.wecms.domain.banking.dto.TransactionVasAccountRequest;
import com.kosign.wecms.domain.banking.dto.TransactionListResponse;
import com.kosign.wecms.domain.banking.dto.TransactionSummaryResponse;
import com.kosign.wecms.domain.banking.dto.SyncStatusResponse;
import com.kosign.wecms.domain.banking.dto.TransactionsSyncRequest;
import com.kosign.wecms.domain.banking.nxisas.BankTransactionSyncService;
import com.kosign.wecms.domain.banking.nxisas.dto.SubmitChallengeRequest;
import com.kosign.wecms.domain.banking.nxisas.dto.SubmitQnaRequest;
import com.kosign.wecms.domain.banking.service.AccountFilter;
import com.kosign.wecms.domain.banking.service.AccountRegistrationService;
import com.kosign.wecms.domain.banking.service.AccountService;
import com.kosign.wecms.domain.banking.service.CurrentSavingsDownloadService;
import com.kosign.wecms.domain.banking.service.CurrentSavingsScope;
import com.kosign.wecms.domain.banking.service.MatchService;
import com.kosign.wecms.domain.banking.service.TransactionService;
import com.kosign.wecms.domain.setting.erptransactionhistory.service.ErpTrnsHistoryWriter;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;

import java.math.BigDecimal;

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
    /** PO-requested template-populated Banking Excel Download (Account
     *  Inquiry + Transaction History, VND + FCY) — see
     *  {@code docs/audits/current-savings-vnd-excel-download-audit.md} +
     *  {@code docs/audits/current-savings-fcy-excel-download-audit.md}. */
    private final CurrentSavingsDownloadService currentSavingsDownloadService;
    /** Writes the WeCMS&rarr;ERP 'W' audit row for each report download,
     *  preserving the legacy {@code downloadProc.jsp} side-effect. */
    private final ErpTrnsHistoryWriter erpHistoryWriter;

    /**
     * Deploy
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
    @PreAuthorize("@menuAuthz.canAccessAccountInquiry(#p0)")
    public ApiResponse<AccountListResponse> listAccounts(
            @RequestParam(value = "currency",      required = false) String currency,
            @RequestParam(value = "bankCd",        required = false) String bankCd,
            @RequestParam(value = "keyword",       required = false) String keyword,
            @RequestParam(value = "sortField",     required = false) String sortField,
            @RequestParam(value = "sortDirection", required = false) String sortDirection,
            // AI-01 — legacy as-of-date historical balance (ACCT_MSTR_R004).
            // yyyyMMdd; blank / today / future → current balances (legacy parity).
            @RequestParam(value = "asOfDate",      required = false) String asOfDate,
            @RequestParam(value = "page", defaultValue = "0")  int page,
            @RequestParam(value = "size", defaultValue = "0")  int size) {
        AccountFilter filter = new AccountFilter(currency, bankCd, keyword);
        return ApiResponse.ok(accountService.list(filter, sortField, sortDirection, asOfDate, page, size));
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
    @PreAuthorize("@menuAuthz.canAccessAny('banking-vnd-account-inquiry','banking-fcy-update-inquire')")
    public void exportAccounts(
            @Valid @RequestBody ExcelExportRequest request,
            HttpServletResponse response) {
        excelExportService.exportTo(response, request);
    }

    /**
     * Excel Download for Account Inquiry (VND) — the PO-requested
     * template-populated {@code .xls} report (legacy {@code fnsh_0001_001}
     * localized download). Loads the locale template from the backend
     * classpath, populates one row per account preserving all template
     * styling, and streams {@code Account Inquiry (VND).xls} /
     * {@code 계좌조회 (VND).xls} / {@code Tra cứu tài khoản (VND).xls}.
     *
     * <p>Dedicated report endpoint — deliberately NOT the generic
     * {@code POST /accounts/export} (which builds a workbook from scratch
     * and cannot preserve the template). Preserves the legacy ERP audit
     * side-effect: WeCMS&rarr;ERP {@code TRNS_TYPE_CD = "09"}.
     *
     * @param lang optional UI-locale hint (EN/KO/VI; KM/other → EN); when
     *             omitted the JWT {@code langCd} is used.
     */
    @GetMapping("/accounts/report")
    @PreAuthorize("@menuAuthz.canAccessAny('banking-vnd-account-inquiry','banking-fcy-update-inquire')")
    public ResponseEntity<ByteArrayResource> downloadAccountInquiryReport(
            @RequestParam(value = "lang", required = false) String lang) {
        CurrentSavingsDownloadService.GeneratedReport report =
                currentSavingsDownloadService.generateAccountInquiry(lang);
        erpHistoryWriter.recordExportedBytes("09", report.content(), report.fileName());
        return reportResponse(report);
    }

    /**
     * Excel Download for Account Inquiry (FCY) — the PO-requested
     * template-populated {@code .xls} report (legacy {@code fnsh_0005_001}
     * localized download). Loads the FCY locale template from the backend
     * classpath, populates one row per account preserving all template
     * styling, and streams {@code Account Inquiry (FCY).xls} /
     * {@code 계좌조회 (FCY).xls} / {@code Tra cứu tài khoản (FCY).xls}.
     *
     * <p>Dedicated FCY report endpoint — deliberately NOT the generic
     * {@code POST /accounts/export} and NOT the VND {@code /accounts/report}.
     * Preserves the legacy ERP audit side-effect: WeCMS&rarr;ERP
     * {@code TRNS_TYPE_CD = "10"} ({@code fnsh_0005_001} branch).
     *
     * @param lang     optional UI-locale hint (EN/KO/VI; KM/other → EN); when
     *                 omitted the JWT {@code langCd} is used.
     * @param currency optional on-screen currency-dropdown narrow ({@code FCY}
     *                 for all foreign currencies, or a concrete CCY such as
     *                 {@code USD}/{@code KRW}); blank → all FCY accounts. Mirrors
     *                 the on-screen FCY list scope so download and screen agree.
     */
    @GetMapping("/accounts/report-fcy")
    @PreAuthorize("@menuAuthz.canAccessAny('banking-vnd-account-inquiry','banking-fcy-update-inquire')")
    public ResponseEntity<ByteArrayResource> downloadAccountInquiryFcyReport(
            @RequestParam(value = "lang", required = false) String lang,
            @RequestParam(value = "currency", required = false) String currency) {
        CurrentSavingsDownloadService.GeneratedReport report =
                currentSavingsDownloadService.generateAccountInquiry(
                        CurrentSavingsScope.FCY, currency, lang);
        erpHistoryWriter.recordExportedBytes("10", report.content(), report.fileName());
        return reportResponse(report);
    }

    /**
     * Register the accounts a user picked in the Select Bank Accounts
     * dialog after a successful NXISAS scrape. Mirrors legacy
     * {@code fnsh_0101_001_c001} (comm_0218_nxisas.ts:1188): writes
     * one row per picked account into {@code ACCT_MSTR} under the
     * current tenant and the bank resolved from
     * {@code BANK_CERT_INFO.SEQ_NO}.
     *
     * <p>Upsert — re-registering an existing {@code (BANK_CD, ACCT_NO)}
     * UPDATEs the row (nickname / details / type / balance + credential
     * re-link) instead of erroring, matching legacy
     * {@code fnsh_0101_001_c001}. The response carries
     * {@code (inserted, updated)} counts; the only business error is the
     * empty-payload guard ({@code WBE_BANK_ACCOUNT_REGISTER_EMPTY}).
     */
    @PostMapping("/accounts/register")
    @PreAuthorize("@menuAuthz.canAccessAny('banking-vnd-account-inquiry','banking-fcy-update-inquire')")
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
    @PreAuthorize("@menuAuthz.canAccessAny('banking-vnd-account-inquiry','banking-fcy-update-inquire')")
    public ApiResponse<SyncStatusResponse> syncTransactions(
            @Valid @RequestBody TransactionsSyncRequest request) {
        // Phase 2: returns a RUNNING status snapshot immediately; the scrape
        // runs on the bounded bankSyncExecutor and the frontend polls /status.
        return ApiResponse.ok(accountService.syncTransactions(request));
    }

    /**
     * Poll the async sync status (Phase 2). Tenant-validated, read-only, safe
     * for repeated polling; returns a stable immutable snapshot. Status values:
     * {@code RUNNING} / {@code COMPLETED} / {@code FAILED} / {@code CANCELLED} /
     * {@code OTP_REQUIRED} / {@code QNA_REQUIRED} / {@code CAPTCHA_REQUIRED}.
     */
    @GetMapping("/accounts/transactions-sync/{sessionId}/status")
    @PreAuthorize("@menuAuthz.canAccessAny('banking-vnd-account-inquiry','banking-fcy-update-inquire')")
    public ApiResponse<SyncStatusResponse> syncStatus(
            @PathVariable java.util.UUID sessionId) {
        return ApiResponse.ok(bankTransactionSyncService.status(sessionId));
    }

    /** Resume a sync after a security-question (QNA) answer — schedules a fresh
     *  background segment that continues from the saved continuation. */
    @PostMapping("/accounts/transactions-sync/{sessionId}/qna")
    @PreAuthorize("@menuAuthz.canAccessAny('banking-vnd-account-inquiry','banking-fcy-update-inquire')")
    public ApiResponse<SyncStatusResponse> submitSyncQna(
            @PathVariable java.util.UUID sessionId,
            @Valid @RequestBody SubmitQnaRequest request) {
        return ApiResponse.ok(bankTransactionSyncService.submitQna(sessionId, request));
    }

    /** Resume a sync after an OTP submission. */
    @PostMapping("/accounts/transactions-sync/{sessionId}/otp")
    @PreAuthorize("@menuAuthz.canAccessAny('banking-vnd-account-inquiry','banking-fcy-update-inquire')")
    public ApiResponse<SyncStatusResponse> submitSyncOtp(
            @PathVariable java.util.UUID sessionId,
            @Valid @RequestBody SubmitChallengeRequest request) {
        return ApiResponse.ok(bankTransactionSyncService.submitOtp(sessionId, request));
    }

    /** Resume a sync after a CAPTCHA submission. */
    @PostMapping("/accounts/transactions-sync/{sessionId}/captcha")
    @PreAuthorize("@menuAuthz.canAccessAny('banking-vnd-account-inquiry','banking-fcy-update-inquire')")
    public ApiResponse<SyncStatusResponse> submitSyncCaptcha(
            @PathVariable java.util.UUID sessionId,
            @Valid @RequestBody SubmitChallengeRequest request) {
        return ApiResponse.ok(bankTransactionSyncService.submitCaptcha(sessionId, request));
    }

    /**
     * Cancel a sync (Phase 1, universal in Phase 2). The frontend calls this
     * from the popup's Cancel button / close-while-scraping confirm, echoing
     * the {@code session_id}. The orchestrator sets the cooperative cancel flag
     * (halts the scrape before the next account/phase), best-effort LOGOUTs the
     * bank-adapter session, and marks the session CANCELLED (retained briefly
     * so the final poll can read it; any later resume is refused). Idempotent +
     * tenant-validated in the service — a body is not required.
     */
    @PostMapping("/accounts/transactions-sync/{sessionId}/cancel")
    @PreAuthorize("@menuAuthz.canAccessAny('banking-vnd-account-inquiry','banking-fcy-update-inquire')")
    public ApiResponse<SyncStatusResponse> cancelSync(
            @PathVariable java.util.UUID sessionId) {
        return ApiResponse.ok(bankTransactionSyncService.cancel(sessionId));
    }

    /**
     * Cancel the CURRENT 2nd-cert challenge (QNA / OTP / CAPTCHA) — an
     * account-level operation, NOT a whole-session cancel. The frontend calls
     * this from the QNA/OTP/CAPTCHA popup's Cancel / X (after the "quit the
     * inquiry for {bank}?" confirm). The current login group's account(s) are
     * recorded as {@code LOGIN_CAN} failures and the batch continues with the
     * next login group, finishing normally with a "Success X / Failed Y"
     * summary (legacy {@code nxisas_v2.js} `run_afterLoginFail` / WBC1152).
     * Idempotent + tenant-validated; a body is not required.
     */
    @PostMapping("/accounts/transactions-sync/{sessionId}/challenge-cancel")
    @PreAuthorize("@menuAuthz.canAccessAny('banking-vnd-account-inquiry','banking-fcy-update-inquire')")
    public ApiResponse<SyncStatusResponse> cancelSyncChallenge(
            @PathVariable java.util.UUID sessionId) {
        return ApiResponse.ok(bankTransactionSyncService.cancelChallenge(sessionId));
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
    @PreAuthorize("@menuAuthz.canAccessAny('banking-vnd-account-inquiry','banking-fcy-update-inquire')")
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
    @PreAuthorize("@menuAuthz.canAccessAny('banking-vnd-inquire-transactions','banking-fcy-inquire-transactions')")
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
    @PreAuthorize("@menuAuthz.canAccessAny('banking-vnd-inquire-transactions','banking-fcy-inquire-transactions')")
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
    @PreAuthorize("@menuAuthz.canAccessAny('banking-vnd-inquire-transactions','banking-fcy-inquire-transactions')")
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
    @PreAuthorize("@menuAuthz.canAccessAny('banking-vnd-inquire-transactions','banking-fcy-inquire-transactions')")
    public ApiResponse<TransactionDetailResponse> confirmTransaction(
            @PathVariable("acctSeqNo") String acctSeqNo,
            @PathVariable("trnsSeqNo") String trnsSeqNo,
            @Valid @RequestBody(required = false) TransactionConfirmRequest request) {
        return ApiResponse.ok(transactionService.confirm(acctSeqNo, trnsSeqNo, request));
    }

    /** Undo a transaction confirmation — flips {@code CNFM_YN} back to {@code 'N'}. */
    @DeleteMapping("/accounts/{acctSeqNo}/transactions/{trnsSeqNo}/confirm")
    @PreAuthorize("@menuAuthz.canAccessAny('banking-vnd-inquire-transactions','banking-fcy-inquire-transactions')")
    public ApiResponse<Void> undoConfirmTransaction(
            @PathVariable("acctSeqNo") String acctSeqNo,
            @PathVariable("trnsSeqNo") String trnsSeqNo) {
        transactionService.undoConfirm(acctSeqNo, trnsSeqNo);
        return ApiResponse.ok(null);
    }

    /* ================================================================
     *  Transaction memo (side panel) — legacy comm_0105_001, reusing the
     *  shared MemoService. The memo thread is keyed by the 4-key
     *  BANK_CD/ACCT_NO/TRSC_DT/ACCT_SEQ_NO derived server-side from the
     *  path; the VND/FCY {@code scope} selects the memo WEB_SVC_ID.
     * ================================================================ */

    /** List one transaction's memos (newest-first). */
    @GetMapping("/accounts/{acctSeqNo}/transactions/{trnsSeqNo}/memos")
    @PreAuthorize("@menuAuthz.canAccessAny('banking-vnd-inquire-transactions','banking-fcy-inquire-transactions')")
    public ApiResponse<MemoListResponse> listTransactionMemos(
            @PathVariable("acctSeqNo") String acctSeqNo,
            @PathVariable("trnsSeqNo") String trnsSeqNo,
            @RequestParam(value = "scope", required = false, defaultValue = "VND")
            @Pattern(regexp = "^(VND|FCY)$", message = "scope must be VND or FCY") String scope) {
        return ApiResponse.ok(transactionService.listMemos(acctSeqNo, trnsSeqNo, scope));
    }

    /** Add a memo to one transaction. */
    @PostMapping("/accounts/{acctSeqNo}/transactions/{trnsSeqNo}/memos")
    @PreAuthorize("@menuAuthz.canAccessAny('banking-vnd-inquire-transactions','banking-fcy-inquire-transactions')")
    public ApiResponse<MemoRow> createTransactionMemo(
            @PathVariable("acctSeqNo") String acctSeqNo,
            @PathVariable("trnsSeqNo") String trnsSeqNo,
            @RequestParam(value = "scope", required = false, defaultValue = "VND")
            @Pattern(regexp = "^(VND|FCY)$", message = "scope must be VND or FCY") String scope,
            @Valid @RequestBody TransactionMemoCreateRequest request) {
        return ApiResponse.ok(transactionService.createMemo(acctSeqNo, trnsSeqNo, scope, request));
    }

    /** Edit one memo's text — author-only. */
    @PatchMapping("/accounts/{acctSeqNo}/transactions/{trnsSeqNo}/memos/{seqNo}")
    @PreAuthorize("@menuAuthz.canAccessAny('banking-vnd-inquire-transactions','banking-fcy-inquire-transactions')")
    public ApiResponse<MemoRow> updateTransactionMemo(
            @PathVariable("acctSeqNo") String acctSeqNo,
            @PathVariable("trnsSeqNo") String trnsSeqNo,
            @PathVariable("seqNo") BigDecimal seqNo,
            @RequestParam(value = "scope", required = false, defaultValue = "VND")
            @Pattern(regexp = "^(VND|FCY)$", message = "scope must be VND or FCY") String scope,
            @Valid @RequestBody TransactionMemoUpdateRequest request) {
        return ApiResponse.ok(transactionService.updateMemo(acctSeqNo, trnsSeqNo, scope, seqNo, request));
    }

    /** Set the row-level sticker color for one transaction's memo thread. */
    @PatchMapping("/accounts/{acctSeqNo}/transactions/{trnsSeqNo}/memos/color")
    @PreAuthorize("@menuAuthz.canAccessAny('banking-vnd-inquire-transactions','banking-fcy-inquire-transactions')")
    public ApiResponse<Void> updateTransactionMemoColor(
            @PathVariable("acctSeqNo") String acctSeqNo,
            @PathVariable("trnsSeqNo") String trnsSeqNo,
            @RequestParam(value = "scope", required = false, defaultValue = "VND")
            @Pattern(regexp = "^(VND|FCY)$", message = "scope must be VND or FCY") String scope,
            @Valid @RequestBody TransactionMemoColorRequest request) {
        transactionService.updateMemoColor(acctSeqNo, trnsSeqNo, scope, request);
        return ApiResponse.ok(null);
    }

    /** Delete one memo — author-only. */
    @DeleteMapping("/accounts/{acctSeqNo}/transactions/{trnsSeqNo}/memos/{seqNo}")
    @PreAuthorize("@menuAuthz.canAccessAny('banking-vnd-inquire-transactions','banking-fcy-inquire-transactions')")
    public ApiResponse<Void> deleteTransactionMemo(
            @PathVariable("acctSeqNo") String acctSeqNo,
            @PathVariable("trnsSeqNo") String trnsSeqNo,
            @PathVariable("seqNo") BigDecimal seqNo,
            @RequestParam(value = "scope", required = false, defaultValue = "VND")
            @Pattern(regexp = "^(VND|FCY)$", message = "scope must be VND or FCY") String scope) {
        transactionService.deleteMemo(acctSeqNo, trnsSeqNo, scope, seqNo);
        return ApiResponse.ok(null);
    }

    /**
     * Assign / change the Reporting Account (chart of accounts) on one
     * bank transaction. Writes {@code CHART_ACCT_CD} +
     * {@code CHART_ACCT_SEQ_NO} on {@code ACCT_TRNS_HSTR}. Mirrors
     * legacy {@code fnsh_0200_001_view.jsp} line 327's Reporting
     * Account cell.
     *
     * <p>Validates the picked chart-of-accounts row against
     * {@code CHART_ACCT_INFM} for the current tenant before the write
     * commits — a stale frontend cannot drift the seq_no / code pair
     * apart, and a deleted / inactive account cannot be assigned.
     *
     * <p>Returns the refreshed transaction detail payload so the
     * frontend can re-render the row in place without a follow-up GET.
     */
    @PatchMapping("/accounts/{acctSeqNo}/transactions/{trnsSeqNo}/reporting-account")
    @PreAuthorize("@menuAuthz.canAccessAny('banking-vnd-inquire-transactions','banking-fcy-inquire-transactions')")
    public ApiResponse<TransactionDetailResponse> updateTransactionReportingAccount(
            @PathVariable("acctSeqNo") String acctSeqNo,
            @PathVariable("trnsSeqNo") String trnsSeqNo,
            @Valid @RequestBody TransactionReportingAccountRequest request) {
        return ApiResponse.ok(
                transactionService.updateReportingAccount(acctSeqNo, trnsSeqNo, request));
    }

    /**
     * Clear the Reporting Account on one bank transaction. Nulls both
     * {@code CHART_ACCT_CD} and {@code CHART_ACCT_SEQ_NO} on
     * {@code ACCT_TRNS_HSTR}. Mirrors the legacy "× clear" affordance
     * on the Reporting Account cell — the row falls back to the brand-
     * blue "Select Account" placeholder after the clear.
     *
     * <p>Idempotent — clearing an already-empty row is a no-op (no
     * error, just returns the refreshed detail). Returns the refreshed
     * transaction detail so the frontend re-renders without a follow-
     * up GET.
     */
    @DeleteMapping("/accounts/{acctSeqNo}/transactions/{trnsSeqNo}/reporting-account")
    @PreAuthorize("@menuAuthz.canAccessAny('banking-vnd-inquire-transactions','banking-fcy-inquire-transactions')")
    public ApiResponse<TransactionDetailResponse> clearTransactionReportingAccount(
            @PathVariable("acctSeqNo") String acctSeqNo,
            @PathVariable("trnsSeqNo") String trnsSeqNo) {
        return ApiResponse.ok(
                transactionService.clearReportingAccount(acctSeqNo, trnsSeqNo));
    }

    /**
     * Assign / change the Purpose ({@code USE_USAG_CD}) on one bank
     * transaction. Writes {@code RCV_PAY_USAG_CD} on the matching
     * {@code ACCT_TRNS_RCV_PAY_INFM} row — creating one when the
     * transaction has no receipt/payment record yet (the Purpose
     * picker is independent of the Confirm flow).
     *
     * <p>Validates the picked entry exists in
     * {@code BUSI_USAG_SET_INFM} for the current tenant and is active
     * ({@code USE_YN='Y'}) before the write commits.
     */
    @PatchMapping("/accounts/{acctSeqNo}/transactions/{trnsSeqNo}/purpose")
    @PreAuthorize("@menuAuthz.canAccessAny('banking-vnd-inquire-transactions','banking-fcy-inquire-transactions')")
    public ApiResponse<TransactionDetailResponse> updateTransactionPurpose(
            @PathVariable("acctSeqNo") String acctSeqNo,
            @PathVariable("trnsSeqNo") String trnsSeqNo,
            @Valid @RequestBody TransactionPurposeRequest request) {
        return ApiResponse.ok(
                transactionService.updatePurpose(acctSeqNo, trnsSeqNo, request));
    }

    /**
     * Clear the Purpose on one bank transaction — nulls
     * {@code RCV_PAY_USAG_CD} on every receipt/payment row tied to the
     * transaction. Idempotent — clearing an already-empty record is a
     * no-op.
     */
    @DeleteMapping("/accounts/{acctSeqNo}/transactions/{trnsSeqNo}/purpose")
    @PreAuthorize("@menuAuthz.canAccessAny('banking-vnd-inquire-transactions','banking-fcy-inquire-transactions')")
    public ApiResponse<TransactionDetailResponse> clearTransactionPurpose(
            @PathVariable("acctSeqNo") String acctSeqNo,
            @PathVariable("trnsSeqNo") String trnsSeqNo) {
        return ApiResponse.ok(
                transactionService.clearPurpose(acctSeqNo, trnsSeqNo));
    }

    /**
     * Assign / change the VAS Account ({@code VAS_ACCT_CD}) on one
     * bank transaction. Writes the picked code on
     * {@code ACCT_TRNS_RCV_PAY_INFM} — creating one when the
     * transaction has no receipt/payment record yet. Validates the
     * picker's choice exists in {@code BUSI_VAS_ACCT_INFM} for the
     * current tenant and is active ({@code STTS='A'}, {@code DEL_YN='N'}).
     */
    @PatchMapping("/accounts/{acctSeqNo}/transactions/{trnsSeqNo}/vas-account")
    @PreAuthorize("@menuAuthz.canAccessAny('banking-vnd-inquire-transactions','banking-fcy-inquire-transactions')")
    public ApiResponse<TransactionDetailResponse> updateTransactionVasAccount(
            @PathVariable("acctSeqNo") String acctSeqNo,
            @PathVariable("trnsSeqNo") String trnsSeqNo,
            @Valid @RequestBody TransactionVasAccountRequest request) {
        return ApiResponse.ok(
                transactionService.updateVasAccount(acctSeqNo, trnsSeqNo, request));
    }

    /**
     * Clear the VAS Account on one bank transaction. Idempotent —
     * clearing an already-empty record is a no-op.
     */
    @DeleteMapping("/accounts/{acctSeqNo}/transactions/{trnsSeqNo}/vas-account")
    @PreAuthorize("@menuAuthz.canAccessAny('banking-vnd-inquire-transactions','banking-fcy-inquire-transactions')")
    public ApiResponse<TransactionDetailResponse> clearTransactionVasAccount(
            @PathVariable("acctSeqNo") String acctSeqNo,
            @PathVariable("trnsSeqNo") String trnsSeqNo) {
        return ApiResponse.ok(
                transactionService.clearVasAccount(acctSeqNo, trnsSeqNo));
    }

    /**
     * Assign / change the Contact ({@code TRCO_KEY}) on one bank
     * transaction. Writes the snapshot pair
     * ({@code RCV_PAY_TRCO_KEY} + {@code RCV_PAY_TRCO_NM}) on
     * {@code ACCT_TRNS_RCV_PAY_INFM} — creating one when the
     * transaction has no receipt/payment record yet. Validates the
     * picker's choice exists in {@code TRCO_INFM} for the current
     * tenant and is non-trashed ({@code TRCO_STTS != 'C'}).
     */
    @PatchMapping("/accounts/{acctSeqNo}/transactions/{trnsSeqNo}/contact")
    @PreAuthorize("@menuAuthz.canAccessAny('banking-vnd-inquire-transactions','banking-fcy-inquire-transactions')")
    public ApiResponse<TransactionDetailResponse> updateTransactionContact(
            @PathVariable("acctSeqNo") String acctSeqNo,
            @PathVariable("trnsSeqNo") String trnsSeqNo,
            @Valid @RequestBody TransactionContactRequest request) {
        return ApiResponse.ok(
                transactionService.updateContact(acctSeqNo, trnsSeqNo, request));
    }

    /**
     * Clear the Contact on one bank transaction. Idempotent — clearing
     * an already-empty record is a no-op.
     */
    @DeleteMapping("/accounts/{acctSeqNo}/transactions/{trnsSeqNo}/contact")
    @PreAuthorize("@menuAuthz.canAccessAny('banking-vnd-inquire-transactions','banking-fcy-inquire-transactions')")
    public ApiResponse<TransactionDetailResponse> clearTransactionContact(
            @PathVariable("acctSeqNo") String acctSeqNo,
            @PathVariable("trnsSeqNo") String trnsSeqNo) {
        return ApiResponse.ok(
                transactionService.clearContact(acctSeqNo, trnsSeqNo));
    }

    /**
     * Smart Organizer — list the payment candidates the user may match the
     * bank transaction to. Mirrors legacy {@code comm_0196_001_r001} /
     * {@code OVRS_PYMN_SUMR_DETL_R006}: domestic non-payroll detail lines,
     * payroll batch rows, and overseas detail lines that pass the exact
     * amount / date / transfer-time / transferred / dispatch-success /
     * unmatched / unlocked filter set (P4 legacy parity).
     */
    @GetMapping("/accounts/{acctSeqNo}/transactions/{trnsSeqNo}/match-candidates")
    @PreAuthorize("@menuAuthz.canAccessAny('banking-vnd-inquire-transactions','banking-fcy-inquire-transactions')")
    public ApiResponse<MatchCandidateListResponse> listMatchCandidates(
            @PathVariable("acctSeqNo") String acctSeqNo,
            @PathVariable("trnsSeqNo") String trnsSeqNo,
            @RequestParam(value = "from",    required = false) String fromDt,
            @RequestParam(value = "to",      required = false) String toDt,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "match_rmrk_yn", required = false) String matchRmrkYn) {
        return ApiResponse.ok(
                matchService.listCandidates(acctSeqNo, trnsSeqNo, fromDt, toDt, keyword, matchRmrkYn));
    }

    /**
     * Smart Organizer — match the bank transaction to a payment line.
     * Atomically writes {@code BUSI_MATCH_LOG} + flips
     * {@code LINK_MATCH_YN='Y'} on {@code ACCT_TRNS_HSTR} and
     * {@code SCDUL_PYMN_SUMR_DETL}. Mirrors legacy
     * {@code comm_0196_001_c001}.
     */
    @PostMapping("/accounts/{acctSeqNo}/transactions/{trnsSeqNo}/match")
    @PreAuthorize("@menuAuthz.canAccessAny('banking-vnd-inquire-transactions','banking-fcy-inquire-transactions')")
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
    @PreAuthorize("@menuAuthz.canAccessAny('banking-vnd-inquire-transactions','banking-fcy-inquire-transactions')")
    public ApiResponse<MatchResultResponse> undoMatch(
            @PathVariable("acctSeqNo") String acctSeqNo,
            @PathVariable("trnsSeqNo") String trnsSeqNo) {
        return ApiResponse.ok(matchService.undo(acctSeqNo, trnsSeqNo));
    }

    /**
     * Excel export for Banking &gt; Transaction History. Routes through
     * the shared {@code ExcelExportService} — the body's {@code screenId}
     * must be {@code "banking-transaction-history-vnd"} or
     * {@code "banking-transaction-history-fcy"} (V156 seed; the single
     * {@code banking-transaction-history} key is retired) and
     * {@code filters.acctSeqNo} is required so the export is scoped to
     * one account. The VND / FCY scope also selects the WeCMS&rarr;ERP audit
     * {@code TRNS_TYPE_CD} ({@code "01"} / {@code "08"}). Mirrors
     * {@code POST /accounts/export}.
     */
    @PostMapping("/accounts/transactions/export")
    @PreAuthorize("@menuAuthz.canAccessAny('banking-vnd-inquire-transactions','banking-fcy-inquire-transactions')")
    public void exportTransactions(
            @Valid @RequestBody ExcelExportRequest request,
            HttpServletResponse response) {
        excelExportService.exportTo(response, request);
    }

    /**
     * Excel Download for Transaction History (VND) — the PO-requested
     * template-populated {@code .xls} report (legacy {@code fnsh_0002_001}
     * localized download). Loads the locale template, populates one row per
     * transaction preserving all template styling, and streams
     * {@code Transaction History (VND).xls} / {@code 거래내역조회 (VND).xls} /
     * {@code Tra cứu giao dịch (VND).xls}.
     *
     * <p>Scoped to one account via {@code acctSeqNo} (the synthetic
     * {@code BANK_CD_ACCT_NO} identifier). Honours the same filter set as
     * the on-screen list ({@code from} / {@code to} / {@code type} /
     * {@code cnfm_yn} / {@code keyword}). Dedicated report endpoint — NOT
     * the generic {@code POST /accounts/transactions/export}. Preserves the
     * legacy ERP audit side-effect: WeCMS&rarr;ERP {@code TRNS_TYPE_CD = "01"}.
     */
    @GetMapping("/accounts/{acctSeqNo}/transactions/report")
    @PreAuthorize("@menuAuthz.canAccessAny('banking-vnd-inquire-transactions','banking-fcy-inquire-transactions')")
    public ResponseEntity<ByteArrayResource> downloadTransactionHistoryReport(
            @PathVariable("acctSeqNo") String acctSeqNo,
            @RequestParam(value = "from",     required = false) String fromDt,
            @RequestParam(value = "to",       required = false) String toDt,
            @RequestParam(value = "type",     required = false) String type,
            @RequestParam(value = "cnfm_yn",  required = false) String cnfmYn,
            @RequestParam(value = "keyword",  required = false) String keyword,
            @RequestParam(value = "lang",     required = false) String lang) {
        CurrentSavingsDownloadService.GeneratedReport report =
                currentSavingsDownloadService.generateTransactionHistory(
                        acctSeqNo, fromDt, toDt, type, cnfmYn, keyword, lang);
        erpHistoryWriter.recordExportedBytes("01", report.content(), report.fileName());
        return reportResponse(report);
    }

    /**
     * Excel Download for Transaction History (FCY) — the PO-requested
     * template-populated {@code .xls} report (legacy {@code fnsh_0006_001}
     * localized download). Loads the FCY locale template, populates one row per
     * transaction preserving all template styling, and streams
     * {@code Transaction History (FCY).xls} / {@code 거래내역조회 (FCY).xls} /
     * {@code Tra cứu giao dịch (FCY).xls}.
     *
     * <p>Scoped to one account via {@code acctSeqNo}; honours the same filter
     * set as the on-screen list ({@code from} / {@code to} / {@code type} /
     * {@code cnfm_yn} / {@code keyword}). Dedicated FCY report endpoint — NOT the
     * generic {@code POST /accounts/transactions/export} and NOT the VND
     * {@code /accounts/{id}/transactions/report}. Preserves the legacy ERP audit
     * side-effect: WeCMS&rarr;ERP {@code TRNS_TYPE_CD = "08"} ({@code fnsh_0006_001}).
     */
    @GetMapping("/accounts/{acctSeqNo}/transactions/report-fcy")
    @PreAuthorize("@menuAuthz.canAccessAny('banking-vnd-inquire-transactions','banking-fcy-inquire-transactions')")
    public ResponseEntity<ByteArrayResource> downloadTransactionHistoryFcyReport(
            @PathVariable("acctSeqNo") String acctSeqNo,
            @RequestParam(value = "from",     required = false) String fromDt,
            @RequestParam(value = "to",       required = false) String toDt,
            @RequestParam(value = "type",     required = false) String type,
            @RequestParam(value = "cnfm_yn",  required = false) String cnfmYn,
            @RequestParam(value = "keyword",  required = false) String keyword,
            @RequestParam(value = "lang",     required = false) String lang) {
        CurrentSavingsDownloadService.GeneratedReport report =
                currentSavingsDownloadService.generateTransactionHistory(
                        CurrentSavingsScope.FCY, acctSeqNo, fromDt, toDt, type, cnfmYn, keyword, lang);
        erpHistoryWriter.recordExportedBytes("08", report.content(), report.fileName());
        return reportResponse(report);
    }

    /**
     * Shared binary-response builder for the template-populated
     * {@code .xls} report downloads. RFC 5987 UTF-8 filename so the
     * localized Korean / Vietnamese names round-trip; legacy {@code .xls}
     * MIME ({@code application/vnd.ms-excel}); {@code nosniff}.
     */
    private static ResponseEntity<ByteArrayResource> reportResponse(
            CurrentSavingsDownloadService.GeneratedReport report) {
        ContentDisposition cd = ContentDisposition.builder("attachment")
                .filename(report.fileName(), java.nio.charset.StandardCharsets.UTF_8)
                .build();
        ByteArrayResource body = new ByteArrayResource(report.content());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.ms-excel"))
                .contentLength(body.contentLength())
                .header(HttpHeaders.CONTENT_DISPOSITION, cd.toString())
                .header("X-Content-Type-Options", "nosniff")
                .header("Access-Control-Expose-Headers", HttpHeaders.CONTENT_DISPOSITION)
                .body(body);
    }
}
