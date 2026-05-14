package com.kosign.wecms.domain.payment.approvalbox.controller;

import com.kosign.wecms.common.export.ExcelExportRequest;
import com.kosign.wecms.common.export.ExcelExportService;
import com.kosign.wecms.common.response.ApiResponse;
import com.kosign.wecms.domain.payment.approvalbox.dto.ApprovalBoxActionResponse;
import com.kosign.wecms.domain.payment.approvalbox.dto.ApprovalBoxBulkApproveRequest;
import com.kosign.wecms.domain.payment.approvalbox.dto.ApprovalBoxBulkApproveResponse;
import com.kosign.wecms.domain.payment.approvalbox.dto.ApprovalBoxListResponse;
import com.kosign.wecms.domain.payment.approvalbox.service.ApprovalBoxService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Payment &gt; Approval Box — REST endpoints. Mirrors the legacy
 * {@code WECMS DEV/WECMS/web/WEB-INF/action/jex/wabooks/resl/resl_0001_001_r001_act.jsp}
 * read path and the {@code comm_0195_001} approve / reject family.
 *
 * <p>Wire format is lowercase snake_case; Java parameter names stay
 * camelCase and Spring's {@code @RequestParam(value=…)} pins each one
 * to its snake_case wire counterpart. Validation lives in the service;
 * the controller is a thin pass-through that wraps the result in
 * {@link ApiResponse}.
 *
 * <p>Audit logging: every state-changing endpoint
 * ({@code POST /…/approve}, {@code POST /…/reject},
 * {@code POST /…/bulk-approve}) writes one or more rows to
 * {@code AUDIT_LOGS_INFM} via the shared
 * {@link com.kosign.wecms.common.auditlog.service.AuditLogService} with
 * {@code WSVC_ID = "approval-box"} (slug, not legacy
 * {@code resl_0001_001}).
 *
 * <p>Authorization: every endpoint is under {@code /api/v1/*} and
 * therefore {@code authenticated()} per {@code SecurityConfig}. Per-row
 * visibility (Redmine #42046) is enforced inside the service against
 * the JWT-bound tenant + user.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/payment/approval-box")
@RequiredArgsConstructor
public class ApprovalBoxController {

    private final ApprovalBoxService service;
    private final ExcelExportService excelExportService;

    /**
     * List Approval Box rows for the current tenant.
     *
     * @param fromDate       required {@code YYYYMMDD}
     * @param toDate         required {@code YYYYMMDD}, ≥ {@code fromDate}
     * @param srchWd         optional partial match
     * @param apprTypeCd     optional one of {@code 00}/{@code 01}/{@code 02}/{@code 03}
     * @param sortField      optional whitelisted FIELD_ID
     * @param sortDirection  optional {@code ASC}/{@code DESC}
     * @param page           zero-based page index (default {@code 0})
     * @param size           rows per page (default
     *                       {@link ApprovalBoxService#DEFAULT_PAGE_SIZE},
     *                       clamped to {@link ApprovalBoxService#MAX_PAGE_SIZE})
     */
    @GetMapping
    public ApiResponse<ApprovalBoxListResponse> list(
            @RequestParam("from_date")                                String fromDate,
            @RequestParam("to_date")                                  String toDate,
            @RequestParam(value = "srch_wd",        required = false) String srchWd,
            @RequestParam(value = "appr_type_cd",   required = false) String apprTypeCd,
            @RequestParam(value = "sort_field",     required = false) String sortField,
            @RequestParam(value = "sort_direction", required = false) String sortDirection,
            @RequestParam(value = "page",           defaultValue = "0") int page,
            @RequestParam(value = "size",           defaultValue = "0") int size) {
        return ApiResponse.ok(service.list(
                fromDate, toDate, srchWd, apprTypeCd,
                sortField, sortDirection, page, size));
    }

    /**
     * Approve one document on behalf of the authenticated user.
     * Mirrors legacy {@code comm_0195_001_uxxx} approve handler.
     */
    @PostMapping("/{apprSeqNo}/approve")
    public ApiResponse<ApprovalBoxActionResponse> approve(@PathVariable String apprSeqNo) {
        return ApiResponse.ok(service.approve(apprSeqNo));
    }

    /**
     * Reject one document on behalf of the authenticated user.
     * Cancels the workflow; downstream waiting peers cascade to
     * {@code STOPPED}.
     */
    @PostMapping("/{apprSeqNo}/reject")
    public ApiResponse<ApprovalBoxActionResponse> reject(@PathVariable String apprSeqNo) {
        return ApiResponse.ok(service.reject(apprSeqNo));
    }

    /**
     * Approve a batch of documents on behalf of the authenticated user.
     * Per-row outcome is partial — see
     * {@link ApprovalBoxBulkApproveResponse}.
     */
    @PostMapping("/bulk-approve")
    public ApiResponse<ApprovalBoxBulkApproveResponse> bulkApprove(
            @Valid @RequestBody ApprovalBoxBulkApproveRequest request) {
        return ApiResponse.ok(service.bulkApprove(request.apprSeqNos()));
    }

    /**
     * Excel export for Approval Box — shared common pipeline (see
     * {@link ExcelExportService}). Thin controller: validate the body,
     * hand it to the service, return nothing (the service streams XLSX
     * bytes straight into {@code response}).
     *
     * <p>Visible / ordered columns, current filters (date range, tab,
     * keyword), and current sort all flow through the request body; the
     * service intersects the column list with the persisted View Setting
     * visibility for the caller and re-runs
     * {@link ApprovalBoxService#list} via
     * {@link com.kosign.wecms.domain.payment.approvalbox.export.ApprovalBoxExportProvider}
     * so export and list share one query path.
     */
    @PostMapping("/export")
    public void export(
            @Valid @RequestBody ExcelExportRequest request,
            HttpServletResponse response) {
        excelExportService.exportTo(response, request);
    }
}
