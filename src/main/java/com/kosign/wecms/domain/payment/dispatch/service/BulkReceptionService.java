package com.kosign.wecms.domain.payment.dispatch.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.domain.payment.dispatch.constant.FirmBankingResultCd;
import com.kosign.wecms.domain.payment.dispatch.van.client.EfsApiClient;
import com.kosign.wecms.domain.payment.dispatch.van.client.EfsProperties;
import com.kosign.wecms.domain.payment.dispatch.van.client.VanApiKeyService;
import com.kosign.wecms.domain.payment.dispatch.van.crypto.EfsCryptoService;
import com.kosign.wecms.domain.payment.dispatch.van.dto.Efs301Request;
import com.kosign.wecms.domain.payment.dispatch.van.dto.Efs301Response;
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
 * Bulk reception — refactor of legacy {@code FILE_DEPOSIT_TRANSFER_BATCH1}.
 *
 * <p>Confirmed TOBE (Mr. Kang 2026-06-06): BATCH1 is <b>removed as a scheduled
 * job</b> and becomes a <b>request-time</b> flow — on a bulk-request insert:
 * validate channels/currency (TB_PAY_FILE_DATA channel counts) → resolve config
 * (TB_FIRM_CONF: VAN_ORG_CD, FILE_TYPE, batch-division) → check operating hours
 * (TB_BATCH_SCHEDULE_INFO/_DTL) → build the fixed-length file → SFTP upload →
 * 00301 reception request → write {@code TB_FIRM_FILE_MST} (PROC_STEP=0/PROC_STAT=6),
 * {@code TB_FIRM_FILE_DATA}, and a {@code TB_FIRM_BAT_API_TRAN} (API_TRAN_TP=00301)
 * audit row.
 *
 * <p><b>D4 scope = the 00301 EFS API request leg only</b> ({@link #issueReception}).
 * That method builds → encrypts → posts → parses the 301 envelope and is fully
 * offline/mock-testable. It is deliberately <b>isolated</b>: {@link #receive} (the
 * orchestrator) does NOT call it yet — the file-gen / SFTP / DB-state steps that
 * must precede it are still blocked.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BulkReceptionService {

    private static final DateTimeFormatter RQ_DTIME_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final ZoneId ICT = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final int TNO_MAX = 50;

    private final EfsApiClient efsApiClient;
    private final EfsCryptoService efsCryptoService;
    private final VanApiKeyService vanApiKeyService;
    private final EfsProperties efsProperties;
    private final ObjectMapper objectMapper;

    /**
     * Entry point for request-time bulk reception of a single bulk-request file.
     * SKELETON — validates nothing and sends nothing yet.
     *
     * @param tranDt   transaction date (YYYYMMDD)
     * @param bizKey   tenant business key
     * @param fileSeqNo bulk file sequence within the tenant/date
     */
    public void receive(String tranDt, String bizKey, String fileSeqNo) {
        // TODO(K3): build fixed-length file via FixedLengthCodec.
        // TODO(B4): SFTP upload the generated file.
        // TODO: after K3 + B4, call issueReception(...) with the file name + header totals,
        //       then write TB_FIRM_FILE_MST (PROC_STEP=0/PROC_STAT=6) + TB_FIRM_BAT_API_TRAN.
        //       The 00301 API leg itself (issueReception) is implemented (D4); the orchestration
        //       around it is intentionally NOT wired here.
        log.info("[skeleton] BulkReceptionService.receive tranDt={} bizKey={} fileSeqNo={} — orchestration not implemented (TODO K3 file-gen, B4 SFTP)",
                tranDt, bizKey, fileSeqNo);
        throw new UnsupportedOperationException(
                "BulkReceptionService.receive not implemented — foundation skeleton (TODO K3 file-gen, B4 SFTP). The 00301 API leg is issueReception(...).");
    }

    /**
     * The {@code WGVN_EFS_301} bulk-reception <b>API request leg</b> — build →
     * encrypt → POST {@code EFS_301} → parse. <b>Isolated:</b> not called from
     * {@link #receive} yet (its K3 file-gen + B4 SFTP prerequisites are unmet).
     *
     * <p>The caller must have already generated (K3) and uploaded (B4) the file
     * named by {@code cmd.fileNm()} — this leg only transmits the header totals.
     *
     * @return the echoed header totals on {@code RC=0000}
     * @throws IllegalArgumentException on missing required input
     * @throws IllegalStateException    when the API key is not provisioned (B2,
     *                                  surfaced from {@link VanApiKeyService})
     * @throws BusinessException        ({@code WBE_EFS_GATEWAY_ERROR}) on a
     *                                  non-{@code 0000} gateway result
     */
    public BulkReceptionApiResult issueReception(BulkReceptionApiCommand cmd) {
        validate(cmd);

        // Resolve the per-VAN key first — a missing key (B2) must surface here,
        // before any envelope is built or any request is sent.
        String apiKey = vanApiKeyService.getApiKey(cmd.vanOrgCd());

        Efs301Request request = new Efs301Request(
                cmd.bankCd(), cmd.prtnId(), cmd.fileNm(), cmd.dataCnt(), cmd.dataAmt(), cmd.dataDcmAmt());
        String businessJson = serialize(request, "301");

        String rqDtime = LocalDateTime.now(ICT).format(RQ_DTIME_FMT);
        String tno = newTno(rqDtime);
        String ev = efsCryptoService.encryptEv(businessJson, apiKey, rqDtime);
        String vv = efsCryptoService.buildVv(businessJson, apiKey);
        String id = StringUtils.hasText(efsProperties.getPartnerId())
                ? efsProperties.getPartnerId() : cmd.vanOrgCd();

        EfsEnvelope requestEnvelope = new EfsEnvelope(
                id, rqDtime, null, tno, ev, vv,
                EfsEnvelope.EM_AES, EfsEnvelope.VM_HMAC_SHA256, null, null);

        EfsEnvelope response = efsApiClient.send(EfsApiClient.EFS_301, requestEnvelope);

        String rc = response == null ? null : response.rc();
        if (response == null || !FirmBankingResultCd.isSuccess(rc)) {
            log.warn("EFS 301 reception failed vanOrgCd={} fileNm={} dataCnt={} rc={}",
                    cmd.vanOrgCd(), cmd.fileNm(), cmd.dataCnt(), rc);
            throw new BusinessException("WBE_EFS_GATEWAY_ERROR", rc);
        }

        String responseJson = efsCryptoService.decryptEv(response.ev(), apiKey);
        Efs301Response parsed = deserialize(responseJson, Efs301Response.class, "301");

        log.debug("EFS 301 reception ok vanOrgCd={} fileNm={} dataCnt={} rc={}",
                cmd.vanOrgCd(), cmd.fileNm(), cmd.dataCnt(), rc);
        return BulkReceptionApiResult.from(parsed);
    }

    private void validate(BulkReceptionApiCommand cmd) {
        if (cmd == null) {
            throw new IllegalArgumentException("BulkReceptionApiCommand is required");
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

    private String serialize(Object value, String svc) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialise EFS " + svc + " request", e);
        }
    }

    private <T> T deserialize(String json, Class<T> type, String svc) {
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse EFS " + svc + " response", e);
        }
    }
}
