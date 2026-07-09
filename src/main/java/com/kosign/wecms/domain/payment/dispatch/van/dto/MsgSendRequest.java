package com.kosign.wecms.domain.payment.dispatch.van.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Business payload of the {@code WGVN_MSG_SEND_001} operational-alert call — the
 * decrypted JSON that travels inside the {@link EfsEnvelope} {@code EV} field.
 * Refactor of the legacy {@code MSGSendAPIThread} inner {@code param} object
 * (legacy {@code thread/MSGSendAPIThread.java:59-63}).
 *
 * <p><b>Vendor casing is mandatory.</b> The gateway expects the exact legacy keys
 * {@code appDiv} / {@code grpID} / {@code content} / {@code mode} — <b>not</b> the
 * snake_case the global {@code SNAKE_CASE} strategy would produce. Each component
 * is pinned with {@link JsonProperty} (an explicit {@code @JsonProperty} overrides
 * the global naming strategy), and field declaration order is kept as
 * {@code appDiv, grpID, content, mode} so the serialized JSON is byte-identical to
 * the legacy {@code JSONObject} insertion order (the {@code EV} plaintext depends
 * on it — see OQ-1 crypto-parity in the implementation plan).
 *
 * <p>{@code appDiv} and {@code grpID} are legacy protocol constants (always
 * {@code "0"} = Slack and {@code "WECMSBATCH"}); they are supplied by
 * {@link com.kosign.wecms.domain.payment.dispatch.alert.OpsAlertService}, never
 * configured.
 *
 * @param appDiv  application division — {@code "0"} (Slack)
 * @param grpID   channel-mapping id — {@code "WECMSBATCH"}
 * @param content the alert message body
 * @param mode    system/environment marker (active Spring profile)
 */
public record MsgSendRequest(
        @JsonProperty("appDiv") String appDiv,
        @JsonProperty("grpID") String grpID,
        @JsonProperty("content") String content,
        @JsonProperty("mode") String mode
) {
}
