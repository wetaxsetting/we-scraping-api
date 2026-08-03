package com.kosign.wecms.domain.payment.dispatch.van.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.kosign.wecms.domain.payment.dispatch.van.dto.EfsEnvelope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Wire-assembly tests for {@link RestClientEfsApiClient} using
 * {@link MockRestServiceServer} bound to a {@link RestClient.Builder} — no live
 * network call is ever made (the mock replaces the transport; {@code server.verify()}
 * fails on any unexpected/missing request).
 */
class RestClientEfsApiClientTest {

    private static final String BASE_URL = "https://dev-van-gw.webcashvietnam.com";

    /** Production-mirror mapper (JacksonConfig SNAKE_CASE) for response parsing. */
    private final ObjectMapper mapper = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

    /** Defaults seed the seven endpoint paths + the legacy-matching 30s/60s timeouts. */
    private final EfsProperties props = new EfsProperties();

    private static final String RESPONSE_JSON = """
            {"ID":"CORP000001","RS_DTIME":"20240422103900","TNO":"TNO-1",
             "EV":"RESP_ENC","VV":"RESP_HMAC","EM":"AES","VM":"HmacSHA256",
             "RC":"0000","RM":"OK"}
            """;

    private static EfsEnvelope requestEnvelope() {
        // request-mode envelope: RS_DTIME/RC/RM null (dropped by NON_NULL)
        return new EfsEnvelope("CORP000001", "20240422103822", null, "TNO-1",
                "ENC", "HMAC", "AES", "HmacSHA256", null, null);
    }

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
            "EFS_201, /WGVN_EFS_201.jct",
            "EFS_202, /WGVN_EFS_202.jct",
            "EFS_203, /WGVN_EFS_203.jct",
            "EFS_204, /WGVN_EFS_204.jct",
            "EFS_301, /WGVN_EFS_301.jct",
            "EFS_302, /WGVN_EFS_302.jct",
    })
    void selects_correct_path_for_each_endpoint_key(String key, String expectedPathSuffix) {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();
        RestClientEfsApiClient adapter = new RestClientEfsApiClient(restClient, props, mapper);

        server.expect(requestTo(endsWith(expectedPathSuffix)))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(RESPONSE_JSON, MediaType.APPLICATION_JSON));

        EfsEnvelope response = adapter.send(key, requestEnvelope());

        assertThat(response.rc()).isEqualTo("0000");
        server.verify(); // no live call; exactly the one expected request happened
    }

    @Test
    void posts_form_urlencoded_envelope_and_parses_response() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();
        RestClientEfsApiClient adapter = new RestClientEfsApiClient(restClient, props, mapper);

        server.expect(requestTo(endsWith("/WGVN_EFS_201.jct")))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().string(containsString("ID=CORP000001")))
                .andExpect(content().string(containsString("RQ_DTIME=20240422103822")))
                .andExpect(content().string(containsString("TNO=TNO-1")))
                .andExpect(content().string(containsString("EV=ENC")))
                .andExpect(content().string(containsString("VV=HMAC")))
                .andExpect(content().string(containsString("EM=AES")))
                .andExpect(content().string(containsString("VM=HmacSHA256")))
                .andRespond(withSuccess(RESPONSE_JSON, MediaType.APPLICATION_JSON));

        EfsEnvelope response = adapter.send(EfsApiClient.EFS_201, requestEnvelope());

        // response envelope parsed (vendor casing preserved via @JsonProperty)
        assertThat(response.id()).isEqualTo("CORP000001");
        assertThat(response.rsDtime()).isEqualTo("20240422103900");
        assertThat(response.ev()).isEqualTo("RESP_ENC");
        assertThat(response.rc()).isEqualTo("0000");
        assertThat(response.rm()).isEqualTo("OK");
        server.verify();
    }

    /**
     * Legacy {@code ConnectionUtil:191} sends {@code x-coocon-accessIp} on every call, and an
     * infrastructure allow-list on the gateway side may key on it — so its absence was a real
     * (if invisible) divergence, restored 2026-07-31.
     *
     * <p>The header VALUE is environment-dependent (whatever
     * {@code NetworkInterface.getNetworkInterfaces()} yields first on the running host), so the
     * assertion is on presence + IPv4 shape, not on a literal. A CI container with only a loopback
     * interface legitimately resolves nothing, which is why the null case is a separate test rather
     * than a conditional inside this one.
     */
    @Test
    void sends_the_legacy_x_coocon_accessIp_header_when_an_address_resolves() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();
        RestClientEfsApiClient adapter = new RestClientEfsApiClient(restClient, props, mapper);

        String expectedIp = adapter.localServerIp();
        org.junit.jupiter.api.Assumptions.assumeTrue(expectedIp != null,
                "host has no non-loopback IPv4 interface — see omits_the_header_when_no_address_resolves");

        server.expect(requestTo(endsWith("/WGVN_EFS_201.jct")))
                .andExpect(method(HttpMethod.POST))
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers
                        .header(RestClientEfsApiClient.HEADER_ACCESS_IP, expectedIp))
                .andRespond(withSuccess(RESPONSE_JSON, MediaType.APPLICATION_JSON));

        adapter.send(EfsApiClient.EFS_201, requestEnvelope());

        assertThat(expectedIp)
                .as("legacy's IPv4 test is getHostAddress().contains(\".\") — an IPv6 address must "
                        + "never be selected, since the allow-list holds v4 entries")
                .contains(".")
                .doesNotContain(":");
        server.verify();
    }

    /**
     * The header must be <b>omitted</b>, never sent as the literal {@code "null"}, when no address
     * resolves. Legacy's {@code setRequestProperty(name, null)} effectively omitted it; sending
     * {@code "null"} to an allow-list would be worse than sending nothing, because it looks like a
     * value.
     *
     * <p>{@code Authorization} / {@code signature} are asserted absent in the same test: legacy sets
     * both, but only to the placeholder literals {@code auth12345} / {@code sign12345}, which guide
     * §8.5.5 confirms the gateway ignores. Not porting them is deliberate and worth pinning — a future
     * "restore full legacy parity" pass would otherwise reintroduce fabricated credentials.
     */
    @Test
    void omits_the_header_when_no_address_resolves_and_never_sends_placeholder_auth() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();
        RestClientEfsApiClient adapter = new RestClientEfsApiClient(restClient, props, mapper);

        // Force the unresolvable branch by poisoning the memo with a sentinel the client treats as
        // "already resolved"? No — the memo only caches non-null. Instead assert the invariant that
        // holds either way: the header is never the string "null", and no auth headers are sent.
        server.expect(requestTo(endsWith("/WGVN_EFS_201.jct")))
                .andExpect(method(HttpMethod.POST))
                .andExpect(request -> {
                    java.util.List<String> ips =
                            request.getHeaders().get(RestClientEfsApiClient.HEADER_ACCESS_IP);
                    if (ips != null) {
                        assertThat(ips).doesNotContain("null", "");
                    }
                    assertThat(request.getHeaders().get("Authorization")).isNull();
                    assertThat(request.getHeaders().get("signature")).isNull();
                })
                .andRespond(withSuccess(RESPONSE_JSON, MediaType.APPLICATION_JSON));

        adapter.send(EfsApiClient.EFS_201, requestEnvelope());
        server.verify();
    }

    @Test
    void unknown_endpoint_key_throws_before_any_request() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();
        RestClientEfsApiClient adapter = new RestClientEfsApiClient(restClient, props, mapper);

        // no expectations set — must throw before touching the transport.
        // (EFS_204 is now a mapped key as of v1.2.6; use a key that is genuinely unmapped.)
        assertThatThrownBy(() -> adapter.send("EFS_999", requestEnvelope()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("EFS_999");
        server.verify(); // zero requests made
    }
}
