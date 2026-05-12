package com.kosign.wecms.domain.setting.vasaccount.dto;

import java.util.List;

/**
 * Paginated list response for VAS Account. Pagination shape mirrors
 * {@code ErpTrnsHistoryListResponse} — zero-based {@code pageNumber}, echoed
 * {@code pageSize} (after the service clamp), server-computed
 * {@code totalPages}.
 *
 * <p>Wire name {@code list} matches the ERP Transaction History precedent;
 * legacy {@code REC} is retired per the CTO directive (2026-05-08).
 */
public record VasAccountListResponse(

        /** Rows on the requested page. */
        List<VasAccountRow> list,

        /** Unpaged filtered total (post {@code search_wd} + {@code stts}). */
        long totalCount,

        /** Zero-based page index. */
        int pageNumber,

        /** Rows per page actually used by the server (after clamp). */
        int pageSize,

        /** Total page count for the unpaged filtered set. */
        int totalPages

) { }
