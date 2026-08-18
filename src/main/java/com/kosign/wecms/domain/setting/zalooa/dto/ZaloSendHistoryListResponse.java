package com.kosign.wecms.domain.setting.zalooa.dto;

import java.util.List;

/**
 * Paged list payload for {@code GET /api/v1/settings/zalo-oa/send-history}.
 *
 * @param rows       attempts for the tenant, newest first
 * @param totalCount total attempts for the tenant, ignoring pagination
 */
public record ZaloSendHistoryListResponse(
        List<ZaloSendHistoryRow> rows,
        long totalCount
) {
}
