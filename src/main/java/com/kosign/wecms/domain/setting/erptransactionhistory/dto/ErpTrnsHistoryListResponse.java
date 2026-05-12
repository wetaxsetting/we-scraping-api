package com.kosign.wecms.domain.setting.erptransactionhistory.dto;

import java.util.List;

/**
 * Settings &gt; ERP Transaction History list response. Mirrors the
 * legacy {@code WSVC.comm_0219_001_r001.xml} top-level shape — page
 * slice plus four tab counts — and renames {@code REC} → {@link #list}
 * per the CTO directive (2026-05-08).
 *
 * <p>The four count fields come from a single aggregate query that
 * intentionally drops the {@code ERP_TYPE} predicate from the filter
 * set (mirrors the JEX {@code getDynamicCNT} in
 * {@code comm_0219_001_r001_act.jsp:77-108}), so every tab chip updates
 * consistently for one filter combination — the user does not have to
 * switch tabs to see the count for a different direction.
 *
 * <p>Pagination metadata follows the
 * {@link com.kosign.wecms.domain.setting.firmbankinghistory.dto.FirmBankingHistoryListResponse}
 * shape — zero-based {@code pageNumber}, echo of the requested
 * {@code pageSize}, server-computed {@code totalPages}.
 *
 * <p>Wire names auto-derived from camelCase via the global SNAKE_CASE
 * Jackson naming strategy.
 */
public record ErpTrnsHistoryListResponse(

        /** Rows on the requested page. */
        List<ErpTrnsHistoryRow> list,

        /** Unpaged filtered total — equivalent to legacy {@code TOT_CNT}. */
        long totalCount,

        /** Direction-specific counts — equivalent to legacy
         *  {@code TOT_CNT_WECMS} ({@code W}). */
        long totalCountWecms,

        /** Direction-specific counts — equivalent to legacy
         *  {@code TOT_CNT_ERP} ({@code E}). */
        long totalCountErp,

        /** Direction-specific counts — equivalent to legacy
         *  {@code TOT_CNT_DELETED} ({@code D}). */
        long totalCountDeleted,

        /** Zero-based page index (Spring Data convention). */
        int pageNumber,

        /** Rows per page; echoed back from the request. */
        int pageSize,

        /** Total page count for the unpaged filtered set. */
        int totalPages

) { }
