package com.kosign.wecms.domain.banking.nxisas.gateway.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * One {@code cardArr[]} entry from a Coocon CARD_USING response &mdash;
 * a per-card wrapper around the scraped transaction list. Mirrors
 * legacy {@code kr.co.coocon.ibasews.cmo.NxisasCardUsingCardArr} per
 * {@code fnsh_0001_001_c001_act.jsp:1319-1323}:
 *
 * <pre>{@code
 * List<NxisasCardUsingCardArr> cardArrList = result.getcardArr();
 * NxisasCardUsingCardArr cardArr = cardArrList.get(0);
 * List<CARD_USING> cardUsings   = cardArr.getCardUsing();
 * }</pre>
 *
 * <p>For a single-card CARD_USING input, Coocon returns exactly one
 * {@code cardArr} entry whose {@code cardUsing} list is the
 * transaction set for that card over the requested date window. The
 * orchestrator only ever reads {@code cardArr.get(0)} because each
 * gateway call covers one card.
 */
@Getter
@Setter
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NxisasCardUsingCardArr {

    @JsonProperty("cardUsing") private List<NxisasCardUsingEntry> cardUsing;
}
