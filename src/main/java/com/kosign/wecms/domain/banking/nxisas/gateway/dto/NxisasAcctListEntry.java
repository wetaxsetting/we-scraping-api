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
    // Maturity date for term/fixed-deposit accounts. Documented as a
    // required field of the Coocon smart-gateway account-list response
    // (글로벌은행 API 1.0.6: "maturityDate String (필수) 만기일"). Was
    // previously unmodelled, so Jackson silently dropped it; the legacy
    // popup showed it from the local engine's acct.getMaturityDate().
    @JsonProperty("maturityDate") private String maturityDate;
}
