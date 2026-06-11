package com.kosign.wecms.domain.banking.ownaccounttransfer.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Body for
 * {@code POST /api/v1/banking/own-account-transfer/scraping-result}.
 * The browser bundle (local NXiSAS engine) runs the bank scrape,
 * captures the result, and POSTs it here verbatim. Spring re-validates
 * ownership / idempotency / amount / currency and writes the canonical
 * row to {@code NAPAS_REMIT_TRNS_HSTR} — the same legacy contract
 * {@code trns_0002_001_c001_act.jsp} writes.
 *
 * <p>Path A scope (OQ-A, PO 2026-05-18): OAT is own→own within the same
 * tenant, VND only. Both source and target must be active VND rows in
 * the caller's own {@code ACCT_MSTR}. External NAPAS recipients are
 * deferred to a future MR.
 *
 * <p>Wire shape is lowercase snake_case via the global Jackson naming
 * strategy.
 */
public record ScrapingResultIntakeRequest(

        /**
         * Client-generated UUID (32–64 alphanumeric chars). Recorded in
         * the audit trail and echoed back on the response for
         * client-side correlation. NOTE: the legacy JEX
         * {@code NAPAS_REMIT_TRNS_HSTR} table has no idempotency column,
         * so this key is NOT persisted and there is no server-side
         * replay/dedup — each POST records a fresh row.
         */
        @NotBlank
        @Pattern(regexp = "[A-Za-z0-9-]{32,64}",
                 message = "{validation.own_account_transfer.idempotency_key}")
        String idempotencyKey,

        /**
         * {@code SAME_BANK} (source and target share BANK_CD) or
         * {@code NAPAS} (cross-bank). Determines which engine job the
         * frontend invoked + which validation branch the service takes.
         */
        @NotBlank
        @Pattern(regexp = "SAME_BANK|NAPAS",
                 message = "{validation.own_account_transfer.transfer_type}")
        String transferType,

        @NotBlank @Pattern(regexp = "\\d{8}") String sourceBankCd,
        @NotBlank @Size(max = 100)             String sourceAcctNo,

        /** Required for {@code SAME_BANK}; optional for {@code NAPAS}
         *  (server resolves from {@code targetNapasBankCd} when null,
         *  per OQ-A scope where the target is also an own account). */
        @Pattern(regexp = "\\d{8}")            String targetBankCd,

        /** Required for {@code NAPAS}; null for {@code SAME_BANK}. */
        @Pattern(regexp = "\\d{6,10}")         String targetNapasBankCd,

        @NotBlank @Size(max = 100)             String targetAcctNo,

        /** Optional display name of the target account-holder
         *  (legacy {@code OWAC_NM}). Empty for OAT own→own; the field
         *  rides for forward compatibility with external-recipient scope. */
        @Size(max = 200)                       String targetOwacNm,

        /** Always {@code "VND"} under the current scope (OQ-A). Server
         *  rejects anything else with
         *  {@code WBE_OWN_ACCOUNT_TRANSFER_INVALID_CCY}. */
        @NotBlank @Pattern(regexp = "VND")     String ccyCd,

        /** What the user composed in Step 1. Must equal
         *  {@link #actualAmt} (VND has no fractional unit; exact match
         *  enforced server-side). */
        @NotBlank
        @Pattern(regexp = "\\d{1,15}",
                 message = "{validation.own_account_transfer.amount_integer}")
        String requestedAmt,

        /** Engine-reported transferred amount. */
        @NotBlank
        @Pattern(regexp = "\\d{1,15}",
                 message = "{validation.own_account_transfer.amount_integer}")
        String actualAmt,

        /**
         * Bank-supplied transaction id from the engine. Format: 6-64
         * alphanumeric (regex enforced); legacy {@code TRNS_STLM_ID}.
         * Optional in case the engine returned without a tx id (e.g.
         * pre-OTP failure); the service rejects success status without
         * a tx id.
         */
        @Size(max = 100)
        @Pattern(regexp = "[A-Za-z0-9_-]{6,64}|",
                 message = "{validation.own_account_transfer.bank_tx_id}")
        String bankTxId,

        /** {@code "00000000"} = engine success; vendor error code
         *  otherwise. */
        @NotBlank @Size(max = 20)              String trnsStts,

        @Size(max = 255)                       String trnsMsg,

        @DecimalMin("0")                       BigDecimal fee,

        /** {@code A}=applicant, {@code B}=beneficiary, {@code S}=split.
         *  Maps from the wire {@code Sender pays} / {@code Recipient pays}
         *  radio (legacy {@code TRNS_FEE_TYPE} {@code 1}/{@code 2}). */
        @Pattern(regexp = "A|B|S")             String feeDfrSbj,

        @Size(max = 500)                       String rmrk,
        @Size(max = 500)                       String rmrk2,

        /** Audit-only metadata. Stored verbatim in
         *  {@code AUDIT_LOGS_INFM}; never used as a security signal
         *  (OQ-B). */
        @Size(max = 50)                        String engineVendor,
        @Size(max = 50)                        String engineVersion

) { }
