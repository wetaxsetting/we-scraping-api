package com.kosign.wecms.domain.setting.firmbankinghistory.controller;

import com.kosign.wecms.common.export.ExcelExportRequest;
import com.kosign.wecms.common.export.ExcelExportService;
import com.kosign.wecms.common.response.ApiResponse;
import com.kosign.wecms.domain.setting.firmbankinghistory.dto.FirmBankingHistoryListResponse;
import com.kosign.wecms.domain.setting.firmbankinghistory.service.FirmBankingHistoryService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Settings &gt; Firm Banking History — read-only audit history of every
 * outbound integration call WeCMS has made for the current tenant.
 * Mirrors legacy {@code comm_0221_001_r001_act.jsp}.
 *
 * <p>Wire format is lowercase snake_case; Java parameter names stay
 * camelCase and Spring's {@code @RequestParam(value=…)} pins each one
 * to its snake_case wire counterpart. Validation lives in the service —
 * the controller is a thin pass-through that wraps the result in
 * {@link ApiResponse}.
 *
 * <p><b>Authority (GAP-4 parity, 2026-06-05).</b> Legacy
 * {@code WSVC.comm_0221_001_r001.xml:25-26} restricts the screen to
 * normal company users ({@code NORM_USER_ONLY_YN=Y},
 * {@code WTA_USER_ALLOW_YN=N}). The refactor has no WTA/agent users
 * (the admin portal is a separate system), so the faithful mapping is
 * "must be a provisioned normal company user" — i.e. an authenticated
 * principal carrying {@code ROLE_ADMIN} or {@code ROLE_USER} (both are
 * normal company roles, stamped from {@code authGrCd} by
 * {@code JwtAuthenticationFilter}). {@code @PreAuthorize} is honoured
 * because {@code SecurityConfig} enables {@code @EnableMethodSecurity}.
 * Both list and export endpoints inherit the class-level gate. Admin
 * access remains allowed by design.
 */
@RestController
@RequestMapping("/api/v1/settings/firm-banking-history")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','USER')")
public class FirmBankingHistoryController {

    private final FirmBankingHistoryService service;
    private final ExcelExportService excelExportService;

    /**
     * List firm-banking history rows for the current tenant.
     *
     * @param fromDate       required {@code YYYYMMDD}
     * @param toDate         required {@code YYYYMMDD}, ≥ {@code fromDate}
     * @param svcType        optional one of {@code F}/{@code S}/{@code G}/{@code B}
     * @param keyword        optional partial match on {@code seq_no}
     * @param sortField      optional whitelisted FIELD_ID
     *                       (see {@link com.kosign.wecms.domain.setting.firmbankinghistory.service.FirmBankingHistorySortField})
     * @param sortDirection  optional {@code ASC}/{@code DESC}
     * @param page           zero-based page index (default {@code 0})
     * @param size           rows per page (default
     *                       {@link FirmBankingHistoryService#DEFAULT_PAGE_SIZE},
     *                       clamped to
     *                       {@link FirmBankingHistoryService#MAX_PAGE_SIZE})
     */
    @GetMapping
    public ApiResponse<FirmBankingHistoryListResponse> list(
            @RequestParam("from_date")                              String fromDate,
            @RequestParam("to_date")                                String toDate,
            @RequestParam(value = "svc_type",       required = false) String svcType,
            @RequestParam(value = "keyword",        required = false) String keyword,
            @RequestParam(value = "sort_field",     required = false) String sortField,
            @RequestParam(value = "sort_direction", required = false) String sortDirection,
            @RequestParam(value = "page",           defaultValue = "0") int page,
            @RequestParam(value = "size",           defaultValue = "0") int size) {
        return ApiResponse.ok(service.list(
                fromDate, toDate, svcType, keyword,
                sortField, sortDirection, page, size));
    }

    /**
     * Stream the filtered + sorted firm-banking history rows as an XLSX.
     * Shared pipeline — see {@link ExcelExportService} for the full
     * contract (column intersection with View Setting, 50k-row cap,
     * RFC 5987 filename encoding). The provider
     * ({@code FirmBankingHistoryExportProvider}) re-runs the same
     * filter path the list endpoint uses via {@code service.listAll}.
     */
    @PostMapping("/export")
    public void export(
            @Valid @RequestBody ExcelExportRequest request,
            HttpServletResponse response) {
        excelExportService.exportTo(response, request);
    }
}
