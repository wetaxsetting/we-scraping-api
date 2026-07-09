package com.kosign.wecms.domain.banking.nxisas;

import com.fasterxml.jackson.databind.JsonNode;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.banking.bankcredentials.service.BankCredentialService;
import com.kosign.wecms.domain.banking.bankcredentials.service.BankCredentialService.DecryptedCredential;
import com.kosign.wecms.domain.banking.nxisas.dto.QnaQuestion;
import com.kosign.wecms.domain.banking.nxisas.dto.ScrapeSessionResponse;
import com.kosign.wecms.domain.banking.nxisas.dto.ScrapedAccount;
import com.kosign.wecms.domain.banking.nxisas.dto.ScrapedCard;
import com.kosign.wecms.domain.banking.nxisas.dto.StartScrapeRequest;
import com.kosign.wecms.domain.banking.nxisas.dto.SubmitChallengeRequest;
import com.kosign.wecms.domain.banking.nxisas.dto.SubmitQnaRequest;
import com.kosign.wecms.domain.banking.nxisas.dto.VerifyCredentialsRequest;
import com.kosign.wecms.domain.banking.nxisas.NxisasGatewayClient.GatewayContinuation;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasAcctListJobInput;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasAcctListJobResult;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasApiOut;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasLoginJobInput;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasLoginJobResult;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasCaptchaJobInput;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasOTPJobInput;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasOTPJobResult;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasQnaJobInput;
import com.kosign.wecms.domain.banking.nxisas.session.NxisasScrapeSession;
import com.kosign.wecms.domain.banking.nxisas.session.NxisasSessionRegistry;
import com.kosign.wecms.domain.banking.nxisas.session.ScrapeChallengeKind;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Orchestrates the multi-step NXISAS scraping flow. Public surface:
 *  - {@link #start(StartScrapeRequest)} — pulls plaintext credential,
 *    POSTs LOGIN, returns the next step
 *  - {@link #submitOtp(UUID, SubmitChallengeRequest)} — POSTs OTP,
 *    then POSTs ACCT_LIST when login completes
 *
 * <p>State + transitions live on {@link NxisasScrapeSession}; this
 * service owns the gateway calls and the result-to-response mapping.
 *
 * <p>Spike scope: only the LOGIN → OTP → ACCT_LIST path is wired.
 * Captcha and QnA are returned as `XXX_REQUIRED` but not yet
 * resumable — Step 4 of the plan calls out the VCB-only narrow scope.
 */
@Service
@RequiredArgsConstructor
public class BankScrapingService {

    private static final Logger log = LoggerFactory.getLogger(BankScrapingService.class);

    private final BankCredentialService credentialService;
    private final NxisasGatewayClient gateway;
    private final NxisasSessionRegistry registry;
    private final NxisasBankConfig bankConfig;
    private final NxisasAcctTypeMappingRepository acctTypeMappingRepo;

    public ScrapeSessionResponse start(StartScrapeRequest req) {
        DecryptedCredential cred = credentialService.loadForScraping(req.credentialSeqNo());
        if (cred.bankCd() == null || cred.bankCd().isBlank()) {
            throw new BusinessException("WBE2051");
        }
        return runLogin(
                cred.bankCd(),
                cred.username(),
                cred.password(),
                cred.corporateId(),
                cred.loginType(),
                cred.nxisasGbSeqNo(),
                req.credentialSeqNo(),
                NxisasScrapeSession.Mode.SETUP);
    }

    /**
     * Inline-credentials verify path used by the Login Credentials
     * "Login" button BEFORE the row is persisted. Mirrors the legacy
     * {@code enter_login_popup} flow at
     * {@code WECMS_STATIC/ts-src/js/jex/wabooks/comm/comm_0218_nxisas.ts:508}
     * where Coocon LOGIN + ACCT_LIST runs first and the user only
     * commits {@code BANK_CERT_INFO} after picking accounts from the
     * scrape result.
     *
     * <p>No {@code credentialSeqNo} is bound to the session because no
     * row exists yet — the persistence call lives on
     * {@code POST /api/v1/login-credentials/bank} and runs after the
     * user's account selection.
     */
    public ScrapeSessionResponse verify(VerifyCredentialsRequest req) {
        // Resolve the post-login flow from the optional scrape_kind hint.
        // The Card Management modal sends "CARD_INFO" to register cards
        // (LOGIN + CARD_INFO); everything else defaults to ACCT_LIST so
        // the existing Login Credentials verify-before-save flow keeps
        // working unchanged.
        NxisasScrapeSession.Mode mode =
                "CARD_INFO".equalsIgnoreCase(req.scrapeKind())
                        ? NxisasScrapeSession.Mode.CARD_REGISTER
                        : NxisasScrapeSession.Mode.SETUP;
        return runLogin(
                req.bankCd(),
                req.loginId(),
                req.password(),
                req.extra(),
                req.loginType(),
                req.nxisasGbSeqNo(),
                null,
                mode);
    }

    /** Shared LOGIN → (challenge | post-login job) path. Both
     *  {@link #start(StartScrapeRequest)} and {@link #verify(VerifyCredentialsRequest)}
     *  funnel here once they have plaintext credentials in hand. The
     *  {@code mode} parameter selects which post-login Coocon job
     *  dispatches when the bank accepts the login outright (no
     *  challenge): SETUP → ACCT_LIST, CARD_REGISTER → CARD_INFO,
     *  SYNC → owned by BankTransactionSyncService. */
    /** Map the scrape session mode to the shared NXISAS debug flow label
     *  (see {@link NxisasApiLog}): SETUP → BANK_SETUP (Login Credentials
     *  verify), CARD_REGISTER → CARD_MANAGEMENT_SETTING (Card Management
     *  add-card), SYNC → BANK_SYNC (owned by BankTransactionSyncService,
     *  not reached here). */
    private static String flowForMode(NxisasScrapeSession session) {
        return switch (session.getMode()) {
            case CARD_REGISTER -> NxisasApiLog.FLOW_CARD_MANAGEMENT_SETTING;
            case SYNC -> NxisasApiLog.FLOW_BANK_SYNC;
            case SETUP -> NxisasApiLog.FLOW_BANK_SETUP;
            // CARD_SYNC sessions are owned by CardTransactionScrapeService
            // and never reach this service; map it for switch-exhaustiveness.
            case CARD_SYNC -> NxisasApiLog.FLOW_CARD_UPDATE_TRANSACTIONS;
        };
    }

    private ScrapeSessionResponse runLogin(String bankCd, String username, String password,
                                           String corporateId, String loginType,
                                           java.math.BigDecimal nxisasGbSeqNo,
                                           java.math.BigDecimal credentialSeqNo,
                                           NxisasScrapeSession.Mode mode) {
        // Resolve the Coocon adapter Module + Class
        // (CORPORATE / COMMON / PERSONAL / DIGIBIZ / …) from the
        // per-bank config — sub-type aware so the picker's
        // nxisas_gb_seq_no radio dispatches Vietcom's DigiBiz path,
        // BIDV's Personal path, etc. Falls back to the SEQ_NO=0
        // default when no per-sub-type override is registered.
        // Coocon's gateway rejects threads with a missing/wrong
        // Module or empty Class.
        Integer seqNoInt = nxisasGbSeqNo == null ? null : nxisasGbSeqNo.intValue();
        NxisasBankConfig.Entry cfg = bankConfig.find(bankCd, seqNoInt)
                .orElseThrow(() -> new BusinessException("WBE2093", bankCd));

        String bizKey = TenantContext.currentBizKey();
        String userId = TenantContext.currentUserId();
        String trSeq = gateway.newTrSeq();
        String bankModule = cfg.module();

        NxisasScrapeSession session = new NxisasScrapeSession(
                trSeq, bizKey, userId, credentialSeqNo, bankModule, bankCd);
        session.setBankCategory(cfg.category());
        session.setMode(mode);

        NxisasLoginJobInput loginInput = new NxisasLoginJobInput();
        loginInput.setLoginId(username);
        loginInput.setLoginPwd(password);
        // Coocon's Vietnam adapters expect ISO-3 "VNM", not ISO-2 "VN".
        // Sending "VN" returns 80010209 ("Incorrect Country Code") at the
        // bank-adapter level. Legacy comm_0218_nxisas.ts:512 hardcodes
        // "VNM" for the same reason.
        loginInput.setCountryCode("VNM");
        // loginType default — an explicit value wins; when blank, ABBANK
        // sends "" while every other bank defaults to "ID". Centralised in
        // NxisasBankConfig so this path and BankTransactionSyncService stay
        // in lockstep (legacy nxisas_fnsh_r001_act.jsp:148-157).
        loginInput.setLoginType(NxisasBankConfig.defaultLoginType(bankModule, loginType));
        // CERT_INFO_DETL_CON2 in legacy maps to loginPWD1 for banks that
        // use a second password field (FNSH_0100_001:214-216). The same
        // column also carries Agribank's corporate ID — send it on both
        // wire fields: Coocon ignores the one that isn't applicable.
        if (corporateId != null && !corporateId.isBlank()) {
            loginInput.setCorporateId(corporateId);
            loginInput.setLoginPwd1(corporateId);
        }
        // Sub-type selector — Coocon's adapter routes Corporate
        // (seq 0), Personal (seq 1), VCB DigiBiz (seq 2), etc. via
        // the bankModule + nxisasGbSeqNo pair. Persist the
        // discriminator into the session so the resumed OTP /
        // ACCT_LIST steps don't lose it.
        if (nxisasGbSeqNo != null) {
            session.setNxisasGbSeqNo(nxisasGbSeqNo.intValue());
        }

        NxisasApiLog.event(session.getSessionId().toString(), flowForMode(session), "LOGIN_START",
                "bankCd", bankCd, "module", bankModule, "class", session.getBankCategory());
        // Card Management add-card scrapes only Shinhan + Woori cards —
        // surface an out-of-scope bank picked in the add-card flow.
        if (session.getMode() == NxisasScrapeSession.Mode.CARD_REGISTER) {
            boolean cardBankSupported = NxisasCardBanks.isSupported(bankCd);
            NxisasApiLog.event(session.getSessionId().toString(), flowForMode(session), "CARD_BANK_SCOPE",
                    "bankCd", bankCd, "cardBank", NxisasCardBanks.label(bankCd),
                    "cardBankSupported", cardBankSupported);
            if (!cardBankSupported) {
                log.warn("NXISAS card-register on UNSUPPORTED card bank bankCd={} (only Shinhan/Woori supported)", bankCd);
            }
        }
        NxisasApiOut<NxisasLoginJobResult> out = gateway.login(bankModule, session.getBankCategory(), trSeq, loginInput);
        NxisasLoginJobResult result = gateway.firstResult(out);

        // Capture the gateway continuation state from the LOGIN response
        // (FNSH_0100_001:249-254). These values must be echoed back on
        // every subsequent call so the gateway routes them to the same
        // bank-adapter session.
        storeContinuation(session, GatewayContinuation.from(out));

        // Bank-adapter status lives on Output.ErrorCode, NOT on the
        // gateway envelope's Common.Result_cd. Common.Result_cd="00000000"
        // means "gateway routed the call successfully" — the bank adapter
        // can still reject the credentials / country code / etc. with
        // its own non-zero ErrorCode (e.g. 80010011 wrong login, 80010209
        // wrong country code). Surface those as ERROR before attempting
        // any continuation call — the SAS_SessionID returned alongside a
        // rejected LOGIN is invalid by Coocon's definition.
        var bankOutput = gateway.firstOutput(out);
        String bankErrCode = bankOutput == null ? null : bankOutput.getErrorCode();
        if (bankErrCode != null && !bankErrCode.isBlank()
                && !"00000000".equals(bankErrCode)) {
            String bankErrMsg = bankOutput.getErrorMessage();
            if (bankErrMsg == null || bankErrMsg.isBlank()) {
                bankErrMsg = bankOutput.getDetailMessage();
            }
            log.info("NXISAS LOGIN rejected by bank adapter [bank={}, code={}, msg={}]",
                    bankModule, bankErrCode, bankErrMsg);
            NxisasApiLog.event(session.getSessionId().toString(), flowForMode(session), "ERROR",
                    "stage", "LOGIN", "scope", "BANK", "bankCd", bankCd, "errorCode", bankErrCode);
            return ScrapeSessionResponse.error(session.getSessionId(), bankErrCode, bankErrMsg);
        }

        ScrapeChallengeKind kind = challengeKindFrom(result);
        if (kind != null) {
            session.setStatus(NxisasScrapeSession.Status.AWAITING_CHALLENGE);
            session.setChallengeKind(kind);
            session.setChallengePrompt(result == null ? null : result.challengePrompt());
            registry.register(session);
            NxisasApiLog.event(session.getSessionId().toString(), flowForMode(session),
                    "CHALLENGE_REQUIRED", "type", kind, "bankCd", bankCd);
            if (kind == ScrapeChallengeKind.QNA) {
                List<QnaQuestion> questions = qnaQuestionsFrom(result);
                return ScrapeSessionResponse.qnaChallenge(
                        session.getSessionId(),
                        session.getChallengePrompt(),
                        questions);
            }
            if (kind == ScrapeChallengeKind.CAPTCHA) {
                String image = captchaImageFrom(result);
                String format = captchaImageFormatFor(bankCd);
                return ScrapeSessionResponse.captchaChallenge(
                        session.getSessionId(),
                        session.getChallengePrompt(),
                        image,
                        format);
            }
            return ScrapeSessionResponse.challenge(
                    session.getSessionId(),
                    statusForChallenge(kind),
                    session.getChallengePrompt());
        }

        // No challenge: gateway accepted the login outright. Proceed
        // to the per-mode post-login job (ACCT_LIST / CARD_INFO).
        NxisasApiLog.event(session.getSessionId().toString(), flowForMode(session), "LOGIN_SUCCESS",
                "bankCd", bankCd, "sasSessionId", NxisasMask.token(session.getSasSessionID()));
        return proceedAfterLogin(session);
    }

    public ScrapeSessionResponse submitOtp(UUID sessionId, SubmitChallengeRequest req) {
        String bizKey = TenantContext.currentBizKey();
        String userId = TenantContext.currentUserId();
        NxisasScrapeSession session = registry.find(sessionId, bizKey, userId)
                .orElseThrow(() -> new BusinessException("WBE2060"));
        session.touch();

        if (session.getChallengeKind() != ScrapeChallengeKind.OTP) {
            // Only OTP is wired in the spike; surface a clear error
            // rather than mis-routing a CAPTCHA / QNA submission.
            throw new BusinessException("WBE9001");
        }

        NxisasApiLog.event(session.getSessionId().toString(), flowForMode(session),
                "CHALLENGE_SUBMIT", "type", "OTP");
        NxisasOTPJobInput otpInput = new NxisasOTPJobInput();
        otpInput.setRes(req.value());

        // Fresh Tr_seq per call — see runAcctList for the legacy-parity rationale.
        String trSeq = gateway.newTrSeq();
        NxisasApiOut<NxisasOTPJobResult> otpOut = gateway.submitOtp(
                session.getBankModule(), session.getBankCategory(), trSeq,
                otpInput, sessionContinuation(session));

        // Update the continuation with whatever the OTP response returned —
        // the gateway may rotate Gw_sys_no / Thread_no on the OTP step.
        storeContinuation(session, GatewayContinuation.from(otpOut));

        String resultCd = otpOut == null || otpOut.getCommon() == null
                ? null : otpOut.getCommon().getResultCd();
        String resultMsg = otpOut == null || otpOut.getCommon() == null
                ? null : otpOut.getCommon().getResultMsg();
        if (isGatewayError(resultCd)) {
            // Gateway rejected the OTP (wrong code, expired, etc.).
            session.setStatus(NxisasScrapeSession.Status.FAILED);
            session.setErrorCode(resultCd);
            session.setErrorMessage(resultMsg);
            return ScrapeSessionResponse.error(session.getSessionId(), resultCd, resultMsg);
        }

        return proceedAfterLogin(session);
    }

    /**
     * Resume an NXISAS scrape after a QNA (security-question) challenge.
     * Mirrors legacy nxisas_v2.js:222-228 — the user picks ONE
     * question from the LOGIN response's list and types its answer;
     * we ship {@code {Question, HiddenValue, Answer}} back to Coocon.
     *
     * <p>The QNA response can:
     * <ul>
     *   <li>complete the login → proceed to ACCT_LIST</li>
     *   <li>return another QNA / OTP / CAPTCHA challenge (wrong answer
     *       or chained 2FA) → surface the new challenge back to the
     *       frontend so the user can retry</li>
     *   <li>error out → tear down the session and surface the code</li>
     * </ul>
     */
    public ScrapeSessionResponse submitQna(UUID sessionId, SubmitQnaRequest req) {
        String bizKey = TenantContext.currentBizKey();
        String userId = TenantContext.currentUserId();
        NxisasScrapeSession session = registry.find(sessionId, bizKey, userId)
                .orElseThrow(() -> new BusinessException("WBE2060"));
        session.touch();

        if (session.getChallengeKind() != ScrapeChallengeKind.QNA) {
            throw new BusinessException("WBE9001");
        }
        NxisasApiLog.event(session.getSessionId().toString(), flowForMode(session),
                "CHALLENGE_SUBMIT", "type", "QNA");

        NxisasQnaJobInput.QnaResponse res = new NxisasQnaJobInput.QnaResponse();
        res.setQuestion(req.question());
        res.setHiddenValue(req.hiddenValue());
        res.setAnswer(req.answer());
        NxisasQnaJobInput qnaInput = new NxisasQnaJobInput();
        qnaInput.setRes(res);

        // Fresh Tr_seq per call — same legacy-parity rationale as
        // submitOtp + runAcctList.
        String trSeq = gateway.newTrSeq();
        NxisasApiOut<NxisasLoginJobResult> qnaOut = gateway.submitQna(
                session.getBankModule(), session.getBankCategory(), trSeq,
                qnaInput, sessionContinuation(session));

        storeContinuation(session, GatewayContinuation.from(qnaOut));

        String resultCd = qnaOut == null || qnaOut.getCommon() == null
                ? null : qnaOut.getCommon().getResultCd();
        String resultMsg = qnaOut == null || qnaOut.getCommon() == null
                ? null : qnaOut.getCommon().getResultMsg();
        if (isGatewayError(resultCd)) {
            session.setStatus(NxisasScrapeSession.Status.FAILED);
            session.setErrorCode(resultCd);
            session.setErrorMessage(resultMsg);
            return ScrapeSessionResponse.error(session.getSessionId(), resultCd, resultMsg);
        }

        // Also surface bank-adapter-level errors (Output.ErrorCode != 00000000)
        // — e.g. "wrong answer, try another question" comes back here.
        NxisasLoginJobResult result = gateway.firstResult(qnaOut);
        var bankOutput = gateway.firstOutput(qnaOut);
        String bankErrCode = bankOutput == null ? null : bankOutput.getErrorCode();
        if (bankErrCode != null && !bankErrCode.isBlank() && !"00000000".equals(bankErrCode)) {
            String bankErrMsg = bankOutput.getErrorMessage();
            if (bankErrMsg == null || bankErrMsg.isBlank()) bankErrMsg = bankOutput.getDetailMessage();
            // Don't discard the session — the user may retry with a
            // different question/answer combo. Frontend stays on the
            // QNA dialog (status ERROR + the session's still in the
            // registry); UX-wise this is the same as a wrong OTP.
            return ScrapeSessionResponse.error(session.getSessionId(), bankErrCode, bankErrMsg);
        }

        // QNA can chain into another challenge (the bank may ask for OTP next).
        ScrapeChallengeKind nextKind = challengeKindFrom(result);
        if (nextKind != null) {
            session.setChallengeKind(nextKind);
            session.setChallengePrompt(result == null ? null : result.challengePrompt());
            if (nextKind == ScrapeChallengeKind.QNA) {
                List<QnaQuestion> questions = qnaQuestionsFrom(result);
                return ScrapeSessionResponse.qnaChallenge(
                        session.getSessionId(),
                        session.getChallengePrompt(),
                        questions);
            }
            return ScrapeSessionResponse.challenge(
                    session.getSessionId(),
                    statusForChallenge(nextKind),
                    session.getChallengePrompt());
        }

        // QNA accepted, no further challenge — proceed to the per-mode
        // post-login job (ACCT_LIST / CARD_INFO).
        return proceedAfterLogin(session);
    }

    /**
     * Resume an NXISAS scrape after a CAPTCHA challenge. Mirrors
     * legacy nxisas_v2.js:243 — the user types the CAPTCHA text;
     * we ship {@code Input.res = "<typed text>"} to Coocon under
     * the {@code CAPTCHA} job discriminator.
     *
     * <p>Like QNA, the CAPTCHA response can either complete the login,
     * chain into another challenge (wrong text → fresh CAPTCHA, or
     * CAPTCHA → OTP), or error out.
     */
    public ScrapeSessionResponse submitCaptcha(UUID sessionId, SubmitChallengeRequest req) {
        String bizKey = TenantContext.currentBizKey();
        String userId = TenantContext.currentUserId();
        NxisasScrapeSession session = registry.find(sessionId, bizKey, userId)
                .orElseThrow(() -> new BusinessException("WBE2060"));
        session.touch();

        if (session.getChallengeKind() != ScrapeChallengeKind.CAPTCHA) {
            throw new BusinessException("WBE9001");
        }
        NxisasApiLog.event(session.getSessionId().toString(), flowForMode(session),
                "CHALLENGE_SUBMIT", "type", "CAPTCHA");

        NxisasCaptchaJobInput captchaInput = new NxisasCaptchaJobInput();
        captchaInput.setRes(req.value());

        String trSeq = gateway.newTrSeq();
        NxisasApiOut<NxisasLoginJobResult> out = gateway.submitCaptcha(
                session.getBankModule(), session.getBankCategory(), trSeq,
                captchaInput, sessionContinuation(session));

        storeContinuation(session, GatewayContinuation.from(out));

        String resultCd = out == null || out.getCommon() == null
                ? null : out.getCommon().getResultCd();
        String resultMsg = out == null || out.getCommon() == null
                ? null : out.getCommon().getResultMsg();
        if (isGatewayError(resultCd)) {
            session.setStatus(NxisasScrapeSession.Status.FAILED);
            session.setErrorCode(resultCd);
            session.setErrorMessage(resultMsg);
            return ScrapeSessionResponse.error(session.getSessionId(), resultCd, resultMsg);
        }

        // Bank-adapter-level error (wrong CAPTCHA, etc.). Keep the
        // session so the user can retry; the frontend stays on the
        // CAPTCHA dialog and shows the error.
        NxisasLoginJobResult result = gateway.firstResult(out);
        var bankOutput = gateway.firstOutput(out);
        String bankErrCode = bankOutput == null ? null : bankOutput.getErrorCode();
        if (bankErrCode != null && !bankErrCode.isBlank() && !"00000000".equals(bankErrCode)) {
            String bankErrMsg = bankOutput.getErrorMessage();
            if (bankErrMsg == null || bankErrMsg.isBlank()) bankErrMsg = bankOutput.getDetailMessage();
            return ScrapeSessionResponse.error(session.getSessionId(), bankErrCode, bankErrMsg);
        }

        // CAPTCHA can chain into another challenge (e.g. wrong text =>
        // fresh CAPTCHA image, or CAPTCHA => OTP).
        ScrapeChallengeKind nextKind = challengeKindFrom(result);
        if (nextKind != null) {
            session.setChallengeKind(nextKind);
            session.setChallengePrompt(result == null ? null : result.challengePrompt());
            if (nextKind == ScrapeChallengeKind.QNA) {
                return ScrapeSessionResponse.qnaChallenge(
                        session.getSessionId(),
                        session.getChallengePrompt(),
                        qnaQuestionsFrom(result));
            }
            if (nextKind == ScrapeChallengeKind.CAPTCHA) {
                return ScrapeSessionResponse.captchaChallenge(
                        session.getSessionId(),
                        session.getChallengePrompt(),
                        captchaImageFrom(result),
                        captchaImageFormatFor(session.getBankModule()));
            }
            return ScrapeSessionResponse.challenge(
                    session.getSessionId(),
                    statusForChallenge(nextKind),
                    session.getChallengePrompt());
        }

        // CAPTCHA accepted, no further challenge — proceed to the
        // per-mode post-login job (ACCT_LIST / CARD_INFO).
        return proceedAfterLogin(session);
    }

    /**
     * Post-login dispatcher — branches on the session's {@code Mode} to
     * run the right Coocon job after the bank accepts the login. Called
     * from every path that completes a LOGIN (initial + every challenge
     * resume). {@code SYNC} stays on this code path's ACCT_LIST fallback
     * for safety; the real SYNC orchestration lives in
     * {@link BankTransactionSyncService} which intercepts SYNC sessions
     * earlier.
     */
    private ScrapeSessionResponse proceedAfterLogin(NxisasScrapeSession session) {
        return session.getMode() == NxisasScrapeSession.Mode.CARD_REGISTER
                ? runCardInfo(session)
                : runAcctList(session);
    }

    /**
     * Coocon CARD_INFO scrape after a successful LOGIN — mirrors
     * {@link #runAcctList} but reads the bank's card list instead of
     * the account list. Surfaces the result via
     * {@link ScrapeSessionResponse#completedCards} so the frontend's
     * "Confirm Cards" popup can render the scraped rows.
     *
     * <p>Mirrors legacy {@code nxisas_fnsh_r001_act.jsp:357-368} +
     * {@code comm_0218_007_c001} — after the user ticks which cards to
     * register, the frontend POSTs the selection to
     * {@code POST /api/v1/payment/card-transactions/cards} which
     * inserts the rows into {@code CARD_MSTR}.
     */
    private ScrapeSessionResponse runCardInfo(NxisasScrapeSession session) {
        // Fresh Tr_seq per call — same legacy-parity rule as runAcctList.
        String trSeq = gateway.newTrSeq();
        var out = gateway.cardInfo(
                session.getBankModule(), session.getBankCategory(), trSeq,
                new com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasCardInfoJobInput(),
                sessionContinuation(session));
        var result = gateway.firstCardInfoResult(out);

        String resultCd = out == null || out.getCommon() == null
                ? null : out.getCommon().getResultCd();
        String resultMsg = out == null || out.getCommon() == null
                ? null : out.getCommon().getResultMsg();
        if (isGatewayError(resultCd)) {
            session.setStatus(NxisasScrapeSession.Status.FAILED);
            session.setErrorCode(resultCd);
            session.setErrorMessage(resultMsg);
            registry.discard(session.getSessionId());
            return ScrapeSessionResponse.error(session.getSessionId(), resultCd, resultMsg);
        }

        java.util.List<ScrapedCard> cards =
                (result == null || result.getCardList() == null)
                        ? java.util.List.of()
                        : result.getCardList().stream()
                                .map(e -> new ScrapedCard(
                                        e.getCardNum(),
                                        e.getCardType(),
                                        e.getCardName(),
                                        e.getHolderName(),
                                        e.getIssueDate(),
                                        e.getExpireDate(),
                                        e.getAcctType(),
                                        e.getPaymentAcctNum()))
                                .toList();

        session.setStatus(NxisasScrapeSession.Status.COMPLETED);
        registry.discard(session.getSessionId());
        log.info("NXISAS card-info scrape completed [bank={}, cards={}]",
                session.getBankModule(), cards.size());
        return ScrapeSessionResponse.completedCards(session.getSessionId(), cards);
    }

    private ScrapeSessionResponse runAcctList(NxisasScrapeSession session) {
        // Coocon requires a fresh Tr_seq for every call (legacy
        // nxisas_fnsh_r001_act.jsp:137 unconditionally regenerates it
        // even when continuation context is carried over). Reusing the
        // LOGIN call's Tr_seq collides with the still-tearing-down
        // LOGIN thread on the gateway side and Coocon answers with
        // "APP00005 — invalid SAS_SessionID".
        String trSeq = gateway.newTrSeq();
        NxisasApiOut<NxisasAcctListJobResult> out = gateway.acctList(
                session.getBankModule(), session.getBankCategory(), trSeq,
                new NxisasAcctListJobInput(), sessionContinuation(session));
        NxisasAcctListJobResult result = gateway.firstAcctListResult(out);

        String resultCd = out == null || out.getCommon() == null
                ? null : out.getCommon().getResultCd();
        String resultMsg = out == null || out.getCommon() == null
                ? null : out.getCommon().getResultMsg();
        if (isGatewayError(resultCd)) {
            session.setStatus(NxisasScrapeSession.Status.FAILED);
            session.setErrorCode(resultCd);
            session.setErrorMessage(resultMsg);
            registry.discard(session.getSessionId());
            return ScrapeSessionResponse.error(session.getSessionId(), resultCd, resultMsg);
        }

        // Map each bank's raw acct-type label → legacy ACCT_TYPE_CD via
        // NXISAS_ACCT_TYPE_MAPPING (legacy fnsh_0101_001_r001_act.jsp:164-176
        // parity). Loaded once per scrape; degrades to "no mapping" when the
        // reference table isn't seeded (acctTypeCd stays null → picker leaves
        // Account Type blank). The raw acctType is still carried for reference.
        Map<String, Map<String, String>> acctTypeMappings =
                acctTypeMappingRepo.loadBankMappings(session.getBankCd());
        List<ScrapedAccount> accounts = result == null || result.getAcctList() == null
                ? List.of()
                : result.getAcctList().stream().map(e -> new ScrapedAccount(
                        e.getAcctName(), e.getAcctNum(), e.getAcctType(), e.getCurrency(),
                        e.getBalance(), e.getAvailableBal(), e.getBranchName(), e.getOpenDate(),
                        e.getMaturityDate(),
                        NxisasAcctTypeMappingRepository.resolve(
                                acctTypeMappings, e.getCurrency(), e.getAcctType())))
                .toList();

        session.setStatus(NxisasScrapeSession.Status.COMPLETED);
        session.setResultAccounts(result == null ? null : result.getAcctList());
        registry.discard(session.getSessionId());
        log.info("NXISAS scrape completed [bank={}, accounts={}]",
                session.getBankModule(), accounts.size());
        return ScrapeSessionResponse.completed(session.getSessionId(), accounts);
    }

    /** Build a {@link GatewayContinuation} from the session's stored gateway state. */
    private static GatewayContinuation sessionContinuation(NxisasScrapeSession session) {
        return new GatewayContinuation(
                session.getSasSessionID(),
                session.getGwSysNo(),
                session.getLbsNo(),
                session.getSsSysNo(),
                session.getThreadNo());
    }

    /** Copy the gateway continuation fields into the session for use on the next call. */
    /** Coocon gateway-envelope success/failure check. The gateway returns
     *  {@code "00000000"} (8 zeros) on success — NOT {@code "0000"} —
     *  and the bank-adapter Output.ErrorCode shares the same convention.
     *  Anything else (including the legacy short forms {@code "0000"} /
     *  {@code "OK"}) is treated as success too, since some legacy adapter
     *  flows use them; only an explicit non-success code triggers ERROR.
     *  Null / blank → treat as success (no envelope info to act on). */
    private static boolean isGatewayError(String resultCd) {
        // Shared policy — see NxisasResultCodes (also consumed by
        // BankTransactionSyncService). Behaviour is identical to the former
        // inline implementation; the logic now lives in one place.
        return NxisasResultCodes.isGatewayError(resultCd);
    }

    private static void storeContinuation(NxisasScrapeSession session, GatewayContinuation cont) {
        session.setSasSessionID(cont.sasSessionID());
        session.setGwSysNo(cont.gwSysNo());
        session.setLbsNo(cont.lbsNo());
        session.setSsSysNo(cont.ssSysNo());
        session.setThreadNo(cont.threadNo());
    }

    private static ScrapeChallengeKind challengeKindFrom(NxisasLoginJobResult result) {
        if (result == null || result.getCallBackFunc() == null) return null;
        String f = result.getCallBackFunc().toLowerCase();
        if (f.contains("otp"))     return ScrapeChallengeKind.OTP;
        if (f.contains("captcha")) return ScrapeChallengeKind.CAPTCHA;
        if (f.contains("qna"))     return ScrapeChallengeKind.QNA;
        return null;
    }

    private static ScrapeSessionResponse.Status statusForChallenge(ScrapeChallengeKind k) {
        return switch (k) {
            case OTP     -> ScrapeSessionResponse.Status.OTP_REQUIRED;
            case CAPTCHA -> ScrapeSessionResponse.Status.CAPTCHA_REQUIRED;
            case QNA     -> ScrapeSessionResponse.Status.QNA_REQUIRED;
        };
    }

    /**
     * Parse the LOGIN response's {@code Result.req} object into a
     * typed question list. The bank adapter ships
     * {@code {API:"QNA", req:[{HiddenValue, Question}, ...]}} —
     * see Shinhan VN's response (snapshot 2026-05-15). Returns an
     * empty list when the shape doesn't match (we never throw — the
     * frontend would have nothing useful to render anyway, so we
     * surface the empty list and let the QNA dialog show a clear
     * "no questions returned" state).
     */
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

    /**
     * Extract the base64 CAPTCHA image from the LOGIN response.
     * Legacy nxisas_v2.js:1543 reads {@code obj.req.req} verbatim —
     * the bank ships {@code req: { req: "<base64>" }} or sometimes
     * {@code req: "<base64>"} directly. Both shapes handled here;
     * returns null when nothing parseable is present.
     */
    private static String captchaImageFrom(NxisasLoginJobResult result) {
        if (result == null || result.getReq() == null) return null;
        JsonNode req = result.getReq();
        // Object shape: { req: "<base64>" } (most adapters)
        if (req.isObject()) {
            JsonNode inner = req.get("req");
            if (inner != null && inner.isTextual()) return inner.asText();
        }
        // String shape: req is itself the base64 payload
        if (req.isTextual()) return req.asText();
        return null;
    }

    /**
     * CAPTCHA image MIME format. Legacy nxisas_v2.js:1539-1542 special-
     * cases VIETNAM_GDT as {@code image/svg+xml}; everything else is
     * {@code image/png}. The frontend renders as
     * {@code data:image/{format};base64,…}.
     *
     * <p>The bank discriminator can be either the legacy {@code bank_cd}
     * (e.g. {@code "VIETNAM_GDT"}) or the resolved Module path
     * (e.g. {@code "GLOBAL/VNM/PUBLIC_GDT"}) — either disambiguates
     * the SVG case.
     */
    private static String captchaImageFormatFor(String bankCdOrModule) {
        if (bankCdOrModule == null) return "png";
        if (bankCdOrModule.contains("GDT") || bankCdOrModule.contains("VIETNAM_GDT")) return "svg+xml";
        return "png";
    }
}
