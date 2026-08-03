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

    /**
     * Connect timeout (ms) — TCP establishment only.
     *
     * <p><b>Source: legacy {@code util.ConnectionUtil.CONNECT_TIMEOUT = 30000} (line 16),</b> the
     * value every EFS call has actually used in DEV/UAT/PROD. Raised from 3 s on 2026-07-31.
     *
     * <p>The vendor guide has no figure for the handshake: §3.2 (see {@link #readTimeoutMs}) governs
     * the <b>message round trip</b> ({@code 요청전문 전송완료로부터 응답전문 수신 완료까지} — "from
     * completion of request-message transmission to completion of response-message reception"), which
     * starts after the connection exists. So there is nothing normative to match here, and the earlier
     * 3 s came from the §6.1 <i>sample</i> — the same non-binding source that produced the 20 s read
     * timeout this codebase already corrected (see {@link #readTimeoutMs}, C-7 / F-1). Legacy is the
     * better evidence: 30 s is what the gateway has been reached with for years across a VPN'd
     * inter-datacentre hop, and a handshake that is merely slow is not a reason to abandon a payment
     * instruction. The failure this protects against is being <i>refused a connection</i>, not being
     * given a slow one.
     *
     * <p><b>Coupled value — do not change in isolation.</b> Same coupling as
     * {@link #readTimeoutMs}: this feeds the per-row worst case that {@code ResultCheckJob}'s
     * {@code lockAtMostFor} must cover, asserted by {@code ResultCheckLockCeilingTest}.
     */
    private int connectTimeoutMs = 30_000;

    /**
     * Read timeout (ms).
     *
     * <p><b>Source: REST API guide v1.2.6 §3.2 전문 응답시간 (p.8-9)</b> — the vendor's only
     * normative timing figure:
     * <blockquote>
     * 웹케시 베트남 과 이용사의 전문교환을 신속하게 하기 위하여 시스템 특성에 맞는 <b>감시시간</b>
     * (전문전송 및 응답에 대한 <b>한계 시간</b>)을 다음과 같이 설정 운용한다.<br>
     * 이용사 · 요청전문 전송완료로부터 응답전문 수신 완료까지 · <b>30 초</b>
     * </blockquote>
     * <i>"…a <b>monitoring time</b> (the <b>limit time</b> for message transmission and response)
     * suited to the system characteristics is configured and operated as follows. Partner · from
     * completion of request-message transmission to completion of response-message reception ·
     * <b>30 seconds</b>."</i>
     *
     * <p><b>Why 60 s and not 30 s.</b> 30 s is the vendor's limit, so timing out at exactly 30 s
     * races it — a response that arrives at 29.9 s is a coin flip. Legacy
     * ({@code util.ConnectionUtil.READ_TIMEOUT}, line 17) sat at 60 s for years without issue, which
     * is the empirical case for clearing the limit rather than meeting it. This field therefore has
     * two agreeing sources — the guide's §3.2 limit and legacy's own constant — and matches legacy
     * exactly, as {@link #connectTimeoutMs} now does.
     *
     * <p><b>What the previous 20 s cost us (C-7 / F-1).</b> This field used to read
     * <i>"PDF §6 sample uses 20 s"</i>. That citation was accurate but the source is not normative —
     * §6 opens with {@code ***** 주의 *****  웹케시 베트남에서 제공하는 소스는 단지 샘플일 뿐이며,
     * 반드시 그대로 사용하여야 하는 것은 아닙니다.} ("the source provided by Webcash Vietnam is
     * <b>merely a sample and does not have to be used as-is</b>"). Meanwhile §3.2 — the binding
     * figure — was cited nowhere in this codebase. The practical effect was that a 202 the gateway
     * answered at 25 s, <i>inside its own SLA</i>, was aborted by us. Per §5.4 that leaves an
     * indeterminate transfer which must then be confirmed by a 203 and must NOT be resent
     * ({@code 입금 이체의 결과가 확인 되지 않은 상태에서 재 거래 시 이중 이체 거래가 발생 할 수 있다}
     * — "re-transacting while the transfer result is unconfirmed can produce a double-transfer
     * transaction"). So the short timeout bought nothing and manufactured {@code PROC_STAT='6'} rows
     * on transfers that were about to succeed.
     *
     * <p><b>Coupled value — do not change in isolation, but the coupling is now weak.</b>
     * {@code ResultCheckJob}'s {@code lockAtMostFor} is derived as
     * {@code result-check.pass-deadline + ONE worst-case row}, where the worst row is
     * {@code connectTimeoutMs + readTimeoutMs + worstPaceMs}. It used to be
     * {@code batchSize x} that row, which made the ceiling grow linearly with these timeouts; since
     * {@code ResultCheckService.passDeadline} landed, a raise here moves the ceiling by seconds rather
     * than hours. {@code ResultCheckLockCeilingTest} asserts the relationship from these very fields by
     * reflection, so it still tells you when a recompute is needed.
     */
    private int readTimeoutMs = 60_000;

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
