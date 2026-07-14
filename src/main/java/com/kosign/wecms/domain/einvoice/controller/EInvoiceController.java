package com.kosign.wecms.domain.einvoice.controller;

import com.kosign.wecms.common.export.ExcelExportRequest;
import com.kosign.wecms.common.export.ExcelExportService;
import com.kosign.wecms.common.response.ApiResponse;
import com.kosign.wecms.domain.einvoice.dto.EInvoiceDecryptRequest;
import com.kosign.wecms.domain.einvoice.dto.EInvoiceDecryptResponse;
import com.kosign.wecms.domain.einvoice.dto.EInvoiceDetailHistoryResponse;
import com.kosign.wecms.domain.einvoice.dto.EInvoiceDetailScrapeRequest;
import com.kosign.wecms.domain.einvoice.dto.EInvoiceDetailScrapeResult;
import com.kosign.wecms.domain.einvoice.dto.EInvoiceHistoryResponse;
import com.kosign.wecms.domain.einvoice.dto.EInvoiceListResponse;
import com.kosign.wecms.domain.einvoice.dto.EInvoiceScrapeRequest;
import com.kosign.wecms.domain.einvoice.dto.EInvoiceScrapeResult;
import com.kosign.wecms.domain.einvoice.service.EInvoiceDetailExportService;
import com.kosign.wecms.domain.einvoice.service.EInvoiceDetailScrapeService;
import com.kosign.wecms.domain.einvoice.service.EInvoiceHistoryService;
import com.kosign.wecms.domain.einvoice.service.EInvoiceScrapeDecryptService;
import com.kosign.wecms.domain.einvoice.service.EInvoiceScrapeService;
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
import org.springframework.security.access.prepost.PreAuthorize;

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
    private final EInvoiceScrapeService scrapeService;
    private final EInvoiceScrapeDecryptService scrapeDecryptService;
    private final EInvoiceDetailScrapeService detailScrapeService;
    private final EInvoiceHistoryService historyService;
    private final ExcelExportService excelExportService;
    private final EInvoiceDetailExportService detailExportService;

    @GetMapping("/einvoices")
    @PreAuthorize("@menuAuthz.canAccessEInvoice(#p0)")
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
            @RequestParam(value = "status", required = false)
            String status,
            @RequestParam(value = "page", required = false, defaultValue = "0")
            int page,
            @RequestParam(value = "size", required = false, defaultValue = "0")
            int size,
            @RequestParam(value = "sortField", required = false)
            String sortField,
            @RequestParam(value = "sortDirection", required = false)
            String sortDirection) {
        return ApiResponse.ok(service.list(
                type, fromDt, toDt, keyword, status, page, size, sortField, sortDirection));
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
    @PreAuthorize("@menuAuthz.canAccessAny('addon-einvoices-sales','addon-einvoices-expenses')")
    public void exportEInvoices(
            @Valid @RequestBody ExcelExportRequest request,
            HttpServletResponse response) {
        excelExportService.exportTo(response, request);
    }

    /**
     * "Download Excel with Details" — refactor port of the legacy
     * {@code einv_0001_001_r002_excel} detail-expansion export.
     *
     * <p>Same {@link ExcelExportRequest} body as the List export, but
     * routed to {@link EInvoiceDetailExportService}, which expands each
     * invoice into its {@code BUSA_DOC_DETL} / {@code EINV_FEE_DETL} /
     * {@code EINV_TAX_DETL} child rows (master columns repeated). The
     * request's {@code columns} (View-Setting list columns) are ignored —
     * the Details sheet uses its own fixed column set. Filters
     * ({@code type}/{@code from}/{@code to}/{@code keyword}) drive the
     * same query path as the list endpoint + the List export.
     */
    @PostMapping("/einvoices/export-details")
    @PreAuthorize("@menuAuthz.canAccessAny('addon-einvoices-sales','addon-einvoices-expenses')")
    public void exportEInvoiceDetails(
            @Valid @RequestBody ExcelExportRequest request,
            HttpServletResponse response) {
        detailExportService.exportTo(response, request);
    }

    /**
     * GDT engine-payload decrypt — refactor port of the legacy
     * {@code nxisas_decrypt} action for the e-Invoice scrape flow.
     *
     * <p>The browser drives the local NXiSAS engine with
     * {@code nonDecrypt:true} (legacy parity), so each job's
     * {@code Output.Result} comes back as Coocon SEED-CBC ciphertext. The
     * frontend posts the raw engine callback array here; the service
     * SEED-CBC-decrypts every {@code Output.Result} with the licensed
     * {@code ISASSeedCBC} ({@code libs/isas1.0.jar}) and returns the array
     * with each result replaced by its parsed plaintext JSON. The browser
     * then parses the phase-1 {@code invoiceList} (to build the phase-2
     * INVOICE_DETAIL jobs) and the phase-2 {@code invoiceDetail} (to build
     * the persist payload).
     *
     * <p>Decryption is unconditional — the GDT engine never returns
     * plaintext, so the scrape cannot proceed without it (OQ-S2 resolved
     * server-side, 2026-07-14). Tenant is read from the JWT.
     */
    @PostMapping("/einvoices/decrypt")
    @PreAuthorize("@menuAuthz.canAccessAny('addon-einvoices-sales','addon-einvoices-expenses')")
    public ApiResponse<EInvoiceDecryptResponse> decryptScrape(
            @Valid @RequestBody EInvoiceDecryptRequest request) {
        return ApiResponse.ok(scrapeDecryptService.decrypt(request));
    }

    /**
     * "Update" (scrape) save — refactor port of the legacy
     * {@code nxisas_einvoice_c001} save action.
     *
     * <p>The browser drives the local NXiSAS engine
     * ({@code LOGIN → INVOICE_LIST → INVOICE_DETAIL}), decrypts the engine
     * {@code Output.Result} server-side via {@link #decryptScrape} (OQ-S2
     * resolved 2026-07-14 — SEED-CBC through the shared
     * {@code SeedCbcDecryptService}), and POSTs the structured invoice rows
     * here. The service re-validates and persists the accepted rows to
     * {@code BUSA_DOC_MSTR} plus one {@code EXT_TRAN_CALL_HIS} audit row.
     *
     * <p>Tenant + user are read from the JWT via {@code TenantContext};
     * the body never carries {@code biz_key}.
     */
    @PostMapping("/einvoices/scrape-results")
    @PreAuthorize("@menuAuthz.canAccessAny('addon-einvoices-sales','addon-einvoices-expenses')")
    public ApiResponse<EInvoiceScrapeResult> saveScrapeResults(
            @Valid @RequestBody EInvoiceScrapeRequest request) {
        return ApiResponse.ok(scrapeService.saveScrapedInvoices(request));
    }

    /**
     * "Update" phase 2 (INVOICE_DETAIL) save — refactor port of the
     * legacy {@code nxisas_einvoice_c002} save action.
     *
     * <p>After phase 1 ({@link #saveScrapeResults}) persists the invoice
     * list, the browser drives the engine's {@code INVOICE_DETAIL} job per
     * listed invoice, decrypts the detail server-side via {@link #decryptScrape}
     * (OQ-S2 resolved 2026-07-14), and POSTs the structured line-item / fee
     * / tax payloads here. The service
     * enriches the existing {@code BUSA_DOC_MSTR} rows and (re)writes
     * {@code BUSA_DOC_DETL} / {@code EINV_FEE_DETL} / {@code EINV_TAX_DETL}.
     *
     * <p>Tenant + user are read from the JWT via {@code TenantContext}.
     */
    @PostMapping("/einvoices/scrape-detail-results")
    @PreAuthorize("@menuAuthz.canAccessAny('addon-einvoices-sales','addon-einvoices-expenses')")
    public ApiResponse<EInvoiceDetailScrapeResult> saveScrapeDetailResults(
            @Valid @RequestBody EInvoiceDetailScrapeRequest request) {
        return ApiResponse.ok(detailScrapeService.saveScrapedDetails(request));
    }

    /**
     * "Latest Inquiry History" — refactor port of legacy
     * {@code nxisas_einvhis_r001}. Reads back the GDT scrape calls
     * recorded in {@code EXT_TRAN_CALL_HIS} for the History popup.
     *
     * <ul>
     *   <li>{@code type} — optional {@code SALES} / {@code EXPENSE};
     *       omitted = both (legacy default {@code SVC_ID LIKE 'GDT_INQUIRY%'}).</li>
     *   <li>{@code from} / {@code to} — required {@code YYYYMMDD} window on
     *       the inquiry timestamp.</li>
     *   <li>{@code status} — optional {@code success} / {@code fail}
     *       (legacy {@code STTS} 0/1).</li>
     * </ul>
     * Tenant comes from the JWT via {@code TenantContext}.
     */
    @GetMapping("/einvoices/history")
    @PreAuthorize("@menuAuthz.canAccessEInvoice(#p0)")
    public ApiResponse<EInvoiceHistoryResponse> history(
            @RequestParam(value = "type", required = false)
            String type,
            @RequestParam("from")
            @NotBlank
            @Pattern(regexp = DATE_8, message = "from must be YYYYMMDD")
            String fromDt,
            @RequestParam("to")
            @NotBlank
            @Pattern(regexp = DATE_8, message = "to must be YYYYMMDD")
            String toDt,
            @RequestParam(value = "status", required = false)
            String status) {
        return ApiResponse.ok(historyService.list(type, fromDt, toDt, status));
    }

    /**
     * "Latest Inquiry — Detail" popup — refactor port of legacy
     * {@code nxisas_einvhis_dt_r001}. Drills into one GDT inquiry batch
     * (opened from the History popup's per-row "View" button) and lists
     * the {@code BUSA_DOC_MSTR} invoices it produced.
     *
     * <ul>
     *   <li>{@code type} — {@code SALES} or {@code EXPENSE} (legacy only
     *       rejects the distinct {@code 'E'} doc type, never Sales {@code 'I'}
     *       or Bill {@code 'B'}); the modal's Sales + Expenses View links both
     *       reach this endpoint.</li>
     *   <li>{@code from} / {@code to} — required {@code YYYYMMDD} issue-date
     *       window (the parent batch's start / end dates).</li>
     *   <li>{@code mod_dtm} — the batch timestamp (the phase-1
     *       {@code /scrape-results} {@code mod_dtm}, preserved through phase 2);
     *       matched exactly against {@code BUSA_DOC_MSTR.MOD_DTM}.</li>
     *   <li>{@code sortField} / {@code sortDirection} — optional; default
     *       {@code ISSU_DT DESC}. Whitelist: {@code DOC_ID}, {@code ISSU_DT},
     *       {@code TRCO_CMNM}, {@code TOTL_AMT}, {@code einv_invoiceStatus}.</li>
     * </ul>
     * Tenant comes from the JWT via {@code TenantContext}.
     */
    @GetMapping("/einvoices/detail-history")
    @PreAuthorize("@menuAuthz.canAccessEInvoice(#p0)")
    public ApiResponse<EInvoiceDetailHistoryResponse> detailHistory(
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
            @RequestParam("mod_dtm")
            @NotBlank
            @Pattern(regexp = "\\d{14}", message = "mod_dtm must be yyyyMMddHHmmss")
            String modDtm,
            @RequestParam(value = "sortField", required = false)
            String sortField,
            @RequestParam(value = "sortDirection", required = false)
            String sortDirection) {
        return ApiResponse.ok(historyService.detailHistory(
                type, fromDt, toDt, modDtm, sortField, sortDirection));
    }
}
