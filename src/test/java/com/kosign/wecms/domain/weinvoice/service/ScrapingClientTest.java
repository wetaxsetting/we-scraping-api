package com.kosign.wecms.domain.weinvoice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kosign.wecms.domain.weinvoice.config.WeInvoiceProperties;
import com.kosign.wecms.domain.weinvoice.service.ScrapingClient.Outcome;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/** The engine's login-test contract (we-scraping web/internal_api.py), against a local stub server. */
class ScrapingClientTest {

    private HttpServer server;
    private final AtomicReference<String> seenKey = new AtomicReference<>();
    private final AtomicReference<String> seenBody = new AtomicReference<>();

    @AfterEach
    void stop() {
        if (server != null) server.stop(0);
    }

    private ScrapingClient clientAnswering(int status, String body) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(ScrapingClient.LOGIN_TEST_PATH, ex -> {
            seenKey.set(ex.getRequestHeaders().getFirst("X-Api-Key"));
            seenBody.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] out = body.getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(status, out.length);
            ex.getResponseBody().write(out);
            ex.close();
        });
        server.start();
        return client("http://127.0.0.1:" + server.getAddress().getPort() + "/", "k-123");
    }

    private static ScrapingClient client(String url, String key) {
        WeInvoiceProperties props = new WeInvoiceProperties(true,
                new WeInvoiceProperties.Db("jdbc:postgresql://h/db", "u", "p", 1), "public", "weinvoice", "out", "store",
                com.kosign.wecms.domain.weinvoice.WeInvoiceTestProps.KEY_B64, "", "", 5, false,
                new WeInvoiceProperties.Scraping(url, key));
        return new ScrapingClient(props, new ObjectMapper());
    }

    @Test
    void acceptedAccountIsOkAndTheRequestCarriesKeyAndAccount() throws IOException {
        ScrapingClient c = clientAnswering(200, "{\"ok\":true,\"reason\":\"ok\",\"message\":\"\",\"attempts\":2}");
        assertThat(c.loginTest("1201496252", "1201496252-U002", "Secret#1").outcome()).isEqualTo(Outcome.OK);
        assertThat(seenKey.get()).isEqualTo("k-123");
        assertThat(seenBody.get()).contains("\"mst\":\"1201496252\"", "\"username\":\"1201496252-U002\"", "\"password\":\"Secret#1\"");
    }

    @Test
    void rejectedAccountCarriesTheGdtMessage() throws IOException {
        ScrapingClient c = clientAnswering(200,
                "{\"ok\":false,\"reason\":\"rejected\",\"message\":\"Tên đăng nhập hoặc mật khẩu không đúng\",\"attempts\":1}");
        var r = c.loginTest("1201496252", "1201496252", "x");
        assertThat(r.outcome()).isEqualTo(Outcome.REJECTED);
        assertThat(r.message()).isEqualTo("Tên đăng nhập hoặc mật khẩu không đúng");
    }

    @Test
    void captchaExhaustedIsUnavailable() throws IOException {
        ScrapingClient c = clientAnswering(200, "{\"ok\":false,\"reason\":\"unavailable\",\"message\":\"Thử 6 lần không được\"}");
        assertThat(c.loginTest("1201496252", "1201496252", "x").outcome()).isEqualTo(Outcome.UNAVAILABLE);
    }

    @Test
    void wrongKeyOrServerErrorIsUnavailable() throws IOException {
        ScrapingClient c = clientAnswering(401, "{\"ok\":false,\"reason\":\"unauthorized\"}");
        var r = c.loginTest("1201496252", "1201496252", "x");
        assertThat(r.outcome()).isEqualTo(Outcome.UNAVAILABLE);
        assertThat(r.message()).contains("401");
    }

    @Test
    void engineDownIsUnavailableNotAnException() {
        // port 1: nothing listens → connection refused
        assertThat(client("http://127.0.0.1:1", "k").loginTest("1201496252", "1201496252", "x").outcome())
                .isEqualTo(Outcome.UNAVAILABLE);
    }

    @Test
    void notConfiguredIsUnavailableWithoutAnyCall() {
        assertThat(client("", "").loginTest("1201496252", "1201496252", "x").outcome()).isEqualTo(Outcome.UNAVAILABLE);
        assertThat(client("${WECMS_WEINVOICE_SCRAPING_URL}", "k").loginTest("1", "1", "x").outcome())
                .isEqualTo(Outcome.UNAVAILABLE);
    }

    @Test
    void settingsNeverPrintTheKey() {
        assertThat(new WeInvoiceProperties.Scraping("http://h", "super-secret").toString()).doesNotContain("super-secret");
    }
}
