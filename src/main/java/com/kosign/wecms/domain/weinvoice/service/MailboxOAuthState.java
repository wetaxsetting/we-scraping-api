package com.kosign.wecms.domain.weinvoice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kosign.wecms.domain.weinvoice.config.WeInvoiceProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

/**
 * Signed, stateless {@code state} of the Microsoft sign-in ({@code GET /mailboxes/microsoft/authorize} →
 * Microsoft → {@code GET /mailboxes/microsoft/callback}).
 *
 * <p>The callback is reached without a JWT (the browser comes back from Microsoft), so the state is what
 * says whose mailbox this is. Format: {@code base64url(json) + "." + base64url(HMAC-SHA256(json part))};
 * the JSON holds the owner, the chosen scope / tax code / folders, an expiry 10 minutes ahead and a random
 * nonce. The HMAC key is derived from {@code WECMS_WEINVOICE_SECRET_KEY}
 * ({@code HMAC-SHA256(secretKey, "weinvoice-ms-oauth-state")}) so the AES key itself is never used as a MAC
 * key. Nothing is stored server side; a forged, altered or expired state is refused.
 */
@Component
public class MailboxOAuthState {

    static final Duration TTL = Duration.ofMinutes(10);
    private static final String HMAC = "HmacSHA256";
    private static final byte[] KEY_LABEL = "weinvoice-ms-oauth-state".getBytes(StandardCharsets.US_ASCII);

    /** What the user chose before leaving for Microsoft. */
    public record Payload(String ownerId, String companyScope, String companyMst, List<String> folders) {
    }

    private final byte[] macKey;
    private final ObjectMapper json;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    @Autowired
    public MailboxOAuthState(WeInvoiceProperties props, ObjectMapper json) {
        // Validated by WeInvoiceProperties when enabled; when disabled every caller is refused before signing.
        this(props.enabled() ? Base64.getDecoder().decode(props.secretKey().trim()) : null, json, Clock.systemUTC());
    }

    MailboxOAuthState(byte[] secretKey, ObjectMapper json, Clock clock) {
        this.macKey = secretKey == null ? null : hmac(secretKey, KEY_LABEL);
        this.json = json;
        this.clock = clock;
    }

    public String sign(Payload p) {
        requireKey();
        byte[] nonce = new byte[16];
        random.nextBytes(nonce);
        ObjectNode node = json.createObjectNode();
        node.put("o", p.ownerId());
        node.put("s", p.companyScope());
        if (p.companyMst() != null) node.put("m", p.companyMst());
        ArrayNode folders = node.putArray("f");
        if (p.folders() != null) p.folders().forEach(folders::add);
        node.put("e", clock.instant().plus(TTL).getEpochSecond());
        node.put("n", b64(nonce));
        String body = b64(node.toString().getBytes(StandardCharsets.UTF_8));
        return body + "." + b64(hmac(macKey, body.getBytes(StandardCharsets.US_ASCII)));
    }

    /** The payload of a state this server signed and that has not expired; empty otherwise. Never throws. */
    public Optional<Payload> verify(String state) {
        if (macKey == null || state == null) return Optional.empty();
        int dot = state.indexOf('.');
        if (dot <= 0 || dot != state.lastIndexOf('.')) return Optional.empty();
        String body = state.substring(0, dot);
        try {
            byte[] expected = hmac(macKey, body.getBytes(StandardCharsets.US_ASCII));
            byte[] given = Base64.getUrlDecoder().decode(state.substring(dot + 1));
            if (!MessageDigest.isEqual(expected, given)) return Optional.empty();
            JsonNode node = json.readTree(Base64.getUrlDecoder().decode(body));
            if (node.path("e").asLong(0) <= clock.instant().getEpochSecond()) return Optional.empty();
            String owner = node.path("o").asText("");
            if (owner.isBlank()) return Optional.empty();
            List<String> folders = new ArrayList<>();
            node.path("f").forEach(f -> folders.add(f.asText()));
            String mst = node.hasNonNull("m") ? node.get("m").asText() : null;
            return Optional.of(new Payload(owner, node.path("s").asText("ONE"), mst, List.copyOf(folders)));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private void requireKey() {
        if (macKey == null) {
            throw new IllegalStateException("WeInvoice secret key is not configured");
        }
    }

    private static String b64(byte[] raw) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    }

    private static byte[] hmac(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(new SecretKeySpec(key, HMAC));
            return mac.doFinal(data);
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA256 unavailable", e);
        }
    }
}
