package com.kosign.wecms.domain.setting.vasaccount.controller;

import com.kosign.wecms.common.export.ExcelExportRequest;
import com.kosign.wecms.common.export.ExcelExportService;
import com.kosign.wecms.common.response.ApiResponse;
import com.kosign.wecms.domain.setting.vasaccount.dto.VasAccountCreateRequest;
import com.kosign.wecms.domain.setting.vasaccount.dto.VasAccountExcelImportRequest;
import com.kosign.wecms.domain.setting.vasaccount.dto.VasAccountExcelImportResult;
import com.kosign.wecms.domain.setting.vasaccount.dto.VasAccountExcelPreview;
import com.kosign.wecms.domain.setting.vasaccount.dto.VasAccountListResponse;
import com.kosign.wecms.domain.setting.vasaccount.dto.VasAccountLookupResponse;
import com.kosign.wecms.domain.setting.vasaccount.dto.VasAccountRow;
import com.kosign.wecms.domain.setting.vasaccount.dto.VasAccountUpdateRequest;
import com.kosign.wecms.domain.setting.vasaccount.service.VasAccountExcelService;
import com.kosign.wecms.domain.setting.vasaccount.service.VasAccountService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
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

import java.util.List;

/**
 * Settings &gt; VAS Account — CRUD endpoints. Mirrors legacy
 * {@code comm_0217_001} (list), {@code comm_0217_004_r001} (detail),
 * {@code comm_0217_003_c001} (create), {@code comm_0217_004_u001}
 * (update), {@code comm_0217_001_d001} (soft delete).
 *
 * <p>Wire format is lowercase snake_case (global Jackson {@code SNAKE_CASE}
 * strategy); Java parameter names stay camelCase and Spring's
 * {@code @RequestParam(value=…)} pins each one to its snake_case wire
 * counterpart.
 *
 * <p>Authorization: every endpoint is under {@code /api/v1/*} and
 * therefore {@code authenticated()} per {@code SecurityConfig}. No
 * {@code @PreAuthorize} annotation yet — DN-1 (closed 2026-05-11):
 * hold until the project-wide auth-gate MR adds method security across
 * the codebase, then this controller picks up
 * {@code @PreAuthorize("hasRole('ADMIN')")} on every endpoint.
 *
 * <p>Excel preview / import endpoints added 2026-05-11 — mirror
 * legacy {@code comm_0217_002_r001} / {@code _c001}. The "Download
 * Template" button is served by static XLSX assets (legacy parity —
 * {@code comm_0217_002.ts fnDownloadExcelForm} pattern) shipped
 * under {@code wecms-web/public/file/excel/VN/}, no backend
 * endpoint involved.
 *
 * <p>XLSX export wired via the shared {@link ExcelExportService} + the
 * per-screen {@link com.kosign.wecms.domain.setting.vasaccount.export.VasAccountExportProvider}
 * registered against {@code screenId = "vas-account"}. The controller
 * is a thin shim — body validation, forward, stream — and the shared
 * pipeline handles visibility intersection, column ordering, POI
 * streaming, and the RFC 5987 filename.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/settings/vas-account")
@RequiredArgsConstructor
public class VasAccountController {

    private final VasAccountService      service;
    private final VasAccountExcelService excelService;
    private final ExcelExportService     excelExportService;

    /**
     * Filtered + paginated list.
     *
     * @param searchWd      optional substring on {@code vas_acct_cd} or {@code vas_acct_nm}
     * @param statuses      optional repeatable {@code stts} (A / I)
     * @param sortField     optional whitelisted FIELD_ID
     * @param sortDirection optional ASC / DESC
     * @param page          zero-based; default {@code 0}
     * @param size          rows per page; default {@link VasAccountService#DEFAULT_PAGE_SIZE},
     *                      clamped to {@link VasAccountService#MAX_PAGE_SIZE}
     */
    @GetMapping
    public ApiResponse<VasAccountListResponse> list(
            @RequestParam(value = "search_wd",      required = false) String       searchWd,
            @RequestParam(value = "stts",           required = false) List<String> statuses,
            @RequestParam(value = "sort_field",     required = false) String       sortField,
            @RequestParam(value = "sort_direction", required = false) String       sortDirection,
            @RequestParam(value = "page",           defaultValue = "0") int        page,
            @RequestParam(value = "size",           defaultValue = "0") int        size) {
        return ApiResponse.ok(service.list(
                searchWd, statuses, sortField, sortDirection, page, size));
    }

    /**
     * Lightweight active+live picker — mirrors legacy {@code comm_1002_001}.
     * Used by the Edit Bank Account dialog's VAS Account typeahead. Returns
     * only {@code STTS='A'} + {@code DEL_YN='N'} rows; capped server-side.
     *
     * <p>The full management list ({@link #list}) is paginated and includes
     * inactive rows; this endpoint is the read path for SELECTION ONLY.
     */
    @GetMapping("/lookup")
    public ApiResponse<VasAccountLookupResponse> lookup(
            @RequestParam(value = "search_wd", required = false) String searchWd) {
        return ApiResponse.ok(service.lookup(searchWd));
    }

    /** Detail by {@code vas_acct_seq_no}. 404-equivalent surfaces as
     *  {@code WBE2040} ("VAS Account not found in this tenant"). */
    @GetMapping("/{vasAcctSeqNo}")
    public ApiResponse<VasAccountRow> detail(@PathVariable Long vasAcctSeqNo) {
        return ApiResponse.ok(service.detail(vasAcctSeqNo));
    }

    /** Create a new VAS Account. Returns the persisted row with its
     *  server-assigned {@code vas_acct_seq_no}. */
    @PostMapping
    public ApiResponse<VasAccountRow> create(@Valid @RequestBody VasAccountCreateRequest req) {
        return ApiResponse.ok(service.create(req));
    }

    /** Update a VAS Account by {@code vas_acct_seq_no}. Returns the
     *  updated row. */
    @PutMapping("/{vasAcctSeqNo}")
    public ApiResponse<VasAccountRow> update(
            @PathVariable Long vasAcctSeqNo,
            @Valid @RequestBody VasAccountUpdateRequest req) {
        return ApiResponse.ok(service.update(vasAcctSeqNo, req));
    }

    /** Soft-delete (DEL_YN='Y'). Mirrors legacy comm_0217_001_d001 —
     *  refactor never hard-deletes (the legacy IDO defines DELETE but is
     *  unused). */
    @DeleteMapping("/{vasAcctSeqNo}")
    public ApiResponse<Void> delete(@PathVariable Long vasAcctSeqNo) {
        service.softDelete(vasAcctSeqNo);
        return ApiResponse.ok(null);
    }

    /* =================================================================
     *  Excel — preview / import (legacy comm_0217_002 family)
     *
     *  Note: the "Download Template" button is served by the static
     *  XLSX assets the legacy project ships under
     *  WECMS_STATIC/web/file/excel/VN/WeCMS_VAS_Accounts_Bulk_Upload_{EN,KO,VI}.xlsx
     *  — copied into wecms-web/public/file/excel/VN/ for the
     *  refactor. The frontend's "Download Template" button is a plain
     *  anchor + locale-aware URL; no backend endpoint involved.
     * ================================================================= */

    /**
     * Parse + validate the uploaded Excel file. Returns the preview
     * payload — every parsed row (valid + invalid) plus the
     * completed / error counts the dialog renders in its summary chips.
     *
     * <p>Wire is multipart/form-data with a single {@code file} part.
     * Validation errors per row are pre-localized server-side
     * (caller's Accept-Language / JWT lang_cd) so the preview UI
     * renders cell tooltips verbatim.
     */
    @PostMapping(value = "/excel/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<VasAccountExcelPreview> excelPreview(
            @RequestPart("file") MultipartFile file) {
        return ApiResponse.ok(excelService.preview(file));
    }

    /**
     * Persist the preview rows. Append mode rejects duplicate codes
     * against existing live rows; Replace mode soft-deletes every
     * live row before inserting. Atomic — both happen inside one
     * transaction so a failed insert rolls back the wipe.
     */
    @PostMapping(value = "/excel/import", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<VasAccountExcelImportResult> excelImport(
            @Valid @RequestBody VasAccountExcelImportRequest req) {
        return ApiResponse.ok(excelService.import_(req));
    }

    /**
     * Excel export — streams an XLSX of the filtered + sorted VAS Account
     * list. Shared {@link ExcelExportService} pipeline; per-screen
     * implementation is
     * {@link com.kosign.wecms.domain.setting.vasaccount.export.VasAccountExportProvider}
     * (registered automatically via {@code ExportableScreenRegistry}).
     *
     * <p>The wire body's {@code filters} map carries {@code searchWd} +
     * {@code statuses} (the same keys the list endpoint uses);
     * {@code sort} carries the same {@code field} + {@code direction}
     * the list endpoint accepts. {@code screenId} must equal
     * {@code "vas-account"}; unknown screenIds surface as {@code WBE2010}.
     */
    @PostMapping("/export")
    public void export(
            @Valid @RequestBody ExcelExportRequest request,
            HttpServletResponse response) {
        excelExportService.exportTo(response, request);
    }
}
