package com.kosign.wecms.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import jakarta.validation.constraints.NotBlank;

/**
 * Coocon API credentials — bound from {@code wecms.coocon.*}.
 *
 * <p>Mirrors the {@code <coocon>} block in the JEX config:
 * {@code WECMS DEV/WECMS/web/WEB-INF/jex/config/VI/jex.wabooks_new.user.xml}.
 *
 * <p>Two API surfaces:
 * <ul>
 *   <li><b>Smart Gateway ({@code smartApiUrl} / {@code smartApiKey})</b> —
 *       used by the exchange-rate batch ({@code EXCH_0001_001}). This is
 *       the endpoint the batch calls to pull Shinhan/Woori FX rates.</li>
 *   <li><b>Overseas API ({@code apiUrl} / {@code apiKey})</b> —
 *       overseas / bigdata endpoint (reserved for future overseas-payment
 *       integrations; not consumed by the current exchange-rate flow).</li>
 * </ul>
 *
 * <p>Profile-specific values:
 * <ul>
 *   <li><b>local / dev</b>: {@code dev-sgw.webcashvietnam.com:8443} (Coocon DEV gateway)</li>
 *   <li><b>prod</b>: override via {@code WECMS_COOCON_SMART_API_URL} /
 *       {@code WECMS_COOCON_SMART_API_KEY} environment variables.</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "wecms.coocon")
public record CooconProperties(

        /**
         * Coocon overseas/bigdata API key (legacy JEX {@code api_key}).
         * Used for overseas finance scraping — not consumed by the current
         * exchange-rate batch but kept for future overseas-payment batches.
         */
        @NotBlank String apiKey,

        /**
         * Coocon overseas/bigdata API URL (legacy JEX {@code api_url}).
         * Endpoint: {@code /overseas/finance/scrap/bigdata_info.jsp}.
         */
        @NotBlank String apiUrl,

        /**
         * Smart Gateway API key (legacy JEX {@code smart_api_key}).
         * Consumed by the exchange-rate batch ({@code EXCH_0001_001}) to
         * authenticate against the Shinhan/Woori FX rate endpoint.
         */
        @NotBlank String smartApiKey,

        /**
         * Smart Gateway URL (legacy JEX {@code smart_api_url}).
         * Endpoint: {@code /domestic/finance/scrap/smart_gateway.jsp}.
         * DEV: {@code https://dev-sgw.webcashvietnam.com:8443/...}
         * PROD: override via {@code WECMS_COOCON_SMART_API_URL}.
         */
        @NotBlank String smartApiUrl,

        /**
         * Unique key assigned to this WeCMS instance (legacy JEX
         * {@code unique_key}). Identifies the tenant in Coocon's system.
         * DEV value: {@code "0000099"}.
         */
        @NotBlank String uniqueKey

) { }
