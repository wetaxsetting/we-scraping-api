package com.kosign.wecms.domain.setting.users.service;

import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.auth.repository.UserBusiConnInfmRepository;
import com.kosign.wecms.domain.setting.users.dto.MenuEntitlementResponse;
import com.kosign.wecms.domain.setting.users.dto.MenuEntitlementUpdateRequest;
import com.kosign.wecms.domain.setting.users.entity.UserMenuAuthInfm;
import com.kosign.wecms.domain.setting.users.repository.UserMenuAuthInfmRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Menu Entitlement CRUD service — backs the Settings &gt; Users panel's
 * "Menu Entitlement" tab. One row per granted {@code MENU_AUTH_CD}
 * slug per (tenant, user) pair in {@link UserMenuAuthInfm}.
 *
 * <p>Save uses <b>delete-then-insert</b>: every existing grant for the
 * (tenant, user) pair is deleted, then the request's slug list is
 * re-inserted with {@code USE_YN='Y'}. This matches the legacy
 * Settings popover patterns (Approval Line, View Setting) and keeps
 * the response reflective of the exact requested set without per-row
 * diffing.
 *
 * <p>Authorisation: every call verifies the target user has an active
 * link to the JWT tenant via {@code USER_BUSI_CONN_INFM} —
 * {@code WBE_USERS_NOT_FOUND} otherwise so we never leak cross-tenant
 * existence.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MenuEntitlementService {

    private static final String DEL_N  = "N";
    private static final String FLAG_Y = "Y";

    private final UserBusiConnInfmRepository userBusiConnRepo;
    private final UserMenuAuthInfmRepository menuAuthRepo;

    /** Self-reference so {@link #save} can re-enter the
     *  {@code @Transactional} {@link #saveInTransaction} in a FRESH
     *  transaction on a concurrency retry — a plain internal call would
     *  reuse the already-rolled-back transaction. Same idiom as
     *  {@code ChartOfAccountsService}. */
    @Autowired
    @Lazy
    private MenuEntitlementService self;

    /** Read every {@code USE_YN='Y'} grant for the user. Order is
     *  insertion order so the round-trip after a Save is stable. */
    @Transactional(readOnly = true)
    public MenuEntitlementResponse load(String userId) {
        String bizKey = TenantContext.currentBizKey();
        ensureTenantUser(bizKey, userId);

        List<UserMenuAuthInfm> rows = menuAuthRepo.findAllByBizKeyAndUserId(bizKey, userId);
        List<String> grants = new ArrayList<>(rows.size());
        for (UserMenuAuthInfm row : rows) {
            if (FLAG_Y.equals(row.getUseYn())) {
                grants.add(row.getMenuAuthCd());
            }
        }
        return new MenuEntitlementResponse(userId, grants);
    }

    /**
     * Replace every grant for the (tenant, user) pair with the request's
     * slug list (full-set replace — therefore <b>idempotent</b>).
     *
     * <p><b>Concurrency.</b> The replace is a delete-then-insert. Two
     * concurrent PUTs for the same (tenant, user) can interleave so both
     * delete (seeing no rows, e.g. a first-time grant) and then both
     * insert the same {@code (BIZ_KEY, USER_ID, MENU_AUTH_CD)} PKs — the
     * loser hits {@code user_menu_auth_infm_pkey} and surfaces
     * {@code WBE_DB}. Because the operation is idempotent, this wrapper
     * catches that race and retries once <b>in a fresh transaction</b>
     * (via {@code self}); the retry now sees the winner's committed rows,
     * deletes them, and re-inserts cleanly. Last-writer-wins, which is
     * the correct semantic for a full-set replace. Not {@code @Transactional}
     * itself so the failed inner transaction is fully rolled back before
     * the retry opens a new one.
     */
    public MenuEntitlementResponse save(String userId, MenuEntitlementUpdateRequest req) {
        // Defense-in-depth admin guard (SEC-1) — the controller is already
        // @PreAuthorize("hasRole('ADMIN')") gated; this blocks a non-admin
        // caller from granting/revoking permissions even if reached outside
        // the annotated controller. → HTTP 403 / WBE1003.
        if (!TenantContext.currentIsAdmin()) {
            throw new AccessDeniedException("WBE1003");
        }
        try {
            return self.saveInTransaction(userId, req);
        } catch (DataIntegrityViolationException race) {
            log.warn("menu-entitlement save race for userId={} — retrying once in a fresh transaction",
                    userId);
            return self.saveInTransaction(userId, req);
        }
    }

    /** Delete-then-insert in a single {@code @Transactional} so a failed
     *  insert rolls the delete back. Invoked through {@code self} so the
     *  proxy starts a real transaction (and a brand-new one on retry). */
    @Transactional
    public MenuEntitlementResponse saveInTransaction(String userId, MenuEntitlementUpdateRequest req) {
        String bizKey  = TenantContext.currentBizKey();
        String actorId = TenantContext.currentUserId();
        ensureTenantUser(bizKey, userId);

        // Dedup the request — frontend should send unique slugs but
        // defend against accidental duplicates so the PK constraint
        // doesn't fire mid-insert and leave a partial dataset.
        Set<String> dedup = new LinkedHashSet<>(req.menuAuthCds());
        menuAuthRepo.deleteAllByBizKeyAndUserId(bizKey, userId);
        menuAuthRepo.flush();           // force the DELETE before the inserts

        OffsetDateTime now = OffsetDateTime.now();
        List<UserMenuAuthInfm> inserts = new ArrayList<>(dedup.size());
        for (String slug : dedup) {
            UserMenuAuthInfm row = new UserMenuAuthInfm();
            row.setBizKey(bizKey);
            row.setUserId(userId);
            row.setMenuAuthCd(slug);
            row.setUseYn(FLAG_Y);
            // JEX user_menu_auth_infm has no REG_* columns — mod-only audit.
            row.setModId(actorId);
            row.setModDtm(now);
            inserts.add(row);
        }
        menuAuthRepo.saveAll(inserts);

        return new MenuEntitlementResponse(userId, new ArrayList<>(dedup));
    }

    // ---------------------------------------------------------------- internals

    private void ensureTenantUser(String bizKey, String userId) {
        userBusiConnRepo
                .findByUserIdAndBizKeyAndDelYn(userId, bizKey, DEL_N)
                .orElseThrow(() -> new BusinessException("WBE_USERS_NOT_FOUND"));
    }
}
