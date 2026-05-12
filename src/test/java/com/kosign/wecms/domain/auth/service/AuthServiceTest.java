package com.kosign.wecms.domain.auth.service;

import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.security.JwtTokenProvider;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.auth.dto.LoginRequest;
import com.kosign.wecms.domain.auth.dto.LoginResponse;
import com.kosign.wecms.domain.auth.entity.BusiAdmnInfm;
import com.kosign.wecms.domain.auth.entity.BusiInfm;
import com.kosign.wecms.domain.auth.entity.UserBusiConnInfm;
import com.kosign.wecms.domain.auth.entity.UserInfm;
import com.kosign.wecms.domain.auth.entity.UserLoginHis;
import com.kosign.wecms.domain.auth.repository.BusiAdmnInfmRepository;
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

import java.util.List;
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
    @Mock BusiAdmnInfmRepository busiAdmnRepo;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtTokenProvider tokenProvider;
    @Mock LoginHistoryService history;

    AuthService authService;

    private static final String ID    = "testuser";
    private static final String PW    = "Password!1";
    private static final String HASH  = "$2a$10$dummy";
    private static final String IP    = "127.0.0.1";
    private static final String BIZ_A = "VN_VNM0004_000001";   // WeCMS default → PRDT_TYPE = "F"
    private static final String BIZ_B = "VN_VNM0004_000014";   // HAENGSUNG    → PRDT_TYPE = "H"
    private static final String BIZ_C = "VN_VNM0004_999999";   // Synthetic Lumens → PRDT_TYPE = "M"
    private static final String LANG  = "EN";

    private UserInfm user;
    private UserBusiConnInfm linkA;
    private UserBusiConnInfm linkB;
    private UserBusiConnInfm linkC;

    @BeforeEach
    void seed() {
        authService = new AuthService(
                userRepo, userBusiRepo, busiRepo, busiAdmnRepo,
                passwordEncoder, tokenProvider, history);

        user = new UserInfm();
        user.setUserId(ID);
        user.setUserNm("Test User");
        user.setUserPw(HASH);
        user.setActvYn("Y");
        user.setLockYn("N");
        user.setDelYn("N");

        linkA = new UserBusiConnInfm();
        linkA.setUserId(ID);
        linkA.setBizKey(BIZ_A);
        linkA.setDelYn("N");

        linkB = new UserBusiConnInfm();
        linkB.setUserId(ID);
        linkB.setBizKey(BIZ_B);
        linkB.setDelYn("N");

        linkC = new UserBusiConnInfm();
        linkC.setUserId(ID);
        linkC.setBizKey(BIZ_C);
        linkC.setDelYn("N");
    }

    private BusiInfm company(String bizKey, String cmpnNm, String prdtType) {
        BusiInfm b = new BusiInfm();
        b.setBizKey(bizKey);
        b.setCmpnNm(cmpnNm);
        b.setPrdtType(prdtType);
        return b;
    }

    // ---------------------------------------------------------------
    // Single-link users — auto-pick the only company.
    // ---------------------------------------------------------------

    @Test
    void singleLink_companyF_carriesPrdtTypeF_andIssuesToken() {
        when(userRepo.findById(ID)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(PW, HASH)).thenReturn(true);
        when(userBusiRepo.findAllByUserIdAndDelYn(ID, "N")).thenReturn(List.of(linkA));
        when(busiRepo.findById(BIZ_A)).thenReturn(Optional.of(company(BIZ_A, "WeCMS R&D Tenant", "F")));
        when(busiAdmnRepo.existsByBizKeyAndUserId(BIZ_A, ID)).thenReturn(true);
        when(tokenProvider.issue(ID, "Test User", BIZ_A, LANG, TenantContext.AUTH_GR_ADMIN, "F"))
                .thenReturn("jwt-token-f");
        when(tokenProvider.expiresInSeconds()).thenReturn(3600L);

        LoginResponse res = authService.login(new LoginRequest(ID, PW, null), IP, LANG);

        assertThat(res.accessToken()).isEqualTo("jwt-token-f");
        assertThat(res.bizKey()).isEqualTo(BIZ_A);
        assertThat(res.authGrCd()).isEqualTo(TenantContext.AUTH_GR_ADMIN);
        assertThat(res.prdtType()).isEqualTo("F");
        assertThat(res.companiesAvailable()).isNull();

        verify(history).record(ID, IP, UserLoginHis.RSLT_SUCCESS, null);

        ArgumentCaptor<UserBusiConnInfm> linkCapture = ArgumentCaptor.forClass(UserBusiConnInfm.class);
        verify(userBusiRepo).save(linkCapture.capture());
        assertThat(linkCapture.getValue().getLstLgnDate()).isNotNull();
    }

    @Test
    void singleLink_companyH_carriesPrdtTypeH() {
        when(userRepo.findById(ID)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(PW, HASH)).thenReturn(true);
        when(userBusiRepo.findAllByUserIdAndDelYn(ID, "N")).thenReturn(List.of(linkB));
        when(busiRepo.findById(BIZ_B)).thenReturn(Optional.of(company(BIZ_B, "HAENGSUNG ELECTRONICS", "H")));
        when(busiAdmnRepo.existsByBizKeyAndUserId(BIZ_B, ID)).thenReturn(false);
        when(tokenProvider.issue(ID, "Test User", BIZ_B, LANG, TenantContext.AUTH_GR_USER, "H"))
                .thenReturn("jwt-token-h");

        LoginResponse res = authService.login(new LoginRequest(ID, PW, null), IP, LANG);

        assertThat(res.accessToken()).isEqualTo("jwt-token-h");
        assertThat(res.bizKey()).isEqualTo(BIZ_B);
        assertThat(res.prdtType()).isEqualTo("H");
        assertThat(res.authGrCd()).isEqualTo(TenantContext.AUTH_GR_USER);
    }

    @Test
    void singleLink_companyM_carriesPrdtTypeM() {
        when(userRepo.findById(ID)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(PW, HASH)).thenReturn(true);
        when(userBusiRepo.findAllByUserIdAndDelYn(ID, "N")).thenReturn(List.of(linkC));
        when(busiRepo.findById(BIZ_C)).thenReturn(Optional.of(company(BIZ_C, "WeCMS Lumens R&D", "M")));
        when(busiAdmnRepo.existsByBizKeyAndUserId(BIZ_C, ID)).thenReturn(false);
        when(tokenProvider.issue(ID, "Test User", BIZ_C, LANG, TenantContext.AUTH_GR_USER, "M"))
                .thenReturn("jwt-token-m");

        LoginResponse res = authService.login(new LoginRequest(ID, PW, null), IP, LANG);

        assertThat(res.prdtType()).isEqualTo("M");
    }

    @Test
    void singleLink_companyWithNullPrdtType_shipsNullVerbatimNotF() {
        when(userRepo.findById(ID)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(PW, HASH)).thenReturn(true);
        when(userBusiRepo.findAllByUserIdAndDelYn(ID, "N")).thenReturn(List.of(linkA));
        when(busiRepo.findById(BIZ_A)).thenReturn(Optional.of(company(BIZ_A, "Mystery Tenant", null)));
        when(busiAdmnRepo.existsByBizKeyAndUserId(BIZ_A, ID)).thenReturn(false);
        when(tokenProvider.issue(ID, "Test User", BIZ_A, LANG, TenantContext.AUTH_GR_USER, null))
                .thenReturn("jwt-token-null");

        LoginResponse res = authService.login(new LoginRequest(ID, PW, null), IP, LANG);

        assertThat(res.prdtType()).isNull();   // Never silently default to "F".
    }

    @Test
    void singleLink_companyWithDeferredLegacyLetter_carriedThroughVerbatim() {
        // 'W' (WABOOKS Default) is not in the F/M/H enum but must still
        // round-trip cleanly so login does not fail for any legacy tenant
        // that still uses the unknown letters.
        when(userRepo.findById(ID)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(PW, HASH)).thenReturn(true);
        when(userBusiRepo.findAllByUserIdAndDelYn(ID, "N")).thenReturn(List.of(linkA));
        when(busiRepo.findById(BIZ_A)).thenReturn(Optional.of(company(BIZ_A, "Legacy WABOOKS", "W")));
        when(busiAdmnRepo.existsByBizKeyAndUserId(BIZ_A, ID)).thenReturn(false);
        when(tokenProvider.issue(ID, "Test User", BIZ_A, LANG, TenantContext.AUTH_GR_USER, "W"))
                .thenReturn("jwt-token-w");

        LoginResponse res = authService.login(new LoginRequest(ID, PW, null), IP, LANG);

        assertThat(res.prdtType()).isEqualTo("W");
    }

    @Test
    void singleLink_requestedBizKeyMismatch_throwsWBE1009() {
        when(userRepo.findById(ID)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(PW, HASH)).thenReturn(true);
        when(userBusiRepo.findAllByUserIdAndDelYn(ID, "N")).thenReturn(List.of(linkA));

        assertBusinessCode("WBE1009", () ->
                authService.login(new LoginRequest(ID, PW, "VN_VNM_OTHER_999999"), IP, LANG));
        verify(history).record(eq(ID), eq(IP), eq(UserLoginHis.RSLT_FAIL), anyString());
        verify(userBusiRepo, never()).save(any());
    }

    // ---------------------------------------------------------------
    // Multi-link users — chooser per-option product type, then finalize.
    // ---------------------------------------------------------------

    @Test
    void multiLink_firstCall_chooserOptionsCarryPerCompanyPrdtType() {
        BusiInfm bA = company(BIZ_A, "WeCMS R&D Tenant",       "F");
        BusiInfm bB = company(BIZ_B, "HAENGSUNG ELECTRONICS",  "H");

        BusiAdmnInfm admin = new BusiAdmnInfm();
        admin.setBizKey(BIZ_A);
        admin.setUserId(ID);
        admin.setUserNm("Test User");

        when(userRepo.findById(ID)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(PW, HASH)).thenReturn(true);
        when(userBusiRepo.findAllByUserIdAndDelYn(ID, "N")).thenReturn(List.of(linkA, linkB));
        when(busiRepo.findAllById(List.of(BIZ_A, BIZ_B))).thenReturn(List.of(bA, bB));
        when(busiAdmnRepo.findAllByUserId(ID)).thenReturn(List.of(admin));

        LoginResponse res = authService.login(new LoginRequest(ID, PW, null), IP, LANG);

        assertThat(res.accessToken()).isNull();
        assertThat(res.prdtType()).isNull();           // top-level prdtType is null on chooser shape
        assertThat(res.companiesAvailable()).hasSize(2);

        // Sorted by company name — HAENGSUNG ELECTRONICS comes first.
        assertThat(res.companiesAvailable().get(0).bizKey()).isEqualTo(BIZ_B);
        assertThat(res.companiesAvailable().get(0).prdtType()).isEqualTo("H");
        assertThat(res.companiesAvailable().get(0).authGrCd()).isEqualTo(TenantContext.AUTH_GR_USER);

        assertThat(res.companiesAvailable().get(1).bizKey()).isEqualTo(BIZ_A);
        assertThat(res.companiesAvailable().get(1).prdtType()).isEqualTo("F");
        assertThat(res.companiesAvailable().get(1).authGrCd()).isEqualTo(TenantContext.AUTH_GR_ADMIN);

        // No token issued yet — no LST_LGN_DATE write, no SUCCESS audit.
        verify(userBusiRepo, never()).save(any());
        verify(tokenProvider, never()).issue(anyString(), anyString(), anyString(),
                                             anyString(), anyString(), anyString());
    }

    @Test
    void multiLink_finalizeSwitchesActivePrdtType_FthenH() {
        // Round 1 — user picks the F company.
        when(userRepo.findById(ID)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(PW, HASH)).thenReturn(true);
        when(userBusiRepo.findAllByUserIdAndDelYn(ID, "N")).thenReturn(List.of(linkA, linkB));
        when(busiRepo.findById(BIZ_A)).thenReturn(Optional.of(company(BIZ_A, "WeCMS R&D Tenant", "F")));
        when(busiAdmnRepo.existsByBizKeyAndUserId(BIZ_A, ID)).thenReturn(true);
        when(tokenProvider.issue(ID, "Test User", BIZ_A, LANG, TenantContext.AUTH_GR_ADMIN, "F"))
                .thenReturn("jwt-round-1");

        LoginResponse roundOne = authService.login(new LoginRequest(ID, PW, BIZ_A), IP, LANG);
        assertThat(roundOne.bizKey()).isEqualTo(BIZ_A);
        assertThat(roundOne.prdtType()).isEqualTo("F");

        // Round 2 — same credentials, picks the H company instead.
        when(busiRepo.findById(BIZ_B)).thenReturn(Optional.of(company(BIZ_B, "HAENGSUNG ELECTRONICS", "H")));
        when(busiAdmnRepo.existsByBizKeyAndUserId(BIZ_B, ID)).thenReturn(false);
        when(tokenProvider.issue(ID, "Test User", BIZ_B, LANG, TenantContext.AUTH_GR_USER, "H"))
                .thenReturn("jwt-round-2");

        LoginResponse roundTwo = authService.login(new LoginRequest(ID, PW, BIZ_B), IP, LANG);
        assertThat(roundTwo.bizKey()).isEqualTo(BIZ_B);
        assertThat(roundTwo.prdtType()).isEqualTo("H");

        // Two distinct tokens, two distinct product types — confirms
        // company switching also switches active prdtType.
        assertThat(roundOne.accessToken()).isNotEqualTo(roundTwo.accessToken());
    }

    @Test
    void multiLink_finalizeWithUnknownBizKey_throwsWBE1009() {
        when(userRepo.findById(ID)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(PW, HASH)).thenReturn(true);
        when(userBusiRepo.findAllByUserIdAndDelYn(ID, "N")).thenReturn(List.of(linkA, linkB));

        assertBusinessCode("WBE1009", () ->
                authService.login(new LoginRequest(ID, PW, "VN_VNM_NOT_LINKED"), IP, LANG));
        verify(history).record(eq(ID), eq(IP), eq(UserLoginHis.RSLT_FAIL), anyString());
        verify(userBusiRepo, never()).save(any());
    }

    // ---------------------------------------------------------------
    // /me — reads prdtType from JWT claim (not from DB).
    // ---------------------------------------------------------------

    @Test
    void me_readsPrdtTypeFromJwtClaim_notFromBusiInfm() {
        // TenantContext is populated by JwtAuthenticationFilter from the
        // signed JWT — me() must trust the claim, not re-query.
        TenantContext.set(ID, "Test User", BIZ_A,
                com.kosign.wecms.common.enums.LangCd.EN,
                TenantContext.AUTH_GR_ADMIN,
                "H");                                     // JWT says "H"
        try {
            when(userRepo.findById(ID)).thenReturn(Optional.of(user));
            when(userBusiRepo.findByUserIdAndBizKeyAndDelYn(ID, BIZ_A, "N"))
                    .thenReturn(Optional.of(linkA));
            // Note: BusiInfm row carries "F" — divergent from the claim
            // on purpose. me() must return the JWT's "H".
            when(busiRepo.findById(BIZ_A)).thenReturn(Optional.of(company(BIZ_A, "WeCMS R&D Tenant", "F")));

            assertThat(authService.me().prdtType()).isEqualTo("H");
        } finally {
            TenantContext.clear();
        }
    }

    // ---------------------------------------------------------------
    // Flag-gate paths preserved from Sprint 0.
    // ---------------------------------------------------------------

    @Test
    void unknownUser_throwsWBE1009_andRecordsFail() {
        when(userRepo.findById(ID)).thenReturn(Optional.empty());

        assertBusinessCode("WBE1009", () -> authService.login(new LoginRequest(ID, PW, null), IP, LANG));
        verify(history).record(ID, IP, UserLoginHis.RSLT_FAIL, "USER_NOT_FOUND");
        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }

    @Test
    void deletedUser_throwsWBE1128_andRecordsFail() {
        user.setDelYn("Y");
        when(userRepo.findById(ID)).thenReturn(Optional.of(user));

        assertBusinessCode("WBE1128", () -> authService.login(new LoginRequest(ID, PW, null), IP, LANG));
        verify(history).record(ID, IP, UserLoginHis.RSLT_FAIL, "DEL_YN=Y");
    }

    @Test
    void inactiveUser_throwsWBE1010_andRecordsFail() {
        user.setActvYn("N");
        when(userRepo.findById(ID)).thenReturn(Optional.of(user));

        assertBusinessCode("WBE1010", () -> authService.login(new LoginRequest(ID, PW, null), IP, LANG));
        verify(history).record(ID, IP, UserLoginHis.RSLT_FAIL, "ACTV_YN=N");
    }

    @Test
    void lockedUser_throwsWBE1011_andRecordsFail() {
        user.setLockYn("Y");
        when(userRepo.findById(ID)).thenReturn(Optional.of(user));

        assertBusinessCode("WBE1011", () -> authService.login(new LoginRequest(ID, PW, null), IP, LANG));
        verify(history).record(ID, IP, UserLoginHis.RSLT_FAIL, "LOCK_YN=Y");
    }

    @Test
    void badPassword_throwsWBE1009_andRecordsFail() {
        when(userRepo.findById(ID)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(PW, HASH)).thenReturn(false);

        assertBusinessCode("WBE1009", () -> authService.login(new LoginRequest(ID, PW, null), IP, LANG));
        verify(history).record(ID, IP, UserLoginHis.RSLT_FAIL, "PW_MISMATCH");
    }

    @Test
    void noActiveLinks_throwsWBE1009() {
        when(userRepo.findById(ID)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(PW, HASH)).thenReturn(true);
        when(userBusiRepo.findAllByUserIdAndDelYn(ID, "N")).thenReturn(List.of());

        assertBusinessCode("WBE1009", () -> authService.login(new LoginRequest(ID, PW, null), IP, LANG));
        verify(history).record(ID, IP, UserLoginHis.RSLT_FAIL, "NO_TENANT_LINKS");
        verify(userBusiRepo, never()).save(any());
    }

    private void assertBusinessCode(String expectedCode, Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(expectedCode);
    }
}
