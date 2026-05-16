package com.kosign.wecms.domain.banking.nxisas.gateway.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * TRANSACTION job result — list of transactions inside the requested
 * date window. Mirrors legacy
 * {@code kr.co.coocon.ibasews.cmo.TransactionFixedDepositResult} /
 * {@code TransForeignResult} which both wrap a
 * {@code List<TRANSACTION_FIXED_DEPOSIT>} accessor named
 * {@code transaction}.
 *
 * <p>Empty / null list means the bank confirmed no transactions in
 * the window — NOT a failure. Caller checks
 * {@link NxisasApiOut#getOutput()}'s {@code ErrorCode} for the
 * pass/fail signal.
 */
@Getter
@Setter
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NxisasTransactionJobResult {

    @JsonProperty("transaction") private List<NxisasTransactionEntry> transaction;
}
