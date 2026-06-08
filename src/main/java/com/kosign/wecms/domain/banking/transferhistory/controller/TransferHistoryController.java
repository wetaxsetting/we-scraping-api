package com.kosign.wecms.domain.banking.transferhistory.controller;

import com.kosign.wecms.common.response.ApiResponse;
import com.kosign.wecms.domain.banking.transferhistory.dto.TransferHistoryListRequest;
import com.kosign.wecms.domain.banking.transferhistory.dto.TransferHistoryListResponse;
import com.kosign.wecms.domain.banking.transferhistory.service.TransferHistoryQueryService;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * Banking &gt; Internal Transfer &gt; Transfer History — read endpoint.
 *
 * <p>Mirrors legacy {@code trns_0003_001_r001}
 * ({@code WECMS DEV/WECMS/web/WEB-INF/action/jex/wabooks/trns/
 * trns_0003_001_r001_act.jsp}) under the spec URI
 * {@code /api/v1/banking/internal-transfer/transfer-history}. Returns a
 * lowercase snake_case envelope per the global Jackson naming strategy
 * — no per-field {@code @JsonProperty}. This is now the sole Transfer
 * History endpoint: the Sprint-1 interim UPPER_SNAKE endpoint at
 * {@code /api/v1/banking/transfers/history} (and its DTOs / service)
 * was deleted 2026-06-07 once the frontend cutover was complete.
 *
 * <p>Per OQ-5 (resolved 2026-05-20) the controller carries no
 * {@code @PreAuthorize} — JWT authentication is the only gate,
 * matching live JEX behaviour ({@code NORM_USER_ONLY_YN=Y} excludes WTA
 * users only, not normal users).
 *
 * <p>Audit-log: NO writes for this read endpoint (OQ-4 resolved
 * 2026-05-20). The slug {@code transfer-history} is reserved as the
 * audit {@code WSVC_ID} for log/audit consistency if/when a future
 * mutation endpoint is added.
 *
 * <p>Bean Validation on query parameters: the controller annotates
 * each {@code @RequestParam} individually so Spring's
 * {@code MethodValidationPostProcessor} (active via the class-level
 * {@code @Validated}) raises {@code ConstraintViolationException} on
 * missing / malformed params — the {@code GlobalExceptionHandler}
 * maps that to the {@code WBE_VALIDATION} envelope. The
 * {@link TransferHistoryListRequest} record is assembled inside the
 * method and forwarded to the service.
 */
@RestController
@RequestMapping("/api/v1/banking/internal-transfer/transfer-history")
@RequiredArgsConstructor
@Validated
@PreAuthorize("@menuAuthz.canAccess('banking-internal-transfer-history')")
public class TransferHistoryController {

    private final TransferHistoryQueryService service;

    /**
     * List transfer history rows for the current tenant within the
     * inclusive {@code from_dt} / {@code to_dt} range, optionally
     * narrowed by status. Sort + paging follow the project-wide
     * common-sort + page-size conventions.
     */
    @GetMapping
    public ApiResponse<TransferHistoryListResponse> list(
            @RequestParam(name = "from_dt")
            @NotBlank
            @Pattern(regexp = "\\d{8}", message = "from_dt must be YYYYMMDD")
            String fromDt,

            @RequestParam(name = "to_dt")
            @NotBlank
            @Pattern(regexp = "\\d{8}", message = "to_dt must be YYYYMMDD")
            String toDt,

            @RequestParam(name = "trns_stts", required = false)
            @Pattern(regexp = "|00000000|99999999",
                     message = "trns_stts must be empty, '00000000' (success), or '99999999' (failed)")
            String trnsStts,

            @RequestParam(name = "page_no", required = false)
            @Min(1) Integer pageNo,

            @RequestParam(name = "page_per_cnt", required = false)
            @Pattern(regexp = TransferHistoryListRequest.PAGE_PER_CNT_PATTERN,
                     message = "page_per_cnt must be one of 10, 15, 20, 50, 100")
            String pagePerCnt,

            @RequestParam(name = "sort_field", required = false) String sortField,

            @RequestParam(name = "sort_direction", required = false) String sortDirection) {

        TransferHistoryListRequest req = new TransferHistoryListRequest(
                fromDt, toDt, trnsStts, pageNo, pagePerCnt, sortField, sortDirection);
        return ApiResponse.ok(service.list(req));
    }
}
