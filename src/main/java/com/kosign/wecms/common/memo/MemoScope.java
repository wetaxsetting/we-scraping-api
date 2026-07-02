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
 * <p>Only {@link #CARD_TRANSACTIONS} is wired today (its value is the
 * modern route). The two Transaction History scopes are provisional
 * placeholders declared ahead of their Phase 2 consumer — they will be
 * set to their modern routes when those screens adopt memo; do not treat
 * their current values as final.
 */
public enum MemoScope {

    /** Payment → Corporate Card → Card Transactions.
     *  {@code REF_NO = CARD_SEQ_NO}. Modern route (legacy was
     *  {@code card_0001_001}). Fits varchar(50) — 41 chars. */
    CARD_TRANSACTIONS("/payment/corporate-card/card-transactions"),

    /** Current &amp; Savings → Transaction History VND — Phase 2, not yet
     *  wired. Provisional placeholder (legacy {@code fnsh_0002_001}) —
     *  set to the modern route when wired; {@code REF_NO = BANK_CD},
     *  {@code REF_NO2 = ACCT_NO}, {@code REF_NO3 = TRSC_DT},
     *  {@code REF_NO4 = ACCT_SEQ_NO}. */
    TRANSACTION_HISTORY_VND("fnsh_0002_001"),

    /** Current &amp; Savings → Transaction History FCY — Phase 2, not yet
     *  wired. Provisional placeholder (legacy {@code fnsh_0006_001}) —
     *  set to the modern route when wired; same 4-key shape as VND. */
    TRANSACTION_HISTORY_FCY("fnsh_0006_001");

    private final String webSvcId;

    MemoScope(String webSvcId) {
        this.webSvcId = webSvcId;
    }

    /** The {@code MEMO_INFM.WEB_SVC_ID} value this feature's memos use. */
    public String webSvcId() {
        return webSvcId;
    }
}
