package com.kosign.wecms.domain.setting.users.dto;

import java.util.List;

/**
 * Response envelope for {@code GET /api/v1/settings/users}.
 *
 * <p>Carries the user rows plus the active-user count for the current
 * tenant. The Settings &gt; Users panel renders the count in its
 * subtitle ("{n} users"). There is intentionally no plan cap — legacy
 * JEX defines {@code WBW1151} ("up to 5 users") but never throws it,
 * and real tenants exceed 5 today. A future tier-pricing MR would
 * surface a per-tenant limit via a dedicated table.
 */
public record UserListResponse(
        List<UserRowResponse> users,
        int                   totalCount
) { }
