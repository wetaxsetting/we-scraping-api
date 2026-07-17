package com.kosign.wecms.domain.payment.cardtransactions.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.kosign.wecms.common.auditlog.service.AuditLogContext;
import com.kosign.wecms.common.auditlog.service.AuditLogService;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.banking.bankcredentials.service.BankCredentialService;
import com.kosign.wecms.domain.banking.bankcredentials.service.BankCredentialService.DecryptedCredential;
import com.kosign.wecms.domain.banking.nxisas.BankSyncExecutorConfig;
import com.kosign.wecms.domain.banking.nxisas.NxisasApiLog;
import com.kosign.wecms.domain.banking.nxisas.NxisasBankConfig;
import com.kosign.wecms.domain.banking.nxisas.NxisasGatewayClient;
import com.kosign.wecms.domain.banking.nxisas.NxisasGatewayClient.GatewayContinuation;
import com.kosign.wecms.domain.banking.nxisas.NxisasMask;
import com.kosign.wecms.domain.banking.nxisas.NxisasResultCodes;
import com.kosign.wecms.domain.banking.nxisas.dto.QnaQuestion;
import com.kosign.wecms.domain.banking.nxisas.dto.SubmitChallengeRequest;
import com.kosign.wecms.domain.banking.nxisas.dto.SubmitQnaRequest;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasApiOut;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasCaptchaJobInput;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasCardUsingEntry;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasCardUsingJobInput;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasCardUsingJobResult;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasLoginJobInput;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasLoginJobResult;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasOTPJobInput;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasOTPJobResult;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasOutput;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasQnaJobInput;
import com.kosign.wecms.domain.banking.nxisas.session.NxisasScrapeSession;
import com.kosign.wecms.domain.banking.nxisas.session.NxisasScrapeSession.CardScrapeKey;
import com.kosign.wecms.domain.banking.nxisas.session.NxisasSessionRegistry;
import com.kosign.wecms.domain.banking.nxisas.session.ScrapeChallengeKind;
import com.kosign.wecms.domain.payment.cardtransactions.dto.CardSyncStatusResponse;
import com.kosign.wecms.domain.payment.cardtransactions.dto.SyncCardTransactionsRequest;
import com.kosign.wecms.domain.payment.cardtransactions.entity.CardMstr;
import com.kosign.wecms.domain.payment.cardtransactions.repository.CardMstrRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * Card Transactions &gt; Update orchestrator (Phase 2 — asynchronous,
 * re-entrant). The card twin of {@code BankTransactionSyncService}: it drives
 * the per-card {@code LOGIN → (QNA/OTP/CAPTCHA?) → CARD_USING} chain over the
 * <b>same</b> {@link NxisasScrapeSession} / {@link NxisasSessionRegistry} /
 * {@link NxisasGatewayClient} infrastructure and reuses the bank scrape's
 * bounded {@code bankSyncExecutor}.
 *
 * <p>The initial POST validates + registers a tenant-scoped session, schedules
 * a background scrape <b>segment</b>, and returns a RUNNING snapshot
 * <b>immediately</b> (the scrape does not run on the request thread). The
 * frontend then polls {@link #status}. Progress is driven by real backend
 * stages (0 → 50 → 100 per card) via the shared session's unit-agnostic
 * progress machinery ({@code totalAccounts} = total cards, one
 * {@code completeAccount} per card), and never moves backward.
 *
 * <p><b>Re-entrant state machine (no parked threads).</b> A worker segment runs
 * only until it reaches a boundary: COMPLETED, FAILED, CANCELLED, or a bank
 * challenge. On a challenge it persists the gateway continuation + challenge
 * metadata on the session, sets the matching {@code AWAITING_CHALLENGE} state,
 * and <b>returns</b> — releasing the worker thread and holding no DB
 * connection. The resume endpoint transitions the session back to RUNNING and
 * schedules a fresh segment that continues from the saved continuation,
 * preserving progress + card counts.
 *
 * <p><b>Transaction boundaries.</b> Coocon gateway calls run outside any DB
 * transaction; each card's persist is a short {@code @Transactional} call on
 * {@link CardTransactionScrapePersistence}.
 *
 * <p><b>Cancellation.</b> {@link #cancel} (universal from the moment the
 * session exists) sets a cooperative flag and marks CANCELLED; the worker
 * checks the flag at every boundary and stops before the next card/phase. The
 * reused card flow has no LOGOUT job (unlike the bank sync), so cancel does not
 * send one — the gateway session times out on its own.
 *
 * <p><b>Credential selection (audit requirement #6):</b> a card is scraped
 * under exactly the credential its {@code CARD_MSTR.BANK_CERT_SEQ} points at.
 * There is NO lowest-{@code SEQ_NO} fallback — a card whose
 * {@code BANK_CERT_SEQ} is null is excluded; if no card has a credential the
 * start returns {@code WBE_CARD_SYNC_NO_CREDENTIAL}.
 */
@Slf4j
@Service
public class CardTransactionScrapeService {

    private static final String WSVC_ID           = "card-transactions";
    private static final String REF_NO            = "CARD_TRNS_HSTR";
    private static final String AUDIT_ACTIVITY    = "audit.card_transactions.activity";
    private static final String AUDIT_SYNC_DETAIL = "audit.card_transactions.sync.detail";
    private static final String FLOW              = NxisasApiLog.FLOW_CARD_UPDATE_TRANSACTIONS;

    /** Woori Vietnam sends CARD_USING without a {@code cardType}
     *  sub-discriminator; every other bank sends {@code "0"}
     *  (nxisas_cardTranList.ts:198-211). */
    private static final String WOORI_VN_BANK_CD = "11704020";

    private static final DateTimeFormatter DTM_14 = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final DateTimeFormatter DT_8   = DateTimeFormatter.ofPattern("yyyyMMdd");

    // Per-card progress fractions (phaseFraction in the overall formula).
    private static final double FRACTION_START   = 0.00; // 0%  — card processing begins
    private static final double FRACTION_PERSIST = 0.50; // 50% — CARD_USING scraped, persisting

    private final CardMstrRepository                cardMstrRepo;
    private final BankCredentialService             credentialService;
    private final NxisasGatewayClient               gateway;
    private final NxisasBankConfig                  bankConfig;
    private final NxisasSessionRegistry             registry;
    private final CardTransactionScrapePersistence  persistence;
    private final AuditLogService                   auditLogService;
    /** Card-feature bank display-name resolver (short name) for the challenge
     *  dialog header — {@code "Shinhan VN"} rather than {@code "Shinhan(Vietnam)"}. */
    private final CardBankNameResolver              bankNameResolver;
    private final Executor                          executor;

    public CardTransactionScrapeService(
            CardMstrRepository cardMstrRepo,
            BankCredentialService credentialService,
            NxisasGatewayClient gateway,
            NxisasBankConfig bankConfig,
            NxisasSessionRegistry registry,
            CardTransactionScrapePersistence persistence,
            AuditLogService auditLogService,
            CardBankNameResolver bankNameResolver,
            @Qualifier(BankSyncExecutorConfig.EXECUTOR) Executor executor) {
        this.cardMstrRepo = cardMstrRepo;
        this.credentialService = credentialService;
        this.gateway = gateway;
        this.bankConfig = bankConfig;
        this.registry = registry;
        this.persistence = persistence;
        this.auditLogService = auditLogService;
        this.bankNameResolver = bankNameResolver;
        this.executor = executor;
    }

    // ─────────────────────────────────────────────────────────────────
    // Public entry points (CardTransactionsController)
    // ─────────────────────────────────────────────────────────────────

    /**
     * Start a new Card Update scrape. Validates + registers the session,
     * schedules the background scrape once, and returns the initial RUNNING
     * snapshot immediately (never runs the scrape on the request thread).
     */
    public CardSyncStatusResponse start(SyncCardTransactionsRequest req) {
        if (req == null) throw new BusinessException("WBE_VALIDATION");
        String bizKey = requireBizKey();
        String userId = TenantContext.currentUserId();
        String now    = OffsetDateTime.now(ZoneOffset.UTC).format(DTM_14);

        List<CardMstr> cards = cardMstrRepo.findActiveCardsForSync(bizKey);
        if (cards.isEmpty()) {
            // Nothing to scrape — terminal COMPLETED with zero counts. No
            // session is registered (the frontend never needs to poll).
            return CardSyncStatusResponse.completed(null, 0, 0, 0);
        }

        // Date window. Auto → from = min(TRN_LST_SUC_DTM[0:8]) across the
        // eligible cards (or today − 90d when none ever synced); to = today.
        // Manual → user range, validated start ≤ end.
        String fromDt;
        String toDt;
        if (Boolean.TRUE.equals(req.auto())) {
            String today = now.substring(0, 8);
            String minLastSuc = cards.stream()
                    .map(CardMstr::getTrnLstSucDtm)
                    .filter(s -> s != null && s.length() >= 8)
                    .map(s -> s.substring(0, 8))
                    .min(String::compareTo)
                    .orElse(null);
            fromDt = minLastSuc != null ? minLastSuc : minus90Days(today);
            toDt   = today;
        } else {
            fromDt = req.fromDate();
            toDt   = req.toDate();
            if (fromDt == null || toDt == null || fromDt.compareTo(toDt) > 0) {
                throw new BusinessException("WBE_CARD_SYNC_BAD_RANGE");
            }
        }

        // Credential lookup — CARD_MSTR.BANK_CERT_SEQ ONLY (no fallback). A
        // card with a null / invalid BANK_CERT_SEQ fails fast (re-register).
        List<CardScrapeKey> eligible = new ArrayList<>(cards.size());
        for (CardMstr c : cards) {
            if (c.getBankCertSeq() == null) {
                log.warn("card-update :: card has no BANK_CERT_SEQ — re-register required bankCd={} cardNo={}",
                        c.getBankCd(), NxisasMask.card(c.getCardNo()));
                throw new BusinessException("WBE_CARD_SYNC_NO_CREDENTIAL");
            }
            String cardType = WOORI_VN_BANK_CD.equals(c.getBankCd()) ? null : "0";
            eligible.add(new CardScrapeKey(c.getBankCd(), c.getCardNo(), cardType, c.getBankCertSeq()));
        }
        for (var seq : eligible.stream().map(CardScrapeKey::bankCertSeq).distinct().toList()) {
            try {
                credentialService.loadForScraping(seq);
            } catch (RuntimeException ex) {
                log.warn("card-update :: BANK_CERT_SEQ {} does not resolve to a credential — re-register required", seq);
                throw new BusinessException("WBE_CARD_SYNC_NO_CREDENTIAL");
            }
        }

        // Order so cards sharing a credential are contiguous (login-once-per
        // credential).
        LinkedHashMap<String, List<CardScrapeKey>> grouped = new LinkedHashMap<>();
        for (CardScrapeKey k : eligible) {
            grouped.computeIfAbsent(loginGroupKey(k), g -> new ArrayList<>()).add(k);
        }
        Deque<CardScrapeKey> queue = new ArrayDeque<>(eligible.size());
        for (List<CardScrapeKey> group : grouped.values()) queue.addAll(group);

        NxisasScrapeSession session = new NxisasScrapeSession(
                gateway.newTrSeq(), bizKey, userId, null, "", null);
        session.setMode(NxisasScrapeSession.Mode.CARD_SYNC);
        session.setCardsQueue(queue);
        session.setFromDt(fromDt);
        session.setToDt(toDt);
        session.setTotalAccounts(eligible.size()); // shared unit counter = cards
        session.setStatus(NxisasScrapeSession.Status.RUNNING);
        registry.register(session);

        NxisasApiLog.event(tid(session), FLOW, "START",
                "cards", eligible.size(), "groups", grouped.size(),
                "from", fromDt, "to", toDt);

        CardSyncStatusResponse initial = buildSnapshot(session);
        scheduleWorker(session);
        return initial;
    }

    /** Read-only status snapshot for polling. Tenant-validated; no side effects. */
    public CardSyncStatusResponse status(UUID sessionId) {
        return buildSnapshot(requireOwnedCardSession(sessionId));
    }

    /** Resume after a QNA (security-question) answer — schedules a fresh segment. */
    public CardSyncStatusResponse submitQna(UUID sessionId, SubmitQnaRequest req) {
        NxisasScrapeSession session = requireResumableCardSession(sessionId, ScrapeChallengeKind.QNA);
        if (session.isWorkerRunning()) return buildSnapshot(session); // duplicate resume — idempotent
        session.setPendingQna(new NxisasScrapeSession.PendingQna(
                req.question(), req.hiddenValue(), req.answer()));
        return resume(session, "QNA");
    }

    /** Resume after an OTP submission. */
    public CardSyncStatusResponse submitOtp(UUID sessionId, SubmitChallengeRequest req) {
        NxisasScrapeSession session = requireResumableCardSession(sessionId, ScrapeChallengeKind.OTP);
        if (session.isWorkerRunning()) return buildSnapshot(session);
        session.setPendingOtp(req.value());
        return resume(session, "OTP");
    }

    /** Resume after a CAPTCHA submission. */
    public CardSyncStatusResponse submitCaptcha(UUID sessionId, SubmitChallengeRequest req) {
        NxisasScrapeSession session = requireResumableCardSession(sessionId, ScrapeChallengeKind.CAPTCHA);
        if (session.isWorkerRunning()) return buildSnapshot(session);
        session.setPendingCaptcha(req.value());
        return resume(session, "CAPTCHA");
    }

    private CardSyncStatusResponse resume(NxisasScrapeSession session, String kind) {
        session.setStatus(NxisasScrapeSession.Status.RUNNING);
        NxisasApiLog.event(tid(session), FLOW, "CHALLENGE_SUBMIT", "type", kind);
        scheduleWorker(session);
        return buildSnapshot(session);
    }

    /**
     * Cancel a Card Update scrape (universal — works from the moment the
     * session exists, during RUNNING, any challenge wait, or resumed
     * processing). Idempotent + tenant-validated. Sets the cooperative flag +
     * marks CANCELLED; the session is <b>retained</b> so the frontend's final
     * poll can read the CANCELLED snapshot (the registry evicts it after the
     * retention window). The card flow has no LOGOUT job, so cancel sends none.
     */
    public CardSyncStatusResponse cancel(UUID sessionId) {
        String bizKey = requireBizKey();
        String userId = TenantContext.currentUserId();
        Optional<NxisasScrapeSession> found = registry.find(sessionId, bizKey, userId);
        if (found.isEmpty()) {
            return cancelledSnapshot(sessionId); // idempotent — nothing to cancel
        }
        NxisasScrapeSession session = found.get();
        if (session.getMode() != NxisasScrapeSession.Mode.CARD_SYNC) {
            return cancelledSnapshot(sessionId); // not ours — no-op
        }
        if (session.isTerminal()) {
            NxisasApiLog.event(tid(session), FLOW, "CANCEL_NOOP_TERMINAL", "status", session.getStatus());
            return buildSnapshot(session); // terminal wins — never overwrite COMPLETED/FAILED
        }
        session.touch();
        session.requestCancel();
        NxisasApiLog.event(tid(session), FLOW, "CANCEL_REQUESTED",
                "status", session.getStatus(), "cardsSynced", session.getSuccessAccounts());
        if (!session.tryMarkTerminal(NxisasScrapeSession.Status.CANCELLED)) {
            NxisasApiLog.event(tid(session), FLOW, "CANCEL_LOST_RACE", "status", session.getStatus());
        }
        return buildSnapshot(session);
    }

    // ─────────────────────────────────────────────────────────────────
    // Background worker (bankSyncExecutor)
    // ─────────────────────────────────────────────────────────────────

    /** Schedule one worker segment. The {@code workerRunning} CAS guard makes
     *  this idempotent — a second call while a segment runs is a no-op. */
    private void scheduleWorker(NxisasScrapeSession session) {
        if (!session.tryStartWorker()) {
            return; // a segment is already running/queued for this session
        }
        final TenantContext.Principal principal = TenantContext.current();
        try {
            executor.execute(() -> runSegment(session, principal));
        } catch (RejectedExecutionException rex) {
            session.finishWorker();
            session.setErrorCode("WBE9999");
            session.setErrorMessage(null);
            session.markTerminal(NxisasScrapeSession.Status.FAILED);
            log.warn("card-update executor saturated — session FAILED [session={}]", session.getSessionId());
        }
    }

    /** One background scrape segment. Re-establishes the tenant context (the
     *  worker runs off the request thread), runs until a boundary, and always
     *  releases the worker guard + restores the prior tenant context. */
    private void runSegment(NxisasScrapeSession session, TenantContext.Principal principal) {
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
            log.error("card-update worker crashed [session={}]", session.getSessionId(), e);
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

    /** Drive the card queue forward within one segment. First resumes a pending
     *  challenge answer (if any), then processes cards until the queue drains
     *  (→ finalize / COMPLETED) or a boundary is hit (challenge / error / cancel). */
    private void advanceAsync(NxisasScrapeSession session) {
        if (session.hasPendingResume()) {
            LoginOutcome o = doPendingResume(session);
            if (o != LoginOutcome.CLEAN) return; // CHALLENGE / ERROR / CANCELLED boundary
        }

        while (!session.getCardsQueue().isEmpty()) {
            if (session.isCancelled()) { markCancelledWorker(session); return; }

            CardScrapeKey key = session.getCardsQueue().peekFirst();

            // Login-once-per-credential reuse.
            if (loginGroupKey(key).equals(session.getActiveLoginGroupKey()) && hasLiveContinuation(session)) {
                NxisasApiLog.event(tid(session), FLOW, "SESSION_REUSE",
                        "bankCd", key.bankCd(), "cardNo", NxisasMask.card(key.cardNo()));
                if (!processOneCardAsync(session, key)) return; // stopped (cancel)
                session.getCardsQueue().pollFirst();
                continue;
            }

            // New credential group → LOGIN.
            DecryptedCredential cred = credentialService.loadForScraping(key.bankCertSeq());
            session.setLoginId(cred.username());
            Integer subSeq = cred.nxisasGbSeqNo() == null ? null : cred.nxisasGbSeqNo().intValue();
            NxisasBankConfig.Entry cfg = bankConfig.find(key.bankCd(), subSeq)
                    .orElseThrow(() -> new BusinessException("WBE2093", key.bankCd()));
            session.setBankCategory(cfg.category());
            session.setNxisasGbSeqNo(subSeq == null ? 0 : subSeq);
            setBankModuleViaReflection(session, cfg.module());

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
            NxisasApiLog.event(tid(session), FLOW, "LOGIN_START",
                    "bankCd", key.bankCd(), "module", cfg.module(), "class", cfg.category());
            NxisasApiOut<NxisasLoginJobResult> out =
                    gateway.login(cfg.module(), cfg.category(), gateway.newTrSeq(), in);
            storeContinuation(session, GatewayContinuation.from(out));
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

    /** Classify a LOGIN-shaped response: bank/gateway error → FAILED terminal;
     *  challenge → AWAITING_CHALLENGE (retained for polling + resume); clean →
     *  stamp the active login group and return CLEAN. */
    private LoginOutcome handleLoginResult(NxisasScrapeSession session,
                                           NxisasApiOut<NxisasLoginJobResult> out, String stage) {
        NxisasOutput<?> bankOut = gateway.firstOutput(out);
        if (bankOut != null && !NxisasResultCodes.isBankAdapterSuccess(bankOut.getErrorCode())) {
            String code = bankOut.getErrorCode();
            String msg  = firstNonBlank(bankOut.getErrorMessage(), bankOut.getDetailMessage());
            NxisasApiLog.event(tid(session), FLOW, "ERROR", "stage", stage, "scope", "BANK", "errorCode", code);
            failSession(session, code, msg);
            return LoginOutcome.ERROR;
        }
        if (gatewayRejected(out)) {
            String code = out.getCommon().getResultCd();
            String msg  = out.getCommon().getResultMsg();
            NxisasApiLog.event(tid(session), FLOW, "ERROR", "stage", stage, "scope", "GATEWAY", "resultCode", code);
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
            NxisasApiLog.event(tid(session), FLOW, "CHALLENGE_REQUIRED", "type", kind);
            return LoginOutcome.CHALLENGE;
        }

        CardScrapeKey head = session.getCardsQueue().peekFirst();
        if (head != null) session.setActiveLoginGroupKey(loginGroupKey(head));
        NxisasApiLog.event(tid(session), FLOW, "LOGIN_SUCCESS",
                "stage", stage, "sasSessionId", NxisasMask.token(session.getSasSessionID()));
        return LoginOutcome.CLEAN;
    }

    /**
     * CARD_USING + persist for one card, updating real progress (0 → 50 → 100).
     * Fail-soft: a CARD_USING-level gateway/bank error logs + records the card
     * as processed-with-failure (counter advances) rather than aborting the
     * batch — mirrors the legacy per-card error collection in
     * {@code fnsh_0001_001_c001_act.jsp}. Returns {@code true} to continue the
     * loop, {@code false} when the segment stopped at a cancel boundary.
     */
    private boolean processOneCardAsync(NxisasScrapeSession session, CardScrapeKey key) {
        String bizKey = session.getBizKey();
        String userId = session.getUserId();
        String now    = OffsetDateTime.now(ZoneOffset.UTC).format(DTM_14);

        if (session.isCancelled()) { markCancelledWorker(session); return false; }
        session.enterPhase(NxisasScrapeSession.Phase.SCRAPING_TRANSACTIONS, FRACTION_START); // 0%

        NxisasBankConfig.Entry cfg = bankConfig.find(key.bankCd(), session.getNxisasGbSeqNo())
                .orElseThrow(() -> new BusinessException("WBE2093", key.bankCd()));

        NxisasCardUsingJobInput in = new NxisasCardUsingJobInput();
        in.setCardNum(key.cardNo());
        in.setStartDate(session.getFromDt());
        in.setEndDate(session.getToDt());
        if (key.cardType() != null) in.setCardType(key.cardType());

        NxisasApiLog.event(tid(session), FLOW, "CARD_USING_START",
                "bankCd", key.bankCd(), "cardNo", NxisasMask.card(key.cardNo()),
                "from", session.getFromDt(), "to", session.getToDt());
        NxisasApiOut<NxisasCardUsingJobResult> out = gateway.cardUsing(
                cfg.module(), cfg.category(), gateway.newTrSeq(), in, continuationOf(session));
        if (session.isCancelled()) { markCancelledWorker(session); return false; } // after gateway

        NxisasOutput<?> bankOut = gateway.firstOutput(out);
        boolean bankOk = bankOut == null || NxisasResultCodes.isBankAdapterSuccess(bankOut.getErrorCode());
        if (!bankOk || gatewayRejected(out)) {
            String code = !bankOk ? bankOut.getErrorCode() : out.getCommon().getResultCd();
            NxisasApiLog.event(tid(session), FLOW, "ERROR",
                    "stage", "CARD_USING", "scope", bankOk ? "GATEWAY" : "BANK",
                    "bankCd", key.bankCd(), "cardNo", NxisasMask.card(key.cardNo()), "code", code);
            log.warn("card-update :: CARD_USING failed bankCd={} cardNo={} code={} — skipping card",
                    key.bankCd(), NxisasMask.card(key.cardNo()), code);
            session.completeAccount(false); // processed with failure — counter advances
            return true;
        }

        NxisasCardUsingJobResult res = gateway.firstCardUsingResult(out);
        List<NxisasCardUsingEntry> scraped;
        if (res == null || res.getCardArr() == null || res.getCardArr().isEmpty()
                || res.getCardArr().get(0).getCardUsing() == null) {
            scraped = List.of();
        } else {
            scraped = res.getCardArr().get(0).getCardUsing();
        }

        session.enterPhase(NxisasScrapeSession.Phase.PERSISTING_TRANSACTIONS, FRACTION_PERSIST); // 50%
        if (session.isCancelled()) { markCancelledWorker(session); return false; } // before persist

        CardMstr card = new CardMstr();
        card.setBizKey(bizKey);
        card.setBankCd(key.bankCd());
        card.setCardNo(key.cardNo());

        int[] counts = persistence.persistCard(bizKey, userId, now, card, scraped);
        session.setCardRowsInserted(session.getCardRowsInserted() + counts[0]);
        session.setCardRowsSkipped(session.getCardRowsSkipped() + counts[1]);

        NxisasApiLog.event(tid(session), FLOW, "CARD_DONE",
                "bankCd", key.bankCd(), "cardNo", NxisasMask.card(key.cardNo()),
                "scraped", scraped.size(), "inserted", counts[0], "skipped", counts[1]);
        session.completeAccount(true); // → 100% fraction for this card + successCards++
        return true;
    }

    /** Queue drained → terminal COMPLETED (retained for the final poll). */
    private void finalizeAsync(NxisasScrapeSession session) {
        if (session.isCancelled()) { markCancelledWorker(session); return; }
        if (!session.tryMarkTerminal(NxisasScrapeSession.Status.COMPLETED)) {
            NxisasApiLog.event(tid(session), FLOW, "COMPLETE_SUPPRESSED", "status", session.getStatus());
            return; // a cancel already won — do not pin progress to 100
        }
        session.markProgressComplete();
        int cardsSynced = session.getSuccessAccounts();
        auditLogService.append(AuditLogContext.builder()
                .wsvcId(WSVC_ID)
                .refNo(REF_NO)
                .activityKey(AUDIT_ACTIVITY)
                .detailKey(AUDIT_SYNC_DETAIL)
                .msgArgs(new Object[] {
                        cardsSynced, cardsSynced,
                        session.getCardRowsInserted(), session.getCardRowsSkipped(),
                })
                .build());
        NxisasApiLog.event(tid(session), FLOW, "COMPLETE",
                "cards", cardsSynced,
                "inserted", session.getCardRowsInserted(),
                "skipped", session.getCardRowsSkipped());
    }

    private void failSession(NxisasScrapeSession session, String code, String msg) {
        session.setErrorCode(code);
        session.setErrorMessage(msg);
        session.markTerminal(NxisasScrapeSession.Status.FAILED);
    }

    private void markCancelledWorker(NxisasScrapeSession session) {
        session.markTerminal(NxisasScrapeSession.Status.CANCELLED);
        NxisasApiLog.event(tid(session), FLOW, "CANCELLED", "cardsSynced", session.getSuccessAccounts());
    }

    // ─────────────────────────────────────────────────────────────────
    // Snapshot building (status wire shape — NO secrets)
    // ─────────────────────────────────────────────────────────────────

    private CardSyncStatusResponse buildSnapshot(NxisasScrapeSession session) {
        String wire = wireStatus(session);
        boolean completed = session.getStatus() == NxisasScrapeSession.Status.COMPLETED;

        CardSyncStatusResponse.Challenge challenge = null;
        if (session.getStatus() == NxisasScrapeSession.Status.AWAITING_CHALLENGE
                && session.getChallengeKind() != null) {
            ScrapeChallengeKind k = session.getChallengeKind();
            CardScrapeKey head = session.getCardsQueue().peekFirst();
            String bankName = resolveBankName(head == null ? null : head.bankCd());
            challenge = new CardSyncStatusResponse.Challenge(
                    k.name(),
                    session.getChallengePrompt(),
                    k == ScrapeChallengeKind.QNA ? session.getChallengeQnaQuestions() : null,
                    k == ScrapeChallengeKind.CAPTCHA ? session.getChallengeCaptchaImage() : null,
                    k == ScrapeChallengeKind.CAPTCHA ? captchaImageFormatFor(session.getBankModule()) : null,
                    session.getLoginId(),
                    bankName);
        }

        int cardsSynced = session.getSuccessAccounts();
        return new CardSyncStatusResponse(
                session.getSessionId(),
                wire,
                session.getProgress(),
                session.getCurrentPhase() == null ? null : session.getCurrentPhase().name(),
                session.getCompletedAccounts(),
                session.getTotalAccounts(),
                cardsSynced,
                session.getFailedAccounts(),
                challenge,
                completed ? cardsSynced : null,
                completed ? session.getCardRowsInserted() : null,
                completed ? session.getCardRowsSkipped() : null,
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

    private static CardSyncStatusResponse cancelledSnapshot(UUID sessionId) {
        return new CardSyncStatusResponse(sessionId, "CANCELLED",
                0, null, 0, 0, 0, 0, null,
                null, null, null, null, null);
    }

    // ─────────────────────────────────────────────────────────────────
    // Session lookup + validation
    // ─────────────────────────────────────────────────────────────────

    /** Tenant-validated CARD_SYNC session for status polling — returns any
     *  state (incl. terminal) so the frontend can read the final snapshot. */
    private NxisasScrapeSession requireOwnedCardSession(UUID sessionId) {
        String bizKey = requireBizKey();
        String userId = TenantContext.currentUserId();
        NxisasScrapeSession s = registry.find(sessionId, bizKey, userId)
                .orElseThrow(() -> new BusinessException("WBE2060"));
        if (s.getMode() != NxisasScrapeSession.Mode.CARD_SYNC) {
            throw new BusinessException("WBE2060");
        }
        s.touch();
        return s;
    }

    /** Tenant-validated CARD_SYNC session for a challenge resume — rejects
     *  cancelled / terminal / expired sessions ({@code WBE2060}) and the wrong
     *  resume verb ({@code WBE9001}). */
    private NxisasScrapeSession requireResumableCardSession(UUID sessionId, ScrapeChallengeKind expected) {
        NxisasScrapeSession s = requireOwnedCardSession(sessionId);
        if (s.isCancelled() || s.isTerminal()) {
            throw new BusinessException("WBE2060");
        }
        if (s.getChallengeKind() != expected) {
            throw new BusinessException("WBE9001");
        }
        return s;
    }

    // ─────────────────────────────────────────────────────────────────
    // Helpers (mirroring BankTransactionSyncService)
    // ─────────────────────────────────────────────────────────────────

    private static String loginGroupKey(CardScrapeKey key) {
        return key.bankCertSeq() == null ? "" : key.bankCertSeq().toPlainString();
    }

    private String resolveBankName(String bankCd) {
        if (bankCd == null) return null;
        return bankNameResolver.resolve(bankCd);
    }

    private static boolean hasLiveContinuation(NxisasScrapeSession s) {
        return s.getSasSessionID() != null && !s.getSasSessionID().isBlank();
    }

    private static GatewayContinuation continuationOf(NxisasScrapeSession s) {
        return new GatewayContinuation(
                s.getSasSessionID(), s.getGwSysNo(), s.getLbsNo(), s.getSsSysNo(), s.getThreadNo());
    }

    private static void storeContinuation(NxisasScrapeSession s, GatewayContinuation c) {
        s.setSasSessionID(c.sasSessionID());
        s.setGwSysNo(c.gwSysNo());
        s.setLbsNo(c.lbsNo());
        s.setSsSysNo(c.ssSysNo());
        s.setThreadNo(c.threadNo());
    }

    /** {@code bankModule} is final-via-Lombok on the session; CARD_SYNC crosses
     *  credentials within one batch, so overwrite it per group via reflection
     *  (same one-field workaround as {@code BankTransactionSyncService}). */
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
        if (f.contains("otp"))     return ScrapeChallengeKind.OTP;
        if (f.contains("captcha")) return ScrapeChallengeKind.CAPTCHA;
        if (f.contains("qna"))     return ScrapeChallengeKind.QNA;
        return null;
    }

    private static List<QnaQuestion> qnaQuestionsFrom(NxisasLoginJobResult result) {
        if (result == null || result.getReq() == null || !result.getReq().isObject()) {
            return List.of();
        }
        JsonNode inner = result.getReq().get("req");
        if (inner == null || !inner.isArray()) return List.of();
        List<QnaQuestion> out = new ArrayList<>(inner.size());
        for (JsonNode n : inner) {
            String hidden = n.path("HiddenValue").asText("");
            String question = n.path("Question").asText("");
            if (hidden.isBlank() && question.isBlank()) continue;
            out.add(new QnaQuestion(hidden, question));
        }
        return out;
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

    private static String minus90Days(String yyyymmdd) {
        if (yyyymmdd == null || yyyymmdd.length() < 8) return yyyymmdd;
        java.time.LocalDate d = java.time.LocalDate.parse(yyyymmdd.substring(0, 8), DT_8).minusDays(90);
        return d.format(DT_8);
    }

    private static String firstNonBlank(String... vals) {
        for (String v : vals) if (v != null && !v.isBlank()) return v;
        return null;
    }

    private static String tid(NxisasScrapeSession session) {
        return session.getSessionId().toString();
    }

    private static String requireBizKey() {
        String bizKey = TenantContext.currentBizKey();
        if (bizKey == null || bizKey.isBlank()) {
            throw new BusinessException("WBE9999");
        }
        return bizKey;
    }
}
