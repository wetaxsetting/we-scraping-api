package com.kosign.wecms.config;

import com.kosign.wecms.common.security.JwtAuthenticationEntryPoint;
import com.kosign.wecms.common.security.JwtAuthenticationFilter;
import com.kosign.wecms.common.security.WecmsPasswordEncoder;
import com.kosign.wecms.domain.payment.dispatch.callback.CallbackIpWhitelistFilter;
import com.kosign.wecms.domain.payment.dispatch.callback.CallbackProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

@Configuration
// Enables @PreAuthorize method-security. The JWT filter already populates
// ROLE_ADMIN / ROLE_USER authorities from the authGrCd claim, so
// hasRole('ADMIN') gates take effect immediately (SEC-1).
@EnableMethodSecurity
@EnableConfigurationProperties(JwtProperties.class)
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final CallbackProperties callbackProperties;

    @Bean
    public PasswordEncoder passwordEncoder() {
        // Coexistence encoder: WRITES the legacy JEX SHA-256 base64
        // (public.hash()) value on every write path — NEVER BCrypt — so the
        // live JEX system can authenticate the same USER_INFM row. On read it
        // verifies the SHA-256 hash (and tolerates a pre-existing BCrypt hash
        // for old dev rows only). upgradeEncoding() always returns false:
        // login is verify-only, with no transparent upgrade and no rewrite of
        // USER_PW. See WecmsPasswordEncoder / LegacyHashPasswordEncoder.
        return new WecmsPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> { })
                // F-04: explicit browser-security headers for a financial API.
                // This is a JSON + image/file-blob API (the Next.js frontend is a
                // separate origin with its own CSP), so a restrictive policy is
                // safe here and also hardens the inline image/file responses:
                //   - CSP default-src 'none'  → a served document loads no active
                //     sub-resources (defence-in-depth vs. any inline-content XSS).
                //   - frame-ancestors 'none' + frameOptions DENY → anti-clickjacking.
                //   - HSTS is emitted ONLY on HTTPS requests (Spring gates it on
                //     request.isSecure()), so local plain-HTTP dev is unaffected
                //     while SIT/UAT/PROD behind TLS get a 1-year policy.
                // Spring Security's other defaults (X-Content-Type-Options nosniff,
                // Cache-Control) remain applied. If a future endpoint must serve a
                // real HTML page from this origin, relax the CSP for that path only.
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'none'; frame-ancestors 'none'; base-uri 'none'"))
                        .frameOptions(frame -> frame.deny())
                        .referrerPolicy(rp -> rp.policy(
                                ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31_536_000)))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/login", "/api/v1/auth/logout", "/actuator/health").permitAll()
                        // Inbound EFS callbacks (01301/01302): the gateway cannot present a
                        // WeCMS JWT — authentication is the per-VAN VV/EV crypto check inside
                        // the callback services, plus the optional source-IP allow-list
                        // enforced by CallbackIpWhitelistFilter (below). See CallbackProperties.
                        .requestMatchers("/api/v1/payment/dispatch/callbacks/**").permitAll()
                        .anyRequest().authenticated())
                // Override default Http403ForbiddenEntryPoint so missing /
                // expired JWTs surface as 401 (RFC 7235) — required by the
                // frontend session-expired interceptor in lib/axios.ts.
                .exceptionHandling(eh -> eh.authenticationEntryPoint(jwtAuthenticationEntryPoint))
                // IP allow-list for the permitted callback path (no-op elsewhere); runs
                // before the JWT filter so a disallowed source is rejected at the edge.
                // Both custom filters anchor on UsernamePasswordAuthenticationFilter (a
                // Spring-Security-registered filter); a custom filter class has no
                // registered order and cannot be used as a position anchor. Insertion
                // order at the same offset is preserved, so adding the callback filter
                // first keeps the chain order: callback → jwt → UsernamePassword.
                .addFilterBefore(new CallbackIpWhitelistFilter(callbackProperties), UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
