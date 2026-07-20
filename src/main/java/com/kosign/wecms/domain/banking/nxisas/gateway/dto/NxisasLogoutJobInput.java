package com.kosign.wecms.domain.banking.nxisas.gateway.dto;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Collections;
import java.util.Map;

/**
 * LOGOUT job has no input body — Coocon expects an empty JSON object
 * {@code {}} in the {@code Input} slot. Mirrors {@link NxisasCardInfoJobInput}
 * verbatim: the {@link JsonAnyGetter} forces Jackson to serialise the bean as
 * {@code {}} instead of throwing on {@code FAIL_ON_EMPTY_BEANS}.
 *
 * <p>Sent on a live gateway continuation (SAS_SessionID + Common fields) so the
 * bank-adapter session opened by the prior LOGIN is closed. Consumed only by
 * {@code BankTransactionSyncService.cancel(...)} as a best-effort teardown —
 * legacy {@code nxisas_v2.js makeLogOutInput} → {@code CooconiSASNX} parity.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NxisasLogoutJobInput {

    @JsonAnyGetter
    public Map<String, Object> empty() {
        return Collections.emptyMap();
    }
}
