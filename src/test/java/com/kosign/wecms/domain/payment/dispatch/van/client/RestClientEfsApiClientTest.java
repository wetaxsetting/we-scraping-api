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

    /** Defaults seed the six endpoint paths + 3s/20s timeouts. */
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
