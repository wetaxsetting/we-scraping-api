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
public class NxisasResult<RESULT> {

    @JsonProperty("API_SEQ")   private String sasSessionId;
    @JsonProperty("ErrorLang") private String errorLang;
    @JsonProperty("Output")    private NxisasOutput<RESULT> output;
}
