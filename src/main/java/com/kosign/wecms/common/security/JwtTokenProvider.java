package com.kosign.wecms.common.security;

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

    public String issue(String userId, String userNm, String bizKey, String langCd) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(props.expiresInSeconds());

        Map<String, Object> claims = new HashMap<>();
        claims.put("userNm", userNm);
        claims.put("bizKey", bizKey);
        claims.put("langCd", langCd);

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
