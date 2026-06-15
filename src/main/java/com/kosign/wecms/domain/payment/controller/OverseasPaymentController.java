package com.kosign.wecms.domain.payment.controller;

import com.kosign.wecms.common.export.ExcelExportRequest;
import com.kosign.wecms.common.export.ExcelExportService;
import com.kosign.wecms.common.response.ApiResponse;
import com.kosign.wecms.domain.payment.dto.ChangeApprovalLineRequest;
import com.kosign.wecms.domain.payment.dto.OverseasMultiApprovalRequest;
import com.kosign.wecms.domain.payment.dto.OverseasPaymentDetailResponse;
import com.kosign.wecms.domain.payment.dto.OverseasPaymentExcelPreview;
import com.kosign.wecms.domain.payment.dto.OverseasPaymentExcelUploadRequest;
import com.kosign.wecms.domain.payment.dto.OverseasPaymentExcelUploadResult;
import com.kosign.wecms.domain.payment.dto.OverseasResultListResponse;
import com.kosign.wecms.domain.payment.dto.OverseasResultRow;
import com.kosign.wecms.domain.payment.dto.OverseasScheduledListResponse;
import com.kosign.wecms.domain.payment.dto.OverseasScheduledRow;
import com.kosign.wecms.domain.payment.internaltransfer.dto.ApprovalActionRequest;
import com.kosign.wecms.domain.payment.internaltransfer.dto.RequestApprovalRequest;
import com.kosign.wecms.domain.payment.internaltransfer.dto.RequestApprovalResponse;
import com.kosign.wecms.domain.payment.internaltransfer.dto.WithdrawalAccountRow;
import com.kosign.wecms.domain.payment.common.WithdrawalAccountListService;
import com.kosign.wecms.domain.payment.service.OverseasPaymentExcelService;
import com.kosign.wecms.domain.payment.service.OverseasPaymentExcelService.DownloadResource;
import com.kosign.wecms.domain.payment.service.OverseasPaymentService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST controller for overseas payment.
 * Base path: {@code /api/v1/payment/overseas-payment}.
 *
 * <p>Mirrors the legacy {@code pymn_0007_001} JEX service.
 */
@RestController
@RequestMapping("/api/v1/payment/overseas-payment")
@RequiredArgsConstructor
@Validated
@PreAuthorize("@menuAuthz.canAccess('payment-overseas-request')")
public class OverseasPaymentController {

    private final OverseasPaymentService        overseasPaymentService;
    private final OverseasPaymentExcelService   overseasExcelService;
    private final WithdrawalAccountListService  withdrawalAccountListService;
    private final ExcelExportService            excelExportService;

    /**
     * Overseas scheduled-payment list.
     * Mirrors legacy {@code pymn_0007_001_r001}.
     *
     * <p>Wire parameters (all query-string, snake_case):
     * <ul>
     *   <li>{@code from_date} — required, yyyyMMdd</li>
     *   <li>{@code to_date}   — required, yyyyMMdd</li>
     *   <li>{@code appr_type_cd} — repeatable; filters by approval status
     *       (e.g. {@code APPROVED}, {@code PENDING}). Omit for all statuses.</li>
     *   <li>{@code keyword} — partial match on file title (type E) or beneficiary
     *       name (type M). Case-insensitive. Omit to skip.</li>
     *   <li>{@code page} — zero-based page index (default 0).</li>
     *   <li>{@code size} — rows per page; defaults to
     *       {@link OverseasPaymentService#DEFAULT_PAGE_SIZE},
     *       clamped to {@link OverseasPaymentService#MAX_PAGE_SIZE}.</li>
     * </ul>
     */
    @GetMapping("/scheduled")
    public ApiResponse<OverseasScheduledListResponse> listScheduled(
            @RequestParam("from_date")
            @Pattern(regexp = "^[0-9]{8}$", message = "from_date must be yyyyMMdd")
            String fromDt,

            @RequestParam("to_date")
            @Pattern(regexp = "^[0-9]{8}$", message = "to_date must be yyyyMMdd")
            String toDt,

            @RequestParam(value = "appr_type_cd", required = false)
            List<String> apprTypeCds,

            @RequestParam(value = "keyword", required = false)
            @Size(max = 100, message = "keyword must be ≤ 100 characters")
            String keyword,

            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "0") int size,

            @RequestParam(value = "sort_field",     required = false) String sortField,
            @RequestParam(value = "sort_direction",  required = false) String sortDirection) {

        return ApiResponse.ok(
                overseasPaymentService.list(fromDt, toDt, apprTypeCds, keyword, page, size, sortField, sortDirection));
    }

    /**
     * Single overseas scheduled-payment row by {@code OVRS_PYMN_NO}.
     */
    @GetMapping("/scheduled/{id}")
    public ApiResponse<OverseasScheduledRow> getScheduledById(@PathVariable("id") String id) {
        return ApiResponse.ok(overseasPaymentService.getById(id));
    }

    /**
     * Full batch detail (summary + per-line rows) for the Request Approval
     * and Approval Details dialogs.
     * Mirrors legacy {@code pymn_0007_001} approval-dialog load.
     */
    @GetMapping("/scheduled/{id}/details")
    public ApiResponse<OverseasPaymentDetailResponse> getScheduledDetail(
            @PathVariable("id") String id) {
        return ApiResponse.ok(overseasPaymentService.getDetail(id));
    }

    /**
     * Submit an unapproved overseas payment batch to the approval engine.
     * Phase A: stamps {@code APPR_SEQ_NO} on {@code OVRS_PYMN_SUMR} so the
     * list row transitions to "pending" status. Full {@code EXPT_RESOL_MSTR}
     * write is deferred.
     */
    // M-1 (legacy parity): multipart so Request Approval attachments ride along
    // (JSON in `request`, files in `attachments`); a no-file submit omits them.
    @PostMapping(path = "/{id}/request-approval", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<RequestApprovalResponse> requestApproval(
            @PathVariable("id") String id,
            @Valid @RequestPart("request") RequestApprovalRequest request,
            @RequestPart(value = "attachments", required = false)
            java.util.List<org.springframework.web.multipart.MultipartFile> attachments) {
        return ApiResponse.ok(overseasPaymentService.requestApproval(id, request, attachments));
    }

    /**
     * Submit a multi-payment ("M" type) overseas approval — approves a
     * list of {@code OVRS_PYMN_MSTR} rows under one {@code EXPT_RESOL_MSTR}.
     * Mirrors the legacy {@code comm_0192_001_c005} {@code OVRS_PYMN_TYPE='M'}
     * branch (one {@code EXPT_RESOL_DETL} per payment +
     * {@code APPR_SEQ_NO} stamped on every master row).
     */
    @PostMapping("/request-approval-multi")
    public ApiResponse<RequestApprovalResponse> requestApprovalMulti(
            @Valid @RequestBody OverseasMultiApprovalRequest request) {
        return ApiResponse.ok(overseasPaymentService.requestApprovalMulti(request));
    }

    /**
     * Replace the approver chain on a pending overseas payment batch.
     * Only allowed while {@code APPR_STTS = 'P'}.
     */
    @PatchMapping("/{id}/approval-line")
    public ApiResponse<Void> changeApprovalLine(
            @PathVariable("id") String id,
            @Valid @RequestBody ChangeApprovalLineRequest request) {
        overseasPaymentService.changeApprovalLine(id, request);
        return ApiResponse.ok(null);
    }

    /**
     * Retract the pending approval request on an overseas payment batch.
     * Deletes the {@code EXPT_RESOL_MSTR} + {@code EXPT_RESOL_APPRVR_INFM} rows
     * and resets {@code APPR_SEQ_NO} / {@code APPR_STTS} on {@code OVRS_PYMN_SUMR}
     * so the batch returns to draft state. Only allowed while {@code APPR_STTS = 'P'}.
     */
    @DeleteMapping("/{id}/approval-line")
    public ApiResponse<Void> removeApprovalLine(@PathVariable("id") String id) {
        overseasPaymentService.removeApprovalLine(id);
        return ApiResponse.ok(null);
    }

    /**
     * Delete a draft overseas payment batch (summary + detail rows).
     * Only allowed when the batch has never been submitted for approval
     * ({@code APPR_SEQ_NO} is null). Submitted batches must be retracted first
     * via {@link #removeApprovalLine} before they can be deleted.
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteDraftBatch(@PathVariable("id") String id) {
        overseasPaymentService.deleteDraftBatch(id);
        return ApiResponse.ok(null);
    }

    /**
     * Approve or reject an overseas payment batch.
     * Phase A: stamps {@code APPR_STTS} on {@code OVRS_PYMN_SUMR}. Full
     * {@code EXPT_RESOL_MSTR} write is deferred to Phase B.
     */
    @PostMapping("/{id}/approval-action")
    public ApiResponse<Void> approvalAction(
            @PathVariable("id") String id,
            @Valid @RequestBody ApprovalActionRequest request) {
        overseasPaymentService.actOnApproval(id, request);
        return ApiResponse.ok(null);
    }

    /**
     * Execute the dispatch ("Transfer") flow on an approved Overseas
     * batch. Writes {@code TB_PAY_FILE_MST} + {@code TB_PAY_FILE_DATA}
     * rows via the shared dispatch infrastructure and stamps
     * {@code TRNF_YN='Y'} + {@code TRNF_DT} on the master. Mirrors the
     * SCDUL_PYMN family's {@code POST .../dispatch} endpoint surface.
     *
     * <p>No request body — the dispatch is a pure server-side flip
     * (no currency override; Overseas always uses the File path and
     * the batch's own {@code CCY_CD} is the transfer currency).
     */
    @PostMapping("/{id}/dispatch")
    public ApiResponse<Void> dispatch(@PathVariable("id") String id) {
        overseasPaymentService.dispatch(id);
        return ApiResponse.ok(null);
    }

    /**
     * Overseas payment-result list.
     * Returns all batch records (OVRS_PYMN_SUMR) in the date range, ordered newest first.
     * {@code trnf_yn = "Y"} → completed; {@code "N"} → in progress.
     */
    @GetMapping("/result")
    @PreAuthorize("@menuAuthz.canAccess('payment-overseas-result')")
    public ApiResponse<OverseasResultListResponse> listResult(
            @RequestParam("from_date")
            @Pattern(regexp = "^[0-9]{8}$", message = "from_date must be yyyyMMdd")
            String fromDt,

            @RequestParam("to_date")
            @Pattern(regexp = "^[0-9]{8}$", message = "to_date must be yyyyMMdd")
            String toDt,

            @RequestParam(value = "keyword", required = false)
            @Size(max = 100)
            String keyword,

            /** {@code "Y"} = Completed only, {@code "N"} = In Progress only, absent = all. */
            @RequestParam(value = "trnf_yn", required = false)
            @Pattern(regexp = "^[YN]$", message = "trnf_yn must be Y or N")
            String trnfYn,

            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "0") int size,

            @RequestParam(value = "sort_field",     required = false) String sortField,
            @RequestParam(value = "sort_direction",  required = false) String sortDirection) {

        return ApiResponse.ok(overseasPaymentService.listResult(fromDt, toDt, keyword, trnfYn, page, size, sortField, sortDirection));
    }

    /**
     * Single overseas payment-result row by {@code OVRS_PYMN_NO}.
     */
    @GetMapping("/result/{id}")
    @PreAuthorize("@menuAuthz.canAccess('payment-overseas-result')")
    public ApiResponse<OverseasResultRow> getResultById(@PathVariable("id") String id) {
        return ApiResponse.ok(overseasPaymentService.getResultById(id));
    }

    /**
     * Streams the Overseas Payment Result XLSX for all batches matching the
     * current filter (same params as {@link #listResult}).
     * One Excel row per {@code OVRS_PYMN_DTLS} detail line.
     */
    @GetMapping("/result/export")
    @PreAuthorize("@menuAuthz.canAccess('payment-overseas-result')")
    public void exportResult(
            @RequestParam("from_date")
            @Pattern(regexp = "^[0-9]{8}$", message = "from_date must be yyyyMMdd")
            String fromDt,

            @RequestParam("to_date")
            @Pattern(regexp = "^[0-9]{8}$", message = "to_date must be yyyyMMdd")
            String toDt,

            @RequestParam(value = "keyword", required = false)
            @Size(max = 100)
            String keyword,

            @RequestParam(value = "trnf_yn", required = false)
            @Pattern(regexp = "^[YN]$", message = "trnf_yn must be Y or N")
            String trnfYn,

            HttpServletResponse response) throws IOException {

        overseasPaymentService.exportResult(fromDt, toDt, keyword, trnfYn, response);
    }

    /**
     * Assign or clear the reporting account for a single detail line.
     * Pass {@code rprt_acct_cd: null} to clear the assignment.
     */
    @PatchMapping("/{id}/details/{ovrsPymnDetlNo}/reporting-account")
    @PreAuthorize("@menuAuthz.canAccess('payment-overseas-result')")
    public ApiResponse<Void> saveReportingAccount(
            @PathVariable("id") String id,
            @PathVariable("ovrsPymnDetlNo") String ovrsPymnDetlNo,
            @RequestBody Map<String, String> body) {
        overseasPaymentService.saveReportingAccount(id, ovrsPymnDetlNo, body.get("rprt_acct_cd"));
        return ApiResponse.ok(null);
    }

    /**
     * Firm-banking-capable withdrawal accounts for the overseas payment
     * Excel upload dialog Step 3 ("Withdrawal Bank/Account No." picker).
     *
     * <p>Mirrors legacy {@code comm_1001_001} — module-agnostic, so it
     * delegates to the shared {@link WithdrawalAccountListService} (the
     * same strict G1/G2/G3 path every payment consumer uses). Overseas no
     * longer couples to {@code InternalTransferService} for this lookup.
     *
     * @param ccyCd optional currency filter (e.g. {@code "USD"}); omit
     *              for all firm-banking accounts regardless of currency.
     */
    @GetMapping("/withdrawal-accounts")
    public ApiResponse<List<WithdrawalAccountRow>> listWithdrawalAccounts(
            @RequestParam(value = "ccy_cd", required = false) String ccyCd) {
        return ApiResponse.ok(withdrawalAccountListService.list(ccyCd));
    }

    /**
     * Download Excel for a single overseas payment result batch
     * (OVRS_PYMN_SUMR + OVRS_PYMN_SUMR_DETL rows).
     *
     * <p>Injects the path-variable {@code id} as {@code ovrsPymnNo} into
     * the request's filter map before forwarding to the shared
     * {@link ExcelExportService}. The provider
     * {@code OverseasPaymentResultDetailExportProvider} reads that key and
     * loads the detail via {@link OverseasPaymentService#getDetail}.
     */
    @PostMapping("/result/{id}/export")
    @PreAuthorize("@menuAuthz.canAccess('payment-overseas-result')")
    public void exportResultDetail(
            @PathVariable("id") String id,
            @Valid @RequestBody ExcelExportRequest request,
            HttpServletResponse response) throws IOException {

        Map<String, Object> mergedFilters = new HashMap<>();
        if (request.filters() != null) mergedFilters.putAll(request.filters());
        mergedFilters.put("ovrsPymnNo", id);

        ExcelExportRequest merged = new ExcelExportRequest(
                request.screenId(),
                request.dymAreaId(),
                request.fileName(),
                request.title(),
                mergedFilters,
                request.sort(),
                request.columns(),
                request.subHeader()
        );
        excelExportService.exportTo(response, merged);
    }

    /**
     * Parse and validate an overseas payment Excel upload file.
     * Returns all rows (valid + invalid) with per-cell error details so
     * the frontend preview dialog can highlight errors inline.
     *
     * <p>Mirrors legacy {@code pymn_0007_001} batch-upload parse step.
     */
    @PostMapping(value = "/excel-preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<OverseasPaymentExcelPreview> excelPreview(
            @RequestPart("file")                              MultipartFile file,
            @RequestParam(value = "bank_cd", required = false) String       bankCd) {
        return ApiResponse.ok(overseasExcelService.preview(file, bankCd));
    }

    /**
     * Persist the overseas-payment Excel batch summary and save the
     * physical Excel file to the configured storage root.
     *
     * <p>Wire: {@code POST /api/v1/payment/overseas-payment/excel-upload}
     * as {@code multipart/form-data} with two parts:
     * <ul>
     *   <li>{@code file} — the original Excel file</li>
     *   <li>{@code data} — JSON body carrying the account selection and row totals</li>
     * </ul>
     * Returns the allocated {@code OVRS_PYMN_NO}.
     */
    @PostMapping(value = "/excel-upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<OverseasPaymentExcelUploadResult> excelUpload(
            @RequestPart("file") MultipartFile file,
            @RequestPart("data") @Valid OverseasPaymentExcelUploadRequest request) {
        return ApiResponse.ok(overseasExcelService.submit(request, file));
    }

    /**
     * Stream the original Excel file uploaded with an overseas payment batch.
     * Drives the file-card download button on the Scheduled Payment detail page.
     *
     * <p>Errors:
     * <ul>
     *   <li>{@code WBE2069} — batch has no attached file (legacy DEV-imported row,
     *       or pre-storage-rollout upload), or the file is missing / unreadable
     *       on the configured storage root.</li>
     * </ul>
     */
    @GetMapping("/scheduled/{id}/file")
    public ResponseEntity<InputStreamResource> downloadFile(
            @PathVariable("id") String id) throws IOException {
        DownloadResource resolved = overseasExcelService.resolveDownload(id);
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
