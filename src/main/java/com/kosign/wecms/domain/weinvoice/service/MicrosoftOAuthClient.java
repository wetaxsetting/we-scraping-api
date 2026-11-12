package com.kosign.wecms.domain.weinvoice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kosign.wecms.domain.weinvoice.config.WeInvoiceProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Microsoft identity platform, delegated authorization-code flow on the multi-tenant {@code common}
 * endpoint, for connecting an Outlook / Microsoft 365 mailbox over IMAP (XOAUTH2).
 *
 * <p>{@link #authorizeUrl} builds the sign-in URL; {@link #exchange} trades the returned code for a
 * refresh token and reads the mailbox address from the ID token. The ID token comes straight from the
 * token endpoint over TLS in answer to this client's own request, so its claims are read without a
 * signature check (OpenID Connect Core §3.1.3.7, TLS server validation in place of signature). Codes and
 * tokens are never logged.
 */
@Slf4j
@Component
public class MicrosoftOAuthClient {

    static final String AUTHORITY = "https://login.microsoftonline.com/common/oauth2/v2.0";
    static final String SCOPE = "offline_access https://outlook.office.com/IMAP.AccessAsUser.All openid email";
    static final Duration TIMEOUT = Duration.ofSeconds(20);

    /** Refresh token + mailbox address of a successful exchange. */
    public record Tokens(String refreshToken, String address) {
        @Override
        public String toString() {
            return "Tokens[refreshToken=***, address=" + address + "]";
        }
    }

    /** Exchange failure; {@link #reason} is the short code put in the redirect ({@code exchange}, {@code no_refresh_token}, {@code no_address}). */
    public static class OAuthException extends Exception {
        private final String reason;

        public OAuthException(String reason, String detail) {
            super(detail);
            this.reason = reason;
        }

        public String reason() {
            return reason;
        }
    }

    private final WeInvoiceProperties props;
    private final ObjectMapper json;
    private final HttpClient http;
    private final String tokenEndpoint;

    @Autowired
    public MicrosoftOAuthClient(WeInvoiceProperties props, ObjectMapper json) {
        this(props, json, AUTHORITY + "/token");
    }

    /** Test seam: a local token endpoint. */
    MicrosoftOAuthClient(WeInvoiceProperties props, ObjectMapper json, String tokenEndpoint) {
        this.props = props;
        this.json = json;
        this.tokenEndpoint = tokenEndpoint;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    public boolean configured() {
        return props.microsoftSignInConfigured();
    }

    public String authorizeUrl(String state) {
        WeInvoiceProperties.Microsoft ms = props.microsoft();
        Map<String, String> q = new LinkedHashMap<>();
        q.put("client_id", ms.clientId().trim());
        q.put("response_type", "code");
        q.put("redirect_uri", ms.redirectUri().trim());
        q.put("response_mode", "query");
        q.put("scope", SCOPE);
        q.put("state", state);
        q.put("prompt", "select_account");
        return AUTHORITY + "/authorize?" + form(q);
    }

    public Tokens exchange(String code) throws OAuthException {
        WeInvoiceProperties.Microsoft ms = props.microsoft();
        Map<String, String> f = new LinkedHashMap<>();
        f.put("client_id", ms.clientId().trim());
        f.put("client_secret", ms.clientSecret().trim());
        f.put("grant_type", "authorization_code");
        f.put("code", code);
        f.put("redirect_uri", ms.redirectUri().trim());
        f.put("scope", SCOPE);
        JsonNode node;
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(tokenEndpoint))
                    .timeout(TIMEOUT)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(form(f)))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            node = json.readTree(response.body());
            if (response.statusCode() != 200) {
                // Microsoft's error code (e.g. invalid_grant) is safe to log; the description may echo input, so it is not.
                log.warn("Microsoft token exchange answered HTTP {} error={}", response.statusCode(),
                        node.path("error").asText(""));
                throw new OAuthException("exchange", "HTTP " + response.statusCode());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OAuthException("exchange", "interrupted");
        } catch (IOException | IllegalArgumentException e) {
            log.warn("Microsoft token exchange failed: {}", e.getClass().getSimpleName());
            throw new OAuthException("exchange", e.getClass().getSimpleName());
        }
        String refresh = node.path("refresh_token").asText("");
        if (refresh.isBlank()) {
            throw new OAuthException("no_refresh_token", "no refresh_token in the answer");
        }
        String address = addressFromIdToken(node.path("id_token").asText(""));
        if (address == null) {
            throw new OAuthException("no_address", "no email / preferred_username claim");
        }
        return new Tokens(refresh, address);
    }

    /** {@code email} claim, else {@code preferred_username}, when it looks like a mail address; {@code null} otherwise. */
    String addressFromIdToken(String idToken) {
        String[] parts = idToken == null ? new String[0] : idToken.split("\\.");
        if (parts.length < 2) return null;
        try {
            JsonNode claims = json.readTree(Base64.getUrlDecoder().decode(parts[1]));
            for (String claim : new String[]{"email", "preferred_username"}) {
                String v = claims.path(claim).asText("").trim();
                if (v.contains("@")) return v;
            }
            return null;
        } catch (IOException | IllegalArgumentException e) {
            return null;
        }
    }

    private static String form(Map<String, String> values) {
        return values.entrySet().stream()
                .map(e -> enc(e.getKey()) + "=" + enc(e.getValue()))
                .collect(Collectors.joining("&"));
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
