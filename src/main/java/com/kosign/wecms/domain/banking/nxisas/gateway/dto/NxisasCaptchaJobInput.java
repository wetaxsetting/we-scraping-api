package com.kosign.wecms.domain.banking.nxisas.gateway.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Input payload for an NXISAS CAPTCHA resume call. Wire shape is
 * identical to OTP — {@code Input.res = "<typed text>"} — but the
 * job discriminator is {@link NxisasJob#CAPTCHA} so Coocon routes
 * to the right adapter step. Mirrors legacy nxisas_v2.js:243.
 */
@Getter
@Setter
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NxisasCaptchaJobInput {

    @JsonProperty("res")
    private String res;
}
