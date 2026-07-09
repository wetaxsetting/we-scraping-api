package com.kosign.wecms.domain.auth.service;

import com.kosign.wecms.domain.auth.entity.UserInfm;
import com.kosign.wecms.domain.auth.entity.UserLoginHis;
import com.kosign.wecms.domain.auth.repository.UserInfmRepository;
import com.kosign.wecms.domain.auth.repository.UserLoginHisRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * F-10 — durable 5-consecutive-failure account auto-lock.
 *
 * <p>Uses the EXISTING schema only: the {@code USER_LOGIN_HIS} FAIL/SUCCESS audit
 * rows (already written by {@link LoginHistoryService}) supply the streak, and
 * {@code USER_INFM.LOCK_YN} is the durable lock the login flow already enforces
 * ({@code AuthService} → {@code WBE1011}). No new column / table is introduced.
 *
 * <p>"Consecutive" is evaluated as "the latest {@value #MAX_CONSECUTIVE_FAILURES}
 * attempts are ALL failures" — a SUCCESS row in that window resets the streak, so a
 * successful login implicitly clears the counter (no separate reset write needed).
 *
 * <p><b>Coexistence note:</b> the lock is written to the shared live JEX DB, so a
 * brute-forced account is locked for the legacy system too — the intended security
 * behaviour. Unlock is an admin / DBA action (set {@code LOCK_YN='N'}), matching the
 * legacy lock semantics.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginLockoutService {

    /** Lock after this many consecutive failed attempts. Kept in lock-step with the
     *  {@code findTop5...} finder below — changing one requires changing the other. */
    public static final int MAX_CONSECUTIVE_FAILURES = 5;

    /** Audit actor stamped on the system-initiated lock write. */
    private static final String SYSTEM_ACTOR = "SYSTEM";

    private final UserLoginHisRepository loginHisRepo;
    private final UserInfmRepository userRepo;

    /**
     * Lock the user when their most recent {@value #MAX_CONSECUTIVE_FAILURES}
     * attempts are all failures. Call this AFTER the current attempt's FAIL row has
     * been committed (the REQUIRES_NEW {@link LoginHistoryService#record}).
     *
     * <p>Runs in its OWN {@code REQUIRES_NEW} transaction so the lock survives the
     * outer login transaction rollback that follows the {@code WBE1009} throw.
     * Idempotent: a no-op when the user row is missing or already locked.
     *
     * @return {@code true} if this call locked the account.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean lockIfThresholdReached(String userId) {
        List<UserLoginHis> recent =
                loginHisRepo.findTop5ByUserIdOrderByRegDtmDescRegUidDesc(userId);
        if (recent.size() < MAX_CONSECUTIVE_FAILURES) {
            return false;
        }
        boolean allFailed = recent.stream()
                .allMatch(h -> UserLoginHis.RSLT_FAIL.equals(h.getRsltCd()));
        if (!allFailed) {
            return false;
        }
        UserInfm user = userRepo.findById(userId).orElse(null);
        if (user == null || user.isLocked()) {
            return false;
        }
        user.setLockYn("Y");
        user.setModId(SYSTEM_ACTOR);
        userRepo.save(user);
        // Audit trail = the FAIL rows in USER_LOGIN_HIS + this WARN line.
        // No password / credential is ever logged.
        log.warn("Account auto-locked after {} consecutive failed logins: userId={}",
                MAX_CONSECUTIVE_FAILURES, userId);
        return true;
    }
}
