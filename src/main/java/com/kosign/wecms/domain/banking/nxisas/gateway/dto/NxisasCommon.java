package com.kosign.wecms.domain.banking.nxisas.gateway.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * NXISAS gateway "Common" envelope. Ported from legacy
 * {@code kr.co.coocon.ibasews.cmo.NxisasCommon}. Field names + JSON
 * property names preserve the exact wire shape the Webcash smart-
 * gateway expects.
 */
@Getter
@Setter
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NxisasCommon {

    @JsonProperty("Auth_key")  private String authKey;
    @JsonProperty("Tr_seq")    private String trSeq;
    @JsonProperty("Gw_sys_no") private String gwSysNo;
    @JsonProperty("Ss_sys_no") private String ssSysNo;
    @JsonProperty("Auth_req")  private String authReq;
    @JsonProperty("Thread_no") private String threadNo;
    @JsonProperty("Lbs_no")    private String lbsNo;
    @JsonProperty("Result_mg") private String resultMsg;
    @JsonProperty("Result_cd") private String resultCd;
}
