package com.kosign.wecms.domain.payment.dispatch.callback.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.domain.payment.dispatch.callback.dto.BulkReceptionResultRequest;
import com.kosign.wecms.domain.payment.dispatch.callback.dto.EfsCallbackEnvelope;
import com.kosign.wecms.domain.payment.dispatch.constant.FirmBankingResultCd;
import com.kosign.wecms.domain.payment.dispatch.van.client.EfsApiClient;
import com.kosign.wecms.domain.payment.dispatch.van.client.EfsProperties;
import com.kosign.wecms.domain.payment.dispatch.van.client.VanApiKeyService;
import com.kosign.wecms.domain.payment.dispatch.van.crypto.EfsCryptoService;
import com.kosign.wecms.domain.payment.dispatch.van.dto.Efs302Request;
import com.kosign.wecms.domain.payment.dispatch.van.dto.Efs302Response;
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
 * 01301 bulk reception result callback handler — refactor of
 * {@code FILE_DEPOSIT_TRANSFER_API1_act.jsp}.
 *
 * <p>Legacy + confirmed TOBE flow: verify VV → decrypt EV → look up the file
 * ({@code TB_FIRM_FILE_MST_R004}) → reconcile received {@code data_cnt}/{@code data_amt}
 * vs requested {@code REQ_CNT}/{@code REQ_AMT} → advance {@code PROC_STEP 0 → 1}
 * (match → {@code PROC_STAT='6'}, mismatch → {@code '3'} + {@code REG_RSLT_CD='9999'})
 * → write a {@code TB_FIRM_BAT_API_TRAN} (API_TRAN_TP=01301) audit row →
 * <b>on a normal result, issue the 00302 execution instruction inline</b>
 * ("실행지시로 갈음" — absorbs the removed BATCH2).
 *
 * <p><b>D5 scope = the 00302 EFS API request leg only</b> ({@link #issueExecution}).
 * That method builds → encrypts → posts → parses the 302 envelope and is fully
 * offline/mock-testable. It is deliberately <b>isolated</b>: {@link #handle} (the
 * 01301 callback) does NOT call it yet — the K9 trigger/idempotency/recovery
 * decision that must gate the money-moving 00302 is still open.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BulkReceptionResultService {

    private static final DateTimeFormatter RQ_DTIME_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final ZoneId ICT = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final int TNO_MAX = 50;

    private final EfsApiClient efsApiClient;
    private final EfsCryptoService efsCryptoService;
    private final VanApiKeyService vanApiKeyService;
    private final EfsProperties efsProperties;
    private final ObjectMapper objectMapper;

    /**
     * Handle a 01301 reception-result callback. The encrypted {@code envelope} is
     * decrypted/verified into a {@link BulkReceptionResultRequest} before
     * processing. SKELETON — no state change, no 00302 issued.
     */
    public void handle(EfsCallbackEnvelope envelope) {
        // TODO(B2): verify VV + decrypt EV (EfsCryptoService) using TB_VAN_API_INFO.API_KEY
        //           to yield a BulkReceptionResultRequest payload.
        // TODO(K3): the per-line reception result detail lives in the SFTP result file — parsing it
        //           (FixedLengthCodec) is out of scope here.
        // TODO: reconcile data_cnt/data_amt vs TB_FIRM_FILE_MST REQ_CNT/REQ_AMT; advance PROC_STEP 0->1.
        // TODO(K9): on success, idempotently issue 00302 by calling issueExecution(...) (the API leg
        //           is implemented, D5) AND define the recovery backstop for a stranded PROC_STEP='1'
        //           file. The trigger MUST dedupe (TB_FIRM_BAT_API_TRAN + a PROC_STEP state guard) so a
        //           duplicate callback can never issue 00302 twice. Pending Mr. Kang — NOT wired here.
        log.info("[skeleton] BulkReceptionResultService.handle id={} tno={} — orchestration not implemented (TODO K9 trigger/recovery, B2 crypto, K3 result-file)",
                envelope == null ? null : envelope.id(),
                envelope == null ? null : envelope.tno());
        throw new UnsupportedOperationException(
                "BulkReceptionResultService.handle not implemented — foundation skeleton (TODO K9 00302 trigger/recovery, B2 crypto). The 00302 API leg is issueExecution(...).");
    }

    /**
     * The {@code WGVN_EFS_302} bulk-execution <b>API request leg</b> — build →
     * encrypt → POST {@code EFS_302} → parse. <b>Isolated:</b> not called from
     * {@link #handle} yet — the K9 idempotent-trigger + recovery decision must
     * land before this money-moving leg can be fired automatically.
     *
     * <p>The per-line transfer outcomes return later via the 01302 callback + the
     * SFTP result file (K3 + B4); this leg only transmits the header totals.
     *
     * @return the echoed header totals on {@code RC=0000}
     * @throws IllegalArgumentException on missing required input
     * @throws IllegalStateException    when the API key is not provisioned (B2,
     *                                  surfaced from {@link VanApiKeyService})
     * @throws BusinessException        ({@code WBE_EFS_GATEWAY_ERROR}) on a
     *                                  non-{@code 0000} gateway result
     */
    public BulkExecutionApiResult issueExecution(BulkExecutionApiCommand cmd) {
        validate(cmd);

        // Resolve the per-VAN key first — a missing key (B2) must surface here,
        // before any envelope is built or any request is sent.
        String apiKey = vanApiKeyService.getApiKey(cmd.vanOrgCd());

        Efs302Request request = new Efs302Request(
                cmd.bankCd(), cmd.prtnId(), cmd.fileNm(), cmd.dataCnt(), cmd.dataAmt(), cmd.dataDcmAmt());
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

        EfsEnvelope response = efsApiClient.send(EfsApiClient.EFS_302, requestEnvelope);

        String rc = response == null ? null : response.rc();
        if (response == null || !FirmBankingResultCd.isSuccess(rc)) {
            log.warn("EFS 302 execution failed vanOrgCd={} fileNm={} dataCnt={} rc={}",
                    cmd.vanOrgCd(), cmd.fileNm(), cmd.dataCnt(), rc);
            throw new BusinessException("WBE_EFS_GATEWAY_ERROR", rc);
        }

        String responseJson = efsCryptoService.decryptEv(response.ev(), apiKey);
        Efs302Response parsed = deserialize(responseJson, Efs302Response.class);

        log.debug("EFS 302 execution ok vanOrgCd={} fileNm={} dataCnt={} rc={}",
                cmd.vanOrgCd(), cmd.fileNm(), cmd.dataCnt(), rc);
        return BulkExecutionApiResult.from(parsed);
    }

    private void validate(BulkExecutionApiCommand cmd) {
        if (cmd == null) {
            throw new IllegalArgumentException("BulkExecutionApiCommand is required");
        }
        if (!StringUtils.hasText(cmd.vanOrgCd())) {
            throw new IllegalArgumentException("vanOrgCd is required");
        }
        if (!StringUtils.hasText(cmd.fileNm())) {
            throw new IllegalArgumentException("fileNm is required");
        }
        if (!StringUtils.hasText(cmd.dataCnt())) {
            throw new IllegalArgumentException("dataCnt is required");
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
            throw new IllegalStateException("Failed to serialise EFS 302 request", e);
        }
    }

    private <T> T deserialize(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse EFS 302 response", e);
        }
    }
}
