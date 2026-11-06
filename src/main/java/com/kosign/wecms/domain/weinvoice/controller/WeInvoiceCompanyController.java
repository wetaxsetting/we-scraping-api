package com.kosign.wecms.domain.weinvoice.controller;

import com.kosign.wecms.common.response.ApiResponse;
import com.kosign.wecms.domain.weinvoice.dto.CompanyCreateRequest;
import com.kosign.wecms.domain.weinvoice.dto.CompanyItem;
import com.kosign.wecms.domain.weinvoice.dto.CompanyListResponse;
import com.kosign.wecms.domain.weinvoice.dto.CompanyPasswordRequest;
import com.kosign.wecms.domain.weinvoice.dto.CompanyPatchRequest;
import com.kosign.wecms.domain.weinvoice.service.CompanyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
 * <p>Every signed-in user manages their own tax codes (UI Spec 02: one account manages several
 * MSTs); there is no WeCMS admin gate. Sub-accounts and the 8-permission model (UI Spec 3.10) are
 * P2. Passwords are write-only.
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

    /** Logs in to the GDT portal through the engine first; saves the account only when accepted (WBE3027 / WBE3028 otherwise). */
    @PostMapping
    public ApiResponse<CompanyItem> create(@Valid @RequestBody CompanyCreateRequest request) {
        return ApiResponse.ok(companies.create(request));
    }

    @PutMapping("/{mst}/password")
    public ApiResponse<CompanyItem> password(@PathVariable("mst") String mst,
                                                 @Valid @RequestBody CompanyPasswordRequest request) {
        return ApiResponse.ok(companies.changePassword(mst, request));
    }

    @PatchMapping("/{mst}")
    public ApiResponse<CompanyItem> patch(@PathVariable("mst") String mst, @Valid @RequestBody CompanyPatchRequest request) {
        return ApiResponse.ok(companies.patch(mst, request));
    }

    /** Removes the MST from the user's list; invoices already collected are kept. */
    @DeleteMapping("/{mst}")
    public ApiResponse<Void> delete(@PathVariable("mst") String mst) {
        companies.delete(mst);
        return ApiResponse.ok(null);
    }
}
