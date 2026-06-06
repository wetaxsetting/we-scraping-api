package com.kosign.wecms.domain.navigation.service;

import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.auth.service.MenuAuthorizationService;
import com.kosign.wecms.domain.navigation.repository.MenuItemRepository;
import com.kosign.wecms.domain.navigation.repository.ProductMenuRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Unit coverage for {@link MenuAuthorizationGuard} (F-3 Phase A) — the
 * server-side {@code @menuAuthz.canAccess(cd)} decision. Mirrors the
 * navigation visibility rule: product-tree gate (admin + user), admin bypass
 * of the per-user grant, per-user grant for normal users; Settings codes skip
 * the product-tree gate; unknown codes deny.
 */
@ExtendWith(MockitoExtension.class)
class MenuAuthorizationGuardTest {

    private static final String TENANT = "VN_VNM0004_000001";
    private static final String USER   = "arthurmorgan@rdr2.com";

    // Representative product-tree leaf cds (verified set, §1 of the
    // permission-code-normalization doc).
    private static final String F_LEAF      = "banking-vnd-account-inquiry";
    private static final String FM_ONLY     = "banking-reports-cash-flow"; // not in H tree
    private static final String HS_REPORT   = "banking-reports-daily-financial-hs";
    private static final String SETTINGS_CD = "settings-core-bank-account"; // not a product leaf
    private static final String UNKNOWN_CD  = "totally-bogus-cd";

    @Mock private MenuItemRepository menuItemRepo;
    @Mock private ProductMenuRepository productMenuRepo;
    @Mock private MenuAuthorizationService menuAuthService;

    @InjectMocks private MenuAuthorizationGuard guard;

    @AfterEach
    void clearContext() {
        TenantContext.clear();
    }

    private void session(String prdtType, String authGrCd) {
        TenantContext.set(USER, USER, TENANT, LangCd.EN, authGrCd, prdtType);
    }

    // ---- product-tree leaves --------------------------------------------

    @Test
    void fAdmin_canAccessFmenuCd() {
        session("F", TenantContext.AUTH_GR_ADMIN);
        when(menuItemRepo.existsByPermissionCd(F_LEAF)).thenReturn(true);
        when(productMenuRepo.existsInProductTree("F", F_LEAF)).thenReturn(true);

        assertThat(guard.canAccess(F_LEAF)).isTrue();
    }

    @Test
    void hAdmin_cannotAccessFonlyCd() {
        session("H", TenantContext.AUTH_GR_ADMIN);
        when(menuItemRepo.existsByPermissionCd(FM_ONLY)).thenReturn(true);
        when(productMenuRepo.existsInProductTree("H", FM_ONLY)).thenReturn(false);

        // Product-tree gate denies even an admin (Cash Flow is not in H's tree).
        assertThat(guard.canAccess(FM_ONLY)).isFalse();
    }

    @Test
    void hAdmin_canAccessHsReportCd() {
        session("H", TenantContext.AUTH_GR_ADMIN);
        when(menuItemRepo.existsByPermissionCd(HS_REPORT)).thenReturn(true);
        when(productMenuRepo.existsInProductTree("H", HS_REPORT)).thenReturn(true);

        assertThat(guard.canAccess(HS_REPORT)).isTrue();
    }

    @Test
    void hUser_withHsGrant_canAccessHsCd() {
        session("H", TenantContext.AUTH_GR_USER);
        when(menuItemRepo.existsByPermissionCd(HS_REPORT)).thenReturn(true);
        when(productMenuRepo.existsInProductTree("H", HS_REPORT)).thenReturn(true);
        when(menuAuthService.isGranted(TENANT, USER, HS_REPORT)).thenReturn(true);

        assertThat(guard.canAccess(HS_REPORT)).isTrue();
    }

    @Test
    void hUser_withStandardDfGrant_cannotAccessHsCd() {
        session("H", TenantContext.AUTH_GR_USER);
        when(menuItemRepo.existsByPermissionCd(HS_REPORT)).thenReturn(true);
        when(productMenuRepo.existsInProductTree("H", HS_REPORT)).thenReturn(true);
        // User holds the STANDARD code, not the -hs split code → no -hs grant.
        when(menuAuthService.isGranted(TENANT, USER, HS_REPORT)).thenReturn(false);

        assertThat(guard.canAccess(HS_REPORT)).isFalse();
    }

    // ---- settings (product-invariant; skip product-tree gate) -----------

    @Test
    void settingsCd_normalUser_requiresGrant() {
        session("H", TenantContext.AUTH_GR_USER);
        when(menuItemRepo.existsByPermissionCd(SETTINGS_CD)).thenReturn(false); // not a product leaf
        when(menuAuthService.isGranted(TENANT, USER, SETTINGS_CD)).thenReturn(false);

        // Settings cd skips the product-tree gate, but a normal user still
        // needs the grant — without it, denied.
        assertThat(guard.canAccess(SETTINGS_CD)).isFalse();
    }

    @Test
    void settingsCd_admin_allowedWithoutProductGate() {
        session("H", TenantContext.AUTH_GR_ADMIN);
        when(menuItemRepo.existsByPermissionCd(SETTINGS_CD)).thenReturn(false);
        // productMenuRepo is intentionally NOT stubbed: if the product-tree gate
        // ran, the unstubbed call would return false and deny — so an allow here
        // proves the gate was skipped for the settings code.

        assertThat(guard.canAccess(SETTINGS_CD)).isTrue();
    }

    // ---- unknown / invalid ---------------------------------------------

    @Test
    void unknownCd_isDenied() {
        // Admin context to prove the deny is unconditional (fails closed even
        // for an admin): an unknown cd is neither a product leaf nor settings-*.
        session("F", TenantContext.AUTH_GR_ADMIN);
        when(menuItemRepo.existsByPermissionCd(UNKNOWN_CD)).thenReturn(false);

        assertThat(guard.canAccess(UNKNOWN_CD)).isFalse();
    }

    @Test
    void blankOrNullCd_isDenied() {
        session("F", TenantContext.AUTH_GR_ADMIN);
        // No repo interaction expected for blank/null — guard short-circuits.
        lenient().when(menuItemRepo.existsByPermissionCd("")).thenReturn(false);

        assertThat(guard.canAccess(null)).isFalse();
        assertThat(guard.canAccess("   ")).isFalse();
    }
}
