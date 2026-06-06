package com.kosign.wecms.domain.navigation.service;

import com.kosign.wecms.common.enums.PrdtType;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.auth.service.MenuAuthorizationService;
import com.kosign.wecms.domain.navigation.dto.NavMenuNode;
import com.kosign.wecms.domain.navigation.dto.NavigationResponse;
import com.kosign.wecms.domain.navigation.entity.MenuItem;
import com.kosign.wecms.domain.navigation.entity.MenuItemTranslation;
import com.kosign.wecms.domain.navigation.entity.ProductMenu;
import com.kosign.wecms.domain.navigation.repository.MenuItemRepository;
import com.kosign.wecms.domain.navigation.repository.MenuItemTranslationRepository;
import com.kosign.wecms.domain.navigation.repository.ProductMenuRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Resolves the navigation tree for the authenticated session.
 *
 * <p>Implements the verified rule
 * {@code Visible Menu = Product Menu Tree (by prdt_type) ∩ User Permission}:
 * <ul>
 *   <li>the product tree is selected from {@link TenantContext#currentPrdtType()}
 *       (the signed JWT — never a client parameter);</li>
 *   <li>a normal user sees the product tree intersected with their
 *       {@code USE_YN='Y'} grants from {@code USER_MENU_AUTH_INFM}
 *       (resolved via {@link MenuAuthorizationService}, the same product-agnostic
 *       resolver {@code /auth/me} uses — kept unchanged);</li>
 *   <li>an admin ({@code authGrCd = ADMIN}) bypasses the per-user grant only and
 *       sees the full product tree (still product-scoped, never a cross-product
 *       union);</li>
 *   <li>a parent collapses when all of its children are filtered out (legacy
 *       {@code OSMCMenuHelper.isPermittedMenu} parent rule).</li>
 * </ul>
 *
 * <p><b>Universe is the tree.</b> A grant for a {@code permission_cd} that is
 * not in the tenant's product tree is inert — it has no node to render. That is
 * what keeps H reduced even if an admin mistakenly granted an F-only leaf.
 *
 * <p>Phase 1 scope: the {@code SHOW_YN} designer gate is enforced; the legacy
 * per-node country gate is not modelled yet (every exported node is VN-only and
 * the refactor has no per-tenant country model — R10). When a country model
 * lands, add the gate here without changing the response contract.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NavigationService {

    private static final String CODE_UNAUTHENTICATED = "WBE1401";
    private static final String SHOW_Y = "Y";
    private static final String LANG_FALLBACK = "EN";

    /** Product type → source menu-group id (traceability echo only). */
    private static final Map<PrdtType, String> MENU_GROUP = Map.of(
            PrdtType.F, "WE_CMSVN",
            PrdtType.M, "WE_CMSVN_LUMENS",
            PrdtType.H, "WE_CMSVN_HAENGSUNG");

    private final MenuItemRepository menuItemRepo;
    private final ProductMenuRepository productMenuRepo;
    private final MenuItemTranslationRepository translationRepo;
    private final MenuAuthorizationService menuAuthService;

    @Transactional(readOnly = true)
    public NavigationResponse resolve() {
        TenantContext.Principal principal = TenantContext.current();
        if (principal == null || principal.userId() == null || principal.bizKey() == null) {
            throw new BusinessException(CODE_UNAUTHENTICATED);
        }
        final String bizKey   = Objects.requireNonNull(principal.bizKey());
        final String userId   = Objects.requireNonNull(principal.userId());
        final String rawPrdt  = principal.prdtType();

        // Recognised product type gates the whole tree. An unrecognised /
        // null product (any of the 11 deferred legacy letters, or a NULL row)
        // has no navigation tree — return an empty, well-formed response
        // rather than throwing, so the gate degrades safely.
        PrdtType prdt = PrdtType.fromCode(rawPrdt).orElse(null);
        if (prdt == null) {
            log.warn("/navigation: product type '{}' is not a recognised navigation product "
                    + "(user={}, tenant={}); returning empty tree", rawPrdt, userId, bizKey);
            return new NavigationResponse(rawPrdt, null, List.of());
        }
        final String productType = prdt.code();

        // Membership for this product (slug + per-product display order).
        List<ProductMenu> membership = productMenuRepo.findAllByProductType(productType);
        if (membership.isEmpty()) {
            log.warn("/navigation: no PRODUCT_MENU rows seeded for product '{}'", productType);
            return new NavigationResponse(productType, MENU_GROUP.get(prdt), List.of());
        }
        Map<String, Integer> seqById = new HashMap<>();
        for (ProductMenu pm : membership) {
            seqById.put(pm.getMenuItemId(), pm.getDispOrd());
        }

        // Catalogue is small + product-independent — load whole, keep only the
        // nodes this product includes.
        Map<String, MenuItem> itemById = new HashMap<>();
        for (MenuItem mi : menuItemRepo.findAll()) {
            if (seqById.containsKey(mi.getMenuItemId())) {
                itemById.put(mi.getMenuItemId(), mi);
            }
        }

        // Labels for the caller's language, with EN as the fallback layer.
        Map<String, String> labels = labelMap(productType, callerLang(principal));

        // Admin bypasses the per-user ∩; a normal user is gated by their grants.
        boolean admin = TenantContext.AUTH_GR_ADMIN.equals(principal.authGrCd());
        Set<String> grants = admin
                ? Set.of()
                : Set.copyOf(menuAuthService.grantedMenuCds(bizKey, userId));

        // children-by-parent, each child list ordered by seq.
        Map<String, List<MenuItem>> childrenByParent = new LinkedHashMap<>();
        List<MenuItem> roots = new ArrayList<>();
        for (MenuItem mi : itemById.values()) {
            if (mi.getParentId() == null) {
                roots.add(mi);
            } else {
                childrenByParent.computeIfAbsent(mi.getParentId(), k -> new ArrayList<>()).add(mi);
            }
        }
        Comparator<MenuItem> bySeq = Comparator
                .comparingInt((MenuItem mi) -> seqById.getOrDefault(mi.getMenuItemId(), 0))
                .thenComparing(MenuItem::getMenuItemId);
        roots.sort(bySeq);
        childrenByParent.values().forEach(list -> list.sort(bySeq));

        List<NavMenuNode> topMenus = new ArrayList<>();
        for (MenuItem root : roots) {
            NavMenuNode node = build(root, childrenByParent, seqById, labels, grants, admin);
            if (node != null) {
                topMenus.add(node);
            }
        }
        return new NavigationResponse(productType, MENU_GROUP.get(prdt), topMenus);
    }

    /**
     * Build a visible node, or {@code null} when it (and its whole subtree)
     * is filtered out.
     *
     * <p>Leaf visible = {@code SHOW_YN='Y'} AND (admin OR grant present).
     * Parent visible = at least one visible child.
     */
    private NavMenuNode build(MenuItem item,
                              Map<String, List<MenuItem>> childrenByParent,
                              Map<String, Integer> seqById,
                              Map<String, String> labels,
                              Set<String> grants,
                              boolean admin) {
        List<MenuItem> rawChildren = childrenByParent.getOrDefault(item.getMenuItemId(), List.of());

        if (rawChildren.isEmpty()) {
            // Leaf (or a parent with no members in this product — treat as leaf).
            if (!SHOW_Y.equals(item.getShowYn())) {
                return null;
            }
            if (item.isParent()) {
                // A grouping node with no children in this product contributes
                // nothing — drop it so empty parents never render.
                return null;
            }
            boolean permitted = admin || grants.contains(item.getPermissionCd());
            return permitted ? toNode(item, seqById, labels, List.of()) : null;
        }

        // Parent: include only if some child survives.
        List<NavMenuNode> kids = new ArrayList<>();
        for (MenuItem child : rawChildren) {
            NavMenuNode kid = build(child, childrenByParent, seqById, labels, grants, admin);
            if (kid != null) {
                kids.add(kid);
            }
        }
        if (kids.isEmpty() || !SHOW_Y.equals(item.getShowYn())) {
            return null;
        }
        return toNode(item, seqById, labels, kids);
    }

    private NavMenuNode toNode(MenuItem item,
                               Map<String, Integer> seqById,
                               Map<String, String> labels,
                               List<NavMenuNode> children) {
        String label = labels.getOrDefault(item.getMenuItemId(), item.getMenuItemId());
        return new NavMenuNode(
                item.getMenuItemId(),
                item.getPermissionCd(),
                item.getParentId(),
                item.getMenuDep() != null ? item.getMenuDep() : 0,
                seqById.getOrDefault(item.getMenuItemId(), 0),
                label,
                item.getRoute(),
                item.getIcon(),
                item.getLegacyMnuId(),
                item.getLegacyWsvcId(),
                children);
    }

    /** caller language → EN fallback overlaid by the caller's own language. */
    private Map<String, String> labelMap(String productType, String lang) {
        Map<String, String> map = new HashMap<>();
        for (MenuItemTranslation t : translationRepo.findAllByProductTypeAndLangCd(productType, LANG_FALLBACK)) {
            map.put(t.getMenuItemId(), t.getLabel());
        }
        if (!LANG_FALLBACK.equals(lang)) {
            for (MenuItemTranslation t : translationRepo.findAllByProductTypeAndLangCd(productType, lang)) {
                map.put(t.getMenuItemId(), t.getLabel());
            }
        }
        return map;
    }

    /** EN / KO / VI from the session; anything else (KM, null) → EN. */
    private String callerLang(TenantContext.Principal principal) {
        if (principal.langCd() == null) {
            return LANG_FALLBACK;
        }
        String lang = principal.langCd().name();
        return switch (lang) {
            case "KO", "VI", "EN" -> lang;
            default -> LANG_FALLBACK;
        };
    }
}
