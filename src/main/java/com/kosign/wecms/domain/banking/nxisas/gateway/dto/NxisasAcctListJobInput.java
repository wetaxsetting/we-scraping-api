package com.kosign.wecms.domain.banking.nxisas.gateway.dto;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Collections;
import java.util.Map;

/**
 * ACCT_LIST job has no input body — Coocon expects an empty JSON
 * object {@code {}} in the {@code Input} slot. Without an explicit
 * serializable surface Jackson throws "no properties discovered to
 * create BeanSerializer" because {@code FAIL_ON_EMPTY_BEANS} defaults
 * to true. The {@link JsonAnyGetter} below returns an empty map so
 * the bean serializes to {@code {}} regardless of that setting.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NxisasAcctListJobInput {

    @JsonAnyGetter
    public Map<String, Object> empty() {
        return Collections.emptyMap();
    }
}
