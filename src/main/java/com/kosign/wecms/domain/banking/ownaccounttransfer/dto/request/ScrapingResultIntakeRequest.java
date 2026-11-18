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

        /**
         * Engine {@code Output.ErrorCode}, verbatim. {@code "00000000"} =
         * success; the bank/engine code otherwise.
         *
         * <p><b>Optional — legacy parity.</b> Legacy declares
         * {@code TRNS_STTS} as a plain 20-char input
         * ({@code WSVC.trns_0002_001_c001.xml}) and
         * {@code trns_0002_001_c001_act.jsp#valiateInput} never requires it;
         * {@code NapasRemitTrnsHstr.copyFrom} stores whatever arrives, and the
         * browser composer initialises the field to {@code ""}
         * ({@code trns_0001_007.ts:2140}). A {@code @NotBlank} here would 400
         * a payload legacy accepts — and this endpoint records a transfer the
         * bank has ALREADY executed, so a rejection destroys the only record
         * of it. Blank/absent is treated as a non-success status downstream.
         *
         * <p>Never {@code "99999999"}: that value is the Transfer-History
         * "Failed" <i>filter</i> sentinel ({@code trns_0003_001_view.jsp:158})
         * and is never stored as a transfer result.
         */
        @Size(max = 20)                        String trnsStts,

        @Size(max = 255)                       String trnsMsg,

        @DecimalMin("0")                       BigDecimal fee,

        /**
         * Fee bearer as the UI models it: {@code A}=applicant (sender pays),
         * {@code B}=beneficiary (recipient pays), {@code S}=split.
         *
         * <p><b>Converted at the DB boundary.</b> Legacy persists the radio's
         * own value — {@code "1"} sender / {@code "2"} recipient
         * ({@code trns_0001_007_view.jsp:177-183} →
         * {@code trns_0001_007.ts:596-600} → {@code :2182}) — and
         * {@code FeeDfrSbjType} declares exactly those two codes. The wire
         * keeps {@code A}/{@code B} so no API contract changes;
         * {@code OwnAccountTransferConstants#toLegacyFeeDfrSbj} maps to
         * {@code 1}/{@code 2} on the way into {@code FEE_DFR_SBJ}.
         */
        @Pattern(regexp = "A|B|S")             String feeDfrSbj,

        @Size(max = 500)                       String rmrk,
        @Size(max = 500)                       String rmrk2,

        /* ---------------------------------------------------------------
         *  POST-TRANSFER ENGINE ECHO — recorded, never re-validated.
         *
         *  Legacy overwrites OUT_ACCT_NO / IN_ACCT_NO / CCY_CD from
         *  {@code Output.Result.transfer[0]} on success
         *  ({@code trns_0001_007.ts:2157-2158, 2163}) and performs NO
         *  ownership or currency re-check on the result
         *  ({@code trns_0002_001_c001_act.jsp:27-56} validates required-ness,
         *  TransType, CcyCd, positive amount and FeeDfrSbjType only — it never
         *  reads ACCT_MSTR).
         *
         *  This refactor DOES re-validate ownership, so the two roles are kept
         *  on separate fields. The {@code sourceAcctNo} / {@code targetAcctNo}
         *  / {@code ccyCd} above are the PRE-transfer identity the user chose
         *  and are validated. The three fields below are the POST-transfer
         *  record: persisted verbatim, never used to look anything up, and
         *  deliberately constrained by length alone — a bank-normalized
         *  account number must never be able to reject an intake for a
         *  transfer the bank already executed.
         *
         *  Null / blank ⇒ the composed values are persisted, matching
         *  legacy's failure branch ({@code :2167-2170}).
         * ------------------------------------------------------------- */

        /** Legacy {@code OUT_ACCT_NO} ← {@code Result.transfer[0].acctNum}. */
        @Size(max = 100)                       String engineOutAcctNo,

        /** Legacy {@code IN_ACCT_NO} ← {@code Result.transfer[0].remitAcctNum}. */
        @Size(max = 100)                       String engineInAcctNo,

        /** Legacy {@code CCY_CD} ← {@code Result.transfer[0].currency}. */
        @Size(max = 3)                         String engineCcyCd,

        /** Audit-only metadata. Stored verbatim in
         *  {@code AUDIT_LOGS_INFM}; never used as a security signal
         *  (OQ-B). */
        @Size(max = 50)                        String engineVendor,
        @Size(max = 50)                        String engineVersion,

        /* -------------------------------------------------------------
         *  Bank-account passwords (Model A — typed every transfer)
         *
         *  The composer collects these per-bank (IBK / Shinhan / Woori
         *  need {@code acctPwd}; only KEB Hana needs {@code trnsPwd} —
         *  see {@code bank-profiles.ts}) and sends them straight to the
         *  local NXiSAS engine as {@code Input.acctNumPWD} /
         *  {@code Input.transPWD}. They ride along here so that, on a
         *  transfer the bank ACCEPTED, the service can persist them to
         *  {@code ACCT_MSTR.ACCT_PWD} / {@code TRNS_PWD} — the same
         *  columns live JEX reads with {@code decrypt()} when it runs
         *  the equivalent legacy screen ({@code trns_0001_007.ts:675-682}).
         *
         *  PLAINTEXT on the wire. Unavoidable: the engine is a
         *  browser-resident process and the bank IB site needs cleartext,
         *  exactly as legacy does. Same constraint and same exposure
         *  envelope as {@code SourceAccountRow.certInfoDetlCon}.
         *
         *  NEVER logged, never echoed in a response, never audited.
         *  Nullable: banks that need no password send neither, and the
         *  {@code COALESCE} in
         *  {@code AcctMstrRepository.applyTransferPasswords} leaves the
         *  stored column untouched for whichever one is absent.
         * ------------------------------------------------------------- */

        /** Account password (legacy {@code ACCT_NUM_PWD}). Persisted to
         *  {@code ACCT_MSTR.ACCT_PWD} only on engine success. */
        @Size(max = 100)                       String acctPwd,

        /** Transfer password (legacy {@code TRNS_PWD}). Persisted to
         *  {@code ACCT_MSTR.TRNS_PWD} only on engine success. KEB Hana
         *  is the only bank that sends one. */
        @Size(max = 100)                       String trnsPwd

) { }
