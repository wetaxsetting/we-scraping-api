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
public class NxisasOutput<RESULT> {

    @JsonProperty("DetailMessage") private String detailMessage;
    @JsonProperty("ErrorCode")     private String errorCode;
    @JsonProperty("Result")        private RESULT result;
}
