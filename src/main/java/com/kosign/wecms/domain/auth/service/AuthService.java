package com.kosign.wecms.domain.auth.service;

import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.security.JwtTokenProvider;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.config.TenantProperties;
import com.kosign.wecms.domain.auth.dto.LoginRequest;
import com.kosign.wecms.domain.auth.dto.LoginResponse;
import com.kosign.wecms.domain.auth.dto.MeResponse;
import com.kosign.wecms.domain.auth.entity.BusiInfm;
import com.kosign.wecms.domain.auth.entity.UserBusiConnInfm;
import com.kosign.wecms.domain.auth.entity.UserInfm;
import com.kosign.wecms.domain.auth.entity.UserLoginHis;
import com.kosign.wecms.domain.auth.repository.BusiInfmRepository;
import com.kosign.wecms.domain.auth.repository.UserBusiConnInfmRepository;
import com.kosign.wecms.domain.auth.repository.UserInfmRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Port of the legacy {@code SzonHelper.logn_2001_001_r001} flow.
 * <p>
 * Preserved: flag-gate order (DEL → ACTV → LOCK → password), WBE codes,
 * USER_LOGIN_HIS audit row on every attempt, LST_LGN_DATE update on success.
 * <p>
 * Single-tenant: {@link TenantProperties#bizKey} pins the active BIZ_KEY to
 * a literal value (no wildcards). Users not linked to that exact tenant →
 * WBE1009. LANG_CD is derived from the request's Accept-Language header at
 * the controller and passed in — legacy USER_INFM does not carry LANG_CD.
 * <p>
 * Deferred: 5-failure auto-lock counter, password-change-history reset,
 * NTNL_CD/CHNL_TYPE/BUSI_STTS post-auth gates, post-login menu resolution.
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
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final LoginHistoryService history;
    private final TenantProperties tenant;

    @Transactional
    public LoginResponse login(LoginRequest req, String ipAddr, String langCd) {
        final String userId = req.userId();
        final String bizKey = tenant.bizKey();

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
            throw new BusinessException(CODE_BAD_CREDENTIALS);
        }

        // If the stored hash is not already BCrypt (e.g. a legacy SHA-256 base64
        // value copied from wecmsvndevdb), re-encode with BCrypt on this
        // successful login so the legacy format is phased out progressively.
        if (passwordEncoder.upgradeEncoding(user.getUserPw())) {
            user.setUserPw(passwordEncoder.encode(req.userPw()));
            user.setModId(userId);
            userRepo.save(user);
            log.info("Upgraded stored password for user {} from legacy hash to BCrypt", userId);
        }

        // Strict single-tenant filter: exact BIZ_KEY only, no wildcards.
        UserBusiConnInfm link = userBusiRepo
                .findByUserIdAndBizKeyAndDelYn(userId, bizKey, "N")
                .orElse(null);
        if (link == null) {
            log.warn("Login blocked: user {} is not linked to tenant {}", userId, bizKey);
            history.record(userId, ipAddr, UserLoginHis.RSLT_FAIL, "TENANT_OUT_OF_SCOPE");
            throw new BusinessException(CODE_BAD_CREDENTIALS);
        }
        link.setLstLgnDate(OffsetDateTime.now());
        link.setModId(userId);
        userBusiRepo.save(link);

        history.record(userId, ipAddr, UserLoginHis.RSLT_SUCCESS, null);

        // Resolve the tenant's display name (BUSI_INFM.CMPN_NM) for the
        // sidebar company header. Tenant row is guaranteed to exist for a
        // valid UserBusiConnInfm link, but fall back to null-safe empty
        // string so a missing row can't NPE the login response.
        String cmpnNm = busiRepo.findById(link.getBizKey())
                .map(BusiInfm::getCmpnNm)
                .orElse("");

        String token = tokenProvider.issue(
                user.getUserId(), user.getUserNm(), link.getBizKey(), langCd);

        return new LoginResponse(
                token,
                "Bearer",
                tokenProvider.expiresInSeconds(),
                user.getUserId(),
                user.getUserNm(),
                link.getBizKey(),
                langCd,
                cmpnNm
        );
    }

    /**
     * Current-session snapshot for the authenticated caller. The JWT filter
     * has already placed the caller's {@code userId} and {@code bizKey} into
     * {@link TenantContext}; this method reads them, verifies the user is
     * still linked to that tenant and has not been deactivated/locked/deleted
     * in the meantime, and returns the fields needed to rehydrate the
     * frontend session.
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

        String cmpnNm = busiRepo.findById(bizKey)
                .map(BusiInfm::getCmpnNm)
                .orElse("");

        LangCd langCd = principal.langCd() != null ? principal.langCd() : LangCd.EN;

        return new MeResponse(
                user.getUserId(),
                user.getUserNm(),
                bizKey,
                langCd.name(),
                cmpnNm
        );
    }
}
