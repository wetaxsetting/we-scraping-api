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

    // ---- param-aware overloads (F-3 §4.1) -------------------------------

    private static final String VND_INQ = "banking-vnd-account-inquiry";
    private static final String FCY_INQ = "banking-fcy-update-inquire";
    private static final String EINV_SALES = "addon-einvoices-sales";
    private static final String EINV_EXP = "addon-einvoices-expenses";

    @Test
    void accountInquiry_vnd_resolvesVndCd() {
        // Stub ONLY the VND cd — if the overload resolved the FCY cd instead,
        // the unstubbed FCY lookups return false and the assertion would fail.
        session("F", TenantContext.AUTH_GR_ADMIN);
        when(menuItemRepo.existsByPermissionCd(VND_INQ)).thenReturn(true);
        when(productMenuRepo.existsInProductTree("F", VND_INQ)).thenReturn(true);

        assertThat(guard.canAccessAccountInquiry("VND")).isTrue();
    }

    @Test
    void accountInquiry_missing_defaultsToVnd() {
        // Documented default: absent/blank currency → VND cd.
        session("F", TenantContext.AUTH_GR_ADMIN);
        when(menuItemRepo.existsByPermissionCd(VND_INQ)).thenReturn(true);
        when(productMenuRepo.existsInProductTree("F", VND_INQ)).thenReturn(true);

        assertThat(guard.canAccessAccountInquiry(null)).isTrue();
        assertThat(guard.canAccessAccountInquiry("  ")).isTrue();
    }

    @Test
    void accountInquiry_fcy_resolvesFcyCd() {
        session("F", TenantContext.AUTH_GR_ADMIN);
        when(menuItemRepo.existsByPermissionCd(FCY_INQ)).thenReturn(true);
        when(productMenuRepo.existsInProductTree("F", FCY_INQ)).thenReturn(true);

        assertThat(guard.canAccessAccountInquiry("FCY")).isTrue();
        // A specific foreign code is also treated as FCY.
        assertThat(guard.canAccessAccountInquiry("USD")).isTrue();
    }

    @Test
    void einvoice_sales_resolvesSalesCd() {
        session("F", TenantContext.AUTH_GR_ADMIN);
        when(menuItemRepo.existsByPermissionCd(EINV_SALES)).thenReturn(true);
        when(productMenuRepo.existsInProductTree("F", EINV_SALES)).thenReturn(true);

        assertThat(guard.canAccessEInvoice("SALES")).isTrue();
    }

    @Test
    void einvoice_expense_resolvesExpensesCd() {
        session("F", TenantContext.AUTH_GR_ADMIN);
        when(menuItemRepo.existsByPermissionCd(EINV_EXP)).thenReturn(true);
        when(productMenuRepo.existsInProductTree("F", EINV_EXP)).thenReturn(true);

        assertThat(guard.canAccessEInvoice("EXPENSE")).isTrue();
        assertThat(guard.canAccessEInvoice("EXPENSES")).isTrue();
    }

    @Test
    void einvoice_unknownOrMissingType_failsClosed() {
        // No repo interaction — unknown/missing discriminator denies before any lookup.
        session("F", TenantContext.AUTH_GR_ADMIN);
        assertThat(guard.canAccessEInvoice("BOGUS")).isFalse();
        assertThat(guard.canAccessEInvoice(null)).isFalse();
        assertThat(guard.canAccessEInvoice("")).isFalse();
    }

    @Test
    void einvoice_hCaller_deniedByProductGate() {
        // e-Invoice is F/M only — an H caller is denied even with a known type.
        session("H", TenantContext.AUTH_GR_ADMIN);
        when(menuItemRepo.existsByPermissionCd(EINV_SALES)).thenReturn(true);
        when(productMenuRepo.existsInProductTree("H", EINV_SALES)).thenReturn(false);

        assertThat(guard.canAccessEInvoice("SALES")).isFalse();
    }

    @Test
    void canAccessAny_allowsWhenAnyCodeResolves() {
        // Caller can access the FCY transactions cd but not VND — canAccessAny
        // should still allow (coarse feature-level gate for account-keyed
        // transaction sub-resources).
        session("F", TenantContext.AUTH_GR_ADMIN);
        when(menuItemRepo.existsByPermissionCd(VND_INQ)).thenReturn(true);
        when(productMenuRepo.existsInProductTree("F", VND_INQ)).thenReturn(false);
        when(menuItemRepo.existsByPermissionCd(FCY_INQ)).thenReturn(true);
        when(productMenuRepo.existsInProductTree("F", FCY_INQ)).thenReturn(true);

        assertThat(guard.canAccessAny(VND_INQ, FCY_INQ)).isTrue();
    }

    @Test
    void canAccessAny_deniesWhenNoCodeResolves() {
        session("H", TenantContext.AUTH_GR_ADMIN);
        when(menuItemRepo.existsByPermissionCd(VND_INQ)).thenReturn(true);
        when(productMenuRepo.existsInProductTree("H", VND_INQ)).thenReturn(false);
        when(menuItemRepo.existsByPermissionCd(FCY_INQ)).thenReturn(true);
        when(productMenuRepo.existsInProductTree("H", FCY_INQ)).thenReturn(false);

        assertThat(guard.canAccessAny(VND_INQ, FCY_INQ)).isFalse();
        assertThat(guard.canAccessAny((String[]) null)).isFalse();
    }
}
