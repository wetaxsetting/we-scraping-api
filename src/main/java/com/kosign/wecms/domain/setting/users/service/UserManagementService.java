package com.kosign.wecms.domain.setting.users.service;

import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.auth.entity.BusiAdmnInfm;
import com.kosign.wecms.domain.auth.entity.BusiAdmnInfmId;
import com.kosign.wecms.domain.auth.entity.UserBusiConnInfm;
import com.kosign.wecms.domain.auth.entity.UserInfm;
import com.kosign.wecms.domain.auth.repository.BusiAdmnInfmRepository;
import com.kosign.wecms.domain.auth.repository.UserBusiConnInfmRepository;
import com.kosign.wecms.domain.auth.repository.UserBusiConnInfmRepository.TenantUserManagementProjection;
import com.kosign.wecms.domain.auth.repository.UserInfmRepository;
import com.kosign.wecms.domain.setting.users.dto.CheckLoginIdResponse;
import com.kosign.wecms.domain.setting.users.dto.UserCreateRequest;
// UserImageService.publicUrl is a static helper — same package, no import needed.
import com.kosign.wecms.domain.setting.users.dto.UserListResponse;
import com.kosign.wecms.domain.setting.users.dto.UserRowResponse;
import com.kosign.wecms.domain.setting.users.dto.UserUpdateRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Settings &gt; Users — CRUD service backing the panel at
 * {@code /api/v1/settings/users}.
 *
 * <p>Tables touched (all already in the local schema — no Flyway in
 * this MR):
 * <ul>
 *   <li>{@code USER_INFM} — master row (PK = login id, global namespace).</li>
 *   <li>{@code USER_BUSI_CONN_INFM} — per-tenant link with {@code DEL_YN}
 *       soft-delete flag.</li>
 *   <li>{@code BUSI_ADMN_INFM} — admin authority for a (tenant, user)
 *       pair. Presence = admin, absence = regular user.</li>
 * </ul>
 *
 * <p>Operational notes:
 * <ul>
 *   <li><b>No plan cap.</b> Legacy JEX defines {@code WBW1151} but
 *       never throws it; the refactor preserves that behaviour. The
 *       panel renders the active count for visibility, not as a gate.
 *       See {@link #create(UserCreateRequest)} Javadoc.</li>
 *   <li>Delete is a <b>per-tenant unlink</b>: flip
 *       {@code USER_BUSI_CONN_INFM.DEL_YN='Y'} and remove the
 *       {@code BUSI_ADMN_INFM} row. {@code USER_INFM} is left intact so
 *       the user record survives across the multi-link fallback (one
 *       user could still belong to another tenant).</li>
 *   <li>Last-admin guard: demoting / deleting the only admin of a
 *       tenant is rejected with {@code WBE_USERS_CANNOT_DEMOTE_LAST_ADMIN}.</li>
 *   <li>Self-delete is blocked with {@code WBE_USERS_CANNOT_DELETE_SELF}.</li>
 * </ul>
 *
 * <p>Tenant scoping: every read / write constrains on
 * {@link TenantContext#currentBizKey()}. The request body / path
 * variable never carries {@code biz_key}.
 *
 * <p>Password writes go through {@link PasswordEncoder} (the
 * Spring-Security-bound {@code WecmsPasswordEncoder} — BCrypt strength
 * 10 on write, BCrypt + legacy SHA-256 base64 on read).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserManagementService {

    private static final String DEL_N  = "N";
    private static final String FLAG_Y = "Y";
    private static final String FLAG_N = "N";

    private final UserInfmRepository         userInfmRepo;
    private final UserBusiConnInfmRepository userBusiConnRepo;
    private final BusiAdmnInfmRepository     busiAdmnRepo;
    private final PasswordEncoder            passwordEncoder;

    // ---------------------------------------------------------------- list

    /**
     * Every active user linked to the current tenant, newest first.
     * Returns the count + the plan-cap so the frontend can paint the
     * "{used} / {max}" subtitle and disable the "Add a user"
     * affordance when {@code totalCount >= planLimit}.
     */
    @Transactional(readOnly = true)
    public UserListResponse list() {
        String bizKey   = TenantContext.currentBizKey();
        String selfId   = TenantContext.currentUserId();
        List<TenantUserManagementProjection> rows =
                userBusiConnRepo.findTenantUsersForManagement(bizKey);

        // Single round trip for the admin set so we don't N+1 the admin
        // check per row.
        Set<String> adminIds = adminIdSet(bizKey);

        List<UserRowResponse> out = new ArrayList<>(rows.size());
        for (TenantUserManagementProjection r : rows) {
            out.add(toRow(r, adminIds, selfId));
        }
        return new UserListResponse(out, out.size());
    }

    // -------------------------------------------------------------- detail

    /**
     * Single-row read. Used by the panel when the user clicks a row
     * (the list already carries everything, but a direct GET is
     * useful for deep-link / refresh scenarios).
     */
    @Transactional(readOnly = true)
    public UserRowResponse detail(String userId) {
        String bizKey = TenantContext.currentBizKey();
        UserBusiConnInfm link = userBusiConnRepo
                .findByUserIdAndBizKeyAndDelYn(userId, bizKey, DEL_N)
                .orElseThrow(() -> new BusinessException("WBE_USERS_NOT_FOUND"));

        UserInfm user = userInfmRepo.findById(userId)
                .orElseThrow(() -> new BusinessException("WBE_USERS_NOT_FOUND"));

        boolean isAdmin = busiAdmnRepo.existsByBizKeyAndUserId(bizKey, userId);
        String  selfId  = TenantContext.currentUserId();
        return toRow(user, link, isAdmin, Objects.equals(userId, selfId));
    }

    // ------------------------------------------------------------ check-id

    /**
     * {@code GET /api/v1/settings/users/check-id?login_id=…} — drives the
     * "Check availability" button. Global existence check on
     * {@code USER_INFM} (the PK is platform-wide, not per-tenant).
     */
    @Transactional(readOnly = true)
    public CheckLoginIdResponse checkLoginId(String loginId) {
        if (loginId == null || loginId.isBlank()) {
            throw new BusinessException("WBE_USERS_LOGIN_ID_REQUIRED");
        }
        return new CheckLoginIdResponse(!userInfmRepo.existsByUserId(loginId.trim()));
    }

    // -------------------------------------------------------------- create

    /**
     * Provision a new user inside the current tenant.
     *
     * <ol>
     *   <li>Reject if a {@code USER_INFM} row already exists for the
     *       supplied login id (PK collision; surfaces as
     *       {@code WBE_USERS_DUPLICATE_LOGIN_ID} — the check-id
     *       endpoint catches this earlier on the happy path).</li>
     *   <li>Insert {@code USER_INFM} with a BCrypt-hashed password
     *       and {@code ACTV_YN='Y' / LOCK_YN='N' / DEL_YN='N'}.</li>
     *   <li>Insert {@code USER_BUSI_CONN_INFM} with
     *       {@code DEL_YN='N'}.</li>
     *   <li>If {@code setAsAdmin}, insert {@code BUSI_ADMN_INFM}.</li>
     * </ol>
     *
     * <p>Wrapped in one {@code @Transactional} so the writes are
     * atomic at the JPA isolation level.
     *
     * <p>No plan cap. Legacy JEX defines a {@code WBW1151} "max 5
     * users" message in {@code WbMessageCode.java:186} but never
     * throws it anywhere — verified by Grep across the full JEX
     * source. Real tenants exceed 5 in DEV (tenant 000001 carries
     * 50 active users). The refactor preserves legacy behaviour:
     * Settings &gt; Users panel shows the count but does not gate
     * creation. A future tier-pricing MR would surface a real cap
     * via a dedicated table.
     */
    @Transactional
    public UserRowResponse create(UserCreateRequest req) {
        String bizKey  = TenantContext.currentBizKey();
        String actorId = TenantContext.currentUserId();
        String userId  = req.userId().trim();

        // Global uniqueness — USER_INFM.USER_ID is a platform-wide PK.
        if (userInfmRepo.existsByUserId(userId)) {
            throw new BusinessException("WBE_USERS_DUPLICATE_LOGIN_ID");
        }

        // 1. USER_INFM
        UserInfm user = new UserInfm();
        user.setUserId(userId);
        user.setUserNm(req.userNm().trim());
        user.setEml(req.eml().trim());
        user.setClphNo(req.clphNo().trim());
        user.setPosiNm(req.posiNm().trim());
        user.setDeptNm(req.deptNm().trim());
        user.setUserPw(passwordEncoder.encode(req.userPw()));
        user.setActvYn(FLAG_Y);
        user.setLockYn(FLAG_N);
        user.setDelYn(FLAG_N);
        user.setBizKey(bizKey);
        user.setRegId(actorId);
        user.setModId(actorId);
        userInfmRepo.save(user);

        // 2. USER_BUSI_CONN_INFM
        UserBusiConnInfm link = new UserBusiConnInfm();
        link.setUserId(userId);
        link.setBizKey(bizKey);
        link.setDelYn(FLAG_N);
        link.setRegId(actorId);
        link.setModId(actorId);
        userBusiConnRepo.save(link);

        // 3. BUSI_ADMN_INFM (optional)
        boolean isAdmin = req.setAsAdmin();
        if (isAdmin) {
            insertAdminRow(bizKey, userId, user.getUserNm(), actorId);
        }

        return toRow(user, link, isAdmin, Objects.equals(userId, actorId));
    }

    // -------------------------------------------------------------- update

    /**
     * Update an existing user's profile + (optionally) password +
     * admin flag.
     *
     * <p>Last-admin guard fires when a {@code setAsAdmin=false} would
     * remove the only remaining admin of the tenant — surfaces as
     * {@code WBE_USERS_CANNOT_DEMOTE_LAST_ADMIN}.
     */
    @Transactional
    public UserRowResponse update(String userId, UserUpdateRequest req) {
        String bizKey  = TenantContext.currentBizKey();
        String actorId = TenantContext.currentUserId();

        UserBusiConnInfm link = userBusiConnRepo
                .findByUserIdAndBizKeyAndDelYn(userId, bizKey, DEL_N)
                .orElseThrow(() -> new BusinessException("WBE_USERS_NOT_FOUND"));

        UserInfm user = userInfmRepo.findById(userId)
                .orElseThrow(() -> new BusinessException("WBE_USERS_NOT_FOUND"));

        // Profile fields.
        user.setUserNm(req.userNm().trim());
        user.setEml(req.eml().trim());
        user.setClphNo(req.clphNo().trim());
        user.setPosiNm(req.posiNm().trim());
        user.setDeptNm(req.deptNm().trim());
        user.setModId(actorId);

        if (req.userPw() != null && !req.userPw().isBlank()) {
            user.setUserPw(passwordEncoder.encode(req.userPw()));
        }

        link.setModId(actorId);

        // Admin toggle.
        boolean wasAdmin = busiAdmnRepo.existsByBizKeyAndUserId(bizKey, userId);
        boolean wantAdmin = req.setAsAdmin();
        if (wasAdmin && !wantAdmin) {
            guardLastAdmin(bizKey);
            busiAdmnRepo.deleteById(new BusiAdmnInfmId(bizKey, userId));
        } else if (!wasAdmin && wantAdmin) {
            insertAdminRow(bizKey, userId, user.getUserNm(), actorId);
        } else if (wasAdmin) {
            // Still admin — keep USER_NM in sync if it changed.
            busiAdmnRepo.findById(new BusiAdmnInfmId(bizKey, userId))
                    .ifPresent(row -> {
                        row.setUserNm(user.getUserNm());
                        row.setModId(actorId);
                    });
        }

        return toRow(user, link, wantAdmin, Objects.equals(userId, actorId));
    }

    // -------------------------------------------------------------- delete

    /**
     * Per-tenant unlink. {@code USER_BUSI_CONN_INFM.DEL_YN='Y'} +
     * delete the {@code BUSI_ADMN_INFM} row if present. The
     * {@code USER_INFM} master is left intact so the user record
     * survives across tenants (multi-link fallback).
     *
     * <p>Guards: cannot delete yourself; cannot delete the only
     * remaining admin.
     */
    @Transactional
    public void delete(String userId) {
        String bizKey  = TenantContext.currentBizKey();
        String actorId = TenantContext.currentUserId();

        if (Objects.equals(userId, actorId)) {
            throw new BusinessException("WBE_USERS_CANNOT_DELETE_SELF");
        }

        UserBusiConnInfm link = userBusiConnRepo
                .findByUserIdAndBizKeyAndDelYn(userId, bizKey, DEL_N)
                .orElseThrow(() -> new BusinessException("WBE_USERS_NOT_FOUND"));

        boolean isAdmin = busiAdmnRepo.existsByBizKeyAndUserId(bizKey, userId);
        if (isAdmin) {
            guardLastAdmin(bizKey);
            busiAdmnRepo.deleteById(new BusiAdmnInfmId(bizKey, userId));
        }

        link.setDelYn(FLAG_Y);
        link.setModId(actorId);
    }

    // ----------------------------------------------------------- internals

    private Set<String> adminIdSet(String bizKey) {
        Set<String> out = new HashSet<>();
        for (BusiAdmnInfm row : busiAdmnRepo.findAll()) {
            if (bizKey.equals(row.getBizKey())) out.add(row.getUserId());
        }
        return out;
    }

    private void guardLastAdmin(String bizKey) {
        if (busiAdmnRepo.countByBizKey(bizKey) <= 1) {
            throw new BusinessException("WBE_USERS_CANNOT_DEMOTE_LAST_ADMIN");
        }
    }

    private void insertAdminRow(String bizKey, String userId, String userNm, String actorId) {
        BusiAdmnInfm row = new BusiAdmnInfm();
        row.setBizKey(bizKey);
        row.setUserId(userId);
        row.setUserNm(userNm);
        row.setRegId(actorId);
        row.setModId(actorId);
        busiAdmnRepo.save(row);
    }

    private UserRowResponse toRow(TenantUserManagementProjection r,
                                  Set<String> adminIds,
                                  String selfId) {
        return new UserRowResponse(
                r.getUserId(),
                r.getUserNm(),
                r.getEml(),
                r.getClphNo(),
                r.getPosiNm(),
                r.getDeptNm(),
                adminIds.contains(r.getUserId()),
                Objects.equals(r.getUserId(), selfId),
                UserImageService.publicUrl(r.getUserId(), r.getUserImg()));
    }

    private UserRowResponse toRow(UserInfm user,
                                  UserBusiConnInfm link,
                                  boolean isAdmin,
                                  boolean isSelf) {
        return new UserRowResponse(
                user.getUserId(),
                user.getUserNm(),
                user.getEml(),
                user.getClphNo(),
                user.getPosiNm(),
                user.getDeptNm(),
                isAdmin,
                isSelf,
                UserImageService.publicUrl(user.getUserId(), link.getUserImg()));
    }
}
