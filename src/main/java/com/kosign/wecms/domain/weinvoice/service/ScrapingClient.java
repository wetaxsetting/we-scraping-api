package com.kosign.wecms.domain.weinvoice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kosign.wecms.domain.weinvoice.config.WeInvoiceProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Client of the scraping engine's internal API (we-scraping {@code web/internal_api.py}).
 *
 * <p>{@link #loginTest} asks the engine to log in to the GDT portal with the given account: the engine
 * fetches the captcha, solves it with its model ({@code g_600x120}) and retries on a wrong captcha. It
 * stores nothing; the caller saves the tax code only when the result is {@link Outcome#OK}. Never throws:
 * any transport problem is {@link Outcome#UNAVAILABLE}. The password is sent but never logged.
 *
 * <p>{@link #mailLoginTest} does the same for a mailbox ({@code POST /internal/mail/login-test}): the engine
 * logs in to the IMAP server once (for {@code OAUTH_MS} it first exchanges the refresh token itself) and
 * checks the folders. Secrets travel in the body only and are never logged.
 */
@Slf4j
@Component
public class ScrapingClient {

    /** GDT login with up to 6 captcha attempts takes a few seconds; leave room for a slow portal. */
    static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(90);
    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    static final String LOGIN_TEST_PATH = "/internal/gdt/login-test";
    static final String MAIL_LOGIN_TEST_PATH = "/internal/mail/login-test";

    public enum Outcome { OK, REJECTED, UNAVAILABLE }

    /** {@code message}: GDT's text for {@code REJECTED}, a short cause otherwise. */
    public record LoginResult(Outcome outcome, String message) {
    }

    /**
     * Body of {@code POST /internal/mail/login-test}. {@code auth}: {@code PASSWORD | APP_PASSWORD | OAUTH_MS};
     * exactly one of {@code password} / {@code refreshToken} is set.
     */
    public record MailLogin(String auth, String host, int port, String tlsMode, String loginId,
                            String password, String refreshToken, List<String> folders) {

        /** Never print a secret. */
        @Override
        public String toString() {
            return "MailLogin[auth=" + auth + ", host=" + host + ", port=" + port + ", tlsMode=" + tlsMode
                    + ", loginId=" + loginId + ", password=" + (password == null ? "<none>" : "***")
                    + ", refreshToken=" + (refreshToken == null ? "<none>" : "***") + ", folders=" + folders + "]";
        }
    }

    /**
     * @param message        the mail server's text for {@code REJECTED}, a short cause otherwise
     * @param missingFolders requested folders the mailbox does not have (empty when all exist)
     */
    public record MailLoginResult(Outcome outcome, String message, List<String> missingFolders) {
    }

    private final WeInvoiceProperties props;
    private final ObjectMapper json;
    private final HttpClient http;

    public ScrapingClient(WeInvoiceProperties props, ObjectMapper json) {
        this.props = props;
        this.json = json;
        this.http = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
    }

    public LoginResult loginTest(String mst, String username, String password) {
        WeInvoiceProperties.Scraping cfg = props.scraping();
        if (!cfg.configured()) {
            return new LoginResult(Outcome.UNAVAILABLE, "scraping not configured");
        }
        Map<String, String> body = new LinkedHashMap<>();
        body.put("mst", mst);
        body.put("username", username);
        body.put("password", password);
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(trimSlash(cfg.url()) + LOGIN_TEST_PATH))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .header("X-Api-Key", cfg.apiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("scraping login-test mst={} answered HTTP {}", mst, response.statusCode());
                return new LoginResult(Outcome.UNAVAILABLE, "scraping HTTP " + response.statusCode());
            }
            JsonNode node = json.readTree(response.body());
            String reason = node.path("reason").asText("");
            String message = node.path("message").asText("");
            if (node.path("ok").asBoolean(false)) {
                return new LoginResult(Outcome.OK, "");
            }
            if ("rejected".equals(reason)) {
                return new LoginResult(Outcome.REJECTED, message);
            }
            return new LoginResult(Outcome.UNAVAILABLE, message.isBlank() ? reason : message);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new LoginResult(Outcome.UNAVAILABLE, "interrupted");
        } catch (IOException | IllegalArgumentException e) {
            log.warn("scraping login-test mst={} failed: {}", mst, e.getClass().getSimpleName());
            return new LoginResult(Outcome.UNAVAILABLE, e.getClass().getSimpleName());
        }
    }

    /**
     * Engine answer {@code {ok, message, missing_folders[]}}: {@code ok=true} → {@link Outcome#OK};
     * {@code ok=false} → {@link Outcome#REJECTED} carrying the server message, unless the engine says
     * {@code reason: "unavailable"}; a non-200 answer or a transport failure → {@link Outcome#UNAVAILABLE}.
     */
    public MailLoginResult mailLoginTest(MailLogin login) {
        WeInvoiceProperties.Scraping cfg = props.scraping();
        if (!cfg.configured()) {
            return new MailLoginResult(Outcome.UNAVAILABLE, "scraping not configured", List.of());
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("auth", login.auth());
        body.put("host", login.host());
        body.put("port", login.port());
        body.put("tls_mode", login.tlsMode());
        body.put("login_id", login.loginId());
        if (login.password() != null) body.put("password", login.password());
        if (login.refreshToken() != null) body.put("refresh_token", login.refreshToken());
        body.put("folders", login.folders() == null ? List.of() : login.folders());
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(trimSlash(cfg.url()) + MAIL_LOGIN_TEST_PATH))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .header("X-Api-Key", cfg.apiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("scraping mail login-test host={} answered HTTP {}", login.host(), response.statusCode());
                return new MailLoginResult(Outcome.UNAVAILABLE, "scraping HTTP " + response.statusCode(), List.of());
            }
            JsonNode node = json.readTree(response.body());
            String message = node.path("message").asText("");
            List<String> missing = new ArrayList<>();
            node.path("missing_folders").forEach(f -> {
                if (f.isTextual() && !f.asText().isBlank()) missing.add(f.asText());
            });
            if (node.path("ok").asBoolean(false)) {
                return new MailLoginResult(Outcome.OK, message, List.copyOf(missing));
            }
            if ("unavailable".equals(node.path("reason").asText(""))) {
                return new MailLoginResult(Outcome.UNAVAILABLE, message.isBlank() ? "unavailable" : message, List.of());
            }
            return new MailLoginResult(Outcome.REJECTED, message, List.copyOf(missing));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new MailLoginResult(Outcome.UNAVAILABLE, "interrupted", List.of());
        } catch (IOException | IllegalArgumentException e) {
            log.warn("scraping mail login-test host={} failed: {}", login.host(), e.getClass().getSimpleName());
            return new MailLoginResult(Outcome.UNAVAILABLE, e.getClass().getSimpleName(), List.of());
        }
    }

    private static String trimSlash(String url) {
        String u = url.trim();
        return u.endsWith("/") ? u.substring(0, u.length() - 1) : u;
    }
}
