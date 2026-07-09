package com.kosign.wecms.common.memo;

/**
 * Central registry of memo scopes — the {@code MEMO_INFM.WEB_SVC_ID}
 * value each feature's memo thread is stored (and read) under.
 *
 * <p>Memo storage is polymorphic: one {@code MEMO_INFM} table is shared
 * across every screen, discriminated by {@code WEB_SVC_ID} + the
 * {@code REF_NO..REF_NO9} keys. This enum is the <b>single source of
 * truth</b> for the {@code WEB_SVC_ID} value of each memo-enabled
 * feature, so the identifier scheme can be switched in exactly one place
 * — never scatter the raw string through services / repositories.
 *
 * <p><b>Convention: the modern refactor route.</b> Memo is a shared
 * refactor feature, so {@code WEB_SVC_ID} represents the modern Next.js
 * route of the owning screen — <i>not</i> the legacy JEX screen id. To
 * change the value, edit only the string in the matching constant here;
 * every consumer ({@code CardTransactionsService} today, Transaction
 * History VND/FCY in Phase 2) picks it up automatically. Values must fit
 * {@code MEMO_INFM.WEB_SVC_ID} (varchar(50)).
 *
 * <p>All three scopes carry their modern Next.js route (wired
 * 2026-07-07). Existing legacy memos written under the old JEX screen
 * ids ({@code card_0001_001} / {@code fnsh_0002_001} /
 * {@code fnsh_0006_001}) are migrated to these values by a one-time DBA
 * SQL script (Card: {@code memo_web_svc_id_card_transactions_20260702.sql};
 * Transaction History: {@code memo_web_svc_id_transaction_history_20260707.sql}).
 */
public enum MemoScope {

    /** Payment → Corporate Card → Card Transactions.
     *  {@code REF_NO = CARD_SEQ_NO}. Modern route (legacy was
     *  {@code card_0001_001}). Fits varchar(50) — 41 chars. */
    CARD_TRANSACTIONS("/payment/corporate-card/card-transactions"),

    /** Current &amp; Savings → Transaction History VND (legacy
     *  {@code fnsh_0002_001}). 4-key thread: {@code REF_NO = BANK_CD},
     *  {@code REF_NO2 = ACCT_NO}, {@code REF_NO3 = TRSC_DT},
     *  {@code REF_NO4 = ACCT_SEQ_NO}. Modern route — 32 chars, fits varchar(50). */
    TRANSACTION_HISTORY_VND("/banking/transaction-history/vnd"),

    /** Current &amp; Savings → Transaction History FCY (legacy
     *  {@code fnsh_0006_001}). Same 4-key shape as VND. Kept distinct
     *  from VND so the two screens' memo threads never collide. */
    TRANSACTION_HISTORY_FCY("/banking/transaction-history/fcy");

    private final String webSvcId;

    MemoScope(String webSvcId) {
        this.webSvcId = webSvcId;
    }

    /** The {@code MEMO_INFM.WEB_SVC_ID} value this feature's memos use. */
    public String webSvcId() {
        return webSvcId;
    }
}
