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
    /** Discriminator: SETUP = legacy comm_0218 add-credential flow;
     *  SYNC = Account Inquiry &gt; Update popup. */
    public enum Mode { SETUP, SYNC }
    @Setter(AccessLevel.PUBLIC) private Mode mode = Mode.SETUP;

    /** {@code yyyyMMdd} sync window start — captured on the initial
     *  start() call so resumed steps after a QNA / OTP challenge use
     *  the same window. */
    @Setter(AccessLevel.PUBLIC) private String fromDt;
    /** {@code yyyyMMdd} sync window end — see {@link #fromDt}. */
    @Setter(AccessLevel.PUBLIC) private String toDt;

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

    public void touch() {
        lastTouched = Instant.now();
    }
}
