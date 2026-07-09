package com.kosign.wecms.domain.auth.service;

import com.kosign.wecms.domain.auth.entity.UserInfm;
import com.kosign.wecms.domain.auth.entity.UserLoginHis;
import com.kosign.wecms.domain.auth.repository.UserInfmRepository;
import com.kosign.wecms.domain.auth.repository.UserLoginHisRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * F-10 — durable 5-consecutive-failure auto-lock logic.
 */
@ExtendWith(MockitoExtension.class)
class LoginLockoutServiceTest {

    private static final String USER = "u1";

    @Mock UserLoginHisRepository loginHisRepo;
    @Mock UserInfmRepository userRepo;
    @InjectMocks LoginLockoutService service;

    private UserLoginHis his(String rsltCd) {
        UserLoginHis h = new UserLoginHis();
        h.setUserId(USER);
        h.setRsltCd(rsltCd);
        return h;
    }

    private UserInfm user(String lockYn) {
        UserInfm u = new UserInfm();
        u.setUserId(USER);
        u.setLockYn(lockYn);
        return u;
    }

    @Test
    void fiveConsecutiveFailures_locksUser() {
        when(loginHisRepo.findTop5ByUserIdOrderByRegDtmDescRegUidDesc(USER))
                .thenReturn(List.of(his("FAIL"), his("FAIL"), his("FAIL"), his("FAIL"), his("FAIL")));
        UserInfm u = user("N");
        when(userRepo.findById(USER)).thenReturn(Optional.of(u));

        boolean locked = service.lockIfThresholdReached(USER);

        assertThat(locked).isTrue();
        assertThat(u.isLocked()).isTrue();
        verify(userRepo).save(u);
    }

    @Test
    void successWithinWindow_resetsStreak_doesNotLock() {
        // A SUCCESS among the last 5 means the streak was broken.
        when(loginHisRepo.findTop5ByUserIdOrderByRegDtmDescRegUidDesc(USER))
                .thenReturn(List.of(his("FAIL"), his("FAIL"), his("SUCCESS"), his("FAIL"), his("FAIL")));

        assertThat(service.lockIfThresholdReached(USER)).isFalse();
        verify(userRepo, never()).save(any());
    }

    @Test
    void fewerThanFiveAttempts_doesNotLock() {
        when(loginHisRepo.findTop5ByUserIdOrderByRegDtmDescRegUidDesc(USER))
                .thenReturn(List.of(his("FAIL"), his("FAIL")));

        assertThat(service.lockIfThresholdReached(USER)).isFalse();
        verify(userRepo, never()).save(any());
    }

    @Test
    void alreadyLockedUser_isNoOp() {
        when(loginHisRepo.findTop5ByUserIdOrderByRegDtmDescRegUidDesc(USER))
                .thenReturn(List.of(his("FAIL"), his("FAIL"), his("FAIL"), his("FAIL"), his("FAIL")));
        when(userRepo.findById(USER)).thenReturn(Optional.of(user("Y")));

        assertThat(service.lockIfThresholdReached(USER)).isFalse();
        verify(userRepo, never()).save(any());
    }

    @Test
    void missingUser_isNoOp() {
        when(loginHisRepo.findTop5ByUserIdOrderByRegDtmDescRegUidDesc(USER))
                .thenReturn(List.of(his("FAIL"), his("FAIL"), his("FAIL"), his("FAIL"), his("FAIL")));
        when(userRepo.findById(USER)).thenReturn(Optional.empty());

        assertThat(service.lockIfThresholdReached(USER)).isFalse();
        verify(userRepo, never()).save(any());
    }
}
