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

@Configuration
// Enables @PreAuthorize method-security. The JWT filter already populates
// ROLE_ADMIN / ROLE_USER authorities from the authGrCd claim, so
// hasRole('ADMIN') gates take effect immediately (SEC-1).
@EnableMethodSecurity
@EnableConfigurationProperties({ JwtProperties.class, TenantProperties.class })
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final CallbackProperties callbackProperties;

    @Bean
    public PasswordEncoder passwordEncoder() {
        // Hybrid: accepts legacy JEX SHA-256 base64 hashes (public.hash()) on
        // the read path, always writes BCrypt on the write path, and flags
        // legacy hashes for transparent upgrade via upgradeEncoding().
        return new WecmsPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> { })
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
