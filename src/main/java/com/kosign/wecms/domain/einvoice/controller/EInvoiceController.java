package com.kosign.wecms.domain.einvoice.controller;

import com.kosign.wecms.common.export.ExcelExportRequest;
import com.kosign.wecms.common.export.ExcelExportService;
import com.kosign.wecms.common.response.ApiResponse;
import com.kosign.wecms.domain.einvoice.dto.EInvoiceListResponse;
import com.kosign.wecms.domain.einvoice.service.EInvoiceService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Add-on Service — Sales / Bill e-Invoice listing endpoint.
 *
 * <p>Sales ({@code type=SALES}) and Bill / Expense ({@code type=EXPENSE}
 * or {@code type=BILL}) collapse to one endpoint with a {@code type}
 * filter. The service ({@link EInvoiceService#resolveDocType}) translates
 * the wire-level enum to the legacy DB-level {@code DOC_TYPE} code per
 * rule IR-1 ({@code SALES → 'I'}, {@code EXPENSE / BILL → 'B'}).
 *
 * <p><b>Date range is required</b> (rule IR-3) — both {@code from} and
 * {@code to} must be supplied as 8-digit {@code YYYYMMDD} strings.
 * Format validation runs here ({@code @Pattern}); calendar-validity
 * and ordering are enforced in the service. Format / ordering /
 * required-field violations all surface as {@code WBE_VALIDATION}
 * via {@link com.kosign.wecms.common.exception.GlobalExceptionHandler}.
 *
 * <p><b>Backend MR B query params</b> (rules IR-5, IR-6, plus pagination
 * + sort):
 * <ul>
 *   <li>{@code keyword} — case-insensitive partial match across the
 *       5-column subset {@code DOC_ID}, {@code TRCO_CMNM},
 *       {@code TRCO_TAX_ID}, {@code EINV_INVOICESYMBOL},
 *       {@code DISP_DOC_ID}. Blank / omitted = no keyword filter.</li>
 *   <li>{@code page} — zero-based page index. Default {@code 0};
 *       negative values clamped to {@code 0}.</li>
 *   <li>{@code size} — page size. Default
 *       {@link EInvoiceService#DEFAULT_PAGE_SIZE} ({@code 10});
 *       {@code <= 0} clamped to default; {@code > } {@link
 *       EInvoiceService#MAX_PAGE_SIZE} ({@code 100}) clamped down.</li>
 *   <li>{@code sortField} — View Setting {@code FIELD_ID}
 *       ({@code DATE_TIME}, {@code INVOICE_NO}, {@code BUYER},
 *       {@code CURRENCY}, {@code TOTAL}, {@code STATUS},
 *       {@code TAX}, {@code PRETAX}). Unknown / blank values
 *       silently fall back to {@code ISSU_DT DESC, REG_DTM DESC}.</li>
 *   <li>{@code sortDirection} — {@code ASC} or {@code DESC}.
 *       Unknown / blank → default sort.</li>
 * </ul>
 *
 * <p><b>Backend MR C — Excel export</b> (per CLAUDE.md "Excel export"
 * rule + the planning doc IR-9):
 * <ul>
 *   <li>{@code POST /api/v1/addon/einvoices/export} streams an XLSX
 *       blob. Sales / Bill collapse to one endpoint; the body's
 *       {@code screenId} ({@code addon-sales-einvoices} /
 *       {@code addon-expenses-einvoices}) selects the per-screen
 *       provider via {@link com.kosign.wecms.common.export.ExportableScreenRegistry}.</li>
 *   <li>Filters travel in the body's {@code filters} map
 *       ({@code from} / {@code to} / {@code keyword} / optional
 *       {@code type}). Validation rules from IR-3 still apply via
 *       {@link EInvoiceService#validateDateRange}.</li>
 *   <li>The export runs unpaged (rule IR-6) and reuses
 *       {@link EInvoiceService#list} so the export and on-screen list
 *       share one query path.</li>
 *   <li>Subheader is computed server-side per CLAUDE.md decision 5 —
 *       one line per CCY group from the totals query.</li>
 *   <li>Frontend ships visible+ordered View Setting columns; backend
 *       intersects with the per-screen allow-list AND the persisted
 *       View Setting visibility before writing.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/addon")
@RequiredArgsConstructor
@Validated
public class EInvoiceController {

    private static final String DATE_8 = "\\d{8}";

    private final EInvoiceService service;
    private final ExcelExportService excelExportService;

    @GetMapping("/einvoices")
    public ApiResponse<EInvoiceListResponse> list(
            @RequestParam("type")
            @NotBlank
            String type,
            @RequestParam("from")
            @NotBlank
            @Pattern(regexp = DATE_8, message = "from must be YYYYMMDD")
            String fromDt,
            @RequestParam("to")
            @NotBlank
            @Pattern(regexp = DATE_8, message = "to must be YYYYMMDD")
            String toDt,
            @RequestParam(value = "keyword", required = false)
            String keyword,
            @RequestParam(value = "page", required = false, defaultValue = "0")
            int page,
            @RequestParam(value = "size", required = false, defaultValue = "0")
            int size,
            @RequestParam(value = "sortField", required = false)
            String sortField,
            @RequestParam(value = "sortDirection", required = false)
            String sortDirection) {
        return ApiResponse.ok(service.list(
                type, fromDt, toDt, keyword, page, size, sortField, sortDirection));
    }

    /**
     * Per-resource Excel export endpoint (Backend MR C).
     *
     * <p>Body is the shared {@link ExcelExportRequest} shape — the
     * controller is intentionally thin. {@link ExcelExportService}
     * resolves the per-screen {@link com.kosign.wecms.common.export.ExportRowProvider}
     * (Sales or Bill) via the registry, intersects the requested
     * columns against the allow-list AND the persisted View Setting,
     * runs the filter+sort path through {@link EInvoiceService}, and
     * streams the workbook back. Errors travel through the standard
     * {@code ApiResponse} envelope when the response is still
     * committable; once bytes are flushed the connection is just closed.
     */
    @PostMapping("/einvoices/export")
    public void exportEInvoices(
            @Valid @RequestBody ExcelExportRequest request,
            HttpServletResponse response) {
        excelExportService.exportTo(response, request);
    }
}
