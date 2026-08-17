package com.kosign.wecms.domain.setting.zalooa.dto;

import java.util.List;

/**
 * Paged list payload for {@code GET /api/v1/settings/zalo-oa}, following the
 * {@code …ListResponse} suffix convention: {@code rows} plus the unpaged
 * {@code total_count}.
 *
 * @param rows       one entry per connection, newest first
 * @param totalCount total connections for the tenant, ignoring pagination
 */
public record ZaloOaListResponse(
        List<ZaloOaRow> rows,
        long totalCount
) {
}
