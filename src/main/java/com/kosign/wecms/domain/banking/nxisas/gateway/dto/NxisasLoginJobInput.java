package com.kosign.wecms.domain.banking.nxisas.gateway.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NxisasLoginJobInput {

    @JsonProperty("loginType")   private String loginType;
    @JsonProperty("loginID")     private String loginId;
    @JsonProperty("loginPWD")    private String loginPwd;
    @JsonProperty("loginPWD1")   private String loginPwd1;
    @JsonProperty("corporateID") private String corporateId;
    @JsonProperty("companyID")   private String companyId;
    @JsonProperty("countryCode") private String countryCode;
}
