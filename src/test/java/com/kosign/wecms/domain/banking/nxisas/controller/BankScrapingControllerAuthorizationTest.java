package com.kosign.wecms.domain.banking.nxisas.controller;

import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.auth.service.MenuAuthorizationService;
import com.kosign.wecms.domain.navigation.repository.MenuItemRepository;
import com.kosign.wecms.domain.navigation.repository.ProductMenuRepository;
import com.kosign.wecms.domain.navigation.service.MenuAuthorizationGuard;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Audit fix (2026-06-19) — server-side authorization for the bank-scraping
 * flow ({@code /api/v1/banking/scraping/**}).
 *
 * <p>The flow is shared by three legitimate parent screens, so it is gated on
 * {@code canAccessAny('settings-core-login-credentials',
 * 'settings-core-bank-account', 'payment-corporate-card-transactions')}. These
 * tests prove (1) the exact class-level wiring, and (2) that <i>each</i> parent
 * entitlement independently grants access (no over-restriction), an admin is
 * allowed, and a user holding none of the three is denied (→ 403).
 */
@ExtendWith(MockitoExtension.class)
class BankScrapingControllerAuthorizationTest {

    private static final String TENANT = "VN_VNM0004_000001";
    private static final String USER   = "arthurmorgan@rdr2.com";

    private static final String LOGIN_CRED = "settings-core-login-credentials";
    private static final String BANK_ACCT   = "settings-core-bank-account";
    private static final String CARD        = "payment-corporate-card-transactions";

    private static final String[] CDS = { LOGIN_CRED, BANK_ACCT, CARD };

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

    // ---- (1) Wiring ----------------------------------------------------

    @Test
    void controller_isGatedOnAnyOfThreeParentCds() {
        PreAuthorize ann = BankScrapingController.class.getAnnotation(PreAuthorize.class);
        assertThat(ann)
                .as("BankScrapingController must carry a class-level @PreAuthorize")
                .isNotNull();
        assertThat(ann.value())
                .isEqualTo("@menuAuthz.canAccessAny('" + LOGIN_CRED + "', '" + BANK_ACCT
                        + "', '" + CARD + "')");
    }

    // ---- (2) Decision matrix ------------------------------------------

    @Test
    void admin_canAccess() {
        session("F", TenantContext.AUTH_GR_ADMIN);
        assertThat(guard.canAccessAny(CDS)).isTrue();
    }

    @Test
    void user_withLoginCredentialsGrant_canAccess() {
        session("F", TenantContext.AUTH_GR_USER);
        when(menuAuthService.isGranted(TENANT, USER, LOGIN_CRED)).thenReturn(true);
        assertThat(guard.canAccessAny(CDS)).isTrue();
    }

    @Test
    void user_withBankAccountGrant_canAccess() {
        // First cd (login-credentials) un-granted → falls through to bank-account.
        // lenient(): canAccessAny probes login-credentials first (isGranted with a
        // non-matching arg), which STRICT_STUBS would otherwise flag.
        session("F", TenantContext.AUTH_GR_USER);
        lenient().when(menuAuthService.isGranted(TENANT, USER, BANK_ACCT)).thenReturn(true);
        assertThat(guard.canAccessAny(CDS)).isTrue();
    }

    @Test
    void user_withCorporateCardGrant_canAccess() {
        // The card cd is a product-tree leaf — must pass the product-tree gate
        // AND the per-user grant. Proves the card add-flow is not over-restricted.
        // lenient(): the two settings cds are probed first (existsByPermissionCd /
        // isGranted with non-matching args) before the card cd resolves true.
        session("F", TenantContext.AUTH_GR_USER);
        lenient().when(menuItemRepo.existsByPermissionCd(CARD)).thenReturn(true);
        lenient().when(productMenuRepo.existsInProductTree("F", CARD)).thenReturn(true);
        lenient().when(menuAuthService.isGranted(TENANT, USER, CARD)).thenReturn(true);
        assertThat(guard.canAccessAny(CDS)).isTrue();
    }

    @Test
    void user_withNoneOfTheThree_isDenied() {
        // No grants stubbed → every canAccess returns false → 403.
        session("F", TenantContext.AUTH_GR_USER);
        assertThat(guard.canAccessAny(CDS)).isFalse();
    }
}
