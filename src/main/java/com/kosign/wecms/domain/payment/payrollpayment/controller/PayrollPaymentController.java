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
import com.kosign.wecms.domain.payment.internaltransfer.dto.WithdrawalAccountRow;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
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
public class PayrollPaymentController {

    /** Path-segment guard for {@code scdul_pymn_no} values. Accepts both
     *  the internal numeric key (5–30 digits) and the user-visible DOC_NO
     *  (VO_YYMMDD_NNN format) so the frontend can navigate to the detail
     *  page using either identifier. */
    private static final String SCDUL_PYMN_NO_PATTERN = "^([0-9]{5,30}|VO_[0-9]{6}_[0-9]{1,9})$";

    private final PayrollPaymentService       service;
    private final PayrollPaymentResultService resultService;
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
