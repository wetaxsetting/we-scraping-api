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
 * default in code below; only {@code enabled} + {@code base-url} are
 * environment-specific and come from a profile yaml
 * (DEV/SIT). Default profile ships {@code enabled=false} + empty {@code base-url}
 * so {@link MockEfsApiClient} is the active bean and no live call can occur.
 *
 * <p><b>Envelope {@code ID} is NOT configuration (D-2, legacy-verified 2026-07-29).</b>
 * A {@code partner-id} property used to live here and was preferred over the VAN org code
 * when set. Legacy has <b>no such property</b>: on every leg the envelope {@code ID} is read
 * from {@code TB_FIRM_CONF.VAN_ORG_CD}, so a configured partner id could only ever send a
 * value legacy never sends. Verified in legacy source:
 * <ul>
 *   <li>202 / 204 — {@code NAPAS_REAL_TIME_BATCH:108} {@code ID = idoOutFCR001.getString("VAN_ORG_CD")},
 *       used at {@code :346} / {@code :559} via {@code inputData(ID, …)} → {@code "ID=" + ID} ({@code :712}).</li>
 *   <li>203 — {@code PROCESSING_RESULT_CHECK_BATCH:164} {@code "ID=" + van_org_cd} ({@code :112}).</li>
 *   <li>301 — {@code FILE_DEPOSIT_TRANSFER_BATCH1:1208} {@code "ID=" + …getString("VAN_ORG_CD")}.</li>
 *   <li>302 — {@code FILE_DEPOSIT_TRANSFER_BATCH2:189} {@code "ID=" + van_org_cd} ({@code :131}).</li>
 *   <li>MSG — {@code MsgSendUtil.SendMsg(van_org_cd, …)} at every legacy call site.</li>
 *   <li>201 — {@code PymnHelper:950}, {@code comm_1003_001_act.jsp:136},
 *       {@code comm_0184_007_r001_act.jsp:355} all {@code put("ID", …VAN_ORG_CD)}.</li>
 * </ul>
 * The separate {@code BANK_PRTN_ID} is a <i>business-payload</i> field ({@code prtn_id}), not the
 * envelope {@code ID} — legacy keeps the two distinct ({@code PROCESSING_RESULT_CHECK_BATCH:144,153})
 * and so does the refactor ({@code Efs202Request.prtnId}). Do not reintroduce a partner-id property.
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

    // NOTE: there is deliberately NO partner-id property here. The EFS envelope {@code ID} is
    // ALWAYS TB_FIRM_CONF.VAN_ORG_CD — see the class Javadoc above for the per-leg legacy evidence.

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
        m.put(EfsApiClient.EFS_MSG, "/WGVN_MSG_SEND_001.jct"); // ops Slack alert (legacy MsgSendUtil); OFF by default
        return m;
    }
}
