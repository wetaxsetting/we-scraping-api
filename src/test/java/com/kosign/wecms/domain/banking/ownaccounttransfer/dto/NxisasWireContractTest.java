package com.kosign.wecms.domain.banking.ownaccounttransfer.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kosign.wecms.config.JacksonConfig;
import com.kosign.wecms.domain.banking.ownaccounttransfer.dto.request.NxisasCertAcctListRequest;
import com.kosign.wecms.domain.banking.ownaccounttransfer.dto.request.NxisasDecryptRequest;
import com.kosign.wecms.domain.banking.ownaccounttransfer.dto.request.NxisasLoggingRequest;
import com.kosign.wecms.domain.banking.ownaccounttransfer.dto.response.NxisasCertAcctListResponse;
import com.kosign.wecms.domain.banking.ownaccounttransfer.dto.response.NxisasDecryptResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the JSON key names on the {@code /nxisas/*} wire contract.
 *
 * <p>Why this test exists: on 2026-08-18 four of these DTOs were found to have
 * UPPER_SNAKE record components with no {@code @JsonProperty}. The global
 * {@code PropertyNamingStrategies.SNAKE_CASE} strategy ({@code JacksonConfig})
 * therefore renamed them on the wire, and because Spring Boot disables
 * {@code FAIL_ON_UNKNOWN_PROPERTIES} the mismatch failed <b>silently with an
 * HTTP 200</b> — {@code /nxisas/decrypt} answered {@code {"nxisas_data": null}}
 * and {@code /nxisas/logging} wrote no audit row at all. Nothing in the build
 * caught it; three separate Javadocs asserted a wire shape no code implemented.
 *
 * <p>The mapper below is built from the real {@link JacksonConfig} customizer
 * rather than a hand-rolled {@code SNAKE_CASE} mapper, so this test tracks the
 * production strategy instead of duplicating it.
 *
 * <p>Two different rules are pinned, deliberately:
 * <ul>
 *   <li><b>lowercase snake_case</b> for {@code /nxisas/decrypt} — the caller is
 *       the refactor's own frontend, so the project-wide wire convention
 *       applies.</li>
 *   <li><b>UPPER_SNAKE</b> for {@code /nxisas/logging} and
 *       {@code /nxisas/cert-acct-list} — the caller is the vendor NXiSAS bundle,
 *       which posts and reads those literal keys. Same exemption the
 *       {@code nxisas/gateway/} DTOs carry.</li>
 * </ul>
 */
class NxisasWireContractTest {

    /** The production wire mapper — same naming strategy the running app uses. */
    private static ObjectMapper wireMapper() {
        Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();
        new JacksonConfig().snakeCaseCustomizer().customize(builder);
        return builder.build();
    }

    private final ObjectMapper wire = wireMapper();

    @Test
    @DisplayName("decrypt request binds the lowercase keys the frontend sends")
    void decryptRequest_bindsLowercaseSnakeCase() throws Exception {
        String body = """
                {"nxisas_data":"[{\\"Job\\":\\"BALANCE\\"}]",
                 "nxisas_uid":"uid-123",
                 "nxisas_action":"action-456"}
                """;

        NxisasDecryptRequest req = wire.readValue(body, NxisasDecryptRequest.class);

        assertThat(req.nxisasData()).isEqualTo("[{\"Job\":\"BALANCE\"}]");
        assertThat(req.nxisasUid()).isEqualTo("uid-123");
        assertThat(req.nxisasAction()).isEqualTo("action-456");
    }

    @Test
    @DisplayName("decrypt response emits nxisas_data, the key the frontend reads")
    void decryptResponse_emitsLowercaseSnakeCase() throws Exception {
        JsonNode json = wire.valueToTree(new NxisasDecryptResponse("payload"));

        assertThat(json.has("nxisas_data")).isTrue();
        assertThat(json.get("nxisas_data").asText()).isEqualTo("payload");
    }

    @Test
    @DisplayName("logging request binds JSON_STR verbatim — the vendor bundle's key")
    void loggingRequest_bindsUpperSnakeFromBundle() throws Exception {
        // nxisas_v2.js:2310 — _input['JSON_STR'] = JSON.stringify(_v2nxisas.orgObj)
        NxisasLoggingRequest req =
                wire.readValue("{\"JSON_STR\":\"[{\\\"Job\\\":\\\"LOGIN\\\"}]\"}",
                        NxisasLoggingRequest.class);

        assertThat(req.JSON_STR())
                .as("a null here means no NXSIAS_MODULE_TRAN_LOG_HIS row is written")
                .isEqualTo("[{\"Job\":\"LOGIN\"}]");
    }

    @Test
    @DisplayName("cert-acct-list request binds SEQ_NO verbatim — the vendor bundle's key")
    void certAcctListRequest_bindsUpperSnakeFromBundle() throws Exception {
        // nxisas_v2.js:1630 — input.SEQ_NO = seq
        NxisasCertAcctListRequest req =
                wire.readValue("{\"SEQ_NO\":\"42\"}", NxisasCertAcctListRequest.class);

        assertThat(req.seqNo())
                .as("a null here is rejected by @NotBlank as WBE_VALIDATION")
                .isEqualTo("42");
    }

    @Test
    @DisplayName("cert-acct-list response emits the UPPER keys the vendor bundle reads")
    void certAcctListResponse_emitsUpperSnakeForBundle() {
        JsonNode json = wire.valueToTree(new NxisasCertAcctListResponse(
                "cert", "pw", "pw2", "pw3", "7"));

        assertThat(json.has("CERT_INFO_CON")).isTrue();
        assertThat(json.has("CERT_INFO_DETL_CON")).isTrue();
        assertThat(json.has("CERT_INFO_DETL_CON2")).isTrue();
        assertThat(json.has("CERT_INFO_DETL_CON3")).isTrue();
        assertThat(json.has("NXISAS_GB_SEQ_NO")).isTrue();
        assertThat(json.get("CERT_INFO_DETL_CON").asText()).isEqualTo("pw");
    }
}
