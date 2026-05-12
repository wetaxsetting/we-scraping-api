package com.kosign.wecms.common.security;

import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.MacAlgorithm;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Component
public class JwtTokenProvider {

    private final JwtProperties props;
    private final SecretKey key;
    private final MacAlgorithm algorithm;

    public JwtTokenProvider(JwtProperties props) {
        this.props = props;
        String secret = props.secret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "wecms.jwt.secret is not set. "
                  + "Set env var WECMS_JWT_SECRET (32+ chars) or run with the 'local' "
                  + "profile active (application-local.yml provides an R&D default).");
        }
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new IllegalStateException(
                    "wecms.jwt.secret must be at least 32 bytes for HS256 "
                  + "(current length: " + secretBytes.length + "). "
                  + "Generate one with:  openssl rand -hex 64");
        }
        // >= 64 bytes -> HS512 (preferred), otherwise HS256.
        this.algorithm = secretBytes.length >= 64 ? Jwts.SIG.HS512 : Jwts.SIG.HS256;
        this.key = Keys.hmacShaKeyFor(secretBytes);
    }

    /**
     * Issue a JWT with the post-login tenant + authority + product-type binding.
     *
     * <p>{@code bizKey} is the BIZ_KEY the user actually resolved at
     * login (auto-picked when they have a single active link, or
     * explicitly chosen via the chooser step) — never a value the
     * frontend can override on a subsequent request. {@code authGrCd}
     * is {@code "ADMIN"} when the user has a BUSI_ADMN_INFM row for
     * this company, {@code "USER"} otherwise. {@code prdtType} is the
     * verbatim {@code BUSI_INFM.PRDT_TYPE} letter for the resolved
     * company — any of the legacy 14 codes; may be {@code null} when
     * the row carries no value. All three fields are read back on
     * every authenticated request by {@link JwtAuthenticationFilter}
     * and surfaced into {@link TenantContext}.
     */
    public String issue(String userId, String userNm, String bizKey, String langCd,
                        String authGrCd, String prdtType) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(props.expiresInSeconds());

        Map<String, Object> claims = new HashMap<>();
        claims.put("userNm",   userNm);
        claims.put("bizKey",   bizKey);
        claims.put("langCd",   langCd);
        // Stamp at issue time so the filter populates TenantContext and
        // Spring Security authorities without re-querying BUSI_ADMN_INFM.
        claims.put("authGrCd", TenantContext.AUTH_GR_ADMIN.equals(authGrCd)
                ? TenantContext.AUTH_GR_ADMIN
                : TenantContext.AUTH_GR_USER);
        // Product type — verbatim DB letter, no enum coercion at the
        // claim layer so legacy tenants on un-modelled letters
        // (W/R/B/E/U/L/S/P/C/D/T) still round-trip cleanly. Null when
        // the company row has no PRDT_TYPE.
        if (prdtType != null && !prdtType.isBlank()) {
            claims.put("prdtType", prdtType);
        }

        return Jwts.builder()
                .issuer(props.issuer())
                .subject(userId)
                .claims(claims)
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(key, algorithm)
                .compact();
    }

    public Jws<Claims> parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token);
    }

    public long expiresInSeconds() {
        return props.expiresInSeconds();
    }
}
