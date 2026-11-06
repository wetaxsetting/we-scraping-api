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
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Client of the scraping engine's internal API (we-scraping {@code web/internal_api.py}).
 *
 * <p>{@link #loginTest} asks the engine to log in to the GDT portal with the given account: the engine
 * fetches the captcha, solves it with its model ({@code g_600x120}) and retries on a wrong captcha. It
 * stores nothing; the caller saves the tax code only when the result is {@link Outcome#OK}. Never throws:
 * any transport problem is {@link Outcome#UNAVAILABLE}. The password is sent but never logged.
 */
@Slf4j
@Component
public class ScrapingClient {

    /** GDT login with up to 6 captcha attempts takes a few seconds; leave room for a slow portal. */
    static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(90);
    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    static final String LOGIN_TEST_PATH = "/internal/gdt/login-test";

    public enum Outcome { OK, REJECTED, UNAVAILABLE }

    /** {@code message}: GDT's text for {@code REJECTED}, a short cause otherwise. */
    public record LoginResult(Outcome outcome, String message) {
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

    private static String trimSlash(String url) {
        String u = url.trim();
        return u.endsWith("/") ? u.substring(0, u.length() - 1) : u;
    }
}
