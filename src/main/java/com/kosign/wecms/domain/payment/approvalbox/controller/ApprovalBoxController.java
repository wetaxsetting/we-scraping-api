package com.kosign.wecms.domain.payment.approvalbox.controller;

import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.export.ExcelExportRequest;
import com.kosign.wecms.common.export.ExcelExportService;
import com.kosign.wecms.common.response.ApiResponse;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.payment.approvalbox.dto.ApprovalBoxActionResponse;
import com.kosign.wecms.domain.payment.approvalbox.dto.ApprovalBoxApprover;
import com.kosign.wecms.domain.payment.approvalbox.dto.ApprovalBoxRow;
import com.kosign.wecms.domain.payment.approvalbox.dto.ApprovalBoxBulkApproveRequest;
import com.kosign.wecms.domain.payment.approvalbox.dto.ApprovalBoxBulkApproveResponse;
import com.kosign.wecms.domain.payment.approvalbox.dto.ApprovalBoxListResponse;
import com.kosign.wecms.domain.payment.approvalbox.dto.ApprovalTransactionRow;
import com.kosign.wecms.domain.payment.approvalbox.dto.ChangeApprovalLineRequest;
import com.kosign.wecms.domain.payment.approvalbox.dto.EditApprovalRequest;
import com.kosign.wecms.domain.payment.approvalbox.service.ApprovalBoxDownloadService;
import com.kosign.wecms.domain.payment.approvalbox.service.ApprovalBoxService;
import com.kosign.wecms.domain.payment.approvalbox.service.ApprovalDetailService;
import com.kosign.wecms.domain.payment.approvalbox.service.ApprovalEditService;
import com.kosign.wecms.domain.payment.internaltransfer.dto.ApprovalAttachmentRow;
import com.kosign.wecms.domain.payment.internaltransfer.service.ApprovalAttachmentDownloadService;
import com.kosign.wecms.domain.setting.erptransactionhistory.service.ErpTrnsHistoryWriter;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.access.prepost.PreAuthorize;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

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
@PreAuthorize("@menuAuthz.canAccess('payment-approval-box')")
public class ApprovalBoxController {

    /** Audit-log slug for Approval Box edits (matches
     *  {@code ApprovalBoxService.AUDIT_WSVC_ID}). */
    private static final String AUDIT_WSVC_ID_APPROVAL_BOX = "approval-box";

    /** ERP transaction-type code for the Approval Box (Approval Result)
     *  WeCMS&rarr;ERP export audit — legacy {@code downloadProc.jsp:1381}
     *  {@code ErpTypeCd.APPROVAL_RESULT}. */
    private static final String ERP_TRNS_TYPE_APPROVAL_RESULT = "11";

    private final ApprovalBoxService service;
    private final ApprovalDetailService approvalDetailService;
    private final ExcelExportService excelExportService;
    private final ApprovalAttachmentDownloadService approvalAttachmentDownloadService;
    private final ApprovalEditService approvalEditService;
    /** Template-populate download report (PO-requested Excel Download). */
    private final ApprovalBoxDownloadService downloadService;
    /** Shared ERP_TRNS_HSTR writer for the WeCMS&rarr;ERP 'W' / "11" export audit. */
    private final ErpTrnsHistoryWriter erpHistoryWriter;
    /*Deploy v3*/
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
     * Real approver chain for one approval document (ordered by line).
     * Read-only; used by the payment Scheduled detail pages to render the
     * true {@code APPRVR_STTS} chips and gate Approve/Reject on the current
     * pending approver — legacy {@code comm_0195_001} / {@code APPR_YN}
     * parity. Tenant + user scoping is enforced in the service.
     */
    @GetMapping("/{apprSeqNo}/approvers")
    public ApiResponse<List<ApprovalBoxApprover>> approvers(@PathVariable String apprSeqNo) {
        return ApiResponse.ok(service.approvers(apprSeqNo));
    }

    /**
     * Common Approval Details master row — resolvable by either
     * {@code appr_seq_no} (direct) OR the legacy-safe {@code post_seq_no}
     * (+ optional {@code bltb_cd}) identity that header notifications carry.
     * Mirrors legacy {@code comm_0195_001} loading by {@code POST_SEQ_NO}
     * (resolved → {@code APPR_SEQ_NO} via {@code BLTB_POST_INFM}). This is a
     * fixed-path GET (not {@code /{apprSeqNo}/…}) so it never collides with
     * the per-approval sub-resource routes below.
     *
     * <p>Returns the same {@link ApprovalBoxRow} shape the list emits, so the
     * common popup renders header / status / approvers / content identically
     * from every entry point. {@code WBE_APPROVAL_DETAIL_NOT_FOUND} when the
     * approval is missing, withdrawn, or in another tenant.
     */
    @GetMapping("/detail")
    public ApiResponse<ApprovalBoxRow> detail(
            @RequestParam(value = "appr_seq_no", required = false) String apprSeqNo,
            @RequestParam(value = "post_seq_no", required = false) String postSeqNo,
            @RequestParam(value = "bltb_cd",     required = false) String bltbCd) {
        ApprovalBoxRow row = approvalDetailService.master(apprSeqNo, postSeqNo, bltbCd);
        if (row == null) {
            throw new BusinessException("WBE_APPROVAL_DETAIL_NOT_FOUND");
        }
        return ApiResponse.ok(row);
    }

    /**
     * Read-only per-line transactions for one approval — populates the
     * Transaction Details section of the Approval Details popup when it is
     * opened from a header notification (which carries only the
     * {@code APPR_SEQ_NO}). Mirrors the inline transaction rows legacy
     * {@code comm_0193_001_r001} returned.
     *
     * <p>Covers the four scheduled kinds (Internal Transfer / Vendor /
     * Expense / Payroll). Card lines are loaded by the frontend via the
     * existing card-by-approval path; Overseas line loading is deferred —
     * both return an empty list here (the popup still renders header /
     * approvers / content / attachments). A missing / wrong-tenant approval
     * also yields an empty list. See {@link ApprovalDetailService}.
     */
    @GetMapping("/{apprSeqNo}/transactions")
    public ApiResponse<List<ApprovalTransactionRow>> transactions(@PathVariable String apprSeqNo) {
        return ApiResponse.ok(approvalDetailService.transactions(apprSeqNo));
    }

    /**
     * Read-only attachment list for one approval (UNION of
     * {@code EXPT_RESOL_FILE} + {@code EXPT_RESOL_PAY_EVID_FILE}), so the
     * notification-opened popup can render the attachment chips. Each row's
     * {@code fileId} + {@code fileType} drive the existing
     * {@code GET …/{apprSeqNo}/attachments/{fileId}?fileType=} download.
     */
    @GetMapping("/{apprSeqNo}/attachments")
    public ApiResponse<List<ApprovalAttachmentRow>> attachments(@PathVariable String apprSeqNo) {
        return ApiResponse.ok(approvalDetailService.attachments(apprSeqNo));
    }

    /**
     * Stream one Approval Details attachment back to the caller. Shared by
     * every payment type the Approval Box popup can open (Internal Transfer
     * / Vendor / Expense / Payroll / Overseas) because the row is keyed by
     * {@code APPR_SEQ_NO} — the {@code EXPT_RESOL_MSTR} PK — not by the
     * per-type payment batch id.
     *
     * <p>The {@code fileType} query param routes to the correct legacy
     * table (GAP-ATT-1): {@code EXPT_RESOL_FILE} (requester uploads, id
     * column {@code FILE_ID}) vs {@code EXPT_RESOL_PAY_EVID_FILE} (payment
     * evidence, id column {@code EVID_FILE_ID}). It is optional and defaults
     * to {@code EXPT_RESOL_FILE}, so a caller that omits it keeps the legacy
     * behaviour. The two tables can share a short {@code fileId} for the
     * same approval, so {@code (apprSeqNo, fileId, fileType)} are validated
     * together by the resolver to avoid streaming the wrong file.
     *
     * <p>Tenant scope ({@code BIZ_KEY}) comes from {@code TenantContext} —
     * never a client value — so a caller cannot reach another tenant's
     * attachment. {@code WBE2069} when the row is missing in the resolved
     * table or the on-disk binary is unavailable.
     */
    @GetMapping("/{apprSeqNo}/attachments/{fileId}")
    public ResponseEntity<InputStreamResource> downloadAttachment(
            @PathVariable
            @Size(max = 15)
            @Pattern(regexp = "^[0-9]+$", message = "apprSeqNo: digits only")
            String apprSeqNo,
            @PathVariable
            @Size(max = 100)
            @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "fileId: invalid format")
            String fileId,
            @RequestParam(value = "fileType", required = false)
            @Pattern(regexp = "^(EXPT_RESOL_FILE|EXPT_RESOL_PAY_EVID_FILE)$",
                     message = "fileType: invalid value")
            String fileType) throws IOException {
        String bizKey = TenantContext.currentBizKey();
        ApprovalAttachmentDownloadService.DownloadFile resolved =
                approvalAttachmentDownloadService.resolve(bizKey, apprSeqNo, fileId, fileType);
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
     * Withdraw an in-flight approval for a non-card payment type
     * (Internal Transfer / Vendor / Expense / Payroll / Overseas).
     * Deletes the {@code EXPT_RESOL_MSTR} + {@code EXPT_RESOL_APPRVR_INFM}
     * rows and clears the source payment batch's stamp so it returns to
     * Draft. Card approvals keep their dedicated
     * {@code /card-transactions/by-approval/{apprSeqNo}} endpoint.
     * Mirrors legacy {@code comm_0195_001.ts deleteApproval()}.
     */
    @DeleteMapping("/{apprSeqNo}/withdraw")
    public ApiResponse<Void> withdraw(@PathVariable String apprSeqNo) {
        service.withdraw(apprSeqNo);
        return ApiResponse.ok(null);
    }

    /**
     * Change the approval line on an in-flight approval for a non-card
     * payment type — replaces the approver chain with the picked line's
     * approvers and resets the master to Pending. Mirrors legacy
     * {@code comm_0195_001.ts editApproval()}.
     */
    @PutMapping("/{apprSeqNo}/approval-line")
    public ApiResponse<Void> changeApprovalLine(
            @PathVariable String apprSeqNo,
            @Valid @RequestBody ChangeApprovalLineRequest req) {
        service.changeApprovalLine(apprSeqNo, req != null ? req.apprLineNo() : null);
        return ApiResponse.ok(null);
    }

    /**
     * Edit an in-flight (Pending / In Progress) approval for a non-card payment
     * type — title + approval content + approver line + attachment add/remove in
     * one multipart call. Mirrors legacy {@code comm_0192_013_u002}
     * ({@code comm_0195_001.ts editApproval()}). Requestor / admin only
     * (enforced in {@link ApprovalEditService}). The JSON {@code request} part
     * is {@link EditApprovalRequest}; {@code attachments} are the newly-added
     * files (legacy REC3). Card approvals use the dedicated
     * {@code /card-transactions/by-approval/{apprSeqNo}/edit} endpoint.
     */
    @PutMapping(value = "/{apprSeqNo}/edit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Void> edit(
            @PathVariable String apprSeqNo,
            @Valid @RequestPart("request") EditApprovalRequest request,
            @RequestPart(value = "attachments", required = false) List<MultipartFile> attachments) {
        approvalEditService.edit(apprSeqNo, request, attachments, AUDIT_WSVC_ID_APPROVAL_BOX);
        return ApiResponse.ok(null);
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

    /**
     * Approval Box Excel Download — PO-requested <b>template-based</b> report
     * (dedicated endpoint; does NOT reuse the generic {@link #export} path).
     * Loads the locale {@code .xls} template, populates the pre-styled rows
     * from {@link ApprovalBoxService#listForExport} (same filter / sort /
     * tenant path as the on-screen list), and streams the populated
     * {@code application/vnd.ms-excel} workbook. See
     * {@code docs/audits/approval-box-excel-download-audit.md}.
     *
     * <p>Preserves the legacy ERP audit: after the workbook is generated, one
     * {@code ERP_TRNS_HSTR} row is written with {@code TRNS_TYPE_CD = "11"}
     * (Approval Result) / {@code ERP_TYPE = "W"} via
     * {@link ErpTrnsHistoryWriter#recordExportedBytes} — the bytes analogue of
     * the generic provider's {@code recordExportedFile("11", …)} (legacy
     * {@code downloadProc.jsp:1380-1399}). The audit write is
     * {@code REQUIRES_NEW} + swallow-and-log, so it never fails the download.
     *
     * @param lang optional UI locale ({@code EN}/{@code KO}/{@code VI}); blank
     *             → JWT {@code langCd}; {@code KM}/other → EN.
     */
    @GetMapping("/report")
    public ResponseEntity<ByteArrayResource> report(
            @RequestParam("from_date")                                String fromDate,
            @RequestParam("to_date")                                  String toDate,
            @RequestParam(value = "srch_wd",        required = false) String srchWd,
            @RequestParam(value = "appr_type_cd",   required = false) String apprTypeCd,
            @RequestParam(value = "sort_field",     required = false) String sortField,
            @RequestParam(value = "sort_direction", required = false) String sortDirection,
            @RequestParam(value = "lang",           required = false) String lang) {
        ApprovalBoxDownloadService.GeneratedReport report = downloadService.generate(
                fromDate, toDate, srchWd, apprTypeCd, sortField, sortDirection, lang);

        // ERP 'W' / "11" (Approval Result) audit — bytes variant (legacy parity).
        erpHistoryWriter.recordExportedBytes(
                ERP_TRNS_TYPE_APPROVAL_RESULT, report.content(), report.fileName());

        ContentDisposition cd = ContentDisposition.builder("attachment")
                .filename(report.fileName(), StandardCharsets.UTF_8)
                .build();
        ByteArrayResource body = new ByteArrayResource(report.content());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.ms-excel"))
                .contentLength(report.content().length)
                .header(HttpHeaders.CONTENT_DISPOSITION, cd.toString())
                .header("X-Content-Type-Options", "nosniff")
                .header("Access-Control-Expose-Headers", HttpHeaders.CONTENT_DISPOSITION)
                .body(body);
    }
}
