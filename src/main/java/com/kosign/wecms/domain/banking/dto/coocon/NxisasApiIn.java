package com.kosign.wecms.domain.banking.dto.coocon;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NxisasApiIn<T> {
    @JsonProperty("SAS_SessionID")
    private String sasSessionID;

    @JsonProperty("Dsnc")
    private String dnsc;

    @JsonProperty("Common")
    private NxisasCommon common;

    @JsonProperty("InputList")
    private List<NxisasThread<T>> inputList;
}
