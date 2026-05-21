package com.kosign.wecms.domain.banking.nxisas.gateway.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Coocon CARD_INFO output. Mirrors {@link NxisasAcctListJobResult} —
 * a single list-valued field carrying one entry per scraped card.
 */
@Getter
@Setter
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NxisasCardInfoJobResult {

    @JsonProperty("cardList") private List<NxisasCardInfoEntry> cardList;
}
