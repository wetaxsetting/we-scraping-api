package com.kosign.wecms.domain.banking.nxisas.gateway.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One row of {@link NxisasBalanceJobResult#getBalance()}. Mirrors
 * legacy {@code kr.co.coocon.ibasews.cmo.BALANCE_FIXED_DEPOSIT} which
 * extends {@code ACCT_FIXED_DEPOSIT}.
 *
 * <p>String-typed numerics for the same reason
 * {@link NxisasTransactionEntry} uses strings — Coocon ships
 * locale-specific separators. Consumer parses via
 * {@code new BigDecimal(s)} after stripping commas.
 */
@Getter
@Setter
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NxisasBalanceEntry {

    @JsonProperty("acctNum")      private String acctNum;
    @JsonProperty("acctType")     private String acctType;
    @JsonProperty("branchName")   private String branchName;
    @JsonProperty("currency")     private String currency;
    @JsonProperty("balance")      private String balance;
    @JsonProperty("availableBal") private String availableBal;
    @JsonProperty("openDate")     private String openDate;
    @JsonProperty("rate")         private String rate;
    @JsonProperty("maturityDate") private String maturityDate;
    @JsonProperty("principal")    private String principal;
}
