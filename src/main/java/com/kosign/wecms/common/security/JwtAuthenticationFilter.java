package com.kosign.wecms.common.security;

import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.tenant.TenantContext;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTH_HEADER  = "Authorization";
    private static final String BEARER       = "Bearer ";

    private final JwtTokenProvider tokenProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader(AUTH_HEADER);
        boolean tenantSet = false;
        if (StringUtils.hasText(header) && header.startsWith(BEARER)) {
            String token = header.substring(BEARER.length());
            try {
                Jws<Claims> jws    = tokenProvider.parse(token);
                Claims      claims = jws.getPayload();
                String      userId = claims.getSubject();
                String      bizKey = claims.get("bizKey", String.class);
                LangCd      langCd = LangCd.fromCodeOrDefault(claims.get("langCd", String.class));

                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(userId, null, List.of());
                SecurityContextHolder.getContext().setAuthentication(auth);

                TenantContext.set(userId, bizKey, langCd);
                tenantSet = true;
            } catch (JwtException ex) {
                log.debug("Rejecting invalid JWT: {}", ex.getMessage());
                SecurityContextHolder.clearContext();
            }
        }
        try {
            chain.doFilter(request, response);
        } finally {
            if (tenantSet) TenantContext.clear();
        }
    }
}
