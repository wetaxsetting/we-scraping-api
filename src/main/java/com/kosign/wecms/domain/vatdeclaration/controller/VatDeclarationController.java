package com.kosign.wecms.domain.vatdeclaration.controller;

import com.kosign.wecms.common.response.ApiResponse;
import com.kosign.wecms.domain.vatdeclaration.dto.AnnexInvoiceListResponse;
import com.kosign.wecms.domain.vatdeclaration.dto.VatDeclarationCreateRequest;
import com.kosign.wecms.domain.vatdeclaration.dto.VatDeclarationDetail;
import com.kosign.wecms.domain.vatdeclaration.dto.VatDeclarationListResponse;
import com.kosign.wecms.domain.vatdeclaration.dto.VatDeclarationUpdateRequest;
import com.kosign.wecms.domain.vatdeclaration.service.VatDeclarationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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

/**
 * Add-on > Tax Report > VAT Declaration (Mẫu 01/GTGT TT80).
 * Contract: docs/features/addon-service/vat-declaration/interface-specification.md
 * (endpoints 1–8; sync/annex/export legs land in a follow-up MR).
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/addon/vat-declarations")
@PreAuthorize("@menuAuthz.canAccess('addon-vat-declaration')")
public class VatDeclarationController {

    private final VatDeclarationService service;

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
}
