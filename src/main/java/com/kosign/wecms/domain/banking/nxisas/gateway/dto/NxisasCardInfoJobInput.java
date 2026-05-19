package com.kosign.wecms.domain.banking.nxisas.gateway.dto;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Collections;
import java.util.Map;

/**
 * CARD_INFO job has no input body — Coocon expects an empty JSON
 * object {@code {}} in the {@code Input} slot. Mirrors
 * {@link NxisasAcctListJobInput} verbatim: the {@link JsonAnyGetter}
 * forces Jackson to serialise the bean as {@code {}} instead of
 * throwing on {@code FAIL_ON_EMPTY_BEANS}.
 *
 * <p>Per the legacy {@code nxisas_fnsh_r001_act.jsp:357-368} CARD_INFO
 * branch, Coocon's {@code CardInfoInput} class is itself an empty
 * marker DTO that just signals "give me the card list after LOGIN."
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NxisasCardInfoJobInput {

    @JsonAnyGetter
    public Map<String, Object> empty() {
        return Collections.emptyMap();
    }
}
