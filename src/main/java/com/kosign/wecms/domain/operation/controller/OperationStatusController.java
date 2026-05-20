package com.kosign.wecms.domain.operation.controller;

import com.kosign.wecms.common.export.ExcelExportRequest;
import com.kosign.wecms.common.export.ExcelExportService;
import com.kosign.wecms.common.response.ApiResponse;
import com.kosign.wecms.domain.operation.dto.InquireOperationStatusRequest;
import com.kosign.wecms.domain.operation.dto.InquireOperationStatusResponse;
import com.kosign.wecms.domain.operation.dto.OperationStatusListResponse;
import com.kosign.wecms.domain.operation.dto.UpdateOperationStatusRequest;
import com.kosign.wecms.domain.operation.dto.UpdateOperationStatusResponse;
import com.kosign.wecms.domain.operation.service.OperationStatusFilter;
import com.kosign.wecms.domain.operation.service.OperationStatusService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/addon")
@RequiredArgsConstructor
public class OperationStatusController {

    private final OperationStatusService service;
    private final ExcelExportService excelExportService;

    /**
     * Operation Status Inquiry list. Mirrors legacy
     * {@code aosv_0001_001_r001} contract:
     *
     * <ul>
     *   <li>{@code q} — case-insensitive partial match on
     *       {@code COMPANYNAME} OR {@code TAXCODE} (legacy
     *       {@code SRCH_WD}, fixed 2-column whitelist).
     *   <li>{@code companyStatus} — partial match on the raw GDT
     *       Vietnamese {@code COMPANYSTATUS} string (legacy partial-match
     *       ILIKE; the legacy {@code wb_get_operat_stts} localisation
     *       helper is commented out and not ported).
     *   <li>{@code page} / {@code size} — zero-based page index +
     *       page size, clamped to
     *       {@link OperationStatusService#MAX_PAGE_SIZE}.
     *   <li>{@code sortField} / {@code sortDirection} — common-sort
     *       contract; {@code FIELD_ID} resolves through
     *       {@code OperationStatusSortField} whitelist; unknown / blank
     *       values fall back silently to default ORDER BY
     *       ({@code SEARCHDATETIME DESC, TAXCODE ASC}).
     * </ul>
     *
     * <p>Tenant scope is enforced by the JWT filter via
     * {@code TenantContext.currentBizKey()}; no {@code BIZ_KEY} query
     * param is accepted.
     */
    @GetMapping("/operation-status")
    public ApiResponse<OperationStatusListResponse> list(
            @RequestParam(value = "q",             required = false) String q,
            @RequestParam(value = "companyStatus", required = false) String companyStatus,
            @RequestParam(value = "sortField",     required = false) String sortField,
            @RequestParam(value = "sortDirection", required = false) String sortDirection,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "0") int size) {
        OperationStatusFilter filter = new OperationStatusFilter(q, companyStatus);
        return ApiResponse.ok(service.list(filter, sortField, sortDirection, page, size));
    }

    /**
     * Excel export — shared common pipeline (see
     * {@link com.kosign.wecms.common.export.ExcelExportService}). Body
     * is the standard {@link ExcelExportRequest} shape; the per-screen
     * provider {@code OperationStatusExportProvider} resolves at
     * runtime via {@code screenId = "addon-operation-status"} and
     * re-runs the same {@link OperationStatusService#list} the page
     * uses so export and list draw from one query path.
     */
    @PostMapping("/operation-status/export")
    public void exportOperationStatus(
            @Valid @RequestBody ExcelExportRequest request,
            HttpServletResponse response) {
        excelExportService.exportTo(response, request);
    }

    /**
     * "Get Business Info" — single Tax ID lookup via the OSMC → NXISAS →
     * GDT (Tổng cục Thuế) business-registration bridge. Mirrors legacy
     * {@code aosv_0001_002_r001_act.jsp:98}. Writes one
     * {@code EXT_TRAN_CALL_HIS} + one {@code NXSIAS_MODULE_TRAN_LOG_HIS}
     * audit row per call. Returns the GDT-matched registration so the
     * popup can render Customer Name + Address fields.
     */
    @PostMapping("/operation-status/inquire")
    public ApiResponse<InquireOperationStatusResponse> inquire(
            @Valid @RequestBody InquireOperationStatusRequest request) {
        return ApiResponse.ok(service.inquire(request.taxCode()));
    }

    /**
     * Toolbar "Update" — re-polls cached {@code OPRT_STTS_INFO} rows
     * from the GDT and upserts the refreshed snapshot. Mirrors legacy
     * {@code aosv_0001_001_u001_act.jsp}.
     *
     * <p>Body is optional: when {@code tax_codes} is omitted / empty,
     * every cached row in the tenant is re-polled. When it carries
     * 1..N tax codes, only those rows are updated — used by the
     * frontend when the user has selected specific rows via the
     * checkbox column. Empty cache is a no-op (returns zero counts).
     */
    @PostMapping("/operation-status/update")
    public ApiResponse<UpdateOperationStatusResponse> bulkUpdate(
            @RequestBody(required = false) UpdateOperationStatusRequest request) {
        List<String> taxCodes = request == null ? null : request.taxCodes();
        return ApiResponse.ok(service.bulkUpdate(taxCodes));
    }
}
