package com.kosign.wecms.domain.banking.nxisas.gateway.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NxisasApiOut<RESULT> {

    @JsonProperty("Common")        private NxisasCommon common;
    @JsonProperty("ResultList")    private List<NxisasResult<RESULT>> resultList;
    @JsonProperty("SAS_SessionID") private String sasSessionID;
}
