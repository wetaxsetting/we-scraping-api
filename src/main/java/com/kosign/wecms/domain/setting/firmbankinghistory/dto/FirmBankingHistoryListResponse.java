package com.kosign.wecms.domain.setting.firmbankinghistory.dto;

import java.util.List;

/**
 * Settings &gt; Firm Banking History list response. Mirrors the legacy
 * {@code TOT_CNT} + {@code REC} top-level WSVC shape but renames
 * {@code REC} → {@link #list} per the CTO directive (2026-05-08).
 *
 * <p>Pagination metadata is added on top of the legacy shape so the
 * frontend's {@code <Pagination>} component drives directly off the
 * response — no client-side {@code totalPages} math, no slice-vs-total
 * confusion.
 *
 * <p>Wire names auto-derived from camelCase via the global SNAKE_CASE
 * Jackson naming strategy.
 */
public record FirmBankingHistoryListResponse(
        /** Rows on the requested page. */
        List<FirmBankingHistoryRow> list,
        /** Unpaged filtered total — does not collapse to {@link #pageSize}. */
        long totalCount,
        /** Zero-based page index (Spring Data convention). */
        int pageNumber,
        /** Rows per page; echoed back from the request. */
        int pageSize,
        /** Total page count for the unpaged filtered set. */
        int totalPages
) { }
