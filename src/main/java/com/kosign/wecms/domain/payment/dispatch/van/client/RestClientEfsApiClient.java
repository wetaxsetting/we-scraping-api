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

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;

/**
 * Real EFS gateway adapter — Spring {@link RestClient}, form-urlencoded POST,
 * mirroring the {@code NxisasGatewayClient} convention. Active only when
 * {@code wecms.dispatch.efs.enabled=true} (DEV/SIT).
 *
 * <p>Sends the request envelope as {@code application/x-www-form-urlencoded}
 * fields {@code ID, RQ_DTIME, TNO, EV, VV, EM, VM} (PDF §4) and parses the JSON
 * response envelope ({@code ID, RS_DTIME, TNO, EV, VV, EM, VM, RC, RM}).
 *
 * <h2>{@code x-coocon-accessIp} — restored from legacy</h2>
 * Legacy sends this header on <b>every</b> EFS call
 * ({@code util.ConnectionUtil.prepareAndSendRequest:191}), carrying the calling server's
 * local non-loopback IPv4 as resolved by {@code ConnectionUtil.getLocalServerIp():268-290}.
 * The vendor guide does not require it, so it was initially dropped here — but an
 * infrastructure allow-list on the gateway side may key on it, and the cost of sending a
 * header the gateway ignores is nil against the cost of being refused by an allow-list that
 * does not. Restored (see {@link #localServerIp()}); omitted rather than sent as
 * {@code "null"} when no address resolves, which is what legacy's
 * {@code setRequestProperty(name, null)} effectively did.
 *
 * <p><b>{@code Authorization} and {@code signature} are deliberately NOT sent</b>, even
 * though legacy sets them at {@code ConnectionUtil:192-193}. Their values are the hardcoded
 * placeholder literals {@code "auth12345"} / {@code "sign12345"}
 * ({@code ConnectionUtil:18-19}) — not credentials — and guide §8.5.5 confirms the gateway
 * ignores both. Sending fabricated auth material would be worse than sending nothing: it
 * would read as a real credential to anyone auditing the traffic. Authentication on this
 * channel is the {@code EV}/{@code VV} AES + HMAC pair, not a header.
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

    /** Legacy header name, verbatim from {@code ConnectionUtil:191}. */
    static final String HEADER_ACCESS_IP = "x-coocon-accessIp";

    private final RestClient efsRestClient;
    private final EfsProperties props;
    private final ObjectMapper objectMapper;

    /**
     * Memoised {@code x-coocon-accessIp} value. Legacy re-resolves on every request; memoising
     * avoids walking every {@code NetworkInterface} per call. Left null on failure rather than
     * cached-as-absent, so a transient {@code SocketException} does not disable the header for the
     * lifetime of the process.
     */
    private volatile String cachedLocalServerIp;

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

        RestClient.RequestBodySpec spec = efsRestClient.post()
                .uri(path)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED);
        String accessIp = localServerIp();
        if (accessIp != null) {
            spec = spec.header(HEADER_ACCESS_IP, accessIp);
        }

        String body = spec
                .body(form)
                .retrieve()
                .body(String.class);

        EfsEnvelope response = parse(body);
        // Never log EV/VV/payload/API_KEY — only the endpoint, path, and RC.
        log.debug("EFS send endpointKey={} path={} rc={}", endpointKey, path,
                response == null ? null : response.rc());
        return response;
    }

    /**
     * The {@code x-coocon-accessIp} value, or {@code null} when no address resolves.
     *
     * <p>Reproduces legacy {@code ConnectionUtil.getLocalServerIp():268-290} step for step —
     * including its quirks, because the point of restoring the header is to send the value an
     * allow-list may already hold, not a better-chosen one:
     * <ul>
     *   <li>iterate {@link NetworkInterface#getNetworkInterfaces()} in enumeration order;</li>
     *   <li>{@code continue} past loopback interfaces — and <b>only</b> loopback: a down, virtual or
     *       point-to-point interface is not skipped, so on a multi-homed host the answer is whichever
     *       the OS enumerates first, exactly as in legacy;</li>
     *   <li>return the first address whose {@code getHostAddress()} contains {@code '.'} — legacy's
     *       IPv4 test, which is why an IPv6-only host yields {@code null};</li>
     *   <li>swallow {@link SocketException} and return {@code null}.</li>
     * </ul>
     * Deliberately NOT "improved" to prefer a site-local or up interface: that would change the value
     * legacy sends, which is the one thing this method must not do.
     */
    String localServerIp() {
        String cached = cachedLocalServerIp;
        if (cached != null) {
            return cached;
        }
        String resolved = resolveLocalServerIp();
        if (resolved != null) {
            cachedLocalServerIp = resolved;
            log.debug("EFS {} resolved", HEADER_ACCESS_IP);
        } else {
            // Not an error: the header is optional and the call proceeds without it.
            log.warn("EFS {} could not be resolved — sending the request without the header "
                    + "(legacy ConnectionUtil behaves the same when getLocalServerIp() returns null)",
                    HEADER_ACCESS_IP);
        }
        return resolved;
    }

    private static String resolveLocalServerIp() {
        try {
            Enumeration<NetworkInterface> nics = NetworkInterface.getNetworkInterfaces();
            while (nics != null && nics.hasMoreElements()) {
                NetworkInterface ni = nics.nextElement();
                if (ni.isLoopback()) {
                    continue;
                }
                Enumeration<InetAddress> addresses = ni.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    String host = addresses.nextElement().getHostAddress();
                    if (host != null && host.contains(".")) {
                        return host;
                    }
                }
            }
        } catch (SocketException e) {
            // Legacy logs and returns null; the caller then omits the header.
            log.warn("EFS {} lookup failed: {}", HEADER_ACCESS_IP, e.getMessage());
        }
        return null;
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
