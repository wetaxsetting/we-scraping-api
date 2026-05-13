package com.kosign.wecms.domain.banking.dto.coocon;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NxisasCommon {
    @JsonProperty("Auth_req")
    private String authReq;

    @JsonProperty("Gw_sys_no")
    private String gwSysNo;

    @JsonProperty("Lbs_no")
    private String lbsNo;

    @JsonProperty("Result_cd")
    private String resultCd;

    @JsonProperty("Result_mg")
    private String resultMsg;

    @JsonProperty("Ss_sys_no")
    private String ssSysNo;

    @JsonProperty("Thread_no")
    private String threadNo;

    @JsonProperty("Auth_key")
    private String authKey;

    @JsonProperty("Tr_seq")
    private String trSeq;
}
