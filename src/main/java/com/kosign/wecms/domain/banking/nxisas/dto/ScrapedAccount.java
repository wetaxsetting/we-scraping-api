package com.kosign.wecms.domain.banking.nxisas.dto;

/** One row of the live gateway acct-list result, surfaced verbatim to the wire. */
public record ScrapedAccount(
        String acctName,
        String acctNum,
        String acctType,
        String currency,
        String balance,
        String availableBal,
        String branchName,
        String openDate,
        /** Maturity date for term/fixed-deposit accounts (Coocon
         *  acct-list field {@code maturityDate}; legacy popup column
         *  fed by {@code acct.getMaturityDate()}). May be blank for
         *  demand-deposit / current accounts. */
        String maturityDate,
        /** Legacy {@code ACCT_TYPE_CD} ({@code 02}–{@code 08}) resolved
         *  from the raw {@link #acctType} via {@code NXISAS_ACCT_TYPE_MAPPING}
         *  (legacy {@code fnsh_0101_001_r001_act.jsp:164-176} parity).
         *  {@code null} when no mapping row exists (or the reference table
         *  is not seeded in this env) — the picker then leaves Account Type
         *  blank for manual selection. Distinct from {@link #acctType},
         *  which is the bank's free-text label and is never shown directly. */
        String acctTypeCd
) { }
