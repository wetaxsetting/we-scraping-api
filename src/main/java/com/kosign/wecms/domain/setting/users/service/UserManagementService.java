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
import com.kosign.wecms.domain.setting.bankaccount.repository.AcctAuthInfoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

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

    /** Legacy yyyyMMddHHmmss audit-stamp format — ACCT_AUTH_INFO keeps
     *  VARCHAR(14) MOD_DTM strings (see {@code AcctAuthInfo} entity). */
    private static final DateTimeFormatter DTM14 =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final UserInfmRepository         userInfmRepo;
    private final UserBusiConnInfmRepository userBusiConnRepo;
    private final BusiAdmnInfmRepository     busiAdmnRepo;
    private final AcctAuthInfoRepository     acctAuthInfoRepo;
    private final PasswordEncoder            passwordEncoder;

    // ---------------------------------------------------------------- list

    /**
     * Every active user linked to the current tenant, newest first.
     * Returns the rows + the active-user count for the panel subtitle.
     * There is no plan cap — neither legacy JEX nor the refactor
     * enforces one (see {@link #create(UserCreateRequest)}), so the
     * response carries no {@code planLimit} field.
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
        requireAdmin();
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
        requireAdmin();
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
     * delete the {@code BUSI_ADMN_INFM} row if present + soft-revoke the
     * user's {@code ACCT_AUTH_INFO} rows in the tenant. The
     * {@code USER_INFM} master is left intact so the user record
     * survives across tenants (multi-link fallback).
     *
     * <p>The {@code ACCT_AUTH_INFO} revoke ({@code USE_YN='N'}) closes a
     * residual-permission hole: those rows are consumed by
     * banking/payment authorization paths, so an unlinked user must not
     * keep effective account access. Mirrors legacy
     * {@code comm_0190_003_d001}. All writes share this method's
     * {@code @Transactional} scope.
     *
     * <p>Guards: cannot delete yourself; cannot delete the only
     * remaining admin.
     */
    @Transactional
    public void delete(String userId) {
        requireAdmin();
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

        // Revoke account-level permissions so a removed member cannot
        // retain effective access via ACCT_AUTH_INFO (soft-revoke, audit
        // history preserved).
        acctAuthInfoRepo.revokeAllByBizKeyAndUserId(
                bizKey, userId, actorId, LocalDateTime.now(ZoneOffset.UTC).format(DTM14));
    }

    // ----------------------------------------------------------- internals

    /**
     * Defense-in-depth admin guard (SEC-1). The controller is already
     * gated with {@code @PreAuthorize("hasRole('ADMIN')")}; this second
     * check ensures a non-admin caller can never mutate users / roles
     * even if the method is reached outside the annotated controller.
     * Rejects with {@link AccessDeniedException} → HTTP 403 /
     * {@code WBE1003} via the global handler.
     */
    private static void requireAdmin() {
        if (!TenantContext.currentIsAdmin()) {
            throw new AccessDeniedException("WBE1003");
        }
    }

    private Set<String> adminIdSet(String bizKey) {
        // Tenant-scoped query — never findAll() across every tenant's
        // admin rows then filter in Java.
        Set<String> out = new HashSet<>();
        for (BusiAdmnInfm row : busiAdmnRepo.findAllByBizKey(bizKey)) {
            out.add(row.getUserId());
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
                sanitizePhoneNumber(r.getClphNo()),
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
                sanitizePhoneNumber(user.getClphNo()),
                user.getPosiNm(),
                user.getDeptNm(),
                isAdmin,
                isSelf,
                UserImageService.publicUrl(user.getUserId(), link.getUserImg()));
    }

    // Plausible phone glyphs: digits, plus, space, dash, parens, dot.
    // Legacy JEX stores CLPH_NO AES-encrypted via DB-side encrypt() /
    // decrypt() (see IDO.USER_INFM_R001.xml:27, IDO.USER_INFM_C001.xml:43).
    // Refactor doesn't install those legacy PL/pgSQL helpers, so rows
    // imported from DEV (V5 / V72 / V73) ship base64 ciphertext like
    // "5s3hGJCIQMt0op+pZGU42Q==". Anything outside the phone charset is
    // dropped here so the panel renders blank instead of leaking the
    // encrypted bytes. Refactor-created users save plain text and pass
    // through untouched.
    private static final Pattern PHONE_GLYPHS = Pattern.compile("^[+0-9 ()\\-.]+$");

    private static String sanitizePhoneNumber(String raw) {
        if (raw == null) return "";
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) return "";
        return PHONE_GLYPHS.matcher(trimmed).matches() ? trimmed : "";
    }
}
