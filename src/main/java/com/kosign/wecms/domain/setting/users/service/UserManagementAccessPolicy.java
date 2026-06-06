package com.kosign.wecms.domain.setting.users.service;

import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.setting.users.repository.UserMenuAuthInfmRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Authorization policy for the Settings &gt; Users surface, referenced
 * from {@code @PreAuthorize} SpEL as {@code @userMgmtAccess}.
 *
 * <p>Split per the revised rule (supersedes the blanket SEC-1
 * admin-only gate):
 * <ul>
 *   <li><b>Read</b> (list, detail) — {@link #canViewUsers()}: ADMIN
 *       <em>or</em> a normal user holding the {@code settings-core-users}
 *       menu permission. Lets a granted viewer open the panel read-only.</li>
 *   <li><b>Write / permission-management</b> (create, update, delete,
 *       menu-entitlement, avatar, check-id) — stays {@code hasRole('ADMIN')}
 *       on the endpoint, with the service-layer admin guard as
 *       defense-in-depth. {@code settings-core-users} NEVER grants these.</li>
 * </ul>
 *
 * <p>Viewing the user roster is not a privilege-escalation vector;
 * management is, which is why only reads open up. A normal user can never
 * promote anyone (writes are admin-only at both the controller and the
 * service).
 */
@Component("userMgmtAccess")
@RequiredArgsConstructor
public class UserManagementAccessPolicy {

    /** Menu slug that grants read access to the Users panel. Matches the
     *  frontend entitlement leaf id. */
    static final String VIEW_USERS_MENU_CD = "settings-core-users";
    private static final String USE_Y = "Y";

    private final UserMenuAuthInfmRepository menuAuthRepo;

    /**
     * True when the caller may READ the user list / detail: an ADMIN, or a
     * normal user granted {@code settings-core-users} (USE_YN='Y') in the
     * current tenant. Used by the read endpoints' {@code @PreAuthorize}.
     */
    @Transactional(readOnly = true)
    public boolean canViewUsers() {
        if (TenantContext.currentIsAdmin()) {
            return true;
        }
        String bizKey = TenantContext.currentBizKey();
        String userId = TenantContext.currentUserId();
        if (bizKey == null || userId == null) {
            return false;
        }
        return menuAuthRepo.existsByBizKeyAndUserIdAndMenuAuthCdAndUseYn(
                bizKey, userId, VIEW_USERS_MENU_CD, USE_Y);
    }
}
