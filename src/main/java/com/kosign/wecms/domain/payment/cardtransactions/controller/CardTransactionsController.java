package com.kosign.wecms.domain.payment.cardtransactions.controller;

import com.kosign.wecms.common.export.ExcelExportRequest;
import com.kosign.wecms.common.export.ExcelExportService;
import com.kosign.wecms.common.response.ApiResponse;
import com.kosign.wecms.domain.banking.nxisas.dto.SubmitChallengeRequest;
import com.kosign.wecms.domain.banking.nxisas.dto.SubmitQnaRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.kosign.wecms.domain.payment.cardtransactions.dto.CardListResponse;
import com.kosign.wecms.domain.payment.cardtransactions.dto.CardSyncSessionResponse;
import com.kosign.wecms.domain.payment.cardtransactions.dto.CardManagementListResponse;
import com.kosign.wecms.domain.payment.cardtransactions.dto.CardTranListPeriodLimitRequest;
import com.kosign.wecms.domain.payment.cardtransactions.dto.CardTranListPeriodLimitResponse;
import com.kosign.wecms.domain.payment.cardtransactions.dto.CardTransactionListResponse;
import com.kosign.wecms.domain.payment.cardtransactions.dto.CardTransactionPatchRequest;
import com.kosign.wecms.domain.payment.cardtransactions.dto.CardTransactionRow;
import com.kosign.wecms.domain.payment.cardtransactions.dto.CreateCardsRequest;
import com.kosign.wecms.domain.payment.cardtransactions.dto.CreateCardsResponse;
import com.kosign.wecms.domain.payment.cardtransactions.dto.CreateReceiptRequest;
import com.kosign.wecms.domain.payment.cardtransactions.dto.CreateReceiptResponse;
import com.kosign.wecms.domain.payment.cardtransactions.dto.CreateMemoRequest;
import com.kosign.wecms.domain.payment.cardtransactions.dto.MemoListResponse;
import com.kosign.wecms.domain.payment.cardtransactions.dto.MemoRow;
import com.kosign.wecms.domain.payment.cardtransactions.dto.PurposeOption;
import com.kosign.wecms.domain.payment.cardtransactions.dto.ReceiptDetailResponse;
import com.kosign.wecms.domain.payment.cardtransactions.dto.LinkExpensesRequest;
import com.kosign.wecms.domain.payment.cardtransactions.dto.LinkExpensesResponse;
import com.kosign.wecms.domain.payment.cardtransactions.dto.LinkableEInvoiceListResponse;
import com.kosign.wecms.domain.payment.cardtransactions.dto.LinkableReceiptListResponse;
import com.kosign.wecms.domain.payment.cardtransactions.dto.NextDocNoResponse;
import com.kosign.wecms.domain.payment.cardtransactions.dto.RequestApprovalRequest;
import com.kosign.wecms.domain.payment.cardtransactions.dto.RequestApprovalResponse;
import com.kosign.wecms.domain.payment.cardtransactions.dto.SyncCardTransactionsRequest;
import com.kosign.wecms.domain.payment.cardtransactions.dto.SyncCardTransactionsResponse;
import com.kosign.wecms.domain.payment.cardtransactions.dto.UpdateCardActiveRequest;
import com.kosign.wecms.domain.payment.cardtransactions.dto.UpdateCardInfoRequest;
import com.kosign.wecms.domain.payment.cardtransactions.dto.UpdateReportingAccountRequest;
import com.kosign.wecms.domain.payment.approvalbox.dto.EditApprovalRequest;
import com.kosign.wecms.domain.payment.approvalbox.service.ApprovalEditService;
import com.kosign.wecms.domain.payment.cardtransactions.service.CardTranListR001Service;
import com.kosign.wecms.domain.payment.cardtransactions.service.CardTransactionScrapeService;
import com.kosign.wecms.domain.payment.cardtransactions.service.CardTransactionSyncService;
import com.kosign.wecms.domain.payment.cardtransactions.service.CardTransactionsService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
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

import java.util.List;
import java.util.UUID;

/**
 * Payment &gt; Card Transactions — four REST endpoints under
 * {@code /api/v1/payment/card-transactions}, mirroring the legacy
 * {@code card_0001_001_*} surface minus the scraper / approval-request
 * write-paths (OQ-1: deferred to the future {@code expense-resolution}
 * slug):
 * <ul>
 *   <li>{@code GET    /api/v1/payment/card-transactions}
 *       — paged ledger + aggregate totals
 *       (legacy {@code card_0001_001_r001}).</li>
 *   <li>{@code GET    /api/v1/payment/card-transactions/cards}
 *       — All-Cards dropdown
 *       (legacy {@code card_0001_001_r002}).</li>
 *   <li>{@code PATCH  /api/v1/payment/card-transactions/{cardSeqNo}}
 *       — single-field description / purpose
 *       (legacy {@code card_0001_001_u001}).</li>
 *   <li>{@code PUT    /api/v1/payment/card-transactions/{cardSeqNo}/reporting-account}
 *       — paired CHART_ACCT update
 *       (legacy {@code card_0001_001_u002}).</li>
 * </ul>
 *
 * <p>Wire format is lowercase snake_case via the global Jackson naming
 * strategy ({@code config/JacksonConfig}). Java fields stay camelCase
 * per {@code wecms-api-main/CLAUDE.md} §"API Naming &amp; Serialization
 * Standards"; only {@code @PathVariable} / {@code @RequestParam} pin
 * their snake_case wire name explicitly.
 *
 * <p>Authorization: every endpoint is under {@code /api/v1/*} and
 * therefore {@code authenticated()} per
 * {@link com.kosign.wecms.config.SecurityConfig}. Per-row visibility +
 * tenant scope live in the service layer against the JWT-bound tenant
 * via {@code TenantContext.currentBizKey()}. {@code @PreAuthorize} role
 * gating is deferred — legacy parity (any tenant user can read +
 * patch), same precedent as {@code ChartOfAccountsController}.
 *
 * <p>Audit logging: every state-changing endpoint writes one row to
 * {@code AUDIT_LOGS_INFM} via
 * {@link com.kosign.wecms.common.auditlog.service.AuditLogService} with
 * {@code WSVC_ID = "card-transactions"} (slug, NOT the legacy
 * {@code card_0001_001} id) per the 2026-05-09 audit-id retirement.
 */
@RestController
@RequestMapping("/api/v1/payment/card-transactions")
@RequiredArgsConstructor
@Validated
@PreAuthorize("@menuAuthz.canAccess('payment-corporate-card-transactions')")
public class CardTransactionsController {

    /** Pattern for the {@code cardSeqNo} path component — {@code
     *  VARCHAR(20)} on DEV, scraper-allocated as a numeric token (e.g.
     *  {@code "156"}). Permissive regex (1..20 digits) accepts the
     *  legacy seed values without re-imposing a tighter shape. */
    private static final String CARD_SEQ_NO_PATTERN = "^[0-9]{1,20}$";

    /** Audit-log slug for Corporate Card edits. */
    private static final String AUDIT_WSVC_ID_CARD = "card-transactions";

    private final CardTransactionsService     service;
    private final CardTransactionSyncService  syncService;
    private final CardTransactionScrapeService scrapeService;
    private final CardTranListR001Service     cardTranListR001Service;
    /** Shared Approval Details edit command (title / content / approver line /
     *  attachments) — same code path as the Approval Box edit, audited under
     *  the card slug. */
    private final ApprovalEditService         approvalEditService;
    /** Shared Excel writer — orchestrates the
     *  {@link com.kosign.wecms.domain.payment.cardtransactions.export.CardTransactionsExportProvider}
     *  + writes the XLSX response. */
    private final ExcelExportService         excelExportService;

    /* ----------------------------------------------------------------
     *  Read — list
     * ---------------------------------------------------------------- */

    /**
     * Paged + filtered + sorted ledger read.
     *
     * @param fromDt       required {@code yyyyMMdd}
     * @param toDt         required {@code yyyyMMdd}
     * @param srchWd       optional partial match on {@code MERCH_NM} /
     *                     {@code CARD_NM}
     * @param bankCds      optional repeated query param
     *                     ({@code bank_cd=A&bank_cd=B}) — bank-code
     *                     allow-list
     * @param cardNos      optional repeated query param — card-no
     *                     allow-list
     * @param linkYn       {@code Y} / {@code N} / blank for "all"
     * @param sortField    optional whitelist FIELD_ID
     * @param sortDirection optional {@code ASC} / {@code DESC}
     * @param page         zero-based page index (default {@code 0})
     * @param size         rows per page (default {@code 15}, max {@code 200})
     */
    @GetMapping
    public ApiResponse<CardTransactionListResponse> list(
            @RequestParam("from_date")                                @NotBlank @Pattern(regexp = "^\\d{8}$") String       fromDt,
            @RequestParam("to_date")                                  @NotBlank @Pattern(regexp = "^\\d{8}$") String       toDt,
            @RequestParam(value = "srch_wd",        required = false)                                       String       srchWd,
            @RequestParam(value = "bank_cd",        required = false)                                       List<String> bankCds,
            @RequestParam(value = "card_no",        required = false)                                       List<String> cardNos,
            @RequestParam(value = "link_yn",        required = false) @Pattern(regexp = "^[YNyn]?$")        String       linkYn,
            @RequestParam(value = "sort_field",     required = false)                                       String       sortField,
            @RequestParam(value = "sort_direction", required = false)                                       String       sortDirection,
            @RequestParam(value = "page",           defaultValue = "0")                                     int          page,
            @RequestParam(value = "size",           defaultValue = "15")                                    int          size) {

        return ApiResponse.ok(service.list(
                fromDt, toDt, srchWd, bankCds, cardNos, linkYn,
                sortField, sortDirection, page, size));
    }

    /* ----------------------------------------------------------------
     *  Read — card transactions for one APPR_SEQ_NO
     *
     *  Consumed by the Approval Box's shared
     *  `<ApprovalDetailsDialog kind="card">` popup so the Transaction
     *  Details section shows every card stamped with the picked
     *  approval reference. Mirrors the legacy
     *  `comm_0195_001.ts:CardDetails(data.REC4)` data path.
     * ---------------------------------------------------------------- */

    @GetMapping("/by-approval/{apprSeqNo}")
    public ApiResponse<java.util.List<CardTransactionRow>> listByApproval(
            @PathVariable("apprSeqNo")
            @Pattern(regexp = "^[0-9]{1,15}$",
                     message = "appr_seq_no: invalid format")
            String apprSeqNo) {
        return ApiResponse.ok(service.listByApprSeqNo(apprSeqNo));
    }

    /**
     * Withdraw a pending Card Approval — kebab-menu "Delete" on the
     * Approval Details popup. Deletes the EXPT_RESOL_MSTR +
     * EXPT_RESOL_APPRVR_INFM rows for this {@code apprSeqNo} and
     * clears every {@code CARD_TRNS_HSTR.APPR_SEQ_NO} pointing at it.
     */
    @DeleteMapping("/by-approval/{apprSeqNo}")
    public ApiResponse<Void> withdrawApproval(
            @PathVariable("apprSeqNo")
            @Pattern(regexp = "^[0-9]{1,15}$",
                     message = "appr_seq_no: invalid format")
            String apprSeqNo) {
        service.withdrawApproval(apprSeqNo);
        return ApiResponse.ok(null);
    }

    /**
     * Change the approval line on a pending Card Approval — kebab-menu
     * "Edit" on the Approval Details popup. Replaces every
     * EXPT_RESOL_APPRVR_INFM row with the new approval line's
     * approvers and resets the master to Pending.
     */
    @PutMapping("/by-approval/{apprSeqNo}/approval-line")
    public ApiResponse<Void> changeApprovalLine(
            @PathVariable("apprSeqNo")
            @Pattern(regexp = "^[0-9]{1,15}$",
                     message = "appr_seq_no: invalid format")
            String apprSeqNo,
            @org.springframework.web.bind.annotation.RequestBody
            com.kosign.wecms.domain.payment.cardtransactions.dto.ChangeApprovalLineRequest req) {
        service.changeApprovalLine(apprSeqNo, req != null ? req.apprLineNo() : null);
        return ApiResponse.ok(null);
    }

    /**
     * Edit an in-flight (Pending / In Progress) Corporate Card approval —
     * title + approval content + approver line + attachment add/remove in one
     * multipart call. Delegates to the shared {@link ApprovalEditService}
     * (same edit surface as Approval Box; the card approval IS an
     * {@code EXPT_RESOL_MSTR} row with {@code APPR_DV='33'}), audited under the
     * card slug. Mirrors legacy {@code comm_0192_013_u002}.
     */
    @PutMapping(value = "/by-approval/{apprSeqNo}/edit",
                consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Void> editApproval(
            @PathVariable("apprSeqNo")
            @Pattern(regexp = "^[0-9]{1,15}$",
                     message = "appr_seq_no: invalid format")
            String apprSeqNo,
            @Valid @org.springframework.web.bind.annotation.RequestPart("request") EditApprovalRequest request,
            @org.springframework.web.bind.annotation.RequestPart(value = "attachments", required = false)
            List<org.springframework.web.multipart.MultipartFile> attachments) {
        approvalEditService.edit(apprSeqNo, request, attachments, AUDIT_WSVC_ID_CARD);
        return ApiResponse.ok(null);
    }

    /* ----------------------------------------------------------------
     *  Read — cards dropdown
     * ---------------------------------------------------------------- */

    @GetMapping("/cards")
    public ApiResponse<CardListResponse> cards(
            @RequestParam(value = "bank_cd", required = false)
            @Pattern(regexp = "^[0-9A-Za-z]{0,20}$",
                    message = "bank_cd: invalid format") String bankCd) {
        return ApiResponse.ok(service.cards(bankCd));
    }

    /* ----------------------------------------------------------------
     *  Write — single-field PATCH
     * ---------------------------------------------------------------- */

    /**
     * Apply the discriminated patch to one row. Body shape is one of:
     * <pre>{@code
     *   { "field": "description", "value": "..." }
     *   { "field": "purpose",     "value": "..." }
     * }</pre>
     *
     * <p>Errors:
     * <ul>
     *   <li>{@code WBE_CARD_TRANSACTIONS_NOT_FOUND} — row missing OR
     *       wrong tenant.</li>
     *   <li>{@code WBE_VALIDATION} — bean-validation failure.</li>
     *   <li>{@code WBE_BAD_REQUEST} — unknown {@code field} discriminator
     *       (Jackson can't resolve the sub-type).</li>
     * </ul>
     */
    @PatchMapping("/{card_seq_no}")
    public ApiResponse<Void> patch(
            @PathVariable("card_seq_no")
            @NotBlank @Size(max = 20)
            @Pattern(regexp = CARD_SEQ_NO_PATTERN,
                    message = "card_seq_no: invalid format")
            String cardSeqNo,
            @Valid @RequestBody CardTransactionPatchRequest body) {
        service.patch(cardSeqNo, body);
        return ApiResponse.ok(null);
    }

    /* ----------------------------------------------------------------
     *  Write — reporting-account PUT
     * ---------------------------------------------------------------- */

    /**
     * Update both {@code CHART_ACCT_SEQ_NO} + {@code CHART_ACCT_CD} on
     * one row. Always-paired.
     *
     * <p>Errors:
     * <ul>
     *   <li>{@code WBE_CARD_TRANSACTIONS_NOT_FOUND} — row missing OR
     *       wrong tenant.</li>
     *   <li>{@code WBE_CARD_TRANSACTIONS_CHART_ACCT_INVALID} — the
     *       picked chart-of-accounts row doesn't exist, is soft-deleted,
     *       or its {@code (seqNo, code)} pair doesn't match a single
     *       active entry.</li>
     *   <li>{@code WBE_VALIDATION} — bean-validation failure.</li>
     * </ul>
     */
    @PutMapping("/{card_seq_no}/reporting-account")
    public ApiResponse<Void> updateReportingAccount(
            @PathVariable("card_seq_no")
            @NotBlank @Size(max = 20)
            @Pattern(regexp = CARD_SEQ_NO_PATTERN,
                    message = "card_seq_no: invalid format")
            String cardSeqNo,
            @Valid @RequestBody UpdateReportingAccountRequest req) {
        service.updateReportingAccount(cardSeqNo, req);
        return ApiResponse.ok(null);
    }

    /**
     * Clear (null out) {@code CHART_ACCT_SEQ_NO} + {@code CHART_ACCT_CD} on
     * one row — the clear-first flow's × button on the inline Reporting
     * Account cell. Mirrors the Transaction History reporting-account clear.
     *
     * <p>Errors: {@code WBE_CARD_TRANSACTIONS_NOT_FOUND} — row missing OR
     * wrong tenant. Idempotent — clearing an already-empty row succeeds.
     */
    @DeleteMapping("/{card_seq_no}/reporting-account")
    public ApiResponse<Void> clearReportingAccount(
            @PathVariable("card_seq_no")
            @NotBlank @Size(max = 20)
            @Pattern(regexp = CARD_SEQ_NO_PATTERN,
                    message = "card_seq_no: invalid format")
            String cardSeqNo) {
        service.clearReportingAccount(cardSeqNo);
        return ApiResponse.ok(null);
    }

    /* ----------------------------------------------------------------
     *  Card Management Setting modal (legacy card_0001_003)
     * ---------------------------------------------------------------- */

    /** Bank-code path-component validator — VARCHAR(20) on
     *  {@code CARD_MSTR.BANK_CD}; legacy values are 8-digit numeric. */
    private static final String BANK_CD_PATTERN = "^[0-9A-Za-z]{1,20}$";

    /** Card-number path-component validator — VARCHAR(100) on
     *  {@code CARD_MSTR.CARD_NO}; masked PAN format like
     *  {@code 407465******5559} contains alphanumerics + asterisks +
     *  hyphens. Allow the full reasonable character set; the service
     *  enforces existence + tenant-scope, not lexical format. */
    private static final String CARD_NO_PATTERN = "^[0-9A-Za-z*\\-]{1,100}$";

    /**
     * List cards for the Card Management Setting modal. Mirrors legacy
     * {@code card_0001_003_r001}.
     *
     * @param srchWd        optional substring against {@code CARD_NM} /
     *                      {@code CARD_NICK_NM} / {@code CARD_HLDR_NM}
     * @param sortField     {@code CardMstrSortField} FIELD_ID
     * @param sortDirection {@code ASC} / {@code DESC}
     * @param size          rows per page (1..100, default 100)
     */
    @GetMapping("/cards/management")
    public ApiResponse<CardManagementListResponse> listManagementCards(
            @RequestParam(value = "srch_wd",        required = false) String srchWd,
            @RequestParam(value = "sort_field",     required = false) String sortField,
            @RequestParam(value = "sort_direction", required = false) String sortDirection,
            @RequestParam(value = "size",           defaultValue = "100") int size) {
        return ApiResponse.ok(service.listManagementCards(srchWd, sortField, sortDirection, size));
    }

    /**
     * Toggle the {@code ACTV_YN} switch on one card. Mirrors legacy
     * {@code card_0001_003_u001}.
     *
     * <p>Errors:
     * <ul>
     *   <li>{@code WBE_CARD_TRANSACTIONS_NOT_FOUND} — card missing OR
     *       wrong tenant (collapsed into one envelope).</li>
     *   <li>{@code WBE_VALIDATION} — bean-validation failure.</li>
     * </ul>
     */
    @PatchMapping("/cards/{bank_cd}/{card_no}/active")
    public ApiResponse<Void> updateCardActive(
            @PathVariable("bank_cd")
            @NotBlank @Pattern(regexp = BANK_CD_PATTERN,
                    message = "bank_cd: invalid format") String bankCd,
            @PathVariable("card_no")
            @NotBlank @Pattern(regexp = CARD_NO_PATTERN,
                    message = "card_no: invalid format")  String cardNo,
            @Valid @RequestBody UpdateCardActiveRequest req) {
        service.updateCardActive(bankCd, cardNo, req);
        return ApiResponse.ok(null);
    }

    /**
     * Inline-rename — update {@code CARD_HLDR_NM} + {@code CARD_NICK_NM}.
     * Mirrors legacy {@code card_0001_003_u002}.
     */
    @PatchMapping("/cards/{bank_cd}/{card_no}")
    public ApiResponse<Void> updateCardInfo(
            @PathVariable("bank_cd")
            @NotBlank @Pattern(regexp = BANK_CD_PATTERN,
                    message = "bank_cd: invalid format") String bankCd,
            @PathVariable("card_no")
            @NotBlank @Pattern(regexp = CARD_NO_PATTERN,
                    message = "card_no: invalid format")  String cardNo,
            @Valid @RequestBody UpdateCardInfoRequest req) {
        service.updateCardInfo(bankCd, cardNo, req);
        return ApiResponse.ok(null);
    }

    /**
     * Hard-delete one card. Mirrors legacy {@code card_0001_003_d001}.
     *
     * <p>Errors:
     * <ul>
     *   <li>{@code WBE1153} — card has one or more rows in
     *       {@code CARD_TRNS_HSTR} (in-use guard, legacy parity).</li>
     *   <li>{@code WBE_CARD_TRANSACTIONS_NOT_FOUND} — card missing OR
     *       wrong tenant.</li>
     * </ul>
     */
    @DeleteMapping("/cards/{bank_cd}/{card_no}")
    public ApiResponse<Void> deleteCard(
            @PathVariable("bank_cd")
            @NotBlank @Pattern(regexp = BANK_CD_PATTERN,
                    message = "bank_cd: invalid format") String bankCd,
            @PathVariable("card_no")
            @NotBlank @Pattern(regexp = CARD_NO_PATTERN,
                    message = "card_no: invalid format")  String cardNo) {
        service.deleteCard(bankCd, cardNo);
        return ApiResponse.ok(null);
    }

    /**
     * Bulk-create endpoint backing the Card Management modal's "Add
     * Card" flow — legacy {@code comm_0218_007_c001}. Posted by the
     * frontend after the NXISAS popup chain scrapes the user's cards
     * and they tick which ones to register.
     *
     * <p>Per-card dedupe on the composite PK
     * {@code (BIZ_KEY, BANK_CD, CARD_NO)} — already-registered cards
     * are silently skipped (no error, no overwrite). Response carries
     * both {@code inserted_count} and {@code skipped_count} so the
     * frontend can surface "Added X cards, Y were already registered".
     *
     * <p>Audit: one row per call (not per card) with
     * {@code WSVC_ID = "card-transactions"}, {@code refNo1 = bank_cd},
     * detail message arg = inserted count.
     */
    @PostMapping("/cards")
    public ApiResponse<CreateCardsResponse> createCards(
            @Valid @RequestBody CreateCardsRequest req) {
        return ApiResponse.ok(service.createCards(req));
    }

    /**
     * Update / sync — kick off the synchronous Coocon scrape across
     * every active card in the tenant that has a registered
     * {@code BANK_CERT_INFO} credential. Mirrors legacy
     * {@code nxisas_cardTranList.ts} "Real-Time Online Inquire"
     * button collapsed into a single backend pass.
     *
     * <p>Wire body carries a {@code yyyyMMdd} date range. Response
     * carries per-card success / inserted / skipped counts plus a
     * structured per-card error list so the frontend can surface
     * "Re-authenticate Woori 5559" / "Captcha required for BIDV …"
     * / "Scrape rate-limited" individually.
     *
     * <p>Synchronous: callers should expect this to take several
     * seconds (one Coocon round-trip per credential + one per card).
     * Failures don't abort the run — the orchestrator collects errors
     * and surfaces them all in the response.
     */
    /**
     * @deprecated Headless single-shot scrape — aborts QNA/OTP/CAPTCHA-gated
     *     banks (e.g. Shinhan 11704088) with {@code WBE_CARD_SYNC_NEEDS_RE_AUTH}.
     *     Superseded 2026-06-19 by the session/challenge-aware
     *     {@code POST /sync-session} flow (Update button now uses that).
     *     Retained for the non-challenge fallback path; do not remove
     *     without PO sign-off.
     */
    @Deprecated
    @PostMapping("/sync")
    public ApiResponse<SyncCardTransactionsResponse> sync(
            @Valid @RequestBody SyncCardTransactionsRequest req) {
        return ApiResponse.ok(syncService.sync(req));
    }

    /* ----------------------------------------------------------------
     *  Update Cards — session/challenge-aware scrape (Shinhan QNA, etc.)
     * ---------------------------------------------------------------- */

    /**
     * Start a Card Transactions Update scrape session. Reuses the shared
     * NXISAS session/challenge framework (the same one Account Inquiry >
     * Bank Transaction Sync and Login Credentials use): runs
     * {@code LOGIN → (QNA/OTP/CAPTCHA?) → CARD_USING} per eligible card
     * and persists into {@code CARD_TRNS_HSTR}. On a challenge it returns
     * {@code *_REQUIRED} + a {@code session_id} the frontend echoes back
     * to the matching resume endpoint below.
     *
     * <p>Replaces the headless {@code POST /sync} for the Update button —
     * that path could not complete QNA-gated banks (Shinhan). See
     * {@link CardTransactionScrapeService}.
     */
    @PostMapping("/sync-session")
    public ApiResponse<CardSyncSessionResponse> startSyncSession(
            @Valid @RequestBody SyncCardTransactionsRequest req) {
        return ApiResponse.ok(scrapeService.start(req));
    }

    /** Resume a Card Update scrape after a QNA (security-question) answer. */
    @PostMapping("/sync-session/{sessionId}/qna")
    public ApiResponse<CardSyncSessionResponse> submitSyncSessionQna(
            @PathVariable UUID sessionId,
            @Valid @RequestBody SubmitQnaRequest req) {
        return ApiResponse.ok(scrapeService.submitQna(sessionId, req));
    }

    /** Resume a Card Update scrape after an OTP submission. */
    @PostMapping("/sync-session/{sessionId}/otp")
    public ApiResponse<CardSyncSessionResponse> submitSyncSessionOtp(
            @PathVariable UUID sessionId,
            @Valid @RequestBody SubmitChallengeRequest req) {
        return ApiResponse.ok(scrapeService.submitOtp(sessionId, req));
    }

    /** Resume a Card Update scrape after a CAPTCHA submission. */
    @PostMapping("/sync-session/{sessionId}/captcha")
    public ApiResponse<CardSyncSessionResponse> submitSyncSessionCaptcha(
            @PathVariable UUID sessionId,
            @Valid @RequestBody SubmitChallengeRequest req) {
        return ApiResponse.ok(scrapeService.submitCaptcha(sessionId, req));
    }

    /**
     * Update Cards popup &mdash; per-bank period-limit lookup. Mirrors
     * legacy {@code POST nxisas_cardTranList_r001.jct} feeding the
     * popup's {@code setLimitPeriod()} / date-range validator.
     *
     * <p>Caller posts {@code REC_IN = [{bank_cd, bank_cert_seq}, ...]};
     * service queries {@code BANK_INFM_DETL} where
     * {@code BANK_DETL_INFO_CD IN ('26','27')} joined through
     * {@code BANK_CERT_INFO.NXISAS_GB_SEQ_NO}; returns one row per
     * input pair with {@code cont1} (count) + {@code cont2} (unit).
     * Falls back to the legacy default ({@code 90 days}) when the
     * refactor hasn't seeded the per-bank rows yet.
     */
    @PostMapping("/period-limits")
    public ApiResponse<CardTranListPeriodLimitResponse> periodLimits(
            @Valid @RequestBody CardTranListPeriodLimitRequest req) {
        return ApiResponse.ok(cardTranListR001Service.fetch(req));
    }

    /**
     * Batch &quot;Request Approval&quot; for selected card-transaction
     * rows. Mirrors legacy {@code card_0001_001.ts:661-676} which
     * routed every Request Approval click through
     * {@code comm_0194_001.act} ({@code APPR_DV='33'}). Stamps
     * {@code APPR_SEQ_NO} on every selected row. See
     * {@code CardTransactionsService#requestApproval} for the
     * EXPT_RESOL_MSTR follow-up note.
     */
    // M-1 (legacy parity): multipart so Request Approval attachments ride along
    // (JSON in `request`, files in `attachments`); a no-file submit omits them.
    @PostMapping(path = "/request-approval",
                 consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<RequestApprovalResponse> requestApproval(
            @Valid @org.springframework.web.bind.annotation.RequestPart("request") RequestApprovalRequest req,
            @org.springframework.web.bind.annotation.RequestPart(value = "attachments", required = false)
            java.util.List<org.springframework.web.multipart.MultipartFile> attachments) {
        return ApiResponse.ok(service.requestApproval(req, attachments));
    }

    /**
     * Excel export — Card Transactions list. Mirrors
     * {@code POST /banking/accounts/export} (CLAUDE.md §"Excel export
     * — common/export shared pipeline"). The shared
     * {@link ExcelExportService} resolves
     * {@link com.kosign.wecms.domain.payment.cardtransactions.export.CardTransactionsExportProvider}
     * via {@code screenId="card-transactions"}, fetches all filtered +
     * sorted rows through {@link CardTransactionsService#listAllRows},
     * intersects the requested columns with the V203 View Setting seed
     * + the provider's allow-list, and streams XLSX bytes on the
     * response.
     *
     * <p>{@code POST} (not {@code GET}) so the column spec, filter
     * state, and sort travel as a JSON body without query-string
     * length limits.
     */
    @PostMapping("/export")
    public void exportCardTransactions(
            @Valid @RequestBody ExcelExportRequest request,
            HttpServletResponse response) {
        excelExportService.exportTo(response, request);
    }

    /**
     * Preview the next {@code VO_YYMMDD_NNN} document number for the
     * current tenant. Used by the Request Approval popup to populate
     * the read-only "Document No." field before the user submits —
     * Card has no parent batch row to source it from. The value is a
     * preview only; the authoritative number is allocated at submit.
     */
    @GetMapping("/next-doc-no")
    public ApiResponse<NextDocNoResponse> nextDocNo() {
        return ApiResponse.ok(service.previewNextDocNo());
    }

    /**
     * List e-Invoice rows eligible to be linked to a card transaction.
     * Backs the Expenses e-Invoice tab on the
     * "Link Card Payments with the Expenses" popup
     * (legacy {@code card_0001_002_r001.act}).
     *
     * <p>Filter chain (legacy parity):
     * <ul>
     *   <li>{@code DOC_TYPE='B'} + {@code HAND_REG_YN='N'} — GDT-synced
     *       Bills only; receipts go through a sibling endpoint.</li>
     *   <li>{@code PROV_STTS NOT IN ('EE','VD')}.</li>
     *   <li>{@code ISSU_DT BETWEEN from_date AND to_date}.</li>
     *   <li>NOT already linked ({@code BUSA_DOC_RCV_PAY_HIS}
     *       NOT EXISTS).</li>
     *   <li>Optional {@code search} on merchant name / display doc id.</li>
     * </ul>
     */
    @GetMapping("/links/e-invoices")
    public ApiResponse<LinkableEInvoiceListResponse> listLinkableEInvoices(
            @RequestParam("from_date") @Pattern(regexp = "^[0-9]{8}$") String fromDt,
            @RequestParam("to_date")   @Pattern(regexp = "^[0-9]{8}$") String toDt,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "page", required = false, defaultValue = "0")  int page,
            @RequestParam(value = "size", required = false, defaultValue = "50") int size) {
        return ApiResponse.ok(service.listLinkableEInvoices(fromDt, toDt, search, page, size));
    }

    /**
     * List manually-registered receipts eligible to be linked to a card
     * transaction. Backs the <i>Receipt</i> tab on the
     * "Link Card Payments with the Expenses" popup
     * (legacy {@code card_0001_002_r002.act}).
     *
     * <p>Same filter shape as {@link #listLinkableEInvoices} but
     * {@code DOC_TYPE='E'} + {@code HAND_REG_YN='Y'} and dates filter on
     * {@code USE_DT}.
     */
    @GetMapping("/links/receipts")
    public ApiResponse<LinkableReceiptListResponse> listLinkableReceipts(
            @RequestParam("from_date") @Pattern(regexp = "^[0-9]{8}$") String fromDt,
            @RequestParam("to_date")   @Pattern(regexp = "^[0-9]{8}$") String toDt,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "page", required = false, defaultValue = "0")  int page,
            @RequestParam(value = "size", required = false, defaultValue = "50") int size) {
        return ApiResponse.ok(service.listLinkableReceipts(fromDt, toDt, search, page, size));
    }

    /**
     * Link one or more expenses / receipts to a card transaction. Mirrors
     * legacy {@code card_0001_002_c001.act}. The sum of every
     * {@code dedu_amt} MUST equal the card transaction's
     * {@code TRSC_AMT} exactly (legacy {@code WBE0001}; refactor
     * {@code WBE_LINK_AMOUNT_MISMATCH}). Wraps the insert of one
     * {@code CARD_TRNS_RCV_PAY_INFM} row + N
     * {@code BUSA_DOC_RCV_PAY_HIS} rows in one transaction.
     */
    @PostMapping("/links")
    public ApiResponse<LinkExpensesResponse> linkExpenses(
            @Valid @RequestBody LinkExpensesRequest req) {
        return ApiResponse.ok(service.linkExpenses(req));
    }

    /**
     * Unlink every document linked to a card transaction. Mirrors
     * legacy {@code card_0001_002_u001.act}. Idempotent — calling it
     * on an unlinked card silently returns 0.
     */
    @DeleteMapping("/links/{card_seq_no}")
    public ApiResponse<Void> unlinkExpenses(
            @PathVariable("card_seq_no")
            @NotBlank @Size(max = 20) String cardSeqNo) {
        service.unlinkExpenses(cardSeqNo);
        return ApiResponse.ok(null);
    }

    /**
     * Create a manually-registered receipt. Mirrors legacy
     * {@code prch_0011_001} — writes one {@code BUSA_DOC_MSTR} row
     * ({@code DOC_TYPE='E'} + {@code HAND_REG_YN='Y'}) and returns the
     * newly-allocated {@code doc_id} so the frontend can select it
     * immediately on the Receipt tab.
     */
    @PostMapping("/receipts")
    public ApiResponse<CreateReceiptResponse> createReceipt(
            @Valid @RequestBody CreateReceiptRequest req) {
        return ApiResponse.ok(service.createReceipt(req));
    }

    /**
     * Read-only detail of one receipt / bill. Backs the View Detail
     * action on the Card Transactions list's linked-doc cell. Returns
     * {@code WBE_NOT_FOUND} when the doc is missing or in another
     * tenant. Mirrors legacy {@code card_0001_001.ts:337-350}
     * `.detail-receipt` / `.detail-e-invoice`.
     */
    @GetMapping("/receipts/{doc_id}")
    public ApiResponse<ReceiptDetailResponse> getReceiptDetail(
            @PathVariable("doc_id")
            @NotBlank @Size(max = 50) String docId) {
        return ApiResponse.ok(service.getReceiptDetail(docId));
    }

    /**
     * List Purpose options for the New Receipt popup's dropdown.
     * Mirrors legacy {@code prch_0011_001.ts:91} USAG combo. Returns
     * {@code USE_USAG_CD} + already-localized {@code usag_nm} per the
     * caller's {@code Accept-Language}.
     *
     * @param grpCd  {@code USAG_GRP_CD} filter; defaults to {@code "6"}
     *               (the New Receipt popup's bucket — manual expenses)
     */
    @GetMapping("/purpose-options")
    public ApiResponse<List<PurposeOption>> purposeOptions(
            @RequestParam(value = "grp_cd", required = false) String grpCd) {
        return ApiResponse.ok(service.listPurposeOptions(grpCd));
    }

    /**
     * List every memo attached to one card transaction. Backs the
     * side-panel memo drawer (legacy {@code card_0001_001.ts:1262-1276}
     * {@code getMemoPopup2}). Returns rows newest-first with author
     * display names resolved server-side.
     */
    @GetMapping("/memos")
    public ApiResponse<MemoListResponse> listMemos(
            @RequestParam("card_seq_no")
            @NotBlank @Size(max = 50) String cardSeqNo) {
        return ApiResponse.ok(service.listMemos(cardSeqNo));
    }

    /**
     * Add one memo to a card transaction. Mirrors legacy
     * {@code MEMO_INFM_C001} via the polymorphic
     * {@code wabooks.dao.MemoInfmDAO}.
     */
    @PostMapping("/memos")
    public ApiResponse<MemoRow> createMemo(
            @Valid @RequestBody CreateMemoRequest req) {
        return ApiResponse.ok(service.createMemo(req));
    }

    /**
     * Delete one memo. Hard delete (legacy {@code MEMO_INFM_D001}
     * parity). The refactor restricts delete to the memo's author —
     * non-authors get {@code WBE_MEMO_NOT_AUTHOR}.
     */
    @DeleteMapping("/memos/{seq_no}")
    public ApiResponse<Void> deleteMemo(
            @PathVariable("seq_no") java.math.BigDecimal seqNo) {
        service.deleteMemo(seqNo);
        return ApiResponse.ok(null);
    }
}
