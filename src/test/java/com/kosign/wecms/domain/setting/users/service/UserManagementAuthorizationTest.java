package com.kosign.wecms.domain.setting.users.service;

import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.auth.repository.BusiAdmnInfmRepository;
import com.kosign.wecms.domain.auth.repository.UserBusiConnInfmRepository;
import com.kosign.wecms.domain.auth.repository.UserInfmRepository;
import com.kosign.wecms.domain.setting.bankaccount.repository.AcctAuthInfoRepository;
import com.kosign.wecms.domain.setting.users.dto.MenuEntitlementUpdateRequest;
import com.kosign.wecms.domain.setting.users.dto.UserCreateRequest;
import com.kosign.wecms.domain.setting.users.dto.UserUpdateRequest;
import com.kosign.wecms.domain.setting.users.repository.UserMenuAuthInfmRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * SEC-1 authorization coverage for Settings &gt; Users.
 *
 * <p>The controller is gated with {@code @PreAuthorize("hasRole('ADMIN')")};
 * these tests cover the service-level <b>defense-in-depth</b> guard
 * ({@code TenantContext.currentIsAdmin()} → {@link AccessDeniedException}
 * → HTTP 403 / {@code WBE1003}). A non-admin caller must be rejected
 * <i>before</i> any repository interaction, on every mutating path;
 * an admin caller must pass the guard.
 */
@ExtendWith(MockitoExtension.class)
class UserManagementAuthorizationTest {

    private static final String TENANT = "VN_VNM0004_000001";
    private static final String ACTOR  = "actor@kosign.dev";
    private static final String TARGET = "target@kosign.dev";

    @Mock private UserInfmRepository         userInfmRepo;
    @Mock private UserBusiConnInfmRepository userBusiConnRepo;
    @Mock private BusiAdmnInfmRepository     busiAdmnRepo;
    @Mock private AcctAuthInfoRepository     acctAuthInfoRepo;
    @Mock private PasswordEncoder            passwordEncoder;
    @Mock private UserMenuAuthInfmRepository menuAuthRepo;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private UserManagementService userService() {
        return new UserManagementService(
                userInfmRepo, userBusiConnRepo, busiAdmnRepo, acctAuthInfoRepo, passwordEncoder,
                new com.kosign.wecms.common.security.TokenRevocationService());
    }

    private MenuEntitlementService menuService() {
        // `self` is field-injected (@Autowired @Lazy) and is only used
        // AFTER the admin guard — the denied path never reaches it.
        return new MenuEntitlementService(userBusiConnRepo, menuAuthRepo);
    }

    private UserCreateRequest createReq(boolean setAsAdmin) {
        return new UserCreateRequest(
                TARGET, "Password!1", "Target User", TARGET, "012345678", "Dev", "Eng", setAsAdmin);
    }

    private UserUpdateRequest updateReq(boolean setAsAdmin) {
        return new UserUpdateRequest(
                "Target User", TARGET, "012345678", "Dev", "Eng", setAsAdmin, null);
    }

    private void bindUser(boolean admin) {
        TenantContext.set(ACTOR, ACTOR, TENANT, LangCd.EN,
                admin ? TenantContext.AUTH_GR_ADMIN : TenantContext.AUTH_GR_USER, "F");
    }

    // ----------------------------------------------------- non-admin denied

    @Test
    void create_byNonAdmin_isDeniedBeforeAnyDbAccess() {
        bindUser(false);
        UserManagementService service = userService();

        assertThatThrownBy(() -> service.create(createReq(false)))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(userInfmRepo, userBusiConnRepo, busiAdmnRepo, acctAuthInfoRepo, passwordEncoder);
    }

    @Test
    void create_byNonAdmin_settingSelfAsAdmin_isDenied() {
        // Privilege-escalation attempt: a normal user trying to create an
        // admin (or promote) must be blocked.
        bindUser(false);
        UserManagementService service = userService();

        assertThatThrownBy(() -> service.create(createReq(true)))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(userInfmRepo, userBusiConnRepo, busiAdmnRepo, acctAuthInfoRepo, passwordEncoder);
    }

    @Test
    void update_byNonAdmin_isDeniedBeforeAnyDbAccess() {
        bindUser(false);
        UserManagementService service = userService();

        assertThatThrownBy(() -> service.update(TARGET, updateReq(true)))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(userInfmRepo, userBusiConnRepo, busiAdmnRepo, acctAuthInfoRepo, passwordEncoder);
    }

    @Test
    void delete_byNonAdmin_isDeniedBeforeAnyDbAccess() {
        bindUser(false);
        UserManagementService service = userService();

        assertThatThrownBy(() -> service.delete(TARGET))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(userInfmRepo, userBusiConnRepo, busiAdmnRepo, acctAuthInfoRepo);
    }

    @Test
    void menuEntitlementSave_byNonAdmin_isDeniedBeforeAnyDbAccess() {
        bindUser(false);
        MenuEntitlementService service = menuService();

        assertThatThrownBy(() ->
                service.save(TARGET, new MenuEntitlementUpdateRequest(List.of("banking.account_inquiry"))))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(userBusiConnRepo, menuAuthRepo);
    }

    // ------------------------------------------------------- admin passes guard

    @Test
    void create_byAdmin_passesGuardAndReachesBusinessLogic() {
        // Admin context: the guard must NOT trip — execution proceeds to
        // the duplicate-id check. Stub it to fire so we prove the guard
        // was passed (a WBE_USERS_DUPLICATE_LOGIN_ID business error, not
        // an AccessDeniedException).
        bindUser(true);
        when(userInfmRepo.existsByUserId(TARGET)).thenReturn(true);
        UserManagementService service = userService();

        assertThatThrownBy(() -> service.create(createReq(false)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("WBE_USERS_DUPLICATE_LOGIN_ID");
    }
}
