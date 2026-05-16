package com.kosign.wecms.domain.banking.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Wire body for {@code POST /api/v1/banking/accounts/transactions-sync}.
 *
 * <p>Sent by the Account Inquiry &gt; "Bank Transaction Sync" popup when
 * the user clicks Sync. Carries the user's picked accounts (by natural
 * keys {@code BANK_CD} + {@code ACCT_NO}) plus the optional date-range +
 * Auto flag the popup collects.
 *
 * <p>The refactor R&amp;D scope does NOT run real bank scraping — the
 * stub service bumps {@code ACCT_MSTR.LST_LGN_SUC_DTM} and
 * {@code ACCT_BAL_LST_INQ_DTM} to {@code now()} for each selected row
 * (the same fields legacy {@code ACCT_MSTR_U002} writes — see
 * {@code fnsh_0001_001_c001_act.jsp:266-287}) and returns canned
 * zero-counts. No {@code ACCT_TRNS_HSTR} inserts.
 *
 * <p>{@code from_dt} / {@code to_dt} / {@code auto} ride through the wire
 * for legacy parity and forward-compatibility but the stub ignores them;
 * keeping them on the contract avoids a wire-shape change when the real
 * sync pipeline lands.
 */
public record TransactionsSyncRequest(

        @NotEmpty
        @Valid
        List<AccountKey> accounts,

        /** Inclusive start of the sync window — ISO {@code yyyy-MM-dd}.
         *  Ignored by the stub; honoured by the real pipeline. */
        @Size(max = 10)
        String fromDt,

        /** Inclusive end of the sync window — ISO {@code yyyy-MM-dd}.
         *  Ignored by the stub. */
        @Size(max = 10)
        String toDt,

        /** Auto-sync flag (whether the operation also schedules itself
         *  to repeat). Ignored by the stub; persisted by the real
         *  pipeline into {@code BUSI_ENV_SET_INFM}. */
        Boolean auto) {

    /**
     * Natural-key pair identifying one {@code ACCT_MSTR} row inside the
     * caller's tenant. Tenant comes from the JWT — never from the wire.
     */
    public record AccountKey(

            @NotBlank
            @Size(max = 20)
            String bankCd,

            @NotBlank
            @Size(max = 100)
            String acctNo) { }
}
