package com.kosign.wecms.domain.auth.controller;

import com.kosign.wecms.common.response.ApiResponse;
import com.kosign.wecms.domain.auth.dto.ChangePasswordRequest;
import com.kosign.wecms.domain.auth.dto.LoginRequest;
import com.kosign.wecms.domain.auth.dto.LoginResponse;
import com.kosign.wecms.domain.auth.dto.MeResponse;
import com.kosign.wecms.domain.auth.service.AuthService;
import com.kosign.wecms.domain.auth.service.ChangePasswordService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String DEFAULT_LANG = "EN";

    private final AuthService authService;
    private final ChangePasswordService changePasswordService;

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(
            @Valid @RequestBody LoginRequest req,
            @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage,
            HttpServletRequest servletReq) {
        String ip     = clientIp(servletReq);
        String langCd = resolveLangCd(acceptLanguage);
        return ApiResponse.ok(authService.login(req, ip, langCd));
    }

    /**
     * Current-session snapshot — called by the frontend on app load to
     * rehydrate auth state from the server of record rather than trusting
     * whatever is sitting in {@code localStorage}. Protected by the global
     * {@code anyRequest().authenticated()} rule in SecurityConfig: missing
     * or invalid tokens never reach this handler.
     */
    @GetMapping("/me")
    public ApiResponse<MeResponse> me() {
        return ApiResponse.ok(authService.me());
    }

    /**
     * Sprint 0 logout: JWT is stateless, so the frontend clears the token
     * locally and calls this endpoint for symmetry + audit. No blacklist,
     * no refresh-token revocation — those are deferred.
     */
    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        return ApiResponse.ok("Logout successful", null);
    }

    /**
     * Self-service password change for the logged-in user. Protected by the
     * global {@code anyRequest().authenticated()} rule — the JWT filter has
     * already bound the caller into {@code TenantContext}, so no user id is
     * accepted from the body (the credential being changed is always the
     * caller's own). Port of legacy {@code comm_0400_001_u001} /
     * {@code comm_0401_001}.
     */
    @PostMapping("/change-password")
    public ApiResponse<Void> changePassword(
            @Valid @RequestBody ChangePasswordRequest req,
            HttpServletRequest servletReq) {
        changePasswordService.changePassword(req, clientIp(servletReq));
        return ApiResponse.ok("Password changed", null);
    }

    private String clientIp(HttpServletRequest req) {
        String fwd = req.getHeader("X-Forwarded-For");
        if (fwd != null && !fwd.isBlank()) {
            int comma = fwd.indexOf(',');
            return (comma > 0 ? fwd.substring(0, comma) : fwd).trim();
        }
        return req.getRemoteAddr();
    }

    /**
     * Map Accept-Language to one of the four WeCMS codes: EN, KO, KM, VI.
     * Fallback is {@code EN} — matches the legacy default.
     */
    private String resolveLangCd(String acceptLanguage) {
        if (acceptLanguage == null || acceptLanguage.isBlank()) return DEFAULT_LANG;
        String primary = acceptLanguage.split(",")[0].trim().split("-")[0].toLowerCase(Locale.ROOT);
        return switch (primary) {
            case "ko"           -> "KO";
            case "km"           -> "KM";
            case "vi"           -> "VI";
            case "en", ""       -> "EN";
            default             -> DEFAULT_LANG;
        };
    }
}
