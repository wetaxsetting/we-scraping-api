package com.kosign.wecms.domain.banking.nxisas.gateway.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Result of an NXISAS LOGIN job.
 *
 * <p>{@code callBackFunc} signals what the gateway expects next:
 * {@code "setOTP"} → caller must collect an OTP and POST an OTP job;
 * {@code "setCaptcha"} → captcha challenge; {@code "setQNA"} → QnA.
 * When {@code callBackFunc} is null AND {@code req} is null the
 * login is complete and the next job (typically ACCT_LIST) can be
 * dispatched.
 *
 * <p>{@code req} carries the challenge prompt / image URL the user
 * needs to see (free text).
 */
@Getter
@Setter
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NxisasLoginJobResult {

    @JsonProperty("CallBackfunc") private String callBackFunc;
    @JsonProperty("req")          private String req;
}
