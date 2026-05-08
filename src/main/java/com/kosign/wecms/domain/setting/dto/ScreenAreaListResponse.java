package com.kosign.wecms.domain.setting.dto;

import java.util.List;

/**
 * {@code data} payload for {@code GET /api/v1/screen-options/{webSvcId}/areas}.
 *
 * <p>Returns the distinct {@code dym_area_id}s configured for a screen,
 * with the column count for each. Used by multi-area pages to drive a
 * tabbed View Setting popover; single-area pages don't need this call.
 *
 * Wire names auto-derived from camelCase Java fields via the global
 * SNAKE_CASE Jackson naming strategy.
 */
public record ScreenAreaListResponse(
        String webSvcId,
        List<Area> areas
) {
    public record Area(
            String  dymAreaId,
            Integer columnCount
    ) { }
}
