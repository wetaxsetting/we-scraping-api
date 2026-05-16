package com.kosign.wecms.domain.banking.nxisas.gateway.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * BALANCE job input — fetches the current balance + available balance
 * for one account. Mirrors legacy
 * {@code kr.co.coocon.ibasews.cmo.BalanceFixedDepositInput} /
 * {@code BalanceForeignInput} (same two fields).
 *
 * <p>Used by the sync orchestrator to refresh {@code ACCT_MSTR.BAL}
 * and {@code ACCT_MSTR.REAL_AMT} after a successful TRANSACTION
 * scrape, mirroring legacy `fnsh_0001_001_c001_act.jsp` BALANCE job
 * handling.
 */
@Getter
@Setter
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NxisasBalanceJobInput {

    @JsonProperty("acctNum")  private String acctNum;
    @JsonProperty("currency") private String currency;
}
