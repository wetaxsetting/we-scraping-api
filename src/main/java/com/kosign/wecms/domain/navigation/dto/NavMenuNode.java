package com.kosign.wecms.domain.navigation.dto;

import java.util.List;

/**
 * One node in the resolved navigation tree returned by
 * {@code GET /api/v1/navigation}. Nodes are already filtered to what the
 * caller may see ({@code Product Tree ∩ grants}, or the full product tree
 * for admins) and ordered by {@code seq} within each parent.
 *
 * <p>Wire names auto-derive from these camelCase fields via the global
 * SNAKE_CASE Jackson strategy ({@code id}, {@code permission_cd},
 * {@code parent_id}, {@code legacy_mnu_id}, …).
 *
 * @param id           refactor slug — the stable node id
 * @param permissionCd kebab menuAuth grant key; {@code null} for pure parents
 * @param parentId     parent slug; {@code null} for top menus
 * @param depth        0 top · 1 group · 2 leaf (legacy {@code MNU_DEP})
 * @param seq          sibling order within the parent (legacy {@code MNU_SEQ})
 * @param label        label resolved to the caller's language (EN fallback)
 * @param route        target Next.js route; {@code null} for pure parents
 * @param icon         CSS / asset icon hint; may be {@code null}
 * @param legacyMnuId  traceability — JexAdmin {@code MNU_ID}
 * @param legacyWsvcId traceability — legacy WSVC id (URL − {@code .act})
 * @param children     nested child nodes (already filtered + ordered)
 */
public record NavMenuNode(
        String id,
        String permissionCd,
        String parentId,
        int depth,
        int seq,
        String label,
        String route,
        String icon,
        String legacyMnuId,
        String legacyWsvcId,
        List<NavMenuNode> children
) { }
