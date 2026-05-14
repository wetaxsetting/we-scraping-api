package com.kosign.wecms.domain.banking.nxisas.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Caller-supplied response to a previously-issued challenge (OTP /
 *  captcha / QnA answer). For the spike only the OTP path is wired. */
public record SubmitChallengeRequest(
        @NotBlank @Size(max = 200) String value
) { }
