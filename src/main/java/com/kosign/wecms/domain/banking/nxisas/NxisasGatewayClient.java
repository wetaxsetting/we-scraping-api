package com.kosign.wecms.domain.banking.nxisas;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kosign.wecms.config.CooconProperties;
import com.kosign.wecms.domain.banking.nxisas.audit.NxisasAuditLogger;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasAcctListJobInput;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasAcctListJobResult;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasBalanceJobInput;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasBalanceJobResult;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasCardInfoJobInput;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasCardInfoJobResult;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasCardUsingJobInput;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasCardUsingJobResult;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasForeignExchangeJobInput;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasForeignExchangeJobResult;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasTransactionJobInput;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasTransactionJobResult;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasApiIn;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasApiOut;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasCommon;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasJob;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasLoginJobInput;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasLoginJobResult;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasCaptchaJobInput;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasOTPJobInput;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasOTPJobResult;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasQnaJobInput;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasOutput;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasResult;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasThread;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * Spring {@code RestClient} wrapper over the Webcash NXISAS smart-
 * gateway. Replaces the legacy
 * {@code kr.co.coocon.ibasews.NxisasRestClient} (Jersey-based) — same
 * wire shape, fewer dependencies.
 *
 * <p>Each method writes a {@code NXSIAS_MODULE_TRAN_LOG_HIS} audit
 * row via {@link NxisasAuditLogger}. The audit row carries the
 * outbound JSON in {@code REQ_DATA} and the result code in
 * {@code NXISAS_ERR_CODE}.
 */
@Component
public class NxisasGatewayClient {

    private static final Logger log = LoggerFactory.getLogger(NxisasGatewayClient.class);

    private final RestClient restClient;
    private final CooconProperties coocon;
    private final ObjectMapper objectMapper;
    private final NxisasAuditLogger audit;

    public NxisasGatewayClient(@Qualifier("nxisasRestClient") RestClient restClient,
                               CooconProperties coocon,
                               ObjectMapper objectMapper,
                               NxisasAuditLogger audit) {
        this.restClient = restClient;
        this.coocon = coocon;
        this.objectMapper = objectMapper;
        this.audit = audit;
    }

    public NxisasApiOut<NxisasLoginJobResult> login(String module, String category, String trSeq,
                                                    NxisasLoginJobInput input) {
        return call(module, category, trSeq, NxisasJob.LOGIN, input, GatewayContinuation.empty(),
                new TypeReference<NxisasApiOut<NxisasLoginJobResult>>() { });
    }

    public NxisasApiOut<NxisasOTPJobResult> submitOtp(String module, String category, String trSeq,
                                                     NxisasOTPJobInput input,
                                                     GatewayContinuation cont) {
        return call(module, category, trSeq, NxisasJob.OTP, input, cont,
                new TypeReference<NxisasApiOut<NxisasOTPJobResult>>() { });
    }

    /**
     * Resume an NXISAS scrape after a QNA (security-question) challenge.
     * The response shape matches {@link NxisasLoginJobResult} — on
     * success {@code CallBackfunc} is null; on a wrong answer the
     * gateway can come back with another QNA / OTP / CAPTCHA challenge
     * inside the same shape.
     */
    public NxisasApiOut<NxisasLoginJobResult> submitQna(String module, String category, String trSeq,
                                                       NxisasQnaJobInput input,
                                                       GatewayContinuation cont) {
        return call(module, category, trSeq, NxisasJob.QNA, input, cont,
                new TypeReference<NxisasApiOut<NxisasLoginJobResult>>() { });
    }

    /**
     * Resume an NXISAS scrape after a CAPTCHA challenge. Wire shape
     * identical to OTP ({@code Input.res = "<typed text>"}); the
     * {@code Job} field is the discriminator. Response reuses
     * {@link NxisasLoginJobResult} for the same reason QNA does — a
     * wrong CAPTCHA can chain into another challenge.
     */
    public NxisasApiOut<NxisasLoginJobResult> submitCaptcha(String module, String category, String trSeq,
                                                            NxisasCaptchaJobInput input,
                                                            GatewayContinuation cont) {
        return call(module, category, trSeq, NxisasJob.CAPTCHA, input, cont,
                new TypeReference<NxisasApiOut<NxisasLoginJobResult>>() { });
    }

    public NxisasApiOut<NxisasAcctListJobResult> acctList(String module, String category, String trSeq,
                                                          NxisasAcctListJobInput input,
                                                          GatewayContinuation cont) {
        return call(module, category, trSeq, NxisasJob.ACCT_LIST, input, cont,
                new TypeReference<NxisasApiOut<NxisasAcctListJobResult>>() { });
    }

    /**
     * CARD_INFO job — pull the card list for the just-logged-in bank
     * session. Parallel to {@link #acctList} but returns
     * {@link NxisasCardInfoJobResult} (a list of card rows with
     * {@code cardNum} / {@code cardType} / {@code holderName} etc.).
     *
     * <p>Mirrors legacy {@code nxisas_fnsh_r001_act.jsp:357-368} — the
     * {@code CARD_INFO} branch where the {@code CardInfoInput} is an
     * empty marker DTO and the response carries the scraped card list.
     *
     * <p>Must be called AFTER a successful LOGIN on the same session
     * (Tr_seq fresh per call per legacy parity; continuation carries
     * SAS_SessionID + Gw_sys_no + Thread_no + Lbs_no + Ss_sys_no).
     */
    public NxisasApiOut<NxisasCardInfoJobResult> cardInfo(String module, String category, String trSeq,
                                                          NxisasCardInfoJobInput input,
                                                          GatewayContinuation cont) {
        return call(module, category, trSeq, NxisasJob.CARD_INFO, input, cont,
                new TypeReference<NxisasApiOut<NxisasCardInfoJobResult>>() { });
    }

    /**
     * CARD_USING job — pull the transaction list for one card over
     * the requested {@code startDate..endDate} window (inclusive,
     * yyyyMMdd). Mirrors legacy {@code nxisas_cardTranList.ts:212}
     * — the Coocon adapter dispatches per bank/cardNum/cardType.
     *
     * <p>Must be called AFTER a successful LOGIN on the same session
     * (Tr_seq fresh per call per legacy parity; continuation carries
     * SAS_SessionID + Gw_sys_no + Thread_no + Lbs_no + Ss_sys_no).
     */
    public NxisasApiOut<NxisasCardUsingJobResult> cardUsing(String module, String category, String trSeq,
                                                            NxisasCardUsingJobInput input,
                                                            GatewayContinuation cont) {
        return call(module, category, trSeq, NxisasJob.CARD_USING, input, cont,
                new TypeReference<NxisasApiOut<NxisasCardUsingJobResult>>() { });
    }

    /** Helper for the CARD_USING post-login flow — peels the first
     *  {@code resultList[0].output.result} off a Coocon CARD_USING
     *  response. Returns {@code null} when the response shape is
     *  degenerate (empty resultList, missing output, etc.) so the
     *  caller can fall back to an empty transaction list. */
    public NxisasCardUsingJobResult firstCardUsingResult(NxisasApiOut<NxisasCardUsingJobResult> out) {
        NxisasResult<NxisasCardUsingJobResult> r = firstEntry(out);
        return r == null || r.getOutput() == null ? null : r.getOutput().getResult();
    }

    /**
     * TRANSACTION job — pull the transaction list for one account over
     * the requested {@code startDate..endDate} window (inclusive,
     * yyyyMMdd). Mirrors legacy {@code fnsh_0001_001_c001_act.jsp}'s
     * IsasJob.TRANSACTION_FIXED_DEPOSIT / IsasJob.TRANSACTION_FOREIGN —
     * the Coocon adapter dispatches local vs FCY by the {@code currency}
     * field in the input, so a single gateway method covers both legacy
     * job types.
     *
     * <p>Must be called AFTER a successful LOGIN on the same session
     * (Tr_seq fresh per call per legacy parity; continuation carries
     * SAS_SessionID + Gw_sys_no + Thread_no + Lbs_no + Ss_sys_no).
     */
    public NxisasApiOut<NxisasTransactionJobResult> transaction(String module, String category, String trSeq,
                                                                NxisasTransactionJobInput input,
                                                                GatewayContinuation cont) {
        return call(module, category, trSeq, NxisasJob.TRANSACTION, input, cont,
                new TypeReference<NxisasApiOut<NxisasTransactionJobResult>>() { });
    }

    /**
     * FOREIGN_EXCHANGE job — fetch the FX-rate list for one bank+round
     * on one inquiry date. Mirrors legacy BCS
     * {@code bcs.jex.wabooks.exch.EXCH_0001_001#execute} (and the
     * EXCH_0010 / EXCH_0020 siblings) — same wire envelope as
     * {@link #acctList} / {@link #transaction}, but with no session
     * continuation (FX has no LOGIN step: every call is standalone with
     * an empty {@code SAS_SessionID}). Module + Class are supplied by
     * the caller per bank (Shinhan = {@code GLOBAL/GBL/BANK_Shinhan} /
     * {@code COMMON}, Woori = {@code GLOBAL/GBL/BANK_Woori} /
     * {@code CORPORATE}).
     */
    public NxisasApiOut<NxisasForeignExchangeJobResult> foreignExchange(
            String module, String category, String trSeq,
            NxisasForeignExchangeJobInput input) {
        return call(module, category, trSeq, NxisasJob.FOREIGN_EXCHANGE, input,
                GatewayContinuation.empty(),
                new TypeReference<NxisasApiOut<NxisasForeignExchangeJobResult>>() { });
    }

    /**
     * BALANCE job — fetch current balance + available balance for one
     * account. Mirrors legacy IsasJob.BALANCE_FIXED_DEPOSIT /
     * BALANCE_FOREIGN; one gateway method covers both, the adapter
     * dispatches by {@code currency}.
     *
     * <p>Called after TRANSACTION so {@code ACCT_MSTR.BAL} +
     * {@code ACCT_MSTR.REAL_AMT} stay in sync with the just-scraped
     * ledger (legacy parity — `fnsh_0001_001_c001_act.jsp` runs the
     * BALANCE job in the same thread chain as TRANSACTION).
     */
    public NxisasApiOut<NxisasBalanceJobResult> balance(String module, String category, String trSeq,
                                                        NxisasBalanceJobInput input,
                                                        GatewayContinuation cont) {
        return call(module, category, trSeq, NxisasJob.BALANCE, input, cont,
                new TypeReference<NxisasApiOut<NxisasBalanceJobResult>>() { });
    }

    /**
     * Gateway continuation context captured from a prior call's response.
     * FNSH_0100_001:249-254 copies these 6 values from the LOGIN response
     * into every subsequent call (OTP, ACCT_LIST) so the gateway can
     * route them to the same bank-adapter session.
     */
    public record GatewayContinuation(
            String sasSessionID,
            String gwSysNo,
            String lbsNo,
            String ssSysNo,
            String threadNo) {

        /** Empty continuation for the initial LOGIN call. */
        public static GatewayContinuation empty() {
            return new GatewayContinuation("", "", "", "", "");
        }

        /** Snapshot from a gateway response (nxisas_fnsh_r001_act.jsp:124-133). */
        public static GatewayContinuation from(NxisasApiOut<?> out) {
            if (out == null) return empty();
            String sas   = out.getSasSessionID() != null ? out.getSasSessionID() : "";
            NxisasCommon c = out.getCommon();
            if (c == null) return new GatewayContinuation(sas, "", "", "", "");
            return new GatewayContinuation(
                    sas,
                    c.getGwSysNo()  != null ? c.getGwSysNo()  : "",
                    c.getLbsNo()    != null ? c.getLbsNo()    : "",
                    c.getSsSysNo()  != null ? c.getSsSysNo()  : "",
                    c.getThreadNo() != null ? c.getThreadNo() : "");
        }
    }

    /**
     * Fresh Tr_seq in the format Coocon expects — DTM14 timestamp
     * (yyyyMMddHHmmss), matching nxisas_fnsh_r001_act.jsp:137.
     * Every call generates a new value; the previous response's Tr_seq
     * is never echoed back.
     */
    public String newTrSeq() {
        return java.time.LocalDateTime.now(java.time.ZoneOffset.UTC)
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
    }

    private <I, R> NxisasApiOut<R> call(String module, String category, String trSeq, NxisasJob job, I input,
                                        GatewayContinuation cont, TypeReference<NxisasApiOut<R>> typeRef) {
        NxisasApiIn<I> apiIn = buildEnvelope(module, category, trSeq, job, input, cont);
        String reqJson;
        try {
            reqJson = objectMapper.writeValueAsString(apiIn);
            log.info("NXISAS request [job={}, trSeq={}]: {}", job, trSeq, reqJson);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize NXISAS request", e);
        }

        String resJson;
        try {
            resJson = restClient.post().body(reqJson).retrieve().body(String.class);
        } catch (Exception e) {
            audit.record(trSeq, module, "ERROR", job.name(), "TRANSPORT",
                    e.getMessage(), reqJson, job.name());
            log.warn("NXISAS gateway call failed [job={}, module={}, trSeq={}]: {}",
                    job, module, trSeq, e.getMessage());
            throw new NxisasTransportException(e);
        }

        try {
            String json = resJson == null ? "" : resJson;
            log.info("NXISAS raw response [job={}, trSeq={}]: {}", job, trSeq, json);
            NxisasApiOut<R> apiOut = objectMapper.readValue(json, typeRef);
            String resultCd = apiOut != null && apiOut.getCommon() != null
                    ? apiOut.getCommon().getResultCd() : null;
            String resultMsg = apiOut != null && apiOut.getCommon() != null
                    ? apiOut.getCommon().getResultMsg() : null;
            log.info("NXISAS parsed [job={}, resultCd={}, sasSessionID={}]",
                    job, resultCd, apiOut != null ? apiOut.getSasSessionID() : "null");
            audit.record(trSeq, module, "OK", job.name(),
                    resultCd != null ? resultCd : "UNKNOWN",
                    resultMsg, reqJson, job.name());
            return apiOut;
        } catch (Exception e) {
            audit.record(trSeq, module, "ERROR", job.name(), "PARSE",
                    e.getMessage() + " | body=" + resJson, reqJson, job.name());
            throw new IllegalStateException("Failed to parse NXISAS response", e);
        }
    }

    private <I> NxisasApiIn<I> buildEnvelope(String module, String category, String trSeq, NxisasJob job, I input,
                                             GatewayContinuation cont) {
        NxisasCommon common = new NxisasCommon();
        common.setAuthKey(coocon.smartApiKey());
        // nxisas_fnsh_r001_act.jsp:137 always generates a fresh DTM14
        // timestamp for Tr_seq on every call — even continuation calls.
        // The echoed Tr_seq from the previous response is NOT forwarded.
        common.setTrSeq(trSeq);
        common.setAuthReq("");
        common.setResultCd("");
        common.setResultMsg("");
        // FNSH_0100_001:249-252: after LOGIN the gateway returns Gw_sys_no,
        // Lbs_no, Ss_sys_no, Thread_no in the Common response. These must
        // be echoed back on subsequent calls so the gateway routes them to
        // the same bank-adapter session. For LOGIN itself cont fields are "".
        common.setGwSysNo(cont.gwSysNo());
        common.setLbsNo(cont.lbsNo());
        common.setSsSysNo(cont.ssSysNo());
        common.setThreadNo(cont.threadNo());

        NxisasThread<I> thread = new NxisasThread<>();
        thread.setModule(module);
        // Legacy FNSH_0100_001:204-205 reads CLASS from the OSMC API config
        // table per BANK_CD (CORPORATE/COMMON). Spike scope: caller supplies
        // the value (null → ""); wiring OSMC config is a follow-up.
        thread.setCategory(category == null ? "" : category);
        thread.setJob(job);
        thread.setErrorLang("EN");
        thread.setInput(input);

        NxisasApiIn<I> apiIn = new NxisasApiIn<>();
        apiIn.setCommon(common);
        apiIn.setInputList(List.of(thread));
        // Dsnc — Coocon gateway envelope-level field. The legacy JEX
        // server-side callers (FNSH_0100_001, EXCH_0001..0030_001)
        // set this to the literal "S" on every request, and the
        // gateway rejects calls that omit it with
        // `GWSM0001: Dsnc 값을 입력해 주십시오`. Mirror that here so
        // the refactor's spike can reach the bank-adapter LOGIN path.
        apiIn.setDnsc("S");
        // FNSH_0100_001:176 sends "" on LOGIN; :254 sends the SAS_SessionID
        // returned by LOGIN on every subsequent call. The cont record carries
        // "" for LOGIN and the real value for OTP / ACCT_LIST.
        apiIn.setSasSessionID(cont.sasSessionID());
        return apiIn;
    }

    /** Convenience: surface the {@code LoginJobResult} when the
     *  gateway returned exactly one result entry. Returns null
     *  otherwise (caller decides whether that's an error). */
    public NxisasLoginJobResult firstResult(NxisasApiOut<NxisasLoginJobResult> out) {
        NxisasResult<NxisasLoginJobResult> r = firstEntry(out);
        return r == null || r.getOutput() == null ? null : r.getOutput().getResult();
    }

    /** Bank-adapter Output wrapper from the first entry — carries the
     *  per-adapter {@code ErrorCode} / {@code ErrorMessage} that sit at
     *  a different layer than the gateway envelope {@code Common.Result_cd}.
     *  Returns null when the response has no entries.
     */
    public <R> NxisasOutput<R> firstOutput(NxisasApiOut<R> out) {
        NxisasResult<R> r = firstEntry(out);
        return r == null ? null : r.getOutput();
    }

    public NxisasAcctListJobResult firstAcctListResult(NxisasApiOut<NxisasAcctListJobResult> out) {
        NxisasResult<NxisasAcctListJobResult> r = firstEntry(out);
        return r == null || r.getOutput() == null ? null : r.getOutput().getResult();
    }

    /** Helper for the CARD_INFO post-login flow — peels the first
     *  {@code resultList[0].output.result} off a Coocon CARD_INFO
     *  response. Returns {@code null} when the response shape is
     *  degenerate (empty resultList, missing output, etc.) so the
     *  caller can fall back to an empty card list. */
    public NxisasCardInfoJobResult firstCardInfoResult(NxisasApiOut<NxisasCardInfoJobResult> out) {
        NxisasResult<NxisasCardInfoJobResult> r = firstEntry(out);
        return r == null || r.getOutput() == null ? null : r.getOutput().getResult();
    }

    public NxisasTransactionJobResult firstTransactionResult(NxisasApiOut<NxisasTransactionJobResult> out) {
        NxisasResult<NxisasTransactionJobResult> r = firstEntry(out);
        return r == null || r.getOutput() == null ? null : r.getOutput().getResult();
    }

    public NxisasBalanceJobResult firstBalanceResult(NxisasApiOut<NxisasBalanceJobResult> out) {
        NxisasResult<NxisasBalanceJobResult> r = firstEntry(out);
        return r == null || r.getOutput() == null ? null : r.getOutput().getResult();
    }

    /** Helper for FOREIGN_EXCHANGE — peels the first
     *  {@code resultList[0].output.result} off a Coocon FX response.
     *  Returns {@code null} when the response shape is degenerate
     *  (empty resultList, missing output, bank-adapter error, etc.) so
     *  the caller can decide whether to skip the round or surface an
     *  error to ops. */
    public NxisasForeignExchangeJobResult firstForeignExchangeResult(
            NxisasApiOut<NxisasForeignExchangeJobResult> out) {
        NxisasResult<NxisasForeignExchangeJobResult> r = firstEntry(out);
        return r == null || r.getOutput() == null ? null : r.getOutput().getResult();
    }

    private <R> NxisasResult<R> firstEntry(NxisasApiOut<R> out) {
        if (out == null) return null;
        List<NxisasResult<R>> list = out.getResultList();
        return (list == null || list.isEmpty()) ? null : list.get(0);
    }

    /** Marker for gateway-transport failures (network, timeout,
     *  non-2xx). Distinct from a parsed error response — the latter
     *  comes back through {@link NxisasApiOut#getCommon().getResultCd()}. */
    public static class NxisasTransportException extends RuntimeException {
        public NxisasTransportException(Throwable cause) { super(cause); }
    }
}
