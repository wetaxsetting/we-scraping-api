package com.kosign.wecms.common.security;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Coexistence password encoder used by the login + user-management flows.
 *
 * <p><b>Coexistence rule (2026-06-10).</b> The refactored Spring app runs
 * <em>alongside</em> the live JEX system against the same database. Both
 * systems must be able to authenticate the same {@code USER_INFM} row, so
 * every password WeCMS writes MUST be in the legacy
 * {@code public.hash()} format — unsalted {@code Base64(SHA-256(utf8(raw)))}
 * (44 chars). Writing BCrypt would lock the user out of legacy JEX (which
 * computes SHA-256 and compares) and is therefore forbidden on every write
 * path.
 *
 * <p><b>Write path</b> ({@link #encode(CharSequence)}): always emits the
 * legacy SHA-256 hash via {@link LegacyHashPasswordEncoder}. This is the
 * single point that guarantees no Spring write path (login, Settings &gt;
 * Users, change-password) can accidentally persist a BCrypt value.
 *
 * <p><b>Read path</b> ({@link #matches(CharSequence, String)}): primarily
 * verifies against the legacy SHA-256 hash. BCrypt hashes are <em>also</em>
 * accepted, purely as tolerance for any local / dev rows that an earlier
 * build's (now-removed) transparent-upgrade behaviour may have already
 * rewritten to BCrypt — live DEV/UAT/PROD rows are 100% SHA-256. No new
 * BCrypt value is ever written.
 *
 * <p><b>No upgrade / no write-back.</b> {@link #upgradeEncoding(String)}
 * always returns {@code false}. Login is verify-only and never mutates
 * {@code USER_INFM.USER_PW}. Password migration is explicitly out of scope.
 */
public final class WecmsPasswordEncoder implements PasswordEncoder {

    /** Read-path tolerance only — never used to write a new hash. */
    private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder(10);
    private final LegacyHashPasswordEncoder legacy = new LegacyHashPasswordEncoder();

    @Override
    public String encode(CharSequence raw) {
        // Coexistence: WeCMS writes the legacy public.hash() format so the
        // live JEX system can still authenticate the same credential.
        return legacy.encode(raw);
    }

    @Override
    public boolean matches(CharSequence raw, String stored) {
        if (stored == null || stored.isEmpty()) return false;
        // Tolerate any pre-existing BCrypt rows (local/dev only); the
        // normal path is the legacy SHA-256 comparison.
        if (isBcrypt(stored)) return bcrypt.matches(raw, stored);
        return legacy.matches(raw, stored);
    }

    @Override
    public boolean upgradeEncoding(String stored) {
        // Never upgrade. Login is verify-only under the coexistence rule —
        // re-encoding would replace the legacy hash and break JEX login.
        return false;
    }

    private static boolean isBcrypt(String s) {
        return s.startsWith("$2a$") || s.startsWith("$2b$") || s.startsWith("$2y$");
    }
}
