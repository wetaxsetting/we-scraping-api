package com.kosign.wecms.domain.banking.nxisas.gateway.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/** Outer envelope for NXISAS gateway requests. */
@Getter
@Setter
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NxisasApiIn<INPUT> {

    @JsonProperty("Common")         private NxisasCommon common;
    @JsonProperty("InputList")      private List<NxisasThread<INPUT>> inputList;
    @JsonProperty("TimeoutRuntime") private String timeOutRunTime;
    @JsonProperty("SAS_SessionID")  private String sasSessionID;
    @JsonProperty("Dsnc")           private String dnsc;
}
