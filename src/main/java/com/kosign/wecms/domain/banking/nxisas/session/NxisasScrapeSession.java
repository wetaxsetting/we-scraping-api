package com.kosign.wecms.domain.banking.nxisas.session;

import com.kosign.wecms.domain.banking.dto.TransactionsSyncRequest;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasAcctListEntry;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.UUID;

/**
 * One in-flight scraping attempt. Holds:
 *  - the session id the frontend echoes back on the OTP submit
 *  - the tenant + user the session is bound to (so a stolen session
 *    id can't be replayed across tenants)
 *  - the bank credential being used (looked up server-side, never
 *    on the wire)
 *  - the current step in the flow
 *  - the most recent gateway result (accounts list when DONE,
 *    challenge prompt when CHALLENGE)
 *
 * <p>State transitions are linear: {@code AWAITING_CHALLENGE} or
 * {@code COMPLETED} or {@code FAILED}. The registry evicts a session
 * once it has been idle past the configured timeout.
 */
@Getter
@RequiredArgsConstructor
public class NxisasScrapeSession {

    public enum Status { AWAITING_CHALLENGE, COMPLETED, FAILED }

    private final UUID sessionId = UUID.randomUUID();
    private final String trSeq;
    private final String bizKey;
    private final String userId;
    private final BigDecimal credentialSeqNo;
    private final String bankModule;
    /** Numeric bank code (e.g. {@code "11704088"}) captured at session
     *  start — needed for the {@code NXISAS_ACCT_TYPE_MAPPING} lookup in
     *  {@code runAcctList} (the gateway {@link #bankModule} is the adapter
     *  path, not the numeric code the mapping table is keyed by). */
    private final String bankCd;

    @Setter(AccessLevel.PUBLIC) private Status status = Status.AWAITING_CHALLENGE;
    @Setter(AccessLevel.PUBLIC) private ScrapeChallengeKind challengeKind;
    @Setter(AccessLevel.PUBLIC) private String challengePrompt;
    @Setter(AccessLevel.PUBLIC) private List<NxisasAcctListEntry> resultAccounts;
    @Setter(AccessLevel.PUBLIC) private String errorCode;
    @Setter(AccessLevel.PUBLIC) private String errorMessage;
    @Setter(AccessLevel.PUBLIC) private Instant lastTouched = Instant.now();
    /** Sub-type discriminator captured at session start so resumed
     *  steps (OTP submit, ACCT_LIST) route to the same Coocon adapter
     *  the LOGIN used. 0 = Corporate / 1 = Personal / 2 = VCB DigiBiz
     *  etc. Defaults to 0. */
    @Setter(AccessLevel.PUBLIC) private int nxisasGbSeqNo;
    /** Coocon thread `Class` field (CORPORATE / COMMON / ""). Legacy
     *  FNSH_0100_001:204-205 reads it from the OSMC API config table per
     *  BANK_CD; spike defaults to "" and the same value is reused on
     *  resumed OTP / ACCT_LIST steps so the gateway routes consistently. */
    @Setter(AccessLevel.PUBLIC) private String bankCategory = "";

    // --- Gateway continuation state (FNSH_0100_001:249-254) ---
    // After LOGIN succeeds the gateway returns a SAS_SessionID plus 5
    // Common fields that must be echoed back verbatim on every subsequent
    // call in the same logical thread (OTP, ACCT_LIST). Omitting them
    // causes the gateway to start a fresh anonymous session and return
    // an empty account list even when login was reported as successful.
    @Setter(AccessLevel.PUBLIC) private String sasSessionID = "";
    @Setter(AccessLevel.PUBLIC) private String gwSysNo = "";
    @Setter(AccessLevel.PUBLIC) private String lbsNo = "";
    @Setter(AccessLevel.PUBLIC) private String ssSysNo = "";
    @Setter(AccessLevel.PUBLIC) private String threadNo = "";

    // -- Bank-Transaction-Sync-only state -----------------------------
    // Consumed by BankTransactionSyncService when this session is
    // serving the Account Inquiry > Update popup (mode = SYNC).
    // The Login Credentials flow (mode = SETUP) leaves these null.
    /** Discriminator: SETUP = legacy comm_0218 add-credential flow
     *  (LOGIN + ACCT_LIST post-login);
     *  SYNC = Account Inquiry &gt; Update popup (LOGIN + per-account
     *  TRANSACTION + BALANCE);
     *  CARD_REGISTER = Card Management Setting "Add Card" flow
     *  (LOGIN + CARD_INFO post-login — registers cards into
     *  {@code CARD_MSTR} via {@code POST /api/v1/payment/card-transactions/cards});
     *  CARD_SYNC = Card Transactions &gt; Update popup (LOGIN + per-card
     *  CARD_USING post-login — persists into {@code CARD_TRNS_HSTR}).
     *  Owned by {@code CardTransactionScrapeService}; structurally the
     *  card twin of {@code SYNC}. */
    public enum Mode { SETUP, SYNC, CARD_REGISTER, CARD_SYNC }
    @Setter(AccessLevel.PUBLIC) private Mode mode = Mode.SETUP;

    /** {@code yyyyMMdd} sync window start — captured on the initial
     *  start() call so resumed steps after a QNA / OTP challenge use
     *  the same window. Under {@link #autoWindow} this holds only a
     *  batch-level fallback; each account's real TRANSACTION start is
     *  computed per-account from its own last-success. */
    @Setter(AccessLevel.PUBLIC) private String fromDt;
    /** {@code yyyyMMdd} sync window end — see {@link #fromDt}. */
    @Setter(AccessLevel.PUBLIC) private String toDt;

    /** True when the popup ran in Auto mode (no explicit date range).
     *  In that case the TRANSACTION start is computed <em>per account</em>
     *  from its own last-success date (legacy {@code fnsh_0001_001.ts}
     *  {@code #acct_Reg} parity) instead of using a single batch window —
     *  so a re-sync only pulls new transactions since each account last
     *  synced, never the whole window again. False when the user picked
     *  an explicit from/to range (that window applies to every account). */
    @Setter(AccessLevel.PUBLIC) private boolean autoWindow = false;

    /** FIFO queue of accounts still to scrape after the current
     *  LOGIN succeeds. Drained by the orchestrator inside
     *  {@code submit*} after challenge resolution. The HEAD entry is
     *  the account whose LOGIN is currently in flight (or just
     *  succeeded and is awaiting TRANSACTION+BALANCE). */
    @Setter(AccessLevel.PUBLIC) private Deque<TransactionsSyncRequest.AccountKey> accountsQueue
            = new ArrayDeque<>();

    /** Running count of credit (IN) rows Coocon returned across every
     *  account in the batch — includes rows that hit the dedup skip,
     *  not just newly-inserted ones. Surfaced on the {@code COMPLETED}
     *  response so the popup can show the same totals as the Latest
     *  Transaction History dialog (which reads from the DB) when the
     *  user re-syncs an already-synced window. The field name keeps
     *  the historical "Inserted" suffix for binary compatibility —
     *  semantics changed; field name didn't. */
    @Setter(AccessLevel.PUBLIC) private int totalCreditInserted = 0;
    /** Running count of debit (OUT) rows Coocon returned. See
     *  {@link #totalCreditInserted}. */
    @Setter(AccessLevel.PUBLIC) private int totalDebitInserted = 0;
    /** Sum of {@code TRSC_AMT} for every Coocon-returned IN row.
     *  Surfaced on the {@code COMPLETED} response as the popup's
     *  "Credit" amount. {@code BigDecimal.ZERO} when no rows
     *  scraped; values from
     *  {@link com.kosign.wecms.domain.banking.nxisas.BankTransactionSyncService}
     *  are normalised positive (sign-flips happen before the sum). */
    @Setter(AccessLevel.PUBLIC) private java.math.BigDecimal totalCreditAmount = java.math.BigDecimal.ZERO;
    /** Sum of {@code TRSC_AMT} for every Coocon-returned OUT row.
     *  See {@link #totalCreditAmount}. */
    @Setter(AccessLevel.PUBLIC) private java.math.BigDecimal totalDebitAmount = java.math.BigDecimal.ZERO;
    /** Count of accounts whose sync has completed (LOGIN +
     *  TRANSACTION + BALANCE all succeeded + audit stamps written). */
    @Setter(AccessLevel.PUBLIC) private int accountsSynced = 0;

    /** Count of newly-inserted debit rows the P1 auto-matcher linked to a
     *  payment during this batch (0 when the tenant's biz-env {@code 00079}
     *  gate is off). Surfaced on the COMPLETED response as
     *  {@code auto_matched_count}. */
    @Setter(AccessLevel.PUBLIC) private int totalAutoMatched = 0;

    /** Per-currency roll-up of the batch — keyed by {@code CCY_CD}
     *  ("USD" / "KRW" / "VND" / …), insertion-ordered so the
     *  {@code COMPLETED} response lists currencies in first-seen order.
     *  Each account's persist result is folded into its currency bucket
     *  in {@code BankTransactionSyncService.processOneAccount}; the
     *  buckets feed the {@code currency_summaries} wire field so the
     *  popup's per-currency groups show their own numbers instead of the
     *  batch aggregate. */
    @Getter private final java.util.LinkedHashMap<String, CurrencyTally> currencyTallies =
            new java.util.LinkedHashMap<>();

    /** Fetch (creating if absent) the running tally for one currency. */
    public CurrencyTally currencyTally(String ccyCd) {
        return currencyTallies.computeIfAbsent(ccyCd == null ? "" : ccyCd, k -> new CurrencyTally());
    }

    /**
     * Mutable per-currency accumulator. {@code successCount} counts
     * accounts of this currency that fully synced; {@code creditCount} /
     * {@code debitCount} count Coocon-returned transaction rows;
     * {@code creditTotal} / {@code debitTotal} sum {@code TRSC_AMT}
     * (sign-normalised positive by the caller). One {@link #addAccount}
     * call per fully-processed account.
     */
    public static final class CurrencyTally {
        private int successCount = 0;
        private int creditCount = 0;
        private int debitCount = 0;
        private int autoMatchedCount = 0;
        private BigDecimal creditTotal = BigDecimal.ZERO;
        private BigDecimal debitTotal = BigDecimal.ZERO;

        public void addAccount(int credit, int debit, BigDecimal creditAmt, BigDecimal debitAmt) {
            this.successCount++;
            this.creditCount += credit;
            this.debitCount  += debit;
            if (creditAmt != null) this.creditTotal = this.creditTotal.add(creditAmt);
            if (debitAmt  != null) this.debitTotal  = this.debitTotal.add(debitAmt);
        }

        /** Fold one account's P1 auto-match result into this currency bucket
         *  (numerator of the per-currency "Debit Matched" row). Called once
         *  per fully-processed account, after {@link #addAccount}. */
        public void addAutoMatched(int autoMatched) {
            this.autoMatchedCount += autoMatched;
        }

        public int successCount()        { return successCount; }
        public int creditCount()         { return creditCount; }
        public int debitCount()          { return debitCount; }
        public int autoMatchedCount()    { return autoMatchedCount; }
        public BigDecimal creditTotal()  { return creditTotal; }
        public BigDecimal debitTotal()   { return debitTotal; }
    }

    /** Bank credential username for the account currently being scraped.
     *  Captured each time {@link com.kosign.wecms.domain.banking.bankcredentials.service.BankCredentialService}
     *  resolves a credential at the head of {@link #accountsQueue}, so a
     *  challenge response ({@code QNA_REQUIRED} today; OTP / CAPTCHA can
     *  follow) can echo it back to the frontend's Security Question
     *  popup. Never sent to Coocon — display-only. */
    @Setter(AccessLevel.PUBLIC) private String loginId;

    /** Bank/login-group key whose LOGIN currently owns the live
     *  {@link #sasSessionID} on this session. This is the refactor analogue
     *  of legacy {@code nxisas_api.js}'s {@code _nxisas_auth[bankCd]} cache
     *  (nxisas_api.js:2428) that lets every account under one bank reuse a
     *  single login instead of re-authenticating per account. {@code null}
     *  until the first clean LOGIN; updated each time a different group's
     *  LOGIN clears. SYNC mode only — the SETUP flow scrapes one bank per
     *  session and never sets it. */
    @Setter(AccessLevel.PUBLIC) private String activeLoginGroupKey;

    // -- CARD_SYNC-only state -----------------------------------------
    // Consumed by CardTransactionScrapeService when this session serves
    // the Card Transactions > Update popup (mode = CARD_SYNC). The card
    // twin of accountsQueue + the per-account counters above. Other modes
    // leave these at their empty defaults.

    /** One card pending CARD_USING after the current LOGIN clears.
     *  {@code bankCertSeq} is the credential the card was registered
     *  under ({@code CARD_MSTR.BANK_CERT_SEQ}); it is the login-group key
     *  so all cards sharing a credential reuse a single LOGIN. The
     *  refactor never silently falls back to another credential — a card
     *  with a null {@code bankCertSeq} is rejected before it reaches the
     *  queue (audit requirement #6). */
    public record CardScrapeKey(String bankCd, String cardNo,
                                String cardType, BigDecimal bankCertSeq) { }

    /** FIFO queue of cards still to scrape after the current LOGIN
     *  succeeds — the card twin of {@link #accountsQueue}. Drained by
     *  {@code CardTransactionScrapeService.advance()} after challenge
     *  resolution. */
    @Setter(AccessLevel.PUBLIC) private Deque<CardScrapeKey> cardsQueue = new ArrayDeque<>();

    /** Running count of newly-inserted {@code CARD_TRNS_HSTR} rows across
     *  every card in the batch. */
    @Setter(AccessLevel.PUBLIC) private int cardRowsInserted = 0;
    /** Running count of scraped rows skipped as duplicates (legacy
     *  composite-key dedup hit). */
    @Setter(AccessLevel.PUBLIC) private int cardRowsSkipped = 0;
    /** Count of cards whose CARD_USING completed (login + scrape + persist). */
    @Setter(AccessLevel.PUBLIC) private int cardsSynced = 0;

    public void touch() {
        lastTouched = Instant.now();
    }
}
