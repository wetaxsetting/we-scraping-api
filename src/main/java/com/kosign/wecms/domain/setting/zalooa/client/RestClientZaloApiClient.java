package com.kosign.wecms.domain.setting.zalooa.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Live {@link ZaloApiClient}. Active only when {@code wecms.zalo.enabled=true}.
 *
 * <p><b>Responses are read as {@code String} and parsed here, not bound by the
 * message converters.</b> Two reasons, both learned from the reference
 * implementation:
 * <ol>
 *   <li>Zalo's token endpoint has been observed answering
 *       {@code Content-Type: text/json}, which no default Spring converter
 *       claims. WeBill365 works around it by registering an extra
 *       {@code MappingJackson2HttpMessageConverter} for that media type; reading
 *       the raw body sidesteps the whole class of problem instead.</li>
 *   <li>{@code ZALO_SEND_HIS.RES_DATA} must store what Zalo actually said. If the
 *       body were bound to a type first, anything the type does not model would
 *       be lost before it could be logged.</li>
 * </ol>
 *
 * <p><b>Non-2xx does not throw at the transport layer.</b> The default RestClient
 * error handler is suppressed so the body of a failed response is still readable
 * and can be surfaced (and, for sends, persisted). What each method does with it
 * differs and is documented on the method.
 *
 * <p><b>Logging discipline.</b> Access tokens and recipient phone numbers are
 * masked. The reference implementation logs the bare access token and the full
 * payload including the phone number at {@code debug}
 * ({@code webill365-vn-api/docs/features/zalo-oa-integration.md} §4.3) — that is
 * the same category of leak F-06 (SIT logging hygiene) closed in this repo, so it
 * is not carried over.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "wecms.zalo", name = "enabled", havingValue = "true")
public class RestClientZaloApiClient implements ZaloApiClient {

    /** Zalo authenticates token calls with the app secret in a header, not a form field. */
    private static final String HEADER_SECRET_KEY = "secret_key";

    /** Zalo takes the bearer value in a bare header of this name — not {@code Authorization}. */
    private static final String HEADER_ACCESS_TOKEN = "access_token";

    private static final String GRANT_AUTHORIZATION_CODE = "authorization_code";
    private static final String GRANT_REFRESH_TOKEN = "refresh_token";

    private final RestClient zaloRestClient;
    private final ZaloProperties props;
    private final ObjectMapper objectMapper;

    public RestClientZaloApiClient(@Qualifier("zaloRestClient") RestClient zaloRestClient,
                                   ZaloProperties props,
                                   ObjectMapper objectMapper) {
        this.zaloRestClient = zaloRestClient;
        this.props = props;
        this.objectMapper = objectMapper;
    }

    @Override
    public ZaloTokenGrant exchangeAuthorizationCode(String code) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("app_id", props.getAppId());
        form.add("grant_type", GRANT_AUTHORIZATION_CODE);
        form.add("code", code);
        return requestToken(form, GRANT_AUTHORIZATION_CODE);
    }

    @Override
    public ZaloTokenGrant refreshAccessToken(String refreshToken) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("app_id", props.getAppId());
        form.add("grant_type", GRANT_REFRESH_TOKEN);
        form.add("refresh_token", refreshToken);
        return requestToken(form, GRANT_REFRESH_TOKEN);
    }

    /**
     * Both grants hit the same endpoint with the same headers and differ only in
     * the form body, so they share one path.
     *
     * <p>A token call that does not yield a complete pair is always an exception:
     * there is nothing partial worth keeping, and persisting half a grant would
     * strand the connection in a state only a human could repair.
     */
    private ZaloTokenGrant requestToken(MultiValueMap<String, String> form, String grantType) {
        ResponseEntity<String> response;
        try {
            response = zaloRestClient.post()
                    .uri(props.getTokenUrl())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .header(HttpHeaders.ACCEPT, MediaType.ALL_VALUE)
                    .header(HEADER_SECRET_KEY, props.getAppSecret())
                    .body(form)
                    .retrieve()
                    .onStatus(status -> true, (req, res) -> { /* handled below, body needed */ })
                    .toEntity(String.class);
        } catch (RestClientException ex) {
            throw new ZaloApiException(
                    "Zalo token call failed (grant_type=" + grantType + "): " + ex.getMessage(), null, ex);
        }

        String body = response.getBody();
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new ZaloApiException("Zalo token call returned HTTP "
                    + response.getStatusCode().value() + " (grant_type=" + grantType + ")");
        }

        JsonNode json = parse(body, "token");
        // Zalo signals refusal in the body with a non-zero `error` while still
        // answering 200 — checked before reading the token fields, which are
        // absent on that path.
        JsonNode error = json.get("error");
        if (error != null && error.asInt() != ZaloSendOutcome.SUCCESS_CODE) {
            JsonNode name = json.get("error_name");
            throw new ZaloApiException("Zalo refused the " + grantType + " grant: "
                    + (name == null ? "unknown reason" : name.asText()), error.asInt());
        }

        String accessToken = text(json, "access_token");
        String refreshToken = text(json, "refresh_token");
        JsonNode expiresIn = json.get("expires_in");
        if (accessToken == null || refreshToken == null) {
            throw new ZaloApiException("Zalo " + grantType
                    + " response is missing access_token and/or refresh_token");
        }
        // Zalo sends expires_in as a JSON string; asLong handles both forms. A
        // missing or non-positive TTL is fatal rather than defaulted: a stored
        // expiry of zero would make every send believe the token is stale and
        // refresh again, and each refresh rotates (and invalidates) the refresh
        // token, so guessing here would burn the connection instead of failing it.
        long ttl = expiresIn == null ? 0L : expiresIn.asLong(0L);
        if (ttl <= 0) {
            throw new ZaloApiException("Zalo " + grantType
                    + " response carries no usable expires_in");
        }

        log.info("feature=zalo-oa token grant ok grant_type={} expires_in={}s", grantType, ttl);
        return new ZaloTokenGrant(accessToken, refreshToken, ttl);
    }

    @Override
    public ZaloSendOutcome sendTemplateMessage(String accessToken, ZaloSendCommand command) {
        ResponseEntity<String> response;
        try {
            response = zaloRestClient.post()
                    .uri(props.getNotifyUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HEADER_ACCESS_TOKEN, accessToken)
                    .body(command)
                    .retrieve()
                    .onStatus(status -> true, (req, res) -> { /* handled below, body needed */ })
                    .toEntity(String.class);
        } catch (RestClientException ex) {
            // No response at all — nothing of Zalo's to record, so this is a fault.
            throw new ZaloApiException("Zalo send call failed for template "
                    + command.templateId() + ": " + ex.getMessage(), null, ex);
        }

        String body = response.getBody();
        if (!response.getStatusCode().is2xxSuccessful()) {
            // Surfaced as an outcome, not an exception: the caller must still write
            // a send-history row, and the HTTP status is the most useful thing we
            // know about the attempt.
            int status = response.getStatusCode().value();
            log.warn("feature=zalo-oa send got HTTP {} template={} phone={}",
                    status, command.templateId(), maskPhone(command.phone()));
            return new ZaloSendOutcome(status, "Zalo returned HTTP " + status, body);
        }

        JsonNode json = parse(body, "send");
        JsonNode error = json.get("error");
        if (error == null) {
            // Honest failure rather than the reference implementation's NPE, which
            // its caller records as a bogus HTTP 500 with the NPE's message.
            throw new ZaloApiException("Zalo send response has no 'error' field: " + body);
        }
        JsonNode message = json.get("message");
        ZaloSendOutcome outcome = new ZaloSendOutcome(
                error.asInt(),
                message == null ? "" : message.asText(),
                body);

        if (outcome.success()) {
            log.info("feature=zalo-oa send ok template={} phone={} tracking_id={}",
                    command.templateId(), maskPhone(command.phone()), command.trackingId());
        } else {
            log.warn("feature=zalo-oa send rejected error={} template={} phone={} message={}",
                    outcome.errorCode(), command.templateId(),
                    maskPhone(command.phone()), outcome.message());
        }
        return outcome;
    }

    private JsonNode parse(String body, String what) {
        if (body == null || body.isBlank()) {
            throw new ZaloApiException("Zalo " + what + " response body is empty");
        }
        try {
            return objectMapper.readTree(body);
        } catch (Exception ex) {
            throw new ZaloApiException("Zalo " + what + " response is not valid JSON", null, ex);
        }
    }

    private static String text(JsonNode json, String field) {
        JsonNode node = json.get(field);
        if (node == null || node.isNull()) {
            return null;
        }
        String value = node.asText();
        return value == null || value.isBlank() ? null : value;
    }

    /**
     * Keep the country prefix and last two digits so a support engineer can
     * correlate a log line with a customer report, without the log becoming a
     * list of customer phone numbers.
     */
    private static String maskPhone(String phone) {
        if (phone == null || phone.length() < 6) {
            return "***";
        }
        return phone.substring(0, 2) + "*".repeat(phone.length() - 4) + phone.substring(phone.length() - 2);
    }
}
