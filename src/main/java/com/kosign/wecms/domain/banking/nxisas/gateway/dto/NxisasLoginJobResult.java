package com.kosign.wecms.domain.banking.nxisas.gateway.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Result of an NXISAS LOGIN job.
 *
 * <p>{@code callBackFunc} signals what the gateway expects next:
 * {@code "setOTP"} → caller must collect an OTP and POST an OTP job;
 * {@code "setCaptcha"} → captcha challenge; {@code "setQNA"} / {@code "QNA"}
 * → security-question challenge. When {@code callBackFunc} is null AND
 * {@code req} is null the login is complete and the next job
 * (typically ACCT_LIST) can be dispatched.
 *
 * <p>{@code req} shape varies by challenge kind:
 * <ul>
 *   <li>OTP / CAPTCHA → free-text string (prompt or image URL)</li>
 *   <li>QNA → nested object
 *       {@code { "API": "QNA", "req": [ { "HiddenValue", "Question" } ... ] }}
 *       — Shinhan VN's typical response (snapshot 2026-05-15).</li>
 * </ul>
 * Typed as {@link JsonNode} so both shapes deserialize. The
 * service-layer challenge classifier reads {@code callBackFunc}
 * to decide the kind and {@link #challengePrompt()} derives a
 * user-facing prompt string from whichever shape is present.
 */
@Getter
@Setter
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NxisasLoginJobResult {

    @JsonProperty("CallBackfunc") private String callBackFunc;
    @JsonProperty("req")          private JsonNode req;

    /**
     * Derive a user-facing prompt string from whichever {@code req}
     * shape the bank adapter returned.
     *
     * <ul>
     *   <li>Textual (OTP / CAPTCHA path): returns the raw text.</li>
     *   <li>QNA object: returns the JSON serialization so the frontend
     *       can parse the question list. The legacy
     *       {@code comm_0218_nxisas} JS pulls the nested
     *       {@code req[i].Question} entries the same way.</li>
     *   <li>Null / missing: returns {@code null}.</li>
     * </ul>
     */
    public String challengePrompt() {
        if (req == null || req.isNull()) return null;
        if (req.isTextual()) return req.asText();
        return req.toString();
    }
}
