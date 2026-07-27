package com.kosign.wecms.domain.setting.purposesettings.dto;

import java.util.List;

/**
 * RECENT quick-pick payload for the Purpose selector — the current
 * user's most-recently-used purpose codes, newest first (top-N).
 * Wire: {@code { "use_usag_cds": ["...", ...] }} via the global Jackson
 * SNAKE_CASE strategy. The frontend intersects these codes with the
 * already-loaded purpose options, so only ids are shipped here.
 */
public record RecentPurposesResponse(List<String> useUsagCds) {}
