package com.kosign.wecms.domain.banking.nxisas.gateway.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One entry in NXISAS's {@code InputList}. Carries the (Module, Class,
 * Job, Input) tuple the gateway dispatches on. Ported from legacy
 * {@code kr.co.coocon.ibasews.cmo.NxisasThread} — refactor drops the
 * legacy {@code @AllArgsConstructor} "guess Job from Input type"
 * convenience constructor: callers in the gateway client set Job
 * explicitly, which is clearer and matches the spike's narrower scope.
 */
@Getter
@Setter
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NxisasThread<INPUT> {

    @JsonProperty("Module")    private String module;
    @JsonProperty("Class")     private String category;
    @JsonProperty("Job")       private NxisasJob job;
    @JsonProperty("ErrorLang") private String errorLang;
    @JsonProperty("Input")     private INPUT input;
}
