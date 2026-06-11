package com.kosign.wecms.domain.setting.users.service;

import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.setting.users.repository.UserMenuAuthInfmRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Read-access policy for Settings &gt; Users (revised SEC-1). Read is
 * allowed for ADMIN <b>or</b> a normal user granted
 * {@code settings-core-users}; writes stay admin-only (covered by
 * {@link UserManagementAuthorizationTest}).
 */
@ExtendWith(MockitoExtension.class)
class UserManagementAccessPolicyTest {

    private static final String TENANT = "VN_VNM0004_000001";
    private static final String USER   = "arthurmorgan@rdr2.com";
    private static final String CD      = "settings-core-users";

    @Mock private UserMenuAuthInfmRepository menuAuthRepo;
    @InjectMocks private UserManagementAccessPolicy policy;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void admin_canView_withoutTouchingTheGrantTable() {
        TenantContext.set(USER, USER, TENANT, LangCd.EN, TenantContext.AUTH_GR_ADMIN, "F");
        assertThat(policy.canViewUsers()).isTrue();
        verifyNoInteractions(menuAuthRepo);
    }

    @Test
    void normalUser_withSettingsCoreUsersGrant_canView() {
        TenantContext.set(USER, USER, TENANT, LangCd.EN, TenantContext.AUTH_GR_USER, "F");
        when(menuAuthRepo.existsByBizKeyAndUserIdAndMenuAuthCdAndUseYn(TENANT, USER, CD, "Y"))
                .thenReturn(true);
        assertThat(policy.canViewUsers()).isTrue();
    }

    @Test
    void normalUser_withoutGrant_cannotView() {
        TenantContext.set(USER, USER, TENANT, LangCd.EN, TenantContext.AUTH_GR_USER, "F");
        lenient().when(menuAuthRepo.existsByBizKeyAndUserIdAndMenuAuthCdAndUseYn(TENANT, USER, CD, "Y"))
                .thenReturn(false);
        assertThat(policy.canViewUsers()).isFalse();
    }
}
