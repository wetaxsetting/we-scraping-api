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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
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
                Jws<Claims> jws      = tokenProvider.parse(token);
                Claims      claims   = jws.getPayload();
                String      userId   = claims.getSubject();
                String      userNm   = claims.get("userNm",   String.class);
                String      bizKey   = claims.get("bizKey",   String.class);
                String      authGrCd = claims.get("authGrCd", String.class);
                String      prdtType = claims.get("prdtType", String.class);
                LangCd      langCd   = LangCd.fromCodeOrDefault(claims.get("langCd", String.class));

                // Spring Security authority — currently advisory (no
                // @PreAuthorize / @EnableMethodSecurity wired up yet),
                // but populated so the follow-up MR that gates admin-
                // only endpoints just needs to flip the switch.
                String resolvedAuth = TenantContext.AUTH_GR_ADMIN.equals(authGrCd)
                        ? TenantContext.AUTH_GR_ADMIN
                        : TenantContext.AUTH_GR_USER;
                SimpleGrantedAuthority grantedRole =
                        new SimpleGrantedAuthority("ROLE_" + resolvedAuth);

                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(userId, null, List.of(grantedRole));
                SecurityContextHolder.getContext().setAuthentication(auth);

                // userNm has been a JWT claim since Sprint 0
                // (JwtTokenProvider.issue) but the filter previously
                // dropped it on the floor. The shared AuditLogService
                // (P-1) requires a display name for the audit row's
                // REG_NM column, and the firm-banking-settings Update
                // slice needs it for the LAST_EDTR column too.
                // Defensive: an authGrCd-less JWT from a pre-V73 cached
                // token defaults to USER, keeping reads working but
                // never silently elevating to ADMIN. prdtType is null
                // for JWTs issued before the V74 cutover — domain
                // services that gate on product type already check
                // for that via PrdtType.fromCode(...).
                TenantContext.set(
                        userId,
                        userNm == null || userNm.isBlank() ? userId : userNm,
                        bizKey,
                        langCd,
                        resolvedAuth,
                        prdtType);
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
