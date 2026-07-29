package com.kosign.wecms.domain.vatdeclaration.controller;

import com.kosign.wecms.common.export.ExcelExportRequest;
import com.kosign.wecms.common.export.ExcelExportService;
import com.kosign.wecms.common.response.ApiResponse;
import com.kosign.wecms.domain.vatdeclaration.dto.AnnexInvoiceListResponse;
import com.kosign.wecms.domain.vatdeclaration.dto.VatDeclarationCreateRequest;
import com.kosign.wecms.domain.vatdeclaration.dto.VatDeclarationDetail;
import com.kosign.wecms.domain.vatdeclaration.dto.VatDeclarationListResponse;
import com.kosign.wecms.domain.vatdeclaration.dto.VatDeclarationUpdateRequest;
import com.kosign.wecms.domain.vatdeclaration.export.VatAnnexExportProvider;
import com.kosign.wecms.domain.vatdeclaration.export.VatExportFile;
import com.kosign.wecms.domain.vatdeclaration.service.VatDeclarationService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Add-on > Tax Report > VAT Declaration (Mẫu 01/GTGT TT80).
 * Contract: docs/features/addon-service/vat-declaration/interface-specification.md.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/addon/vat-declarations")
@PreAuthorize("@menuAuthz.canAccess('addon-vat-declaration')")
public class VatDeclarationController {

    private final VatDeclarationService service;
    private final ExcelExportService excelExportService;

    @GetMapping
    public ApiResponse<VatDeclarationListResponse> list(
            @RequestParam(value = "year", required = false) Integer year,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return ApiResponse.ok(service.list(year, status, page, size));
    }

    @PostMapping
    public ApiResponse<VatDeclarationDetail> create(@Valid @RequestBody VatDeclarationCreateRequest request) {
        return ApiResponse.ok(service.create(request));
    }

    @GetMapping("/{sid}")
    public ApiResponse<VatDeclarationDetail> detail(@PathVariable("sid") Long sid) {
        return ApiResponse.ok(service.detail(sid));
    }

    @PutMapping("/{sid}")
    public ApiResponse<VatDeclarationDetail> update(@PathVariable("sid") Long sid,
                                                    @RequestBody VatDeclarationUpdateRequest request) {
        return ApiResponse.ok(service.update(sid, request));
    }

    @DeleteMapping("/{sid}")
    public ApiResponse<Void> delete(@PathVariable("sid") Long sid) {
        service.delete(sid);
        return ApiResponse.ok(null);
    }

    @PutMapping("/{sid}/lock")
    public ApiResponse<VatDeclarationDetail> lock(@PathVariable("sid") Long sid) {
        return ApiResponse.ok(service.lock(sid));
    }

    @DeleteMapping("/{sid}/lock")
    public ApiResponse<VatDeclarationDetail> unlock(@PathVariable("sid") Long sid) {
        return ApiResponse.ok(service.unlock(sid));
    }

    // --- Annex (8% reduction / BKBR / BKMV / ALLOC) --------------------------

    @GetMapping("/{sid}/annex/invoices")
    public ApiResponse<AnnexInvoiceListResponse> annexInvoices(
            @PathVariable("sid") Long sid,
            @RequestParam(value = "annex_type", required = false) String annexType,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "io_dv", required = false) String ioDv,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return ApiResponse.ok(service.listAnnexInvoices(sid, annexType, search, ioDv, page, size));
    }

    // Add / delete annex row are gone: annex lines are derived from the period's
    // invoices (BUSA_DOC_MSTR + BUSA_DOC_DETL), so there is nothing to persist.

    // --- Export (XML / annex Excel / PDF) ------------------------------------

    /**
     * HTKK XML export (API #12) — locked declarations only. Binary download
     * (§0 exception to the {@code ApiResponse<T>} envelope): the frontend
     * consumes it via the blob handler, never {@code unwrap()}.
     */
    @GetMapping("/{sid}/export/xml")
    public ResponseEntity<byte[]> exportXml(@PathVariable("sid") Long sid) {
        return download(service.exportXml(sid));
    }

    /**
     * Annex invoice Excel export (API #13) — routed through the shared
     * {@code common/export} pipeline. {@code sid} is injected into the filters
     * from the path so the resource id is the trusted path value, not client body.
     */
    @PostMapping("/{sid}/annex/export")
    public void exportAnnex(@PathVariable("sid") Long sid,
                            @Valid @RequestBody ExcelExportRequest request,
                            HttpServletResponse response) {
        Map<String, Object> filters = new HashMap<>(
                request.filters() != null ? request.filters() : Map.of());
        filters.put(VatAnnexExportProvider.FILTER_DECL_ID, sid);
        ExcelExportRequest scoped = new ExcelExportRequest(
                request.screenId(), request.dymAreaId(), request.fileName(), request.title(),
                filters, request.sort(), request.columns(), request.subHeader());
        excelExportService.exportTo(response, scoped);
    }

    /**
     * A4 PDF export (API #14) — locked declarations only. Binary download.
     */
    @GetMapping("/{sid}/export/pdf")
    public ResponseEntity<byte[]> exportPdf(@PathVariable("sid") Long sid) {
        return download(service.exportPdf(sid));
    }

    private static ResponseEntity<byte[]> download(VatExportFile file) {
        ContentDisposition cd = ContentDisposition.builder("attachment")
                .filename(file.fileName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, cd.toString())
                .header("Access-Control-Expose-Headers", HttpHeaders.CONTENT_DISPOSITION)
                .contentType(MediaType.parseMediaType(file.mediaType()))
                .body(file.content());
    }
}
