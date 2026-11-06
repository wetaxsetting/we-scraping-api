package com.kosign.wecms.domain.weinvoice.partner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.kosign.wecms.common.i18n.MessageResolver;
import com.kosign.wecms.domain.weinvoice.WeInvoiceTestProps;
import com.kosign.wecms.domain.weinvoice.config.WeInvoiceProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** X-Api-Key gate of /api/partner/v1/** — fail closed, constant-time compare, standard envelope. */
class PartnerApiKeyFilterTest {

    /** Same shape as the application mapper: snake_case + JSR-310 (the envelope carries an Instant). */
    private final ObjectMapper mapper = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .registerModule(new JavaTimeModule());
    private final MessageResolver messages = messages();

    private static MessageResolver messages() {
        MessageResolver m = mock(MessageResolver.class);
        when(m.resolve(anyString())).thenReturn("Partner API key missing or invalid.");
        return m;
    }

    private MockHttpServletResponse run(WeInvoiceProperties props, String uri, String key, MockFilterChain chain)
            throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", uri);
        if (key != null) req.addHeader(PartnerApiKeyFilter.HEADER, key);
        MockHttpServletResponse res = new MockHttpServletResponse();
        new PartnerApiKeyFilter(props, mapper, messages).doFilter(req, res, chain);
        return res;
    }

    @Test
    void otherPathsPassUntouched() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse res = run(WeInvoiceTestProps.enabled("k1", 5), "/api/v1/weinvoice/me", null, chain);
        assertThat(chain.getRequest()).isNotNull();
        assertThat(res.getStatus()).isEqualTo(200);
    }

    @Test
    void validKeyPasses() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        run(WeInvoiceTestProps.enabled("k1,k2", 5), "/api/partner/v1/einvoices", " k2 ", chain);
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void missingKeyIs401WithEnvelope() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse res = run(WeInvoiceTestProps.enabled("k1", 5), "/api/partner/v1/einvoices", null, chain);
        assertThat(chain.getRequest()).isNull();
        assertThat(res.getStatus()).isEqualTo(401);
        assertThat(res.getContentAsString()).contains("\"code\":\"WBE3019\"").contains("\"error\":true");
    }

    @Test
    void wrongKeyIs401() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse res = run(WeInvoiceTestProps.enabled("k1", 5), "/api/partner/v1/einvoices", "k3", chain);
        assertThat(chain.getRequest()).isNull();
        assertThat(res.getStatus()).isEqualTo(401);
    }

    @Test
    void noConfiguredKeyRefusesEverything() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse res = run(WeInvoiceTestProps.enabled("", 5), "/api/partner/v1/einvoices", "", chain);
        assertThat(chain.getRequest()).isNull();
        assertThat(res.getStatus()).isEqualTo(401);
    }

    @Test
    void disabledWeInvoiceRefusesEvenAValidKey() throws Exception {
        WeInvoiceProperties disabled = new WeInvoiceProperties(false, null, null, null, null, null, null, "k1", null, 5, false, null);
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse res = run(disabled, "/api/partner/v1/einvoices", "k1", chain);
        assertThat(chain.getRequest()).isNull();
        assertThat(res.getStatus()).isEqualTo(401);
    }
}
