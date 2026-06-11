package com.kosign.wecms.domain.payment.payrollpayment.controller;

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
import com.kosign.wecms.domain.payment.payrollpayment.dto.PayrollMatchCandidateListResponse;
import com.kosign.wecms.domain.payment.payrollpayment.dto.PayrollMatchRequest;
import com.kosign.wecms.domain.payment.payrollpayment.dto.PayrollMatchResultResponse;
import com.kosign.wecms.domain.payment.payrollpayment.service.PayrollBankingMatchService;
import com.kosign.wecms.domain.payment.payrollpayment.service.PayrollPaymentResultService;
import com.kosign.wecms.domain.payment.payrollpayment.service.PayrollPaymentService;
import com.kosign.wecms.domain.payment.payrollpayment.service.PayrollPaymentService.DownloadResource;
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
 * Payment &gt; Payroll Payment — REST endpoints. Mirrors the Internal
 * Transfer / Vendor / Expense controllers; Payroll, Vendor, Expense and
 * Internal Transfer share the physical {@code SCDUL_PYMN_SUMR} table —
 * the {@code SCDUL_PYMN_TYPE_CD = '00'} discriminator stamped by the
 * service layer keeps the modules isolated. Wire shape mirrors Internal
 * Transfer one-for-one so the frontend can share its wire types.
 *
 * <p>Legacy mapping:
 * <ul>
 *   <li>{@code pymn_0005_001} — Scheduled Payment list</li>
 *   <li>{@code pymn_0005_002} — Excel upload (parse + create)</li>
 *   <li>{@code pymn_0005_003} — Scheduled Payment detail</li>
 *   <li>{@code pymn_0006_001} — Payment Result list</li>
 *   <li>{@code pymn_0006_002} — Payment Result detail</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/payment/payroll-payment")
@RequiredArgsConstructor
@Validated
@PreAuthorize("@menuAuthz.canAccess('payment-payroll-scheduled')")
public class PayrollPaymentController {

    /** Path-segment guard for {@code scdul_pymn_no} values. Accepts both
     *  the internal numeric key (5–30 digits) and the user-visible DOC_NO
     *  (VO_YYMMDD_NNN format) so the frontend can navigate to the detail
     *  page using either identifier. */
    private static final String SCDUL_PYMN_NO_PATTERN = "^([0-9]{1,30}|VO_[0-9]{6}_[0-9]{1,9})$";

    /** Line-id format on {@code SCDUL_PYMN_SUMR_DETL.SCDUL_PYMN_DETL_NO}
     *  — alphanumeric, 1-20 chars (mirrors Internal Transfer). */
    private static final String SCDUL_PYMN_DETL_NO_PATTERN = "^[A-Za-z0-9]{1,20}$";

    private final PayrollPaymentService       service;
    private final PayrollPaymentResultService resultService;
    private final PayrollBankingMatchService  matchService;
    private final ExcelExportService          excelExportService;

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

    @GetMapping("/{id}")
    public ApiResponse<ScheduledTransferDetailResponse> detail(
            @PathVariable("id")
            @Size(max = 30)
            @Pattern(regexp = SCDUL_PYMN_NO_PATTERN, message = "id: invalid scdul_pymn_no format")
            String id) {
        return ApiResponse.ok(service.detail(id));
    }

    @GetMapping("/results")
    @PreAuthorize("@menuAuthz.canAccess('payment-payroll-result')")
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

    @GetMapping("/results/{id}")
    @PreAuthorize("@menuAuthz.canAccess('payment-payroll-result')")
    public ApiResponse<TransferResultDetailResponse> resultDetail(
            @PathVariable("id")
            @Size(max = 30)
            @Pattern(regexp = SCDUL_PYMN_NO_PATTERN, message = "id: invalid scdul_pymn_no format")
            String id) {
        return ApiResponse.ok(resultService.detail(id));
    }

    /** Excel export of the Payroll Payment Result list. Provider:
     *  {@code PayrollPaymentResultExportProvider} (CLAUDE.md §13b). */
    @PostMapping("/results/export")
    @PreAuthorize("@menuAuthz.canAccess('payment-payroll-result')")
    public void exportResults(
            @Valid @RequestBody ExcelExportRequest request,
            HttpServletResponse response) {
        excelExportService.exportTo(response, request);
    }

    /* ----------------------------------------------------------------
     *  Write actions
     * ---------------------------------------------------------------- */

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(
            @PathVariable("id")
            @Size(max = 30)
            @Pattern(regexp = SCDUL_PYMN_NO_PATTERN, message = "id: invalid scdul_pymn_no format")
            String id) {
        service.delete(id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/{id}/request-approval")
    public ApiResponse<RequestApprovalResponse> requestApproval(
            @PathVariable("id")
            @Size(max = 30)
            @Pattern(regexp = SCDUL_PYMN_NO_PATTERN, message = "id: invalid scdul_pymn_no format")
            String id,
            @Valid @RequestBody RequestApprovalRequest req) {
        return ApiResponse.ok(service.requestApproval(id, req));
    }

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

    /** Withdraw an in-flight approval request — kebab "Delete" on the
     *  Approval Details popup. Mirrors the Internal Transfer contract. */
    @DeleteMapping("/{id}/approval")
    public ApiResponse<Void> withdrawApproval(
            @PathVariable("id")
            @Size(max = 30)
            @Pattern(regexp = SCDUL_PYMN_NO_PATTERN, message = "id: invalid scdul_pymn_no format")
            String id) {
        service.withdrawApproval(id);
        return ApiResponse.ok(null);
    }

    /** Replace the approver chain on an in-flight approval request —
     *  kebab "Edit" on the Approval Details popup. Mirrors the Internal
     *  Transfer contract. */
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
     * Update the chart-of-accounts assignment on one Payroll Payment
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

    /** Re-run one Result-detail line. Legacy `pymn_0006_002.ts:441-472`. */
    @PostMapping("/{id}/lines/{lineId}/rerun")
    public ApiResponse<Void> rerun(
            @PathVariable("id")
            @Size(max = 30)
            @Pattern(regexp = SCDUL_PYMN_NO_PATTERN, message = "id: invalid scdul_pymn_no format")
            String id,
            @PathVariable("lineId")
            @Size(max = 20)
            @Pattern(regexp = SCDUL_PYMN_DETL_NO_PATTERN, message = "lineId: invalid scdul_pymn_detl_no format")
            String lineId) {
        service.rerun(id, lineId);
        return ApiResponse.ok(null);
    }

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
     *  Banking match — Result detail (legacy pymn_0006_002 + comm_0197_*)
     *  Header-level / batch-level, 1:1. No Unmatch-All (Payroll). Phase 2:
     *  backend only — no frontend UI yet.
     * ---------------------------------------------------------------- */

    /** Candidate bank transactions for a Payroll batch (legacy
     *  {@code comm_0197_001_r001} batch path). Candidates are pinned to the
     *  batch's exact pay date + total amount; the popup's date range is
     *  non-filtering (legacy parity). */
    @GetMapping("/{id}/match/candidates")
    public ApiResponse<PayrollMatchCandidateListResponse> matchCandidates(
            @PathVariable("id")
            @Size(max = 30)
            @Pattern(regexp = SCDUL_PYMN_NO_PATTERN, message = "id: invalid scdul_pymn_no format")
            String id,
            @RequestParam(value = "srch_wd", required = false) String srchWd) {
        return ApiResponse.ok(matchService.listCandidates(id, srchWd));
    }

    /** Match a Payroll batch to a bank transaction (legacy
     *  {@code comm_0197_001_c001} batch path). */
    @PostMapping("/{id}/match")
    public ApiResponse<PayrollMatchResultResponse> match(
            @PathVariable("id")
            @Size(max = 30)
            @Pattern(regexp = SCDUL_PYMN_NO_PATTERN, message = "id: invalid scdul_pymn_no format")
            String id,
            @Valid @RequestBody PayrollMatchRequest req) {
        return ApiResponse.ok(matchService.match(id, req));
    }

    /** Unmatch a Payroll batch (legacy {@code comm_0197_002_u001} batch path).
     *  No Unmatch-All for Payroll. */
    @DeleteMapping("/{id}/match")
    public ApiResponse<PayrollMatchResultResponse> unmatch(
            @PathVariable("id")
            @Size(max = 30)
            @Pattern(regexp = SCDUL_PYMN_NO_PATTERN, message = "id: invalid scdul_pymn_no format")
            String id) {
        return ApiResponse.ok(matchService.unmatch(id));
    }

    /* ----------------------------------------------------------------
     *  Excel upload — withdrawal accounts · parse · save draft
     * ---------------------------------------------------------------- */

    @GetMapping("/withdrawal-accounts")
    public ApiResponse<List<WithdrawalAccountRow>> listWithdrawalAccounts(
            @RequestParam(value = "ccy_cd", required = false) String ccyCd) {
        return ApiResponse.ok(service.listWithdrawalAccounts(ccyCd));
    }

    @PostMapping(path = "/uploads/parse",
                 consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ParseExcelResponse> parseExcel(
            @RequestPart("file_data") MultipartFile fileData,
            @RequestParam("bank_cd")  String        bankCd) {
        return ApiResponse.ok(service.parseExcel(fileData, bankCd));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<CreateScheduledTransferResponse> create(
            @Valid @RequestPart("request") CreateScheduledTransferRequest request,
            @RequestPart(value = "file_data", required = false) MultipartFile fileData) {
        return ApiResponse.ok(service.createDraft(request, fileData));
    }

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
