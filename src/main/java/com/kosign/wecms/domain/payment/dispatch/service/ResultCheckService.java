package com.kosign.wecms.domain.payment.dispatch.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.legacy.LegacyFunctionRepository;
import com.kosign.wecms.domain.payment.dispatch.alert.OpsAlertService;
import com.kosign.wecms.domain.payment.dispatch.constant.FirmBankingResultCd;
import com.kosign.wecms.domain.payment.dispatch.van.client.EfsApiClient;
import com.kosign.wecms.domain.payment.dispatch.van.client.EfsProperties;
import com.kosign.wecms.domain.payment.dispatch.van.client.VanApiKeyService;
import com.kosign.wecms.domain.payment.dispatch.van.crypto.EfsCryptoService;
import com.kosign.wecms.domain.payment.dispatch.entity.TbFirmTran;
import com.kosign.wecms.domain.payment.dispatch.enums.ProcStat;
import com.kosign.wecms.domain.payment.dispatch.repository.TbFirmConfRepository;
import com.kosign.wecms.domain.payment.dispatch.repository.TbFirmTranRepository;
import com.kosign.wecms.domain.payment.dispatch.van.dto.Efs203Request;
import com.kosign.wecms.domain.payment.dispatch.van.dto.Efs203Response;
import com.kosign.wecms.domain.payment.dispatch.van.dto.EfsEnvelope;
import com.kosign.wecms.domain.setting.firmbankingsettings.entity.TbFirmConf;
import com.kosign.wecms.domain.setting.firmbankingsettings.entity.TbFirmConfId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
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
    /** Processing-result-inquiry leg ({@code WCConst.TRAN_TP_PROCESS_INQ}) — the 203 audit row. */
    private static final String TRAN_TP_PROCESS_INQ = "203";
    /** Legacy {@code WCConst.ORG_ST_OK} — connecting-org state "거래가능 / available". */
    private static final String ORG_ST_OK = "1";
    /** Legacy re-check window: only transfers older than 2 minutes are inquired. */
    private static final int RESULT_CHECK_DELAY_MINUTES = 2;

    private final EfsApiClient efsApiClient;
    private final EfsCryptoService efsCryptoService;
    private final VanApiKeyService vanApiKeyService;
    private final EfsProperties efsProperties;
    private final ObjectMapper objectMapper;
    private final TbFirmTranRepository tbFirmTranRepository;
    private final OpsAlertService opsAlertService;
    /** B3 (audit M-4): allocates the fresh {@code TRAN_SEQ} for each 203 audit row (firm_seq_no funnel). */
    private final LegacyFunctionRepository legacyFunctions;
    /** B4 (audit M-6): source of the connecting-org state ({@code ORG_STAT}) gate. */
    private final TbFirmConfRepository firmConfRepository;
    /** B5 (audit M-5): per-bank inter-request pacing for the scan loop. */
    private final DispatchPacer dispatchPacer;
    /** Item 1 (audit M-7): atomic original-firm-tran + pay-target finalize (one REQUIRES_NEW tx per row). */
    private final DispatchResultTxService dispatchResultTxService;

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
        return inquire(cmd, null);
    }

    /**
     * As {@link #inquire(ResultCheckInquiryCommand)} but with a caller-allocated envelope
     * {@code TNO} — the fresh {@code TB_FIRM_TRAN.TRAN_SEQ} of the {@code TRAN_TP='203'}
     * audit row the scheduled poll creates (B3 / audit X-2). Sending the inquiry's own
     * allocated sequence as the gateway {@code TNO} (never a UUID) keeps the 203 request
     * correlated 1:1 to its ledger row, exactly as legacy
     * {@code PROCESSING_RESULT_CHECK_BATCH} (envelope {@code TNO = tran_seq}). A blank
     * {@code inquiryTno} falls back to a generated one (the direct-call convenience path).
     */
    public ResultCheckInquiryResult inquire(ResultCheckInquiryCommand cmd, String inquiryTno) {
        validate(cmd);

        // Resolve the per-VAN key first — a missing key (B2) must surface here,
        // before any envelope is built or any request is sent.
        String apiKey = vanApiKeyService.getApiKey(cmd.vanOrgCd());

        Efs203Request request = new Efs203Request(
                cmd.bankCd(), cmd.prtnId(), cmd.orgTranDt(), cmd.orgTno(), cmd.macctNo());
        String businessJson = serialize(request);

        String rqDtime = LocalDateTime.now(ICT).format(RQ_DTIME_FMT);
        String tno = StringUtils.hasText(inquiryTno) ? inquiryTno : newTno(rqDtime);
        String ev = efsCryptoService.encryptEv(businessJson, apiKey, rqDtime);
        String vv = efsCryptoService.buildVv(businessJson, apiKey);
        String id = StringUtils.hasText(efsProperties.getPartnerId())
                ? efsProperties.getPartnerId() : cmd.vanOrgCd();

        EfsEnvelope requestEnvelope = new EfsEnvelope(
                id, rqDtime, null, tno, ev, vv,
                EfsEnvelope.EM_AES, EfsEnvelope.VM_HMAC_SHA256, null, null);

        EfsEnvelope response = efsApiClient.send(EfsApiClient.EFS_203, requestEnvelope);

        String rc = response == null ? null : response.rc();

        // B1 (audit H-1): RC=0001 "원거래 없음 / original transaction not found" is a DEFINITIVE
        // outcome, not a transient gateway error. Legacy PROCESSING_RESULT_CHECK accepts 0001
        // alongside 0000 and marks the original FAIL. Return a not-found result (no EV payload to
        // decrypt) so reflectOutcome finalizes PROC_STAT='3' — never re-inquiring the row forever.
        if (response != null && FirmBankingResultCd.NO_FOUND.equals(rc)) {
            log.info("EFS 203 inquiry: original not found (0001) vanOrgCd={} orgTno={} macct={}",
                    cmd.vanOrgCd(), cmd.orgTno(), mask(cmd.macctNo()));
            return ResultCheckInquiryResult.notFound(cmd.orgTranDt(), cmd.orgTno());
        }

        if (response == null || !FirmBankingResultCd.isSuccess(rc)) {
            // G999 / other / no-response = transient: throw so the caller leaves PROC_STAT='6'
            // and the next pass retries (never a definitive result).
            log.warn("EFS 203 inquiry failed vanOrgCd={} orgTno={} macct={} rc={}",
                    cmd.vanOrgCd(), cmd.orgTno(), mask(cmd.macctNo()), rc);
            throw new BusinessException("WBE_EFS_GATEWAY_ERROR", rc);
        }

        // A3 (audit H-5): verify the response VV before decrypting/trusting EV (legacy
        // SecurityUtil.VerifyMac → G003). A tampered/corrupt envelope must not be processed;
        // surface G003 as a gateway error so the row stays '6' and is retried, never mis-mapped.
        if (!efsCryptoService.verifyVv(response.ev(), response.vv(), apiKey)) {
            log.warn("EFS 203 response VV verification failed vanOrgCd={} orgTno={}",
                    cmd.vanOrgCd(), cmd.orgTno());
            throw new BusinessException("WBE_EFS_GATEWAY_ERROR", FirmBankingResultCd.ENC_VRFY_ERR);
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
     * <p><b>Scope note (C4, 2026-06-13; atomicity Item 1, 2026-07-21):</b> the 203-confirmed
     * status is propagated to the originating {@code TB_PAY_TARGET} row as well as the
     * {@code TB_FIRM_TRAN} ledger, now atomically via {@link DispatchResultTxService}
     * (one {@code REQUIRES_NEW} transaction per row). The link is the shared
     * {@code (BIZ_KEY, TRAN_DT, TRAN_SEQ)} triple the C2 write seam established (the 202
     * firm-tran row reuses the pay-target {@code TRAN_SEQ}).
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
            boolean pace;
            try {
                pace = reflectOutcome(row);
            } catch (Exception e) {
                // No-response / gateway error: leave at PROC_STAT='6' for the next pass.
                // Legacy paces on the comms-fail branch (PROCESSING_RESULT_CHECK_BATCH:234).
                log.warn("result-check: 203 inquiry deferred for tranSeq={} ({}) — left pending",
                        row.getTranSeq(), e.getMessage());
                pace = true;
            }
            // B5 (audit M-5): per-bank inter-request pacing (Woori 3000ms, else 100ms), keyed on
            // the aged row's OUT_BANK_CD. Legacy PROCESSING_RESULT_CHECK_BATCH sleeps on the
            // ORG_STAT-skip (:108), comms-fail (:234) and original-fail (:393) branches, but NOT
            // after a clean success (6-1 has no Thread.sleep) — reflectOutcome returns that decision.
            // Scan loop only (the event-driven legs are unpaced by design).
            if (pace) {
                dispatchPacer.pause(row.getOutBankCd());
            }
        }
    }

    /**
     * Process one aged 202 row through the 203 processing-result inquiry, mirroring legacy
     * {@code PROCESSING_RESULT_CHECK_BATCH} row body end-to-end:
     * <ol>
     *   <li><b>B4 (audit M-6) — {@code ORG_STAT} gate.</b> Load {@code TB_FIRM_CONF}
     *       ({@code R001}); when the connecting-org state is not {@code '1'} (available),
     *       skip the row (legacy {@code continue}). A missing conf row is skipped too
     *       (defensive — one anomalous row must not abort the scan).</li>
     *   <li><b>B3 (audit M-4) — 203 audit row.</b> Allocate a fresh {@code TRAN_SEQ}
     *       (firm_seq_no funnel) and insert a {@code TRAN_TP='203'} {@code TB_FIRM_TRAN}
     *       row at {@code PROC_STAT='6'} (legacy {@code C001}), pointing back at the
     *       original via {@code ORG_TRAN_DT}/{@code ORG_TRAN_SEQ}. That fresh sequence is
     *       also the inquiry's gateway {@code TNO}.</li>
     *   <li>Issue the 203 inquiry. On a transport failure the 203 row is finalized FAIL
     *       ({@code U001}) + a failure alert fires, and the exception is rethrown so the
     *       <b>original</b> 202 row is left at {@code '6'} for the next pass (legacy
     *       {@code 5-1 실패} branch — never finalizes the original on a comms failure).</li>
     *   <li>On a definitive result, the 203 row is finalized SUCC ({@code U003}) with the
     *       confirmed detail (curren/amt/owner/remarks/org_proc_tm), then the original 202
     *       row ({@code U004}) + the originating {@code TB_PAY_TARGET} ({@code U001}) are
     *       set to {@code '0'}/{@code '3'} from {@code org_resp_cd}. When the original
     *       transaction failed, a {@code "원거래오류"} failure alert fires (legacy {@code 6-2}).</li>
     * </ol>
     *
     * <p><b>Alerts fire on failure only</b> (H2 / audit M-3): legacy sends a Slack alert in
     * the {@code 5-1} (comms fail) and {@code 6-2} (original-tx fail) branches, never on a
     * clean success. A missing pay-target row is logged and skipped, never failing the
     * firm-tran writes or the rest of the scan.
     *
     * @return whether the caller should apply the per-bank pace after this row — {@code true}
     *         on the ORG_STAT-skip / original-fail branches (legacy sleeps), {@code false} on a
     *         clean success (legacy 6-1 has no sleep) or a missing-conf skip. The comms-fail
     *         branch rethrows, and {@link #runOnce()} paces on the caught exception (legacy :234).
     */
    private boolean reflectOutcome(TbFirmTran row) {
        // B4 (audit M-6): connecting-org state gate (legacy TB_FIRM_CONF_R001 + ORG_STAT check).
        TbFirmConf conf = firmConfRepository
                .findById(new TbFirmConfId(row.getBizKey(), row.getOutBankCd(), row.getWdrlActNo()))
                .orElse(null);
        if (conf == null) {
            log.warn("result-check: no TB_FIRM_CONF for bizKey={} outBankCd={} wdrlActNo={} — row skipped",
                    row.getBizKey(), row.getOutBankCd(), row.getWdrlActNo());
            return false; // no legacy equivalent (legacy would abort on a conf error); do not pace
        }
        if (!ORG_ST_OK.equals(conf.getOrgStat())) {
            log.info("result-check: ORG_STAT={} (not '1') for bizKey={} outBankCd={} — inquiry skipped",
                    conf.getOrgStat(), row.getBizKey(), row.getOutBankCd());
            return true; // legacy PROCESSING_RESULT_CHECK_BATCH:108 paces on the ORG_STAT skip
        }

        String vanOrgCd = conf.getVanOrgCd();
        String bankPrtnId = conf.getBankPrtnId();
        LocalDateTime now = LocalDateTime.now(ICT);
        String tranDt = now.format(DT8);
        String tranTm = now.format(TM6);
        String dttm14 = now.format(RQ_DTIME_FMT);

        // B3 (audit M-4): insert the TRAN_TP='203' inquiry audit row at PROC_STAT='6' (legacy C001),
        // with a fresh firm_seq_no as both its PK and the inquiry's gateway TNO.
        String inqSeq = legacyFunctions.nextTbFirmTranSeq();
        TbFirmTran inq = new TbFirmTran();
        inq.setTranDt(tranDt);
        inq.setBizKey(row.getBizKey());
        inq.setTranSeq(inqSeq);
        inq.setOutBankCd(row.getOutBankCd());
        inq.setBankPrtnId(bankPrtnId);
        inq.setTranTp(TRAN_TP_PROCESS_INQ);
        inq.setTranTm(tranTm);
        inq.setVanOrgCd(vanOrgCd);
        inq.setWdrlActNo(row.getWdrlActNo());
        inq.setTarBankCd(row.getTarBankCd());
        inq.setProcStat(ProcStat.REQ.code());
        inq.setOrgTranDt(row.getTranDt());
        inq.setOrgTranSeq(row.getTranSeq());
        inq.setRqDttm(dttm14);
        tbFirmTranRepository.save(inq);

        ResultCheckInquiryResult res;
        try {
            res = inquire(new ResultCheckInquiryCommand(
                    vanOrgCd, row.getOutBankCd(), bankPrtnId,
                    row.getTranDt(), row.getTranSeq(), row.getWdrlActNo()), inqSeq);
        } catch (BusinessException e) {
            // Legacy 5-1 실패: comms failure → finalize the 203 row FAIL (U001) + failure alert,
            // and leave the ORIGINAL 202 row at '6' by rethrowing (runOnce logs + moves on).
            String rc = e.getArgs() != null && e.getArgs().length > 0 && e.getArgs()[0] != null
                    ? e.getArgs()[0].toString() : FirmBankingResultCd.ETC_ERR;
            inq.setProcStat(ProcStat.FAIL.code());
            inq.setRsCd(rc);
            inq.setRsMsg(e.getCode());
            inq.setRsDttm(LocalDateTime.now(ICT).format(RQ_DTIME_FMT));
            tbFirmTranRepository.save(inq);
            failureAlert(vanOrgCd, "처리결과조회 Batch", row.getOutBankCd(), bankPrtnId,
                    inqSeq, row.getTranSeq(), rc, e.getCode(), null, null);
            throw e;
        }

        boolean success = FirmBankingResultCd.isSuccess(res.orgRespCd());
        String procStat = success ? ProcStat.SUCC.code() : ProcStat.FAIL.code();
        String rsDttm = LocalDateTime.now(ICT).format(RQ_DTIME_FMT);

        // 203 row → SUCC (legacy U003): the inquiry itself succeeded; carry the confirmed detail.
        inq.setProcStat(ProcStat.SUCC.code());
        inq.setRsCd(res.orgRespCd());
        inq.setRsMsg(res.orgRespMsg());
        inq.setTrscCcyCd(res.currenCd());
        inq.setTranAmt(combineAmount(res.amt(), res.dcmAmt()));
        inq.setFincMdaNo(res.tarFincMdaNo());
        inq.setFincMdaOwnerNm(res.fincMdaOwnerNm());
        inq.setWdrlAcctRmrk(res.withdAcctRmrk());
        inq.setTarAcctRmrk(res.tarAcctRmrk());
        inq.setOrgProcTm(res.orgProcTm());
        inq.setOrgRespCd(res.orgRespCd());
        inq.setOrgRespMsg(res.orgRespMsg());
        inq.setRsDttm(rsDttm);
        tbFirmTranRepository.save(inq);

        // Item 1 (audit M-7): finalize the original 202 firm-tran (U004) + the originating
        // TB_PAY_TARGET (U001) atomically in ONE REQUIRES_NEW transaction (legacy one-tx pair).
        // A failure here rolls BOTH back (never firm-tran '0'/'3' while pay-target stays '6') and
        // propagates so runOnce logs it and moves on — one failed row never blocks the others.
        dispatchResultTxService.finalizeOriginalAndPayTarget(
                row.getBizKey(), row.getTranDt(), row.getTranSeq(),
                procStat, res.orgRespCd(), res.orgRespMsg(), rsDttm, res.fincMdaOwnerNm());

        // H2 (audit M-3): failure-only alert. Legacy fires "처리결과조회 Batch 원거래오류" (with the
        // [ORG Result] block) only in the 6-2 original-transaction-failed branch (:391), never on success.
        if (!success) {
            String commsRc = res.orgProcTm() == null && FirmBankingResultCd.NO_FOUND.equals(res.orgRespCd())
                    ? FirmBankingResultCd.NO_FOUND : FirmBankingResultCd.SUCC;
            failureAlert(vanOrgCd, "처리결과조회 Batch 원거래오류", row.getOutBankCd(), bankPrtnId,
                    inqSeq, row.getTranSeq(), commsRc, "", res.orgRespCd(), res.orgRespMsg());
        }

        log.info("result-check: tranSeq={} confirmed orgRespCd={} -> PROC_STAT={} (203 row tranSeq={})",
                row.getTranSeq(), res.orgRespCd(), procStat, inqSeq);

        // Legacy paces on the original-fail branch (:393) but NOT on a clean success (6-1).
        return !success;
    }

    /**
     * Build + send a failure ops alert (legacy {@code PROCESSING_RESULT_CHECK_BATCH:222-232}
     * comms-fail and {@code :378-391} original-tx-fail). The {@code [ORG Result]} block is
     * appended only when {@code orgRc} is supplied (the original-tx-fail case). The
     * {@code [ENV][WAS_NO]} server marker is attached by {@link OpsAlertService}.
     */
    private void failureAlert(String vanOrgCd, String header, String bankCd, String prtnId,
                              String tno, String orgTno, String rc, String rm,
                              String orgRc, String orgRm) {
        StringBuilder sb = new StringBuilder(header)
                .append("\nID : ").append(vanOrgCd)
                .append("\nbank_cd : ").append(bankCd)
                .append("\nprtn_id : ").append(prtnId)
                .append("\nTNO : ").append(tno)
                .append("\nORG TNO : ").append(orgTno)
                .append("\n[Result]")
                .append("\nRC : ").append(rc == null ? "" : rc)
                .append("\nRM : ").append(rm == null ? "" : rm);
        if (orgRc != null) {
            sb.append("\n[ORG Result]")
                    .append("\nORG RC : ").append(orgRc)
                    .append("\nORG RM : ").append(orgRm == null ? "" : orgRm);
        }
        opsAlertService.notify(vanOrgCd, sb.toString());
    }

    /** Combine an integer + decimal-portion amount pair into one {@link BigDecimal} (blank itg → null). */
    private static BigDecimal combineAmount(String itg, String dcm) {
        if (!StringUtils.hasText(itg)) {
            return null;
        }
        String d = StringUtils.hasText(dcm) ? dcm.trim() : "0";
        return new BigDecimal(itg.trim() + "." + d);
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
