package com.kosign.wecms.domain.banking.nxisas.gateway.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Coocon CARD_USING output. Mirrors the legacy CMO class
 * {@code kr.co.coocon.ibasews.cmo.CardUsingResult} (see
 * {@code fnsh_0001_001_c001_act.jsp:1319} &mdash;
 * {@code cardUsingJob.getOutput().getResult().getcardArr()}).
 *
 * <p>Coocon wraps the scraped transaction list in a two-level shape:
 * a top-level {@code cardArr} list, each entry of which carries a
 * {@code cardUsing} list of transactions. For a single-card request
 * the orchestrator reads {@code cardArr[0].cardUsing}; multi-card
 * responses can theoretically exist but every refactor call covers
 * exactly one card so the helper
 * {@code CardTransactionSyncService} only reads the first entry.
 */
@Getter
@Setter
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NxisasCardUsingJobResult {

    @JsonProperty("cardArr") private List<NxisasCardUsingCardArr> cardArr;
}
