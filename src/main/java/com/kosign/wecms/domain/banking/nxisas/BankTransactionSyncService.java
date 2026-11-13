package com.kosign.wecms.domain.banking.nxisas;

import com.fasterxml.jackson.databind.JsonNode;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.banking.bankcredentials.service.BankCredentialService;
import com.kosign.wecms.domain.banking.bankcredentials.service.BankCredentialService.DecryptedCredential;
import com.kosign.wecms.domain.banking.dto.CurrencySummary;
import com.kosign.wecms.domain.banking.dto.SyncStatusResponse;
import com.kosign.wecms.domain.banking.dto.TransactionsSyncRequest;
import com.kosign.wecms.domain.banking.entity.AcctMstr;
import com.kosign.wecms.domain.banking.nxisas.BankTransactionSyncPersistence.PersistResult;
import com.kosign.wecms.domain.banking.nxisas.NxisasGatewayClient.GatewayContinuation;
import com.kosign.wecms.domain.banking.nxisas.dto.QnaQuestion;
import com.kosign.wecms.domain.banking.nxisas.dto.SubmitChallengeRequest;
import com.kosign.wecms.domain.banking.nxisas.dto.SubmitQnaRequest;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasApiOut;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasBalanceEntry;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasBalanceJobInput;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasBalanceJobResult;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasCaptchaJobInput;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasLoginJobInput;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasLoginJobResult;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasLogoutJobInput;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasOTPJobInput;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasOTPJobResult;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasOutput;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasQnaJobInput;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasTransactionEntry;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasTransactionJobInput;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasTransactionJobResult;
import com.kosign.wecms.domain.banking.nxisas.session.NxisasScrapeSession;
import com.kosign.wecms.domain.banking.nxisas.session.NxisasSessionRegistry;
import com.kosign.wecms.domain.banking.nxisas.session.ScrapeChallengeKind;
import com.kosign.wecms.domain.banking.repository.AcctMstrRepository;
import com.kosign.wecms.domain.banking.service.AutoMatchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * Bank Transaction Sync orchestrator (Phase 2 — asynchronous, re-entrant).
 *
 * <p>The initial POST validates + registers a tenant-scoped
 * {@link NxisasScrapeSession}, schedules a background scrape <b>segment</b> on
 * the bounded {@code bankSyncExecutor}, and returns a RUNNING status snapshot
 * <b>immediately</b> (the scrape does not run on the request thread). The
 * frontend then polls {@link #status}. Progress is driven by real backend
 * stages (0 → 25 → 35 → 50 → 100 per account) and never moves backward.
 *
 * <p><b>Re-entrant state machine (no parked threads).</b> A worker segment runs
 * only until it reaches a boundary: COMPLETED, FAILED, CANCELLED, or a bank
 * challenge (OTP / QNA / CAPTCHA). On a challenge it persists the gateway
 * continuation + challenge metadata on the session, sets the matching
 * {@code AWAITING_CHALLENGE} state, and <b>returns</b> — releasing the worker
 * thread and holding no DB connection, latch, or queue wait. When the user
 * submits the answer, the resume endpoint transitions the session back to
 * RUNNING and schedules a <b>fresh</b> segment that continues from the saved
 * continuation, preserving progress + completed-account counts.
 *
 * <p><b>Transaction boundaries.</b> Coocon gateway calls run outside any DB
 * transaction; each persistence phase is a short {@code @Transactional} call on
 * {@link BankTransactionSyncPersistence}. No transaction is ever held across a
 * gateway round-trip or a challenge wait.
 *
 * <p><b>Cancellation.</b> {@link #cancel} (universal from the moment the
 * session exists) sets a cooperative flag, best-effort LOGOUTs the bank session
 * (legacy {@code _v2nxisas.stopAllNxisas}), and marks CANCELLED. The worker
 * checks the flag at every boundary and stops before the next account/phase.
 *
 * <p>Error codes: {@code WBE2050} no credential · {@code WBE_BANK_ACCOUNT_NOT_FOUND}
 * · {@code WBE2093} no bank config · {@code WBE2060} session not found / expired /
 * wrong-tenant / terminal · {@code WBE9001} wrong resume verb · {@code WBE2095}
 * bank/gateway rejected · {@code WBE2096} all HAND_REG.
 */
@Service
public class BankTransactionSyncService {

    private static final Logger log = LoggerFactory.getLogger(BankTransactionSyncService.class);

    private static final String OK_CODE = NxisasResultCodes.OK;
    /** Legacy per-account "login inquiry cancelled" result code (legacy
     *  {@code nxisas_v2.js run_afterLoginFail} `ErrorCode = "LOGIN_CAN"`). Set
     *  on an account whose 2nd-cert challenge (QNA/OTP/CAPTCHA) the user
     *  cancels — the account fails but the batch continues (§15 Case 2). */
    private static final String LOGIN_CAN = "LOGIN_CAN";
    /** Stored ACCT_MSTR failure message for a LOGIN_CAN account. The frontend
     *  renders the localized label via the shared scraping-result-code map
     *  ({@code fb_history.result.LOGIN_CAN}); this is the DB/audit fallback. */
    private static final String LOGIN_CAN_MSG = "Login inquiry was cancelled.";
    private static final DateTimeFormatter DTM_14 = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final DateTimeFormatter DTM_DISPLAY = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DT_8 = DateTimeFormatter.ofPattern("yyyyMMdd");

    // Per-account progress fractions (phaseFraction in the overall formula).
    private static final double FRACTION_START = 0.00;   // 0%   — account processing begins
    private static final double FRACTION_TX     = 0.25;   // 25%  — TRANSACTION scrape complete
    private static final double FRACTION_PERSIST = 0.35;  // 35%  — scraped rows deduped + persisted
    private static final double FRACTION_BALANCE = 0.50;  // 50%  — BALANCE processing started

    // Fixed-Deposit routing — legacy nxisas_tranList_osmc.js:300-361.
    /** Legacy Fixed-Deposit (Time-Deposit) account-type code (ACCT_MSTR.ACCT_TYPE_CD). */
    private static final String ACCT_TYPE_FIXED_DEPOSIT = "02";
    /** VCB — the only FD bank that scrapes BALANCE_FIXED_DEPOSIT +
     *  TRANSACTION_FIXED_DEPOSIT (nxisas_tranList_osmc.js:301). */
    private static final String BANK_VCB = "11704004";
    /** FD banks that scrape BALANCE_FIXED_DEPOSIT only — no
     *  TRANSACTION_FIXED_DEPOSIT support (nxisas_tranList_osmc.js:323-329):
     *  Shinhan, ACB, HD, VietinBank, VPBank. */
    private static final java.util.Set<String> FD_BALANCE_ONLY_BANKS = java.util.Set.of(
            "11704088", // Shinhan
            "11704011", // ACB
            "11704101", // HD
            "11704001", // Vietin
            "11704102"  // VP
    );

    /** Per-account scrape routing derived from {@code ACCT_TYPE_CD} + {@code BANK_CD}. */
    private enum ScrapeMode {
        /** Current / Savings — and FD at a non-whitelisted bank: BALANCE + TRANSACTION. */
        REGULAR,
        /** VCB Fixed Deposit: BALANCE_FIXED_DEPOSIT + TRANSACTION_FIXED_DEPOSIT. */
        FD_WITH_TRANSACTION,
        /** Shinhan/ACB/HD/Vietin/VP Fixed Deposit: BALANCE_FIXED_DEPOSIT only. */
        FD_BALANCE_ONLY
    }

    /**
     * Route one account to its scrape mode — legacy
     * {@code nxisas_tranList_osmc.js:300-361}. Fixed-Deposit accounts
     * ({@code ACCT_TYPE_CD "02"}) are gated to a fixed bank whitelist: VCB
     * scrapes FD balance + FD transactions, the other five whitelisted banks
     * scrape FD balance only. An FD account at any other bank falls through to
     * the regular BALANCE + TRANSACTION pair (legacy else-branch at line 340).
     */
    private static ScrapeMode scrapeModeFor(AcctMstr acct) {
        if (!ACCT_TYPE_FIXED_DEPOSIT.equals(acct.getAcctTypeCd())) {
            return ScrapeMode.REGULAR;
        }
        if (BANK_VCB.equals(acct.getBankCd())) {
            return ScrapeMode.FD_WITH_TRANSACTION;
        }
        if (FD_BALANCE_ONLY_BANKS.contains(acct.getBankCd())) {
            return ScrapeMode.FD_BALANCE_ONLY;
        }
        return ScrapeMode.REGULAR;
    }

    private final BankCredentialService credentialService;
    private final NxisasGatewayClient gateway;
    private final NxisasBankConfig bankConfig;
    private final AcctMstrRepository acctRepo;
    private final com.kosign.wecms.domain.banking.synchistory.service.SyncHistoryWriter syncHistoryWriter;
    private final AcctMstrStatusUpdater statusUpdater;
    private final AutoMatchService autoMatchService;
    private final NxisasSessionRegistry registry;
    private final BankTransactionSyncPersistence persistence;
    private final Executor executor;

    public BankTransactionSyncService(
            BankCredentialService credentialService,
            NxisasGatewayClient gateway,
            NxisasBankConfig bankConfig,
            AcctMstrRepository acctRepo,
            com.kosign.wecms.domain.banking.synchistory.service.SyncHistoryWriter syncHistoryWriter,
            AcctMstrStatusUpdater statusUpdater,
            AutoMatchService autoMatchService,
            NxisasSessionRegistry registry,
            BankTransactionSyncPersistence persistence,
            @Qualifier(BankSyncExecutorConfig.EXECUTOR) Executor executor) {
        this.credentialService = credentialService;
        this.gateway = gateway;
        this.bankConfig = bankConfig;
        this.acctRepo = acctRepo;
        this.syncHistoryWriter = syncHistoryWriter;
        this.statusUpdater = statusUpdater;
        this.autoMatchService = autoMatchService;
        this.registry = registry;
        this.persistence = persistence;
        this.executor = executor;
    }

    // ─────────────────────────────────────────────────────────────────
    // Public entry points (AccountController)
    // ─────────────────────────────────────────────────────────────────

    /**
     * Start a new sync batch. Validates + registers the session, schedules the
     * background scrape once, and returns the initial RUNNING snapshot
     * immediately (never runs the scrape on the request thread).
     */
    public SyncStatusResponse start(TransactionsSyncRequest req) {
        final OffsetDateTime startedAt = OffsetDateTime.now(ZoneOffset.UTC);
        final boolean autoWindow = req.fromDt() == null || req.toDt() == null;
        final String fromDt = autoWindow ? startedAt.minusMonths(3).format(DT_8) : req.fromDt().replace("-", "");
        final String toDt = autoWindow ? startedAt.format(DT_8) : req.toDt().replace("-", "");

        // Legacy 92-day cap (nxisas_tranList.js:220). Re-validated server-side.
        if (req.fromDt() != null && req.toDt() != null) {
            try {
                java.time.LocalDate from = java.time.LocalDate.parse(fromDt, DT_8);
                java.time.LocalDate to = java.time.LocalDate.parse(toDt, DT_8);
                long days = java.time.temporal.ChronoUnit.DAYS.between(from, to) + 1;
                if (days > 92) {
                    throw new BusinessException("WBE_BANK_TRANSACTION_SYNC_RANGE_TOO_LONG");
                }
            } catch (java.time.format.DateTimeParseException e) {
                throw new BusinessException("WBE_VALIDATION");
            }
        }

        // Drop HAND_REG accounts (no credential); WBE2096 when all are HAND_REG.
        final String bizKey = requireBizKey();
        List<TransactionsSyncRequest.AccountKey> filtered = new ArrayList<>(req.accounts().size());
        List<com.kosign.wecms.domain.banking.entity.AcctMstrId> ids = new ArrayList<>(req.accounts().size());
        for (TransactionsSyncRequest.AccountKey key : req.accounts()) {
            ids.add(new com.kosign.wecms.domain.banking.entity.AcctMstrId(bizKey, key.bankCd(), key.acctNo()));
        }
        java.util.Map<String, AcctMstr> byKey = new java.util.HashMap<>();
        for (AcctMstr acct : acctRepo.findAllById(ids)) {
            byKey.put(acct.getBankCd() + " " + acct.getAcctNo(), acct);
        }
        for (TransactionsSyncRequest.AccountKey key : req.accounts()) {
            AcctMstr acct = byKey.get(key.bankCd() + " " + key.acctNo());
            if (acct == null) {
                throw new BusinessException("WBE_BANK_ACCOUNT_NOT_FOUND");
            }
            if ("Y".equalsIgnoreCase(acct.getHandRegYn())) {
                log.info("BankTransactionSync skipping HAND_REG account [bank={}, acct={}]",
                        key.bankCd(), key.acctNo());
                continue;
            }
            filtered.add(key);
        }
        if (filtered.isEmpty()) {
            throw new BusinessException("WBE2096");
        }

        // Order the queue so accounts of the same login group are contiguous
        // (login-once-per-CREDENTIAL; legacy nxisas_tranList_osmc.js:265 groups
        // the real-time inquiry by BANK_CERT_SEQ, not by BANK_CD).
        java.util.LinkedHashMap<String, List<TransactionsSyncRequest.AccountKey>> grouped =
                new java.util.LinkedHashMap<>();
        for (TransactionsSyncRequest.AccountKey key : filtered) {
            AcctMstr acct = byKey.get(key.bankCd() + " " + key.acctNo());
            grouped.computeIfAbsent(loginGroupKey(acct), k -> new ArrayList<>()).add(key);
        }
        Deque<TransactionsSyncRequest.AccountKey> queue = new ArrayDeque<>(filtered.size());
        for (List<TransactionsSyncRequest.AccountKey> group : grouped.values()) {
            queue.addAll(group);
        }

        NxisasScrapeSession session = newSyncSession(queue, fromDt, toDt, autoWindow);
        session.setTotalAccounts(filtered.size());
        session.setStatus(NxisasScrapeSession.Status.RUNNING);
        registry.register(session);
        NxisasApiLog.event(tid(session), NxisasApiLog.FLOW_BANK_SYNC, "START",
                "accounts", filtered.size(), "groups", grouped.size());

        // Snapshot the initial RUNNING/0 state BEFORE scheduling so the return
        // value is deterministic regardless of how fast the worker starts.
        SyncStatusResponse initial = buildSnapshot(session);
        scheduleWorker(session);
        return initial;
    }

    /** Read-only status snapshot for polling. Tenant-validated; no side effects. */
    public SyncStatusResponse status(UUID sessionId) {
        return buildSnapshot(requireOwnedSyncSession(sessionId));
    }

    /** Resume after an OTP submission — schedules a fresh worker segment. */
    public SyncStatusResponse submitOtp(UUID sessionId, SubmitChallengeRequest req) {
        NxisasScrapeSession session = requireResumableSyncSession(sessionId, ScrapeChallengeKind.OTP);
        if (session.isWorkerRunning()) return buildSnapshot(session); // duplicate resume — idempotent
        session.setPendingOtp(req.value());
        return resume(session, "OTP");
    }

    /** Resume after a QNA (security-question) answer. */
    public SyncStatusResponse submitQna(UUID sessionId, SubmitQnaRequest req) {
        NxisasScrapeSession session = requireResumableSyncSession(sessionId, ScrapeChallengeKind.QNA);
        if (session.isWorkerRunning()) return buildSnapshot(session);
        session.setPendingQna(new NxisasScrapeSession.PendingQna(
                req.question(), req.hiddenValue(), req.answer()));
        return resume(session, "QNA");
    }

    /** Resume after a CAPTCHA submission. */
    public SyncStatusResponse submitCaptcha(UUID sessionId, SubmitChallengeRequest req) {
        NxisasScrapeSession session = requireResumableSyncSession(sessionId, ScrapeChallengeKind.CAPTCHA);
        if (session.isWorkerRunning()) return buildSnapshot(session);
        session.setPendingCaptcha(req.value());
        return resume(session, "CAPTCHA");
    }

    private SyncStatusResponse resume(NxisasScrapeSession session, String kind) {
        session.setStatus(NxisasScrapeSession.Status.RUNNING);
        NxisasApiLog.event(tid(session), NxisasApiLog.FLOW_BANK_SYNC, "CHALLENGE_SUBMIT", "type", kind);
        scheduleWorker(session);
        return buildSnapshot(session);
    }

    /**
     * Cancel a sync (universal — works from the moment the session exists,
     * during RUNNING, any challenge wait, or resumed processing). Idempotent +
     * tenant-validated. Preserves the Phase 1 contract: cooperative flag +
     * best-effort gateway LOGOUT + mark CANCELLED. The session is <b>retained</b>
     * (not discarded) so the frontend's final poll can read the CANCELLED
     * snapshot; the registry evicts it after the retention window.
     */
    public SyncStatusResponse cancel(UUID sessionId) {
        String bizKey = requireBizKey();
        String userId = TenantContext.currentUserId();
        Optional<NxisasScrapeSession> found = registry.find(sessionId, bizKey, userId);
        if (found.isEmpty()) {
            return cancelledSnapshot(sessionId); // idempotent — nothing to cancel
        }
        NxisasScrapeSession session = found.get();
        if (session.getMode() != NxisasScrapeSession.Mode.SYNC) {
            return cancelledSnapshot(sessionId); // not ours — no-op
        }
        // Idempotency + terminal precedence: an already-terminal session is
        // returned UNCHANGED. A late cancel after COMPLETED / FAILED must not
        // overwrite the real terminal state (COMPLETED/FAILED wins), must not
        // re-mutate progress/counters/terminalAt, and — per the "no logout after
        // a terminal state" policy — must not fire a second gateway LOGOUT (the
        // continuation was already closed by finalize/fail; a repeated CANCELLED
        // likewise already logged out on its first pass). See §15.14.
        if (session.isTerminal()) {
            NxisasApiLog.event(tid(session), NxisasApiLog.FLOW_BANK_SYNC, "CANCEL_NOOP_TERMINAL",
                    "status", session.getStatus());
            return buildSnapshot(session);
        }
        session.touch();
        session.requestCancel();
        NxisasApiLog.event(tid(session), NxisasApiLog.FLOW_BANK_SYNC, "CANCEL_REQUESTED",
                "status", session.getStatus(), "accountsSynced", session.getAccountsSynced());
        // Live session (RUNNING / *_REQUIRED) → the continuation is still open,
        // so LOGOUT is appropriate (best-effort).
        bestEffortLogout(session);
        // Once-only terminal transition. If a concurrent worker finalized
        // COMPLETED/FAILED between the isTerminal() check above and here, the CAS
        // loses and we respect that terminal instead of clobbering it to CANCELLED.
        if (!session.tryMarkTerminal(NxisasScrapeSession.Status.CANCELLED)) {
            NxisasApiLog.event(tid(session), NxisasApiLog.FLOW_BANK_SYNC, "CANCEL_LOST_RACE",
                    "status", session.getStatus());
        }
        return buildSnapshot(session);
    }

    /**
     * Cancel the CURRENT 2nd-cert challenge (QNA / OTP / CAPTCHA) — an
     * <b>account-level</b> operation, NOT a whole-session cancel (§15 Case 2,
     * strict legacy parity with {@code nxisas_v2.js} `run_afterLoginFail` /
     * WBC1152). The account(s) whose LOGIN raised the challenge are recorded as
     * {@code LOGIN_CAN} failures and the batch <b>continues</b> with the next
     * login group; the session is NOT marked CANCELLED. When the queue drains
     * the worker finalizes COMPLETED normally, and the popup shows the standard
     * "Success X / Failed Y" summary.
     *
     * <p>Legacy fails the whole bank login thread, so all queued accounts under
     * the current login group (keyed by {@code BANK_CD}) fail together — for the
     * common one-account-per-bank case that is exactly the current account.
     * Progress, completed/success counters, and per-currency Credit/Debit totals
     * already earned are preserved (progress is monotonic).
     *
     * <p>Idempotent + tenant-validated: an already-terminal session, or one not
     * currently awaiting a challenge, returns its snapshot unchanged; a session
     * whose worker is mid-flight (a resume raced in) is left to that worker.
     */
    public SyncStatusResponse cancelChallenge(UUID sessionId) {
        NxisasScrapeSession session = requireOwnedSyncSession(sessionId);
        if (session.isTerminal()) {
            return buildSnapshot(session); // nothing to do — batch already finished/cancelled
        }
        if (session.getStatus() != NxisasScrapeSession.Status.AWAITING_CHALLENGE
                || session.getChallengeKind() == null) {
            return buildSnapshot(session); // not awaiting a challenge — no-op
        }
        if (session.isWorkerRunning()) {
            return buildSnapshot(session); // a resume is mid-flight — let it run
        }

        failCurrentAccountAsLoginCancelled(session);
        clearChallengeForContinue(session);
        session.setStatus(NxisasScrapeSession.Status.RUNNING);
        NxisasApiLog.event(tid(session), NxisasApiLog.FLOW_BANK_SYNC, "CHALLENGE_CANCELLED",
                "failedAccounts", session.getFailedAccounts(),
                "remaining", session.getAccountsQueue().size());
        scheduleWorker(session); // continue with the next account → eventual COMPLETED
        return buildSnapshot(session);
    }

    /** Record ONLY the current account (the queue head whose login raised the
     *  challenge) as a {@code LOGIN_CAN} failure and drop it from the queue.
     *  {@code completedAccounts}/{@code failedAccounts} increment once;
     *  {@code successAccounts} is unchanged. The next account (its login retries
     *  fresh) is then processed by the resumed worker — legacy
     *  {@code run_afterLoginFail} for the cancelled login attempt. */
    private void failCurrentAccountAsLoginCancelled(NxisasScrapeSession session) {
        Deque<TransactionsSyncRequest.AccountKey> queue = session.getAccountsQueue();
        TransactionsSyncRequest.AccountKey head = queue.pollFirst();
        if (head == null) return;
        String bizKey = session.getBizKey();
        String userId = session.getUserId();
        String nowDb = OffsetDateTime.now(ZoneOffset.UTC).format(DTM_14);
        String ccy = acctRepo.findByBizKeyAndBankCdAndAcctNo(bizKey, head.bankCd(), head.acctNo())
                .map(AcctMstr::getCcyCd).orElse("");

        statusUpdater.markFailure(bizKey, head.bankCd(), head.acctNo(), LOGIN_CAN, LOGIN_CAN_MSG, userId, nowDb);
        syncHistoryWriter.recordAttempt(bizKey, userId, head.bankCd(), head.acctNo(),
                session.getFromDt(), session.getToDt(), LOGIN_CAN, LOGIN_CAN_MSG);
        session.completeAccount(false);      // completedAccounts++ + failedAccounts++ (once)
        session.foldCurrencyFailure(ccy);    // per-currency Failed++
        session.addFailedAccount(head.bankCd(), head.acctNo(), ccy, LOGIN_CAN, LOGIN_CAN_MSG);
        NxisasApiLog.event(tid(session), NxisasApiLog.FLOW_BANK_SYNC, "LOGIN_CANCELLED",
                "bankCd", head.bankCd(), "acctNo", NxisasMask.acct(head.acctNo()));
    }

    /** Clear the challenge display metadata + pending secret + the failed
     *  login's continuation so the next group logs in fresh — WITHOUT marking
     *  the session terminal (the batch continues). */
    private void clearChallengeForContinue(NxisasScrapeSession session) {
        session.setChallengeKind(null);
        session.setChallengePrompt(null);
        session.setChallengeQnaQuestions(null);
        session.setChallengeCaptchaImage(null);
        session.clearPendingResume();
        session.setActiveLoginGroupKey(null);
        storeContinuation(session, new GatewayContinuation("", "", "", "", ""));
    }

    // ─────────────────────────────────────────────────────────────────
    // Background worker (bankSyncExecutor)
    // ─────────────────────────────────────────────────────────────────

    /** Schedule one worker segment. The {@code workerRunning} CAS guard makes
     *  this idempotent — a second call while a segment runs is a no-op (prevents
     *  duplicate scheduling AND duplicate resume from spawning two workers). */
    private void scheduleWorker(NxisasScrapeSession session) {
        if (!session.tryStartWorker()) {
            return; // a segment is already running/queued for this session
        }
        final TenantContext.Principal principal = TenantContext.current();
        try {
            executor.execute(() -> runSegment(session, principal));
        } catch (RejectedExecutionException rex) {
            // Bounded executor saturated — release the guard + fail the session
            // (bounded, observable) rather than block a request thread.
            session.finishWorker();
            session.setErrorCode("WBE9999");
            session.setErrorMessage(null);
            session.markTerminal(NxisasScrapeSession.Status.FAILED);
            log.warn("BankTransactionSync executor saturated — session FAILED [session={}]",
                    session.getSessionId());
        }
    }

    /** One background scrape segment. Re-establishes the tenant context (the
     *  worker runs off the request thread), runs until a boundary, and always
     *  releases the worker guard + clears the tenant context in {@code finally}. */
    private void runSegment(NxisasScrapeSession session, TenantContext.Principal principal) {
        // Save + restore the thread's prior context: on a real background
        // thread there is none (→ cleared), but an inline/same-thread executor
        // (tests) must get its caller context back, not wiped.
        TenantContext.Principal prev = TenantContext.current();
        try {
            if (principal != null) {
                TenantContext.set(principal.userId(), principal.userNm(), principal.bizKey(),
                        principal.langCd(), principal.authGrCd(), principal.prdtType());
            }
            if (session.isCancelled()) { markCancelledWorker(session); return; }
            advanceAsync(session);
        } catch (BusinessException be) {
            failSession(session, be.getCode(), null);
        } catch (RuntimeException e) {
            log.error("BankTransactionSync worker crashed [session={}]", session.getSessionId(), e);
            failSession(session, "WBE9999", null);
        } finally {
            if (prev != null) {
                TenantContext.set(prev.userId(), prev.userNm(), prev.bizKey(),
                        prev.langCd(), prev.authGrCd(), prev.prdtType());
            } else {
                TenantContext.clear();
            }
            session.finishWorker();
        }
    }

    /**
     * Drive the queue forward within one segment. First resumes a pending
     * challenge answer (if any), then processes accounts until the queue drains
     * (→ finalize / COMPLETED) or a boundary is hit (challenge / error / cancel).
     */
    private void advanceAsync(NxisasScrapeSession session) {
        // ── Resume-from-challenge leg ──
        if (session.hasPendingResume()) {
            LoginOutcome o = doPendingResume(session);
            if (o != LoginOutcome.CLEAN) return; // CHALLENGE / ERROR / CANCELLED boundary
        }

        // ── Account loop ──
        while (!session.getAccountsQueue().isEmpty()) {
            if (session.isCancelled()) { markCancelledWorker(session); return; }

            TransactionsSyncRequest.AccountKey key = session.getAccountsQueue().peekFirst();
            AcctMstr acct = acctRepo.findByBizKeyAndBankCdAndAcctNo(
                            session.getBizKey(), key.bankCd(), key.acctNo())
                    .orElseThrow(() -> new BusinessException("WBE_BANK_ACCOUNT_NOT_FOUND"));

            // Login-once-per-credential reuse (nxisas_tranList_osmc.js:265).
            if (loginGroupKey(acct).equals(session.getActiveLoginGroupKey()) && hasLiveContinuation(session)) {
                NxisasApiLog.event(tid(session), NxisasApiLog.FLOW_BANK_SYNC, "SESSION_REUSE",
                        "bankCd", key.bankCd(), "acctNo", NxisasMask.acct(key.acctNo()));
                if (!processAccountAsync(session, acct)) return; // stopped (cancel / fail)
                session.getAccountsQueue().pollFirst();
                continue;
            }

            // New login group → LOGIN with THIS account's own credential.
            DecryptedCredential cred = resolveCredentialForAccount(acct);
            session.setLoginId(cred.username());
            Integer subTypeSeqNo = cred.nxisasGbSeqNo() == null ? null : cred.nxisasGbSeqNo().intValue();
            NxisasBankConfig.Entry cfg = bankConfig.find(acct.getBankCd(), subTypeSeqNo)
                    .orElseThrow(() -> new BusinessException("WBE2093", acct.getBankCd()));
            session.setBankCategory(cfg.category());
            session.setNxisasGbSeqNo(subTypeSeqNo == null ? 0 : subTypeSeqNo);

            NxisasLoginJobInput in = new NxisasLoginJobInput();
            in.setLoginId(cred.username());
            in.setLoginPwd(cred.password());
            in.setCountryCode("VNM");
            in.setLoginType(NxisasBankConfig.defaultLoginType(cfg.module(), cred.loginType()));
            if (cred.corporateId() != null && !cred.corporateId().isBlank()) {
                in.setCorporateId(cred.corporateId());
                in.setLoginPwd1(cred.corporateId());
            }

            if (session.isCancelled()) { markCancelledWorker(session); return; } // before gateway
            NxisasApiLog.event(tid(session), NxisasApiLog.FLOW_BANK_SYNC, "LOGIN_START",
                    "bankCd", acct.getBankCd(), "module", cfg.module(), "class", cfg.category());
            String trSeq = gateway.newTrSeq();
            NxisasApiOut<NxisasLoginJobResult> out = gateway.login(cfg.module(), cfg.category(), trSeq, in);
            storeContinuation(session, GatewayContinuation.from(out));
            setBankModuleViaReflection(session, cfg.module());
            if (session.isCancelled()) { markCancelledWorker(session); return; } // after gateway

            LoginOutcome o = handleLoginResult(session, out, "LOGIN");
            if (o != LoginOutcome.CLEAN) return; // CHALLENGE / ERROR boundary
            // CLEAN → activeLoginGroupKey set; loop again → reuse branch processes head
        }

        finalizeAsync(session);
    }

    private enum LoginOutcome { CLEAN, CHALLENGE, ERROR }

    /** Do the pending gateway challenge-resume call, clear the secret, then
     *  classify the result. */
    private LoginOutcome doPendingResume(NxisasScrapeSession session) {
        ScrapeChallengeKind kind = session.getPendingResumeKind();
        String trSeq = gateway.newTrSeq();
        NxisasApiOut<NxisasLoginJobResult> out;
        switch (kind) {
            case OTP -> {
                NxisasOTPJobInput otpIn = new NxisasOTPJobInput();
                otpIn.setRes(session.getPendingChallengeValue());
                NxisasApiOut<NxisasOTPJobResult> otpOut = gateway.submitOtp(
                        session.getBankModule(), session.getBankCategory(), trSeq, otpIn, continuationOf(session));
                storeContinuation(session, GatewayContinuation.from(otpOut));
                out = new NxisasApiOut<>();
                out.setCommon(otpOut.getCommon());
                out.setResultList(otpToLoginResults(otpOut));
                out.setSasSessionID(otpOut.getSasSessionID());
            }
            case CAPTCHA -> {
                NxisasCaptchaJobInput capIn = new NxisasCaptchaJobInput();
                capIn.setRes(session.getPendingChallengeValue());
                out = gateway.submitCaptcha(session.getBankModule(), session.getBankCategory(), trSeq,
                        capIn, continuationOf(session));
                storeContinuation(session, GatewayContinuation.from(out));
            }
            default -> { // QNA
                NxisasScrapeSession.PendingQna q = session.getPendingQna();
                NxisasQnaJobInput.QnaResponse res = new NxisasQnaJobInput.QnaResponse();
                res.setQuestion(q == null ? null : q.question());
                res.setHiddenValue(q == null ? null : q.hiddenValue());
                res.setAnswer(q == null ? null : q.answer());
                NxisasQnaJobInput qnaIn = new NxisasQnaJobInput();
                qnaIn.setRes(res);
                out = gateway.submitQna(session.getBankModule(), session.getBankCategory(), trSeq,
                        qnaIn, continuationOf(session));
                storeContinuation(session, GatewayContinuation.from(out));
            }
        }
        session.clearPendingResume(); // drop the secret immediately after the call
        if (session.isCancelled()) { markCancelledWorker(session); return LoginOutcome.ERROR; }
        return handleLoginResult(session, out, kind.name());
    }

    /**
     * Classify a LOGIN-shaped response: bank/gateway error → FAILED terminal;
     * challenge → AWAITING_CHALLENGE (retained for polling + resume); clean →
     * stamp the active login group and return CLEAN (the loop then processes
     * the head account via the reuse branch).
     */
    private LoginOutcome handleLoginResult(NxisasScrapeSession session,
                                           NxisasApiOut<NxisasLoginJobResult> out, String stage) {
        NxisasOutput<?> bankOut = gateway.firstOutput(out);
        if (bankOut != null) {
            String code = bankOut.getErrorCode();
            if (!NxisasResultCodes.isBankAdapterSuccess(code)) {
                String msg = bankOut.getErrorMessage();
                if (msg == null || msg.isBlank()) msg = bankOut.getDetailMessage();
                NxisasApiLog.event(tid(session), NxisasApiLog.FLOW_BANK_SYNC, "ERROR",
                        "stage", stage, "scope", "BANK", "errorCode", code);
                logFailedAttempt(session, code, msg);
                stampScrapeFailure(session, code, msg);
                failSession(session, code, msg);
                return LoginOutcome.ERROR;
            }
        }
        if (gatewayRejected(out)) {
            String code = out.getCommon().getResultCd();
            String msg = out.getCommon().getResultMsg();
            NxisasApiLog.event(tid(session), NxisasApiLog.FLOW_BANK_SYNC, "ERROR",
                    "stage", stage, "scope", "GATEWAY", "resultCode", code);
            logFailedAttempt(session, code, msg);
            stampScrapeFailure(session, code, msg);
            failSession(session, code, msg);
            return LoginOutcome.ERROR;
        }

        NxisasLoginJobResult result = gateway.firstResult(out);
        ScrapeChallengeKind kind = challengeKindFrom(result);
        if (kind != null) {
            session.setChallengeKind(kind);
            session.setChallengePrompt(result == null ? null : result.challengePrompt());
            session.setChallengeQnaQuestions(kind == ScrapeChallengeKind.QNA ? qnaQuestionsFrom(result) : null);
            session.setChallengeCaptchaImage(kind == ScrapeChallengeKind.CAPTCHA ? captchaImageFrom(result) : null);
            session.setStatus(NxisasScrapeSession.Status.AWAITING_CHALLENGE);
            NxisasApiLog.event(tid(session), NxisasApiLog.FLOW_BANK_SYNC, "CHALLENGE_REQUIRED", "type", kind);
            return LoginOutcome.CHALLENGE;
        }

        TransactionsSyncRequest.AccountKey head = session.getAccountsQueue().peekFirst();
        if (head != null) session.setActiveLoginGroupKey(loginGroupKey(session, head));
        NxisasApiLog.event(tid(session), NxisasApiLog.FLOW_BANK_SYNC, "LOGIN_SUCCESS",
                "stage", stage, "sasSessionId", NxisasMask.token(session.getSasSessionID()));
        return LoginOutcome.CLEAN;
    }

    /**
     * Per-account TRANSACTION → persist → auto-match → BALANCE → stamp, updating
     * real progress at each stage (25 / 35 / 50 / 100). Gateway calls are outside
     * any DB transaction; persistence phases are short {@code @Transactional}
     * calls. Returns {@code true} to continue the loop, {@code false} when the
     * segment stopped at a terminal boundary (cancel / fail-fast).
     */
    private boolean processAccountAsync(NxisasScrapeSession session, AcctMstr acct) {
        String bizKey = session.getBizKey();
        String userId = session.getUserId();
        String bankCd = acct.getBankCd();
        String acctNo = acct.getAcctNo();
        String ccy = acct.getCcyCd();
        String nowDb = OffsetDateTime.now(ZoneOffset.UTC).format(DTM_14);

        if (session.isCancelled()) { markCancelledWorker(session); return false; }
        session.enterPhase(NxisasScrapeSession.Phase.SCRAPING_TRANSACTIONS, FRACTION_START); // 0%

        final String startDate = session.isAutoWindow() ? perAccountStartDt(acct) : session.getFromDt();
        NxisasBankConfig.Entry cfg = bankConfig.find(bankCd, session.getNxisasGbSeqNo())
                .orElseThrow(() -> new BusinessException("WBE2093", bankCd));

        // Fixed-Deposit routing — legacy nxisas_tranList_osmc.js:300-361.
        // VCB FD → FD balance + FD transaction; the five balance-only FD banks
        // → FD balance, no transaction job; everything else → regular pair.
        final ScrapeMode mode = scrapeModeFor(acct);

        // ── TRANSACTION (gateway; no DB tx) ──
        // Balance-only FD banks send no transaction job (legacy has no
        // TRANSACTION_FIXED_DEPOSIT support for them); the account still
        // completes successfully on the BALANCE leg alone.
        List<NxisasTransactionEntry> entries;
        if (mode == ScrapeMode.FD_BALANCE_ONLY) {
            NxisasApiLog.event(tid(session), NxisasApiLog.FLOW_BANK_SYNC, "TRANSACTION_SKIP",
                    "bankCd", bankCd, "acctNo", NxisasMask.acct(acctNo), "reason", "FD_BALANCE_ONLY");
            entries = List.of();
        } else {
            NxisasTransactionJobInput tIn = new NxisasTransactionJobInput();
            tIn.setAcctNum(acctNo);
            tIn.setCurrency(ccy);
            tIn.setStartDate(startDate);
            tIn.setEndDate(session.getToDt());
            if (session.isCancelled()) { markCancelledWorker(session); return false; } // before gateway
            NxisasApiLog.event(tid(session), NxisasApiLog.FLOW_BANK_SYNC, "TRANSACTION_START",
                    "bankCd", bankCd, "acctNo", NxisasMask.acct(acctNo),
                    "fixedDeposit", mode == ScrapeMode.FD_WITH_TRANSACTION);
            String trSeq = gateway.newTrSeq();
            NxisasApiOut<NxisasTransactionJobResult> tOut = mode == ScrapeMode.FD_WITH_TRANSACTION
                    ? gateway.transactionFixedDeposit(cfg.module(), cfg.category(), trSeq, tIn, continuationOf(session))
                    : gateway.transaction(cfg.module(), cfg.category(), trSeq, tIn, continuationOf(session));
            if (session.isCancelled()) { markCancelledWorker(session); return false; } // after gateway

            String txFailCode = scrapeFailureCode(tOut);
            if (txFailCode != null) {
                statusUpdater.markFailure(bizKey, bankCd, acctNo, txFailCode, scrapeFailureMsg(tOut), userId, nowDb);
                NxisasApiLog.event(tid(session), NxisasApiLog.FLOW_BANK_SYNC, "ERROR",
                        "stage", "TRANSACTION", "scope", "BANK", "bankCd", bankCd, "errorCode", txFailCode);
                session.completeAccount(false);            // this account processed as a failure
                failSession(session, "WBE2095", scrapeFailureMsg(tOut)); // fail-fast batch
                return false;
            }
            NxisasTransactionJobResult tResult = gateway.firstTransactionResult(tOut);
            entries = tResult == null || tResult.getTransaction() == null
                    ? List.of() : tResult.getTransaction();
        }

        session.enterPhase(NxisasScrapeSession.Phase.PERSISTING_TRANSACTIONS, FRACTION_TX); // 25%
        if (session.isCancelled()) { markCancelledWorker(session); return false; } // before persist

        // ── persist + auto-match (own short tx) ──
        PersistResult pr = persistence.persistTransactions(bizKey, userId, bankCd, acctNo, ccy, entries, nowDb);
        session.setTotalDebitInserted(session.getTotalDebitInserted() + pr.debitCount());
        session.setTotalCreditInserted(session.getTotalCreditInserted() + pr.creditCount());
        session.setTotalDebitAmount(session.getTotalDebitAmount().add(pr.debitTotal()));
        session.setTotalCreditAmount(session.getTotalCreditAmount().add(pr.creditTotal()));
        session.setTotalAutoMatched(session.getTotalAutoMatched() + pr.autoMatchedCount());
        session.foldCurrency(ccy, pr.creditCount(), pr.debitCount(), pr.creditTotal(), pr.debitTotal(),
                pr.autoMatchedCount());
        session.enterPhase(NxisasScrapeSession.Phase.PERSISTING_TRANSACTIONS, FRACTION_PERSIST); // 35%
        if (session.isCancelled()) { markCancelledWorker(session); return false; } // after persist

        // ── BALANCE (gateway; no DB tx) → persist stamps (own short tx) ──
        session.enterPhase(NxisasScrapeSession.Phase.REFRESHING_BALANCE, FRACTION_BALANCE); // 50%
        NxisasApiLog.event(tid(session), NxisasApiLog.FLOW_BANK_SYNC, "BALANCE_START",
                "bankCd", bankCd, "acctNo", NxisasMask.acct(acctNo),
                "fixedDeposit", mode != ScrapeMode.REGULAR);
        Balance bal = fetchBalance(session, acct, cfg, mode != ScrapeMode.REGULAR);
        if (session.isCancelled()) { markCancelledWorker(session); return false; } // after gateway
        persistence.persistBalanceAndStamp(bizKey, userId, bankCd, acctNo, nowDb,
                bal.ok(), bal.code(), bal.msg(), bal.bal(), bal.avail());

        // Per-account success audit row (REQUIRES_NEW).
        syncHistoryWriter.recordAttempt(bizKey, userId, bankCd, acctNo, startDate, session.getToDt(),
                com.kosign.wecms.domain.banking.synchistory.service.SyncHistoryWriter.RSLT_CD_SUCCESS, "");

        NxisasApiLog.event(tid(session), NxisasApiLog.FLOW_BANK_SYNC, "ACCOUNT_DONE",
                "bankCd", bankCd, "acctNo", NxisasMask.acct(acctNo), "txCount", entries.size());
        session.completeAccount(true); // → 100% fraction for this account
        return true;
    }

    /** Parsed BALANCE result (no DB mutation — that's {@code persistBalanceAndStamp}). */
    private record Balance(boolean ok, String code, String msg, BigDecimal bal, BigDecimal avail) { }

    private Balance fetchBalance(NxisasScrapeSession session, AcctMstr acct, NxisasBankConfig.Entry cfg,
                                 boolean fixedDeposit) {
        NxisasBalanceJobInput in = new NxisasBalanceJobInput();
        in.setAcctNum(acct.getAcctNo());
        in.setCurrency(acct.getCcyCd());
        String trSeq = gateway.newTrSeq();
        NxisasApiOut<NxisasBalanceJobResult> out = fixedDeposit
                ? gateway.balanceFixedDeposit(cfg.module(), cfg.category(), trSeq, in, continuationOf(session))
                : gateway.balance(cfg.module(), cfg.category(), trSeq, in, continuationOf(session));

        NxisasOutput<NxisasBalanceJobResult> bankOut = gateway.firstOutput(out);
        String balErr = bankOut == null ? null : bankOut.getErrorCode();
        boolean balanceOk = balErr != null && !balErr.isBlank() && NxisasResultCodes.isBankAdapterSuccess(balErr);
        if (!balanceOk) {
            log.warn("BankTransactionSync BALANCE soft-failed [bank={}, acct={}, code={}]",
                    acct.getBankCd(), acct.getAcctNo(), balErr);
            return new Balance(false, balErr == null || balErr.isBlank() ? "ERROR" : balErr,
                    bankOut == null ? null : bankOut.getErrorMessage(), null, null);
        }
        NxisasBalanceJobResult result = gateway.firstBalanceResult(out);
        List<NxisasBalanceEntry> list = result == null ? null
                : (result.getBalance() != null && !result.getBalance().isEmpty()
                        ? result.getBalance() : result.getAcctList());
        if (list == null || list.isEmpty()) {
            return new Balance(true, OK_CODE, null, null, null);
        }
        NxisasBalanceEntry first = list.get(0);
        return new Balance(true, OK_CODE, null, parseDecimal(first.getBalance()), parseDecimal(first.getAvailableBal()));
    }

    /** Queue drained → terminal COMPLETED. Computes the completion extras once
     *  (on the worker thread) and publishes them for the poll thread. */
    private void finalizeAsync(NxisasScrapeSession session) {
        // Cancel checkpoint immediately before completion — if a cancel arrived
        // after the last account/balance phase (the previously unguarded window),
        // honour it instead of finalizing COMPLETED (audit gap D4).
        if (session.isCancelled()) { markCancelledWorker(session); return; }

        // Compute the COMPLETED extras first, but only PIN progress + mark
        // terminal if the once-only transition is accepted. If a concurrent
        // cancel committed CANCELLED between the check above and the CAS below,
        // tryMarkTerminal returns false and we respect CANCELLED — a worker must
        // never overwrite a committed CANCELLED with COMPLETED (audit D3).
        session.setLastUpdate(OffsetDateTime.now(ZoneOffset.UTC).format(DTM_DISPLAY));
        session.setAutoMatchEnabledFlag(autoMatchService.isAutoMatchEnabled(session.getBizKey()));
        if (!session.tryMarkTerminal(NxisasScrapeSession.Status.COMPLETED)) {
            NxisasApiLog.event(tid(session), NxisasApiLog.FLOW_BANK_SYNC, "COMPLETE_SUPPRESSED",
                    "status", session.getStatus());
            return; // a terminal (cancel) already won — do not pin progress to 100
        }
        session.markProgressComplete();
        log.info("BankTransactionSync completed [bizKey={}, accounts={}, debit={}, credit={}, autoMatched={}]",
                session.getBizKey(), session.getAccountsSynced(),
                session.getTotalDebitInserted(), session.getTotalCreditInserted(), session.getTotalAutoMatched());
        NxisasApiLog.event(tid(session), NxisasApiLog.FLOW_BANK_SYNC, "COMPLETE",
                "success", session.getAccountsSynced(),
                "debit", session.getTotalDebitInserted(), "credit", session.getTotalCreditInserted());
    }

    private void failSession(NxisasScrapeSession session, String code, String msg) {
        session.setErrorCode(code);
        session.setErrorMessage(msg);
        session.markTerminal(NxisasScrapeSession.Status.FAILED);
    }

    private void markCancelledWorker(NxisasScrapeSession session) {
        session.markTerminal(NxisasScrapeSession.Status.CANCELLED);
        NxisasApiLog.event(tid(session), NxisasApiLog.FLOW_BANK_SYNC, "CANCELLED",
                "accountsSynced", session.getAccountsSynced());
    }

    // ─────────────────────────────────────────────────────────────────
    // Snapshot building (status wire shape — NO secrets)
    // ─────────────────────────────────────────────────────────────────

    private SyncStatusResponse buildSnapshot(NxisasScrapeSession session) {
        String wire = wireStatus(session);
        boolean completed = session.getStatus() == NxisasScrapeSession.Status.COMPLETED;

        SyncStatusResponse.Challenge challenge = null;
        if (session.getStatus() == NxisasScrapeSession.Status.AWAITING_CHALLENGE
                && session.getChallengeKind() != null) {
            ScrapeChallengeKind k = session.getChallengeKind();
            challenge = new SyncStatusResponse.Challenge(
                    k.name(),
                    session.getChallengePrompt(),
                    k == ScrapeChallengeKind.QNA ? session.getChallengeQnaQuestions() : null,
                    k == ScrapeChallengeKind.CAPTCHA ? session.getChallengeCaptchaImage() : null,
                    k == ScrapeChallengeKind.CAPTCHA ? captchaImageFormatFor(session.getBankModule()) : null,
                    session.getLoginId());
        }

        List<CurrencySummary> summaries = session.currencySummariesSnapshot();

        List<NxisasScrapeSession.FailedAccountDetail> failedRaw = session.failedAccountDetailsSnapshot();
        List<SyncStatusResponse.FailedAccount> failedDetails = failedRaw.isEmpty() ? null
                : failedRaw.stream()
                        .map(d -> new SyncStatusResponse.FailedAccount(
                                d.bankCd(), d.acctNo(), d.ccyCd(), d.errorCode(), d.errorMessage()))
                        .toList();

        return new SyncStatusResponse(
                session.getSessionId(),
                wire,
                session.getProgress(),
                session.getCurrentPhase() == null ? null : session.getCurrentPhase().name(),
                session.getCompletedAccounts(),
                session.getTotalAccounts(),
                session.getSuccessAccounts(),
                session.getFailedAccounts(),
                summaries,
                failedDetails,
                challenge,
                completed ? session.getAccountsSynced() : null,
                completed ? session.getTotalDebitInserted() : null,
                completed ? session.getTotalCreditInserted() : null,
                completed ? session.getTotalDebitAmount().toPlainString() : null,
                completed ? session.getTotalCreditAmount().toPlainString() : null,
                completed ? session.getLastUpdate() : null,
                completed ? session.getTotalAutoMatched() : null,
                completed ? session.getAutoMatchEnabledFlag() : null,
                session.getErrorCode(),
                session.getErrorMessage());
    }

    private static String wireStatus(NxisasScrapeSession session) {
        return switch (session.getStatus()) {
            case RUNNING -> "RUNNING";
            case COMPLETED -> "COMPLETED";
            case FAILED -> "FAILED";
            case CANCELLED -> "CANCELLED";
            case AWAITING_CHALLENGE -> session.getChallengeKind() == null ? "RUNNING"
                    : switch (session.getChallengeKind()) {
                        case OTP -> "OTP_REQUIRED";
                        case QNA -> "QNA_REQUIRED";
                        case CAPTCHA -> "CAPTCHA_REQUIRED";
                    };
        };
    }

    private static SyncStatusResponse cancelledSnapshot(UUID sessionId) {
        return new SyncStatusResponse(sessionId, "CANCELLED",
                0, null, 0, 0, 0, 0,
                List.of(), null, null,
                null, null, null, null, null, null, null, null,
                null, null);
    }

    // ─────────────────────────────────────────────────────────────────
    // Session lookup + validation
    // ─────────────────────────────────────────────────────────────────

    /** Tenant-validated SYNC session for status polling — returns any state
     *  (incl. terminal/cancelled) so the frontend can read the final snapshot. */
    private NxisasScrapeSession requireOwnedSyncSession(UUID sessionId) {
        String bizKey = requireBizKey();
        String userId = TenantContext.currentUserId();
        NxisasScrapeSession s = registry.find(sessionId, bizKey, userId)
                .orElseThrow(() -> new BusinessException("WBE2060"));
        if (s.getMode() != NxisasScrapeSession.Mode.SYNC) {
            throw new BusinessException("WBE2060");
        }
        s.touch();
        return s;
    }

    /** Tenant-validated SYNC session for a challenge resume — rejects cancelled
     *  / terminal / expired sessions ({@code WBE2060}) and the wrong resume verb
     *  ({@code WBE9001}). */
    private NxisasScrapeSession requireResumableSyncSession(UUID sessionId, ScrapeChallengeKind expected) {
        NxisasScrapeSession s = requireOwnedSyncSession(sessionId);
        if (s.isCancelled() || s.isTerminal()) {
            throw new BusinessException("WBE2060"); // cancelled / completed / failed cannot resume
        }
        if (s.getChallengeKind() != expected) {
            throw new BusinessException("WBE9001");
        }
        return s;
    }

    // ─────────────────────────────────────────────────────────────────
    // Failure-audit helpers (unchanged behaviour)
    // ─────────────────────────────────────────────────────────────────

    private void logFailedAttempt(NxisasScrapeSession session, String rsltCd, String errCont) {
        TransactionsSyncRequest.AccountKey head = session.getAccountsQueue().peekFirst();
        if (head == null) return;
        try {
            syncHistoryWriter.recordAttempt(session.getBizKey(), session.getUserId(),
                    head.bankCd(), head.acctNo(), session.getFromDt(), session.getToDt(),
                    rsltCd == null || rsltCd.isBlank() ? "ERROR" : rsltCd, errCont == null ? "" : errCont);
        } catch (RuntimeException e) {
            log.warn("Failed to persist EXT_TRAN_CALL_HIS row for sync failure", e);
        }
    }

    private void stampScrapeFailure(NxisasScrapeSession session, String code, String msg) {
        TransactionsSyncRequest.AccountKey head = session.getAccountsQueue().peekFirst();
        if (head == null) return;
        String nowDb = OffsetDateTime.now(ZoneOffset.UTC).format(DTM_14);
        try {
            statusUpdater.markFailure(session.getBizKey(), head.bankCd(), head.acctNo(),
                    code, msg, session.getUserId(), nowDb);
        } catch (RuntimeException e) {
            log.warn("Failed to stamp ACCT_MSTR failure status for sync failure", e);
        }
    }

    /** Best-effort bank-adapter LOGOUT on cancel (legacy {@code stopAllNxisas}).
     *  No live continuation → skip. Failures swallowed. Never logs secrets. */
    private void bestEffortLogout(NxisasScrapeSession session) {
        if (!hasLiveContinuation(session)) return;
        try {
            String trSeq = gateway.newTrSeq();
            gateway.logout(session.getBankModule(), session.getBankCategory(), trSeq,
                    new NxisasLogoutJobInput(), continuationOf(session));
            NxisasApiLog.event(tid(session), NxisasApiLog.FLOW_BANK_SYNC, "LOGOUT_SENT",
                    "bankModule", session.getBankModule());
        } catch (RuntimeException e) {
            log.warn("BankTransactionSync cancel LOGOUT best-effort failed [session={}]",
                    session.getSessionId(), e);
        }
    }

    private String scrapeFailureCode(NxisasApiOut<?> out) {
        NxisasOutput<?> bankOut = gateway.firstOutput(out);
        if (bankOut != null && !NxisasResultCodes.isBankAdapterSuccess(bankOut.getErrorCode())) {
            return bankOut.getErrorCode();
        }
        if (gatewayRejected(out)) {
            return out.getCommon().getResultCd();
        }
        return null;
    }

    private String scrapeFailureMsg(NxisasApiOut<?> out) {
        NxisasOutput<?> bankOut = gateway.firstOutput(out);
        if (bankOut != null) {
            String msg = bankOut.getErrorMessage();
            if (msg == null || msg.isBlank()) msg = bankOut.getDetailMessage();
            if (msg != null && !msg.isBlank()) return msg;
        }
        if (out != null && out.getCommon() != null) {
            return out.getCommon().getResultMsg();
        }
        return null;
    }

    // ─────────────────────────────────────────────────────────────────
    // Session + gateway helpers (unchanged)
    // ─────────────────────────────────────────────────────────────────

    private NxisasScrapeSession newSyncSession(Deque<TransactionsSyncRequest.AccountKey> queue,
                                               String fromDt, String toDt, boolean autoWindow) {
        String bizKey = requireBizKey();
        String userId = TenantContext.currentUserId();
        String trSeq = gateway.newTrSeq();
        NxisasScrapeSession session = new NxisasScrapeSession(trSeq, bizKey, userId, null, "", null);
        session.setMode(NxisasScrapeSession.Mode.SYNC);
        session.setAccountsQueue(queue);
        session.setFromDt(fromDt);
        session.setToDt(toDt);
        session.setAutoWindow(autoWindow);
        return session;
    }

    /**
     * Login-group key — {@code BANK_CD} + the account's own
     * {@code ACCT_MSTR.BANK_CERT_SEQ}.
     *
     * <p><b>Why the cert seq is part of the key.</b> A tenant can register
     * several credentials at one bank: different sub-types (Vietcom
     * {@code 11704004} has {@code NXISAS_GB_SEQ_NO} 0 = "VCB iB@nking" and
     * 1 = "VCB DigiBiz", per {@code BANK_INFM_DETL.BANK_DETL_INFO_CD='25'})
     * and/or different bank-side user levels (preparer vs approver logins,
     * which legacy stores as separate {@code BANK_CERT_INFO} rows because the
     * find-or-create key includes {@code CERT_INFO_CON} —
     * {@code fnsh_0101_001_c001_act.jsp:168}). Their login IDs and passwords
     * are <b>not</b> interchangeable.
     *
     * <p>Legacy groups the Account Inquiry real-time inquiry by
     * {@code BANK_CERT_SEQ} — one engine login per <i>credential</i>, not per
     * bank ({@code nxisas_tranList_osmc.js:265-279}). Keying by bank alone
     * merged two credentials into one login group, so accounts bound to the
     * second credential were scraped with the first one's ID and password.
     */
    private static String loginGroupKey(String bankCd, Long bankCertSeq) {
        return bankCd + "#" + (bankCertSeq == null ? "" : bankCertSeq);
    }

    private static String loginGroupKey(AcctMstr acct) {
        return loginGroupKey(acct.getBankCd(), acct.getBankCertSeq());
    }

    /** {@link #loginGroupKey(AcctMstr)} for a wire {@code AccountKey}, which
     *  carries only {@code (BANK_CD, ACCT_NO)} — the binding is read back from
     *  {@code ACCT_MSTR}. An account that vanished mid-sync degrades to the
     *  unbound key rather than throwing on the login-success path. */
    private String loginGroupKey(NxisasScrapeSession session,
                                 TransactionsSyncRequest.AccountKey key) {
        return acctRepo.findByBizKeyAndBankCdAndAcctNo(
                        session.getBizKey(), key.bankCd(), key.acctNo())
                .map(BankTransactionSyncService::loginGroupKey)
                .orElseGet(() -> loginGroupKey(key.bankCd(), null));
    }

    /**
     * Resolve the credential this account is actually registered under —
     * {@code ACCT_MSTR.BANK_CERT_SEQ → BANK_CERT_INFO.SEQ_NO}, the same pointer
     * legacy reads via {@code _v2nxisas.getCertInfo(bank_cert_seq)} and that
     * {@code fnsh_0101_001_c001_act.jsp:257,321} stamps at Add-and-Save.
     *
     * <p>Falls back to the bank-level lookup only when the account carries no
     * binding at all ({@code BANK_CERT_SEQ} null — an account unlinked by a
     * credential delete, or a pre-refactor row). That fallback is a guess when
     * the bank has more than one credential, so it is logged.
     *
     * <p>A binding that points at another bank's credential is refused rather
     * than followed: sending bank A's login to bank B is a guaranteed auth
     * failure that feeds the bank's 3-strike lockout. Same stance as
     * {@code OwnAccountTransferService.resolveCertForAccount}.
     */
    private DecryptedCredential resolveCredentialForAccount(AcctMstr acct) {
        final Long seq = acct.getBankCertSeq();
        if (seq == null) {
            log.warn("bank-sync :: account {}/{} has no BANK_CERT_SEQ — falling back to the "
                            + "bank-level credential lookup (ambiguous when the bank has more "
                            + "than one registered login; re-register the account via "
                            + "Settings > Login Credentials to bind it)",
                    acct.getBankCd(), NxisasMask.acct(acct.getAcctNo()));
            return credentialService.loadForScrapingByBank(acct.getBankCd());
        }
        DecryptedCredential cred = credentialService.loadForScraping(BigDecimal.valueOf(seq));
        if (cred == null) {
            // loadForScraping normally throws WBE2050 itself; this covers a dangling
            // BANK_CERT_SEQ surfacing as a null projection rather than an exception.
            log.warn("bank-sync :: account {}/{} points at BANK_CERT_SEQ={} which resolved to no "
                            + "credential — refusing to guess one",
                    acct.getBankCd(), NxisasMask.acct(acct.getAcctNo()), seq);
            throw new BusinessException("WBE2050");
        }
        if (cred.bankCd() != null && !cred.bankCd().equals(acct.getBankCd())) {
            log.warn("bank-sync :: account {}/{} points at BANK_CERT_SEQ={} owned by bank {} — "
                            + "refusing to scrape with another bank's credential",
                    acct.getBankCd(), NxisasMask.acct(acct.getAcctNo()), seq, cred.bankCd());
            throw new BusinessException("WBE2050");
        }
        return cred;
    }

    private static String perAccountStartDt(AcctMstr acct) {
        String marker = firstNonBlank(acct.getAcctHisLstInqDt(), acct.getAcctBalLstInqDtm(), acct.getLstLgnSucDtm());
        if (marker != null && marker.length() >= 8) {
            return marker.substring(0, 8);
        }
        return OffsetDateTime.now(ZoneOffset.UTC).minusMonths(3).format(DT_8);
    }

    private static String firstNonBlank(String... vals) {
        for (String v : vals) {
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }

    private static String tid(NxisasScrapeSession session) {
        return session.getSessionId().toString();
    }

    private static boolean hasLiveContinuation(NxisasScrapeSession s) {
        return s.getSasSessionID() != null && !s.getSasSessionID().isBlank();
    }

    private static GatewayContinuation continuationOf(NxisasScrapeSession s) {
        return new GatewayContinuation(s.getSasSessionID(), s.getGwSysNo(), s.getLbsNo(), s.getSsSysNo(), s.getThreadNo());
    }

    private static void storeContinuation(NxisasScrapeSession s, GatewayContinuation c) {
        s.setSasSessionID(c.sasSessionID());
        s.setGwSysNo(c.gwSysNo());
        s.setLbsNo(c.lbsNo());
        s.setSsSysNo(c.ssSysNo());
        s.setThreadNo(c.threadNo());
    }

    private static void setBankModuleViaReflection(NxisasScrapeSession session, String module) {
        try {
            java.lang.reflect.Field f = NxisasScrapeSession.class.getDeclaredField("bankModule");
            f.setAccessible(true);
            f.set(session, module);
        } catch (ReflectiveOperationException e) {
            throw new BusinessException("WBE9999");
        }
    }

    private static boolean gatewayRejected(NxisasApiOut<?> out) {
        if (out == null || out.getCommon() == null) return false;
        return NxisasResultCodes.isGatewayError(out.getCommon().getResultCd());
    }

    private static ScrapeChallengeKind challengeKindFrom(NxisasLoginJobResult result) {
        if (result == null || result.getCallBackFunc() == null) return null;
        String f = result.getCallBackFunc().toLowerCase();
        if (f.contains("otp")) return ScrapeChallengeKind.OTP;
        if (f.contains("captcha")) return ScrapeChallengeKind.CAPTCHA;
        if (f.contains("qna")) return ScrapeChallengeKind.QNA;
        return null;
    }

    private static List<QnaQuestion> qnaQuestionsFrom(NxisasLoginJobResult result) {
        if (result == null || result.getReq() == null || !result.getReq().isObject()) {
            return List.of();
        }
        JsonNode inner = result.getReq().get("req");
        if (inner == null || !inner.isArray()) return List.of();
        List<QnaQuestion> outList = new ArrayList<>(inner.size());
        for (JsonNode n : inner) {
            String hidden = n.path("HiddenValue").asText("");
            String question = n.path("Question").asText("");
            if (hidden.isBlank() && question.isBlank()) continue;
            outList.add(new QnaQuestion(hidden, question));
        }
        return outList;
    }

    private static String captchaImageFrom(NxisasLoginJobResult result) {
        if (result == null || result.getReq() == null) return null;
        JsonNode req = result.getReq();
        if (req.isObject()) {
            JsonNode inner = req.get("req");
            if (inner != null && inner.isTextual()) return inner.asText();
        }
        if (req.isTextual()) return req.asText();
        return null;
    }

    private static String captchaImageFormatFor(String bankModuleOrCd) {
        if (bankModuleOrCd == null) return "png";
        if (bankModuleOrCd.contains("GDT") || bankModuleOrCd.contains("VIETNAM_GDT")) return "svg+xml";
        return "png";
    }

    @SuppressWarnings("unchecked")
    private static <T> List<com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasResult<T>>
            otpToLoginResults(NxisasApiOut<NxisasOTPJobResult> in) {
        List<?> raw = in.getResultList();
        return (List<com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasResult<T>>)
                (raw == null ? List.<Object>of() : raw);
    }

    private static String requireBizKey() {
        String bizKey = TenantContext.currentBizKey();
        if (bizKey == null || bizKey.isBlank()) {
            throw new BusinessException("WBE9999");
        }
        return bizKey;
    }

    private static BigDecimal parseDecimal(String raw) {
        if (raw == null) return null;
        String t = raw.trim().replace(",", "");
        if (t.isEmpty()) return null;
        try {
            return new BigDecimal(t);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
