package com.kosign.wecms.domain.banking.ownaccounttransfer;

import java.util.List;
import java.util.Set;

/**
 * Per-feature constants for Banking &gt; Own Account Transfer (refactor
 * slug {@code own-account-transfer}). Single home for the values that
 * are wire-stable or appear in more than one file — feature-local
 * holders keep the service body readable and stop magic strings from
 * drifting between the service, the audit writer, and the test
 * fixtures.
 *
 * <p>Path A scope: this feature MR ships
 * {@code /source-accounts}, {@code /balance-refresh},
 * {@code /engine-connection-check}, and {@code /napas-bank-info}. The
 * transfer-execution surface (composer / scraping-result intake /
 * canonical transfer-history) is deferred to a separate composer MR
 * per the finalized backend plan §9.3. The constants listed below
 * intentionally cover only the in-scope endpoints.
 */
public final class OwnAccountTransferConstants {

    private OwnAccountTransferConstants() { }

    /** Per-feature stable identifier — refactor route slug. Used as
     *  {@code AUDIT_LOGS_INFM.WSVC_ID} and as the SLF4J / MDC log tag.
     *  CLAUDE.md 2026-05-09: refactor writes use the slug, never the
     *  legacy WSVC id {@code trns_0001_001}. */
    public static final String WSVC_SLUG = "own-account-transfer";

    /** SLF4J / log tag — same value as {@link #WSVC_SLUG} by design. */
    public static final String LOG_TAG = WSVC_SLUG;

    /** Logical entity name written into {@code AUDIT_LOGS_INFM.REF_NO}
     *  for the ACCT_MSTR-scoped actions (list, balance refresh). */
    public static final String AUDIT_REF_ACCT_MSTR = "ACCT_MSTR";

    /** Logical entity name written into {@code AUDIT_LOGS_INFM.REF_NO}
     *  for the engine-handshake audit row. The engine isn't backed by
     *  a single DB table — {@code "NXISAS"} is the legacy convention
     *  for engine-handshake audit rows. */
    public static final String AUDIT_REF_NXISAS = "NXISAS";

    /** Logical entity name written into {@code AUDIT_LOGS_INFM.REF_NO}
     *  for the NAPAS bank-info lookup audit row. */
    public static final String AUDIT_REF_NAPAS_BANK_INFO = "NAPAS_BANK_INFO";

    /** Logical entity name written into {@code AUDIT_LOGS_INFM.REF_NO}
     *  for the scraping-result intake (the row gets persisted to
     *  {@code NAPAS_REMIT_TRNS_HSTR}). */
    public static final String AUDIT_REF_NAPAS_REMIT = "NAPAS_REMIT_TRNS_HSTR";

    /** Currency — feature is VND-only by design (OQ-A, PO 2026-05-18). */
    public static final String CCY_VND = "VND";

    /** {@code ACCT_MSTR.ACTV_YN} value for an active row. */
    public static final String ACTV_YN_ACTIVE = "Y";

    /** {@code ACCT_AUTH_INFO.USE_YN} value for an active grant. */
    public static final String USE_YN_ACTIVE = "Y";

    /** {@code ACCT_AUTH_INFO.AUTH_TYPE} value granting transfer (withdraw). */
    public static final String AUTH_TYPE_WITHDRAW = "W";

    /** {@code ACCT_AUTH_INFO.AUTH_TYPE} value granting read-only view. */
    public static final String AUTH_TYPE_VIEW = "V";

    /** Account types listed on the source-account picker:
     *  {@code 03} current, {@code 05} saving (legacy
     *  {@code _r001_act.jsp:70}). */
    public static final Set<String> SOURCE_ACCT_TYPE_CDS = Set.of("03", "05");

    /** HAENGSUNG-only saving-account types that get the maturity +
     *  balance gate (legacy {@code _r001_act.jsp:74-77}). */
    public static final Set<String> HAENGSUNG_GATED_SAVING_TYPES = Set.of("05", "06", "08");

    /** Engine-side success code returned by NXiSAS for a successful
     *  balance refresh. Legacy {@code _u001_act.jsp:107} compares
     *  verbatim. */
    public static final String ENGINE_SUCCESS_STTS = "00000000";

    /** Allowed sender banks for the default product type — Shinhan VN /
     *  IBK VN / Vietcombank / VietinBank / Woori VN. Mirrors
     *  {@code _r001_act.jsp:144-155}. */
    public static final List<String> ALLOWED_BANK_CDS_DEFAULT = List.of(
            "11704088",  // Shinhan VN
            "11704003",  // IBK VN
            "11704004",  // Vietcombank
            "11704001",  // VietinBank
            "11704020"); // Woori VN

    /** Allowed sender banks for HAENGSUNG ({@code PRDT_TYPE='H'}) —
     *  VietinBank only. */
    public static final List<String> ALLOWED_BANK_CDS_HAENGSUNG = List.of(
            "11704001"); // VietinBank

    /* ===============================================================
     *  Scraping-result intake (composer execution path)
     * =============================================================== */

    /** Wire value for {@code transferType} when source and target share
     *  a {@code BANK_CD}. */
    public static final String TRANSFER_TYPE_SAME_BANK = "SAME_BANK";

    /** Wire value for {@code transferType} when source and target are
     *  on different banks (routed via NAPAS). */
    public static final String TRANSFER_TYPE_NAPAS     = "NAPAS";

    /** {@code NAPAS_REMIT_TRNS_HSTR.TRANS_TYPE} value for same-bank
     *  transfers (legacy {@code trns_0001_007.ts:2052-2055}). */
    public static final String NRTH_TRANS_TYPE_SAME_BANK = "1";

    /** {@code NAPAS_REMIT_TRNS_HSTR.TRANS_TYPE} value for cross-bank
     *  (NAPAS) transfers (legacy {@code trns_0001_007.ts:2056-2060}). */
    public static final String NRTH_TRANS_TYPE_NAPAS     = "2";

    /** {@code NAPAS_REMIT_TRNS_HSTR.TRNS_STLM_YN} value the intake
     *  always writes — legacy {@code trns_0002_001_c001_act.jsp:104}.
     *  The settlement-confirm flow (a separate batch job) flips this
     *  to {@code Y} when the bank settles; not in this MR. */
    public static final String NRTH_STLM_YN_NO = "N";

    /** {@code NAPAS_REMIT_TRNS_HSTR.TRNS_STLM_YN} settled value —
     *  excluded by the duplicate-settlement-id guard (legacy
     *  {@code ValidationHelper.validateTrnsStlmId}: {@code TRNS_STLM_ID
     *  = ? AND TRNS_STLM_YN <> 'Y'}). Only not-yet-settled rows count
     *  as a duplicate. */
    public static final String NRTH_STLM_YN_YES = "Y";

    /* =================================================================
     *  FEE_DFR_SBJ — wire vocabulary vs legacy column vocabulary
     * ================================================================= */

    /** Wire value for "sender pays" (applicant bears the fee). */
    public static final String FEE_BEARER_WIRE_SENDER    = "A";

    /** Wire value for "recipient pays" (beneficiary bears the fee). */
    public static final String FEE_BEARER_WIRE_RECIPIENT = "B";

    /** {@code NAPAS_REMIT_TRNS_HSTR.FEE_DFR_SBJ} value legacy stores for
     *  "sender pays" — {@code FeeDfrSbjType.SENDER_PAYS("1")}. */
    public static final String FEE_DFR_SBJ_SENDER_PAYS    = "1";

    /** {@code NAPAS_REMIT_TRNS_HSTR.FEE_DFR_SBJ} value legacy stores for
     *  "recipient pays" — {@code FeeDfrSbjType.RECEIPIENT_PAYS("2")}. */
    public static final String FEE_DFR_SBJ_RECIPIENT_PAYS = "2";

    /**
     * Translate the wire fee-bearer letter to the value legacy persists.
     *
     * <p>Legacy carries ONE value end-to-end: the radio's own
     * {@code value="1"} / {@code value="2"}
     * ({@code trns_0001_007_view.jsp:177-183}), read into
     * {@code trns_fee_val} ({@code trns_0001_007.ts:596-600}), sent to the
     * engine as {@code feePaymentType} ({@code :2086}) AND written to the
     * column as {@code FEE_DFR_SBJ} ({@code :2182}).
     * {@code wabooks/enums/FeeDfrSbjType} declares exactly those two codes,
     * and {@code ValidationHelper.validateFeeDfrSbjType} rejects anything else
     * with {@code WBE0001}. No legacy reader expects {@code A}/{@code B}.
     *
     * <p>The refactor's UI models the choice as {@code A}/{@code B} and the
     * frontend already maps {@code A}→{@code "1"} / {@code B}→{@code "2"} for
     * the engine payload ({@code bank-profiles.ts}); only the persisted column
     * had drifted. Converting here — at the DB boundary — restores parity
     * without touching the wire contract.
     *
     * <p>Anything that is not a known wire letter passes through unchanged, so
     * a caller that already sends the legacy {@code 1}/{@code 2} is idempotent
     * and the unused {@code S} (split — no legacy counterpart) is not silently
     * rewritten into a valid-looking code.
     *
     * @param wireValue the DTO's {@code fee_dfr_sbj}; may be {@code null}
     * @return the legacy column value, or the input unchanged / {@code null}
     */
    public static String toLegacyFeeDfrSbj(String wireValue) {
        if (FEE_BEARER_WIRE_SENDER.equals(wireValue))    return FEE_DFR_SBJ_SENDER_PAYS;
        if (FEE_BEARER_WIRE_RECIPIENT.equals(wireValue)) return FEE_DFR_SBJ_RECIPIENT_PAYS;
        return wireValue;
    }
}
