package com.kosign.wecms.domain.banking.nxisas;

import com.fasterxml.jackson.databind.JsonNode;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.legacy.LegacyFunctionRepository;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.banking.bankcredentials.service.BankCredentialService;
import com.kosign.wecms.domain.banking.bankcredentials.service.BankCredentialService.DecryptedCredential;
import com.kosign.wecms.domain.banking.dto.TransactionsSyncRequest;
import com.kosign.wecms.domain.banking.dto.TransactionsSyncResponse;
import com.kosign.wecms.domain.banking.entity.AcctMstr;
import com.kosign.wecms.domain.banking.entity.AcctTrnsHstr;
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
import com.kosign.wecms.domain.banking.repository.AcctTrnsHstrRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;

/**
 * Bank Transaction Sync orchestrator — the multi-step scrape flow
 * fronted by the Account Inquiry &gt; Update popup.
 *
 * <p>Drives the per-account LOGIN → (challenge?) → TRANSACTION → BALANCE
 * chain. When Coocon returns an OTP / QNA / CAPTCHA challenge, the
 * orchestrator persists the in-flight batch on an
 * {@link NxisasScrapeSession} (mode = SYNC), returns a
 * {@code *_REQUIRED} response to the frontend, and resumes from the
 * matching {@code submit*} entry point once the user supplies the
 * answer. Mirrors the legacy {@code fnsh_0001_001_c001_act.jsp}
 * LOGIN→TRANSACTION→BALANCE pipeline and reuses the same registry +
 * session-eviction infrastructure as
 * {@link BankScrapingService} (which serves the Login Credentials
 * Setup flow).
 *
 * <p>Per-account fail-fast: the {@code @Transactional} on every public
 * entry method rolls back partial DB writes when any step throws —
 * surfacing a partial sync would hide a stale-cache bug between
 * Account Inquiry and Settings &gt; Login Credentials.
 *
 * <p>Error codes:
 * <ul>
 *   <li>{@code WBE2050} — no credential registered for the bank.</li>
 *   <li>{@code WBE_BANK_ACCOUNT_NOT_FOUND} — selected account isn't in
 *       this tenant (stale frontend state).</li>
 *   <li>{@code WBE2093} — bank lacks an {@code NxisasBankConfig} entry.</li>
 *   <li>{@code WBE2060} — sync session not found / expired.</li>
 *   <li>{@code WBE9001} — wrong resume verb (e.g. POST /qna for an OTP
 *       session).</li>
 *   <li>{@code WBE2095} — bank or gateway rejected the call (raw bank
 *       code carried in {@code args[1]}).</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class BankTransactionSyncService {

    private static final Logger log = LoggerFactory.getLogger(BankTransactionSyncService.class);

    /** Coocon gateway success ({@code 00000000}) — used for the status
     *  columns this service stamps on ACCT_MSTR after a clean job. The
     *  success/failure *decisions* now route through the shared
     *  {@link NxisasResultCodes} policy (whitelist: {@code 00000000},
     *  {@code 42110000}, {@code 42120011}, {@code 42120111}), matching
     *  legacy {@code nxisas_api.js isSuccess()}. */
    private static final String OK_CODE = NxisasResultCodes.OK;

    /** Legacy {@code yyyyMMddHHmmss} compact audit-column format. */
    private static final DateTimeFormatter DTM_14 = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    /** Display-form {@code yyyy-MM-dd HH:mm:ss} returned to the
     *  frontend for the success dialog's "Last Update" field. */
    private static final DateTimeFormatter DTM_DISPLAY = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    /** Coocon TRANSACTION job date-range format (yyyyMMdd). */
    private static final DateTimeFormatter DT_8 = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final BankCredentialService credentialService;
    private final NxisasGatewayClient gateway;
    private final NxisasBankConfig bankConfig;
    private final AcctMstrRepository acctRepo;
    private final AcctTrnsHstrRepository trnsRepo;
    private final LegacyFunctionRepository legacyFn;
    private final NxisasSessionRegistry registry;
    private final com.kosign.wecms.domain.banking.synchistory.service.SyncHistoryWriter syncHistoryWriter;
    /** HR-2: stamps a failed-scrape status onto ACCT_MSTR in a
     *  REQUIRES_NEW transaction so the failure survives this service's
     *  outer-transaction rollback (mirrors legacy c001 which commits the
     *  failure status). */
    private final AcctMstrStatusUpdater statusUpdater;
    /** P1 auto-matcher — links each newly-scraped withdrawal to its
     *  outgoing payment (biz-env {@code 00079}-gated; off ⇒ no-op).
     *  Runs inside this service's transaction so the just-inserted rows
     *  are visible. See {@code AutoMatchService}. */
    private final com.kosign.wecms.domain.banking.service.AutoMatchService autoMatchService;

    // ─────────────────────────────────────────────────────────────────
    // Public entry points (called from AccountController)
    // ─────────────────────────────────────────────────────────────────

    /**
     * Start a new sync batch. Drains the request's account list
     * through the per-account LOGIN/TRANSACTION/BALANCE loop until
     * either the queue empties (→ {@code COMPLETED}) or a bank LOGIN
     * stops on a challenge (→ {@code *_REQUIRED}).
     */
    @Transactional
    public TransactionsSyncResponse start(TransactionsSyncRequest req) {
        final OffsetDateTime startedAt = OffsetDateTime.now(ZoneOffset.UTC);
        // Auto window (no explicit range from the popup) → start is
        // computed PER ACCOUNT from its own last-success inside
        // processOneAccount (legacy fnsh_0001_001.ts #acct_Reg parity).
        // `fromDt` here is only a batch-level fallback for accounts that
        // never synced and for the audit-history window on a LOGIN-stage
        // failure (before the per-account start is known) — legacy's
        // never-synced fallback is today − 3 months.
        final boolean autoWindow = req.fromDt() == null || req.toDt() == null;
        final String fromDt = autoWindow
                ? startedAt.minusMonths(3).format(DT_8)
                : req.fromDt().replace("-", "");
        final String toDt = autoWindow
                ? startedAt.format(DT_8)
                : req.toDt().replace("-", "");

        // Legacy 92-day cap (nxisas_tranList.js line 220 parity).
        // Frontend enforces the same rule locally; re-validate here so
        // a stale / spoofed client can't trigger an oversized Coocon
        // TRANSACTION job. Inclusive day count to match what the user
        // sees on the date chips.
        if (req.fromDt() != null && req.toDt() != null) {
            try {
                java.time.LocalDate from = java.time.LocalDate.parse(fromDt, DT_8);
                java.time.LocalDate to   = java.time.LocalDate.parse(toDt,   DT_8);
                long days = java.time.temporal.ChronoUnit.DAYS.between(from, to) + 1;
                if (days > 92) {
                    throw new BusinessException("WBE_BANK_TRANSACTION_SYNC_RANGE_TOO_LONG");
                }
            } catch (java.time.format.DateTimeParseException e) {
                // Malformed date — surface as validation error rather than
                // crashing on the Coocon job input.
                throw new BusinessException("WBE_VALIDATION");
            }
        }

        // Legacy parity (fnsh_0001_001.ts:399 — `if (data('yn') == 'Y') ...`):
        // hand-registered accounts have no bank credential so the
        // online scrape can't run against them. Silently drop them
        // before queuing — the frontend disables their checkbox too,
        // but a stale / spoofed payload could still ship one through.
        // If every selected account is HAND_REG, surface a typed
        // error so the popup doesn't open an empty challenge dialog
        // chain.
        final String bizKey = requireBizKey();
        java.util.List<TransactionsSyncRequest.AccountKey> filtered = new java.util.ArrayList<>(req.accounts().size());
        // Batch-load all referenced accounts in one query, then run the
        // original per-element not-found + HAND_REG checks (request order)
        // against the map. Missing account still throws
        // WBE_BANK_ACCOUNT_NOT_FOUND on the first absent key; HAND_REG rows
        // are still skipped; queue order is unchanged.
        java.util.List<com.kosign.wecms.domain.banking.entity.AcctMstrId> ids =
                new java.util.ArrayList<>(req.accounts().size());
        for (TransactionsSyncRequest.AccountKey key : req.accounts()) {
            ids.add(new com.kosign.wecms.domain.banking.entity.AcctMstrId(
                    bizKey, key.bankCd(), key.acctNo()));
        }
        // Key by (bank_cd, acct_no); biz_key is constant within this call
        // and findAllById already scopes by the composite PK.
        java.util.Map<String, AcctMstr> byKey = new java.util.HashMap<>();
        for (AcctMstr acct : acctRepo.findAllById(ids)) {
            byKey.put(acct.getBankCd() + " " + acct.getAcctNo(), acct);
        }
        for (TransactionsSyncRequest.AccountKey key : req.accounts()) {
            AcctMstr acct = byKey.get(key.bankCd() + " " + key.acctNo());
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

        // Order the queue so accounts of the same bank/login group are
        // contiguous — this is what lets advance() reuse one LOGIN per
        // group (legacy nxisas_api.js login-once-per-bank). Groups keep
        // first-seen order, and within a group accounts keep their selection
        // order, so the processing order stays deterministic. A LinkedHashMap
        // preserves both orderings.
        java.util.LinkedHashMap<String, java.util.List<TransactionsSyncRequest.AccountKey>> grouped =
                new java.util.LinkedHashMap<>();
        for (TransactionsSyncRequest.AccountKey key : filtered) {
            grouped.computeIfAbsent(loginGroupKey(key), k -> new java.util.ArrayList<>()).add(key);
        }
        Deque<TransactionsSyncRequest.AccountKey> queue = new ArrayDeque<>(filtered.size());
        for (java.util.List<TransactionsSyncRequest.AccountKey> group : grouped.values()) {
            queue.addAll(group);
        }
        NxisasScrapeSession session = newSyncSession(queue, fromDt, toDt, autoWindow);
        NxisasApiLog.event(tid(session), NxisasApiLog.FLOW_BANK_SYNC, "START",
                "accounts", filtered.size(), "groups", grouped.size());
        return advance(session);
    }

    /** Resume after a QNA (security-question) answer. */
    @Transactional
    public TransactionsSyncResponse submitQna(UUID sessionId, SubmitQnaRequest req) {
        NxisasScrapeSession session = requireSession(sessionId);
        assertChallenge(session, ScrapeChallengeKind.QNA);
        NxisasApiLog.event(tid(session), NxisasApiLog.FLOW_BANK_SYNC, "CHALLENGE_SUBMIT", "type", "QNA");

        NxisasQnaJobInput.QnaResponse res = new NxisasQnaJobInput.QnaResponse();
        res.setQuestion(req.question());
        res.setHiddenValue(req.hiddenValue());
        res.setAnswer(req.answer());
        NxisasQnaJobInput input = new NxisasQnaJobInput();
        input.setRes(res);

        String trSeq = gateway.newTrSeq();
        NxisasApiOut<NxisasLoginJobResult> out = gateway.submitQna(
                session.getBankModule(), session.getBankCategory(), trSeq,
                input, continuationOf(session));
        storeContinuation(session, GatewayContinuation.from(out));

        return handleLoginContinuation(session, out, "QNA");
    }

    /** Resume after an OTP submission. */
    @Transactional
    public TransactionsSyncResponse submitOtp(UUID sessionId, SubmitChallengeRequest req) {
        NxisasScrapeSession session = requireSession(sessionId);
        assertChallenge(session, ScrapeChallengeKind.OTP);
        NxisasApiLog.event(tid(session), NxisasApiLog.FLOW_BANK_SYNC, "CHALLENGE_SUBMIT", "type", "OTP");

        NxisasOTPJobInput input = new NxisasOTPJobInput();
        input.setRes(req.value());

        String trSeq = gateway.newTrSeq();
        NxisasApiOut<NxisasOTPJobResult> out = gateway.submitOtp(
                session.getBankModule(), session.getBankCategory(), trSeq,
                input, continuationOf(session));
        storeContinuation(session, GatewayContinuation.from(out));

        // OTP responses share the LoginJobResult shape; wrap by hand.
        NxisasApiOut<NxisasLoginJobResult> wrap = new NxisasApiOut<>();
        wrap.setCommon(out.getCommon());
        wrap.setResultList(otpToLoginResults(out));
        wrap.setSasSessionID(out.getSasSessionID());
        return handleLoginContinuation(session, wrap, "OTP");
    }

    /** Resume after a CAPTCHA submission. */
    @Transactional
    public TransactionsSyncResponse submitCaptcha(UUID sessionId, SubmitChallengeRequest req) {
        NxisasScrapeSession session = requireSession(sessionId);
        assertChallenge(session, ScrapeChallengeKind.CAPTCHA);
        NxisasApiLog.event(tid(session), NxisasApiLog.FLOW_BANK_SYNC, "CHALLENGE_SUBMIT", "type", "CAPTCHA");

        NxisasCaptchaJobInput input = new NxisasCaptchaJobInput();
        input.setRes(req.value());

        String trSeq = gateway.newTrSeq();
        NxisasApiOut<NxisasLoginJobResult> out = gateway.submitCaptcha(
                session.getBankModule(), session.getBankCategory(), trSeq,
                input, continuationOf(session));
        storeContinuation(session, GatewayContinuation.from(out));

        return handleLoginContinuation(session, out, "CAPTCHA");
    }

    // ─────────────────────────────────────────────────────────────────
    // Core flow
    // ─────────────────────────────────────────────────────────────────

    /**
     * Drive the queue forward — for each pending account, attempt
     * LOGIN; if it succeeds outright, run TRANSACTION + BALANCE +
     * persist; loop. When LOGIN returns a challenge, persist state on
     * the session and return the matching {@code *_REQUIRED} response.
     */
    private TransactionsSyncResponse advance(NxisasScrapeSession session) {
        while (!session.getAccountsQueue().isEmpty()) {
            TransactionsSyncRequest.AccountKey key = session.getAccountsQueue().peekFirst();
            AcctMstr acct = acctRepo.findByBizKeyAndBankCdAndAcctNo(
                            session.getBizKey(), key.bankCd(), key.acctNo())
                    .orElseThrow(() -> new BusinessException("WBE_BANK_ACCOUNT_NOT_FOUND"));

            // ── Legacy login-once-per-bank parity (nxisas_api.js:2428) ──
            // When the HEAD account belongs to the bank/login group whose
            // LOGIN already cleared on this session (activeLoginGroupKey) AND
            // the gateway continuation is still live, REUSE the existing
            // SAS_SessionID: skip LOGIN and go straight to this account's
            // TRANSACTION + BALANCE. This is the refactor analogue of the
            // legacy _nxisas_auth[bankCd] session cache — N accounts under
            // one bank cost exactly one LOGIN (and one OTP/QNA/CAPTCHA),
            // not N. The session's bank-routing fields (bankModule,
            // bankCategory, nxisasGbSeqNo) were set when this group's LOGIN
            // ran and stay valid because every account in the group shares
            // one per-bank credential.
            if (loginGroupKey(key).equals(session.getActiveLoginGroupKey())
                    && hasLiveContinuation(session)) {
                NxisasApiLog.event(tid(session), NxisasApiLog.FLOW_BANK_SYNC, "SESSION_REUSE",
                        "bankCd", key.bankCd(), "acctNo", NxisasMask.acct(key.acctNo()));
                processOneAccount(session, acct);
                session.getAccountsQueue().pollFirst();
                continue;
            }

            // ── New bank/login group (or no live session yet) → LOGIN ──
            // Configure the session for the HEAD account's bank.
            DecryptedCredential cred = credentialService.loadForScrapingByBank(acct.getBankCd());
            // Capture the credential username on the session so a
            // subsequent QNA challenge response can echo it back to the
            // Security Question popup. The popup has no other path to
            // the value because the credential lives entirely
            // server-side in BANK_CERT_INFO.
            session.setLoginId(cred.username());
            Integer subTypeSeqNo = cred.nxisasGbSeqNo() == null ? null
                    : cred.nxisasGbSeqNo().intValue();
            NxisasBankConfig.Entry cfg = bankConfig.find(acct.getBankCd(), subTypeSeqNo)
                    .orElseThrow(() -> new BusinessException("WBE2093", acct.getBankCd()));
            session.setBankCategory(cfg.category());
            // Capture the sub-type seqNo on the session so the per-account
            // TRANSACTION + BALANCE steps resolve the SAME Module/Class the
            // LOGIN used. Without this the follow-on steps fell back to the
            // SEQ_NO=0 default, which diverged from a sub-typed LOGIN (VP
            // Bank: LOGIN CORPORATE, TX/BAL PERSONAL). The session field is
            // a primitive int; a null credential seqNo maps to 0, which now
            // resolves identically to the default for every bank (no bank
            // carries a SEQ_NO=0 override). See
            // docs/audits/bank-profile-enterprise-audit.md §13.
            session.setNxisasGbSeqNo(subTypeSeqNo == null ? 0 : subTypeSeqNo);

            // POST LOGIN.
            NxisasLoginJobInput in = new NxisasLoginJobInput();
            in.setLoginId(cred.username());
            in.setLoginPwd(cred.password());
            in.setCountryCode("VNM");
            // loginType default centralised in NxisasBankConfig — explicit
            // value wins; blank → "" for ABBANK, "ID" otherwise (legacy
            // nxisas_fnsh_r001_act.jsp:148-157).
            in.setLoginType(NxisasBankConfig.defaultLoginType(cfg.module(), cred.loginType()));
            if (cred.corporateId() != null && !cred.corporateId().isBlank()) {
                in.setCorporateId(cred.corporateId());
                in.setLoginPwd1(cred.corporateId());
            }

            NxisasApiLog.event(tid(session), NxisasApiLog.FLOW_BANK_SYNC, "GROUP_START",
                    "bankCd", acct.getBankCd(), "acctNo", NxisasMask.acct(acct.getAcctNo()));
            NxisasApiLog.event(tid(session), NxisasApiLog.FLOW_BANK_SYNC, "LOGIN_START",
                    "bankCd", acct.getBankCd(), "module", cfg.module(), "class", cfg.category());

            String trSeq = gateway.newTrSeq();
            NxisasApiOut<NxisasLoginJobResult> out =
                    gateway.login(cfg.module(), cfg.category(), trSeq, in);
            storeContinuation(session, GatewayContinuation.from(out));

            // Update bank module on the session — the gateway-class
            // pair is per-bank, and a multi-bank batch needs to swap
            // it as the queue head changes.
            setBankModuleViaReflection(session, cfg.module());

            TransactionsSyncResponse mid = handleLoginContinuation(session, out, "LOGIN");
            if (mid.status() != TransactionsSyncResponse.Status.COMPLETED) {
                // Either CHALLENGE or ERROR — return immediately; the
                // queue still has the HEAD account waiting for the
                // resume call to finish its LOGIN.
                return mid;
            }
            // Account fully processed (LOGIN clean, TRANSACTION +
            // BALANCE done, queue head popped). Continue to next.
        }

        // Queue drained — terminal success.
        return finalize(session);
    }

    /**
     * Inspect the result of a LOGIN-shape response (initial LOGIN, or
     * a QNA / OTP / CAPTCHA resume that itself shares the LOGIN result
     * shape). Three outcomes:
     * <ul>
     *   <li>bank-adapter error → terminal ERROR.</li>
     *   <li>challenge returned → persist on session, return matching
     *       {@code *_REQUIRED}.</li>
     *   <li>clean login → run TRANSACTION + BALANCE + persist + advance
     *       to the next account in the queue (recurses via
     *       {@link #advance}).</li>
     * </ul>
     */
    private TransactionsSyncResponse handleLoginContinuation(
            NxisasScrapeSession session,
            NxisasApiOut<NxisasLoginJobResult> out,
            String stage) {

        NxisasOutput<?> bankOut = gateway.firstOutput(out);
        if (bankOut != null) {
            String code = bankOut.getErrorCode();
            if (!NxisasResultCodes.isBankAdapterSuccess(code)) {
                String msg = bankOut.getErrorMessage();
                if (msg == null || msg.isBlank()) msg = bankOut.getDetailMessage();
                log.info("BankTransactionSync {} bank-rejected [bank={}, code={}, msg={}]",
                        stage, session.getBankModule(), code, msg);
                NxisasApiLog.event(tid(session), NxisasApiLog.FLOW_BANK_SYNC, "ERROR",
                        "stage", stage, "scope", "BANK", "errorCode", code);
                session.setStatus(NxisasScrapeSession.Status.FAILED);
                registry.discard(session.getSessionId());
                logFailedAttempt(session, code, msg);
                stampScrapeFailure(session, code, msg);
                return TransactionsSyncResponse.error(session.getSessionId(), code, msg);
            }
        }
        if (gatewayRejected(out)) {
            String code = out.getCommon().getResultCd();
            String msg = out.getCommon().getResultMsg();
            log.info("BankTransactionSync {} gateway-rejected [bank={}, code={}]",
                    stage, session.getBankModule(), code);
            NxisasApiLog.event(tid(session), NxisasApiLog.FLOW_BANK_SYNC, "ERROR",
                    "stage", stage, "scope", "GATEWAY", "resultCode", code);
            session.setStatus(NxisasScrapeSession.Status.FAILED);
            registry.discard(session.getSessionId());
            logFailedAttempt(session, code, msg);
            stampScrapeFailure(session, code, msg);
            return TransactionsSyncResponse.error(session.getSessionId(), code, msg);
        }

        NxisasLoginJobResult result = gateway.firstResult(out);
        ScrapeChallengeKind kind = challengeKindFrom(result);
        if (kind != null) {
            session.setStatus(NxisasScrapeSession.Status.AWAITING_CHALLENGE);
            session.setChallengeKind(kind);
            session.setChallengePrompt(result == null ? null : result.challengePrompt());
            registry.register(session);
            TransactionsSyncRequest.AccountKey head = session.getAccountsQueue().peekFirst();
            NxisasApiLog.event(tid(session), NxisasApiLog.FLOW_BANK_SYNC, "CHALLENGE_REQUIRED",
                    "type", kind, "bankCd", head == null ? "-" : head.bankCd());

            if (kind == ScrapeChallengeKind.QNA) {
                return TransactionsSyncResponse.qnaChallenge(
                        session.getSessionId(),
                        session.getChallengePrompt(),
                        qnaQuestionsFrom(result),
                        session.getLoginId());
            }
            if (kind == ScrapeChallengeKind.CAPTCHA) {
                return TransactionsSyncResponse.captchaChallenge(
                        session.getSessionId(),
                        session.getChallengePrompt(),
                        captchaImageFrom(result),
                        captchaImageFormatFor(session.getBankModule()));
            }
            return TransactionsSyncResponse.otpChallenge(
                    session.getSessionId(), session.getChallengePrompt());
        }

        // LOGIN clean — run TRANSACTION + BALANCE + persist for the
        // HEAD account, pop it, then advance the queue.
        TransactionsSyncRequest.AccountKey key = session.getAccountsQueue().peekFirst();
        AcctMstr acct = acctRepo.findByBizKeyAndBankCdAndAcctNo(
                        session.getBizKey(), key.bankCd(), key.acctNo())
                .orElseThrow(() -> new BusinessException("WBE_BANK_ACCOUNT_NOT_FOUND"));
        // This group's LOGIN cleared — its SAS_SessionID now owns the
        // session. Stamp the group so subsequent same-bank accounts reuse
        // it in advance() instead of re-logging in. Reached both from the
        // initial LOGIN and from a challenge resume (submitOtp/Qna/Captcha
        // → handleLoginContinuation), so a post-challenge clean login also
        // unlocks session reuse for the rest of the group.
        session.setActiveLoginGroupKey(loginGroupKey(key));
        NxisasApiLog.event(tid(session), NxisasApiLog.FLOW_BANK_SYNC, "LOGIN_SUCCESS",
                "stage", stage, "bankCd", key.bankCd(),
                "sasSessionId", NxisasMask.token(session.getSasSessionID()));
        processOneAccount(session, acct);
        session.getAccountsQueue().pollFirst();
        return advance(session);
    }

    /**
     * Write an EXT_TRAN_CALL_HIS row for a failed scrape attempt. The
     * failing account is the queue head — the orchestrator's bank-side
     * LOGIN ran against it. When the queue is empty (defensive: a
     * gateway timeout on the very first call can race the queue-head
     * read) the row is skipped to avoid a NULL identifiers row.
     */
    private void logFailedAttempt(NxisasScrapeSession session, String rsltCd, String errCont) {
        TransactionsSyncRequest.AccountKey head = session.getAccountsQueue().peekFirst();
        if (head == null) return;
        try {
            syncHistoryWriter.recordAttempt(
                    session.getBizKey(),
                    session.getUserId(),
                    head.bankCd(),
                    head.acctNo(),
                    session.getFromDt(),
                    session.getToDt(),
                    rsltCd == null || rsltCd.isBlank() ? "ERROR" : rsltCd,
                    errCont == null ? "" : errCont
            );
        } catch (RuntimeException e) {
            // Audit write is best-effort. Never let a logging failure
            // mask the original sync error — log and swallow.
            log.warn("Failed to persist EXT_TRAN_CALL_HIS row for sync failure", e);
        }
    }

    /**
     * HR-2: stamp the failed-scrape status onto the queue-head account so
     * the Account Inquiry list shows "Failed" after a LOGIN / gateway
     * rejection (legacy {@code fnsh_0001_001_c001_act.jsp} commits the
     * error code into the per-job status columns). Best-effort and
     * REQUIRES_NEW — a stamping failure never masks the original sync
     * error, and the write survives even when the outer transaction later
     * rolls back. The head account is the one the bank-side LOGIN ran
     * against; skipped when the queue is empty (defensive).
     */
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

    /**
     * Returns the offending bank / gateway error code if {@code out}
     * represents a failed job, or {@code null} when it succeeded
     * ({@code 00000000} / {@code 42110000} / gateway OK). Same decision
     * logic as {@link #assertOk} but non-throwing, so the caller can
     * stamp the failure status before raising the WBE2095 it would have
     * thrown.
     */
    private String scrapeFailureCode(NxisasApiOut<?> out) {
        NxisasOutput<?> bankOut = gateway.firstOutput(out);
        if (bankOut != null) {
            String code = bankOut.getErrorCode();
            if (!NxisasResultCodes.isBankAdapterSuccess(code)) {
                return code;
            }
        }
        if (gatewayRejected(out)) {
            return out.getCommon().getResultCd();
        }
        return null;
    }

    /** Companion to {@link #scrapeFailureCode} — the human-readable
     *  message for the failed job (bank error message first, gateway
     *  result message as fallback). */
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

    /** Per-account TRANSACTION + BALANCE + persistence step. Called
     *  once LOGIN has cleared for the HEAD account. */
    private void processOneAccount(NxisasScrapeSession session, AcctMstr acct) {
        String nowDb = OffsetDateTime.now(ZoneOffset.UTC).format(DTM_14);
        String userId = session.getUserId();

        // TRANSACTION.
        // Auto mode → start from THIS account's own last-success so a
        // re-sync only pulls new rows since it last synced (legacy
        // fnsh_0001_001.ts:336-344 #acct_Reg). Explicit-range mode → the
        // single user-picked window applies to every account.
        final String startDate = session.isAutoWindow()
                ? perAccountStartDt(acct)
                : session.getFromDt();
        NxisasTransactionJobInput tIn = new NxisasTransactionJobInput();
        tIn.setAcctNum(acct.getAcctNo());
        tIn.setCurrency(acct.getCcyCd());
        tIn.setStartDate(startDate);
        tIn.setEndDate(session.getToDt());
        NxisasBankConfig.Entry cfg = bankConfig.find(acct.getBankCd(), session.getNxisasGbSeqNo())
                .orElseThrow(() -> new BusinessException("WBE2093", acct.getBankCd()));

        NxisasApiLog.event(tid(session), NxisasApiLog.FLOW_BANK_SYNC, "TRANSACTION_START",
                "bankCd", acct.getBankCd(), "acctNo", NxisasMask.acct(acct.getAcctNo()),
                "from", session.getFromDt(), "to", session.getToDt());
        String trSeq = gateway.newTrSeq();
        NxisasApiOut<NxisasTransactionJobResult> tOut = gateway.transaction(
                cfg.module(), cfg.category(), trSeq, tIn, continuationOf(session));
        // HR-2: on a TRANSACTION-job failure the outer @Transactional
        // rolls back, so stamp the failure status in a REQUIRES_NEW
        // transaction first (legacy c001 commits the failure code into
        // ACCT_HIS_INQ_STTS) — then throw the existing WBE2095 so the
        // caller's error contract is unchanged.
        String txFailCode = scrapeFailureCode(tOut);
        if (txFailCode != null) {
            statusUpdater.markFailure(session.getBizKey(), acct.getBankCd(), acct.getAcctNo(),
                    txFailCode, scrapeFailureMsg(tOut), userId, nowDb);
            log.info("BankTransactionSync TRANSACTION rejected [bank={}, code={}]",
                    acct.getBankCd(), txFailCode);
            NxisasApiLog.event(tid(session), NxisasApiLog.FLOW_BANK_SYNC, "ERROR",
                    "stage", "TRANSACTION", "scope", "BANK",
                    "bankCd", acct.getBankCd(), "acctNo", NxisasMask.acct(acct.getAcctNo()),
                    "errorCode", txFailCode);
            throw new BusinessException("WBE2095", acct.getBankCd(), txFailCode);
        }
        NxisasTransactionJobResult tResult = gateway.firstTransactionResult(tOut);
        List<NxisasTransactionEntry> entries = tResult == null || tResult.getTransaction() == null
                ? List.of() : tResult.getTransaction();

        PersistResult pr = persistEntries(session.getBizKey(), userId, acct, entries, nowDb);
        session.setTotalDebitInserted(session.getTotalDebitInserted() + pr.debitCount);
        session.setTotalCreditInserted(session.getTotalCreditInserted() + pr.creditCount);
        session.setTotalDebitAmount(session.getTotalDebitAmount().add(pr.debitTotal));
        session.setTotalCreditAmount(session.getTotalCreditAmount().add(pr.creditTotal));
        // Fold this account's result into its currency bucket so the
        // COMPLETED response can report per-currency groups (USD / KRW /
        // …) with their own account tally + Credit / Debit numbers rather
        // than the batch aggregate restated under every currency.
        session.currencyTally(acct.getCcyCd())
                .addAccount(pr.creditCount, pr.debitCount, pr.creditTotal, pr.debitTotal);

        // P1 AUTO-MATCH — link the just-inserted withdrawals to their
        // outgoing payment (legacy fnsh_0001_001_c001_act.jsp:1853-2079).
        // Gated per-tenant by biz-env 00079 inside the service. Wrapped so a
        // matcher failure never aborts the sync (the transactions are already
        // persisted); the service also guards each debit individually.
        try {
            int autoMatched = autoMatchService.autoMatchNewDebits(
                    session.getBizKey(), userId, acct.getAcctNo(), pr.newDebits);
            session.setTotalAutoMatched(session.getTotalAutoMatched() + autoMatched);
        } catch (Exception amEx) {
            log.error("BankTransactionSync AUTO-MATCH failed [bank={}, acct={}] — sync continues",
                    acct.getBankCd(), acct.getAcctNo(), amEx);
        }

        // BALANCE (soft-fail).
        refreshBalance(session, acct);

        // Audit stamps.
        acct.setLstLgnSucDtm(nowDb);
        acct.setAcctBalLstInqDtm(nowDb);
        // HR-2: clean LOGIN + TRANSACTION outcome for this account. The
        // managed entity commits with the outer transaction. BALANCE
        // status is set inside refreshBalance() (success → 00000000,
        // soft-fail → bank error code). Legacy c001 writes the same
        // per-job status columns; ACCT_BAL_INQ_STTS is the one the
        // Account Inquiry list badge reads.
        acct.setLstLgnStts(OK_CODE);
        acct.setLstLgnSttsMsg(null);
        acct.setAcctHisInqStts(OK_CODE);
        acct.setAcctHisInqMsg(null);
        acct.setAcctHisLstInqDt(nowDb);
        acct.setModId(userId);
        acct.setModDtm(nowDb);

        session.setAccountsSynced(session.getAccountsSynced() + 1);
        NxisasApiLog.event(tid(session), NxisasApiLog.FLOW_BANK_SYNC, "ACCOUNT_DONE",
                "bankCd", acct.getBankCd(), "acctNo", NxisasMask.acct(acct.getAcctNo()),
                "txCount", entries.size());

        // Audit-log this per-account success — one row in
        // EXT_TRAN_CALL_HIS so the Bank Transaction Sync > History
        // popup lists the attempt. Mirrors legacy WeCMS which writes
        // the audit row at the end of each per-account scrape.
        // REQUIRES_NEW propagation ensures a later failure on a
        // different account doesn't roll this row back.
        syncHistoryWriter.recordAttempt(
                session.getBizKey(),
                userId,
                acct.getBankCd(),
                acct.getAcctNo(),
                startDate,
                session.getToDt(),
                com.kosign.wecms.domain.banking.synchistory.service.SyncHistoryWriter.RSLT_CD_SUCCESS,
                ""
        );
    }

    /** Build the terminal {@code COMPLETED} response from the
     *  session's accumulators. */
    private TransactionsSyncResponse finalize(NxisasScrapeSession session) {
        session.setStatus(NxisasScrapeSession.Status.COMPLETED);
        registry.discard(session.getSessionId());
        String now = OffsetDateTime.now(ZoneOffset.UTC).format(DTM_DISPLAY);
        log.info("BankTransactionSync completed [bizKey={}, accounts={}, debit={}, credit={}, autoMatched={}]",
                session.getBizKey(), session.getAccountsSynced(),
                session.getTotalDebitInserted(), session.getTotalCreditInserted(),
                session.getTotalAutoMatched());
        NxisasApiLog.event(tid(session), NxisasApiLog.FLOW_BANK_SYNC, "COMPLETE",
                "success", session.getAccountsSynced(),
                "debit", session.getTotalDebitInserted(),
                "credit", session.getTotalCreditInserted());
        return TransactionsSyncResponse.completed(
                session.getSessionId(),
                session.getAccountsSynced(),
                session.getTotalDebitInserted(),
                session.getTotalCreditInserted(),
                session.getTotalDebitAmount(),
                session.getTotalCreditAmount(),
                now,
                currencySummariesOf(session),
                session.getTotalAutoMatched());
    }

    /** Project the session's per-currency tallies into the wire DTO list,
     *  preserving first-seen currency order. {@code failedCount} is 0:
     *  {@link #finalize} only runs when the queue fully drains, and any
     *  account-level failure returns {@code ERROR} from
     *  {@link #handleLoginContinuation} / {@link #processOneAccount}
     *  before the batch can finalize — so a COMPLETED batch means every
     *  account of every currency synced. */
    private static List<com.kosign.wecms.domain.banking.dto.CurrencySummary> currencySummariesOf(
            NxisasScrapeSession session) {
        List<com.kosign.wecms.domain.banking.dto.CurrencySummary> out =
                new ArrayList<>(session.getCurrencyTallies().size());
        for (var e : session.getCurrencyTallies().entrySet()) {
            NxisasScrapeSession.CurrencyTally t = e.getValue();
            out.add(new com.kosign.wecms.domain.banking.dto.CurrencySummary(
                    e.getKey(),
                    t.successCount(),
                    0,
                    t.creditCount(),
                    t.creditTotal().toPlainString(),
                    t.debitCount(),
                    t.debitTotal().toPlainString()));
        }
        return out;
    }

    // ─────────────────────────────────────────────────────────────────
    // Persistence (TRANSACTION → ACCT_TRNS_HSTR + ACCT_MSTR balance)
    // ─────────────────────────────────────────────────────────────────

    /** Per-account persist result — counts + amount totals for
     *  every Coocon-returned row in the scrape window, regardless
     *  of whether the row was newly INSERTed or skipped as a
     *  duplicate (dedup hit on BANK_TRNS_FULL_ID). The popup's
     *  Credit / Debit display these "scraped" numbers so the user
     *  sees the same totals every time they re-sync the same
     *  window — matching the Latest Transaction History dialog
     *  that reads from the DB. Returned by {@link #persistEntries}
     *  and accumulated onto the session inside
     *  {@link #processOneAccount}. */
    private record PersistResult(
            int debitCount,
            int creditCount,
            BigDecimal debitTotal,
            BigDecimal creditTotal,
            /** Newly-INSERTED debit ({@code INOUT_DV='2'}) rows only — the
             *  auto-matcher's input. Dedup-skipped rows are NOT included (they
             *  already exist and were matched on a prior sync). */
            List<AcctTrnsHstr> newDebits) { }

    private PersistResult persistEntries(String bizKey, String userId, AcctMstr acct,
                                         List<NxisasTransactionEntry> entries, String nowDb) {
        int debit = 0;
        int credit = 0;
        BigDecimal debitSum  = BigDecimal.ZERO;
        BigDecimal creditSum = BigDecimal.ZERO;
        List<AcctTrnsHstr> newDebits = new java.util.ArrayList<>();

        for (NxisasTransactionEntry e : entries) {
            BigDecimal amt;
            String inoutDv;
            if (isPositive(e.getDebit())) {
                amt = parseDecimal(e.getDebit());
                inoutDv = "2";
                if (amt.signum() < 0) { amt = amt.negate(); inoutDv = "1"; }
            } else if (isPositive(e.getCredit())) {
                amt = parseDecimal(e.getCredit());
                inoutDv = "1";
                if (amt.signum() < 0) { amt = amt.negate(); inoutDv = "2"; }
            } else {
                continue;
            }

            // Tally every Coocon-returned row, regardless of whether
            // the INSERT below ends up running. The popup's Credit /
            // Debit totals reflect "what the bank scrape returned for
            // this window" — the user-facing meaning. Dedup
            // (BANK_TRNS_FULL_ID lookup) below decides whether we add
            // a new ACCT_TRNS_HSTR row, but it doesn't change what
            // Coocon told us about the window.
            if ("1".equals(inoutDv)) {
                credit++;
                creditSum = creditSum.add(amt);
            } else {
                debit++;
                debitSum = debitSum.add(amt);
            }

            String fullId = BankTrnsIdGenerator.fullId(
                    acct.getBankCd(), e.getDate(), e.getTime(),
                    amt, inoutDv, e.getRemarks(), e.getRemarks2());
            if (trnsRepo.findFirstByBizKeyAndBankTrnsFullId(bizKey, fullId).isPresent()) {
                continue;
            }

            AcctTrnsHstr row = new AcctTrnsHstr();
            row.setBizKey(bizKey);
            row.setAcctSeqNo(legacyFn.nextAcctTrnsHstrAcctSeqNo(bizKey));
            row.setBankCd(acct.getBankCd());
            row.setAcctNo(acct.getAcctNo());
            row.setTrscDt(e.getDate());
            row.setTrscTm(e.getTime() == null ? "" : e.getTime());
            row.setCcyCd(acct.getCcyCd());
            row.setInoutDv(inoutDv);
            row.setTrscAmt(amt);
            row.setBal(parseDecimal(e.getBalance()));
            row.setRmrk(e.getRemarks());
            row.setRmrk2(e.getRemarks2());
            row.setBankRefNo(e.getBankReference());
            row.setBankTrnsFullId(fullId);
            row.setBankTrnsShortId(BankTrnsIdGenerator.shortId(e.getDate(), fullId));
            row.setLinkMatchYn("N");
            row.setRegId(userId);
            row.setRegDtm(nowDb);
            row.setModId(userId);
            row.setModDtm(nowDb);

            trnsRepo.save(row);
            // Feed only genuinely-new debits to the P1 auto-matcher (credits
            // and dedup-skipped rows are never outgoing-payment candidates).
            if ("2".equals(inoutDv)) newDebits.add(row);
        }
        return new PersistResult(debit, credit, debitSum, creditSum, newDebits);
    }

    private void refreshBalance(NxisasScrapeSession session, AcctMstr acct) {
        NxisasBankConfig.Entry cfg = bankConfig.find(acct.getBankCd(), session.getNxisasGbSeqNo())
                .orElseThrow(() -> new BusinessException("WBE2093", acct.getBankCd()));
        NxisasBalanceJobInput in = new NxisasBalanceJobInput();
        in.setAcctNum(acct.getAcctNo());
        in.setCurrency(acct.getCcyCd());

        NxisasApiLog.event(tid(session), NxisasApiLog.FLOW_BANK_SYNC, "BALANCE_START",
                "bankCd", acct.getBankCd(), "acctNo", NxisasMask.acct(acct.getAcctNo()));
        String trSeq = gateway.newTrSeq();
        NxisasApiOut<NxisasBalanceJobResult> out = gateway.balance(
                cfg.module(), cfg.category(), trSeq, in, continuationOf(session));

        NxisasOutput<NxisasBalanceJobResult> bankOut = gateway.firstOutput(out);
        // BALANCE is stricter than LOGIN/TRANSACTION: a null/blank adapter
        // code counts as a soft-fail here (legacy c001 stamps an error
        // status when balance returns nothing usable), so we require an
        // explicit whitelisted code — unlike isBankAdapterSuccess which
        // treats null/blank as success for the routed-but-silent LOGIN case.
        String balErr = bankOut == null ? null : bankOut.getErrorCode();
        boolean balanceOk = balErr != null && !balErr.isBlank()
                && NxisasResultCodes.isBankAdapterSuccess(balErr);
        if (!balanceOk) {
            String balCode = bankOut == null ? "ERROR" : bankOut.getErrorCode();
            String balMsg  = bankOut == null ? null : bankOut.getErrorMessage();
            log.warn("BankTransactionSync BALANCE soft-failed [bank={}, acct={}, code={}]",
                    acct.getBankCd(), acct.getAcctNo(), balCode);
            // HR-2: soft-fail still records the balance-inquiry status so
            // the Account Inquiry list badge reflects the failed refresh
            // (legacy c001 sets ACCT_BAL_INQ_STTS / _MSG on balance
            // failure). The managed entity commits with the outer
            // transaction; the transaction list + login status already
            // succeeded, so this is a partial result, not a rollback.
            acct.setAcctBalInqStts(balCode == null || balCode.isBlank() ? "ERROR" : balCode);
            acct.setAcctBalInqMsg(balMsg);
            return;
        }
        // HR-2: balance inquiry succeeded — clear any prior error status
        // (legacy c001 stamps ACCT_BAL_INQ_STTS = 00000000 on success).
        // Set before the empty-list early-return so a successful balance
        // job with no rows still reads as success on the list badge.
        acct.setAcctBalInqStts(OK_CODE);
        acct.setAcctBalInqMsg(null);

        NxisasBalanceJobResult result = gateway.firstBalanceResult(out);
        List<NxisasBalanceEntry> list = result == null ? null
                : (result.getBalance() != null && !result.getBalance().isEmpty()
                        ? result.getBalance() : result.getAcctList());
        if (list == null || list.isEmpty()) return;

        NxisasBalanceEntry first = list.get(0);
        BigDecimal bal = parseDecimal(first.getBalance());
        BigDecimal avail = parseDecimal(first.getAvailableBal());
        if (bal != null) acct.setBal(bal);
        if (avail != null) acct.setRealAmt(avail);
    }

    // ─────────────────────────────────────────────────────────────────
    // Session helpers
    // ─────────────────────────────────────────────────────────────────

    private NxisasScrapeSession newSyncSession(
            Deque<TransactionsSyncRequest.AccountKey> queue, String fromDt, String toDt,
            boolean autoWindow) {
        String bizKey = requireBizKey();
        String userId = TenantContext.currentUserId();
        // bankModule is per-account (set inside advance()) — start
        // empty; the first iteration writes the correct value.
        String trSeq = gateway.newTrSeq();
        // bankCd is null here — the SYNC flow scrapes per-account
        // transactions/balance, not the acct-list, so the
        // NXISAS_ACCT_TYPE_MAPPING lookup (SETUP only) does not apply.
        NxisasScrapeSession session = new NxisasScrapeSession(
                trSeq, bizKey, userId, null, "", null);
        session.setMode(NxisasScrapeSession.Mode.SYNC);
        session.setAccountsQueue(queue);
        session.setFromDt(fromDt);
        session.setToDt(toDt);
        session.setAutoWindow(autoWindow);
        return session;
    }

    private NxisasScrapeSession requireSession(UUID sessionId) {
        String bizKey = requireBizKey();
        String userId = TenantContext.currentUserId();
        NxisasScrapeSession s = registry.find(sessionId, bizKey, userId)
                .orElseThrow(() -> new BusinessException("WBE2060"));
        if (s.getMode() != NxisasScrapeSession.Mode.SYNC) {
            // A SETUP-flow session was passed to the sync resume
            // endpoint — refuse rather than half-execute.
            throw new BusinessException("WBE2060");
        }
        s.touch();
        return s;
    }

    private static void assertChallenge(NxisasScrapeSession session, ScrapeChallengeKind expected) {
        if (session.getChallengeKind() != expected) {
            throw new BusinessException("WBE9001");
        }
    }

    /**
     * Bank/login-group key for an account. Accounts that share a key are
     * scraped under a single LOGIN (legacy {@code _nxisas_auth[bankCd]}
     * parity). The key is the {@code bankCd}: the refactor loads exactly
     * one credential per bank ({@code loadForScrapingByBank(bankCd)}), so
     * the credential's sub-type ({@code nxisasGbSeqNo}) is constant within a
     * bank and {@code bankCd} alone is the faithful login-group identity.
     * If a future requirement allows two credentials for one bank, widen
     * this to {@code bankCd + ":" + nxisasGbSeqNo}.
     */
    private static String loginGroupKey(TransactionsSyncRequest.AccountKey key) {
        return key.bankCd();
    }

    /**
     * Per-account TRANSACTION window start for Auto mode — the account's
     * own last-success date so a re-sync only fetches new rows. Mirrors
     * legacy {@code fnsh_0001_001.ts:336-344} (live {@code #acct_Reg}
     * handler): last transaction-history inquiry → last balance inquiry
     * → last successful login → today − 3 months (legacy never-synced
     * fallback). Returns {@code yyyyMMdd} (the Coocon TRANSACTION job
     * format); the stored markers are {@code yyyyMMddHHmmss} so the
     * leading 8 chars are the date.
     */
    private static String perAccountStartDt(AcctMstr acct) {
        String marker = firstNonBlank(
                acct.getAcctHisLstInqDt(),
                acct.getAcctBalLstInqDtm(),
                acct.getLstLgnSucDtm());
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

    /** Flow trace id for the NXISAS debug log — the scrape session id, the
     *  one value shared across start / challenge / submit / final and
     *  echoed to the frontend as {@code session_id}. See {@link NxisasApiLog}. */
    private static String tid(NxisasScrapeSession session) {
        return session.getSessionId().toString();
    }

    /** True when the session carries a usable gateway continuation — a
     *  non-blank {@code SAS_SessionID} from a prior clean LOGIN. Guards the
     *  login-reuse branch in {@link #advance} so a session that lost its
     *  continuation (defensive) re-logs in rather than firing a follow-on
     *  job against an empty session. */
    private static boolean hasLiveContinuation(NxisasScrapeSession s) {
        return s.getSasSessionID() != null && !s.getSasSessionID().isBlank();
    }

    private static GatewayContinuation continuationOf(NxisasScrapeSession s) {
        return new GatewayContinuation(
                s.getSasSessionID(), s.getGwSysNo(),
                s.getLbsNo(), s.getSsSysNo(), s.getThreadNo());
    }

    private static void storeContinuation(NxisasScrapeSession s, GatewayContinuation c) {
        s.setSasSessionID(c.sasSessionID());
        s.setGwSysNo(c.gwSysNo());
        s.setLbsNo(c.lbsNo());
        s.setSsSysNo(c.ssSysNo());
        s.setThreadNo(c.threadNo());
    }

    /** {@code NxisasScrapeSession.bankModule} is final-via-Lombok
     *  ({@code @RequiredArgsConstructor} declares it in the constructor
     *  but Lombok generates no setter). For SYNC mode we cross banks
     *  inside one batch, so we must overwrite it as the queue head
     *  changes. We can't introduce a public setter without breaking
     *  the SETUP flow's immutability contract — go through reflection
     *  for this one field. The cost is one access-check per LOGIN
     *  call which is far less than the network round-trip. */
    private static void setBankModuleViaReflection(NxisasScrapeSession session, String module) {
        try {
            java.lang.reflect.Field f = NxisasScrapeSession.class.getDeclaredField("bankModule");
            f.setAccessible(true);
            f.set(session, module);
        } catch (ReflectiveOperationException e) {
            throw new BusinessException("WBE9999");
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // Gateway response parsing helpers
    // ─────────────────────────────────────────────────────────────────

    private void assertOk(NxisasApiOut<?> out, String stage, String bankCd) {
        NxisasOutput<?> bankOut = gateway.firstOutput(out);
        if (bankOut != null) {
            String code = bankOut.getErrorCode();
            if (!NxisasResultCodes.isBankAdapterSuccess(code)) {
                log.info("BankTransactionSync {} rejected [bank={}, code={}, msg={}]",
                        stage, bankCd, code, bankOut.getErrorMessage());
                throw new BusinessException("WBE2095", bankCd, code);
            }
        }
        if (gatewayRejected(out)) {
            String code = out.getCommon().getResultCd();
            log.info("BankTransactionSync {} gateway-rejected [bank={}, code={}]",
                    stage, bankCd, code);
            throw new BusinessException("WBE2095", bankCd, code);
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

    /** OTP responses use {@link NxisasOTPJobResult}, but for the
     *  purpose of inspecting CallBackfunc / Output.ErrorCode the
     *  shape is identical to {@link NxisasLoginJobResult}. Rewrap the
     *  payload so the shared continuation handler can consume it. */
    @SuppressWarnings("unchecked")
    private static <T> List<com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasResult<T>>
            otpToLoginResults(NxisasApiOut<NxisasOTPJobResult> in) {
        // Coocon returns the same wire bytes for OTP / LOGIN result
        // entries; the typed wrapper differs only in the
        // Result<T> generic. We rebuild a fresh list with the LOGIN
        // result type so handleLoginContinuation can read it.
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

    private static boolean isPositive(String raw) {
        if (raw == null || raw.isBlank()) return false;
        try {
            BigDecimal v = parseDecimal(raw);
            return v != null && v.signum() != 0;
        } catch (RuntimeException e) {
            return false;
        }
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
