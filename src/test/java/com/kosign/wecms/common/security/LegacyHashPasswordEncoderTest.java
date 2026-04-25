package com.kosign.wecms.common.security;

import org.junit.jupiter.api.Test;

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

    @Test
    void hybridEncoderAcceptsBothSchemes() {
        WecmsPasswordEncoder hybrid = new WecmsPasswordEncoder();

        // Legacy hash path
        String legacyStored = "vm/e2euhU9d0JFSQ+PQSDOviptOlRnYD7KM0PekNYnU=";
        assertThat(hybrid.matches("Password!1", legacyStored)).isTrue();
        assertThat(hybrid.upgradeEncoding(legacyStored)).isTrue();

        // BCrypt path — encode with hybrid, then verify round-trip
        String bcryptStored = hybrid.encode("Password!1");
        assertThat(bcryptStored).startsWith("$2a$");
        assertThat(hybrid.matches("Password!1", bcryptStored)).isTrue();
        assertThat(hybrid.upgradeEncoding(bcryptStored)).isFalse();
    }
}
