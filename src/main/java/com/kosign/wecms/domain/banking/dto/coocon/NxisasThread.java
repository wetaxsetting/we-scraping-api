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
public class NxisasThread<T> {
    @JsonProperty("Class")
    private String category;

    @JsonProperty("Module")
    private String module;

    @JsonProperty("Job")
    private String job;

    @JsonProperty("ErrorLang")
    private String errorLang;

    @JsonProperty("Input")
    private T input;
}
