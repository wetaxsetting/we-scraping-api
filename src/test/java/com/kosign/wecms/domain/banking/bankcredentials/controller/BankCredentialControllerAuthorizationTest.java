package com.kosign.wecms.domain.banking.bankcredentials.controller;

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
import static org.mockito.Mockito.when;

/**
 * Audit X-1 fix (2026-06-19) — server-side authorization for the Bank
 * Credentials CRUD controller ({@code BANK_CERT_INFO}).
 *
 * <p>Same two-part proof as {@code ReportEndpointAuthorizationTest} (no Spring
 * context / DB):
 * <ol>
 *   <li><b>Wiring</b> — the controller carries the class-level
 *       {@code @PreAuthorize("@menuAuthz.canAccess('settings-core-login-credentials')")}
 *       (covers list / create / update / delete), read reflectively so a dropped
 *       or wrong gate fails the build.</li>
 *   <li><b>Decision matrix</b> — {@link MenuAuthorizationGuard#canAccess(String)}
 *       (the bean that annotation invokes) allows an admin and a granted user,
 *       and denies (→ 403 / {@code WBE1003}) a user without the entitlement.</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class BankCredentialControllerAuthorizationTest {

    private static final String TENANT = "VN_VNM0004_000001";
    private static final String USER   = "arthurmorgan@rdr2.com";
    private static final String CD      = "settings-core-login-credentials";

    @Mock private MenuItemRepository menuItemRepo;
    @Mock private ProductMenuRepository productMenuRepo;
    @Mock private MenuAuthorizationService menuAuthService;
    @InjectMocks private MenuAuthorizationGuard guard;

    @AfterEach
    void clearContext() {
        TenantContext.clear();
    }

    private void session(String authGrCd) {
        TenantContext.set(USER, USER, TENANT, LangCd.EN, authGrCd, "F");
    }

    // ---- (1) Wiring ----------------------------------------------------

    @Test
    void controller_isGatedOnLoginCredentialsCd() {
        PreAuthorize ann = BankCredentialController.class.getAnnotation(PreAuthorize.class);
        assertThat(ann)
                .as("BankCredentialController must carry a class-level @PreAuthorize")
                .isNotNull();
        assertThat(ann.value())
                .isEqualTo("@menuAuthz.canAccess('" + CD + "')");
    }

    // ---- (2) Decision matrix ------------------------------------------

    @Test
    void admin_canAccess() {
        // settings-* code: not a product-tree leaf → product gate skipped;
        // admin bypasses the per-user grant.
        session(TenantContext.AUTH_GR_ADMIN);
        assertThat(guard.canAccess(CD)).isTrue();
    }

    @Test
    void user_withLoginCredentialsGrant_canAccess() {
        session(TenantContext.AUTH_GR_USER);
        when(menuAuthService.isGranted(TENANT, USER, CD)).thenReturn(true);
        assertThat(guard.canAccess(CD)).isTrue();
    }

    @Test
    void user_withoutGrant_isDenied() {
        session(TenantContext.AUTH_GR_USER);
        when(menuAuthService.isGranted(TENANT, USER, CD)).thenReturn(false);
        assertThat(guard.canAccess(CD)).isFalse();
    }
}
