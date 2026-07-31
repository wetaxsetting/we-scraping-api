package com.kosign.wecms.domain.payment.dispatch.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.domain.payment.dispatch.constant.FirmBankingResultCd;
import com.kosign.wecms.domain.payment.dispatch.van.client.EfsApiClient;
import com.kosign.wecms.domain.payment.dispatch.van.client.EfsProperties;
import com.kosign.wecms.domain.payment.dispatch.van.client.VanApiKeyService;
import com.kosign.wecms.domain.payment.dispatch.van.crypto.EfsCryptoService;
import com.kosign.wecms.domain.payment.dispatch.van.dto.Efs202Request;
import com.kosign.wecms.domain.payment.dispatch.van.dto.Efs202Response;
import com.kosign.wecms.domain.payment.dispatch.van.dto.Efs204Request;
import com.kosign.wecms.domain.payment.dispatch.van.dto.Efs204Response;
import com.kosign.wecms.domain.payment.dispatch.van.dto.EfsEnvelope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * {@code WGVN_EFS_202} — real-time single transfer (REST API guide ver 1.2.2
 * §5.4). Refactor of legacy {@code NAPAS_REAL_TIME_BATCH}.
 *
 * <p>Confirmed TOBE (Mr. Kang 2026-06-04): single transfer is <b>real-time /
 * synchronous at request time</b>, NOT a scheduled job.
 *
 * <p><b>Woori FX pre-step ({@code WGVN_EFS_204}, guide v1.2.6 §5.6).</b> When the
 * outbound bank is Woori ({@code bank_cd=970457}) and the transfer currency is not
 * VND, an applied-rate inquiry runs <i>before</i> the 202 (mirrors legacy
 * {@code NAPAS_REAL_TIME_BATCH.java:329-413}). Its seven response FX fields
 * ({@code apl_exc_rate_itg/dcm}, {@code withd_amt_itg/dcm}, {@code dep_curren_cd},
 * {@code dep_amt_itg/dcm}) feed the 202 request's FX block; a non-{@code 0000} 204
 * is fatal (no transfer without a confirmed rate). On the ordinary domestic-VND
 * path the 204 is skipped and the FX block is sent as empty strings (legacy parity).
 *
 * <p>Flow (mirrors {@link com.kosign.wecms.domain.payment.dispatch.recipient.RecipientLookupService}):
 * resolve key by {@code vanOrgCd} ({@link VanApiKeyService}) → [Woori/non-VND only:
 * {@link EfsApiClient#EFS_204 WGVN_EFS_204} FX inquiry] → build {@link Efs202Request}
 * (incl. FX block) → serialise (global SNAKE_CASE) → build {@code EV}/{@code VV}
 * ({@link EfsCryptoService}) → POST via {@link EfsApiClient#send EfsApiClient.EFS_202}:
 * <ul>
 *   <li><b>{@code RC=0000}</b> — verify the response {@code VV} (audit H-5), then decrypt +
 *       map {@link Efs202Response} → success. A {@code VV}-mismatch is treated as pending
 *       (see below), never a trusted success.</li>
 *   <li><b>{@code RC=G999} / no response / {@code VV}-mismatch</b> — no definitive response.
 *       Per §5.4 this is NOT a failure: returns {@link RealtimeTransferResult#ofPending()}
 *       so the caller leaves {@code PROC_STAT='6'} and the scheduled {@link ResultCheckService}
 *       confirms the real outcome via a later {@code WGVN_EFS_203} (audit B2 / H-4). This
 *       replaces the earlier synchronous inline-203 recovery, which risked a premature FAIL
 *       and could not distinguish "still settling" from "failed".</li>
 *   <li><b>other non-{@code 0000}</b> — a definite error, surfaced as {@code WBE_EFS_GATEWAY_ERROR}.</li>
 * </ul>
 *
 * <p>The {@code PROC_STAT} write (success/pending/fail) is owned by the caller
 * ({@link RealtimeDispatchService}); the scheduled result-check owns the eventual
 * {@code 6 → 0/3} finalisation. Logging never emits {@code API_KEY}, {@code EV},
 * {@code VV}, the decrypted payload, or full account numbers.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RealtimeTransferService {

    private static final DateTimeFormatter RQ_DTIME_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final ZoneId ICT = ZoneId.of("Asia/Ho_Chi_Minh");

    /**
     * Woori bank code — the only bank for which the {@code WGVN_EFS_204} FX-rate
     * pre-inquiry applies (guide v1.2.6 §5.6; legacy {@code WCConst.BANK_CD_WOORI},
     * branch at {@code NAPAS_REAL_TIME_BATCH.java:295}).
     */
    private static final String BANK_CD_WOORI = "970457";
    /** Domestic currency — the 204 pre-step is skipped when the transfer is in VND. */
    private static final String CURRENCY_VND = "VND";

    private final EfsApiClient efsApiClient;
    private final EfsCryptoService efsCryptoService;
    private final VanApiKeyService vanApiKeyService;
    private final EfsProperties efsProperties;
    private final ObjectMapper objectMapper;

    /**
     * Execute a real-time single transfer.
     *
     * @return a direct-success result on {@code RC=0000}, or the confirming 203
     *         result on {@code RC=G999} (see {@link RealtimeTransferResult})
     * @throws IllegalArgumentException on missing required input
     * @throws IllegalStateException    when the API key is not provisioned (B2,
     *                                  surfaced from {@link VanApiKeyService})
     * @throws BusinessException        ({@code WBE_EFS_GATEWAY_ERROR}) on a
     *                                  non-{@code 0000}, non-{@code G999} result
     */
    public RealtimeTransferResult transfer(RealtimeTransferCommand cmd, String tno) {
        validate(cmd);

        // Resolve the per-VAN key first — a missing key (B2) must surface here,
        // before any envelope is built or any request is sent.
        String apiKey = vanApiKeyService.getApiKey(cmd.vanOrgCd());
        // D-2 (legacy parity): the envelope ID is ALWAYS TB_FIRM_CONF.VAN_ORG_CD — never a
        // configured partner id. Per-leg legacy evidence in EfsProperties' class Javadoc.
        String id = cmd.vanOrgCd();

        // §5.6 Woori-only pre-FX inquiry (WGVN_EFS_204). On the Woori / non-VND path the
        // 204 response FX fields populate the 202 FX block; on the ordinary domestic-VND
        // path the FX fields stay empty strings (legacy NAPAS_REAL_TIME_BATCH.java:329-413
        // initialises them to "" and always includes them in param_202:553-565).
        // (Direct-call convenience path — the dispatch path pre-fetches the 204 via
        // RealtimeDispatchService.runFxInquiry with its own TRAN_SEQ and calls the 3-arg overload.)
        Efs204Response fx = null;
        if (isWooriFxTransfer(cmd)) {
            fx = inquireFxRate(cmd, apiKey, id, tno);
            if (fx == null) {
                // D — 204 no-response on the Woori FX pre-step: legacy skips the 202 (leaves the
                // transaction for the result-check / re-run). Return pending; never send the 202
                // with an empty FX block. An explicit non-0000 / VV-fail 204 still throws upstream.
                log.warn("EFS 204 FX pre-inquiry no-response vanOrgCd={} macct={} — 202 skipped (pending)",
                        cmd.vanOrgCd(), mask(cmd.macctNo()));
                return RealtimeTransferResult.ofPending();
            }
        }
        return transfer(cmd, fx, tno);
    }

    /**
     * Execute a real-time single transfer with a <b>pre-fetched</b> {@code WGVN_EFS_204}
     * FX result (or {@code null} for the ordinary domestic-VND path).
     *
     * <p>Used by {@link RealtimeDispatchService} on the Woori / non-VND path, where the
     * 204 inquiry has already run (and been persisted as a {@code TRAN_TP='204'}
     * {@code TB_FIRM_TRAN} audit row) <i>before</i> the 202 row is created — so the 204
     * is never fired twice. The 202 logic here is identical to the single-arg
     * {@link #transfer(RealtimeTransferCommand)}; only the source of the FX block
     * differs (caller-supplied vs fetched inline).
     */
    public RealtimeTransferResult transfer(RealtimeTransferCommand cmd, Efs204Response fx, String tno) {
        validate(cmd);
        if (!StringUtils.hasText(tno)) {
            throw new IllegalArgumentException("tno (TRAN_SEQ) is required");
        }

        String apiKey = vanApiKeyService.getApiKey(cmd.vanOrgCd());
        // D-2 (legacy parity): the envelope ID is ALWAYS TB_FIRM_CONF.VAN_ORG_CD — never a
        // configured partner id. Per-leg legacy evidence in EfsProperties' class Javadoc.
        String id = cmd.vanOrgCd();

        Efs202Request request = new Efs202Request(
                cmd.bankCd(), cmd.prtnId(), cmd.currenCd(), cmd.amt(), cmd.dcmAmt(),
                cmd.tarFincMdaDiv(), cmd.macctNo(), cmd.tarBankCd(), cmd.tarFincMdaNo(),
                cmd.fincMdaOwnerNm(), cmd.tranUse(), cmd.antDivCd(),
                // withd_curren_cd is the withdrawal currency (legacy WITHD_CCY_CD) — always
                // sent, not part of the 204 response feed. The remaining seven FX fields come
                // from the 204 response (or "" when no 204 ran).
                nullToEmpty(cmd.withdCurrenCd()),
                fx == null ? "" : nullToEmpty(fx.depCurrenCd()),
                fx == null ? "" : nullToEmpty(fx.aplExcRateItg()),
                fx == null ? "" : nullToEmpty(fx.aplExcRateDcm()),
                fx == null ? "" : nullToEmpty(fx.withdAmtItg()),
                fx == null ? "" : nullToEmpty(fx.withdAmtDcm()),
                fx == null ? "" : nullToEmpty(fx.depAmtItg()),
                fx == null ? "" : nullToEmpty(fx.depAmtDcm()));
        String businessJson = serialize(request);

        // A1 (audit X-2): the envelope TNO is the caller-allocated TRAN_SEQ (the pay-target's
        // dep_seq / the 202 TB_FIRM_TRAN PK), NOT a UUID — so the scheduled result-check can
        // reconstruct org_tno from the stored TRAN_SEQ and the gateway correlates the 203 inquiry
        // to this original transaction (legacy NAPAS_REAL_TIME_BATCH TNO = dep_seq).
        String rqDtime = LocalDateTime.now(ICT).format(RQ_DTIME_FMT);
        String ev = efsCryptoService.encryptEv(businessJson, apiKey, rqDtime);
        String vv = efsCryptoService.buildVv(businessJson, apiKey);

        EfsEnvelope requestEnvelope = new EfsEnvelope(
                id, rqDtime, null, tno, ev, vv,
                EfsEnvelope.EM_AES, EfsEnvelope.VM_HMAC_SHA256, null, null);

        EfsEnvelope response = efsApiClient.send(EfsApiClient.EFS_202, requestEnvelope);
        String rc = response == null ? null : response.rc();

        // B2 (audit H-4/D2): no response is NOT a failure. Legacy leaves PROC_STAT='6' and lets the
        // scheduled PROCESSING_RESULT_CHECK confirm the real outcome later (via 203) — never a
        // synchronous inline 203 and never a premature FAIL. Return a pending result.
        if (response == null) {
            log.warn("EFS 202 no response vanOrgCd={} macct={} tno={} — left pending for result-check",
                    cmd.vanOrgCd(), mask(cmd.macctNo()), tno);
            return RealtimeTransferResult.ofPending();
        }

        if (FirmBankingResultCd.isSuccess(rc)) {
            // A3 (audit H-5): verify the response VV before decrypting/trusting EV (legacy
            // SecurityUtil.VerifyMac → G003). A corrupt/tampered "success" cannot be trusted as a
            // completed transfer; treat it as no-definitive-response (leave '6', let result-check
            // confirm) rather than risk a double-transfer or a false FAIL.
            if (!efsCryptoService.verifyVv(response.ev(), response.vv(), apiKey)) {
                log.warn("EFS 202 response VV verification failed vanOrgCd={} macct={} tno={} — left pending",
                        cmd.vanOrgCd(), mask(cmd.macctNo()), tno);
                return RealtimeTransferResult.ofPending();
            }
            String responseJson = efsCryptoService.decryptEv(response.ev(), apiKey);
            Efs202Response parsed = deserialize(responseJson, Efs202Response.class);
            log.debug("EFS 202 transfer ok vanOrgCd={} macct={} tarBankCd={} acct={} rc={}",
                    cmd.vanOrgCd(), mask(cmd.macctNo()), cmd.tarBankCd(), mask(cmd.tarFincMdaNo()), rc);
            return RealtimeTransferResult.ofTransfer(parsed);
        }

        if (FirmBankingResultCd.ETC_ERR.equals(rc)) {
            // §5.4: G999 = "no definitive response". Leave PROC_STAT='6'; the scheduled result-check
            // reconciles via a later 203 (B2 — no synchronous inline 203, which risked a premature FAIL).
            log.info("EFS 202 returned G999 vanOrgCd={} macct={} tno={} — left pending for result-check",
                    cmd.vanOrgCd(), mask(cmd.macctNo()), tno);
            return RealtimeTransferResult.ofPending();
        }

        // Definite gateway business error (non-0000, non-G999) → committed FAIL (legacy parity).
        log.warn("EFS 202 transfer failed vanOrgCd={} macct={} tarBankCd={} acct={} rc={}",
                cmd.vanOrgCd(), mask(cmd.macctNo()), cmd.tarBankCd(), mask(cmd.tarFincMdaNo()), rc);
        throw new BusinessException("WBE_EFS_GATEWAY_ERROR", rc);
    }

    private void validate(RealtimeTransferCommand cmd) {
        if (cmd == null) {
            throw new IllegalArgumentException("RealtimeTransferCommand is required");
        }
        if (!StringUtils.hasText(cmd.vanOrgCd())) {
            throw new IllegalArgumentException("vanOrgCd is required");
        }
        if (!StringUtils.hasText(cmd.macctNo())) {
            throw new IllegalArgumentException("macctNo is required");
        }
        if (!StringUtils.hasText(cmd.tarBankCd())) {
            throw new IllegalArgumentException("tarBankCd is required");
        }
        if (!StringUtils.hasText(cmd.tarFincMdaNo())) {
            throw new IllegalArgumentException("tarFincMdaNo is required");
        }
        if (!StringUtils.hasText(cmd.amt())) {
            throw new IllegalArgumentException("amt is required");
        }
    }

    /**
     * True when this transfer needs the {@code WGVN_EFS_204} FX-rate pre-inquiry:
     * the outbound bank is Woori AND the transfer currency is not VND. Mirrors the
     * legacy branch {@code NAPAS_REAL_TIME_BATCH.java:295}
     * ({@code BANK_CD_WOORI.equals(OUT_BANK_CD) && !"VND".equals(ccy)}), including
     * the legacy default of treating a blank transfer currency as {@code VND}.
     */
    public boolean isWooriFxTransfer(RealtimeTransferCommand cmd) {
        String ccy = StringUtils.hasText(cmd.currenCd()) ? cmd.currenCd() : CURRENCY_VND;
        return BANK_CD_WOORI.equals(cmd.bankCd()) && !CURRENCY_VND.equals(ccy);
    }

    /**
     * Run the {@code WGVN_EFS_204} applied-rate inquiry as a standalone, persistable
     * step (resolving the per-VAN key + envelope id internally). Used by
     * {@link RealtimeDispatchService} so the 204 audit row ({@code TRAN_TP='204'}) +
     * {@code TB_PAY_TARGET} FX columns can be written before the 202 — exactly the
     * legacy {@code NAPAS_REAL_TIME_BATCH} order. Delegates to the same inquiry body
     * the inline 202 path uses, so the 204 EFS logic is unchanged.
     *
     * @return the FX response on {@code RC=0000}; {@code null} on a no-response (null
     *         envelope) 204 — legacy `continue`s and leaves the 204 row at
     *         {@code PROC_STAT='6'} for a later re-inquiry (finding D)
     * @throws BusinessException ({@code WBE_EFS_GATEWAY_ERROR}) on an explicit non-{@code 0000}
     *                           204 response or a VV-verify failure (carries the gateway RC as arg[0])
     */
    public Efs204Response inquireFxRate(RealtimeTransferCommand cmd, String tno) {
        validate(cmd);
        if (!StringUtils.hasText(tno)) {
            throw new IllegalArgumentException("tno (TRAN_SEQ) is required");
        }
        String apiKey = vanApiKeyService.getApiKey(cmd.vanOrgCd());
        // D-2 (legacy parity): the envelope ID is ALWAYS TB_FIRM_CONF.VAN_ORG_CD — never a
        // configured partner id. Per-leg legacy evidence in EfsProperties' class Javadoc.
        String id = cmd.vanOrgCd();
        return inquireFxRate(cmd, apiKey, id, tno);
    }

    /**
     * Run the {@code WGVN_EFS_204} applied-rate inquiry (guide v1.2.6 §5.6) and return
     * the FX response. Mirrors {@code NAPAS_REAL_TIME_BATCH.java:335-413}: build
     * {@link Efs204Request} from the same withdrawal / target / amount fields the 202
     * transfer uses, run it on its own envelope (fresh {@code TNO}), and parse the
     * response.
     *
     * <p>A non-{@code 0000} 204 result (or a null/no-response envelope) is fatal — the
     * legacy batch does not proceed to the 202 transfer without a confirmed rate
     * (it logs + alerts + skips the target), so the synchronous refactor surfaces
     * {@code WBE_EFS_GATEWAY_ERROR}. No DB status write in this phase (D2/D3 scope).
     */
    private Efs204Response inquireFxRate(RealtimeTransferCommand cmd, String apiKey, String id, String tno) {
        Efs204Request request = new Efs204Request(
                cmd.bankCd(), cmd.prtnId(),
                cmd.macctNo(),                 // withd_acct_no  ← legacy WDRL_ACT_NO (= 202 macct_no)
                nullToEmpty(cmd.withdCurrenCd()),
                cmd.tarFincMdaNo(),            // dep_acct_no    ← legacy FINC_MDA_NO (= 202 tar_finc_mda_no)
                cmd.amt(), cmd.dcmAmt(),
                cmd.currenCd());               // tran_curren_cd ← legacy TRSC_CCY_CD (= 202 curren_cd)
        String businessJson = serialize(request);

        // A1 (audit X-2): the 204 envelope TNO is the caller-allocated TRAN_SEQ (legacy ccy_exc_seq),
        // not a UUID — keeps the gateway record correlated to the 204 TB_FIRM_TRAN row.
        String rqDtime = LocalDateTime.now(ICT).format(RQ_DTIME_FMT);
        String ev = efsCryptoService.encryptEv(businessJson, apiKey, rqDtime);
        String vv = efsCryptoService.buildVv(businessJson, apiKey);

        EfsEnvelope requestEnvelope = new EfsEnvelope(
                id, rqDtime, null, tno, ev, vv,
                EfsEnvelope.EM_AES, EfsEnvelope.VM_HMAC_SHA256, null, null);

        EfsEnvelope response = efsApiClient.send(EfsApiClient.EFS_204, requestEnvelope);

        // D — legacy parity (NAPAS_REAL_TIME_BATCH:358-362): a null / no-response 204 envelope is
        // NOT a definitive failure. Legacy `continue`s and leaves the 204 row at PROC_STAT='6' for a
        // later re-inquiry — it does NOT mark it '3'. Signal no-response by returning null; only an
        // explicit non-0000 response (or a VV-verify failure) is a definitive '3' error.
        if (response == null) {
            log.warn("EFS 204 FX inquiry no-response (null envelope) vanOrgCd={} macct={}",
                    cmd.vanOrgCd(), mask(cmd.macctNo()));
            return null;
        }
        String rc = response.rc();
        if (!FirmBankingResultCd.isSuccess(rc)) {
            log.warn("EFS 204 FX inquiry failed vanOrgCd={} macct={} acct={} rc={}",
                    cmd.vanOrgCd(), mask(cmd.macctNo()), mask(cmd.tarFincMdaNo()), rc);
            throw new BusinessException("WBE_EFS_GATEWAY_ERROR", rc);
        }

        // A3 (audit H-5): verify the 204 response VV before decrypt (legacy VerifyMac → G003). A
        // failed rate inquiry must not proceed to the 202 with an unverified rate.
        if (!efsCryptoService.verifyVv(response.ev(), response.vv(), apiKey)) {
            log.warn("EFS 204 response VV verification failed vanOrgCd={} macct={}",
                    cmd.vanOrgCd(), mask(cmd.macctNo()));
            throw new BusinessException("WBE_EFS_GATEWAY_ERROR", FirmBankingResultCd.ENC_VRFY_ERR);
        }

        String responseJson = efsCryptoService.decryptEv(response.ev(), apiKey);
        log.debug("EFS 204 FX inquiry ok vanOrgCd={} macct={} rc={}",
                cmd.vanOrgCd(), mask(cmd.macctNo()), rc);
        return deserialize(responseJson, Efs204Response.class);
    }

    /** Legacy parity: FX block fields are sent as empty strings, never null/omitted. */
    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            // Never include the payload in the message (it carries account data).
            throw new IllegalStateException("Failed to serialise EFS request", e);
        }
    }

    private <T> T deserialize(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse EFS response", e);
        }
    }

    /** Mask all but the last 4 chars of an account number for logs. */
    private String mask(String acct) {
        if (acct == null || acct.length() <= 4) {
            return "****";
        }
        return "****" + acct.substring(acct.length() - 4);
    }
}
