package com.kosign.wecms.domain.payment.dispatch.van.client;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Externalised EFS gateway configuration — refactor home for the legacy
 * {@code wecms_batch.properties} {@code {ENV}_API_URL} + endpoint paths.
 * Bound from {@code wecms.dispatch.efs.*}.
 *
 * <p>The vendor endpoint <b>paths</b> are fixed ({@code /WGVN_EFS_{code}.jct}) and
 * default in code below; only {@code enabled} + {@code base-url} (and the
 * partner {@code ID}) are environment-specific and come from a profile yaml
 * (DEV/SIT). Default profile ships {@code enabled=false} + empty {@code base-url}
 * so {@link MockEfsApiClient} is the active bean and no live call can occur.
 *
 * <p><b>Security:</b> the {@code API_KEY} is the per-VAN AES/HMAC secret and is
 * <b>never</b> stored here or in any yaml — it is provisioned out-of-band into
 * {@code TB_VAN_API_INFO} and read via
 * {@link com.kosign.wecms.domain.payment.dispatch.van.client.VanApiKeyService} (B2).
 *
 * <p>The six services documented in REST API guide ver 1.2.6 are mapped
 * ({@code EFS_201/202/203/204/301/302}). {@code WGVN_EFS_204} (Woori FX-rate
 * inquiry, §5.6) was added in v1.2.6 and is now seeded below.
 */
@Component
@ConfigurationProperties(prefix = "wecms.dispatch.efs")
@Getter
@Setter
public class EfsProperties {

    /**
     * Master switch. {@code false} (default) → {@link MockEfsApiClient} is active
     * and no live call is possible. Set {@code true} only in a DEV/SIT profile
     * where {@link #baseUrl} is configured (B1) — then {@link RestClientEfsApiClient}
     * becomes the active bean.
     */
    private boolean enabled = false;

    /** Base URL of the EFS gateway for the active environment (B1). Empty by default. */
    private String baseUrl = "";

    /**
     * Partner identifier sent as the envelope {@code ID} (PDF sample {@code CORP000001}).
     * Environment/partner-specific; NOT a secret. Empty by default.
     */
    private String partnerId = "";

    /**
     * Endpoint-key → vendor path map. Defaults to the five services confirmed in
     * the guide; a profile may override but rarely needs to (paths are vendor-fixed).
     */
    private Map<String, String> endpoints = defaultEndpoints();

    /** Connect timeout (ms). PDF §6 sample uses 3 s. */
    private int connectTimeoutMs = 3_000;

    /** Read timeout (ms). PDF §6 sample uses 20 s. */
    private int readTimeoutMs = 20_000;

    private static Map<String, String> defaultEndpoints() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put(EfsApiClient.EFS_201, "/WGVN_EFS_201.jct");
        m.put(EfsApiClient.EFS_202, "/WGVN_EFS_202.jct");
        m.put(EfsApiClient.EFS_203, "/WGVN_EFS_203.jct");
        m.put(EfsApiClient.EFS_204, "/WGVN_EFS_204.jct"); // Woori FX-rate inquiry (guide v1.2.6 §5.6)
        m.put(EfsApiClient.EFS_301, "/WGVN_EFS_301.jct");
        m.put(EfsApiClient.EFS_302, "/WGVN_EFS_302.jct");
        return m;
    }
}
