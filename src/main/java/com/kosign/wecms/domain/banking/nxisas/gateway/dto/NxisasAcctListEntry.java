package com.kosign.wecms.domain.banking.nxisas.gateway.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One row of {@code NxisasAcctListJobResult.acctList}. */
@Getter
@Setter
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NxisasAcctListEntry {

    @JsonProperty("acctName")     private String acctName;
    @JsonProperty("acctNum")      private String acctNum;
    @JsonProperty("acctType")     private String acctType;
    @JsonProperty("currency")     private String currency;
    @JsonProperty("balance")      private String balance;
    @JsonProperty("availableBal") private String availableBal;
    @JsonProperty("branchName")   private String branchName;
    @JsonProperty("openDate")     private String openDate;
}
