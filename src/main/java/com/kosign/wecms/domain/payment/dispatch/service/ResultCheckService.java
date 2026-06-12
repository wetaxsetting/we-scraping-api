package com.kosign.wecms.domain.payment.dispatch.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.domain.payment.dispatch.constant.FirmBankingResultCd;
import com.kosign.wecms.domain.payment.dispatch.van.client.EfsApiClient;
import com.kosign.wecms.domain.payment.dispatch.van.client.EfsProperties;
import com.kosign.wecms.domain.payment.dispatch.van.client.VanApiKeyService;
import com.kosign.wecms.domain.payment.dispatch.van.crypto.EfsCryptoService;
import com.kosign.wecms.domain.payment.dispatch.entity.TbFirmTran;
import com.kosign.wecms.domain.payment.dispatch.enums.ProcStat;
import com.kosign.wecms.domain.payment.dispatch.repository.TbFirmTranRepository;
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
import java.util.List;
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
    private static final DateTimeFormatter DT8 = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter TM6 = DateTimeFormatter.ofPattern("HHmmss");
    private static final ZoneId ICT = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final int TNO_MAX = 50;

    /** Real-time transfer leg ({@code TB_FIRM_TRAN.TRAN_TP}) the poll re-checks. */
    private static final String TRAN_TP_TRANSFER = "202";
    /** Legacy re-check window: only transfers older than 2 minutes are inquired. */
    private static final int RESULT_CHECK_DELAY_MINUTES = 2;

    private final EfsApiClient efsApiClient;
    private final EfsCryptoService efsCryptoService;
    private final VanApiKeyService vanApiKeyService;
    private final EfsProperties efsProperties;
    private final ObjectMapper objectMapper;
    private final TbFirmTranRepository tbFirmTranRepository;

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
     * One scheduled result-check pass — refactor of legacy
     * {@code PROCESSING_RESULT_CHECK_BATCH}. Selects no-response real-time transfers
     * ({@code TRAN_TP='202' / PROC_STAT='6'}) older than {@value #RESULT_CHECK_DELAY_MINUTES}
     * minutes, issues a {@code WGVN_EFS_203} inquiry for each, and reflects the
     * confirmed outcome onto the {@code TB_FIRM_TRAN} row ({@code PROC_STAT 6→0/3}).
     *
     * <p>Each row is processed independently: a gateway error or no-response on one
     * row is logged and the row is left at {@code PROC_STAT='6'} for the next pass
     * (legacy parity — never abandons a transfer on a single failed inquiry, never
     * blocks the rest of the batch).
     *
     * <p><b>Operational gating (D4/B7):</b> the {@link ResultCheckJob} cron defaults
     * to "never fire" — this body only runs when an environment that owns the 202
     * traffic explicitly sets {@code wecms.dispatch.result-check.cron}, so the
     * refactor never co-processes live rows on the shared dev DB by accident.
     *
     * <p><b>Scope note:</b> the {@code TB_PAY_TARGET} status propagation (the
     * originating API-path row) is NOT done here — the {@code TB_FIRM_TRAN ↔
     * TB_PAY_TARGET} link is owned by the real-time 202 write seam (D2/D3), which is
     * not yet wired; this pass settles the firm-transaction ledger only.
     */
    public void runOnce() {
        LocalDateTime now = LocalDateTime.now(ICT);
        String today = now.format(DT8);
        String cutoffTm = now.minusMinutes(RESULT_CHECK_DELAY_MINUTES).format(TM6);

        List<TbFirmTran> aged = tbFirmTranRepository
                .findByTranTpAndProcStat(TRAN_TP_TRANSFER, ProcStat.REQ.code()).stream()
                .filter(t -> today.equals(t.getTranDt()))
                .filter(t -> StringUtils.hasText(t.getTranTm()) && t.getTranTm().trim().compareTo(cutoffTm) <= 0)
                .toList();

        if (aged.isEmpty()) {
            log.debug("result-check: no aged 202/PROC_STAT=6 transfers");
            return;
        }
        log.info("result-check: {} aged 202 transfer(s) to inquire", aged.size());
        for (TbFirmTran row : aged) {
            try {
                reflectOutcome(row);
            } catch (Exception e) {
                // No-response / gateway error: leave at PROC_STAT='6' for the next pass.
                log.warn("result-check: 203 inquiry deferred for tranSeq={} ({}) — left pending",
                        row.getTranSeq(), e.getMessage());
            }
        }
    }

    /** Issue the 203 inquiry for one aged 202 row and persist the confirmed outcome.
     *  The single {@code save()} (a detached-entity merge) is atomic on its own. */
    private void reflectOutcome(TbFirmTran row) {
        ResultCheckInquiryResult res = inquire(new ResultCheckInquiryCommand(
                row.getVanOrgCd(), row.getOutBankCd(), row.getBankPrtnId(),
                row.getTranDt(), row.getTranSeq(), row.getWdrlActNo()));

        boolean success = FirmBankingResultCd.isSuccess(res.orgRespCd());
        row.setOrgRespCd(res.orgRespCd());
        row.setOrgRespMsg(res.orgRespMsg());
        row.setProcStat(success ? ProcStat.SUCC.code() : ProcStat.FAIL.code());
        row.setRsDttm(LocalDateTime.now(ICT).format(RQ_DTIME_FMT));
        if (!success) {
            row.setRsCd(res.orgRespCd());
            row.setRsMsg(res.orgRespMsg());
        }
        tbFirmTranRepository.save(row);
        log.info("result-check: tranSeq={} confirmed orgRespCd={} -> PROC_STAT={}",
                row.getTranSeq(), res.orgRespCd(), row.getProcStat());
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
