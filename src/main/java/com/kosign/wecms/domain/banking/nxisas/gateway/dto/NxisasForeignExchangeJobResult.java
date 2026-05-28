package com.kosign.wecms.domain.banking.nxisas.gateway.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Coocon FOREIGN_EXCHANGE output — carries the per-round
 * {@code standPoint} (round label / timestamp) and a list of
 * {@link NxisasForeignExchangeEntry} rows, one per currency pair.
 * Mirrors the legacy JSP's
 * {@code resultData.getJSONArray("foreignExchange")} +
 * {@code resultData.getString("standPoint")} reads in
 * {@code EXCH_0001_001.java:186-189}.
 */
@Getter
@Setter
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NxisasForeignExchangeJobResult {

    @JsonProperty("standPoint")      private String standPoint;
    @JsonProperty("foreignExchange") private List<NxisasForeignExchangeEntry> foreignExchange;
}
