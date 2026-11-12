package com.kosign.wecms.domain.weinvoice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kosign.wecms.domain.weinvoice.WeInvoiceTestProps;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Authorize URL shape and the code exchange, against a local stub token endpoint (no network). */
class MicrosoftOAuthClientTest {

    private HttpServer server;
    private final AtomicReference<String> seenForm = new AtomicReference<>();
    private final ObjectMapper json = new ObjectMapper();

    @AfterEach
    void stop() {
        if (server != null) server.stop(0);
        server = null;
    }

    private MicrosoftOAuthClient answering(int status, String body) throws IOException {
        stop();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/token", ex -> {
            seenForm.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] out = body.getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(status, out.length);
            ex.getResponseBody().write(out);
            ex.close();
        });
        server.start();
        return new MicrosoftOAuthClient(WeInvoiceTestProps.enabled("", 5), json,
                "http://127.0.0.1:" + server.getAddress().getPort() + "/token");
    }

    private static String idToken(String claimsJson) {
        Base64.Encoder b = Base64.getUrlEncoder().withoutPadding();
        return b.encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8)) + "."
                + b.encodeToString(claimsJson.getBytes(StandardCharsets.UTF_8)) + ".sig";
    }

    @Test
    void authorizeUrlCarriesTheContractParameters() {
        String url = new MicrosoftOAuthClient(WeInvoiceTestProps.enabled("", 5), json).authorizeUrl("st.ate");
        String decoded = URLDecoder.decode(url, StandardCharsets.UTF_8);
        assertThat(url).startsWith("https://login.microsoftonline.com/common/oauth2/v2.0/authorize?");
        assertThat(decoded).contains("client_id=ms-client", "response_type=code", "response_mode=query",
                "scope=offline_access https://outlook.office.com/IMAP.AccessAsUser.All openid email",
                "prompt=select_account", "state=st.ate",
                "redirect_uri=https://api.example.vn/api/v1/weinvoice/mailboxes/microsoft/callback");
        assertThat(url).doesNotContain("ms-secret");
    }

    @Test
    void exchangeReadsRefreshTokenAndEmailClaim() throws Exception {
        MicrosoftOAuthClient c = answering(200, "{\"access_token\":\"at\",\"refresh_token\":\"rt-1\",\"id_token\":\""
                + idToken("{\"email\":\"ke.toan@contoso.com\",\"preferred_username\":\"other@contoso.com\"}") + "\"}");
        MicrosoftOAuthClient.Tokens t = c.exchange("code-1");
        assertThat(t.refreshToken()).isEqualTo("rt-1");
        assertThat(t.address()).isEqualTo("ke.toan@contoso.com");
        assertThat(t.toString()).doesNotContain("rt-1");
        String form = URLDecoder.decode(seenForm.get(), StandardCharsets.UTF_8);
        assertThat(form).contains("grant_type=authorization_code", "code=code-1", "client_id=ms-client",
                "client_secret=ms-secret",
                "redirect_uri=https://api.example.vn/api/v1/weinvoice/mailboxes/microsoft/callback");
    }

    @Test
    void preferredUsernameIsTheFallbackAddress() throws Exception {
        MicrosoftOAuthClient c = answering(200, "{\"refresh_token\":\"rt\",\"id_token\":\""
                + idToken("{\"preferred_username\":\"a@contoso.onmicrosoft.com\"}") + "\"}");
        assertThat(c.exchange("c").address()).isEqualTo("a@contoso.onmicrosoft.com");
    }

    @Test
    void failuresCarryShortReasons() throws Exception {
        MicrosoftOAuthClient refused = answering(400, "{\"error\":\"invalid_grant\"}");
        assertThatThrownBy(() -> refused.exchange("c"))
                .isInstanceOfSatisfying(MicrosoftOAuthClient.OAuthException.class, e -> assertThat(e.reason()).isEqualTo("exchange"));
        MicrosoftOAuthClient noRefresh = answering(200, "{\"id_token\":\"" + idToken("{\"email\":\"a@b.vn\"}") + "\"}");
        assertThatThrownBy(() -> noRefresh.exchange("c"))
                .isInstanceOfSatisfying(MicrosoftOAuthClient.OAuthException.class, e -> assertThat(e.reason()).isEqualTo("no_refresh_token"));
        MicrosoftOAuthClient noAddress = answering(200, "{\"refresh_token\":\"rt\",\"id_token\":\"" + idToken("{\"name\":\"A\"}") + "\"}");
        assertThatThrownBy(() -> noAddress.exchange("c"))
                .isInstanceOfSatisfying(MicrosoftOAuthClient.OAuthException.class, e -> assertThat(e.reason()).isEqualTo("no_address"));
    }

    @Test
    void notConfiguredWithoutWebBaseOrClient() {
        assertThat(new MicrosoftOAuthClient(WeInvoiceTestProps.enabled("", 5), json).configured()).isTrue();
        assertThat(new MicrosoftOAuthClient(WeInvoiceTestProps.disabled(), json).configured()).isFalse();
    }
}
