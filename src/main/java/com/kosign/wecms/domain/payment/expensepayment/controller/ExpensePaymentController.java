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
import com.kosign.wecms.domain.payment.internaltransfer.dto.WithdrawalAccountRow;
import com.kosign.wecms.domain.payment.expensepayment.service.ExpensePaymentResultService;
import com.kosign.wecms.domain.payment.expensepayment.service.ExpensePaymentService;
import com.kosign.wecms.domain.payment.expensepayment.service.ExpensePaymentService.DownloadResource;
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
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Payment &gt; Expense Payment — REST endpoints. Phase A scope:
 *
 * <ul>
 *   <li>{@code GET /api/v1/payment/expense-payment} — Scheduled Payment
 *       list (legacy {@code pymn_0003_001_r001}).</li>
 *   <li>{@code GET /api/v1/payment/expense-payment/results} — Payment
 *       Result list (legacy {@code pymn_0004_001_r001}).</li>
 * </ul>
 *
 * <p>Detail / create / dispatch / request-approval endpoints land in
 * Phase B following the Internal Transfer baseline (see memory
 * {@code project_payment_module_reference.md}).
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
public class ExpensePaymentController {

    /** Path-segment guard for {@code scdul_pymn_no} values. Accepts both
     *  the internal numeric key (5–30 digits) and the user-visible DOC_NO
     *  (VO_YYMMDD_NNN format, e.g. VO_260521_002) so the frontend can
     *  navigate to the detail page using either identifier. */
    private static final String SCDUL_PYMN_NO_PATTERN = "^([0-9]{5,30}|VO_[0-9]{6}_[0-9]{1,9})$";

    private final ExpensePaymentService       service;
    private final ExpensePaymentResultService resultService;
    private final ExcelExportService          excelExportService;

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
     * List Expense Payment results (dispatched + not-yet-dispatched
     * batches with their per-currency totals). Mirrors legacy
     * {@code pymn_0004_001_r001}.
     */
    @GetMapping("/results")
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
    public ApiResponse<TransferResultDetailResponse> resultDetail(
            @PathVariable("id")
            @Size(max = 30)
            @Pattern(regexp = SCDUL_PYMN_NO_PATTERN, message = "id: invalid scdul_pymn_no format")
            String id) {
        return ApiResponse.ok(resultService.detail(id));
    }

    /** Excel export of the Expense Payment Result list. Provider:
     *  {@code ExpensePaymentResultExportProvider} (CLAUDE.md §13b). */
    @PostMapping("/results/export")
    public void exportResults(
            @Valid @RequestBody ExcelExportRequest request,
            HttpServletResponse response) {
        excelExportService.exportTo(response, request);
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

    /** Submit one batch for approval. Legacy {@code comm_0192_001_c005}. */
    @PostMapping("/{id}/request-approval")
    public ApiResponse<RequestApprovalResponse> requestApproval(
            @PathVariable("id")
            @Size(max = 30)
            @Pattern(regexp = SCDUL_PYMN_NO_PATTERN, message = "id: invalid scdul_pymn_no format")
            String id,
            @Valid @RequestBody RequestApprovalRequest req) {
        return ApiResponse.ok(service.requestApproval(id, req));
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
}
