package com.kosign.wecms.domain.setting.contacts.dto;

import java.util.List;

/**
 * RECENT quick-pick payload for the Contact selector — the current
 * user's most-recently-used contact keys, newest first (top-N).
 * Wire: {@code { "trco_keys": ["...", ...] }} via the global Jackson
 * SNAKE_CASE strategy. The frontend intersects these keys with the
 * already-loaded contact options, so only ids are shipped here.
 */
public record RecentContactsResponse(List<String> trcoKeys) {}
