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

    /** Coocon gateway success — same convention used everywhere else in
     *  the NXISAS layer ({@code BankScrapingService.isGatewayError}). */
    private static final String OK_CODE = "00000000";

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
        final String fromDt = req.fromDt() == null
                ? startedAt.minusDays(30).format(DT_8)
                : req.fromDt().replace("-", "");
        final String toDt = req.toDt() == null
                ? startedAt.format(DT_8)
                : req.toDt().replace("-", "");

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
        for (TransactionsSyncRequest.AccountKey key : req.accounts()) {
            AcctMstr acct = acctRepo.findByBizKeyAndBankCdAndAcctNo(
                            bizKey, key.bankCd(), key.acctNo())
                    .orElseThrow(() -> new BusinessException("WBE_BANK_ACCOUNT_NOT_FOUND"));
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

        Deque<TransactionsSyncRequest.AccountKey> queue = new ArrayDeque<>(filtered);
        NxisasScrapeSession session = newSyncSession(queue, fromDt, toDt);
        return advance(session);
    }

    /** Resume after a QNA (security-question) answer. */
    @Transactional
    public TransactionsSyncResponse submitQna(UUID sessionId, SubmitQnaRequest req) {
        NxisasScrapeSession session = requireSession(sessionId);
        assertChallenge(session, ScrapeChallengeKind.QNA);

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

            // Configure the session for the HEAD account's bank.
            DecryptedCredential cred = credentialService.loadForScrapingByBank(acct.getBankCd());
            Integer subTypeSeqNo = cred.nxisasGbSeqNo() == null ? null
                    : cred.nxisasGbSeqNo().intValue();
            NxisasBankConfig.Entry cfg = bankConfig.find(acct.getBankCd(), subTypeSeqNo)
                    .orElseThrow(() -> new BusinessException("WBE2093", acct.getBankCd()));
            session.setBankCategory(cfg.category());

            // POST LOGIN.
            NxisasLoginJobInput in = new NxisasLoginJobInput();
            in.setLoginId(cred.username());
            in.setLoginPwd(cred.password());
            in.setCountryCode("VNM");
            in.setLoginType((cred.loginType() == null || cred.loginType().isBlank())
                    ? "ID" : cred.loginType());
            if (cred.corporateId() != null && !cred.corporateId().isBlank()) {
                in.setCorporateId(cred.corporateId());
                in.setLoginPwd1(cred.corporateId());
            }

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
            if (code != null && !code.isBlank() && !OK_CODE.equals(code)) {
                String msg = bankOut.getErrorMessage();
                if (msg == null || msg.isBlank()) msg = bankOut.getDetailMessage();
                log.info("BankTransactionSync {} bank-rejected [bank={}, code={}, msg={}]",
                        stage, session.getBankModule(), code, msg);
                session.setStatus(NxisasScrapeSession.Status.FAILED);
                registry.discard(session.getSessionId());
                return TransactionsSyncResponse.error(session.getSessionId(), code, msg);
            }
        }
        if (gatewayRejected(out)) {
            String code = out.getCommon().getResultCd();
            String msg = out.getCommon().getResultMsg();
            log.info("BankTransactionSync {} gateway-rejected [bank={}, code={}]",
                    stage, session.getBankModule(), code);
            session.setStatus(NxisasScrapeSession.Status.FAILED);
            registry.discard(session.getSessionId());
            return TransactionsSyncResponse.error(session.getSessionId(), code, msg);
        }

        NxisasLoginJobResult result = gateway.firstResult(out);
        ScrapeChallengeKind kind = challengeKindFrom(result);
        if (kind != null) {
            session.setStatus(NxisasScrapeSession.Status.AWAITING_CHALLENGE);
            session.setChallengeKind(kind);
            session.setChallengePrompt(result == null ? null : result.challengePrompt());
            registry.register(session);

            if (kind == ScrapeChallengeKind.QNA) {
                return TransactionsSyncResponse.qnaChallenge(
                        session.getSessionId(),
                        session.getChallengePrompt(),
                        qnaQuestionsFrom(result));
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
        processOneAccount(session, acct);
        session.getAccountsQueue().pollFirst();
        return advance(session);
    }

    /** Per-account TRANSACTION + BALANCE + persistence step. Called
     *  once LOGIN has cleared for the HEAD account. */
    private void processOneAccount(NxisasScrapeSession session, AcctMstr acct) {
        String nowDb = OffsetDateTime.now(ZoneOffset.UTC).format(DTM_14);
        String userId = session.getUserId();

        // TRANSACTION.
        NxisasTransactionJobInput tIn = new NxisasTransactionJobInput();
        tIn.setAcctNum(acct.getAcctNo());
        tIn.setCurrency(acct.getCcyCd());
        tIn.setStartDate(session.getFromDt());
        tIn.setEndDate(session.getToDt());
        NxisasBankConfig.Entry cfg = bankConfig.find(acct.getBankCd(), null)
                .orElseThrow(() -> new BusinessException("WBE2093", acct.getBankCd()));

        String trSeq = gateway.newTrSeq();
        NxisasApiOut<NxisasTransactionJobResult> tOut = gateway.transaction(
                cfg.module(), cfg.category(), trSeq, tIn, continuationOf(session));
        assertOk(tOut, "TRANSACTION", acct.getBankCd());
        NxisasTransactionJobResult tResult = gateway.firstTransactionResult(tOut);
        List<NxisasTransactionEntry> entries = tResult == null || tResult.getTransaction() == null
                ? List.of() : tResult.getTransaction();

        PersistResult pr = persistEntries(session.getBizKey(), userId, acct, entries, nowDb);
        session.setTotalDebitInserted(session.getTotalDebitInserted() + pr.debitCount);
        session.setTotalCreditInserted(session.getTotalCreditInserted() + pr.creditCount);
        session.setTotalDebitAmount(session.getTotalDebitAmount().add(pr.debitTotal));
        session.setTotalCreditAmount(session.getTotalCreditAmount().add(pr.creditTotal));

        // BALANCE (soft-fail).
        refreshBalance(session, acct);

        // Audit stamps.
        acct.setLstLgnSucDtm(nowDb);
        acct.setAcctBalLstInqDtm(nowDb);
        acct.setModId(userId);
        acct.setModDtm(nowDb);

        session.setAccountsSynced(session.getAccountsSynced() + 1);
    }

    /** Build the terminal {@code COMPLETED} response from the
     *  session's accumulators. */
    private TransactionsSyncResponse finalize(NxisasScrapeSession session) {
        session.setStatus(NxisasScrapeSession.Status.COMPLETED);
        registry.discard(session.getSessionId());
        String now = OffsetDateTime.now(ZoneOffset.UTC).format(DTM_DISPLAY);
        log.info("BankTransactionSync completed [bizKey={}, accounts={}, debit={}, credit={}]",
                session.getBizKey(), session.getAccountsSynced(),
                session.getTotalDebitInserted(), session.getTotalCreditInserted());
        return TransactionsSyncResponse.completed(
                session.getSessionId(),
                session.getAccountsSynced(),
                session.getTotalDebitInserted(),
                session.getTotalCreditInserted(),
                session.getTotalDebitAmount(),
                session.getTotalCreditAmount(),
                now);
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
            BigDecimal creditTotal) { }

    private PersistResult persistEntries(String bizKey, String userId, AcctMstr acct,
                                         List<NxisasTransactionEntry> entries, String nowDb) {
        int debit = 0;
        int credit = 0;
        BigDecimal debitSum  = BigDecimal.ZERO;
        BigDecimal creditSum = BigDecimal.ZERO;

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
            row.setAcctSeqNo(legacyFn.nextVal(
                    "acct_trns_hstr", "acct_seq_no", bizKey, null, null, null, null, null));
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
            row.setLockedYn("N");
            row.setRegId(userId);
            row.setRegDtm(nowDb);
            row.setModId(userId);
            row.setModDtm(nowDb);

            trnsRepo.save(row);
        }
        return new PersistResult(debit, credit, debitSum, creditSum);
    }

    private void refreshBalance(NxisasScrapeSession session, AcctMstr acct) {
        NxisasBankConfig.Entry cfg = bankConfig.find(acct.getBankCd(), null)
                .orElseThrow(() -> new BusinessException("WBE2093", acct.getBankCd()));
        NxisasBalanceJobInput in = new NxisasBalanceJobInput();
        in.setAcctNum(acct.getAcctNo());
        in.setCurrency(acct.getCcyCd());

        String trSeq = gateway.newTrSeq();
        NxisasApiOut<NxisasBalanceJobResult> out = gateway.balance(
                cfg.module(), cfg.category(), trSeq, in, continuationOf(session));

        NxisasOutput<NxisasBalanceJobResult> bankOut = gateway.firstOutput(out);
        if (bankOut == null || !OK_CODE.equals(bankOut.getErrorCode())) {
            log.warn("BankTransactionSync BALANCE soft-failed [bank={}, acct={}, code={}]",
                    acct.getBankCd(), acct.getAcctNo(),
                    bankOut == null ? "null" : bankOut.getErrorCode());
            return;
        }
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
            Deque<TransactionsSyncRequest.AccountKey> queue, String fromDt, String toDt) {
        String bizKey = requireBizKey();
        String userId = TenantContext.currentUserId();
        // bankModule is per-account (set inside advance()) — start
        // empty; the first iteration writes the correct value.
        String trSeq = gateway.newTrSeq();
        NxisasScrapeSession session = new NxisasScrapeSession(
                trSeq, bizKey, userId, null, "");
        session.setMode(NxisasScrapeSession.Mode.SYNC);
        session.setAccountsQueue(queue);
        session.setFromDt(fromDt);
        session.setToDt(toDt);
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
            if (code != null && !code.isBlank() && !OK_CODE.equals(code)) {
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
        String code = out.getCommon().getResultCd();
        if (code == null || code.isBlank()) return false;
        return !OK_CODE.equals(code) && !"0000".equalsIgnoreCase(code) && !"OK".equalsIgnoreCase(code);
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
