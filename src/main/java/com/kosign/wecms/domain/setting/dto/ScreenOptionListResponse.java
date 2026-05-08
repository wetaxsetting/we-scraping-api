package com.kosign.wecms.domain.setting.dto;

import java.util.List;

/**
 * {@code data} payload for {@code GET /api/v1/screen-options/{webSvcId}/{dymAreaId}}.
 *
 * <p>{@code items} is the merged list — system defaults overlaid with
 * the caller's user overrides for the (webSvcId, dymAreaId) pair,
 * ordered by {@code disp_ord ASC}. The frontend renders the popup
 * directly off this list and uses the same shape on PUT to save user
 * overrides.
 *
 * Wire names auto-derived from camelCase Java fields via the global
 * SNAKE_CASE Jackson naming strategy.
 */
public record ScreenOptionListResponse(
        String webSvcId,
        String dymAreaId,
        List<ScreenOptionItem> items
) { }
