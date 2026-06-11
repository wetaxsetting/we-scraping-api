package com.kosign.wecms.domain.payment.internaltransfer.controller;

import com.kosign.wecms.common.export.ExcelExportRequest;
import com.kosign.wecms.common.export.ExcelExportService;
import com.kosign.wecms.common.response.ApiResponse;
import com.kosign.wecms.domain.payment.internaltransfer.dto.TransferResultDetailResponse;
import com.kosign.wecms.domain.payment.internaltransfer.dto.TransferResultListResponse;
import com.kosign.wecms.domain.payment.internaltransfer.service.InternalTransferResultService;
import jakarta.servlet.http.HttpServletResponse;
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

    private static final String SCDUL_PYMN_NO_PATTERN = "^([0-9]{5,30}|VO_[0-9]{6}_[0-9]{1,9})$";

    private final InternalTransferResultService service;
    private final ExcelExportService            excelExportService;

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
     */
    @PostMapping("/export")
    public void exportResults(
            @Valid @RequestBody ExcelExportRequest request,
            HttpServletResponse response) {
        excelExportService.exportTo(response, request);
    }
}
