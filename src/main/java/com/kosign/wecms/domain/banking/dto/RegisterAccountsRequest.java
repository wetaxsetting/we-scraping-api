package com.kosign.wecms.domain.banking.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * Wire body for {@code POST /api/v1/banking/accounts/register}.
 *
 * <p>Sent by the Select Bank Accounts dialog after a successful
 * NXISAS scrape. Carries:
 *
 * <ul>
 *   <li>{@code credentialSeqNo} — the {@code BANK_CERT_INFO.SEQ_NO}
 *       that the scrape was run against. The service reads
 *       {@code BANK_CD} off the credential row (single source of
 *       truth — the picker can't smuggle in a different bank).</li>
 *   <li>{@code accounts} — the picked rows from the scrape result.
 *       Each row carries the natural keys ({@code accountNo}/
 *       {@code currency}/{@code accountTypeCd}) plus the user's
 *       editable nickname / details cells.</li>
 * </ul>
 *
 * <p>Mirrors legacy {@code fnsh_0101_001_c001} input shape
 * (comm_0218_nxisas.ts:1188).
 */
public record RegisterAccountsRequest(

        @NotNull
        BigDecimal credentialSeqNo,

        @NotEmpty
        @Valid
        List<AccountEntry> accounts) {

    /**
     * One account the user wants to register. Field set is the
     * intersection of (NXISAS scrape result) + (user edits in the
     * Select Bank Accounts dialog) + (legacy {@code fnsh_0101_001_c001}
     * {@code REC_IN} payload).
     */
    public record AccountEntry(

            @NotBlank
            @Size(max = 100)
            String accountNo,

            @NotBlank
            @Size(max = 3)
            String currency,

            /** Legacy {@code ACCT_TYPE_CD} — 02..08 per
             *  {@link com.kosign.wecms.common.enums.AcctTypeCd}.
             *  Empty / null is allowed when the picker leaves the
             *  Account Type dropdown unset; the service stores
             *  {@code "03"} (current) as the safe default to satisfy
             *  the NOT NULL column. */
            @Size(max = 2)
            String acctTypeCd,

            /** Optional nickname the user typed. May be blank — the
             *  legacy {@code wb_format_acct_nick_nm} formula
             *  synthesises a display label from {@code BANK_CD} +
             *  {@code ACCT_NO} on read when {@code ACCT_NICK_NM} is
             *  empty, so no value is needed for the table to render. */
            @Size(max = 50)
            String accountNickname,

            /** Free-form description / memo (legacy {@code CONT}).
             *  Renders as the Settings &gt; Bank Account "Description"
             *  cell. Optional. */
            @Size(max = 200)
            String accountDetails,

            /** Real-name string from the bank adapter (legacy
             *  {@code ACCT_NM}). Optional; populated when the scrape
             *  returned a holder name. */
            @Size(max = 100)
            String acctNm,

            /** Opening balance from the scrape (legacy {@code BAL}).
             *  Used to seed the inquiry view; nightly batches refresh
             *  it. */
            BigDecimal bal) { }
}
