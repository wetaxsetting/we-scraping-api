package com.kosign.wecms.domain.banking.bankcredentials.dto;

import java.math.BigDecimal;

/**
 * One row of the bank-credentials list. The password ciphertext is
 * NEVER serialized to the wire — only existence is implied by the
 * row. The plaintext password is reachable only via the scraping
 * flow, which fetches it server-side from
 * {@link com.kosign.wecms.domain.banking.bankcredentials.service.BankCredentialService}.
 */
public record BankCredentialRow(
        BigDecimal seqNo,
        String bankCd,
        String username,
        String acctListInqStts,
        String acctListInqMsg,
        String lstAcctListInqDt,
        String regDtm,
        String modDtm
) { }
