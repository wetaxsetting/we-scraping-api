package com.kosign.wecms.domain.setting.erptransactionhistory.controller;

import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.export.ExcelExportRequest;
import com.kosign.wecms.common.export.ExcelExportService;
import com.kosign.wecms.common.response.ApiResponse;
import com.kosign.wecms.domain.setting.erptransactionhistory.dto.ErpTrnsHistoryListResponse;
import com.kosign.wecms.domain.setting.erptransactionhistory.service.ErpTrnsHistoryService;
import com.kosign.wecms.domain.setting.erptransactionhistory.service.ErpTrnsHistoryService.DownloadResource;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Settings &gt; ERP Transaction History — read-only audit ledger of
 * every file exchanged between WeCMS and the customer's ERP. Mirrors
 * legacy {@code comm_0219_001_r001_act.jsp} (list + counts) and
 * {@code comm_0219_001_r002_view.jsp} (download).
 *
 * <p>Wire format is lowercase snake_case; Java parameter names stay
 * camelCase and Spring's {@code @RequestParam(value=…)} pins each one
 * to its snake_case wire counterpart. Validation lives in the service;
 * the controller is a thin pass-through that wraps the result in
 * {@link ApiResponse} (for the list endpoint) or streams the file via
 * {@link InputStreamResource} (for the download endpoint).
 *
 * <p>Authorization: every endpoint is under {@code /api/v1/*} and
 * therefore {@code authenticated()} per {@code SecurityConfig}. No
 * per-row ACL — any normal customer user in the tenant can list and
 * download any row (mirrors the legacy WSVC contract).
 *
 * <p>Audit logging: this read-only feature does not write
 * {@code AUDIT_LOGS_INFM} rows. Mirrors the
 * {@link com.kosign.wecms.domain.setting.firmbankinghistory.controller.FirmBankingHistoryController}
 * precedent — the refactor restricts audit writes to mutating CRUD
 * (firm-banking-settings Create / Update). The legacy JEX
 * {@code mntLogYn=Y} filter that fires per request is intentionally
 * not ported for read paths.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/settings/erp-transaction-history")
@RequiredArgsConstructor
@PreAuthorize("@menuAuthz.canAccess('settings-erp-transaction-history')")
public class ErpTrnsHistoryController {

    private final ErpTrnsHistoryService service;
    private final ExcelExportService excelExportService;

    /**
     * List ERP-transaction-history rows for the current tenant.
     *
     * @param fromDate       required {@code YYYYMMDD}
     * @param toDate         required {@code YYYYMMDD}, ≥ {@code fromDate}
     * @param erpType        optional one of {@code W}/{@code E}/{@code D}
     * @param trnsTypeCd     optional 2-digit code {@code "01".."17"}
     * @param keyword        optional partial match on {@code reg_id}
     * @param sortField      optional whitelisted FIELD_ID
     *                       (see {@link com.kosign.wecms.domain.setting.erptransactionhistory.service.ErpTrnsHistorySortField})
     * @param sortDirection  optional {@code ASC}/{@code DESC}
     * @param page           zero-based page index (default {@code 0})
     * @param size           rows per page (default
     *                       {@link ErpTrnsHistoryService#DEFAULT_PAGE_SIZE},
     *                       clamped to
     *                       {@link ErpTrnsHistoryService#MAX_PAGE_SIZE})
     */
    @GetMapping
    public ApiResponse<ErpTrnsHistoryListResponse> list(
            @RequestParam("from_date")                                String fromDate,
            @RequestParam("to_date")                                  String toDate,
            @RequestParam(value = "erp_type",       required = false) String erpType,
            @RequestParam(value = "trns_type_cd",   required = false) String trnsTypeCd,
            @RequestParam(value = "keyword",        required = false) String keyword,
            @RequestParam(value = "sort_field",     required = false) String sortField,
            @RequestParam(value = "sort_direction", required = false) String sortDirection,
            @RequestParam(value = "page",           defaultValue = "0") int page,
            @RequestParam(value = "size",           defaultValue = "0") int size) {
        return ApiResponse.ok(service.list(
                fromDate, toDate, erpType, trnsTypeCd, keyword,
                sortField, sortDirection, page, size));
    }

    /**
     * Download the file attached to a row. Mirrors legacy
     * {@code comm_0219_001_r002_view.jsp}:
     * <ul>
     *   <li>Row not found / not in tenant → {@code WBE2014}.</li>
     *   <li>File missing / {@code FILE_SIZE = 0} → {@code WBE2015}.</li>
     * </ul>
     *
     * <p>Filename in {@code Content-Disposition} is the legacy
     * {@code SAVE_FILE_NM} (NAS storage name), not the user's original
     * filename — intentional for audit-trail parity with JEX
     * {@code FileUtils.downloadFileAsBinary(out, util, file, save_file_nm)}.
     * Non-ASCII filenames are RFC 5987 UTF-8-encoded so they round-trip
     * cleanly across browsers + frontend
     * {@code lib/file.ts downloadFile(url, filename)}.
     */
    @GetMapping("/{seqNo}/download")
    public ResponseEntity<InputStreamResource> download(@PathVariable long seqNo) throws IOException {
        if (seqNo <= 0) {
            // Defensive — the path-variable converter already rejects
            // non-numeric input via 400 envelopes through
            // GlobalExceptionHandler, but a "/0/download" call would
            // otherwise hit findById with id=0 and surface as WBE2014.
            // Surface as the explicit validation envelope instead so
            // the frontend can render a clearer error.
            throw new BusinessException("WBE_VALIDATION", "seq_no: must be a positive number");
        }
        DownloadResource resolved = service.resolveDownload(seqNo);
        InputStream stream = Files.newInputStream(resolved.path());

        ContentDisposition cd = ContentDisposition.builder("attachment")
                .filename(resolved.saveFileNm(), StandardCharsets.UTF_8)
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
     * Thumbnail variant — legacy {@code comm_0219_001_r002_view.jsp}
     * handles {@code ?cmd=getthumb} with a 640px JPEG via
     * {@code FileUtils.downloadThumbFileAsBinary}. Phase 2 deferred —
     * no current frontend consumer surfaces thumbnails. Surface as
     * {@code WBE9001} ("not yet implemented") so a future caller fails
     * loudly rather than seeing a misleading 404.
     */
    @GetMapping("/{seqNo}/thumbnail")
    public ApiResponse<Void> thumbnail(@PathVariable long seqNo) {
        log.warn("thumbnail endpoint not implemented (seqNo={})", seqNo);
        throw new BusinessException("WBE9001");
    }

    /**
     * Stream the filtered + sorted ERP-transaction-history rows as an
     * XLSX. Shared pipeline — see {@link ExcelExportService} for the
     * full contract (column intersection with View Setting, 50k-row
     * cap, RFC 5987 filename encoding). The provider
     * ({@code ErpTrnsHistoryExportProvider}) re-runs the same filter
     * path the list endpoint uses via {@code service.listAll}.
     */
    @PostMapping("/export")
    public void export(
            @Valid @RequestBody ExcelExportRequest request,
            HttpServletResponse response) {
        excelExportService.exportTo(response, request);
    }
}
