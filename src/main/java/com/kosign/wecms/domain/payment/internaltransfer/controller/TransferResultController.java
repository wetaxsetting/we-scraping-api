package com.kosign.wecms.domain.payment.internaltransfer.controller;

import com.kosign.wecms.common.export.ExcelExportRequest;
import com.kosign.wecms.common.export.ExcelExportService;
import com.kosign.wecms.common.response.ApiResponse;
import com.kosign.wecms.domain.payment.internaltransfer.dto.TransferResultDetailResponse;
import com.kosign.wecms.domain.payment.internaltransfer.dto.TransferResultListResponse;
import com.kosign.wecms.domain.payment.internaltransfer.service.InternalTransferDownloadService;
import com.kosign.wecms.domain.setting.erptransactionhistory.service.ErpTrnsHistoryWriter;
import com.kosign.wecms.domain.payment.internaltransfer.service.InternalTransferResultService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ResponseEntity;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * Payment &gt; Internal Transfer — Transfer Result endpoints (the
 * {@code pymn_0010_*} legacy family). Read-only.
 *
 * <p>Per the permission matrix at
 * {@code WECMS DEV/WECMS/web/view/jex/wabooks/comm/comm_0190_003_view.jsp:540}
 * the Transfer Result row carries only the {@code R} flag — no U / D / T.
 * Refactor mirrors: only {@code GET} endpoints.
 *
 * <p>See {@link InternalTransferController} class-Javadoc for the wire
 * format / auth / audit context that applies to the whole feature.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/payment/internal-transfer/results")
@RequiredArgsConstructor
@Validated
@PreAuthorize("@menuAuthz.canAccess('payment-internal-transfer-result')")
public class TransferResultController {

    private static final String SCDUL_PYMN_NO_PATTERN = "^([0-9]{1,30}|VO_[0-9]{6}_[0-9]{1,9})$";

    private final InternalTransferResultService   service;
    private final ExcelExportService              excelExportService;
    private final InternalTransferDownloadService downloadService;
    /** ERP_TRNS_HSTR 'W' audit writer for the Result generated-report
     *  download (code 17 = Internal Transfer Result). */
    private final ErpTrnsHistoryWriter            erpHistoryWriter;

    /**
     * List Transfer Result batches for the current tenant. Mirrors
     * legacy {@code pymn_0010_001_r001}.
     */
    @GetMapping
    public ApiResponse<TransferResultListResponse> list(
            @RequestParam("from_date")                                String fromDate,
            @RequestParam("to_date")                                  String toDate,
            @RequestParam(value = "srch_wd",        required = false) String srchWd,
            @RequestParam(value = "trnf_stts",      required = false) String trnfStts,
            @RequestParam(value = "sort_field",     required = false) String sortField,
            @RequestParam(value = "sort_direction", required = false) String sortDirection,
            @RequestParam(value = "page",           defaultValue = "0")  int page,
            @RequestParam(value = "size",           defaultValue = "10") int size) {
        return ApiResponse.ok(service.list(
                fromDate, toDate, srchWd, trnfStts, sortField, sortDirection, page, size));
    }

    /**
     * Transfer Result detail. Mirrors legacy {@code pymn_0010_002_r001}.
     */
    @GetMapping("/{id}")
    public ApiResponse<TransferResultDetailResponse> detail(
            @PathVariable("id")
            @Size(max = 30)
            @Pattern(regexp = SCDUL_PYMN_NO_PATTERN, message = "id: invalid scdul_pymn_no format")
            String id) {
        return ApiResponse.ok(service.detail(id));
    }

    /**
     * Excel export of the current Transfer Result list — the shared
     * {@code common/export} pipeline streams the XLSX bytes back per
     * CLAUDE.md §13b. Provider:
     * {@code InternalTransferResultExportProvider}
     * ({@code screenId = "payment-internal-transfer-result"}).
     *
     * <p><b>Deprecated for the toolbar</b> (2026-06-30): the toolbar Excel
     * now calls {@link #downloadBulkReport} so it produces the same
     * locale-template 21-column report as the per-row download (legacy
     * {@code pymn_0002_002_r003} bulk-report parity), not this 7-column
     * summary. Endpoint retained for any other consumer / back-compat.
     */
    @PostMapping("/export")
    public void exportResults(
            @Valid @RequestBody ExcelExportRequest request,
            HttpServletResponse response) {
        excelExportService.exportTo(response, request);
    }

    /**
     * Toolbar Excel — the legacy 21-column populated report for ALL batches
     * in the current Transfer Result filter (bulk). Same locale template and
     * column layout as the per-row {@link #downloadReport}, with every
     * filtered batch's transfer lines appended into one workbook (legacy
     * {@code pymn_0002_002_r003} bulk-report parity). Replaces the old
     * from-scratch 7-column {@code ExcelExportService} summary for the
     * toolbar. Filter params mirror {@link #list}.
     */
    @GetMapping("/report")
    public ResponseEntity<ByteArrayResource> downloadBulkReport(
            @RequestParam("from_date")                          String fromDate,
            @RequestParam("to_date")                            String toDate,
            @RequestParam(value = "srch_wd",   required = false) String srchWd,
            @RequestParam(value = "trnf_stts", required = false) String trnfStts,
            @RequestParam(value = "lang",      required = false) String lang) {
        java.util.List<String> ids = service.dispatchedIdsForExport(fromDate, toDate, srchWd, trnfStts);
        // Bulk Transfer Result report download → ERP_TRNS_HSTR 'W' / 17
        // (Internal Transfer Result), same audit code as the per-row report.
        InternalTransferDownloadService.GeneratedReport report = downloadService.generateBulk(ids, lang);
        erpHistoryWriter.recordExportedBytes("17", report.content(), report.fileName());
        return InternalTransferController.reportResponse(report);
    }

    /**
     * Excel Download — the legacy 21-column populated report
     * (ports {@code pymn_0010_002_r002}). Same generated artifact as the
     * Scheduled side ({@link InternalTransferController#downloadReport});
     * the Transfer Result list-row glyph and detail screen call this
     * result-scoped endpoint so the {@code payment-internal-transfer-result}
     * menu authorization applies. NOT the originally uploaded file.
     *
     * @param id   batch key
     * @param lang optional UI-locale hint (EN/KO/VI; KM/other → EN)
     */
    @GetMapping("/{id}/report")
    public ResponseEntity<ByteArrayResource> downloadReport(
            @PathVariable("id")
            @Size(max = 30)
            @Pattern(regexp = SCDUL_PYMN_NO_PATTERN, message = "id: invalid scdul_pymn_no format")
            String id,
            @RequestParam(value = "lang", required = false) String lang) {
        // Result list-row/detail report download → ERP_TRNS_HSTR 'W' / 17
        // (Internal Transfer Result). Same artifact as the Scheduled download,
        // recorded with the Result type code.
        InternalTransferDownloadService.GeneratedReport report = downloadService.generate(id, lang);
        erpHistoryWriter.recordExportedBytes("17", report.content(), report.fileName());
        return InternalTransferController.reportResponse(report);
    }
}
