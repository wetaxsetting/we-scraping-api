package com.kosign.wecms.domain.banking.nxisas.gateway.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * BALANCE job result. Mirrors legacy
 * {@code kr.co.coocon.ibasews.cmo.BalanceFixedDepositResult} /
 * {@code BalanceForeignResult}.
 *
 * <p>Coocon may populate either {@code balance} (most adapters) or
 * {@code acctList} (a few foreign adapters reuse the ACCT_LIST shape)
 * — consumer reads whichever is non-empty.
 */
@Getter
@Setter
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NxisasBalanceJobResult {

    @JsonProperty("balance")  private List<NxisasBalanceEntry> balance;
    @JsonProperty("acctList") private List<NxisasBalanceEntry> acctList;
}
