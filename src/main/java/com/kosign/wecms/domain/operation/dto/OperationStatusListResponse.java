package com.kosign.wecms.domain.operation.dto;

import java.util.List;

/**
 * Response envelope for {@code GET /api/v1/addon/operation-status}.
 * Carries the page slice plus pagination metadata so the frontend can
 * render the count text + arrow nav off one round-trip.
 *
 * Wire names auto-derived from camelCase Java fields via the global
 * SNAKE_CASE Jackson naming strategy.
 */
public record OperationStatusListResponse(
        List<OperationStatusRow> rows,
        long totalCount,
        int pageNumber,
        int pageSize,
        int totalPages
) { }
