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
import java.util.UUID;

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
 *   <li><b>{@code RC=0000}</b> — decrypt + map {@link Efs202Response} → success.</li>
 *   <li><b>{@code RC=G999}</b> (no definitive response) — per §5.4 the caller MUST
 *       confirm the real outcome via {@code WGVN_EFS_203}. Delegates to
 *       {@link ResultCheckService#inquire} using the <i>original</i> transaction
 *       date + {@code TNO} + {@code macct_no}, and returns the 203 result. This is
 *       the legacy "no-response ≠ failure" rule that prevents a double-transfer.</li>
 *   <li><b>other non-{@code 0000}</b> — surfaced as {@code WBE_EFS_GATEWAY_ERROR}.</li>
 * </ul>
 *
 * <p><b>No DB status writes in this phase (D2).</b> The legacy no-response⇒
 * {@code PROC_STAT='6'} persistence (and finalisation to {@code '0'}/{@code '3'})
 * lands when the {@code TB_FIRM_TRAN} status seam is wired — out of D2/D3 scope.
 * Logging never emits {@code API_KEY}, {@code EV}, {@code VV}, the decrypted
 * payload, or full account numbers.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RealtimeTransferService {

    private static final DateTimeFormatter RQ_DTIME_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final ZoneId ICT = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final int TNO_MAX = 50;
    /** {@code org_tran_dt} is the {@code yyyyMMdd} date part of the request datetime. */
    private static final int TRAN_DT_LEN = 8;

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
    private final ResultCheckService resultCheckService;

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
    public RealtimeTransferResult transfer(RealtimeTransferCommand cmd) {
        validate(cmd);

        // Resolve the per-VAN key first — a missing key (B2) must surface here,
        // before any envelope is built or any request is sent.
        String apiKey = vanApiKeyService.getApiKey(cmd.vanOrgCd());
        String id = StringUtils.hasText(efsProperties.getPartnerId())
                ? efsProperties.getPartnerId() : cmd.vanOrgCd();

        // §5.6 Woori-only pre-FX inquiry (WGVN_EFS_204). On the Woori / non-VND path the
        // 204 response FX fields populate the 202 FX block; on the ordinary domestic-VND
        // path the FX fields stay empty strings (legacy NAPAS_REAL_TIME_BATCH.java:329-413
        // initialises them to "" and always includes them in param_202:553-565).
        Efs204Response fx = isWooriFxTransfer(cmd) ? inquireFxRate(cmd, apiKey, id) : null;

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

        String rqDtime = LocalDateTime.now(ICT).format(RQ_DTIME_FMT);
        String tno = newTno(rqDtime);
        String ev = efsCryptoService.encryptEv(businessJson, apiKey, rqDtime);
        String vv = efsCryptoService.buildVv(businessJson, apiKey);

        EfsEnvelope requestEnvelope = new EfsEnvelope(
                id, rqDtime, null, tno, ev, vv,
                EfsEnvelope.EM_AES, EfsEnvelope.VM_HMAC_SHA256, null, null);

        EfsEnvelope response = efsApiClient.send(EfsApiClient.EFS_202, requestEnvelope);
        String rc = response == null ? null : response.rc();

        if (response != null && FirmBankingResultCd.isSuccess(rc)) {
            String responseJson = efsCryptoService.decryptEv(response.ev(), apiKey);
            Efs202Response parsed = deserialize(responseJson, Efs202Response.class);
            log.debug("EFS 202 transfer ok vanOrgCd={} macct={} tarBankCd={} acct={} rc={}",
                    cmd.vanOrgCd(), mask(cmd.macctNo()), cmd.tarBankCd(), mask(cmd.tarFincMdaNo()), rc);
            return RealtimeTransferResult.ofTransfer(parsed);
        }

        if (FirmBankingResultCd.ETC_ERR.equals(rc)) {
            // §5.4 recovery: G999 is "no definitive response", NOT a failure. Confirm the
            // true outcome via 203 using the ORIGINAL tran date + TNO + macct (org_tno = the
            // TNO we just sent). No PROC_STAT write here (D2/D3 scope).
            log.info("EFS 202 returned G999 vanOrgCd={} macct={} tno={} — confirming via 203",
                    cmd.vanOrgCd(), mask(cmd.macctNo()), tno);
            ResultCheckInquiryResult recovery = resultCheckService.inquire(new ResultCheckInquiryCommand(
                    cmd.vanOrgCd(), cmd.bankCd(), cmd.prtnId(),
                    rqDtime.substring(0, TRAN_DT_LEN), tno, cmd.macctNo()));
            return RealtimeTransferResult.ofRecovery(recovery);
        }

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
    private boolean isWooriFxTransfer(RealtimeTransferCommand cmd) {
        String ccy = StringUtils.hasText(cmd.currenCd()) ? cmd.currenCd() : CURRENCY_VND;
        return BANK_CD_WOORI.equals(cmd.bankCd()) && !CURRENCY_VND.equals(ccy);
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
    private Efs204Response inquireFxRate(RealtimeTransferCommand cmd, String apiKey, String id) {
        Efs204Request request = new Efs204Request(
                cmd.bankCd(), cmd.prtnId(),
                cmd.macctNo(),                 // withd_acct_no  ← legacy WDRL_ACT_NO (= 202 macct_no)
                nullToEmpty(cmd.withdCurrenCd()),
                cmd.tarFincMdaNo(),            // dep_acct_no    ← legacy FINC_MDA_NO (= 202 tar_finc_mda_no)
                cmd.amt(), cmd.dcmAmt(),
                cmd.currenCd());               // tran_curren_cd ← legacy TRSC_CCY_CD (= 202 curren_cd)
        String businessJson = serialize(request);

        String rqDtime = LocalDateTime.now(ICT).format(RQ_DTIME_FMT);
        String tno = newTno(rqDtime);
        String ev = efsCryptoService.encryptEv(businessJson, apiKey, rqDtime);
        String vv = efsCryptoService.buildVv(businessJson, apiKey);

        EfsEnvelope requestEnvelope = new EfsEnvelope(
                id, rqDtime, null, tno, ev, vv,
                EfsEnvelope.EM_AES, EfsEnvelope.VM_HMAC_SHA256, null, null);

        EfsEnvelope response = efsApiClient.send(EfsApiClient.EFS_204, requestEnvelope);
        String rc = response == null ? null : response.rc();

        if (response == null || !FirmBankingResultCd.isSuccess(rc)) {
            log.warn("EFS 204 FX inquiry failed vanOrgCd={} macct={} acct={} rc={}",
                    cmd.vanOrgCd(), mask(cmd.macctNo()), mask(cmd.tarFincMdaNo()), rc);
            throw new BusinessException("WBE_EFS_GATEWAY_ERROR", rc);
        }

        String responseJson = efsCryptoService.decryptEv(response.ev(), apiKey);
        log.debug("EFS 204 FX inquiry ok vanOrgCd={} macct={} rc={}",
                cmd.vanOrgCd(), mask(cmd.macctNo()), rc);
        return deserialize(responseJson, Efs204Response.class);
    }

    private String newTno(String rqDtime) {
        String tno = rqDtime + UUID.randomUUID().toString().replace("-", "");
        return tno.length() > TNO_MAX ? tno.substring(0, TNO_MAX) : tno;
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
