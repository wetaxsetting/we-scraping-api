package com.kosign.wecms.domain.setting.users.dto;

import java.util.List;

/**
 * Response for {@code GET /api/v1/settings/users/{user_id}/menu-entitlement}
 * and {@code PUT} on the same path.
 *
 * <p>Carries the user id (echoed for round-trip safety) and the full
 * list of granted {@code MENU_AUTH_CD} slugs. The Y-only storage
 * convention means an absent slug = denied; the frontend renders the
 * panel by intersecting this list against its static
 * {@code ENTITLEMENT_SECTIONS} tree.
 */
public record MenuEntitlementResponse(
        String       userId,
        List<String> menuAuthCds
) { }
