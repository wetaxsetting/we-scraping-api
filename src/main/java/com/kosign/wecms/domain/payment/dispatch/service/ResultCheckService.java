package com.kosign.wecms.domain.payment.dispatch.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.domain.payment.dispatch.constant.FirmBankingResultCd;
import com.kosign.wecms.domain.payment.dispatch.van.client.EfsApiClient;
import com.kosign.wecms.domain.payment.dispatch.van.client.EfsProperties;
import com.kosign.wecms.domain.payment.dispatch.van.client.VanApiKeyService;
import com.kosign.wecms.domain.payment.dispatch.van.crypto.EfsCryptoService;
import com.kosign.wecms.domain.payment.dispatch.van.dto.Efs203Request;
import com.kosign.wecms.domain.payment.dispatch.van.dto.Efs203Response;
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
 * {@code WGVN_EFS_203} — processing-result inquiry (REST API guide ver 1.2.2
 * §5.5). Refactor of legacy {@code PROCESSING_RESULT_CHECK_BATCH}.
 *
 * <p>Two callers:
 * <ul>
 *   <li>{@link RealtimeTransferService} — invokes {@link #inquire} when a 202
 *       transfer returns {@code RC=G999} (no definitive response) to confirm the
 *       true outcome (§5.4 recovery rule).</li>
 *   <li>The scheduled poll ({@link com.kosign.wecms.domain.payment.dispatch.scheduler.ResultCheckJob})
 *       — would call {@link #inquire} for aged {@code TRAN_TP='202'}/{@code PROC_STAT='6'}
 *       rows. <b>Still gated (D4/B7):</b> {@link #runOnce()} stays a no-op and the
 *       cron defaults to "never fire" so the refactor never co-processes live
 *       rows on the shared dev DB.</li>
 * </ul>
 *
 * <p>Flow (mirrors {@link com.kosign.wecms.domain.payment.dispatch.recipient.RecipientLookupService}):
 * build {@link Efs203Request} → serialise (global SNAKE_CASE) → resolve key by
 * {@code vanOrgCd} ({@link VanApiKeyService}) → build {@code EV}/{@code VV}
 * ({@link EfsCryptoService}) → POST via {@link EfsApiClient#send EfsApiClient.EFS_203}
 * → on {@code RC=0000}, decrypt the response {@code EV} and map the 203 fields.
 *
 * <p><b>No DB status writes in this phase (D3).</b> {@code PROC_STAT} is not
 * mutated here; the caller owns that decision once the {@code TB_FIRM_TRAN} seam
 * is wired. Logging never emits {@code API_KEY}, {@code EV}, {@code VV}, the
 * decrypted payload, or full account numbers.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResultCheckService {

    private static final DateTimeFormatter RQ_DTIME_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final ZoneId ICT = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final int TNO_MAX = 50;

    private final EfsApiClient efsApiClient;
    private final EfsCryptoService efsCryptoService;
    private final VanApiKeyService vanApiKeyService;
    private final EfsProperties efsProperties;
    private final ObjectMapper objectMapper;

    /**
     * Confirm the true outcome of an original transaction via {@code WGVN_EFS_203}.
     *
     * @return the decrypted 203 result on {@code RC=0000}
     * @throws IllegalArgumentException on missing required input
     * @throws IllegalStateException    when the API key is not provisioned (B2,
     *                                  surfaced from {@link VanApiKeyService})
     * @throws BusinessException        ({@code WBE_EFS_GATEWAY_ERROR}) on a
     *                                  non-{@code 0000} gateway result
     */
    public ResultCheckInquiryResult inquire(ResultCheckInquiryCommand cmd) {
        validate(cmd);

        // Resolve the per-VAN key first — a missing key (B2) must surface here,
        // before any envelope is built or any request is sent.
        String apiKey = vanApiKeyService.getApiKey(cmd.vanOrgCd());

        Efs203Request request = new Efs203Request(
                cmd.bankCd(), cmd.prtnId(), cmd.orgTranDt(), cmd.orgTno(), cmd.macctNo());
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

        EfsEnvelope response = efsApiClient.send(EfsApiClient.EFS_203, requestEnvelope);

        String rc = response == null ? null : response.rc();
        if (response == null || !FirmBankingResultCd.isSuccess(rc)) {
            log.warn("EFS 203 inquiry failed vanOrgCd={} orgTno={} macct={} rc={}",
                    cmd.vanOrgCd(), cmd.orgTno(), mask(cmd.macctNo()), rc);
            throw new BusinessException("WBE_EFS_GATEWAY_ERROR", rc);
        }

        String responseJson = efsCryptoService.decryptEv(response.ev(), apiKey);
        Efs203Response parsed = deserialize(responseJson, Efs203Response.class);

        log.debug("EFS 203 inquiry ok vanOrgCd={} orgTno={} macct={} orgRespCd={}",
                cmd.vanOrgCd(), cmd.orgTno(), mask(cmd.macctNo()), parsed.orgRespCd());
        return ResultCheckInquiryResult.from(parsed);
    }

    /**
     * One scheduled result-check pass. <b>SKELETON — guarded no-op</b> so the
     * scheduler wiring (lock + cadence) can be proven without touching live rows.
     * Enabling the cron + the {@code TB_FIRM_TRAN} row selection that drives
     * {@link #inquire} is gated on D4/B7.
     */
    public void runOnce() {
        // TODO(D4/B7): select aged TRAN_TP='202'/PROC_STAT='6' rows and call inquire(...).
        // Intentionally a no-op in this phase: enabling against the shared dev DB is gated on D4/B7.
        log.debug("[skeleton] ResultCheckService.runOnce — no-op (poll wiring gated on D4/B7).");
    }

    private void validate(ResultCheckInquiryCommand cmd) {
        if (cmd == null) {
            throw new IllegalArgumentException("ResultCheckInquiryCommand is required");
        }
        if (!StringUtils.hasText(cmd.vanOrgCd())) {
            throw new IllegalArgumentException("vanOrgCd is required");
        }
        if (!StringUtils.hasText(cmd.orgTranDt())) {
            throw new IllegalArgumentException("orgTranDt is required");
        }
        if (!StringUtils.hasText(cmd.orgTno())) {
            throw new IllegalArgumentException("orgTno is required");
        }
        if (!StringUtils.hasText(cmd.macctNo())) {
            throw new IllegalArgumentException("macctNo is required");
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
            throw new IllegalStateException("Failed to serialise EFS 203 request", e);
        }
    }

    private <T> T deserialize(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse EFS 203 response", e);
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
