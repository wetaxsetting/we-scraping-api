package com.kosign.wecms.domain.navigation.dto;

import java.util.List;

/**
 * Resolved navigation tree for the authenticated session, returned by
 * {@code GET /api/v1/navigation}.
 *
 * <p>The server selects the product tree from the session's
 * {@code prdt_type} (never a client parameter) and returns the
 * already-resolved visible tree:
 * <ul>
 *   <li><b>normal user</b> → {@code Product Tree ∩ USER_MENU_AUTH_INFM grants}</li>
 *   <li><b>admin</b> → the full {@code Product Tree}</li>
 * </ul>
 * Parents collapse when all their children are filtered out, mirroring the
 * legacy {@code OSMCMenuHelper.isPermittedMenu} parent rule.
 *
 * <p>{@code topMenus} are the depth-0 roots, each carrying its nested
 * {@code children} — one resolved hierarchy, not four per-surface lists.
 * Wire names derive via the global SNAKE_CASE strategy
 * ({@code product_type}, {@code menu_group_id}, {@code top_menus}).
 *
 * @param productType verbatim {@code BUSI_INFM.PRDT_TYPE} letter for the session
 * @param menuGroupId traceability to the source tree
 *                    ({@code F→WE_CMSVN}, {@code M→WE_CMSVN_LUMENS},
 *                    {@code H→WE_CMSVN_HAENGSUNG}); {@code null} when the
 *                    product type is not a recognised navigation product
 * @param topMenus    resolved depth-0 nodes with nested children
 */
public record NavigationResponse(
        String productType,
        String menuGroupId,
        List<NavMenuNode> topMenus
) { }
