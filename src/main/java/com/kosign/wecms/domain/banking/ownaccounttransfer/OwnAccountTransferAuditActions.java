package com.kosign.wecms.domain.banking.ownaccounttransfer;

/**
 * Audit-row {@code ACTION}-equivalent constants for Own Account
 * Transfer events. Per OQ-4 (PO 2026-05-18) the feature audits every
 * action, not only mutations — list endpoints, the balance-refresh
 * patch, and the engine-handshake check all write an audit row.
 *
 * <p>Wire-stable: once shipped these strings are part of the audit
 * vocabulary and must not be renamed without a back-fill migration.
 *
 * <p>Path A scope: this MR ships the four endpoints below. The two
 * composer-scope actions in the finalized plan ({@code LIST_RECIPIENT_BANKS},
 * {@code LOOKUP_NAPAS_BANK_INFO} — the legacy recipient-bank inquiry)
 * either land with this MR ({@code LOOKUP_NAPAS_BANK_INFO} — the
 * read of {@code NAPAS_BANK_INFO} for the /napas-bank-info endpoint)
 * or stay deferred ({@code LIST_RECIPIENT_BANKS} — needs the
 * NAPAS_BANK_REMIT_INFO table; documented blocker, composer MR).
 */
public final class OwnAccountTransferAuditActions {

    private OwnAccountTransferAuditActions() { }

    /** Action written by {@code GET /source-accounts}. */
    public static final String LIST_SOURCE_ACCOUNTS    = "LIST_SOURCE_ACCOUNTS";

    /** Action written by {@code POST /balance-refresh} (success AND
     *  failure both write — the audit row IS the record of the engine
     *  outcome). */
    public static final String BALANCE_REFRESH         = "BALANCE_REFRESH";

    /** Action written by {@code POST /engine-connection-check}. The
     *  row IS the only artifact — the endpoint has no domain effect. */
    public static final String ENGINE_CONNECTION_CHECK = "ENGINE_CONNECTION_CHECK";

    /** Action written by {@code GET /napas-bank-info}. */
    public static final String LOOKUP_NAPAS_BANK_INFO  = "LOOKUP_NAPAS_BANK_INFO";

    /** Action written by {@code GET /target-accounts}. */
    public static final String LIST_TARGET_ACCOUNTS    = "LIST_TARGET_ACCOUNTS";

    /** Action written by {@code POST /scraping-result} when the engine
     *  reported success ({@code trns_stts = "00000000"}) AND the row was
     *  persisted (fresh write) or matched (replay). */
    public static final String SCRAPING_RESULT_OK      = "SCRAPING_RESULT_OK";

    /** Action written by {@code POST /scraping-result} when the
     *  composer-side validation rejected the intake (amount mismatch,
     *  source/target ownership fail, idempotency conflict, etc.) OR
     *  the engine reported a non-success status. The audit row is the
     *  forensic record either way. */
    public static final String SCRAPING_RESULT_REJECT  = "SCRAPING_RESULT_REJECT";
}
