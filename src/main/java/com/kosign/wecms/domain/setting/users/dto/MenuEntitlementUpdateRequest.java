package com.kosign.wecms.domain.setting.users.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Body for {@code PUT /api/v1/settings/users/{user_id}/menu-entitlement}.
 *
 * <p>Carries the FULL set of granted {@code MENU_AUTH_CD} slugs the
 * panel currently shows. The service replaces every prior grant for
 * the (tenant, user) pair with this exact set via delete-then-insert
 * so a missing entry = revoked.
 *
 * <p>An empty list is valid — it means "revoke every grant". Null is
 * not (would be ambiguous with "untouched").
 */
public record MenuEntitlementUpdateRequest(
        @NotNull
        List<@Size(min = 1, max = 100) String> menuAuthCds
) { }
