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

    public enum Status { RUNNING, AWAITING_CHALLENGE, COMPLETED, FAILED, CANCELLED }

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

    @Setter(AccessLevel.PUBLIC) private volatile Status status = Status.AWAITING_CHALLENGE;
    @Setter(AccessLevel.PUBLIC) private volatile ScrapeChallengeKind challengeKind;
    @Setter(AccessLevel.PUBLIC) private volatile String challengePrompt;
    @Setter(AccessLevel.PUBLIC) private volatile List<NxisasAcctListEntry> resultAccounts;
    @Setter(AccessLevel.PUBLIC) private volatile String errorCode;
    @Setter(AccessLevel.PUBLIC) private volatile String errorMessage;
    @Setter(AccessLevel.PUBLIC) private volatile Instant lastTouched = Instant.now();

    /** Cooperative-cancellation flag (Phase 1). Set by
     *  {@code BankTransactionSyncService.cancel(...)} on a SEPARATE request
     *  thread while the scrape loop runs on the original request thread; the
     *  scrape checks it between accounts and stops cleanly without starting
     *  the next account (legacy {@code _v2nxisas.stopAllNxisas} parity). A
     *  cancel while the session is {@code AWAITING_CHALLENGE} discards the
     *  session so any later OTP/QNA/CAPTCHA resume is refused. Volatile for
     *  cross-thread visibility; Lombok's class-level {@code @Getter} is
     *  suppressed for this field so the manual {@link #isCancelled()} /
     *  {@link #requestCancel()} pair is the only accessor surface. */
    @Getter(AccessLevel.NONE)
    private volatile boolean cancelled = false;

    public boolean isCancelled() { return cancelled; }

    public void requestCancel() { this.cancelled = true; }

    // ── Async execution state (Phase 2 — SYNC mode only) ──────────────
    // Populated by BankTransactionSyncService's background worker; read by
    // the status-polling endpoint. Primitives are volatile for cross-thread
    // visibility; the compound (phase, fraction, counters, currency) updates
    // go through synchronized methods so a status read sees a consistent
    // snapshot. Other modes (SETUP / CARD_*) never touch these.

    /** Coarse per-account processing phase — drives the status snapshot +
     *  the progress fraction. */
    public enum Phase { SCRAPING_TRANSACTIONS, PERSISTING_TRANSACTIONS, REFRESHING_BALANCE }

    @Getter(AccessLevel.NONE) private volatile int progress = 0;
    @Getter(AccessLevel.NONE) private volatile Phase currentPhase;
    @Getter(AccessLevel.NONE) private volatile int totalAccounts = 0;
    /** Processed accounts = success + failure (drives the frontend counter). */
    @Getter(AccessLevel.NONE) private volatile int completedAccounts = 0;
    @Getter(AccessLevel.NONE) private volatile int failedAccounts = 0;
    /** Fraction (0..1) of the CURRENT account's work done — the phaseFraction
     *  term in the overall-progress formula. */
    @Getter(AccessLevel.NONE) private volatile double currentAccountFraction = 0.0;
    /** One-worker guard: only one background segment may run per session at a
     *  time. CAS false→true to schedule; reset to false when the segment ends
     *  (challenge / terminal). Prevents duplicate scheduling AND duplicate
     *  resume from spawning two workers. */
    @Getter(AccessLevel.NONE) private final java.util.concurrent.atomic.AtomicBoolean workerRunning =
            new java.util.concurrent.atomic.AtomicBoolean(false);
    @Getter(AccessLevel.NONE) private volatile java.time.Instant createdAt = java.time.Instant.now();
    @Getter(AccessLevel.NONE) private volatile java.time.Instant terminalAt;

    // Pending challenge answer to consume on the next worker segment. SECRETS
    // — never included in any status snapshot; cleared immediately after the
    // gateway resume call.
    @Getter(AccessLevel.NONE) private volatile ScrapeChallengeKind pendingResumeKind;
    @Getter(AccessLevel.NONE) private volatile String pendingChallengeValue;   // OTP / CAPTCHA text
    @Getter(AccessLevel.NONE) private volatile PendingQna pendingQna;

    /** Non-secret QNA continuation (question + hidden token) plus the user's
     *  answer — held only between the resume submit and the gateway QNA call,
     *  then cleared. Excluded from every status snapshot. */
    public record PendingQna(String question, String hiddenValue, String answer) { }

    public int getProgress()            { return progress; }
    public Phase getCurrentPhase()      { return currentPhase; }
    public int getTotalAccounts()       { return totalAccounts; }
    public int getCompletedAccounts()   { return completedAccounts; }
    public int getFailedAccounts()      { return failedAccounts; }
    /** Success count = accounts fully processed OK (mirrors {@link #accountsSynced}). */
    public int getSuccessAccounts()     { return accountsSynced; }
    public java.time.Instant getCreatedAt()  { return createdAt; }
    public java.time.Instant getTerminalAt() { return terminalAt; }

    public void setTotalAccounts(int n) { this.totalAccounts = n; }

    public boolean tryStartWorker()  { return workerRunning.compareAndSet(false, true); }
    public void finishWorker()       { workerRunning.set(false); }
    public boolean isWorkerRunning() { return workerRunning.get(); }

    public boolean isTerminal() {
        return status == Status.COMPLETED || status == Status.FAILED || status == Status.CANCELLED;
    }

    /** Enter a per-account phase + set its progress fraction; recomputes the
     *  monotonic overall progress. Synchronized so a concurrent status read
     *  sees a consistent (phase, fraction, progress) triple. */
    public synchronized void enterPhase(Phase phase, double fraction) {
        this.currentPhase = phase;
        this.currentAccountFraction = fraction;
        recomputeProgress();
        touch();
    }

    /** Record one fully-processed account (success or failure) and reset the
     *  per-account fraction; recomputes progress. */
    public synchronized void completeAccount(boolean success) {
        if (success) this.accountsSynced++;
        else this.failedAccounts++;
        this.completedAccounts++;
        this.currentAccountFraction = 0.0;
        recomputeProgress();
        touch();
    }

    /** Pin progress at exactly 100 (terminal COMPLETED). */
    public synchronized void markProgressComplete() {
        this.progress = 100;
        touch();
    }

    /** Overall progress = floor(((completedAccounts + currentAccountFraction) /
     *  totalAccounts) * 100), clamped to [0,100] and never decreasing
     *  (monotonic). Caller holds the monitor. */
    private void recomputeProgress() {
        if (totalAccounts <= 0) return;
        double raw = ((completedAccounts + currentAccountFraction) / totalAccounts) * 100.0;
        int computed = (int) Math.floor(raw);
        if (computed < 0) computed = 0;
        if (computed > 100) computed = 100;
        if (computed > progress) progress = computed;   // monotonic
    }

    /** Terminal-state transition — stamps {@link #status} + {@link #terminalAt}
     *  so the registry can retain the session for a documented window after it
     *  finishes (so the frontend's last poll can read the terminal snapshot).
     *
     *  <p>Delegates to {@link #tryMarkTerminal(Status)} so it is <b>once-only</b>:
     *  the first accepted terminal state wins and a later, different terminal
     *  transition is ignored (cancel-vs-completion precedence — see
     *  {@link #tryMarkTerminal}). Callers that don't need the accepted/ignored
     *  signal use this void form; callers that must respect precedence
     *  (finalize, cancel) call {@link #tryMarkTerminal} and branch on the result. */
    public void markTerminal(Status terminal) {
        tryMarkTerminal(terminal);
    }

    /** Once-only terminal transition. Atomically (under the session monitor)
     *  stamps {@link #status} + {@link #terminalAt} <b>only if the session is not
     *  already terminal</b>, and reports whether the transition was accepted.
     *
     *  <p>Precedence (the fix for the cancel-vs-completion race): the FIRST
     *  terminal state wins. Once COMPLETED / FAILED / CANCELLED is set, a later
     *  call with a different terminal status is a no-op that returns
     *  {@code false} — a late Cancel cannot overwrite a real COMPLETED/FAILED,
     *  and a worker finalizing COMPLETED cannot overwrite a CANCELLED that a
     *  concurrent cancel already committed. A repeated identical terminal is
     *  likewise a no-op (idempotent). {@link #terminalAt} is stamped exactly
     *  once — on the accepted transition. */
    public synchronized boolean tryMarkTerminal(Status terminal) {
        if (isTerminal()) {
            return false;
        }
        this.status = terminal;
        this.terminalAt = java.time.Instant.now();
        touch();
        return true;
    }

    // Pending-resume (secret) accessors — set on a resume submit, consumed +
    // cleared by the worker before it does anything else.
    public ScrapeChallengeKind getPendingResumeKind() { return pendingResumeKind; }
    public String getPendingChallengeValue()          { return pendingChallengeValue; }
    public PendingQna getPendingQna()                  { return pendingQna; }
    public void setPendingOtp(String v)     { this.pendingResumeKind = ScrapeChallengeKind.OTP; this.pendingChallengeValue = v; }
    public void setPendingCaptcha(String v) { this.pendingResumeKind = ScrapeChallengeKind.CAPTCHA; this.pendingChallengeValue = v; }
    public void setPendingQna(PendingQna q) { this.pendingResumeKind = ScrapeChallengeKind.QNA; this.pendingQna = q; }
    public void clearPendingResume() { this.pendingResumeKind = null; this.pendingChallengeValue = null; this.pendingQna = null; }
    public boolean hasPendingResume() { return pendingResumeKind != null; }

    /** Immutable per-currency snapshot for the status / terminal response —
     *  copies under the monitor so a concurrent {@link #foldCurrency} can't
     *  cause a ConcurrentModificationException. {@code failedCount} is the
     *  per-currency count of accounts that failed but did <em>not</em> abort the
     *  batch — today only account-level {@code LOGIN_CAN} challenge-cancels
     *  (legacy per-bank {@code run_afterLoginFail}), folded via
     *  {@link #foldCurrencyFailure}. A whole-batch fail-fast still marks the
     *  session FAILED (no COMPLETED summary is read), so a COMPLETED batch's
     *  failedCount reflects only the continue-past LOGIN_CAN failures. */
    public synchronized java.util.List<com.kosign.wecms.domain.banking.dto.CurrencySummary> currencySummariesSnapshot() {
        java.util.List<com.kosign.wecms.domain.banking.dto.CurrencySummary> out =
                new java.util.ArrayList<>(currencyTallies.size());
        for (var e : currencyTallies.entrySet()) {
            CurrencyTally t = e.getValue();
            out.add(new com.kosign.wecms.domain.banking.dto.CurrencySummary(
                    e.getKey(), t.successCount(), t.failedCount(),
                    t.creditCount(), t.creditTotal().toPlainString(),
                    t.debitCount(), t.debitTotal().toPlainString(),
                    t.autoMatchedCount()));
        }
        return java.util.Collections.unmodifiableList(out);
    }

    /** Thread-safe per-currency fold (one writer thread, but guarded so a
     *  concurrent status snapshot read stays consistent). */
    public synchronized void foldCurrency(String ccy, int credit, int debit,
                                          BigDecimal creditAmt, BigDecimal debitAmt, int autoMatched) {
        CurrencyTally t = currencyTally(ccy);
        t.addAccount(credit, debit, creditAmt, debitAmt);
        t.addAutoMatched(autoMatched);
    }

    /** Record one account-level failure (LOGIN_CAN challenge-cancel) in its
     *  currency bucket so a COMPLETED batch's per-currency "Success / Failed"
     *  reflects the continue-past failure. Does not touch credit/debit totals —
     *  a cancelled-login account scraped no transactions. */
    public synchronized void foldCurrencyFailure(String ccy) {
        currencyTally(ccy).addFailure();
        touch();
    }

    /** Per-account failure details (bank / account / currency / code / message)
     *  for accounts that failed WITHOUT aborting the batch — the source of the
     *  popup's per-account failed tooltip. Guarded so a status snapshot read
     *  sees a consistent list. Insertion-ordered. */
    @Getter(AccessLevel.NONE)
    private final java.util.List<FailedAccountDetail> failedAccountDetails = new java.util.ArrayList<>();

    /** Non-secret failed-account locator + sanitized error for the snapshot. */
    public record FailedAccountDetail(String bankCd, String acctNo, String ccyCd,
                                      String errorCode, String errorMessage) { }

    public synchronized void addFailedAccount(String bankCd, String acctNo, String ccyCd,
                                              String errorCode, String errorMessage) {
        failedAccountDetails.add(new FailedAccountDetail(bankCd, acctNo, ccyCd, errorCode, errorMessage));
        touch();
    }

    /** Immutable copy for the wire snapshot (never the live list). */
    public synchronized java.util.List<FailedAccountDetail> failedAccountDetailsSnapshot() {
        return java.util.List.copyOf(failedAccountDetails);
    }

    // Non-secret challenge display metadata for the status snapshot — set when
    // the worker hits a challenge, read by the status endpoint. (The secret
    // answer lives in the pending-resume fields above, never here.)
    @Getter(AccessLevel.NONE)
    private volatile java.util.List<com.kosign.wecms.domain.banking.nxisas.dto.QnaQuestion> challengeQnaQuestions;
    @Getter(AccessLevel.NONE) private volatile String challengeCaptchaImage;

    public java.util.List<com.kosign.wecms.domain.banking.nxisas.dto.QnaQuestion> getChallengeQnaQuestions() {
        return challengeQnaQuestions;
    }
    public void setChallengeQnaQuestions(
            java.util.List<com.kosign.wecms.domain.banking.nxisas.dto.QnaQuestion> q) {
        this.challengeQnaQuestions = q;
    }
    public String getChallengeCaptchaImage()        { return challengeCaptchaImage; }
    public void setChallengeCaptchaImage(String s)  { this.challengeCaptchaImage = s; }

    // Terminal COMPLETED extras, computed once by the worker at finalize and
    // read by the status poll thread (volatile publish).
    @Getter(AccessLevel.NONE) private volatile Boolean autoMatchEnabledFlag;
    @Getter(AccessLevel.NONE) private volatile String lastUpdate;
    public Boolean getAutoMatchEnabledFlag()        { return autoMatchEnabledFlag; }
    public void setAutoMatchEnabledFlag(Boolean b)  { this.autoMatchEnabledFlag = b; }
    public String getLastUpdate()                   { return lastUpdate; }
    public void setLastUpdate(String s)             { this.lastUpdate = s; }
    /** Sub-type discriminator captured at session start so resumed
     *  steps (OTP submit, ACCT_LIST) route to the same Coocon adapter
     *  the LOGIN used. 0 = Corporate / 1 = Personal / 2 = VCB DigiBiz
     *  etc. Defaults to 0. */
    @Setter(AccessLevel.PUBLIC) private volatile int nxisasGbSeqNo;
    /** Coocon thread `Class` field (CORPORATE / COMMON / ""). Legacy
     *  FNSH_0100_001:204-205 reads it from the OSMC API config table per
     *  BANK_CD; spike defaults to "" and the same value is reused on
     *  resumed OTP / ACCT_LIST steps so the gateway routes consistently. */
    @Setter(AccessLevel.PUBLIC) private volatile String bankCategory = "";

    // --- Gateway continuation state (FNSH_0100_001:249-254) ---
    // After LOGIN succeeds the gateway returns a SAS_SessionID plus 5
    // Common fields that must be echoed back verbatim on every subsequent
    // call in the same logical thread (OTP, ACCT_LIST). Omitting them
    // causes the gateway to start a fresh anonymous session and return
    // an empty account list even when login was reported as successful.
    @Setter(AccessLevel.PUBLIC) private volatile String sasSessionID = "";
    @Setter(AccessLevel.PUBLIC) private volatile String gwSysNo = "";
    @Setter(AccessLevel.PUBLIC) private volatile String lbsNo = "";
    @Setter(AccessLevel.PUBLIC) private volatile String ssSysNo = "";
    @Setter(AccessLevel.PUBLIC) private volatile String threadNo = "";

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
    @Setter(AccessLevel.PUBLIC) private volatile Mode mode = Mode.SETUP;

    /** {@code yyyyMMdd} sync window start — captured on the initial
     *  start() call so resumed steps after a QNA / OTP challenge use
     *  the same window. Under {@link #autoWindow} this holds only a
     *  batch-level fallback; each account's real TRANSACTION start is
     *  computed per-account from its own last-success. */
    @Setter(AccessLevel.PUBLIC) private volatile String fromDt;
    /** {@code yyyyMMdd} sync window end — see {@link #fromDt}. */
    @Setter(AccessLevel.PUBLIC) private volatile String toDt;

    /** True when the popup ran in Auto mode (no explicit date range).
     *  In that case the TRANSACTION start is computed <em>per account</em>
     *  from its own last-success date (legacy {@code fnsh_0001_001.ts}
     *  {@code #acct_Reg} parity) instead of using a single batch window —
     *  so a re-sync only pulls new transactions since each account last
     *  synced, never the whole window again. False when the user picked
     *  an explicit from/to range (that window applies to every account). */
    @Setter(AccessLevel.PUBLIC) private volatile boolean autoWindow = false;

    /** FIFO queue of accounts still to scrape after the current
     *  LOGIN succeeds. Drained by the orchestrator inside
     *  {@code submit*} after challenge resolution. The HEAD entry is
     *  the account whose LOGIN is currently in flight (or just
     *  succeeded and is awaiting TRANSACTION+BALANCE). */
    @Setter(AccessLevel.PUBLIC) private volatile Deque<TransactionsSyncRequest.AccountKey> accountsQueue
            = new ArrayDeque<>();

    /** Running count of credit (IN) rows Coocon returned across every
     *  account in the batch — includes rows that hit the dedup skip,
     *  not just newly-inserted ones. Surfaced on the {@code COMPLETED}
     *  response so the popup can show the same totals as the Latest
     *  Transaction History dialog (which reads from the DB) when the
     *  user re-syncs an already-synced window. The field name keeps
     *  the historical "Inserted" suffix for binary compatibility —
     *  semantics changed; field name didn't. */
    @Setter(AccessLevel.PUBLIC) private volatile int totalCreditInserted = 0;
    /** Running count of debit (OUT) rows Coocon returned. See
     *  {@link #totalCreditInserted}. */
    @Setter(AccessLevel.PUBLIC) private volatile int totalDebitInserted = 0;
    /** Sum of {@code TRSC_AMT} for every Coocon-returned IN row.
     *  Surfaced on the {@code COMPLETED} response as the popup's
     *  "Credit" amount. {@code BigDecimal.ZERO} when no rows
     *  scraped; values from
     *  {@link com.kosign.wecms.domain.banking.nxisas.BankTransactionSyncService}
     *  are normalised positive (sign-flips happen before the sum). */
    @Setter(AccessLevel.PUBLIC) private volatile java.math.BigDecimal totalCreditAmount = java.math.BigDecimal.ZERO;
    /** Sum of {@code TRSC_AMT} for every Coocon-returned OUT row.
     *  See {@link #totalCreditAmount}. */
    @Setter(AccessLevel.PUBLIC) private volatile java.math.BigDecimal totalDebitAmount = java.math.BigDecimal.ZERO;
    /** Count of accounts whose sync has completed (LOGIN +
     *  TRANSACTION + BALANCE all succeeded + audit stamps written). */
    @Setter(AccessLevel.PUBLIC) private volatile int accountsSynced = 0;

    /** Count of newly-inserted debit rows the P1 auto-matcher linked to a
     *  payment during this batch (0 when the tenant's biz-env {@code 00079}
     *  gate is off). Surfaced on the COMPLETED response as
     *  {@code auto_matched_count}. */
    @Setter(AccessLevel.PUBLIC) private volatile int totalAutoMatched = 0;

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
        private int failedCount = 0;
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

        /** One account of this currency failed without aborting the batch
         *  (LOGIN_CAN challenge-cancel). No credit/debit rows were scraped. */
        public void addFailure() {
            this.failedCount++;
        }

        /** Fold one account's P1 auto-match result into this currency bucket
         *  (numerator of the per-currency "Debit Matched" row). Called once
         *  per fully-processed account, after {@link #addAccount}. */
        public void addAutoMatched(int autoMatched) {
            this.autoMatchedCount += autoMatched;
        }

        public int successCount()        { return successCount; }
        public int failedCount()         { return failedCount; }
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
    @Setter(AccessLevel.PUBLIC) private volatile String loginId;

    /** Bank/login-group key whose LOGIN currently owns the live
     *  {@link #sasSessionID} on this session. This is the refactor analogue
     *  of legacy {@code nxisas_api.js}'s {@code _nxisas_auth[bankCd]} cache
     *  (nxisas_api.js:2428) that lets every account under one bank reuse a
     *  single login instead of re-authenticating per account. {@code null}
     *  until the first clean LOGIN; updated each time a different group's
     *  LOGIN clears. SYNC mode only — the SETUP flow scrapes one bank per
     *  session and never sets it. */
    @Setter(AccessLevel.PUBLIC) private volatile String activeLoginGroupKey;

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
    @Setter(AccessLevel.PUBLIC) private volatile Deque<CardScrapeKey> cardsQueue = new ArrayDeque<>();

    /** Running count of newly-inserted {@code CARD_TRNS_HSTR} rows across
     *  every card in the batch. */
    @Setter(AccessLevel.PUBLIC) private volatile int cardRowsInserted = 0;
    /** Running count of scraped rows skipped as duplicates (legacy
     *  composite-key dedup hit). */
    @Setter(AccessLevel.PUBLIC) private volatile int cardRowsSkipped = 0;
    /** Count of cards whose CARD_USING completed (login + scrape + persist). */
    @Setter(AccessLevel.PUBLIC) private volatile int cardsSynced = 0;

    public void touch() {
        lastTouched = Instant.now();
    }
}
