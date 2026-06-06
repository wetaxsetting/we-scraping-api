package com.kosign.wecms.domain.navigation.service;

import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.auth.service.MenuAuthorizationService;
import com.kosign.wecms.domain.navigation.repository.MenuItemRepository;
import com.kosign.wecms.domain.navigation.repository.ProductMenuRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Server-side menu authorization guard (F-3, Phase A).
 *
 * <p>Exposed as the Spring bean {@code @menuAuthz} so controllers can gate an
 * endpoint with method security, e.g.
 * <pre>{@code @PreAuthorize("@menuAuthz.canAccess('banking-reports-cash-flow')")}</pre>
 * mirroring the existing {@code @userMgmtAccess.canViewUsers()} precedent.
 *
 * <p><b>Phase A ships the guard only — no endpoint is annotated yet.</b> Wiring
 * it onto business endpoints is Phases B–E of
 * {@code docs/jex/features/menu-product-type-management/server-side-menu-authorization-design.md}.
 *
 * <p>The decision mirrors the navigation visibility rule exactly (so a menu the
 * nav would hide is an endpoint this guard denies — they cannot drift):
 * <ol>
 *   <li><b>Validity</b> — the cd must be a product-tree leaf (in {@code MENU_ITEM})
 *       OR a {@code settings-*} code. Anything else is unknown → deny.</li>
 *   <li><b>Product-tree gate</b> — a product-tree leaf must belong to the
 *       <i>current tenant's</i> product ({@code PRODUCT_MENU} for
 *       {@link TenantContext#currentPrdtType()}). Applies to admin and normal
 *       user alike, so an H caller is denied an F/M-only cd (Cash Flow, Daily
 *       Cash, e-Invoices, …). {@code settings-*} codes are product-invariant
 *       and skip this gate.</li>
 *   <li><b>Admin bypass</b> — an admin ({@code authGrCd=ADMIN}) skips the
 *       per-user grant, but never the product-tree gate.</li>
 *   <li><b>Per-user grant</b> — a normal user must hold a {@code USE_YN='Y'}
 *       row in {@code USER_MENU_AUTH_INFM} for the cd.</li>
 * </ol>
 *
 * <p>Reuses {@link MenuAuthorizationService} (unchanged resolver) and the
 * navigation catalogue ({@code MENU_ITEM} / {@code PRODUCT_MENU}). All identity
 * + product context comes from the signed JWT via {@link TenantContext} — never
 * from request input.
 */
@Component("menuAuthz")
@RequiredArgsConstructor
public class MenuAuthorizationGuard {

    /** Recognised non-product (product-invariant) code family — Settings.
     *  Settings panels are not seeded in the product tree (they are GNB/modal,
     *  identical for F/M/H), so they are authorised by grant only. */
    private static final String SETTINGS_PREFIX = "settings-";

    private final MenuItemRepository menuItemRepo;
    private final ProductMenuRepository productMenuRepo;
    private final MenuAuthorizationService menuAuthService;

    /**
     * Whether the current caller may invoke an endpoint guarded by
     * {@code permissionCd}. See the class Javadoc for the four-step rule.
     *
     * @param permissionCd the kebab menu permission code (an annotation
     *                     literal, not user input)
     * @return {@code true} to allow; {@code false} → Spring raises
     *         {@code AccessDeniedException} → HTTP 403 / {@code WBE1003}
     */
    public boolean canAccess(String permissionCd) {
        if (permissionCd == null) {
            return false;
        }
        final String cd = permissionCd.trim();
        if (cd.isEmpty()) {
            return false;
        }

        final boolean productLeaf = menuItemRepo.existsByPermissionCd(cd);
        final boolean settings = cd.startsWith(SETTINGS_PREFIX);

        // (1) Validity — only product-tree leaves and settings-* codes are
        //     recognised. An unknown / typo'd cd is denied outright (for admin
        //     and user), so a misannotated endpoint fails closed.
        if (!productLeaf && !settings) {
            return false;
        }

        // (2) Product-tree gate — product leaves must belong to THIS tenant's
        //     product tree. Settings codes are product-invariant → skip.
        if (productLeaf) {
            final String prdtType = TenantContext.currentPrdtType();
            if (prdtType == null || !productMenuRepo.existsInProductTree(prdtType, cd)) {
                return false;
            }
        }

        // (3) Admin bypasses the per-user grant (never the product-tree gate).
        if (TenantContext.currentIsAdmin()) {
            return true;
        }

        // (4) Normal user — must hold the grant.
        final String bizKey = TenantContext.currentBizKey();
        final String userId = TenantContext.currentUserId();
        if (bizKey == null || userId == null) {
            return false;
        }
        return menuAuthService.isGranted(bizKey, userId, cd);
    }
}
