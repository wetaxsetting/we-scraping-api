package com.kosign.wecms.domain.auth.service;

import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.security.JwtTokenProvider;
import com.kosign.wecms.common.security.TokenRevocationService;
import com.kosign.wecms.common.tenant.TenantContext;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import com.kosign.wecms.domain.auth.dto.CompanyOption;
import com.kosign.wecms.domain.auth.dto.LoginRequest;
import com.kosign.wecms.domain.auth.dto.LoginResponse;
import com.kosign.wecms.domain.auth.dto.MeResponse;
import com.kosign.wecms.domain.auth.entity.BusiAdmnInfm;
import com.kosign.wecms.domain.auth.entity.BusiInfm;
import com.kosign.wecms.domain.auth.entity.UserBusiConnInfm;
import com.kosign.wecms.domain.auth.entity.UserInfm;
import com.kosign.wecms.domain.auth.entity.UserLoginHis;
import com.kosign.wecms.domain.auth.repository.BusiAdmnInfmRepository;
import com.kosign.wecms.domain.auth.repository.BusiInfmRepository;
import com.kosign.wecms.domain.auth.repository.UserBusiConnInfmRepository;
import com.kosign.wecms.domain.auth.repository.UserInfmRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Port of the legacy {@code SzonHelper.logn_2001_001_r001} flow, with the
 * single-tenant pin retired in favour of per-user tenant resolution.
 * <p>
 * Preserved from legacy: flag-gate order (DEL → ACTV → LOCK → password),
 * WBE codes, USER_LOGIN_HIS audit row on every terminal attempt,
 * LST_LGN_DATE update on the resolved link.
 * <p>
 * <b>Operational model: one user = one company account.</b> Current WeCMS
 * operation treats a {@code USER_ID} as belonging to exactly one active
 * {@code BIZ_KEY}. The expected production login path is therefore "1
 * link → auto-pick → JWT issued → home" with **no chooser screen**.
 * Multi-link users are an exceptional / test-demo scenario only — the
 * chooser branch below is kept as a safe generic fallback, NOT as a
 * documented business pattern (see {@code wecms-api/CLAUDE.md}
 * §"Operational model: one user = one company account"). If / when real
 * multi-company users become a documented requirement, this Javadoc and
 * the matching CLAUDE.md bullets should be replaced with explicit rules.
 * <p>
 * <b>Tenant resolution.</b> The configured-tenant pin is gone. Instead:
 * <ol>
 *   <li>Look up every active USER_BUSI_CONN_INFM link for the user.</li>
 *   <li>0 links → WBE1009 (user is not provisioned at any company).</li>
 *   <li><b>1 link → auto-pick that BIZ_KEY (the normal path).</b> If the
 *       request also carried a {@code biz_key}, it must match.</li>
 *   <li>≥ 2 links → <b>exceptional path</b>. If request carried no
 *       {@code biz_key}, return the chooser response (no JWT issued, no
 *       LST_LGN_DATE update, no USER_LOGIN_HIS SUCCESS row). If it did,
 *       find the matching link — unknown / non-existent links fail with
 *       WBE1009.</li>
 * </ol>
 * <p>
 * <b>Authority.</b> {@code authGrCd} is {@code "ADMIN"} when
 * BUSI_ADMN_INFM has a row for the resolved (BIZ_KEY, USER_ID) pair,
 * {@code "USER"} otherwise. The value is stamped into the JWT claim so
 * {@link com.kosign.wecms.common.security.JwtAuthenticationFilter} can
 * surface it into {@link TenantContext} on every authenticated request
 * without re-querying.
 * <p>
 * <b>Product type.</b> The verbatim {@code BUSI_INFM.PRDT_TYPE} letter
 * for the resolved company is stamped into the JWT claim and into both
 * the authenticated response and (per-option) the chooser response.
 * The refactor explicitly supports {@code F}, {@code M} and {@code H};
 * any other legacy letter on a tenant row is round-tripped verbatim
 * but not modelled as an enum — see
 * {@link com.kosign.wecms.common.enums.PrdtType}.
 * <p>
 * Deferred: 5-failure auto-lock counter, password-change-history reset,
 * NTNL_CD/CHNL_TYPE/BUSI_STTS post-auth gates.
 * <p>
 * <b>Menu authorization.</b> {@code /me} now returns the caller's
 * allowed-menu set (admins bypass; normal users get their
 * {@code USE_YN='Y'} grants from {@code USER_MENU_AUTH_INFM} via
 * {@link MenuAuthorizationService}) so the frontend can filter the top
 * nav, sidebar, and routes. Sourced fresh per {@code /me}, never from a
 * JWT claim.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String CODE_BAD_CREDENTIALS = "WBE1009";
    private static final String CODE_INACTIVE        = "WBE1010";
    private static final String CODE_LOCKED          = "WBE1011";
    private static final String CODE_DELETED         = "WBE1128";
    private static final String CODE_UNAUTHENTICATED = "WBE1401";

    private final UserInfmRepository userRepo;
    private final UserBusiConnInfmRepository userBusiRepo;
    private final BusiInfmRepository busiRepo;
    private final BusiAdmnInfmRepository busiAdmnRepo;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final LoginHistoryService history;
    private final AdminLoginHistoryService adminHistory;
    private final MenuAuthorizationService menuAuthService;
    private final TokenRevocationService tokenRevocation;
    private final LoginLockoutService loginLockout;

    @Transactional
    public LoginResponse login(LoginRequest req, String ipAddr, String langCd) {
        final String userId          = req.userId();
        final String requestedBizKey = req.bizKey();   // null on first call

        UserInfm user = userRepo.findById(userId).orElse(null);
        if (user == null) {
            history.record(userId, ipAddr, UserLoginHis.RSLT_FAIL, "USER_NOT_FOUND");
            throw new BusinessException(CODE_BAD_CREDENTIALS);
        }

        if (user.isDeleted()) {
            history.record(userId, ipAddr, UserLoginHis.RSLT_FAIL, "DEL_YN=Y");
            throw new BusinessException(CODE_DELETED);
        }

        if (!user.isActive()) {
            history.record(userId, ipAddr, UserLoginHis.RSLT_FAIL, "ACTV_YN=N");
            throw new BusinessException(CODE_INACTIVE);
        }

        if (user.isLocked()) {
            history.record(userId, ipAddr, UserLoginHis.RSLT_FAIL, "LOCK_YN=Y");
            throw new BusinessException(CODE_LOCKED);
        }

        if (!passwordEncoder.matches(req.userPw(), user.getUserPw())) {
            history.record(userId, ipAddr, UserLoginHis.RSLT_FAIL, "PW_MISMATCH");
            // F-10: brute-force auto-lock. After the FAIL row is committed (above,
            // REQUIRES_NEW), lock the account when the last 5 attempts are all
            // failures. The lock write is its own REQUIRES_NEW tx so it survives
            // the rollback caused by the throw below. The error stays the generic
            // WBE1009 (the password WAS wrong); the lock takes effect on the next
            // attempt → WBE1011, even with the correct password.
            loginLockout.lockIfThresholdReached(userId);
            throw new BusinessException(CODE_BAD_CREDENTIALS);
        }

        // Coexistence rule (2026-06-10): login is VERIFY-ONLY. USER_PW is
        // never re-encoded or written here. The legacy public.hash()
        // SHA-256 value stays intact so the live JEX system can keep
        // authenticating the same credential. (The former transparent
        // SHA-256 → BCrypt upgrade was removed — it mutated live data and
        // broke JEX login.)

        // Multi-company resolution: every active link the user has.
        List<UserBusiConnInfm> links = userBusiRepo.findAllByUserIdAndDelYn(userId, "N");
        if (links.isEmpty()) {
            log.warn("Login blocked: user {} has zero active USER_BUSI_CONN_INFM links", userId);
            history.record(userId, ipAddr, UserLoginHis.RSLT_FAIL, "NO_TENANT_LINKS");
            throw new BusinessException(CODE_BAD_CREDENTIALS);
        }

        UserBusiConnInfm selectedLink;
        if (links.size() == 1) {
            // Single-company user — auto-pick. If the caller supplied a
            // biz_key, it must match the user's only link.
            selectedLink = links.get(0);
            if (requestedBizKey != null && !requestedBizKey.equals(selectedLink.getBizKey())) {
                log.warn("Login blocked: user {} requested biz_key {} but only link is {}",
                        userId, requestedBizKey, selectedLink.getBizKey());
                history.record(userId, ipAddr, UserLoginHis.RSLT_FAIL, "TENANT_OUT_OF_SCOPE");
                throw new BusinessException(CODE_BAD_CREDENTIALS);
            }
        } else if (requestedBizKey == null) {
            // Multi-company user, first call: return the chooser. No
            // JWT, no LST_LGN_DATE update, no SUCCESS audit row — the
            // user hasn't authenticated to any specific company yet.
            return buildChooserResponse(user, links);
        } else {
            // Multi-company user, finalising with a chosen biz_key.
            selectedLink = links.stream()
                    .filter(l -> requestedBizKey.equals(l.getBizKey()))
                    .findFirst()
                    .orElse(null);
            if (selectedLink == null) {
                log.warn("Login blocked: user {} requested biz_key {} but has no matching active link",
                        userId, requestedBizKey);
                history.record(userId, ipAddr, UserLoginHis.RSLT_FAIL, "TENANT_OUT_OF_SCOPE");
                throw new BusinessException(CODE_BAD_CREDENTIALS);
            }
        }

        // Update the last-login marker on the resolved link only.
        selectedLink.setLstLgnDate(OffsetDateTime.now());
        selectedLink.setModId(userId);
        userBusiRepo.save(selectedLink);

        history.record(userId, ipAddr, UserLoginHis.RSLT_SUCCESS, null);

        // Fetch the company row once for the display name, product-type
        // stamp on the JWT, and the user-facing tax ID — single round-trip.
        BusiInfm busi = busiRepo.findById(selectedLink.getBizKey()).orElse(null);
        String cmpnNm   = busi != null && busi.getCmpnNm()   != null ? busi.getCmpnNm()   : "";
        // Verbatim PRDT_TYPE letter; null when the company row has no
        // value. Never substitute a default — silent "F" would mask
        // the seed bug V74 just fixed and would mis-classify tenants
        // on any of the 11 deferred legacy letters.
        String prdtType = busi != null ? busi.getPrdtType() : null;
        // Verbatim TAX_ID — null when unset. Surfaced on the wire so
        // the Lock Screen popup can display the company tax /
        // business-registration number in brackets (Figma 83:12764).
        String taxId    = busi != null ? busi.getTaxId() : null;

        // Authority for the resolved company. ADMIN if and only if the
        // user has a BUSI_ADMN_INFM row for this BIZ_KEY. Fetch the row (not a
        // boolean probe) so its USER_NM can feed the admin login-history audit.
        BusiAdmnInfm busiAdmn = busiAdmnRepo
                .findByBizKeyAndUserId(selectedLink.getBizKey(), userId)
                .orElse(null);
        String authGrCd = busiAdmn != null
                ? TenantContext.AUTH_GR_ADMIN
                : TenantContext.AUTH_GR_USER;

        // Gap #9 — admin login-history audit (legacy BUSI_ADMN_LOGIN_HIS).
        // Legacy writes this row on a successful WeCMS-channel login when the
        // user is a company admin (logn_2001_001_r001_act.jsp:270-284,
        // `busiAdmnInfm != null && ChnlType.TYPE_C`). The refactor IS the WeCMS
        // customer channel, so the channel test is implicit; the admin test is
        // the BUSI_ADMN_INFM presence just resolved. Written in THIS transaction
        // (not REQUIRES_NEW) so it commits with the login, mirroring legacy.
        // REG_NM comes from BUSI_ADMN_INFM.USER_NM (legacy busiAdmnInfm.getUserNm()).
        if (busiAdmn != null) {
            adminHistory.record(selectedLink.getBizKey(), userId, busiAdmn.getUserNm(), ipAddr);
        }

        String token = tokenProvider.issue(
                user.getUserId(), user.getUserNm(),
                selectedLink.getBizKey(), langCd, authGrCd, prdtType);

        return LoginResponse.authenticated(
                token,
                "Bearer",
                tokenProvider.expiresInSeconds(),
                user.getUserId(),
                user.getUserNm(),
                selectedLink.getBizKey(),
                langCd,
                cmpnNm,
                authGrCd,
                prdtType,
                taxId
        );
    }

    /**
     * Current-session snapshot for the authenticated caller. The JWT filter
     * has already placed the caller's {@code userId}, {@code bizKey} and
     * {@code authGrCd} into {@link TenantContext}; this method reads them,
     * verifies the user is still linked to that tenant and has not been
     * deactivated/locked/deleted in the meantime, and returns the fields
     * needed to rehydrate the frontend session.
     *
     * <p>Missing context, or a user whose flags have flipped since the token
     * was issued, produces {@code WBE1401} — the frontend then clears local
     * state and redirects to {@code /login}.
     */
    @Transactional(readOnly = true)
    public MeResponse me() {
        TenantContext.Principal principal = TenantContext.current();
        if (principal == null || principal.userId() == null || principal.bizKey() == null) {
            throw new BusinessException(CODE_UNAUTHENTICATED);
        }
        final String userId = Objects.requireNonNull(principal.userId());
        final String bizKey = Objects.requireNonNull(principal.bizKey());

        UserInfm user = userRepo.findById(userId).orElse(null);
        if (user == null || user.isDeleted() || !user.isActive() || user.isLocked()) {
            log.warn("/auth/me rejected: user {} no longer eligible", userId);
            throw new BusinessException(CODE_UNAUTHENTICATED);
        }

        UserBusiConnInfm link = userBusiRepo
                .findByUserIdAndBizKeyAndDelYn(userId, bizKey, "N")
                .orElse(null);
        if (link == null) {
            log.warn("/auth/me rejected: user {} not linked to tenant {}", userId, bizKey);
            throw new BusinessException(CODE_UNAUTHENTICATED);
        }

        // Pull the company row once — cmpnNm for display, taxId for the
        // Lock Screen popup. Re-queried per /me call so an ops-side
        // TAX_ID correction surfaces on the next session rehydrate
        // without forcing a fresh login.
        BusiInfm busi = busiRepo.findById(bizKey).orElse(null);
        String cmpnNm = busi != null && busi.getCmpnNm() != null ? busi.getCmpnNm() : "";
        String taxId  = busi != null ? busi.getTaxId() : null;

        LangCd langCd = principal.langCd() != null ? principal.langCd() : LangCd.EN;

        // Re-resolve authority from the JWT claim. The filter has
        // already normalised it to ADMIN or USER, so trust the in-
        // memory value here — re-querying BUSI_ADMN_INFM on every /me
        // call is a needless DB hop and the JWT is signed.
        String authGrCd = principal.authGrCd() != null
                ? principal.authGrCd()
                : TenantContext.AUTH_GR_USER;

        // prdtType is signed into the JWT and surfaced via
        // TenantContext. Trust the claim — do not re-query BUSI_INFM
        // for it. Null when the issuing tenant row had no PRDT_TYPE.
        String prdtType = principal.prdtType();

        // Menu Authorization. Admins bypass filtering (allMenus=true);
        // normal users get only their USE_YN='Y' grants from
        // USER_MENU_AUTH_INFM. Resolved fresh per /me (no JWT menu claim)
        // so entitlement edits apply on the next reload without re-login.
        boolean allMenus = TenantContext.AUTH_GR_ADMIN.equals(authGrCd);
        List<String> allowedMenuCds = allMenus
                ? List.of()
                : menuAuthService.grantedMenuCds(bizKey, userId);

        return new MeResponse(
                user.getUserId(),
                user.getUserNm(),
                bizKey,
                langCd.name(),
                cmpnNm,
                authGrCd,
                prdtType,
                taxId,
                allMenus,
                allowedMenuCds
        );
    }

    /**
     * Server-side logout. Reproduces the two side effects of legacy JEX
     * {@code logn_0002_001_r001_act.jsp} (lines 47-56):
     *
     * <ol>
     *   <li><b>Audit row</b> — write a {@code USER_LOGIN_HIS} row with
     *       {@code RSLT_CD='LOGOUT'} for the logged-in user (legacy
     *       {@code UserLoginHisDAO.create}), in its own {@code REQUIRES_NEW} tx
     *       just like the SUCCESS/FAIL rows on login.</li>
     *   <li><b>Invalidate the credential (F-03)</b> — revoke the presented
     *       access token by its {@code jti} so it is rejected on every
     *       subsequent request (see
     *       {@link com.kosign.wecms.common.security.JwtAuthenticationFilter}).
     *       This is the JWT equivalent of legacy {@code session.invalidate()} —
     *       and closes the hole legacy left, since its stateless JWT fallback
     *       was unrevocable.</li>
     * </ol>
     *
     * <p>The token has already been validated by the filter on the way in; we
     * re-parse only to read the {@code sub} (for the audit row) + {@code jti} /
     * {@code exp} (for revocation). Best-effort: an already-invalid/expired
     * token has no logged-in user to audit and nothing to revoke, so it is
     * ignored.
     *
     * <p>Revocation is in-process only (see {@link TokenRevocationService} for
     * the documented restart / multi-instance limitations).
     */
    public void logout(String authorizationHeader, String ipAddr) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            return;
        }
        String token = authorizationHeader.substring("Bearer ".length());
        try {
            Claims claims = tokenProvider.parse(token).getPayload();
            // Legacy parity (logn_0002_001_r001_act.jsp:47-53): audit the logout
            // before invalidating the credential. userId is the token subject.
            String userId = claims.getSubject();
            if (userId != null) {
                history.record(userId, ipAddr, UserLoginHis.RSLT_LOGOUT, null);
            }
            // F-03: invalidate the credential server-side (≈ legacy session.invalidate()).
            if (claims.getId() != null && claims.getExpiration() != null) {
                tokenRevocation.revokeToken(claims.getId(), claims.getExpiration().toInstant());
            }
        } catch (JwtException ignored) {
            // Token already invalid / expired — no user to audit, nothing to revoke.
        }
    }

    /**
     * Build the chooser shape returned on the first call of a multi-link
     * user's login. One {@link CompanyOption} per active link, with the
     * company display name from BUSI_INFM and per-link authority from
     * BUSI_ADMN_INFM. Sorted by company name for predictable rendering.
     */
    private LoginResponse buildChooserResponse(UserInfm user, List<UserBusiConnInfm> links) {
        List<String> bizKeys = links.stream()
                .map(UserBusiConnInfm::getBizKey)
                .toList();

        // Single fetch — pulls CMPN_NM + PRDT_TYPE per company row in
        // one query; both feed into the CompanyOption rows below.
        Map<String, BusiInfm> busiByBizKey = busiRepo.findAllById(bizKeys).stream()
                .collect(Collectors.toMap(BusiInfm::getBizKey, b -> b));

        Set<String> adminBizKeys = busiAdmnRepo.findAllByUserId(user.getUserId()).stream()
                .map(BusiAdmnInfm::getBizKey)
                .collect(Collectors.toSet());

        List<CompanyOption> options = links.stream()
                .map(l -> {
                    BusiInfm b = busiByBizKey.get(l.getBizKey());
                    String cmpnNm = b != null && b.getCmpnNm() != null ? b.getCmpnNm() : "";
                    // Verbatim PRDT_TYPE per option — null when the row
                    // has no value. Frontend may display the product
                    // label but must not gate on this value (no JWT
                    // exists yet for an unselected company).
                    String prdtType = b != null ? b.getPrdtType() : null;
                    String authGrCd = adminBizKeys.contains(l.getBizKey())
                            ? TenantContext.AUTH_GR_ADMIN
                            : TenantContext.AUTH_GR_USER;
                    return new CompanyOption(l.getBizKey(), cmpnNm, authGrCd, prdtType);
                })
                .sorted(Comparator.comparing(CompanyOption::cmpnNm,
                                             Comparator.nullsLast(String::compareTo))
                        .thenComparing((Function<CompanyOption, String>) CompanyOption::bizKey))
                .toList();

        log.info("Login chooser: user {} has {} active links, returning options for selection",
                user.getUserId(), options.size());

        return LoginResponse.chooser(user.getUserId(), user.getUserNm(), options);
    }
}
