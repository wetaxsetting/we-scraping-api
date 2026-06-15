package com.kosign.wecms.common.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the Java implementation to the exact output of the legacy PostgreSQL
 * {@code public.hash(txt text)} function. The reference value was taken
 * directly from wecmsvndevdb via MCP:
 *
 * <pre>
 *   SELECT hash('Password!1');  -- vm/e2euhU9d0JFSQ+PQSDOviptOlRnYD7KM0PekNYnU=
 * </pre>
 *
 * If this test ever fails, either the legacy function was changed or our
 * reimplementation drifted.
 */
class LegacyHashPasswordEncoderTest {

    private final LegacyHashPasswordEncoder encoder = new LegacyHashPasswordEncoder();

    @Test
    void encodeMatchesPostgresHashForKnownPassword() {
        String expected = "vm/e2euhU9d0JFSQ+PQSDOviptOlRnYD7KM0PekNYnU=";
        assertThat(encoder.encode("Password!1")).isEqualTo(expected);
        assertThat(encoder.encode("Password!1")).hasSize(44);
    }

    @Test
    void matchesReturnsTrueForMatchingLegacyHash() {
        String stored = "vm/e2euhU9d0JFSQ+PQSDOviptOlRnYD7KM0PekNYnU=";
        assertThat(encoder.matches("Password!1", stored)).isTrue();
    }

    @Test
    void matchesReturnsFalseForWrongPassword() {
        String stored = "vm/e2euhU9d0JFSQ+PQSDOviptOlRnYD7KM0PekNYnU=";
        assertThat(encoder.matches("different", stored)).isFalse();
    }

    @Test
    void matchesRejectsNullInputs() {
        assertThat(encoder.matches(null, "abc")).isFalse();
        assertThat(encoder.matches("abc", null)).isFalse();
        assertThat(encoder.matches("abc", "")).isFalse();
    }

    @Test
    void encodingIsDeterministic() {
        assertThat(encoder.encode("hello")).isEqualTo(encoder.encode("hello"));
    }

    /**
     * Coexistence rule (2026-06-10): {@link WecmsPasswordEncoder} WRITES the
     * legacy {@code public.hash()} SHA-256 value (never BCrypt) so the live
     * JEX system can authenticate the same {@code USER_INFM} row; it still
     * TOLERATES a BCrypt hash on read (for any pre-existing dev row) but
     * never upgrades. This pins all of that.
     */
    @Test
    void coexistenceEncoderWritesSha256AndToleratesBcryptOnRead() {
        WecmsPasswordEncoder encoder = new WecmsPasswordEncoder();

        // Write path → legacy SHA-256 Base64, NOT BCrypt.
        String written = encoder.encode("Password!1");
        assertThat(written).isEqualTo("vm/e2euhU9d0JFSQ+PQSDOviptOlRnYD7KM0PekNYnU=");
        assertThat(written).hasSize(44);
        assertThat(written)
                .doesNotStartWith("$2a$")
                .doesNotStartWith("$2b$")
                .doesNotStartWith("$2y$");

        // Read path → verifies the legacy SHA-256 hash, and never flags it
        // for upgrade (login is verify-only — no write-back).
        String legacyStored = "vm/e2euhU9d0JFSQ+PQSDOviptOlRnYD7KM0PekNYnU=";
        assertThat(encoder.matches("Password!1", legacyStored)).isTrue();
        assertThat(encoder.matches("wrong", legacyStored)).isFalse();
        assertThat(encoder.upgradeEncoding(legacyStored)).isFalse();

        // Read path → a pre-existing BCrypt hash (e.g. a dev row written by an
        // earlier build) is still accepted, and likewise never upgraded.
        String bcryptStored = new BCryptPasswordEncoder(10).encode("Password!1");
        assertThat(bcryptStored).startsWith("$2a$");
        assertThat(encoder.matches("Password!1", bcryptStored)).isTrue();
        assertThat(encoder.upgradeEncoding(bcryptStored)).isFalse();
    }
}
