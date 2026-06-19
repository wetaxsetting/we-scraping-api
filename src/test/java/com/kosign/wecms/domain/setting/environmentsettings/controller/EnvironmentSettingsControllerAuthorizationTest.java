package com.kosign.wecms.domain.setting.environmentsettings.controller;

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
 * Audit fix (2026-06-19) — server-side authorization for the generic tenant
 * environment-settings store ({@code /api/v1/settings/environment-settings}).
 *
 * <p>Shared by the Preferences (codes 00074 / 00077) and Firm Banking Settings
 * (codes 00078 / 00079) panels, so the single GET/PUT pair is gated on
 * {@code canAccessAny('settings-core-preference', 'settings-firm-banking-settings')}.
 * These tests prove the class-level wiring, that either parent entitlement
 * independently grants, an admin is allowed, and a user with neither is denied
 * (→ 403 / {@code WBE1003}).
 */
@ExtendWith(MockitoExtension.class)
class EnvironmentSettingsControllerAuthorizationTest {

    private static final String TENANT = "VN_VNM0004_000001";
    private static final String USER   = "arthurmorgan@rdr2.com";

    private static final String PREFERENCE    = "settings-core-preference";
    private static final String FIRM_BANKING  = "settings-firm-banking-settings";
    private static final String[] CDS = { PREFERENCE, FIRM_BANKING };

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
    void controller_isGatedOnEitherSettingsCd() {
        PreAuthorize ann = EnvironmentSettingsController.class.getAnnotation(PreAuthorize.class);
        assertThat(ann)
                .as("EnvironmentSettingsController must carry a class-level @PreAuthorize")
                .isNotNull();
        assertThat(ann.value())
                .isEqualTo("@menuAuthz.canAccessAny('" + PREFERENCE + "', '" + FIRM_BANKING + "')");
    }

    // ---- (2) Decision matrix ------------------------------------------

    @Test
    void admin_canAccess() {
        session(TenantContext.AUTH_GR_ADMIN);
        assertThat(guard.canAccessAny(CDS)).isTrue();
    }

    @Test
    void user_withPreferenceGrant_canAccess() {
        session(TenantContext.AUTH_GR_USER);
        when(menuAuthService.isGranted(TENANT, USER, PREFERENCE)).thenReturn(true);
        assertThat(guard.canAccessAny(CDS)).isTrue();
    }

    @Test
    void user_withFirmBankingGrant_canAccess() {
        // Preference un-granted → falls through to firm-banking-settings.
        // lenient(): canAccessAny probes preference first (isGranted, non-matching arg).
        session(TenantContext.AUTH_GR_USER);
        lenient().when(menuAuthService.isGranted(TENANT, USER, FIRM_BANKING)).thenReturn(true);
        assertThat(guard.canAccessAny(CDS)).isTrue();
    }

    @Test
    void user_withNeitherGrant_isDenied() {
        session(TenantContext.AUTH_GR_USER);
        assertThat(guard.canAccessAny(CDS)).isFalse();
    }
}
