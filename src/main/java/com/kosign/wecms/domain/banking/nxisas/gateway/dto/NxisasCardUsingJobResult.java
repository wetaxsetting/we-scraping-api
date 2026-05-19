package com.kosign.wecms.domain.banking.nxisas.gateway.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Coocon CARD_USING output. Mirrors {@link NxisasTransactionJobResult}
 * — a single list-valued field carrying one {@link NxisasCardUsingEntry}
 * per transaction Coocon scraped from the bank's card statement.
 */
@Getter
@Setter
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NxisasCardUsingJobResult {

    @JsonProperty("tranList") private List<NxisasCardUsingEntry> tranList;
}
