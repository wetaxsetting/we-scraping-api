package com.kosign.wecms.domain.auth.service;

import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.auth.dto.ChangePasswordRequest;
import com.kosign.wecms.domain.auth.entity.UserInfm;
import com.kosign.wecms.domain.auth.entity.UserPwChgHis;
import com.kosign.wecms.domain.auth.repository.UserInfmRepository;
import com.kosign.wecms.domain.auth.repository.UserPwChgHisRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Change-password flow for the logged-in user. Port of the legacy
 * {@code comm_0400_001_u001_act.jsp} self-service password update
 * (popup {@code comm_0401_001}).
 * <p>
 * Validation order mirrors legacy exactly:
 * <ol>
 *   <li>All three fields required — enforced upstream by bean validation on
 *       {@link ChangePasswordRequest} ({@code WBE_VALIDATION}); legacy
 *       {@code ValidationHelper.validateRequired}.</li>
 *   <li>New password must equal the confirmation, else
 *       {@code WBE_CHANGE_PASSWORD_MISMATCH} (legacy WBE1052,
 *       {@code comm_0400_001_u001_act.jsp:97-99}).</li>
 *   <li>Current password must match the stored hash, else {@code WBE2091}
 *       "Password confirmation failed." (legacy WBE1053 + the
 *       {@code USER_INFM where user_pw = hash(?)} COUNT check at
 *       {@code comm_0400_001_u001_act.jsp:102-108}).</li>
 *   <li>New password must not match any of the last 3 history rows, else
 *       {@code WBE_CHANGE_PASSWORD_REUSED} (legacy WBE1017,
 *       {@code IDO.USER_PW_CHG_HIS_R003.xml}).</li>
 * </ol>
 * On success: {@code USER_INFM.USER_PW} is rewritten with a fresh BCrypt
 * hash and a {@code USER_PW_CHG_HIS} row is inserted (mirrors legacy
 * {@code UserInfmDAO.patch} + {@code UserPwChgHisDAO.create}).
 * <p>
 * Hashing reuses the shared {@code PasswordEncoder}
 * ({@code WecmsPasswordEncoder}) — identical to the login write path, so a
 * password changed here authenticates against {@code AuthService.login}
 * with no special handling, and history rows imported from legacy DEV
 * (SHA-256 base64) still match in the reuse check.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChangePasswordService {

    /** Legacy parity: only the last 3 changes block reuse (WBE1017). */
    private static final int REUSE_HISTORY_DEPTH = 3;

    private static final String CODE_UNAUTHENTICATED   = "WBE1401";
    private static final String CODE_CONFIRM_MISMATCH  = "WBE_CHANGE_PASSWORD_MISMATCH";
    private static final String CODE_CURRENT_MISMATCH  = "WBE2091";
    private static final String CODE_REUSED            = "WBE_CHANGE_PASSWORD_REUSED";

    private final UserInfmRepository userRepo;
    private final UserPwChgHisRepository historyRepo;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public void changePassword(ChangePasswordRequest req, String ipAddr) {
        final String userId = TenantContext.currentUserId();
        if (userId == null) {
            // No bound session — the JWT filter should have rejected this
            // before the controller, but fail closed.
            throw new BusinessException(CODE_UNAUTHENTICATED);
        }

        UserInfm user = userRepo.findById(userId).orElse(null);
        if (user == null || user.isDeleted() || !user.isActive() || user.isLocked()) {
            log.warn("change-password rejected: user {} no longer eligible", userId);
            throw new BusinessException(CODE_UNAUTHENTICATED);
        }

        // 1. New password must match its confirmation (legacy WBE1052).
        if (!req.newPassword().equals(req.confirmPassword())) {
            throw new BusinessException(CODE_CONFIRM_MISMATCH);
        }

        // 2. Current password must match the stored hash (legacy WBE1053 →
        //    refactor surfaces "Password confirmation failed." / WBE2091).
        if (!passwordEncoder.matches(req.currentPassword(), user.getUserPw())) {
            throw new BusinessException(CODE_CURRENT_MISMATCH);
        }

        // 3. New password must not reuse any of the last 3 changes
        //    (legacy WBE1017 / USER_PW_CHG_HIS_R003).
        if (isRecentlyUsed(userId, req.newPassword())) {
            throw new BusinessException(CODE_REUSED);
        }

        OffsetDateTime now = OffsetDateTime.now();
        String encoded = passwordEncoder.encode(req.newPassword());

        // Update the live credential.
        user.setUserPw(encoded);
        user.setModId(userId);
        userRepo.save(user);

        // Append the history row (per-user MAX+1 — Java-side generator).
        UserPwChgHis row = new UserPwChgHis();
        row.setUserId(userId);
        row.setRegSeqNo(historyRepo.findMaxRegSeqNo(userId) + 1);
        row.setUserPw(encoded);
        row.setIpAddr(ipAddr);
        row.setRegDtm(now);
        row.setModDtm(now);
        historyRepo.save(row);

        log.info("Password changed for user {}", userId);
    }

    private boolean isRecentlyUsed(String userId, String rawNewPassword) {
        List<UserPwChgHis> recent =
                historyRepo.findTop3ByUserIdOrderByRegDtmDescRegSeqNoDesc(userId);
        return recent.stream()
                .limit(REUSE_HISTORY_DEPTH)
                .map(UserPwChgHis::getUserPw)
                .anyMatch(stored -> passwordEncoder.matches(rawNewPassword, stored));
    }
}
