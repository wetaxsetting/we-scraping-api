package com.kosign.wecms.domain.weinvoice.controller;

import com.kosign.wecms.common.response.ApiResponse;
import com.kosign.wecms.domain.weinvoice.dto.CompanyCreateRequest;
import com.kosign.wecms.domain.weinvoice.dto.CompanyItem;
import com.kosign.wecms.domain.weinvoice.dto.CompanyListResponse;
import com.kosign.wecms.domain.weinvoice.dto.CompanyPasswordRequest;
import com.kosign.wecms.domain.weinvoice.dto.CompanyPatchRequest;
import com.kosign.wecms.domain.weinvoice.dto.RequestAccepted;
import com.kosign.wecms.domain.weinvoice.service.CompanyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * WeInvoice screen 06 — GDT connections (managed tax codes).
 *
 * <p>Changing connections is admin-only until the P2 permission model (8 permissions per
 * sub-account, UI Spec 3.10) exists; reading them is open to every user of the tenant.
 * Passwords are write-only.
 */
@RestController
@RequestMapping("/api/v1/weinvoice/companies")
@RequiredArgsConstructor
@Validated
public class WeInvoiceCompanyController {

    private final CompanyService companies;

    @GetMapping
    public ApiResponse<CompanyListResponse> list() {
        return ApiResponse.ok(companies.list());
    }

    /** Stores the account as {@code pending} and queues the engine login test. */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<RequestAccepted> create(@Valid @RequestBody CompanyCreateRequest request) {
        return ApiResponse.ok(companies.create(request));
    }

    @PutMapping("/{mst}/password")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<RequestAccepted> password(@PathVariable("mst") String mst,
                                                 @Valid @RequestBody CompanyPasswordRequest request) {
        return ApiResponse.ok(companies.changePassword(mst, request));
    }

    @PatchMapping("/{mst}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<CompanyItem> patch(@PathVariable("mst") String mst, @Valid @RequestBody CompanyPatchRequest request) {
        return ApiResponse.ok(companies.patch(mst, request));
    }

    /** Removes the MST from the tenant; invoices already collected are kept. */
    @DeleteMapping("/{mst}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> delete(@PathVariable("mst") String mst) {
        companies.delete(mst);
        return ApiResponse.ok(null);
    }
}
