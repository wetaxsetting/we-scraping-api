package com.kosign.wecms.domain.auth.service;

import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.security.JwtTokenProvider;
import com.kosign.wecms.config.TenantProperties;
import com.kosign.wecms.domain.auth.dto.LoginRequest;
import com.kosign.wecms.domain.auth.dto.LoginResponse;
import com.kosign.wecms.domain.auth.entity.BusiInfm;
import com.kosign.wecms.domain.auth.entity.UserBusiConnInfm;
import com.kosign.wecms.domain.auth.entity.UserInfm;
import com.kosign.wecms.domain.auth.entity.UserLoginHis;
import com.kosign.wecms.domain.auth.repository.BusiInfmRepository;
import com.kosign.wecms.domain.auth.repository.UserBusiConnInfmRepository;
import com.kosign.wecms.domain.auth.repository.UserInfmRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserInfmRepository userRepo;
    @Mock UserBusiConnInfmRepository userBusiRepo;
    @Mock BusiInfmRepository busiRepo;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtTokenProvider tokenProvider;
    @Mock LoginHistoryService history;

    AuthService authService;

    private static final String ID   = "testuser";
    private static final String PW   = "Password!1";
    private static final String HASH = "$2a$10$dummy";
    private static final String IP   = "127.0.0.1";
    private static final String BIZ  = "VN_VNM0004_000001";
    private static final String LANG = "EN";

    private UserInfm user;
    private UserBusiConnInfm link;

    @BeforeEach
    void seed() {
        authService = new AuthService(
                userRepo, userBusiRepo, busiRepo, passwordEncoder,
                tokenProvider, history, new TenantProperties(BIZ));

        user = new UserInfm();
        user.setUserId(ID);
        user.setUserNm("Test User");
        user.setUserPw(HASH);
        user.setActvYn("Y");
        user.setLockYn("N");
        user.setDelYn("N");

        link = new UserBusiConnInfm();
        link.setUserId(ID);
        link.setBizKey(BIZ);
        link.setDelYn("N");
    }

    @Test
    void success_issuesToken_andRecordsSuccessHistory_andUpdatesLstLgnDate() {
        BusiInfm busi = new BusiInfm();
        busi.setBizKey(BIZ);
        busi.setCmpnNm("Test Company");

        when(userRepo.findById(ID)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(PW, HASH)).thenReturn(true);
        when(userBusiRepo.findByUserIdAndBizKeyAndDelYn(ID, BIZ, "N")).thenReturn(Optional.of(link));
        when(busiRepo.findById(BIZ)).thenReturn(Optional.of(busi));
        when(tokenProvider.issue(ID, "Test User", BIZ, LANG)).thenReturn("jwt-token");
        when(tokenProvider.expiresInSeconds()).thenReturn(3600L);

        LoginResponse res = authService.login(new LoginRequest(ID, PW), IP, LANG);

        assertThat(res.accessToken()).isEqualTo("jwt-token");
        assertThat(res.tokenType()).isEqualTo("Bearer");
        assertThat(res.expiresIn()).isEqualTo(3600L);
        assertThat(res.userId()).isEqualTo(ID);
        assertThat(res.userNm()).isEqualTo("Test User");
        assertThat(res.bizKey()).isEqualTo(BIZ);
        assertThat(res.langCd()).isEqualTo("EN");
        assertThat(res.cmpnNm()).isEqualTo("Test Company");

        verify(history).record(ID, IP, UserLoginHis.RSLT_SUCCESS, null);

        ArgumentCaptor<UserBusiConnInfm> linkCapture = ArgumentCaptor.forClass(UserBusiConnInfm.class);
        verify(userBusiRepo).save(linkCapture.capture());
        assertThat(linkCapture.getValue().getLstLgnDate()).isNotNull();
    }

    @Test
    void unknownUser_throwsWBE1009_andRecordsFail() {
        when(userRepo.findById(ID)).thenReturn(Optional.empty());

        assertBusinessCode("WBE1009", () -> authService.login(new LoginRequest(ID, PW), IP, LANG));
        verify(history).record(ID, IP, UserLoginHis.RSLT_FAIL, "USER_NOT_FOUND");
        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }

    @Test
    void deletedUser_throwsWBE1128_andRecordsFail() {
        user.setDelYn("Y");
        when(userRepo.findById(ID)).thenReturn(Optional.of(user));

        assertBusinessCode("WBE1128", () -> authService.login(new LoginRequest(ID, PW), IP, LANG));
        verify(history).record(ID, IP, UserLoginHis.RSLT_FAIL, "DEL_YN=Y");
    }

    @Test
    void inactiveUser_throwsWBE1010_andRecordsFail() {
        user.setActvYn("N");
        when(userRepo.findById(ID)).thenReturn(Optional.of(user));

        assertBusinessCode("WBE1010", () -> authService.login(new LoginRequest(ID, PW), IP, LANG));
        verify(history).record(ID, IP, UserLoginHis.RSLT_FAIL, "ACTV_YN=N");
    }

    @Test
    void lockedUser_throwsWBE1011_andRecordsFail() {
        user.setLockYn("Y");
        when(userRepo.findById(ID)).thenReturn(Optional.of(user));

        assertBusinessCode("WBE1011", () -> authService.login(new LoginRequest(ID, PW), IP, LANG));
        verify(history).record(ID, IP, UserLoginHis.RSLT_FAIL, "LOCK_YN=Y");
    }

    @Test
    void badPassword_throwsWBE1009_andRecordsFail() {
        when(userRepo.findById(ID)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(PW, HASH)).thenReturn(false);

        assertBusinessCode("WBE1009", () -> authService.login(new LoginRequest(ID, PW), IP, LANG));
        verify(history).record(ID, IP, UserLoginHis.RSLT_FAIL, "PW_MISMATCH");
    }

    @Test
    void outOfScopeTenant_throwsWBE1009() {
        // User is valid but has no link to the configured tenant (or no active link at all).
        when(userRepo.findById(ID)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(PW, HASH)).thenReturn(true);
        when(userBusiRepo.findByUserIdAndBizKeyAndDelYn(ID, BIZ, "N")).thenReturn(Optional.empty());

        assertBusinessCode("WBE1009", () -> authService.login(new LoginRequest(ID, PW), IP, LANG));
        verify(history).record(eq(ID), eq(IP), eq(UserLoginHis.RSLT_FAIL), anyString());
        verify(userBusiRepo, never()).save(any());
    }

    private void assertBusinessCode(String expectedCode, Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(expectedCode);
    }
}
