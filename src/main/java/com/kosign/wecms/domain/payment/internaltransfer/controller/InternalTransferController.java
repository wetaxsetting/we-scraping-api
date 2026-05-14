package com.kosign.wecms.domain.payment.internaltransfer.controller;

import com.kosign.wecms.common.response.ApiResponse;
import com.kosign.wecms.domain.payment.internaltransfer.dto.CreateScheduledTransferRequest;
import com.kosign.wecms.domain.payment.internaltransfer.dto.CreateScheduledTransferResponse;
import com.kosign.wecms.domain.payment.internaltransfer.dto.DispatchRequest;
import com.kosign.wecms.domain.payment.internaltransfer.dto.ParseExcelResponse;
import com.kosign.wecms.domain.payment.internaltransfer.dto.ScheduledTransferDetailResponse;
import com.kosign.wecms.domain.payment.internaltransfer.dto.ScheduledTransferListResponse;
import com.kosign.wecms.domain.payment.internaltransfer.dto.UpdateReportingAccountRequest;
import com.kosign.wecms.domain.payment.internaltransfer.service.InternalTransferDispatchService;
import com.kosign.wecms.domain.payment.internaltransfer.service.InternalTransferService;
import com.kosign.wecms.domain.payment.internaltransfer.service.InternalTransferService.DownloadResource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Payment &gt; Internal Transfer — Scheduled Transfer endpoints
 * (the {@code pymn_0009_*} legacy family). Read + write surface.
 *
 * <p>Companion read-only Transfer Result endpoints live in
 * {@link TransferResultController}.
 *
 * <p>REST base path is {@code /api/v1/payment/internal-transfer} —
 * follows the project's {@code /api/v1/<module>/<slug>} convention
 * per {@code wecms-api-main/CLAUDE.md} §"Refactor identifier naming"
 * (canonical example {@code /api/v1/settings/firm-banking-settings}).
 *
 * <p>Wire format is lowercase snake_case via the global Jackson
 * {@code SNAKE_CASE} strategy on {@code @RequestBody} payloads; query
 * params explicitly pin their snake_case wire name via
 * {@code @RequestParam(value="...")} to match existing precedent
 * ({@link com.kosign.wecms.domain.payment.approvalbox.controller.ApprovalBoxController}).
 *
 * <p>Authorization: every endpoint is under {@code /api/v1/*} and
 * therefore {@code authenticated()} per
 * {@link com.kosign.wecms.config.SecurityConfig}. Per-row visibility +
 * tenant scope are enforced inside the service layer against the
 * JWT-bound tenant. {@code @PreAuthorize} role gating is a follow-up MR
 * mirroring the existing approval-box precedent (which also defers to
 * frontend gating today).
 *
 * <p>Audit logging: every state-changing endpoint writes one or more
 * rows to {@code AUDIT_LOGS_INFM} via
 * {@link com.kosign.wecms.common.auditlog.service.AuditLogService} with
 * {@code WSVC_ID = "internal-transfer"} (slug, not legacy
 * {@code pymn_0009_001}).
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/payment/internal-transfer")
@RequiredArgsConstructor
@Validated
public class InternalTransferController {

    /** Pattern for the {@code scdul_pymn_no} path component. Refactor
     *  format is {@code <yyyyMMdd><6-digit-seq>} per
     *  {@link com.kosign.wecms.domain.payment.internaltransfer.service.InternalTransferKeyGenerator}
     *  — 14 digits — but the regex stays permissive (5..30 digits) to
     *  accept legacy DEV-imported values during seed work. Tighter
     *  validation lives in the service layer's
     *  {@code findByBizKeyAndScdulPymnNoAndScdulPymnTypeCd} miss-path. */
    private static final String SCDUL_PYMN_NO_PATTERN = "^[0-9]{5,30}$";

    /** Pattern for {@code scdul_pymn_detl_no} — 1..20 chars per legacy
     *  column length, alphanumeric to permit legacy seed data. */
    private static final String SCDUL_PYMN_DETL_NO_PATTERN = "^[A-Za-z0-9]{1,20}$";

    private final InternalTransferService         service;
    private final InternalTransferDispatchService dispatchService;

    /* ----------------------------------------------------------------
     *  Read
     * ---------------------------------------------------------------- */

    /**
     * List Scheduled Transfer batches for the current tenant. Mirrors
     * legacy {@code pymn_0009_001_r001}.
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
            @RequestParam(value = "sort_field",     required = false) String sortField,
            @RequestParam(value = "sort_direction", required = false) String sortDirection,
            @RequestParam(value = "page",           defaultValue = "0")  int page,
            @RequestParam(value = "size",           defaultValue = "10") int size) {
        return ApiResponse.ok(service.list(
                fromDate, toDate, srchWd, sortField, sortDirection, page, size));
    }

    /**
     * Firm-banking-capable withdrawal accounts the current tenant can
     * use as the source of an Internal Transfer batch — drives the
     * Step-3 "Withdrawal Bank/Account No." picker in the upload
     * dialog (Figma 1:9972 — legacy {@code pymn_0009_002_v001}).
     *
     * <p>Mirrors legacy {@code comm_1001_001}. Optional {@code ccy_cd}
     * narrows the list to one currency (matches the legacy WSVC
     * input; the legacy implementation has the filter commented out
     * but we honour it for forward compatibility — the frontend can
     * pass the uploaded file's currency once parsing is complete).
     */
    @GetMapping("/withdrawal-accounts")
    public ApiResponse<java.util.List<com.kosign.wecms.domain.payment.internaltransfer.dto.WithdrawalAccountRow>>
            listWithdrawalAccounts(
                    @RequestParam(value = "ccy_cd", required = false) String ccyCd) {
        return ApiResponse.ok(service.listWithdrawalAccounts(ccyCd));
    }

    /**
     * Detail by composite PK. Returns master fields + per-line list.
     * Mirrors legacy {@code pymn_0009_003_r001}.
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
     * Stream the original Excel file uploaded with the batch — drives
     * the file-card download button on the Scheduled Transfer detail
     * page (Figma 8:599). Mirrors the ERP Transaction History contract
     * exactly: octet-stream body, RFC 5987 UTF-8 filename in
     * {@code Content-Disposition}, response length set from the
     * persisted {@code FILE_SIZE} column when available.
     *
     * <p>Errors:
     * <ul>
     *   <li>{@code WBE2060} — batch missing OR not in tenant.</li>
     *   <li>{@code WBE2015} — batch row exists but no file behind it
     *       (legacy DEV-imported, or pre-storage-rollout draft).</li>
     * </ul>
     *
     * <p>The {@code Content-Disposition filename} carries the user's
     * <em>original</em> Excel name (e.g. {@code "WeCMS_Internal_Transfer_upload_EN.xlsx"}),
     * NOT the {@code SAVE_FILE_NM} disk leaf — the user sees the same
     * filename they uploaded. Non-ASCII names round-trip via the
     * Spring {@code ContentDisposition} builder's RFC 5987 encoding.
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

    /* ----------------------------------------------------------------
     *  Excel upload — parse step (legacy pymn_0009_002_r001)
     * ---------------------------------------------------------------- */

    /**
     * Parse + per-line validate an Excel upload. Returns a preview
     * the frontend renders in the upload dialog before commit.
     *
     * <p>Phase A: ships as a local stub — accepts the file, validates
     * shape (non-empty, extension allow-list), returns an empty
     * preview. Real Apache POI parsing lands in a follow-up MR per
     * backend doc §13.
     */
    @PostMapping(path = "/uploads/parse",
                 consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ParseExcelResponse> parseExcel(
            @RequestPart("file_data")  MultipartFile fileData,
            // `bank_cd` is sent as a plain-text multipart field via
            // `FormData.append("bank_cd", "...")`. Browser FormData
            // doesn't stamp a per-part `Content-Type`, so
            // `@RequestPart String` fails the message-converter
            // lookup and surfaces as MissingServletRequestPartException.
            // `@RequestParam` binds simple form fields in multipart
            // requests without needing a part-level Content-Type.
            @RequestParam("bank_cd")   String        bankCd) {
        return ApiResponse.ok(service.parseExcel(fileData, bankCd));
    }

    /* ----------------------------------------------------------------
     *  Create draft batch (legacy pymn_0009_002_c001)
     * ---------------------------------------------------------------- */

    /**
     * Save a draft batch. {@code file_data} is the original Excel; the
     * {@code request} JSON-part carries the title + withdrawal account
     * info + per-line array.
     *
     * <p>Two-part multipart: {@code file_data} (binary) +
     * {@code request} (JSON). The frontend serialises the JSON body
     * with {@code Blob([JSON.stringify(req)], { type: "application/json" })}
     * — see {@code wecms-web-main/lib/api/internalTransfer.ts}.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<CreateScheduledTransferResponse> create(
            @Valid @RequestPart("request") CreateScheduledTransferRequest request,
            @RequestPart(value = "file_data", required = false) MultipartFile fileData) {
        return ApiResponse.ok(service.createDraft(request, fileData));
    }

    /* ----------------------------------------------------------------
     *  Update reporting account (legacy pymn_0009_002_u002)
     * ---------------------------------------------------------------- */

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

    /* ----------------------------------------------------------------
     *  Dispatch / Execute Transfer (legacy pymn_0009_001_c001)
     * ---------------------------------------------------------------- */

    /**
     * Execute the dispatch flow. Cut-off-time gate + audit row +
     * {@code TRNF_YN='Y'} stamp. The actual bank-network insert
     * ({@code TB_PAY_TARGET} / {@code TB_PAY_FILE_MST}) is a Phase B
     * follow-up — see {@link InternalTransferDispatchService}.
     */
    @PostMapping("/{id}/dispatch")
    public ApiResponse<Void> dispatch(
            @PathVariable("id")
            @Size(max = 30)
            @Pattern(regexp = SCDUL_PYMN_NO_PATTERN, message = "id: invalid scdul_pymn_no format")
            String id,
            @Valid @RequestBody DispatchRequest req) {
        dispatchService.dispatch(id, req);
        return ApiResponse.ok(null);
    }

    /* ----------------------------------------------------------------
     *  Delete draft batch (legacy pymn_0009_001_d001)
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

    /* ----------------------------------------------------------------
     *  Request Approval (legacy comm_0194_001 → comm_0192_001_c005)
     * ---------------------------------------------------------------- */

    /**
     * Submit one batch for approval. Mirrors the legacy
     * {@code comm_0192_001_c005} entry point at the network boundary —
     * the same shape the frontend's `<RequestApprovalDialog>` posts.
     *
     * <p>Phase A scope (see service Javadoc): the call stamps a
     * synthesised {@code APPR_SEQ_NO} on the master + writes an
     * audit row + echoes the new value back. The full per-leg
     * approval-line snapshot (legacy BLTB_POST_INFM /
     * EXPT_RESOL_MSTR fan-out) lands when those tables are ported.
     */
    @org.springframework.web.bind.annotation.PostMapping("/{id}/request-approval")
    public ApiResponse<com.kosign.wecms.domain.payment.internaltransfer.dto.RequestApprovalResponse>
    requestApproval(
            @PathVariable("id")
            @Size(max = 30)
            @Pattern(regexp = SCDUL_PYMN_NO_PATTERN, message = "id: invalid scdul_pymn_no format")
            String id,
            @Valid @RequestBody
            com.kosign.wecms.domain.payment.internaltransfer.dto.RequestApprovalRequest req) {
        return ApiResponse.ok(service.requestApproval(id, req));
    }

    /* ----------------------------------------------------------------
     *  Approval action — Approve / Reject (legacy comm_0195_001
     *  footer buttons → bltb_0001_003_u001)
     * ---------------------------------------------------------------- */

    /**
     * Approve or reject the active approval request on one batch.
     * Posted by the {@code <ApprovalDetailsDialog>} footer's Approve /
     * Reject buttons (Figma {@code comm_0195_001} frames 03 / 07 / 12
     * / 13; legacy `bltb_0001_003_u001`).
     *
     * <p>Phase A scope (see service Javadoc): the call stamps
     * {@code APPR_STTS = "C"} on Approve / {@code "X"} on Reject
     * directly on the master + writes an audit row. The per-approver
     * chain ({@code EXPT_RESOL_APPRVR_INFM}) lands when that table is
     * ported.
     */
    @PostMapping("/{id}/approval-action")
    public ApiResponse<Void> approvalAction(
            @PathVariable("id")
            @Size(max = 30)
            @Pattern(regexp = SCDUL_PYMN_NO_PATTERN, message = "id: invalid scdul_pymn_no format")
            String id,
            @Valid @RequestBody
            com.kosign.wecms.domain.payment.internaltransfer.dto.ApprovalActionRequest req) {
        service.actOnApproval(id, req);
        return ApiResponse.ok(null);
    }
}
