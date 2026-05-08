package com.kosign.wecms.config;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * API-wide JSON serialization rule: every wire field is lowercase
 * snake_case. Java DTO fields stay camelCase (e.g. {@code expiresIn},
 * {@code accountNo}) and Jackson's {@link PropertyNamingStrategies#SNAKE_CASE}
 * does the conversion. Per-field {@code @JsonProperty} overrides remain
 * authoritative when a wire name diverges from the Java field
 * (e.g. {@code token} → {@code access_token}).
 *
 * The whole API was previously UPPER_SNAKE via per-DTO {@code @JsonProperty}
 * annotations — the CTO realigned the contract to lowercase snake_case so
 * the DTOs no longer need to spell every wire name out.
 */
@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer snakeCaseCustomizer() {
        return builder -> builder.propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
    }
}
