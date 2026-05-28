package com.kosign.wecms.domain.banking.nxisas.gateway.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One foreign-exchange rate row returned by Coocon's FOREIGN_EXCHANGE
 * job. Field names match the gateway's response shape verbatim — see
 * legacy {@code EXCH_0001_001.java:189-197} where the JSP reads
 * {@code foreignExchange.getString("baseCurrency")} etc.
 *
 * <p>Amount fields are returned as strings (legacy parity — the gateway
 * sometimes ships values like {@code "0"} for unset legs, or
 * thousand-separated numbers for some banks). Callers parse via
 * {@code BigDecimal}.
 */
@Getter
@Setter
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NxisasForeignExchangeEntry {

    @JsonProperty("baseCurrency")    private String baseCurrency;
    @JsonProperty("counterCurrency") private String counterCurrency;
    @JsonProperty("baseDate")        private String baseDate;
    @JsonProperty("baseTime")        private String baseTime;
    @JsonProperty("transferSell")    private String transferSell;
    @JsonProperty("transferBuy")     private String transferBuy;
    @JsonProperty("buy")             private String buy;
    @JsonProperty("sell")            private String sell;
    @JsonProperty("basicRate")       private String basicRate;
    @JsonProperty("basicTransfer")   private String basicTransfer;
}
