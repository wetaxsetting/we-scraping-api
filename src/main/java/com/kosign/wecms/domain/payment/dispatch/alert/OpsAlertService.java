package com.kosign.wecms.domain.payment.dispatch.alert;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kosign.wecms.domain.payment.dispatch.van.client.EfsApiClient;
import com.kosign.wecms.domain.payment.dispatch.van.client.EfsProperties;
import com.kosign.wecms.domain.payment.dispatch.van.client.VanApiKeyService;
import com.kosign.wecms.domain.payment.dispatch.van.crypto.EfsCryptoService;
import com.kosign.wecms.domain.payment.dispatch.van.dto.EfsEnvelope;
import com.kosign.wecms.domain.payment.dispatch.van.dto.MsgSendRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Operational Slack alert to the VAN gateway ({@code WGVN_MSG_SEND_001}) — refactor
 * of the legacy {@code util.MsgSendUtil} + {@code thread.MSGSendAPIThread}. Posts a
 * batch ops notification (error / cutoff / result) to the {@code WECMSBATCH} Slack
 * channel through the shared EFS transport.
 *
 * <p><b>Ops observability only</b> — this is NOT customer-facing (no SMS / Kakao /
 * Email / in-app notification; the in-app header bell is the unrelated
 * {@code domain/payment/notification} BLTB module).
 *
 * <p><b>Fire-and-forget</b> — {@link #notify} runs on the dedicated
 * {@link OpsAlertAsyncConfig#EXECUTOR} via {@code @Async}, wraps its whole body in
 * try/catch, logs failures at {@code warn}, and never propagates: a failed or slow
 * alert can never block or fail a payment dispatch (legacy parity — the old raw
 * {@code Thread} swallowed every error).
 *
 * <p><b>Gated OFF by default</b> — when {@code wecms.dispatch.alert.enabled=false}
 * (the default, read via {@code @Value}) {@link #notify} is an immediate no-op and
 * no envelope is built or sent. Even when enabled, the underlying
 * {@link EfsApiClient} is the {@code MockEfsApiClient} unless
 * {@code wecms.dispatch.efs.enabled=true}. Mirrors the {@code recipient-inquiry}
 * gate pattern in {@code FirmBankingInquiryService}.
 *
 * <p><b>appDiv / grpID are legacy protocol constants</b> (verified: hardcoded
 * {@code "0"} / {@code "WECMSBATCH"} at all 10 legacy call sites, never
 * configurable) — see {@code MsgSendUtil.SendMsg}. {@code TNO} is the fixed legacy
 * literal {@code "M9999999"}. Only the enable flag is configuration.
 *
 * <p><b>Envelope {@code ID} = VAN org code (audit M-12 / H2).</b> Legacy
 * {@code MSGSendAPIThread:77} always sends {@code vanOrgCd} as the envelope {@code ID}
 * (never the partner id — that is a per-request contract, distinct from the alert
 * channel). This is the ID the Slack gateway keys the channel on, so it is sent
 * verbatim here, unlike the request/callback legs which may use {@code partner-id}.
 *
 * <p><b>Environment / server marker (audit F3/F4 / H2).</b> The alert carries the
 * legacy {@code ServerGB} label {@code [ENV][WAS_NO]} appended to the first content
 * line (legacy {@code CommUtil.getServerGB}) and the legacy {@code mode} marker in
 * the business JSON. Both derive from {@code wecms.dispatch.alert.env-mode}
 * ({@code T}/{@code U}/{@code R} → DEV/UAT/PROD, legacy {@code ENV_MODE}) +
 * {@code wecms.dispatch.alert.was-no}. When {@code env-mode} is unset (local R&D) the
 * active Spring profile is used as a safe fallback so nothing is silently blank.
 *
 * <p>Security: the per-VAN {@code API_KEY} is read from {@code TB_VAN_API_INFO} via
 * {@link VanApiKeyService} — never from config/env. Logging never emits
 * {@code API_KEY}, {@code EV}, {@code VV}, or the message content.
 */
@Slf4j
@Service
public class OpsAlertService {

    /** Legacy {@code appDiv} constant — {@code "0"} = Slack. Never configurable. */
    private static final String APP_DIV = "0";
    /** Legacy {@code grpID} constant — the {@code WECMSBATCH} Slack channel. Never configurable. */
    private static final String GRP_ID = "WECMSBATCH";
    /** Legacy fixed transaction number for the message-send envelope. */
    private static final String TNO = "M9999999";

    private static final DateTimeFormatter RQ_DTIME_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final ZoneId ICT = ZoneId.of("Asia/Ho_Chi_Minh");

    private final EfsApiClient efsApiClient;
    private final EfsCryptoService efsCryptoService;
    private final VanApiKeyService vanApiKeyService;
    private final EfsProperties efsProperties;
    private final ObjectMapper objectMapper;
    private final Environment environment;

    /** Master gate — OFF by default. The whole subsystem is inert until flipped on. */
    @Value("${wecms.dispatch.alert.enabled:false}")
    private boolean enabled;

    /**
     * Legacy {@code ENV_MODE} marker ({@code T}=DEV / {@code U}=UAT / {@code R}=PROD).
     * Drives both the business-JSON {@code mode} field and the {@code ServerGB} label.
     * Blank (default) → falls back to the active Spring profile (local R&D).
     */
    @Value("${wecms.dispatch.alert.env-mode:}")
    private String envMode;

    /** Legacy {@code WAS_NO} — the server/instance number shown in the {@code ServerGB} label. */
    @Value("${wecms.dispatch.alert.was-no:}")
    private String wasNo;

    public OpsAlertService(EfsApiClient efsApiClient,
                           EfsCryptoService efsCryptoService,
                           VanApiKeyService vanApiKeyService,
                           EfsProperties efsProperties,
                           ObjectMapper objectMapper,
                           Environment environment) {
        this.efsApiClient = efsApiClient;
        this.efsCryptoService = efsCryptoService;
        this.vanApiKeyService = vanApiKeyService;
        this.efsProperties = efsProperties;
        this.objectMapper = objectMapper;
        this.environment = environment;
    }

    /**
     * Post an ops alert asynchronously. No-op when disabled or when either argument
     * is blank. Never throws — all failures are logged at {@code warn} and swallowed.
     *
     * @param vanOrgCd the VAN organisation code (envelope {@code ID} + key lookup)
     * @param content  the human-readable alert body (may contain diagnostic fields)
     */
    @Async(OpsAlertAsyncConfig.EXECUTOR)
    public void notify(String vanOrgCd, String content) {
        if (!enabled) {
            return;
        }
        if (!StringUtils.hasText(vanOrgCd) || !StringUtils.hasText(content)) {
            log.warn("ops-alert skipped — blank vanOrgCd or content");
            return;
        }
        try {
            String apiKey = vanApiKeyService.getApiKey(vanOrgCd);

            // F3 (audit): re-attach the legacy ServerGB [ENV][WAS_NO] marker to the first content
            // line (legacy CommUtil.getServerGB — "batchname [ENV][WAS_NO]\nfields...").
            String labelledContent = withServerLabel(content);
            MsgSendRequest payload = new MsgSendRequest(APP_DIV, GRP_ID, labelledContent, resolveMode());
            String businessJson = objectMapper.writeValueAsString(payload);

            String rqDtime = LocalDateTime.now(ICT).format(RQ_DTIME_FMT);
            String ev = efsCryptoService.encryptEv(businessJson, apiKey, rqDtime);
            String vv = efsCryptoService.buildVv(businessJson, apiKey);
            // M-12 (audit): the alert-channel envelope ID is ALWAYS the VAN org code (legacy
            // MSGSendAPIThread:77), never the partner id — this is the channel-mapping key.
            String id = vanOrgCd;

            EfsEnvelope request = new EfsEnvelope(
                    id, rqDtime, null, TNO, ev, vv,
                    EfsEnvelope.EM_AES, EfsEnvelope.VM_HMAC_SHA256, null, null);

            EfsEnvelope response = efsApiClient.send(EfsApiClient.EFS_MSG, request);

            String rc = response == null ? null : response.rc();
            if (rc == null || !"0000".equals(rc)) {
                // Legacy parity: log only, never fail the caller (MSGSendAPIThread:115).
                log.warn("ops-alert non-success vanOrgCd={} rc={}", vanOrgCd, rc);
            }
        } catch (Exception e) {
            // Fire-and-forget: an alert failure must never affect the dispatch flow.
            log.warn("ops-alert send failed vanOrgCd={} — swallowed", vanOrgCd, e);
        }
    }

    /**
     * Legacy {@code mode} marker for the business JSON (legacy {@code MSGSendAPIThread:63}
     * = {@code ENV_MODE}). Uses the configured {@code env-mode} ({@code T}/{@code U}/{@code R})
     * when set; otherwise falls back to the active Spring profile (local R&D), or {@code "-"}.
     */
    private String resolveMode() {
        if (StringUtils.hasText(envMode)) {
            return envMode;
        }
        String[] profiles = environment.getActiveProfiles();
        return profiles.length > 0 ? profiles[0] : "-";
    }

    /**
     * Append the legacy {@code ServerGB} label {@code [ENV][WAS_NO]} to the first line of the
     * content (mirrors {@code CommUtil.getServerGB}: {@code T}→DEV / {@code U}→UAT / {@code R}→PROD,
     * else the raw mode; {@code WAS_NO} verbatim). Placing it on line 1 matches the legacy
     * {@code "batchname " + ServerGB + "\n" + fields} shape.
     */
    private String withServerLabel(String content) {
        String label = serverLabel();
        int nl = content.indexOf('\n');
        if (nl < 0) {
            return content + " " + label;
        }
        return content.substring(0, nl) + " " + label + content.substring(nl);
    }

    /** Build the {@code [ENV][WAS_NO]} marker (legacy {@code CommUtil.getServerGB}). */
    private String serverLabel() {
        String mode = resolveMode();
        String env;
        switch (mode) {
            case "T" -> env = "DEV";
            case "U" -> env = "UAT";
            case "R" -> env = "PROD";
            default  -> env = mode;
        }
        return "[" + env + "][" + (wasNo == null ? "" : wasNo) + "]";
    }
}
