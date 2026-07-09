package com.kosign.wecms.domain.payment.expensepayment.controller;

import com.kosign.wecms.common.export.ExcelExportRequest;
import com.kosign.wecms.common.export.ExcelExportService;
import com.kosign.wecms.common.response.ApiResponse;
import com.kosign.wecms.domain.payment.internaltransfer.dto.ApprovalActionRequest;
import com.kosign.wecms.domain.payment.internaltransfer.dto.CreateScheduledTransferRequest;
import com.kosign.wecms.domain.payment.internaltransfer.dto.CreateScheduledTransferResponse;
import com.kosign.wecms.domain.payment.internaltransfer.dto.DispatchRequest;
import com.kosign.wecms.domain.payment.internaltransfer.dto.ParseExcelResponse;
import com.kosign.wecms.domain.payment.internaltransfer.dto.RequestApprovalRequest;
import com.kosign.wecms.domain.payment.internaltransfer.dto.RequestApprovalResponse;
import com.kosign.wecms.domain.payment.internaltransfer.dto.ScheduledTransferDetailResponse;
import com.kosign.wecms.domain.payment.internaltransfer.dto.ScheduledTransferListResponse;
import com.kosign.wecms.domain.payment.internaltransfer.dto.TransferResultDetailResponse;
import com.kosign.wecms.domain.payment.internaltransfer.dto.TransferResultListResponse;
import com.kosign.wecms.domain.payment.internaltransfer.dto.UpdateReportingAccountRequest;
import com.kosign.wecms.domain.payment.internaltransfer.dto.WithdrawalAccountRow;
import com.kosign.wecms.domain.payment.internaltransfer.service.ScdulPymnTypeCd;
import com.kosign.wecms.domain.payment.match.dto.PaymentLineMatchCandidateListResponse;
import com.kosign.wecms.domain.payment.match.dto.PaymentLineMatchRequest;
import com.kosign.wecms.domain.payment.match.dto.PaymentLineMatchResultResponse;
import com.kosign.wecms.domain.payment.match.dto.PaymentLineUnmatchAllResponse;
import com.kosign.wecms.domain.payment.match.service.DomesticPaymentLineMatchService;
import com.kosign.wecms.domain.payment.expensepayment.service.ExpensePaymentDownloadService;
import com.kosign.wecms.domain.payment.expensepayment.service.ExpensePaymentResultService;
import com.kosign.wecms.domain.payment.expensepayment.service.ExpensePaymentService;
import com.kosign.wecms.domain.payment.expensepayment.service.ExpensePaymentService.DownloadResource;
import com.kosign.wecms.domain.setting.erptransactionhistory.service.ErpTrnsHistoryWriter;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.InputStreamResource;
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
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.multipart.MultipartFile;

/**
 * Payment &gt; Expense Payment — REST endpoints.
 *
 * <p>Full CRUD + workflow surface is implemented: Scheduled Payment list
 * (legacy {@code pymn_0003_001_r001}) + detail (legacy
 * {@code pymn_0003_002_r001}), Excel parse/preview + save draft (legacy
 * {@code pymn_0003_002_r001} / {@code _c001}), withdrawal-account lookup
 * (legacy {@code comm_1001_001}), request-approval / approval-action /
 * withdraw-approval / change-approval-line, dispatch (legacy
 * {@code pymn_0003_001_c001}), payment-line matching, original-file
 * download, and the Payment Result list + detail + export (legacy
 * {@code pymn_0004_001_r001} / {@code _002_r001}). (The earlier "Phase A
 * — list only; detail/create/dispatch/request-approval land in Phase B"
 * note was stale and was removed — audit G8.)
 *
 * <p>Expense, Vendor and Internal Transfer share the physical
 * {@code SCDUL_PYMN_SUMR} table; the {@code SCDUL_PYMN_TYPE_CD = '02'}
 * discriminator stamped by the service layer keeps the two modules
 * isolated. Wire shape mirrors Internal Transfer one-for-one so the
 * frontend can share its wire types.
 *
 * <p>Authorization: every endpoint is under {@code /api/v1/*} and
 * therefore {@code authenticated()} per {@code SecurityConfig}. Per-row
 * visibility + tenant scope live inside the service against the
 * JWT-bound tenant.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/payment/expense-payment")
@RequiredArgsConstructor
@Validated
@PreAuthorize("@menuAuthz.canAccess('payment-expense-scheduled')")
public class ExpensePaymentController {

    /** Path-segment guard for {@code scdul_pymn_no} values. Accepts both
     *  the internal numeric key (5–30 digits) and the user-visible DOC_NO
     *  (VO_YYMMDD_NNN format, e.g. VO_260521_002) so the frontend can
     *  navigate to the detail page using either identifier. */
    private static final String SCDUL_PYMN_NO_PATTERN = "^([0-9]{1,30}|VO_[0-9]{6}_[0-9]{1,9})$";

    /** Line-id format on {@code SCDUL_PYMN_SUMR_DETL.SCDUL_PYMN_DETL_NO}
     *  — alphanumeric, 1-20 chars (mirrors Internal Transfer). */
    private static final String SCDUL_PYMN_DETL_NO_PATTERN = "^[A-Za-z0-9]{1,20}$";

    private final ExpensePaymentService           service;
    private final ExpensePaymentResultService     resultService;
    private final ExpensePaymentDownloadService   downloadService;
    /** ERP_TRNS_HSTR 'W' audit writer for the generated-report downloads
     *  (Scheduled → code 03, Result → code 05). */
    private final ErpTrnsHistoryWriter            erpHistoryWriter;
    private final ExcelExportService              excelExportService;
    private final DomesticPaymentLineMatchService lineMatchService;

    /**
     * List Scheduled Expense Payment batches for the current tenant.
     * Mirrors legacy {@code pymn_0003_001_r001}.
     *
     * @param fromDate       required {@code YYYYMMDD}
     * @param toDate         required {@code YYYYMMDD} ≥ {@code fromDate}
     * @param srchWd         optional title / approval-ref LIKE
     * @param sortField      optional whitelisted FIELD_ID
     * @param sortDirection  optional {@code ASC} / {@code DESC}
     * @param page           zero-based page index (default {@code 0})
     * @param size           rows per page (default 10, max 200)
     */
    @GetMapping
    public ApiResponse<ScheduledTransferListResponse> list(
            @RequestParam("from_date")                                String fromDate,
            @RequestParam("to_date")                                  String toDate,
            @RequestParam(value = "srch_wd",        required = false) String srchWd,
            @RequestParam(value = "appr_type",      required = false) String apprType,
            @RequestParam(value = "sort_field",     required = false) String sortField,
            @RequestParam(value = "sort_direction", required = false) String sortDirection,
            @RequestParam(value = "page",           defaultValue = "0")  int page,
            @RequestParam(value = "size",           defaultValue = "10") int size) {
        return ApiResponse.ok(service.list(
                fromDate, toDate, srchWd, apprType, sortField, sortDirection, page, size));
    }

    /**
     * Preview the next {@code VO_YYMMDD_NNN} document number for the Request
     * Approval popup (read-only; reserves / persists nothing). Mirrors the
     * Internal Transfer / Card precedent so Expense Payment renders the same
     * friendly id format before submit.
     */
    @GetMapping("/next-doc-no")
    public ApiResponse<com.kosign.wecms.domain.payment.internaltransfer.dto.NextDocNoResponse> nextDocNo() {
        return ApiResponse.ok(service.previewNextDocNo());
    }

    /**
     * Scheduled Expense Payment detail. Mirrors legacy
     * {@code pymn_0003_002_r001}.
     */
    @GetMapping("/{id}")
    public ApiResponse<ScheduledTransferDetailResponse> detail(
            @PathVariable("id")
            @Size(max = 30)
            @Pattern(regexp = SCDUL_PYMN_NO_PATTERN, message = "id: invalid scdul_pymn_no format")
            String id) {
        return ApiResponse.ok(service.detail(id));
    }

    /**
     * Excel Download — the legacy 22-column populated report
     * (ports {@code pymn_0002_002_r002}, Expense branch / type {@code "02"}).
     * Loads the locale template from the backend classpath, populates one
     * row per transfer detail (incl. Contact Name / {@code TRCO_CMENM} at
     * column J) preserving all template styling, and streams
     * {@code WeCMS_Expense_Payment_download_{LANG}.xlsx}.
     *
     * <p>This is the artifact the Scheduled Payment list-row glyph and the
     * Scheduled detail file-card download — NOT the originally uploaded
     * Excel file (which {@code GET /{id}/file} still serves as the import
     * source). See {@code docs/audits/expense-payment-excel-download-audit.md}.
     *
     * @param id   batch key
     * @param lang optional UI-locale hint (EN/KO/VI; KM/other → EN). When
     *             omitted the JWT {@code langCd} is used.
     */
    @GetMapping("/{id}/report")
    public ResponseEntity<ByteArrayResource> downloadReport(
            @PathVariable("id")
            @Size(max = 30)
            @Pattern(regexp = SCDUL_PYMN_NO_PATTERN, message = "id: invalid scdul_pymn_no format")
            String id,
            @RequestParam(value = "lang", required = false) String lang) {
        // Scheduled list/detail report download → ERP_TRNS_HSTR 'W' / 03
        // (Expense Scheduled Payment). Audit the exact downloaded bytes.
        ExpensePaymentDownloadService.GeneratedReport report = downloadService.generate(id, lang);
        erpHistoryWriter.recordExportedBytes("03", report.content(), report.fileName());
        return reportResponse(report);
    }

    /**
     * List Expense Payment results (dispatched + not-yet-dispatched
     * batches with their per-currency totals). Mirrors legacy
     * {@code pymn_0004_001_r001}.
     */
    @GetMapping("/results")
    @PreAuthorize("@menuAuthz.canAccess('payment-expense-result')")
    public ApiResponse<TransferResultListResponse> listResults(
            @RequestParam("from_date")                                String fromDate,
            @RequestParam("to_date")                                  String toDate,
            @RequestParam(value = "srch_wd",        required = false) String srchWd,
            @RequestParam(value = "trnf_stts",      required = false) String trnfStts,
            @RequestParam(value = "sort_field",     required = false) String sortField,
            @RequestParam(value = "sort_direction", required = false) String sortDirection,
            @RequestParam(value = "page",           defaultValue = "0")  int page,
            @RequestParam(value = "size",           defaultValue = "10") int size) {
        return ApiResponse.ok(resultService.list(
                fromDate, toDate, srchWd, trnfStts,
                sortField, sortDirection, page, size));
    }

    /**
     * Expense Payment Result detail. Mirrors legacy
     * {@code pymn_0004_002_r001}.
     */
    @GetMapping("/results/{id}")
    @PreAuthorize("@menuAuthz.canAccess('payment-expense-result')")
    public ApiResponse<TransferResultDetailResponse> resultDetail(
            @PathVariable("id")
            @Size(max = 30)
            @Pattern(regexp = SCDUL_PYMN_NO_PATTERN, message = "id: invalid scdul_pymn_no format")
            String id) {
        return ApiResponse.ok(resultService.detail(id));
    }

    /**
     * Excel Download — the legacy 22-column report for an Expense Payment
     * Result batch. Same artifact as {@link #downloadReport} but routed
     * through the result-scoped endpoint so {@code payment-expense-result}
     * menu authorization applies (the report is per-batch, keyed only by
     * {@code SCDUL_PYMN_NO}). Drives the Result list-row glyph and the
     * Result detail file-card download.
     */
    @GetMapping("/results/{id}/report")
    @PreAuthorize("@menuAuthz.canAccess('payment-expense-result')")
    public ResponseEntity<ByteArrayResource> downloadResultReport(
            @PathVariable("id")
            @Size(max = 30)
            @Pattern(regexp = SCDUL_PYMN_NO_PATTERN, message = "id: invalid scdul_pymn_no format")
            String id,
            @RequestParam(value = "lang", required = false) String lang) {
        // Result list-row/detail report download → ERP_TRNS_HSTR 'W' / 05
        // (Expense Payment Result).
        ExpensePaymentDownloadService.GeneratedReport report = downloadService.generate(id, lang);
        erpHistoryWriter.recordExportedBytes("05", report.content(), report.fileName());
        return reportResponse(report);
    }

    /** Shared builder for the generated-report binary response (RFC 5987
     *  filename, XLSX content type, no-sniff). Mirrors the Vendor Payment /
     *  Internal Transfer controllers' {@code reportResponse}. */
    private static ResponseEntity<ByteArrayResource> reportResponse(
            ExpensePaymentDownloadService.GeneratedReport report) {
        ContentDisposition cd = ContentDisposition.builder("attachment")
                .filename(report.fileName(), StandardCharsets.UTF_8)
                .build();
        ByteArrayResource body = new ByteArrayResource(report.content());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .contentLength(body.contentLength())
                .header(HttpHeaders.CONTENT_DISPOSITION, cd.toString())
                .header("X-Content-Type-Options", "nosniff")
                .header("Access-Control-Expose-Headers", HttpHeaders.CONTENT_DISPOSITION)
                .body(body);
    }

    /** Excel export of the Expense Payment Result list (legacy 7-column summary
     *  via {@code ExpensePaymentResultExportProvider}, CLAUDE.md §13b).
     *  <b>Deprecated for the toolbar</b> (2026-06-30): the toolbar Excel now
     *  calls {@link #downloadResultBulkReport} for the full 22-column bulk
     *  template report. Retained for back-compat. */
    @PostMapping("/results/export")
    @PreAuthorize("@menuAuthz.canAccess('payment-expense-result')")
    public void exportResults(
            @Valid @RequestBody ExcelExportRequest request,
            HttpServletResponse response) {
        excelExportService.exportTo(response, request);
    }

    /**
     * Toolbar Excel — the legacy 22-column report for ALL batches in the
     * current Payment Result filter (bulk). Same locale template + column
     * layout (incl. Contact Name) as the per-row {@link #downloadResultReport},
     * with every filtered batch appended into one workbook (legacy
     * {@code pymn_0002_002_r003} bulk-report parity). Replaces the old
     * from-scratch 7-column summary for the toolbar.
     */
    @GetMapping("/results/report")
    @PreAuthorize("@menuAuthz.canAccess('payment-expense-result')")
    public ResponseEntity<ByteArrayResource> downloadResultBulkReport(
            @RequestParam("from_date")                           String fromDate,
            @RequestParam("to_date")                             String toDate,
            @RequestParam(value = "srch_wd",   required = false) String srchWd,
            @RequestParam(value = "trnf_stts", required = false) String trnfStts,
            @RequestParam(value = "lang",      required = false) String lang) {
        java.util.List<String> ids = resultService.dispatchedIdsForExport(fromDate, toDate, srchWd, trnfStts);
        // Bulk Result report download → ERP_TRNS_HSTR 'W' / 05 (Expense Result).
        ExpensePaymentDownloadService.GeneratedReport report = downloadService.generateBulk(ids, lang);
        erpHistoryWriter.recordExportedBytes("05", report.content(), report.fileName());
        return reportResponse(report);
    }

    /* ----------------------------------------------------------------
     *  Phase C — write actions (Delete · Request Approval ·
     *  Approval Action · Dispatch). Mirrors the Internal Transfer
     *  controller surface; deferred for Phase D: Excel upload,
     *  withdrawal-account picker, reporting-account PATCH, file
     *  download.
     * ---------------------------------------------------------------- */

    /** Delete a draft Expense Payment batch. Legacy {@code pymn_0003_001_d001}. */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(
            @PathVariable("id")
            @Size(max = 30)
            @Pattern(regexp = SCDUL_PYMN_NO_PATTERN, message = "id: invalid scdul_pymn_no format")
            String id) {
        service.delete(id);
        return ApiResponse.ok(null);
    }

    /** Submit one batch for approval. Legacy {@code comm_0192_001_c005}.
     *  M-1: multipart so attachments ride along (JSON in `request`, files in
     *  `attachments`); a no-file submit omits the `attachments` parts. */
    @PostMapping(path = "/{id}/request-approval", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<RequestApprovalResponse> requestApproval(
            @PathVariable("id")
            @Size(max = 30)
            @Pattern(regexp = SCDUL_PYMN_NO_PATTERN, message = "id: invalid scdul_pymn_no format")
            String id,
            @Valid @RequestPart("request") RequestApprovalRequest req,
            @RequestPart(value = "attachments", required = false)
            java.util.List<org.springframework.web.multipart.MultipartFile> attachments) {
        return ApiResponse.ok(service.requestApproval(id, req, attachments));
    }

    /** Approve / reject the active approval request. Legacy {@code bltb_0001_003_u001}. */
    @PostMapping("/{id}/approval-action")
    public ApiResponse<Void> approvalAction(
            @PathVariable("id")
            @Size(max = 30)
            @Pattern(regexp = SCDUL_PYMN_NO_PATTERN, message = "id: invalid scdul_pymn_no format")
            String id,
            @Valid @RequestBody ApprovalActionRequest req) {
        service.actOnApproval(id, req);
        return ApiResponse.ok(null);
    }

    /**
     * Withdraw an in-flight approval request — kebab "Delete" on the
     * Approval Details popup. Mirrors the Internal Transfer contract.
     */
    @DeleteMapping("/{id}/approval")
    public ApiResponse<Void> withdrawApproval(
            @PathVariable("id")
            @Size(max = 30)
            @Pattern(regexp = SCDUL_PYMN_NO_PATTERN, message = "id: invalid scdul_pymn_no format")
            String id) {
        service.withdrawApproval(id);
        return ApiResponse.ok(null);
    }

    /**
     * Replace the approver chain on an in-flight approval request —
     * kebab "Edit" on the Approval Details popup. Mirrors the Internal
     * Transfer contract.
     */
    @PutMapping("/{id}/approval-line")
    public ApiResponse<Void> changeApprovalLine(
            @PathVariable("id")
            @Size(max = 30)
            @Pattern(regexp = SCDUL_PYMN_NO_PATTERN, message = "id: invalid scdul_pymn_no format")
            String id,
            @Valid @RequestBody
            com.kosign.wecms.domain.payment.internaltransfer.dto.ChangeApprovalLineRequest req) {
        service.changeApprovalLine(id, req.apprLineNo());
        return ApiResponse.ok(null);
    }

    /**
     * Update the chart-of-accounts assignment on one Expense Payment
     * line. Mirrors Internal Transfer's contract — used by the
     * Scheduled detail page's inline picker.
     */
    @PatchMapping("/{id}/lines/{lineId}/reporting-account")
    public ApiResponse<Void> updateReportingAccount(
            @PathVariable("id")
            @Size(max = 30)
            @Pattern(regexp = SCDUL_PYMN_NO_PATTERN, message = "id: invalid scdul_pymn_no format")
            String id,
            @PathVariable("lineId")
            @Size(max = 20)
            @Pattern(regexp = SCDUL_PYMN_DETL_NO_PATTERN, message = "lineId: invalid scdul_pymn_detl_no format")
            String lineId,
            @Valid @RequestBody UpdateReportingAccountRequest req) {
        service.updateReportingAccount(id, lineId, req);
        return ApiResponse.ok(null);
    }


    /** Execute the dispatch flow. Legacy {@code pymn_0003_001_c001}. */
    @PostMapping("/{id}/dispatch")
    public ApiResponse<Void> dispatch(
            @PathVariable("id")
            @Size(max = 30)
            @Pattern(regexp = SCDUL_PYMN_NO_PATTERN, message = "id: invalid scdul_pymn_no format")
            String id,
            @Valid @RequestBody DispatchRequest req) {
        service.dispatch(id, req);
        return ApiResponse.ok(null);
    }

    /* ----------------------------------------------------------------
     *  Excel upload — withdrawal accounts · parse · save draft
     *  (legacy comm_1001_001 / pymn_0003_002_r001 / _c001)
     * ---------------------------------------------------------------- */

    /** Firm-banking-capable withdrawal accounts the current tenant can
     *  use as the source of a Expense Payment batch. Mirrors legacy
     *  {@code comm_1001_001}. Module-agnostic — same lookup the
     *  Internal Transfer endpoint exposes. */
    @GetMapping("/withdrawal-accounts")
    public ApiResponse<List<WithdrawalAccountRow>> listWithdrawalAccounts(
            @RequestParam(value = "ccy_cd", required = false) String ccyCd) {
        return ApiResponse.ok(service.listWithdrawalAccounts(ccyCd));
    }

    /** Parse + per-line validate an uploaded Expense Excel. Mirrors
     *  legacy {@code pymn_0003_002_r001} — Expense reuses the Internal
     *  Transfer template format, so the workbook walk is shared. */
    @PostMapping(path = "/uploads/parse",
                 consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ParseExcelResponse> parseExcel(
            @RequestPart("file_data") MultipartFile fileData,
            @RequestParam("bank_cd")  String        bankCd) {
        return ApiResponse.ok(service.parseExcel(fileData, bankCd));
    }

    /** Save a draft Expense batch. Mirrors legacy
     *  {@code pymn_0003_002_c001}. Two-part multipart: {@code file_data}
     *  (binary) + {@code request} (JSON). */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<CreateScheduledTransferResponse> create(
            @Valid @RequestPart("request") CreateScheduledTransferRequest request,
            @RequestPart(value = "file_data", required = false) MultipartFile fileData) {
        return ApiResponse.ok(service.createDraft(request, fileData));
    }

    /**
     * Stream the original Excel file uploaded with an Expense batch —
     * drives the file-card download glyph on the Scheduled Payment
     * detail page (Figma 24:4705) and the row-trailing download icon
     * on the list. Mirrors legacy {@code pymn_0001_003} download.
     *
     * <p>Errors:
     * <ul>
     *   <li>{@code WBE2060} — batch missing OR not in tenant.</li>
     *   <li>{@code WBE2069} — batch row exists but no file behind it
     *       (legacy DEV-imported, or pre-storage-rollout draft).</li>
     * </ul>
     */
    @GetMapping("/{id}/file")
    public ResponseEntity<InputStreamResource> downloadFile(
            @PathVariable("id")
            @Size(max = 30)
            @Pattern(regexp = SCDUL_PYMN_NO_PATTERN, message = "id: invalid scdul_pymn_no format")
            String id) throws IOException {
        DownloadResource resolved = service.resolveDownload(id);
        InputStream stream = Files.newInputStream(resolved.path());

        ContentDisposition cd = ContentDisposition.builder("attachment")
                .filename(resolved.orgFileNm(), StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(resolved.fileSize())
                .header(HttpHeaders.CONTENT_DISPOSITION, cd.toString())
                .header("X-Content-Type-Options", "nosniff")
                .header("Access-Control-Expose-Headers", HttpHeaders.CONTENT_DISPOSITION)
                .body(new InputStreamResource(stream));
    }

    /* ============================================================
     * Banking Match (Phase 2) — per-line, legacy comm_0197_* domestic path.
     * Expense batches carry SCDUL_PYMN_TYPE_CD = '02'.
     * ============================================================ */

    /**
     * Candidate bank transactions for an Expense payment line
     * (legacy {@code comm_0197_001_r001} domestic path).
     */
    @GetMapping("/{id}/lines/{lineId}/match/candidates")
    public ApiResponse<PaymentLineMatchCandidateListResponse> matchCandidates(
            @PathVariable("id")
            @Pattern(regexp = SCDUL_PYMN_NO_PATTERN, message = "id: invalid scdul_pymn_no format")
            String id,
            @PathVariable("lineId")
            @Pattern(regexp = SCDUL_PYMN_DETL_NO_PATTERN, message = "lineId: invalid scdul_pymn_detl_no format")
            String lineId,
            @RequestParam(value = "match_rmrk_yn", required = false) String matchRmrkYn,
            @RequestParam(value = "srch_wd",       required = false) String srchWd) {
        return ApiResponse.ok(lineMatchService.listCandidates(
                ScdulPymnTypeCd.EXPENSE.code(), id, lineId, matchRmrkYn, srchWd));
    }

    /** Match an Expense payment line to a bank transaction (legacy {@code comm_0197_001_c001}). */
    @PostMapping("/{id}/lines/{lineId}/match")
    public ApiResponse<PaymentLineMatchResultResponse> match(
            @PathVariable("id")
            @Pattern(regexp = SCDUL_PYMN_NO_PATTERN, message = "id: invalid scdul_pymn_no format")
            String id,
            @PathVariable("lineId")
            @Pattern(regexp = SCDUL_PYMN_DETL_NO_PATTERN, message = "lineId: invalid scdul_pymn_detl_no format")
            String lineId,
            @Valid @RequestBody PaymentLineMatchRequest req) {
        return ApiResponse.ok(lineMatchService.match(
                ScdulPymnTypeCd.EXPENSE.code(), id, lineId, req));
    }

    /** Unmatch a single Expense payment line (legacy {@code comm_0197_002_u001}). */
    @DeleteMapping("/{id}/lines/{lineId}/match")
    public ApiResponse<PaymentLineMatchResultResponse> unmatch(
            @PathVariable("id")
            @Pattern(regexp = SCDUL_PYMN_NO_PATTERN, message = "id: invalid scdul_pymn_no format")
            String id,
            @PathVariable("lineId")
            @Pattern(regexp = SCDUL_PYMN_DETL_NO_PATTERN, message = "lineId: invalid scdul_pymn_detl_no format")
            String lineId) {
        return ApiResponse.ok(lineMatchService.unmatch(
                ScdulPymnTypeCd.EXPENSE.code(), id, lineId));
    }

    /** Unmatch every matched line under an Expense batch (legacy {@code comm_0197_001_u001}). */
    @DeleteMapping("/{id}/matches")
    public ApiResponse<PaymentLineUnmatchAllResponse> unmatchAll(
            @PathVariable("id")
            @Pattern(regexp = SCDUL_PYMN_NO_PATTERN, message = "id: invalid scdul_pymn_no format")
            String id) {
        return ApiResponse.ok(lineMatchService.unmatchAll(ScdulPymnTypeCd.EXPENSE.code(), id));
    }
}
