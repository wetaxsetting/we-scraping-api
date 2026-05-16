package com.kosign.wecms.domain.banking.nxisas.gateway.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * TRANSACTION job input — mirrors legacy
 * {@code kr.co.coocon.ibasews.cmo.TransactionFixedDepositInput} +
 * {@code TransForeignInput} (both ship the same four fields). One
 * call per (account, currency) pair; Coocon returns the transaction
 * list for the {@code startDate..endDate} window inclusive.
 *
 * <p>Date format is {@code yyyyMMdd} per legacy parity
 * (legacy `fnsh_0001_001_c001_act.jsp` builds these via
 * {@code DateTimeUtils.format(..., "yyyyMMdd")}).
 */
@Getter
@Setter
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NxisasTransactionJobInput {

    @JsonProperty("acctNum")   private String acctNum;
    @JsonProperty("currency")  private String currency;
    @JsonProperty("startDate") private String startDate;
    @JsonProperty("endDate")   private String endDate;
}
