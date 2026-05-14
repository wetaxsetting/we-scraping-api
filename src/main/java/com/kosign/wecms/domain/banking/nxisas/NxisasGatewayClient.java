package com.kosign.wecms.domain.banking.nxisas;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kosign.wecms.domain.banking.nxisas.audit.NxisasAuditLogger;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasAcctListJobInput;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasAcctListJobResult;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasApiIn;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasApiOut;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasCommon;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasJob;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasLoginJobInput;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasLoginJobResult;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasOTPJobInput;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasOTPJobResult;
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
    private final NxisasGatewayProperties props;
    private final ObjectMapper objectMapper;
    private final NxisasAuditLogger audit;

    public NxisasGatewayClient(@Qualifier("nxisasRestClient") RestClient restClient,
                               NxisasGatewayProperties props,
                               ObjectMapper objectMapper,
                               NxisasAuditLogger audit) {
        this.restClient = restClient;
        this.props = props;
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

    public NxisasApiOut<NxisasAcctListJobResult> acctList(String module, String category, String trSeq,
                                                          NxisasAcctListJobInput input,
                                                          GatewayContinuation cont) {
        return call(module, category, trSeq, NxisasJob.ACCT_LIST, input, cont,
                new TypeReference<NxisasApiOut<NxisasAcctListJobResult>>() { });
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
        common.setAuthKey(props.apiKey());
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

    public NxisasAcctListJobResult firstAcctListResult(NxisasApiOut<NxisasAcctListJobResult> out) {
        NxisasResult<NxisasAcctListJobResult> r = firstEntry(out);
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
