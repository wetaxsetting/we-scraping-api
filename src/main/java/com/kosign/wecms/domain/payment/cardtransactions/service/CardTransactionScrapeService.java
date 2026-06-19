package com.kosign.wecms.domain.payment.cardtransactions.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.kosign.wecms.common.auditlog.service.AuditLogContext;
import com.kosign.wecms.common.auditlog.service.AuditLogService;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.banking.bankcredentials.service.BankCredentialService;
import com.kosign.wecms.domain.banking.bankcredentials.service.BankCredentialService.DecryptedCredential;
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
import com.kosign.wecms.domain.banking.repository.BankInfmRepository;
import com.kosign.wecms.domain.payment.cardtransactions.dto.CardSyncSessionResponse;
import com.kosign.wecms.domain.payment.cardtransactions.dto.SyncCardTransactionsRequest;
import com.kosign.wecms.domain.payment.cardtransactions.entity.CardMstr;
import com.kosign.wecms.domain.payment.cardtransactions.repository.CardMstrRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

/**
 * Card Transactions &gt; Update orchestrator — the session/challenge-aware
 * card-scrape flow fronted by the Update Cards popup.
 *
 * <p>The card twin of {@code BankTransactionSyncService}: it drives the
 * per-card {@code LOGIN → (QNA/OTP/CAPTCHA?) → CARD_USING} chain over the
 * <b>same</b> {@link NxisasScrapeSession} / {@link NxisasSessionRegistry}
 * / {@link NxisasGatewayClient} infrastructure the Account Inquiry sync,
 * Login Credentials verify, and Add-Card flows use. It introduces no new
 * bank-login system — only a new {@code Mode.CARD_SYNC} post-login job
 * ({@code CARD_USING}) and a card-specific persist target
 * ({@code CARD_TRNS_HSTR} via {@link CardTransactionPersister}).
 *
 * <p>This fixes the audit's CONFIRMED_DEFECT: the legacy headless
 * {@code CardTransactionSyncService} aborted QNA-gated banks (Shinhan
 * 11704088) with {@code WBE_CARD_SYNC_NEEDS_RE_AUTH}; this flow pumps the
 * QNA / OTP / CAPTCHA challenge through the existing
 * {@code submit*}-resume pattern and then runs {@code CARD_USING}.
 *
 * <p><b>Credential selection (audit requirement #6):</b> a card is
 * scraped under exactly the credential its {@code CARD_MSTR.BANK_CERT_SEQ}
 * points at. There is NO lowest-{@code SEQ_NO} fallback — a card whose
 * {@code BANK_CERT_SEQ} is null is excluded from the queue; if no card
 * has a credential the start returns {@code WBE_CARD_SYNC_NO_CREDENTIAL}
 * (re-register the card).
 *
 * <p><b>Session lifecycle (audit requirement #8):</b> same as Account
 * Inquiry sync — the session is discarded from the registry on terminal
 * COMPLETED/ERROR and idle-evicted otherwise. No card-specific LOGOUT
 * job (the reused flow has none).
 */
@Slf4j
@Service
@RequiredArgsConstructor
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

    private final CardMstrRepository         cardMstrRepo;
    private final BankCredentialService      credentialService;
    private final NxisasGatewayClient        gateway;
    private final NxisasBankConfig           bankConfig;
    private final NxisasSessionRegistry      registry;
    private final CardTransactionPersister   cardPersister;
    private final AuditLogService            auditLogService;
    /** Bank-code → display-name resolver for the challenge dialog header
     *  (so the Security Question popup shows "Shinhan VN", not a code). */
    private final BankInfmRepository         bankInfmRepo;

    // ─────────────────────────────────────────────────────────────────
    // Public entry points (CardTransactionsController)
    // ─────────────────────────────────────────────────────────────────

    /** Start a Card Update scrape session. Drains the tenant's eligible
     *  cards through the per-credential LOGIN/CARD_USING loop until the
     *  queue empties (→ COMPLETED) or a LOGIN stops on a challenge
     *  (→ {@code *_REQUIRED}). */
    @Transactional
    public CardSyncSessionResponse start(SyncCardTransactionsRequest req) {
        if (req == null) throw new BusinessException("WBE_VALIDATION");
        String bizKey = requireBizKey();
        String userId = TenantContext.currentUserId();
        String now    = OffsetDateTime.now(ZoneOffset.UTC).format(DTM_14);

        List<CardMstr> cards = cardMstrRepo.findActiveCardsForSync(bizKey);
        if (cards.isEmpty()) {
            return CardSyncSessionResponse.completed(null, 0, 0, 0);
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

        // Credential lookup — CARD_MSTR.BANK_CERT_SEQ ONLY, exactly the
        // way Account Inquiry keys off ACCT_MSTR.BANK_CERT_SEQ. There is
        // NO fallback to the lowest BANK_CERT_INFO.SEQ_NO. A card whose
        // BANK_CERT_SEQ is null (orphan from the old Add-Card bug) or
        // invalid (points at a missing / cross-tenant credential row) is
        // NOT scraped — we fail fast with a clear "re-register" error and
        // never run a partial scrape.
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
        // Validate every referenced credential resolves BEFORE any LOGIN,
        // so an INVALID BANK_CERT_SEQ (no BANK_CERT_INFO row / wrong tenant)
        // surfaces the same clear "re-register" error instead of a partial
        // scrape or a confusing low-level credential-not-found code.
        for (var seq : eligible.stream().map(CardScrapeKey::bankCertSeq).distinct().toList()) {
            try {
                credentialService.loadForScraping(seq);
            } catch (RuntimeException ex) {
                log.warn("card-update :: BANK_CERT_SEQ {} does not resolve to a credential — re-register required", seq);
                throw new BusinessException("WBE_CARD_SYNC_NO_CREDENTIAL");
            }
        }

        // Order so cards sharing a credential (BANK_CERT_SEQ) are
        // contiguous — lets advance() reuse one LOGIN per credential group
        // (legacy login-once-per-bank parity).
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

        NxisasApiLog.event(tid(session), FLOW, "START",
                "cards", eligible.size(), "groups", grouped.size(),
                "from", fromDt, "to", toDt);
        return advance(session, userId, now);
    }

    /** Resume after a QNA (security-question) answer. */
    @Transactional
    public CardSyncSessionResponse submitQna(UUID sessionId, SubmitQnaRequest req) {
        NxisasScrapeSession session = requireSession(sessionId);
        assertChallenge(session, ScrapeChallengeKind.QNA);
        NxisasApiLog.event(tid(session), FLOW, "CHALLENGE_SUBMIT", "type", "QNA");

        NxisasQnaJobInput.QnaResponse res = new NxisasQnaJobInput.QnaResponse();
        res.setQuestion(req.question());
        res.setHiddenValue(req.hiddenValue());
        res.setAnswer(req.answer());
        NxisasQnaJobInput input = new NxisasQnaJobInput();
        input.setRes(res);

        NxisasApiOut<NxisasLoginJobResult> out = gateway.submitQna(
                session.getBankModule(), session.getBankCategory(), gateway.newTrSeq(),
                input, continuationOf(session));
        storeContinuation(session, GatewayContinuation.from(out));
        return handleLoginContinuation(session, out, "QNA");
    }

    /** Resume after an OTP submission. */
    @Transactional
    public CardSyncSessionResponse submitOtp(UUID sessionId, SubmitChallengeRequest req) {
        NxisasScrapeSession session = requireSession(sessionId);
        assertChallenge(session, ScrapeChallengeKind.OTP);
        NxisasApiLog.event(tid(session), FLOW, "CHALLENGE_SUBMIT", "type", "OTP");

        NxisasOTPJobInput input = new NxisasOTPJobInput();
        input.setRes(req.value());

        NxisasApiOut<NxisasOTPJobResult> out = gateway.submitOtp(
                session.getBankModule(), session.getBankCategory(), gateway.newTrSeq(),
                input, continuationOf(session));
        storeContinuation(session, GatewayContinuation.from(out));

        // OTP responses share the LoginJobResult wire shape — rewrap.
        NxisasApiOut<NxisasLoginJobResult> wrap = new NxisasApiOut<>();
        wrap.setCommon(out.getCommon());
        wrap.setResultList(otpToLoginResults(out));
        wrap.setSasSessionID(out.getSasSessionID());
        return handleLoginContinuation(session, wrap, "OTP");
    }

    /** Resume after a CAPTCHA submission. */
    @Transactional
    public CardSyncSessionResponse submitCaptcha(UUID sessionId, SubmitChallengeRequest req) {
        NxisasScrapeSession session = requireSession(sessionId);
        assertChallenge(session, ScrapeChallengeKind.CAPTCHA);
        NxisasApiLog.event(tid(session), FLOW, "CHALLENGE_SUBMIT", "type", "CAPTCHA");

        NxisasCaptchaJobInput input = new NxisasCaptchaJobInput();
        input.setRes(req.value());

        NxisasApiOut<NxisasLoginJobResult> out = gateway.submitCaptcha(
                session.getBankModule(), session.getBankCategory(), gateway.newTrSeq(),
                input, continuationOf(session));
        storeContinuation(session, GatewayContinuation.from(out));
        return handleLoginContinuation(session, out, "CAPTCHA");
    }

    // ─────────────────────────────────────────────────────────────────
    // Core flow
    // ─────────────────────────────────────────────────────────────────

    /** Drive the card queue forward — for each pending card, LOGIN
     *  (reusing the live session for same-credential cards); on a clean
     *  login run CARD_USING + persist; on a challenge persist state and
     *  return the {@code *_REQUIRED} response. */
    private CardSyncSessionResponse advance(NxisasScrapeSession session, String userId, String now) {
        while (!session.getCardsQueue().isEmpty()) {
            CardScrapeKey key = session.getCardsQueue().peekFirst();

            // Login-once-per-credential reuse — same credential group as
            // the live LOGIN and the continuation is still good → skip
            // LOGIN, go straight to CARD_USING.
            if (loginGroupKey(key).equals(session.getActiveLoginGroupKey())
                    && hasLiveContinuation(session)) {
                NxisasApiLog.event(tid(session), FLOW, "SESSION_REUSE",
                        "bankCd", key.bankCd(), "cardNo", NxisasMask.card(key.cardNo()));
                processOneCard(session, key, userId, now);
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

            NxisasApiLog.event(tid(session), FLOW, "LOGIN_START",
                    "bankCd", key.bankCd(), "module", cfg.module(), "class", cfg.category());
            NxisasApiOut<NxisasLoginJobResult> out =
                    gateway.login(cfg.module(), cfg.category(), gateway.newTrSeq(), in);
            storeContinuation(session, GatewayContinuation.from(out));

            // handleLoginContinuation returns a challenge / error directly,
            // or — on a clean login — runs CARD_USING for the head card and
            // recurses through advance() to drain the rest, returning the
            // terminal COMPLETED. Either way its result is this batch's
            // result; the queue head waits for a resume call on a challenge.
            return handleLoginContinuation(session, out, "LOGIN");
        }
        return finalize(session, now);
    }

    /** Inspect a LOGIN-shape response (initial LOGIN or a QNA/OTP/CAPTCHA
     *  resume). Bank/gateway error → terminal ERROR; challenge → persist +
     *  {@code *_REQUIRED}; clean → CARD_USING the head card, pop, recurse. */
    private CardSyncSessionResponse handleLoginContinuation(
            NxisasScrapeSession session, NxisasApiOut<NxisasLoginJobResult> out, String stage) {

        String userId = session.getUserId();
        String now    = OffsetDateTime.now(ZoneOffset.UTC).format(DTM_14);

        NxisasOutput<?> bankOut = gateway.firstOutput(out);
        if (bankOut != null && !NxisasResultCodes.isBankAdapterSuccess(bankOut.getErrorCode())) {
            String code = bankOut.getErrorCode();
            String msg  = firstNonBlank(bankOut.getErrorMessage(), bankOut.getDetailMessage());
            NxisasApiLog.event(tid(session), FLOW, "ERROR",
                    "stage", stage, "scope", "BANK", "errorCode", code);
            session.setStatus(NxisasScrapeSession.Status.FAILED);
            registry.discard(session.getSessionId());
            return CardSyncSessionResponse.error(session.getSessionId(), code, msg);
        }
        if (gatewayRejected(out)) {
            String code = out.getCommon().getResultCd();
            String msg  = out.getCommon().getResultMsg();
            NxisasApiLog.event(tid(session), FLOW, "ERROR",
                    "stage", stage, "scope", "GATEWAY", "resultCode", code);
            session.setStatus(NxisasScrapeSession.Status.FAILED);
            registry.discard(session.getSessionId());
            return CardSyncSessionResponse.error(session.getSessionId(), code, msg);
        }

        NxisasLoginJobResult result = gateway.firstResult(out);
        ScrapeChallengeKind kind = challengeKindFrom(result);
        if (kind != null) {
            session.setStatus(NxisasScrapeSession.Status.AWAITING_CHALLENGE);
            session.setChallengeKind(kind);
            session.setChallengePrompt(result == null ? null : result.challengePrompt());
            registry.register(session);
            CardScrapeKey head = session.getCardsQueue().peekFirst();
            String bankName = resolveBankName(head == null ? null : head.bankCd());
            NxisasApiLog.event(tid(session), FLOW, "CHALLENGE_REQUIRED",
                    "type", kind, "bankCd", head == null ? "-" : head.bankCd());
            if (kind == ScrapeChallengeKind.QNA) {
                return CardSyncSessionResponse.qnaChallenge(
                        session.getSessionId(), session.getChallengePrompt(),
                        qnaQuestionsFrom(result), session.getLoginId(), bankName);
            }
            if (kind == ScrapeChallengeKind.CAPTCHA) {
                return CardSyncSessionResponse.captchaChallenge(
                        session.getSessionId(), session.getChallengePrompt(),
                        captchaImageFrom(result), captchaImageFormatFor(session.getBankModule()), bankName);
            }
            return CardSyncSessionResponse.otpChallenge(
                    session.getSessionId(), session.getChallengePrompt(), bankName);
        }

        // Clean login — this credential group now owns the session.
        CardScrapeKey key = session.getCardsQueue().peekFirst();
        session.setActiveLoginGroupKey(loginGroupKey(key));
        NxisasApiLog.event(tid(session), FLOW, "LOGIN_SUCCESS",
                "stage", stage, "bankCd", key.bankCd(),
                "sasSessionId", NxisasMask.token(session.getSasSessionID()));
        processOneCard(session, key, userId, now);
        session.getCardsQueue().pollFirst();
        return advance(session, userId, now);
    }

    /** CARD_USING + persist for one card. Fail-soft: a CARD_USING-level
     *  gateway/bank error logs + skips that card (no rows, not counted)
     *  rather than aborting the whole batch — mirrors the legacy
     *  per-card error collection in {@code fnsh_0001_001_c001_act.jsp}. */
    private void processOneCard(NxisasScrapeSession session, CardScrapeKey key, String userId, String now) {
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

        NxisasOutput<?> bankOut = gateway.firstOutput(out);
        boolean bankOk = bankOut == null || NxisasResultCodes.isBankAdapterSuccess(bankOut.getErrorCode());
        if (!bankOk || gatewayRejected(out)) {
            String code = !bankOk ? bankOut.getErrorCode() : out.getCommon().getResultCd();
            NxisasApiLog.event(tid(session), FLOW, "ERROR",
                    "stage", "CARD_USING", "scope", bankOk ? "GATEWAY" : "BANK",
                    "bankCd", key.bankCd(), "cardNo", NxisasMask.card(key.cardNo()), "code", code);
            log.warn("card-update :: CARD_USING failed bankCd={} cardNo={} code={} — skipping card",
                    key.bankCd(), NxisasMask.card(key.cardNo()), code);
            return;
        }

        NxisasCardUsingJobResult res = gateway.firstCardUsingResult(out);
        List<NxisasCardUsingEntry> scraped;
        if (res == null || res.getCardArr() == null || res.getCardArr().isEmpty()
                || res.getCardArr().get(0).getCardUsing() == null) {
            scraped = List.of();
        } else {
            scraped = res.getCardArr().get(0).getCardUsing();
        }

        // Reload the managed card so the persister has the registry CARD_NM
        // fallback + so we can stamp TRN_LST_SUC_DTM (the queue carries
        // keys, not entities, so they survive challenge resumes).
        CardMstr card = new CardMstr();
        card.setBizKey(session.getBizKey());
        card.setBankCd(key.bankCd());
        card.setCardNo(key.cardNo());

        int[] counts = cardPersister.persist(session.getBizKey(), userId, now, card, scraped);
        session.setCardRowsInserted(session.getCardRowsInserted() + counts[0]);
        session.setCardRowsSkipped(session.getCardRowsSkipped() + counts[1]);
        session.setCardsSynced(session.getCardsSynced() + 1);

        // Advance the per-card cursor only when the scrape actually
        // returned rows (legacy fnsh_0001_001_c001_act.jsp:1413/1422 —
        // an empty result holds the cursor so tomorrow re-checks today).
        if (!scraped.isEmpty()) {
            cardMstrRepo.patchTrnLstSucDtm(session.getBizKey(), key.bankCd(), key.cardNo(), now, userId, now);
        }
        NxisasApiLog.event(tid(session), FLOW, "CARD_DONE",
                "bankCd", key.bankCd(), "cardNo", NxisasMask.card(key.cardNo()),
                "scraped", scraped.size(), "inserted", counts[0], "skipped", counts[1]);
    }

    private CardSyncSessionResponse finalize(NxisasScrapeSession session, String now) {
        session.setStatus(NxisasScrapeSession.Status.COMPLETED);
        registry.discard(session.getSessionId());
        auditLogService.append(AuditLogContext.builder()
                .wsvcId(WSVC_ID)
                .refNo(REF_NO)
                .activityKey(AUDIT_ACTIVITY)
                .detailKey(AUDIT_SYNC_DETAIL)
                .msgArgs(new Object[] {
                        session.getCardsSynced(), session.getCardsSynced(),
                        session.getCardRowsInserted(), session.getCardRowsSkipped(),
                })
                .build());
        NxisasApiLog.event(tid(session), FLOW, "COMPLETE",
                "cards", session.getCardsSynced(),
                "inserted", session.getCardRowsInserted(),
                "skipped", session.getCardRowsSkipped());
        return CardSyncSessionResponse.completed(
                session.getSessionId(), session.getCardsSynced(),
                session.getCardRowsInserted(), session.getCardRowsSkipped());
    }

    // ─────────────────────────────────────────────────────────────────
    // Session + parsing helpers (duplicated per-orchestrator, mirroring
    // BankTransactionSyncService — the established codebase pattern).
    // ─────────────────────────────────────────────────────────────────

    /** Login-group key — the credential (BANK_CERT_SEQ). All cards under
     *  one credential reuse a single LOGIN. */
    private static String loginGroupKey(CardScrapeKey key) {
        return key.bankCertSeq() == null ? "" : key.bankCertSeq().toPlainString();
    }

    /** Resolve a bank code to its display name (e.g. {@code "Shinhan VN"})
     *  for the challenge dialog header — falls back to the code, then null.
     *  Same lookup the headless {@code CardTransactionSyncService} uses. */
    private String resolveBankName(String bankCd) {
        if (bankCd == null) return null;
        return bankInfmRepo.findById(bankCd)
                .map(b -> b.getRprsBankNm() == null ? bankCd : b.getRprsBankNm())
                .orElse(bankCd);
    }

    private NxisasScrapeSession requireSession(UUID sessionId) {
        String bizKey = requireBizKey();
        String userId = TenantContext.currentUserId();
        NxisasScrapeSession s = registry.find(sessionId, bizKey, userId)
                .orElseThrow(() -> new BusinessException("WBE2060"));
        if (s.getMode() != NxisasScrapeSession.Mode.CARD_SYNC) {
            // A non-card session id was passed to the card resume endpoint.
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

    /** {@code bankModule} is final-via-Lombok on the session; CARD_SYNC
     *  crosses credentials within one batch, so overwrite it per group
     *  via reflection (same one-field workaround as
     *  {@code BankTransactionSyncService}). */
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
