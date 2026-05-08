package com.kosign.wecms.domain.setting.firmbankinghistory.controller;

import com.kosign.wecms.common.response.ApiResponse;
import com.kosign.wecms.domain.setting.firmbankinghistory.dto.FirmBankingHistoryListResponse;
import com.kosign.wecms.domain.setting.firmbankinghistory.service.FirmBankingHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
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
 */
@RestController
@RequestMapping("/api/v1/settings/firm-banking-history")
@RequiredArgsConstructor
public class FirmBankingHistoryController {

    private final FirmBankingHistoryService service;

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
}
