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
        String openDate
) { }
