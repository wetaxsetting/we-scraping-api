package com.kosign.wecms.domain.payment.dispatch.van.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kosign.wecms.domain.payment.dispatch.van.dto.EfsEnvelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

/**
 * Real EFS gateway adapter — Spring {@link RestClient}, form-urlencoded POST,
 * mirroring the {@code NxisasGatewayClient} convention. Active only when
 * {@code wecms.dispatch.efs.enabled=true} (DEV/SIT).
 *
 * <p>Sends the request envelope as {@code application/x-www-form-urlencoded}
 * fields {@code ID, RQ_DTIME, TNO, EV, VV, EM, VM} (PDF §4) and parses the JSON
 * response envelope ({@code ID, RS_DTIME, TNO, EV, VV, EM, VM, RC, RM}).
 *
 * <p><b>Logging discipline:</b> never logs {@code EV}, {@code VV}, the decrypted
 * payload, or the {@code API_KEY}. Only the endpoint key, resolved path, and the
 * (non-secret) {@code RC} are logged.
 *
 * <p>Out of scope (later phases): {@code EV}/{@code VV} are built/verified by the
 * service layer via {@code EfsCryptoService} (B2 key); this adapter is pure transport.
 */
@Component
@ConditionalOnProperty(prefix = "wecms.dispatch.efs", name = "enabled", havingValue = "true")
public class RestClientEfsApiClient implements EfsApiClient {

    private static final Logger log = LoggerFactory.getLogger(RestClientEfsApiClient.class);

    private final RestClient efsRestClient;
    private final EfsProperties props;
    private final ObjectMapper objectMapper;

    public RestClientEfsApiClient(@Qualifier("efsRestClient") RestClient efsRestClient,
                                  EfsProperties props,
                                  ObjectMapper objectMapper) {
        this.efsRestClient = efsRestClient;
        this.props = props;
        this.objectMapper = objectMapper;
    }

    @Override
    public EfsEnvelope send(String endpointKey, EfsEnvelope request) {
        String path = resolvePath(endpointKey);
        MultiValueMap<String, String> form = toForm(request);

        String body = efsRestClient.post()
                .uri(path)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(String.class);

        EfsEnvelope response = parse(body);
        // Never log EV/VV/payload/API_KEY — only the endpoint, path, and RC.
        log.debug("EFS send endpointKey={} path={} rc={}", endpointKey, path,
                response == null ? null : response.rc());
        return response;
    }

    private String resolvePath(String endpointKey) {
        String path = props.getEndpoints() == null ? null : props.getEndpoints().get(endpointKey);
        if (!StringUtils.hasText(path)) {
            throw new IllegalArgumentException("Unmapped EFS endpoint key: " + endpointKey
                    + " (configure wecms.dispatch.efs.endpoints).");
        }
        return path;
    }

    /** Request-mode form fields only (never RC/RM/RS_DTIME); EM/VM default to AES/HmacSHA256. */
    private MultiValueMap<String, String> toForm(EfsEnvelope req) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        putIfPresent(form, "ID", req.id());
        putIfPresent(form, "RQ_DTIME", req.rqDtime());
        putIfPresent(form, "TNO", req.tno());
        putIfPresent(form, "EV", req.ev());
        putIfPresent(form, "VV", req.vv());
        form.add("EM", StringUtils.hasText(req.em()) ? req.em() : EfsEnvelope.EM_AES);
        form.add("VM", StringUtils.hasText(req.vm()) ? req.vm() : EfsEnvelope.VM_HMAC_SHA256);
        return form;
    }

    private void putIfPresent(MultiValueMap<String, String> form, String key, String value) {
        if (value != null) {
            form.add(key, value);
        }
    }

    private EfsEnvelope parse(String body) {
        if (!StringUtils.hasText(body)) {
            throw new IllegalStateException("Empty EFS response body");
        }
        try {
            return objectMapper.readValue(body, EfsEnvelope.class);
        } catch (Exception e) {
            // Do not include the raw body (may carry EV/VV) in the message.
            throw new IllegalStateException("Failed to parse EFS response envelope", e);
        }
    }
}
