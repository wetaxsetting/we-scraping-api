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
 * <p>Flow (mirrors {@link com.kosign.wecms.domain.payment.dispatch.recipient.RecipientLookupService}):
 * build {@link Efs202Request} → serialise (global SNAKE_CASE) → resolve key by
 * {@code vanOrgCd} ({@link VanApiKeyService}) → build {@code EV}/{@code VV}
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

        Efs202Request request = new Efs202Request(
                cmd.bankCd(), cmd.prtnId(), cmd.currenCd(), cmd.amt(), cmd.dcmAmt(),
                cmd.tarFincMdaDiv(), cmd.macctNo(), cmd.tarBankCd(), cmd.tarFincMdaNo(),
                cmd.fincMdaOwnerNm(), cmd.tranUse(), cmd.antDivCd());
        String businessJson = serialize(request);

        String rqDtime = LocalDateTime.now(ICT).format(RQ_DTIME_FMT);
        String tno = newTno(rqDtime);
        String ev = efsCryptoService.encryptEv(businessJson, apiKey, rqDtime);
        String vv = efsCryptoService.buildVv(businessJson, apiKey);
        String id = StringUtils.hasText(efsProperties.getPartnerId())
                ? efsProperties.getPartnerId() : cmd.vanOrgCd();

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

    private String newTno(String rqDtime) {
        String tno = rqDtime + UUID.randomUUID().toString().replace("-", "");
        return tno.length() > TNO_MAX ? tno.substring(0, TNO_MAX) : tno;
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            // Never include the payload in the message (it carries account data).
            throw new IllegalStateException("Failed to serialise EFS 202 request", e);
        }
    }

    private <T> T deserialize(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse EFS 202 response", e);
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
