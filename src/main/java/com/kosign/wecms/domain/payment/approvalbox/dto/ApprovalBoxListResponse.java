package com.kosign.wecms.domain.payment.approvalbox.dto;

import java.util.List;

/**
 * Payment &gt; Approval Box list response — page slice + four tab counts.
 * Mirrors the legacy top-level shape from {@code WSVC.resl_0001_001_r001.xml}
 * with the wire renames documented in
 * {@code design-backend.md} §2.1 (no legacy {@code REC}/{@code REC3}
 * envelope; counts under {@link #tabCounts}; pagination metadata
 * Spring-Data-style).
 *
 * <p>Wire names auto-derived from camelCase via the global SNAKE_CASE
 * Jackson naming strategy.
 */
public record ApprovalBoxListResponse(
        List<ApprovalBoxRow>  list,
        long                  totalCount,
        ApprovalBoxTabCounts  tabCounts,
        int                   pageNumber,
        int                   pageSize,
        int                   totalPages
) { }
