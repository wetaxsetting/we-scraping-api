package com.kosign.wecms.common.security;

import com.kosign.wecms.config.JwtProperties;
import com.kosign.wecms.domain.auth.repository.BusiAdmnInfmRepository;
import com.kosign.wecms.domain.auth.repository.BusiInfmRepository;
import com.kosign.wecms.domain.auth.repository.UserBusiConnInfmRepository;
import com.kosign.wecms.domain.auth.repository.UserInfmRepository;
import com.kosign.wecms.domain.auth.service.AdminLoginHistoryService;
import com.kosign.wecms.domain.auth.service.AuthService;
import com.kosign.wecms.domain.auth.service.LoginHistoryService;
import com.kosign.wecms.domain.auth.service.LoginLockoutService;
import com.kosign.wecms.domain.auth.service.MenuAuthorizationService;
import com.kosign.wecms.domain.auth.entity.UserLoginHis;
import com.kosign.wecms.common.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * F-03 — JWT lifecycle hardening. Exercises the real {@link JwtTokenProvider},
 * {@link TokenRevocationService}, and {@link JwtAuthenticationFilter} together
 * (no Spring context) to verify: tokens carry a {@code jti}; a valid token
 * authenticates; a logged-out / revoked token, an expired token, and a token
 * superseded by a user lock/delete/demote are all rejected.
 */
class JwtRevocationTest {

    // Test-only secret — >= 64 bytes (HS512), and deliberately NOT the
    // F-01 blocklisted dev placeholder.
    private static final String SECRET =
            "test-only-jwt-secret-do-not-use-anywhere-0123456789abcdefghijklmnop";

    private final JwtProperties props = new JwtProperties(SECRET, 3600, "wecms-api");
    private final JwtTokenProvider provider = new JwtTokenProvider(props);
    private final TokenRevocationService revocation = new TokenRevocationService();
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(provider, revocation);

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    private Authentication runFilter(String token) throws Exception {
        SecurityContextHolder.clearContext();
        MockHttpServletRequest req = new MockHttpServletRequest();
        if (token != null) {
            req.addHeader("Authorization", "Bearer " + token);
        }
        filter.doFilter(req, new MockHttpServletResponse(), new MockFilterChain());
        return SecurityContextHolder.getContext().getAuthentication();
    }

    private String issue(String userId) {
        return provider.issue(userId, "Name", "BK0001", "EN", "USER", null);
    }

    @Test
    void issuedTokenCarriesJti() {
        String jti = provider.parse(issue("u1")).getPayload().getId();
        assertThat(jti).isNotBlank();
    }

    @Test
    void validToken_authenticates() throws Exception {
        Authentication auth = runFilter(issue("u1"));
        assertThat(auth).isNotNull();
        assertThat(auth.getPrincipal()).isEqualTo("u1");
    }

    @Test
    void revokedToken_isRejected() throws Exception {
        String token = issue("u1");
        assertThat(runFilter(token)).isNotNull();                 // valid first

        var claims = provider.parse(token).getPayload();
        revocation.revokeToken(claims.getId(), claims.getExpiration().toInstant());

        assertThat(runFilter(token)).isNull();                    // rejected after revoke
    }

    @Test
    void expiredToken_isRejected() throws Exception {
        // A provider whose tokens are already expired at issue time.
        JwtTokenProvider expiredProvider =
                new JwtTokenProvider(new JwtProperties(SECRET, -10, "wecms-api"));
        String token = expiredProvider.issue("u1", "Name", "BK0001", "EN", "USER", null);
        assertThat(runFilter(token)).isNull();
    }

    @Test
    void lockedOrDemotedUser_tokenIsRejected() throws Exception {
        String token = issue("u1");
        assertThat(runFilter(token)).isNotNull();                 // valid first

        // Simulate UserManagementService delete/demote/password-reset: cut off
        // every token for the user issued before now (use a clearly-after cutoff
        // so the second-precision iat is strictly before it).
        revocation.revokeAllForUser("u1", Instant.now().plusSeconds(60));

        assertThat(runFilter(token)).isNull();                    // rejected
        // A different user's token is unaffected.
        assertThat(runFilter(issue("u2"))).isNotNull();
    }

    @Test
    void logout_revokesPresentedToken_soFilterRejectsIt() throws Exception {
        LoginHistoryService history = mock(LoginHistoryService.class);
        AuthService auth = new AuthService(
                mock(UserInfmRepository.class), mock(UserBusiConnInfmRepository.class),
                mock(BusiInfmRepository.class), mock(BusiAdmnInfmRepository.class),
                mock(PasswordEncoder.class), provider, history,
                mock(AdminLoginHistoryService.class),
                mock(MenuAuthorizationService.class), revocation,
                mock(LoginLockoutService.class));

        String token = issue("u1");
        assertThat(runFilter(token)).isNotNull();                 // logged in

        auth.logout("Bearer " + token, "10.0.0.1");               // server-side logout

        assertThat(runFilter(token)).isNull();                    // token no longer valid
        // Legacy parity: logout writes a USER_LOGIN_HIS RSLT_CD='LOGOUT' row.
        verify(history).record("u1", "10.0.0.1", UserLoginHis.RSLT_LOGOUT, null);
    }
}
