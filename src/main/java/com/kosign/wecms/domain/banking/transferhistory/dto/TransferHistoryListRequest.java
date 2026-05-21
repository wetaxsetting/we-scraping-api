package com.kosign.wecms.domain.banking.transferhistory.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Validated query-parameter bundle for
 * {@code GET /api/v1/banking/internal-transfer/transfer-history}.
 *
 * <p>The controller assembles this from individual {@code @RequestParam}s
 * — Spring binds it with the field names verbatim, so the wire names
 * (lower snake_case) are pinned by the controller's {@code @RequestParam}
 * annotations rather than by the request object.
 *
 * <p>Field rules mirror the legacy {@code ValidationHelper.validateRequired
 * / validateDate8 / validatePeriod / validatePageNo / validatePagePerCnt}
 * calls in {@code trns_0003_001_r001_act.jsp:32-50}. No period-length
 * cap (OQ-2 resolved 2026-05-20 — no documented numeric cap in legacy).
 *
 * <p>Note: {@link #pagePerCnt()} is constrained by {@link #PAGE_PER_CNT_PATTERN}
 * to the whitelist {@code {10,15,20,50,100}} per OQ-6 (resolved
 * 2026-05-20). Default is 15 — applied service-side when null.
 */
public record TransferHistoryListRequest(
        @NotBlank
        @Pattern(regexp = "\\d{8}", message = "from_dt must be YYYYMMDD")
        String fromDt,

        @NotBlank
        @Pattern(regexp = "\\d{8}", message = "to_dt must be YYYYMMDD")
        String toDt,

        @Pattern(regexp = "|00000000|99999999",
                 message = "trns_stts must be empty, '00000000' (success), or '99999999' (failed)")
        String trnsStts,

        @Min(1) Integer pageNo,

        @Pattern(regexp = "10|15|20|50|100",
                 message = "page_per_cnt must be one of 10, 15, 20, 50, 100")
        String pagePerCnt,

        String sortField,
        String sortDirection
) {
    public static final String PAGE_PER_CNT_PATTERN = "10|15|20|50|100";
    public static final int DEFAULT_PAGE_PER_CNT = 15;
}
