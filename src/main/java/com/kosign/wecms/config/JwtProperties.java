package com.kosign.wecms.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

@ConfigurationProperties(prefix = "wecms.jwt")
public record JwtProperties(
        @NotBlank String secret,
        @Positive long expiresInSeconds,
        @NotBlank String issuer
) { }
