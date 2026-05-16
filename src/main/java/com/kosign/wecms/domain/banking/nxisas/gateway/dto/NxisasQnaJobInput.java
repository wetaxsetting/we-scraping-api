package com.kosign.wecms.domain.banking.nxisas.gateway.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Input payload for an NXISAS QNA resume call. Mirrors the legacy
 * {@code nxisas_v2.js:222-228} shape — the gateway expects:
 *
 * <pre>
 * "Input": { "res": { "Question", "HiddenValue", "Answer" } }
 * </pre>
 *
 * The user picks ONE question from the LOGIN response's question
 * list and types its answer; only the picked
 * {@code (Question, HiddenValue, Answer)} triple is shipped.
 */
@Getter
@Setter
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NxisasQnaJobInput {

    @JsonProperty("res")
    private QnaResponse res;

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class QnaResponse {
        @JsonProperty("Question")    private String question;
        @JsonProperty("HiddenValue") private String hiddenValue;
        @JsonProperty("Answer")      private String answer;
    }
}
