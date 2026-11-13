package com.kosign.wecms.domain.weinvoice.partner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kosign.wecms.common.exception.TraceId;
import com.kosign.wecms.common.i18n.MessageResolver;
import com.kosign.wecms.common.response.ApiResponse;
import com.kosign.wecms.domain.weinvoice.config.WeInvoiceProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

/**
 * Machine-to-machine gate of the WeInvoice Partner API ({@code /api/partner/v1/**}), which
 * WeCMS calls in place of the Coocon engine. The path is {@code permitAll} in the JWT chain —
 * WeCMS has no WeInvoice user token — and this filter is its authentication: header
 * {@code X-Api-Key} must equal one of {@code WECMS_WEINVOICE_PARTNER_API_KEYS}.
 *
 * <ul>
 *   <li><b>Fail closed</b> — no key configured, or WeInvoice disabled, rejects every call.</li>
 *   <li>Keys are compared as SHA-256 digests with {@link MessageDigest#isEqual} (constant time,
 *       and independent of the key length).</li>
 *   <li>Rejections answer 401 in the standard envelope, code {@code WBE3019}; the key is never
 *       logged.</li>
 * </ul>
 *
 * <p>Not a Spring {@code @Component}: wired explicitly into the security chain (same reason as
 * {@code CallbackIpWhitelistFilter}) so it does not also register as a servlet filter.
 */
@Slf4j
public class PartnerApiKeyFilter extends OncePerRequestFilter {

    public static final String PATH_PREFIX = "/api/partner/v1/";
    public static final String HEADER = "X-Api-Key";
    private static final String CODE = "WBE3019";

    private final boolean enabled;
    private final List<byte[]> keyDigests;
    private final ObjectMapper objectMapper;
    private final MessageResolver messages;

    public PartnerApiKeyFilter(WeInvoiceProperties props, ObjectMapper objectMapper, MessageResolver messages) {
        this.enabled = props.enabled();
        this.keyDigests = props.partnerKeyList().stream().map(PartnerApiKeyFilter::digest).toList();
        this.objectMapper = objectMapper;
        this.messages = messages;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String ctx = request.getContextPath();
        String path = ctx != null && !ctx.isEmpty() && uri.startsWith(ctx) ? uri.substring(ctx.length()) : uri;
        return !path.startsWith(PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String presented = request.getHeader(HEADER);
        if (enabled && presented != null && !presented.isBlank() && matches(presented.trim())) {
            chain.doFilter(request, response);
            return;
        }
        log.warn("Partner API rejected {} {} (key {}, keys configured {}, enabled {})", request.getMethod(),
                request.getRequestURI(), presented == null || presented.isBlank() ? "missing" : "invalid",
                keyDigests.size(), enabled);
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(),
                ApiResponse.error(CODE, messages.resolve(CODE), request.getRequestURI(), TraceId.current()));
    }

    private boolean matches(String presented) {
        byte[] d = digest(presented);
        boolean ok = false;
        for (byte[] k : keyDigests) {
            ok |= MessageDigest.isEqual(k, d);   // no early exit: same work whichever key matches
        }
        return ok;
    }

    private static byte[] digest(String s) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
