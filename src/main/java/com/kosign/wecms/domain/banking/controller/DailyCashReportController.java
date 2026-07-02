package com.kosign.wecms.domain.banking.controller;

import com.kosign.wecms.common.export.ExcelExportRequest;
import com.kosign.wecms.common.export.ExcelExportService;
import com.kosign.wecms.common.response.ApiResponse;
import com.kosign.wecms.domain.banking.dto.DailyCashReportResponse;
import com.kosign.wecms.domain.banking.service.DailyCashCurrencyFilter;
import com.kosign.wecms.domain.banking.service.DailyCashReportService;
import com.kosign.wecms.domain.banking.service.ReportsDownloadService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

/**
 * Daily Cash report (refactor route slug {@code daily-cash-report}).
 *
 * Replaces the legacy WSVC {@code rept_0009_001_r001}. See
 * {@code docs/refactor-wecms/features/daily-cash-report.md}.
 *
 * <p>F-3 Phase B-1: gated by {@code @menuAuthz.canAccess('banking-reports-daily-cash')}
 * (Daily Cash is an F/M product-tree leaf — H is denied). The export endpoint
 * shares the same cd as the report it serialises.
 */
@RestController
@RequestMapping("/api/v1/banking/reports/daily-cash-report")
@RequiredArgsConstructor
@Validated
public class DailyCashReportController {

    private final DailyCashReportService service;
    private final ExcelExportService excelExportService;
    /** PO-requested template-populated Daily Cash Excel Download — see
     *  {@code docs/audits/daily-cash-daily-financial-excel-download-audit.md}. */
    private final ReportsDownloadService reportsDownloadService;

    @GetMapping
    @PreAuthorize("@menuAuthz.canAccess('banking-reports-daily-cash')")
    public ApiResponse<DailyCashReportResponse> search(
            @RequestParam("asOfDate")
            @NotBlank
            @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$") String asOfDate,
            @RequestParam(value = "currency", required = false) String currency) {
        return ApiResponse.ok(service.search(asOfDate, DailyCashCurrencyFilter.parse(currency)));
    }

    /**
     * Generic Excel export — shared {@code ExcelExportService} pipeline.
     * Retained; the Daily Cash Excel button no longer calls it (it now calls
     * the dedicated template-populated {@code /report} endpoint below).
     */
    @PostMapping("/export")
    @PreAuthorize("@menuAuthz.canAccess('banking-reports-daily-cash')")
    public void export(@Valid @RequestBody ExcelExportRequest request, HttpServletResponse response) {
        excelExportService.exportTo(response, request);
    }

    /**
     * Excel Download for Daily Cash — the PO-requested template-populated
     * {@code .xlsx} report. Loads the locale template from the backend
     * classpath, populates the Current + Savings account sections preserving
     * all template styling, and streams {@code Daily Cash.xlsx} /
     * {@code 일일시재.xlsx} / {@code Báo cáo dòng tiền hàng ngày.xlsx}.
     *
     * <p>Dedicated report endpoint — deliberately NOT the generic
     * {@code POST /export} (which builds a workbook from scratch and cannot
     * preserve the template). Reuses {@link DailyCashReportService#search} so
     * the download and the on-screen report share one query path.
     *
     * @param asOfDate ISO {@code yyyy-MM-dd} report date (same as {@code search})
     * @param currency optional currency scope; blank → ALL
     * @param lang     optional UI locale (EN/KO/VI; KM/other → EN)
     */
    @GetMapping("/report")
    @PreAuthorize("@menuAuthz.canAccess('banking-reports-daily-cash')")
    public ResponseEntity<ByteArrayResource> downloadReport(
            @RequestParam("asOfDate")
            @NotBlank
            @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$") String asOfDate,
            @RequestParam(value = "currency", required = false) String currency,
            @RequestParam(value = "lang", required = false) String lang) {
        ReportsDownloadService.GeneratedReport report =
                reportsDownloadService.generateDailyCash(asOfDate, currency, lang);
        return reportResponse(report);
    }

    /**
     * Shared binary-response builder for the template-populated {@code .xlsx}
     * report download. RFC 5987 UTF-8 filename so the localized Korean /
     * Vietnamese names round-trip; OOXML XLSX MIME; {@code nosniff}.
     */
    static ResponseEntity<ByteArrayResource> reportResponse(
            ReportsDownloadService.GeneratedReport report) {
        ContentDisposition cd = ContentDisposition.builder("attachment")
                .filename(report.fileName(), StandardCharsets.UTF_8)
                .build();
        ByteArrayResource body = new ByteArrayResource(report.content());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .contentLength(body.contentLength())
                .header(HttpHeaders.CONTENT_DISPOSITION, cd.toString())
                .header("X-Content-Type-Options", "nosniff")
                .header("Access-Control-Expose-Headers", HttpHeaders.CONTENT_DISPOSITION)
                .body(body);
    }
}
