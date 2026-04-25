package com.kosign.wecms.common.security;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Hybrid password encoder used by the login flow.
 *
 * <p><b>Write path:</b> new passwords are always BCrypt-encoded
 * (strength 10) via {@link BCryptPasswordEncoder}. BCrypt is the Spring
 * Security default — salted per-user and tunable work factor.
 *
 * <p><b>Read path:</b> {@link #matches(CharSequence, String)} accepts
 * either a BCrypt hash ({@code $2a$..} / {@code $2b$..} / {@code $2y$..})
 * or a legacy JEX SHA-256 base64 hash (as produced by
 * {@code public.hash()} in the legacy DB). This lets rows copied directly
 * from wecmsvndevdb authenticate without a migration.
 *
 * <p><b>Upgrade:</b> {@link #upgradeEncoding(String)} returns {@code true}
 * whenever the stored hash is NOT BCrypt. The {@code AuthService} checks
 * this after a successful {@code matches()} and rewrites the stored
 * password with a fresh BCrypt hash — so legacy hashes are phased out
 * on first login without a bulk migration.
 */
public final class WecmsPasswordEncoder implements PasswordEncoder {

    private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder(10);
    private final LegacyHashPasswordEncoder legacy = new LegacyHashPasswordEncoder();

    @Override
    public String encode(CharSequence raw) {
        return bcrypt.encode(raw);
    }

    @Override
    public boolean matches(CharSequence raw, String stored) {
        if (stored == null || stored.isEmpty()) return false;
        if (isBcrypt(stored)) return bcrypt.matches(raw, stored);
        return legacy.matches(raw, stored);
    }

    @Override
    public boolean upgradeEncoding(String stored) {
        return stored != null && !stored.isEmpty() && !isBcrypt(stored);
    }

    private static boolean isBcrypt(String s) {
        return s.startsWith("$2a$") || s.startsWith("$2b$") || s.startsWith("$2y$");
    }
}
