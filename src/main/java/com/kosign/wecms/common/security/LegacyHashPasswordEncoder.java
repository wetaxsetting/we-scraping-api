package com.kosign.wecms.common.security;

import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * Replicates the legacy JEX WeCMS password hash verbatim.
 *
 * <p>Legacy stored {@code USER_INFM.USER_PW} values are produced by the
 * PostgreSQL SQL function {@code public.hash(txt text)} found in
 * wecmsvndevdb:
 *
 * <pre>
 * CREATE OR REPLACE FUNCTION public.hash(txt text)
 *   RETURNS text LANGUAGE sql IMMUTABLE STRICT
 *   AS $$ select encode(public.digest($1, 'sha256'), 'base64') $$;
 * </pre>
 *
 * Equivalent Java: {@code Base64.encode(SHA-256(UTF-8 bytes of raw))}.
 * Output is always 44 characters (32 bytes × 4/3, padded). Unsalted and
 * deterministic — same plaintext always produces the same hash.
 *
 * <p>This encoder is the single write path for {@code USER_INFM.USER_PW}:
 * {@link WecmsPasswordEncoder#encode} delegates here so every password the
 * Spring app persists is the legacy {@code public.hash()} SHA-256 base64
 * value — never BCrypt — keeping the live JEX system able to authenticate
 * the same credential (coexistence rule, 2026-06-10). Login is verify-only:
 * there is <b>no</b> transparent SHA-256&nbsp;&rarr;&nbsp;BCrypt upgrade and
 * <b>no</b> automatic rewrite of {@code USER_PW} on successful login. (The
 * earlier upgrade behaviour described in pre-2026-06-10 revisions was
 * removed because it mutated live data and broke JEX login.)
 */
public final class LegacyHashPasswordEncoder implements PasswordEncoder {

    @Override
    public String encode(CharSequence raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(raw.toString().getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandated on every Java platform.
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    @Override
    public boolean matches(CharSequence raw, String stored) {
        if (raw == null || stored == null || stored.isEmpty()) return false;
        return constantTimeEquals(encode(raw), stored);
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) return false;
        int diff = 0;
        for (int i = 0; i < a.length(); i++) diff |= a.charAt(i) ^ b.charAt(i);
        return diff == 0;
    }
}
