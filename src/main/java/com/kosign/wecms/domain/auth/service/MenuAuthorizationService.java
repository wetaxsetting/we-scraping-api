package com.kosign.wecms.domain.auth.service;

import com.kosign.wecms.domain.setting.users.entity.UserMenuAuthInfm;
import com.kosign.wecms.domain.setting.users.repository.UserMenuAuthInfmRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Resolves the set of menu slugs a user is allowed to see, from
 * {@code USER_MENU_AUTH_INFM} (the table the Settings &gt; Users &gt;
 * Menu Entitlement screen writes). Consumed by {@link AuthService#me()}
 * so the frontend can filter the top nav, sidebar, and routes after
 * login / reload — there is no menu claim in the JWT (entitlement
 * changes must take effect on the next {@code /me} without a token
 * re-issue), so this is read fresh per {@code /me} call.
 *
 * <p>Admin bypass is decided by the caller ({@code AuthService.me()})
 * from the JWT {@code authGrCd} claim — an admin gets every menu and
 * this resolver is not consulted. For a normal user, the granted set is
 * the {@code USE_YN='Y'} rows for the (tenant, user) pair. The
 * {@code menu_auth_cd} values are the kebab-case leaf slugs the
 * entitlement UI uses (e.g. {@code addon-einvoices-sales}); the frontend
 * maps them to routes via {@code lib/menuAuth.ts}.
 */
@Service
@RequiredArgsConstructor
public class MenuAuthorizationService {

    private static final String USE_Y = "Y";

    private final UserMenuAuthInfmRepository menuAuthRepo;

    /**
     * Granted menu slugs for a (tenant, user) pair — only the
     * {@code USE_YN='Y'} rows. Returns an empty list when the user has
     * no granted menus (a valid state: the frontend then renders the
     * safe "no menus" landing). Order is the repository's natural order;
     * the frontend does not rely on it.
     */
    @Transactional(readOnly = true)
    public List<String> grantedMenuCds(String bizKey, String userId) {
        return menuAuthRepo.findAllByBizKeyAndUserId(bizKey, userId).stream()
                .filter(row -> USE_Y.equals(row.getUseYn()))
                .map(UserMenuAuthInfm::getMenuAuthCd)
                .toList();
    }

    /**
     * Targeted grant probe — true when the (tenant, user) pair holds a
     * {@code USE_YN='Y'} row for {@code menuAuthCd}. Backs the server-side
     * menu guard's per-user check (the per-row {@code exists} avoids loading
     * the whole grant set when only one cd is being authorised).
     */
    @Transactional(readOnly = true)
    public boolean isGranted(String bizKey, String userId, String menuAuthCd) {
        return menuAuthRepo.existsByBizKeyAndUserIdAndMenuAuthCdAndUseYn(
                bizKey, userId, menuAuthCd, USE_Y);
    }
}
