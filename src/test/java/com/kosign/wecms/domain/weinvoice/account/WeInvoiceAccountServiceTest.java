package com.kosign.wecms.domain.weinvoice.account;

import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.security.JwtTokenProvider;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.auth.dto.LoginResponse;
import com.kosign.wecms.domain.auth.repository.UserInfmRepository;
import com.kosign.wecms.domain.weinvoice.config.WeInvoiceDb;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
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

/** docs/features/addon-service/weinvoice/weinvoice-self-signup-2026-11-13.md */
@ExtendWith(MockitoExtension.class)
class WeInvoiceAccountServiceTest {

    @Mock WeInvoiceDb db;
    @Mock WeInvoiceAccountRepository accounts;
    @Mock UserInfmRepository wecmsUsers;
    @Mock PasswordEncoder verifier;
    @Mock JwtTokenProvider tokens;

    WeInvoiceAccountService service;

    private static final String EMAIL = "new.user@example.com";

    @BeforeEach
    void setUp() {
        service = new WeInvoiceAccountService(db, accounts, wecmsUsers, verifier, tokens);
    }

    private static WeInvoiceAccount account(String id, boolean active, boolean locked, boolean deleted) {
        return new WeInvoiceAccount(id, "Nguyễn Văn A", id, null, "Công ty A", "hash", "VI", active, locked, deleted, 0);
    }

    private static SignupRequest signup(String email, String pw) {
        return new SignupRequest(email, "Nguyễn Văn A", pw, "0901234567", "Công ty A");
    }

    private static String code(Throwable t) {
        return ((BusinessException) t).getCode();
    }

    // ---------------------------------------------------------------- sign-up

    @Test
    void signup_storesBcryptHashAndLowerCasedEmail_thenSignsIn() {
        when(db.enabled()).thenReturn(true);
        when(accounts.find(EMAIL)).thenReturn(Optional.empty(), Optional.of(account(EMAIL, true, false, false)));
        when(wecmsUsers.existsByUserIdIgnoreCase(EMAIL)).thenReturn(false);
        when(accounts.insertSignup(eq(EMAIL), eq("Nguyễn Văn A"), eq("0901234567"), eq("Công ty A"), anyString(), eq("VI")))
                .thenReturn(true);
        when(tokens.issue(EMAIL, "Nguyễn Văn A", WeInvoiceAccountService.BIZ_KEY, "VI", TenantContext.AUTH_GR_USER, null))
                .thenReturn("jwt");

        LoginResponse res = service.signup(signup("  New.User@Example.com ", "abc12345"), "VI");

        assertThat(res.accessToken()).isEqualTo("jwt");
        assertThat(res.bizKey()).isEqualTo(WeInvoiceAccountService.BIZ_KEY);
        ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
        verify(accounts).insertSignup(eq(EMAIL), any(), any(), any(), hash.capture(), any());
        assertThat(hash.getValue()).startsWith("$2");
        assertThat(new BCryptPasswordEncoder().matches("abc12345", hash.getValue())).isTrue();
    }

    @Test
    void signup_rejectsAnEmailThatIsAWeCmsLogin() {
        when(db.enabled()).thenReturn(true);
        when(accounts.find(EMAIL)).thenReturn(Optional.empty());
        when(wecmsUsers.existsByUserIdIgnoreCase(EMAIL)).thenReturn(true);

        assertThatThrownBy(() -> service.signup(signup(EMAIL, "abc12345"), "VI"))
                .satisfies(t -> assertThat(code(t)).isEqualTo(WeInvoiceAccountService.CODE_TAKEN));
        verify(accounts, never()).insertSignup(any(), any(), any(), any(), any(), any());
    }

    @Test
    void signup_rejectsAnExistingWeInvoiceAccount() {
        when(db.enabled()).thenReturn(true);
        when(accounts.find(EMAIL)).thenReturn(Optional.of(account(EMAIL, true, false, false)));

        assertThatThrownBy(() -> service.signup(signup(EMAIL, "abc12345"), "VI"))
                .satisfies(t -> assertThat(code(t)).isEqualTo(WeInvoiceAccountService.CODE_TAKEN));
    }

    @Test
    void signup_rejectsBadEmailAndWeakPasswords() {
        when(db.enabled()).thenReturn(true);
        assertThatThrownBy(() -> service.signup(signup("not-an-email", "abc12345"), "VI"))
                .satisfies(t -> assertThat(code(t)).isEqualTo(WeInvoiceAccountService.CODE_BAD_EMAIL));
        for (String weak : new String[]{"abc1234", "abcdefgh", "12345678"}) {
            assertThatThrownBy(() -> service.signup(signup(EMAIL, weak), "VI"))
                    .as(weak)
                    .satisfies(t -> assertThat(code(t)).isEqualTo(WeInvoiceAccountService.CODE_WEAK_PASSWORD));
        }
        verify(accounts, never()).insertSignup(any(), any(), any(), any(), any(), any());
    }

    @Test
    void signup_whenWeInvoiceIsDisabled_isUnavailable() {
        when(db.enabled()).thenReturn(false);
        assertThatThrownBy(() -> service.signup(signup(EMAIL, "abc12345"), "VI"))
                .satisfies(t -> assertThat(code(t)).isEqualTo(WeInvoiceAccountService.CODE_UNAVAILABLE));
    }

    // ---------------------------------------------------------------- login

    @Test
    void login_unknownId_fallsThrough() {
        when(db.enabled()).thenReturn(true);
        when(accounts.find("someone")).thenReturn(Optional.empty());
        assertThat(service.login("someone", "pw", "EN")).isEmpty();
    }

    @Test
    void login_disabled_fallsThroughWithoutTouchingTheTable() {
        when(db.enabled()).thenReturn(false);
        assertThat(service.login("someone", "pw", "EN")).isEmpty();
        verify(accounts, never()).find(any());
    }

    @Test
    void login_tableMissingOrDbDown_fallsThroughSoWeCmsLoginsKeepWorking() {
        when(db.enabled()).thenReturn(true);
        when(accounts.find("someone")).thenThrow(new BadSqlGrammarException("find", "SELECT …",
                new java.sql.SQLException("relation \"weinvoice.winv_user_infm\" does not exist")));
        assertThat(service.login("someone", "pw", "EN")).isEmpty();
    }

    @Test
    void login_wrongPassword_countsTheFailure_andAnswersBadCredentials() {
        when(db.enabled()).thenReturn(true);
        when(accounts.find(EMAIL)).thenReturn(Optional.of(account(EMAIL, true, false, false)));
        when(verifier.matches("bad", "hash")).thenReturn(false);

        assertThatThrownBy(() -> service.login(EMAIL, "bad", "EN"))
                .satisfies(t -> assertThat(code(t)).isEqualTo(WeInvoiceAccountService.CODE_BAD_CREDENTIALS));
        verify(accounts).recordFailure(EMAIL);
        verify(accounts, never()).recordSuccess(any());
    }

    @Test
    void login_movedWeCmsAccount_keepsItsStoredIdAsSubject() {
        // A row moved from USER_INFM keeps its spelling; the subject must be that id (owner of the winv_* data).
        when(db.enabled()).thenReturn(true);
        when(accounts.find("Lamine@Gmail.com")).thenReturn(Optional.of(account("lamine@gmail.com", true, false, false)));
        when(verifier.matches("pw1", "hash")).thenReturn(true);
        when(tokens.issue(eq("lamine@gmail.com"), any(), eq(WeInvoiceAccountService.BIZ_KEY), eq("EN"),
                eq(TenantContext.AUTH_GR_USER), eq(null))).thenReturn("jwt");

        LoginResponse res = service.login("Lamine@Gmail.com", "pw1", "EN").orElseThrow();

        assertThat(res.userId()).isEqualTo("lamine@gmail.com");
        verify(accounts).recordSuccess("lamine@gmail.com");
    }

    @Test
    void login_flagsAreCheckedBeforeThePassword() {
        when(db.enabled()).thenReturn(true);
        when(accounts.find("d")).thenReturn(Optional.of(account("d", true, false, true)));
        when(accounts.find("i")).thenReturn(Optional.of(account("i", false, false, false)));
        when(accounts.find("l")).thenReturn(Optional.of(account("l", true, true, false)));

        assertThatThrownBy(() -> service.login("d", "x", "EN")).satisfies(t -> assertThat(code(t)).isEqualTo("WBE1128"));
        assertThatThrownBy(() -> service.login("i", "x", "EN")).satisfies(t -> assertThat(code(t)).isEqualTo("WBE1010"));
        assertThatThrownBy(() -> service.login("l", "x", "EN")).satisfies(t -> assertThat(code(t)).isEqualTo("WBE1011"));
        verify(verifier, never()).matches(any(), any());
    }

    // ---------------------------------------------------------------- /me

    @Test
    void me_rejectsALockedAccount() {
        when(db.enabled()).thenReturn(true);
        when(accounts.find(EMAIL)).thenReturn(Optional.of(account(EMAIL, true, true, false)));
        TenantContext.Principal p = new TenantContext.Principal(EMAIL, "A", WeInvoiceAccountService.BIZ_KEY, LangCd.VI,
                TenantContext.AUTH_GR_USER, null);

        assertThatThrownBy(() -> service.me(p)).satisfies(t -> assertThat(code(t)).isEqualTo("WBE1401"));
    }

    @Test
    void me_returnsTheAccount() {
        when(db.enabled()).thenReturn(true);
        when(accounts.find(EMAIL)).thenReturn(Optional.of(account(EMAIL, true, false, false)));
        TenantContext.Principal p = new TenantContext.Principal(EMAIL, "A", WeInvoiceAccountService.BIZ_KEY, LangCd.VI,
                TenantContext.AUTH_GR_USER, null);

        var me = service.me(p);
        assertThat(me.userId()).isEqualTo(EMAIL);
        assertThat(me.cmpnNm()).isEqualTo("Công ty A");
        assertThat(me.langCd()).isEqualTo("VI");
        assertThat(me.bizKey()).isEqualTo(WeInvoiceAccountService.BIZ_KEY);
    }
}
