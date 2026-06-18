package com.kosign.wecms.domain.banking.nxisas;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Masking guarantees for the NXISAS debug-logging layer — nothing
 * sensitive reaches a log line in clear text.
 */
class NxisasMaskTest {

    private final ObjectMapper om = new ObjectMapper();

    @Test
    void acctKeepsOnlyLastFour() {
        assertThat(NxisasMask.acct("0011001234567")).isEqualTo("***4567");
        assertThat(NxisasMask.acct("1234")).isEqualTo("****");
        assertThat(NxisasMask.acct("")).isEqualTo("");
        assertThat(NxisasMask.acct(null)).isEqualTo("");
    }

    @Test
    void cardKeepsOnlyLastFour() {
        assertThat(NxisasMask.card("9704000011112222")).isEqualTo("***2222");
        assertThat(NxisasMask.card("12")).isEqualTo("****");
        assertThat(NxisasMask.card(null)).isEqualTo("");
    }

    @Test
    void jsonMasksAccountAndCardNumbersToLastFour() throws Exception {
        String raw = """
            { "Input": { "acctNum": "0011001234567", "cardNum": "9704000011112222" } }
            """;
        String masked = NxisasMask.json(om.readTree(raw));
        assertThat(masked).contains("***4567");      // account → last 4
        assertThat(masked).contains("***2222");      // card → last 4
        assertThat(masked).doesNotContain("0011001234567");
        assertThat(masked).doesNotContain("9704000011112222");
    }

    @Test
    void tokenKeepsFirstThreeAndLastThree() {
        assertThat(NxisasMask.token("abcdef123456xyz")).isEqualTo("abc***xyz");
        assertThat(NxisasMask.token("short")).isEqualTo("***");
        assertThat(NxisasMask.token(null)).isEqualTo("");
    }

    @Test
    void jsonRedactsSecretsAndMasksTokens() throws Exception {
        String raw = """
            {
              "Common": { "Auth_key": "supersecretkey", "SAS_SessionID": "abcdef123456xyz" },
              "Input": { "loginPWD": "p@ssw0rd", "res": "654321", "loginId": "alice" },
              "qna": { "Answer": "my-pet", "HiddenValue": "hv-token", "Question": "pet?" }
            }
            """;
        String masked = NxisasMask.json(om.readTree(raw));

        // Secrets fully redacted — never present in clear text.
        assertThat(masked).doesNotContain("supersecretkey");
        assertThat(masked).doesNotContain("p@ssw0rd");
        assertThat(masked).doesNotContain("654321");
        assertThat(masked).doesNotContain("my-pet");
        assertThat(masked).doesNotContain("hv-token");
        assertThat(masked).contains("***REDACTED***");

        // Session token partially masked, not redacted.
        assertThat(masked).contains("abc***xyz");
        assertThat(masked).doesNotContain("abcdef123456xyz");

        // Non-sensitive values pass through.
        assertThat(masked).contains("alice");
        assertThat(masked).contains("pet?");
    }
}
