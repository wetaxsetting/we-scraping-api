package com.kosign.wecms.domain.weinvoice.controller;

import com.kosign.wecms.common.response.ApiResponse;
import com.kosign.wecms.domain.vatdeclaration.dto.AnnexInvoiceListResponse;
import com.kosign.wecms.domain.vatdeclaration.dto.VatDeclarationCreateRequest;
import com.kosign.wecms.domain.vatdeclaration.dto.VatDeclarationDetail;
import com.kosign.wecms.domain.vatdeclaration.dto.VatDeclarationListResponse;
import com.kosign.wecms.domain.vatdeclaration.dto.VatDeclarationUpdateRequest;
import com.kosign.wecms.domain.vatdeclaration.export.VatExportFile;
import com.kosign.wecms.domain.vatdeclaration.service.VatDeclarationService;
import com.kosign.wecms.domain.vatdeclaration.service.VatScope;
import com.kosign.wecms.domain.weinvoice.common.CompanyAccessGuard;
import com.kosign.wecms.domain.weinvoice.repository.CompanyConnectionRepository.CompanyRow;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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

/**
 * WeInvoice Thuế → Tờ khai GTGT (Mẫu 01/GTGT TT80) of one managed MST — the WeCMS VAT declaration module
 * ({@link VatDeclarationService}, same rules and DTOs as {@code /api/v1/addon/vat-declarations}) fed by the
 * engine invoices of that MST. The book is per (signed-in user, MST) ({@link VatScope#weinvoice}); the MST must be
 * one of the user's active tax codes ({@code WBE3002} otherwise). Binary exports are the documented exception to
 * the {@code ApiResponse} envelope.
 *
 * <p>Record: docs/features/addon-service/weinvoice/weinvoice-vat-declaration-engine-invoices-2026-11-25.md.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/weinvoice/companies/{mst}/vat-declarations")
public class WeInvoiceVatDeclarationController {

    private final VatDeclarationService service;
    private final CompanyAccessGuard guard;

    @GetMapping
    public ApiResponse<VatDeclarationListResponse> list(
            @PathVariable("mst") String mst,
            @RequestParam(value = "year", required = false) Integer year,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return ApiResponse.ok(service.list(scope(mst), year, status, page, size));
    }

    @PostMapping
    public ApiResponse<VatDeclarationDetail> create(@PathVariable("mst") String mst,
                                                    @Valid @RequestBody VatDeclarationCreateRequest request) {
        return ApiResponse.ok(service.create(scope(mst), request));
    }

    @GetMapping("/{sid}")
    public ApiResponse<VatDeclarationDetail> detail(@PathVariable("mst") String mst, @PathVariable("sid") Long sid) {
        return ApiResponse.ok(service.detail(scope(mst), sid));
    }

    @PutMapping("/{sid}")
    public ApiResponse<VatDeclarationDetail> update(@PathVariable("mst") String mst, @PathVariable("sid") Long sid,
                                                    @RequestBody VatDeclarationUpdateRequest request) {
        return ApiResponse.ok(service.update(scope(mst), sid, request));
    }

    @DeleteMapping("/{sid}")
    public ApiResponse<Void> delete(@PathVariable("mst") String mst, @PathVariable("sid") Long sid) {
        service.delete(scope(mst), sid);
        return ApiResponse.ok(null);
    }

    /** Re-aggregate the period's engine invoices into the indicators (draft only). */
    @PostMapping("/{sid}/sync")
    public ApiResponse<VatDeclarationDetail> sync(@PathVariable("mst") String mst, @PathVariable("sid") Long sid) {
        return ApiResponse.ok(service.sync(scope(mst), sid));
    }

    @PutMapping("/{sid}/lock")
    public ApiResponse<VatDeclarationDetail> lock(@PathVariable("mst") String mst, @PathVariable("sid") Long sid) {
        return ApiResponse.ok(service.lock(scope(mst), sid));
    }

    @DeleteMapping("/{sid}/lock")
    public ApiResponse<VatDeclarationDetail> unlock(@PathVariable("mst") String mst, @PathVariable("sid") Long sid) {
        return ApiResponse.ok(service.unlock(scope(mst), sid));
    }

    @GetMapping("/{sid}/annex/invoices")
    public ApiResponse<AnnexInvoiceListResponse> annexInvoices(
            @PathVariable("mst") String mst,
            @PathVariable("sid") Long sid,
            @RequestParam(value = "annex_type", required = false) String annexType,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "io_dv", required = false) String ioDv,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return ApiResponse.ok(service.listAnnexInvoices(scope(mst), sid, annexType, search, ioDv, page, size));
    }

    /** HTKK XML — locked declarations only. */
    @GetMapping("/{sid}/export/xml")
    public ResponseEntity<byte[]> exportXml(@PathVariable("mst") String mst, @PathVariable("sid") Long sid) {
        return download(service.exportXml(scope(mst), sid));
    }

    /** A4 PDF of the main form — locked declarations only. */
    @GetMapping("/{sid}/export/pdf")
    public ResponseEntity<byte[]> exportPdf(@PathVariable("mst") String mst, @PathVariable("sid") Long sid) {
        return download(service.exportPdf(scope(mst), sid));
    }

    /** 01/GTGT workbook (EN/VN by session language) — locked declarations only. */
    @GetMapping("/{sid}/export/report-excel")
    public ResponseEntity<byte[]> exportReportExcel(@PathVariable("mst") String mst, @PathVariable("sid") Long sid) {
        return download(service.exportReportExcel(scope(mst), sid));
    }

    private VatScope scope(String mst) {
        CompanyRow company = guard.requireCompany(mst);
        return VatScope.weinvoice(guard.ownerId(), company.mst(), company.companyName());
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
