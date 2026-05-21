package com.kosign.wecms.domain.payment.cardtransactions.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

/**
 * Card Transactions &gt; Update &mdash; per-bank period-limit lookup
 * request. Mirrors legacy
 * {@code nxisas_cardTranList_r001_act.jsp:23-29} which expects
 * {@code REC_IN = [{ BANK_CD, BANK_CERT_SEQ }, ...]}.
 *
 * <p>The popup's date-range validation uses the LONGEST per-bank cap
 * across every active card; the orchestrator returns one row per
 * input pair so the frontend can also surface the per-bank limit if
 * future UX needs it.
 */
public record CardTranListPeriodLimitRequest(
        @Valid
        @NotEmpty
        List<Input> recIn) {

    /** One row in the inbound list — mirrors legacy
     *  {@code REC_IN[i]} shape ({@code BANK_CD} + {@code BANK_CERT_SEQ}). */
    public record Input(
            @NotNull String bankCd,
            @NotNull BigDecimal bankCertSeq) {
    }
}
