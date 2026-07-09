package com.kosign.wecms.config.haengsung;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link HaengsungOracleProperties} binding rules:
 * fail-fast when enabled with missing credentials, lenient when disabled,
 * and pool defaults applied.
 */
class HaengsungOraclePropertiesTest {

    @Test
    void disabled_allowsBlankCredentials() {
        HaengsungOracleProperties props = new HaengsungOracleProperties(
                false, null, null, null, null, null, null, null, null);

        assertThat(props.enabled()).isFalse();
        // Defaults still applied so the record is fully usable.
        assertThat(props.maxPoolSize()).isEqualTo(5);
        assertThat(props.minIdle()).isEqualTo(1);
        assertThat(props.connectionTimeoutMs()).isEqualTo(30_000L);
        assertThat(props.keepaliveMs()).isEqualTo(120_000L);
        assertThat(props.validationTimeoutMs()).isEqualTo(5_000L);
    }

    @Test
    void enabled_withFullCredentials_isAccepted() {
        HaengsungOracleProperties props = new HaengsungOracleProperties(
                true, "jdbc:oracle:thin:@//host:1521/DEV", "erp_user", "secret",
                null, null, null, null, null);

        assertThat(props.enabled()).isTrue();
        assertThat(props.url()).isEqualTo("jdbc:oracle:thin:@//host:1521/DEV");
        assertThat(props.maxPoolSize()).isEqualTo(5);
    }

    @Test
    void enabled_missingUrl_failsFast() {
        assertThatThrownBy(() -> new HaengsungOracleProperties(
                true, "  ", "erp_user", "secret", null, null, null, null, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HAENGSUNG_ORACLE_URL");
    }

    @Test
    void enabled_missingUsername_failsFast() {
        assertThatThrownBy(() -> new HaengsungOracleProperties(
                true, "jdbc:oracle:thin:@//host:1521/DEV", null, "secret",
                null, null, null, null, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HAENGSUNG_ORACLE_USERNAME");
    }

    @Test
    void enabled_missingPassword_failsFast() {
        assertThatThrownBy(() -> new HaengsungOracleProperties(
                true, "jdbc:oracle:thin:@//host:1521/DEV", "erp_user", "",
                null, null, null, null, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HAENGSUNG_ORACLE_PASSWORD");
    }

    @Test
    void failFastMessage_neverContainsCredentialValues() {
        assertThatThrownBy(() -> new HaengsungOracleProperties(
                true, null, "topsecret-user", "topsecret-pw", null, null, null, null, null))
                .hasMessageNotContaining("topsecret-user")
                .hasMessageNotContaining("topsecret-pw");
    }

    @Test
    void explicitPoolValues_arePreserved() {
        HaengsungOracleProperties props = new HaengsungOracleProperties(
                true, "jdbc:oracle:thin:@//host:1521/DEV", "u", "p",
                10, 2, 15_000L, 60_000L, 3_000L);

        assertThat(props.maxPoolSize()).isEqualTo(10);
        assertThat(props.minIdle()).isEqualTo(2);
        assertThat(props.connectionTimeoutMs()).isEqualTo(15_000L);
        assertThat(props.keepaliveMs()).isEqualTo(60_000L);
        assertThat(props.validationTimeoutMs()).isEqualTo(3_000L);
    }
}
