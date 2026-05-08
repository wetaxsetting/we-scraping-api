package com.kosign.wecms.common.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Write-only password hashing facade for new accounts.
 *
 * <p>Always emits BCrypt — no SHA-256, no legacy {@code public.hash()}.
 * Bootstrap and any other future write path must call this service so it's
 * physically impossible to write a legacy hash to {@code USER_INFM.USER_PW}
 * via new code.
 *
 * <p>Login reads continue to flow through {@link WecmsPasswordEncoder}
 * (the hybrid Spring Security {@code PasswordEncoder}) which still accepts
 * legacy hashes and transparently upgrades them on first successful login.
 * This split is intentional — see seahorse plan §5.6.
 */
@Service
@RequiredArgsConstructor
public class PasswordHashService {

    private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder(10);

    /** BCrypt-encode a raw password for a brand-new account. */
    public String encodeForNewUser(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("password must not be blank");
        }
        return bcrypt.encode(raw);
    }
}
