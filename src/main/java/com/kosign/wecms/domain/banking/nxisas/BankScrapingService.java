package com.kosign.wecms.domain.banking.nxisas;

import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.banking.bankcredentials.service.BankCredentialService;
import com.kosign.wecms.domain.banking.bankcredentials.service.BankCredentialService.DecryptedCredential;
import com.kosign.wecms.domain.banking.nxisas.dto.ScrapeSessionResponse;
import com.kosign.wecms.domain.banking.nxisas.dto.ScrapeSessionResponse.Status;
import com.kosign.wecms.domain.banking.nxisas.dto.ScrapedAccount;
import com.kosign.wecms.domain.banking.nxisas.dto.StartScrapeRequest;
import com.kosign.wecms.domain.banking.nxisas.dto.SubmitChallengeRequest;
import com.kosign.wecms.domain.banking.nxisas.dto.VerifyCredentialsRequest;
import com.kosign.wecms.domain.banking.nxisas.NxisasGatewayClient.GatewayContinuation;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasAcctListJobInput;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasAcctListJobResult;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasApiOut;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasLoginJobInput;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasLoginJobResult;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasOTPJobInput;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasOTPJobResult;
import com.kosign.wecms.domain.banking.nxisas.session.NxisasScrapeSession;
import com.kosign.wecms.domain.banking.nxisas.session.NxisasSessionRegistry;
import com.kosign.wecms.domain.banking.nxisas.session.ScrapeChallengeKind;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
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
                req.credentialSeqNo());
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
        return runLogin(
                req.bankCd(),
                req.loginId(),
                req.password(),
                req.extra(),
                req.loginType(),
                req.nxisasGbSeqNo(),
                null);
    }

    /** Shared LOGIN → (challenge | ACCT_LIST) path. Both
     *  {@link #start(StartScrapeRequest)} and {@link #verify(VerifyCredentialsRequest)}
     *  funnel here once they have plaintext credentials in hand. */
    private ScrapeSessionResponse runLogin(String bankCd, String username, String password,
                                           String corporateId, String loginType,
                                           java.math.BigDecimal nxisasGbSeqNo,
                                           java.math.BigDecimal credentialSeqNo) {
        // Resolve the Coocon adapter Module + Class (CORPORATE/COMMON)
        // from the per-bank config. Legacy EXCH_0030_001:74-75 hardcodes
        // these for Woori VN (11704020 → BANK_Woori/CORPORATE) and
        // Shinhan VN (11704088 → BANK_Shinhan/COMMON); other banks are
        // an OSMC-config follow-up. Coocon's gateway rejects threads
        // with a missing/wrong Module or empty Class.
        NxisasBankConfig.Entry cfg = bankConfig.find(bankCd)
                .orElseThrow(() -> new BusinessException("WBE2093", bankCd));

        String bizKey = TenantContext.currentBizKey();
        String userId = TenantContext.currentUserId();
        String trSeq = gateway.newTrSeq();
        String bankModule = cfg.module();

        NxisasScrapeSession session = new NxisasScrapeSession(
                trSeq, bizKey, userId, credentialSeqNo, bankModule);
        session.setBankCategory(cfg.category());

        NxisasLoginJobInput loginInput = new NxisasLoginJobInput();
        loginInput.setLoginId(username);
        loginInput.setLoginPwd(password);
        // Coocon's Vietnam adapters expect ISO-3 "VNM", not ISO-2 "VN".
        // Sending "VN" returns 80010209 ("Incorrect Country Code") at the
        // bank-adapter level. Legacy comm_0218_nxisas.ts:512 hardcodes
        // "VNM" for the same reason.
        loginInput.setCountryCode("VNM");
        // FNSH_0100_001:210 hardcodes "ID" unconditionally — mirror that
        // default so the field is never omitted from the wire envelope.
        // A credential row may override with a bank-specific value.
        loginInput.setLoginType(
                (loginType != null && !loginType.isBlank()) ? loginType : "ID");
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
            return new ScrapeSessionResponse(
                    session.getSessionId(), Status.ERROR, null, null,
                    bankErrCode, bankErrMsg);
        }

        ScrapeChallengeKind kind = challengeKindFrom(result);
        if (kind != null) {
            session.setStatus(NxisasScrapeSession.Status.AWAITING_CHALLENGE);
            session.setChallengeKind(kind);
            session.setChallengePrompt(result == null ? null : result.getReq());
            registry.register(session);
            return new ScrapeSessionResponse(
                    session.getSessionId(),
                    statusForChallenge(kind),
                    session.getChallengePrompt(),
                    null, null, null);
        }

        // No challenge: gateway accepted the login outright. Proceed to acct-list.
        return runAcctList(session);
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
            return new ScrapeSessionResponse(
                    session.getSessionId(), Status.ERROR, null, null,
                    resultCd, resultMsg);
        }

        return runAcctList(session);
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
            return new ScrapeSessionResponse(
                    session.getSessionId(), Status.ERROR, null, null,
                    resultCd, resultMsg);
        }

        List<ScrapedAccount> accounts = result == null || result.getAcctList() == null
                ? List.of()
                : result.getAcctList().stream().map(e -> new ScrapedAccount(
                        e.getAcctName(), e.getAcctNum(), e.getAcctType(), e.getCurrency(),
                        e.getBalance(), e.getAvailableBal(), e.getBranchName(), e.getOpenDate()))
                .toList();

        session.setStatus(NxisasScrapeSession.Status.COMPLETED);
        session.setResultAccounts(result == null ? null : result.getAcctList());
        registry.discard(session.getSessionId());
        log.info("NXISAS scrape completed [bank={}, accounts={}]",
                session.getBankModule(), accounts.size());
        return new ScrapeSessionResponse(
                session.getSessionId(), Status.COMPLETED, null, accounts, null, null);
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
        if (resultCd == null || resultCd.isBlank()) return false;
        if ("00000000".equals(resultCd)) return false;
        if ("0000".equalsIgnoreCase(resultCd)) return false;
        if ("OK".equalsIgnoreCase(resultCd)) return false;
        return true;
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

    private static Status statusForChallenge(ScrapeChallengeKind k) {
        return switch (k) {
            case OTP     -> Status.OTP_REQUIRED;
            case CAPTCHA -> Status.CAPTCHA_REQUIRED;
            case QNA     -> Status.QNA_REQUIRED;
        };
    }
}
