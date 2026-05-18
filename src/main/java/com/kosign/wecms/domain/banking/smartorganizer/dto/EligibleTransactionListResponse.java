package com.kosign.wecms.domain.banking.smartorganizer.dto;

import java.util.List;

/**
 * Paginated eligibility list response. {@code totalCount} is the
 * unbounded match count (server-side, not the page size); the
 * frontend uses it to render the "x of y" pagination label and to
 * gate the "all rows selected" checkbox heuristic.
 */
public record EligibleTransactionListResponse(
        long totalCount,
        List<EligibleTransactionRow> rows
) {
}
