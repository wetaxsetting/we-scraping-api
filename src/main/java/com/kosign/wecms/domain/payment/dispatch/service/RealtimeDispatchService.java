package com.kosign.wecms.domain.payment.dispatch.service;

import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.domain.payment.dispatch.constant.FirmBankingResultCd;
import com.kosign.wecms.domain.payment.dispatch.entity.TbFirmTran;
import com.kosign.wecms.domain.payment.dispatch.entity.TbPayTarget;
import com.kosign.wecms.domain.payment.dispatch.enums.ProcStat;
import com.kosign.wecms.domain.payment.dispatch.file.VietnameseText;
import com.kosign.wecms.domain.payment.dispatch.van.dto.Efs204Response;
import com.kosign.wecms.domain.payment.dispatch.repository.TbFirmConfRepository;
import com.kosign.wecms.domain.payment.dispatch.repository.TbFirmTranRepository;
import com.kosign.wecms.domain.payment.dispatch.repository.TbPayTargetRepository;
import com.kosign.wecms.domain.setting.firmbankingsettings.entity.TbFirmConf;
import com.kosign.wecms.domain.setting.firmbankingsettings.entity.TbFirmConfId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Real-time single-transfer dispatcher (C2) — refactor of the per-target body of
 * legacy {@code NAPAS_REAL_TIME_BATCH}. Replaces the every-minute
 * {@code TB_PAY_TARGET} pickup loop with a synchronous, after-commit hand-off:
 * {@link RealtimeDispatchRegistrar} schedules {@link #dispatch} for each row a VND
 * payment dispatch just committed at {@code PROC_STAT='9'} (Mr. Kang 2026-06-04
 * TOBE — request-time, not scheduled).
 *
 * <p>Scope (C2 + 204 FX): the API path. When a dispatched {@code TB_PAY_TARGET} row is
 * <b>Woori ({@code 970457}) and non-VND</b>, the {@code WGVN_EFS_204} FX pre-step runs
 * first — a {@code TRAN_TP='204'} {@code TB_FIRM_TRAN} audit row ({@code _U005}) plus the
 * {@code TB_PAY_TARGET} FX columns ({@code _U002}) are persisted before the 202, exactly
 * as legacy {@code NAPAS_REAL_TIME_BATCH} (see {@link #runFxInquiry}). On the ordinary
 * domestic-VND path the 204 pre-step is skipped. The 203 → {@code TB_PAY_TARGET}
 * propagation (the scheduled poll) is C4 and lives in {@link ResultCheckService}.
 *
 * <p>Per-target flow ({@link #dispatch}, one {@code REQUIRES_NEW} transaction):
 * <ol>
 *   <li><b>Gate 1</b> — re-read {@code TB_PAY_TARGET}; dispatch only when
 *       {@code PROC_STAT='9'} (legacy {@code R001}). Absent / already advanced → no-op.</li>
 *   <li>resolve {@code TB_FIRM_CONF} ({@code VAN_ORG_CD} / {@code BANK_PRTN_ID}).</li>
 *   <li><b>Gate 2</b> — if a {@code TB_FIRM_TRAN} row already exists for
 *       {@code (TRAN_DT, BIZ_KEY, TRAN_SEQ)} → already dispatched → no-op.</li>
 *   <li><b>204 FX pre-step (Woori / non-VND only)</b> — run + persist the
 *       {@code WGVN_EFS_204} inquiry ({@link #runFxInquiry}); on failure skip the 202.</li>
 *   <li>insert the {@code TB_FIRM_TRAN} 202 row at {@code PROC_STAT='6'},
 *       <b>reusing the pay-target {@code TRAN_SEQ}</b> as its PK (legacy
 *       {@code dep_seq} link — the basis for the future C4 propagation).</li>
 *   <li>call {@link RealtimeTransferService#transfer} (202, with the built-in
 *       {@code G999}→203 recovery).</li>
 *   <li>map the outcome onto {@code TB_FIRM_TRAN} ({@code _U004}) +
 *       {@code TB_PAY_TARGET} ({@code _U001}) — see {@link #applyOutcome}.</li>
 * </ol>
 *
 * <h2>Transaction model</h2>
 * Runs in its own {@code REQUIRES_NEW} unit, <b>after</b> the payment commit, so an
 * EFS failure can never roll back the user's saved transfer (failure isolation —
 * identical to the File path's {@code BulkReceptionService.receive}). The EFS call
 * runs inside this transaction (the accepted {@code BulkReceptionService} precedent
 * for low-volume R&D). A gateway business error ({@code BusinessException}) is
 * caught and recorded as a committed FAIL state; an infrastructure error (e.g.
 * missing API key, B2) propagates → the unit rolls back → the row stays at
 * {@code '9'} for re-dispatch (the registrar logs it).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RealtimeDispatchService {

    private static final DateTimeFormatter DTTM14 = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final DateTimeFormatter TM6 = DateTimeFormatter.ofPattern("HHmmss");
    private static final ZoneId ICT = ZoneId.of("Asia/Ho_Chi_Minh");

    /** Legacy {@code WCConst.TRAN_TP_TRANSFER} — real-time deposit transfer. */
    private static final String TRAN_TP_TRANSFER = "202";
    /** Legacy {@code WCConst.TRAN_TP_CCY_EXC_INQ} — exchange-transfer (FX) info inquiry. */
    private static final String TRAN_TP_CCY_EXC_INQ = "204";
    /** Legacy {@code WCConst.TAR_FINC_MDA_DIV_ACCT} — target media = account. */
    private static final String FINC_MDA_DIV_ACCOUNT = "A";
    /** Legacy {@code WCConst.BANK_CD_WOORI} — the only bank that runs the 204 FX pre-step. */
    private static final String BANK_CD_WOORI = "970457";
    /** Domestic currency — the 204 pre-step is skipped when the transfer is in VND. */
    private static final String CURRENCY_VND = "VND";

    private final TbPayTargetRepository payTargetRepository;
    private final TbFirmConfRepository firmConfRepository;
    private final TbFirmTranRepository firmTranRepository;
    private final RealtimeTransferService realtimeTransferService;
    private final DispatchKeyGenerator dispatchKeyGenerator;

    /**
     * Dispatch one committed {@code TB_PAY_TARGET} row through {@code WGVN_EFS_202}
     * and persist the firm-tran + pay-target status.
     *
     * @throws IllegalStateException when {@code TB_FIRM_CONF} is missing for the row,
     *                               or the API key is not provisioned (B2) — both roll
     *                               this unit back (caught + logged by the registrar)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void dispatch(String bizKey, String tranDt, String tranSeq) {
        // 1. Re-read the committed row (legacy R001 selects PROC_STAT='9').
        TbPayTarget pay = payTargetRepository
                .findByBizKeyAndTranDtAndTranSeq(bizKey, tranDt, tranSeq)
                .orElse(null);
        if (pay == null) {
            log.info("realtime-dispatch no-op — pay-target not found bizKey={} tranDt={} tranSeq={}",
                    bizKey, tranDt, tranSeq);
            return;
        }
        if (!ProcStat.WAIT.code().equals(pay.getProcStat())) {
            log.info("realtime-dispatch no-op — pay-target not WAIT (procStat={}) bizKey={} tranDt={} tranSeq={}",
                    pay.getProcStat(), bizKey, tranDt, tranSeq);
            return;
        }

        // 2. Idempotency: a firm-tran row for this (tranDt, bizKey, tranSeq) means already dispatched.
        if (firmTranRepository.existsByTranDtAndBizKeyAndTranSeq(tranDt, bizKey, tranSeq)) {
            log.info("realtime-dispatch no-op — TB_FIRM_TRAN already exists (idempotent skip) "
                    + "bizKey={} tranDt={} tranSeq={}", bizKey, tranDt, tranSeq);
            return;
        }

        // 3. Resolve dispatch config (legacy TB_FIRM_CONF_R001).
        TbFirmConf conf = firmConfRepository
                .findById(new TbFirmConfId(bizKey, pay.getOutBankCd(), pay.getWdrlActNo()))
                .orElseThrow(() -> new IllegalStateException(
                        "TB_FIRM_CONF missing for bizKey=" + bizKey + " outBankCd=" + pay.getOutBankCd()
                                + " wdrlActNo=" + pay.getWdrlActNo()));

        LocalDateTime now = LocalDateTime.now(ICT);
        String dttm14 = now.format(DTTM14);
        String tranTm = now.format(TM6);

        // Legacy applies Vietnamese tone normalization before the 202 (NAPAS_REAL_TIME_BATCH:261-270).
        String ownerNm = VietnameseText.replaceTone(pay.getFincMdaOwnerNm());
        String tranUsag = VietnameseText.replaceTone(pay.getTranUsag());

        String[] amt = splitAmount(pay.getTranAmt());
        RealtimeTransferCommand cmd = new RealtimeTransferCommand(
                conf.getVanOrgCd(), pay.getOutBankCd(), conf.getBankPrtnId(),
                pay.getTrscCcyCd(), amt[0], amt[1],
                FINC_MDA_DIV_ACCOUNT, pay.getWdrlActNo(), pay.getTarBankCd(),
                pay.getFincMdaNo(), ownerNm, tranUsag, pay.getAntDivCd(),
                pay.getWithdCcyCd());

        // 4. Woori / non-VND FX pre-step (legacy NAPAS_REAL_TIME_BATCH:286-485). Runs the
        //    WGVN_EFS_204 inquiry, persists a TRAN_TP='204' TB_FIRM_TRAN row (U005) and the
        //    TB_PAY_TARGET FX columns (U002), BEFORE the 202 row — exactly the legacy order.
        //    On the ordinary domestic-VND path this is skipped and fx stays null.
        Efs204Response fx = null;
        if (isWooriNonVnd(pay)) {
            fx = runFxInquiry(bizKey, tranDt, tranTm, dttm14, pay, conf, cmd, ownerNm, tranUsag);
            if (fx == null) {
                // 204 failed — the failed 204 audit row is committed and the 202 is skipped
                // (legacy `continue`); the pay-target is left at WAIT for a later re-dispatch.
                log.warn("realtime-dispatch 204 FX inquiry failed bizKey={} tranSeq={} — 202 skipped",
                        bizKey, tranSeq);
                return;
            }
        }

        // 5. Insert the 202 firm-tran row at PROC_STAT='6' (legacy TB_FIRM_TRAN_C001),
        //    reusing the pay-target TRAN_SEQ as the PK (legacy dep_seq link).
        TbFirmTran firmTran = new TbFirmTran();
        firmTran.setTranDt(tranDt);
        firmTran.setBizKey(bizKey);
        firmTran.setTranSeq(tranSeq);
        firmTran.setOutBankCd(pay.getOutBankCd());
        firmTran.setBankPrtnId(conf.getBankPrtnId());
        firmTran.setTranTp(TRAN_TP_TRANSFER);
        firmTran.setTranTm(tranTm);
        firmTran.setVanOrgCd(conf.getVanOrgCd());
        firmTran.setWdrlActNo(pay.getWdrlActNo());
        firmTran.setTarBankCd(pay.getTarBankCd());
        firmTran.setFincMdaDiv(FINC_MDA_DIV_ACCOUNT);
        firmTran.setFincMdaNo(pay.getFincMdaNo());
        firmTran.setFincMdaOwnerNm(ownerNm);
        firmTran.setAntDivCd(pay.getAntDivCd());
        firmTran.setTrscCcyCd(pay.getTrscCcyCd());
        firmTran.setTranAmt(pay.getTranAmt());
        firmTran.setTranUsag(tranUsag);
        firmTran.setRqDttm(dttm14);
        firmTran.setProcStat(ProcStat.REQ.code());
        firmTranRepository.saveAndFlush(firmTran);

        // 6. EFS 202 (RealtimeTransferService owns 202 + the G999→203 recovery). The Woori
        //    path passes the already-fetched FX so the 204 is never fired twice; the VND path
        //    uses the single-arg call (which fires no 204) — both keep the 202 logic unchanged.
        try {
            RealtimeTransferResult result = (fx != null)
                    ? realtimeTransferService.transfer(cmd, fx)
                    : realtimeTransferService.transfer(cmd);
            applyOutcome(firmTran, pay, result);
        } catch (BusinessException e) {
            // Gateway business error (non-0000, non-G999) → committed FAIL (legacy parity).
            String rc = e.getArgs() != null && e.getArgs().length > 0 && e.getArgs()[0] != null
                    ? e.getArgs()[0].toString() : FirmBankingResultCd.ETC_ERR;
            applyFail(firmTran, pay, rc, e.getCode());
            log.warn("realtime-dispatch 202 failed (rc={}) bizKey={} tranSeq={} — FAIL recorded",
                    rc, bizKey, tranSeq);
        }
    }

    /**
     * True when this pay-target needs the {@code WGVN_EFS_204} FX pre-step: outbound bank
     * is Woori AND the transfer currency is not VND (a blank currency is treated as VND).
     * Mirrors legacy {@code NAPAS_REAL_TIME_BATCH.java:293}.
     */
    private boolean isWooriNonVnd(TbPayTarget pay) {
        String ccy = StringUtils.hasText(pay.getTrscCcyCd()) ? pay.getTrscCcyCd() : CURRENCY_VND;
        return BANK_CD_WOORI.equals(pay.getOutBankCd()) && !CURRENCY_VND.equals(ccy);
    }

    /**
     * Run + persist the Woori {@code WGVN_EFS_204} FX inquiry — refactor of legacy
     * {@code NAPAS_REAL_TIME_BATCH.java:298-485}. Allocates a fresh {@code TRAN_SEQ}
     * (legacy {@code ccy_exc_seq}, independent of the 202's {@code dep_seq}) via the
     * {@code firm_seq_no} funnel, inserts a {@code TRAN_TP='204'} {@code TB_FIRM_TRAN}
     * row at {@code PROC_STAT='6'} (C001), then runs the 204 inquiry:
     * <ul>
     *   <li><b>success</b> — updates the 204 row via {@code _U005}
     *       ({@code PROC_STAT='0'} + {@code RS_CD}/{@code RS_MSG} +
     *       {@code DEP_CCY_CD}/{@code APL_EXC_RATE}/{@code WITHD_AMT}/{@code DEP_AMT}),
     *       then writes the same four FX columns onto the originating
     *       {@code TB_PAY_TARGET} via {@code _U002} (PROC_STAT untouched — the 202 step
     *       owns it). Returns the FX response so the caller feeds it into the 202.</li>
     *   <li><b>failure</b> — updates the 204 row via {@code _U005}
     *       ({@code PROC_STAT='3'} + the gateway RC). The pay-target is left untouched
     *       (legacy {@code continue}). Returns {@code null} so the caller skips the 202.</li>
     * </ul>
     */
    private Efs204Response runFxInquiry(String bizKey, String tranDt, String tranTm, String dttm14,
                                        TbPayTarget pay, TbFirmConf conf, RealtimeTransferCommand cmd,
                                        String ownerNm, String tranUsag) {
        // Independent TRAN_SEQ (legacy ccy_exc_seq = Sequence.getSeq()), via the firm_seq_no funnel.
        String ccyExcSeq = dispatchKeyGenerator.nextTranSeq(bizKey, tranDt);

        // 204 audit row at PROC_STAT='6' (legacy TB_FIRM_TRAN_C001, TRAN_TP='204').
        TbFirmTran fxTran = new TbFirmTran();
        fxTran.setTranDt(tranDt);
        fxTran.setBizKey(bizKey);
        fxTran.setTranSeq(ccyExcSeq);
        fxTran.setOutBankCd(pay.getOutBankCd());
        fxTran.setBankPrtnId(conf.getBankPrtnId());
        fxTran.setTranTp(TRAN_TP_CCY_EXC_INQ);
        fxTran.setTranTm(tranTm);
        fxTran.setVanOrgCd(conf.getVanOrgCd());
        fxTran.setWdrlActNo(pay.getWdrlActNo());
        fxTran.setTarBankCd(pay.getTarBankCd());
        fxTran.setFincMdaDiv(FINC_MDA_DIV_ACCOUNT);
        fxTran.setFincMdaNo(pay.getFincMdaNo());
        fxTran.setFincMdaOwnerNm(ownerNm);
        fxTran.setAntDivCd(pay.getAntDivCd());
        fxTran.setTrscCcyCd(pay.getTrscCcyCd());
        fxTran.setTranAmt(pay.getTranAmt());
        fxTran.setTranUsag(tranUsag);
        fxTran.setRqDttm(dttm14);
        fxTran.setProcStat(ProcStat.REQ.code());
        firmTranRepository.saveAndFlush(fxTran);

        try {
            Efs204Response fx = realtimeTransferService.inquireFxRate(cmd);

            BigDecimal aplExcRate = combineAmount(fx.aplExcRateItg(), fx.aplExcRateDcm());
            BigDecimal withdAmt = combineAmount(fx.withdAmtItg(), fx.withdAmtDcm());
            BigDecimal depAmt = combineAmount(fx.depAmtItg(), fx.depAmtDcm());
            String fxDttm14 = LocalDateTime.now(ICT).format(DTTM14);

            // _U005 success — 204 row to PROC_STAT='0' with the FX result.
            fxTran.setProcStat(ProcStat.SUCC.code());
            fxTran.setRsCd(FirmBankingResultCd.SUCC);
            fxTran.setRsMsg(FirmBankingResultCd.MSG_SUCC);
            fxTran.setRsDttm(fxDttm14);
            fxTran.setDepCcyCd(fx.depCurrenCd());
            fxTran.setAplExcRate(aplExcRate);
            fxTran.setWithdAmt(withdAmt);
            fxTran.setDepAmt(depAmt);
            firmTranRepository.save(fxTran);

            // _U002 — same four FX columns onto TB_PAY_TARGET (PROC_STAT untouched; the 202 owns it).
            pay.setDepCcyCd(fx.depCurrenCd());
            pay.setAplExcRate(aplExcRate);
            pay.setWithdAmt(withdAmt);
            pay.setDepAmt(depAmt);
            pay.setProcDttm(fxDttm14);
            payTargetRepository.save(pay);

            return fx;
        } catch (BusinessException e) {
            // _U005 failure — 204 row to PROC_STAT='3' with the gateway RC (no FX values on a failed
            // inquiry). The pay-target is NOT touched (legacy `continue`); caller skips the 202.
            String rc = e.getArgs() != null && e.getArgs().length > 0 && e.getArgs()[0] != null
                    ? e.getArgs()[0].toString() : FirmBankingResultCd.ETC_ERR;
            fxTran.setProcStat(ProcStat.FAIL.code());
            fxTran.setRsCd(rc);
            fxTran.setRsMsg(e.getCode());
            fxTran.setRsDttm(LocalDateTime.now(ICT).format(DTTM14));
            firmTranRepository.save(fxTran);
            return null;
        }
    }

    /**
     * Map a {@link RealtimeTransferResult} onto the firm-tran ({@code _U004}) and
     * pay-target ({@code _U001}) rows. Direct {@code RC=0000} → SUCCESS with the
     * balance fields; {@code G999} recovery → the confirming 203's {@code orgRespCd}
     * decides SUCCESS/FAIL.
     */
    private void applyOutcome(TbFirmTran firmTran, TbPayTarget pay, RealtimeTransferResult result) {
        boolean success;
        String respCd;
        String respMsg;
        String ownerNm;

        if (result.recovered()) {
            ResultCheckInquiryResult rec = result.recovery();
            success = FirmBankingResultCd.isSuccess(rec.orgRespCd());
            respCd = rec.orgRespCd();
            respMsg = rec.orgRespMsg();
            ownerNm = rec.fincMdaOwnerNm();
        } else {
            success = true; // transfer() only returns a non-recovered result on RC=0000.
            respCd = FirmBankingResultCd.SUCC;
            respMsg = FirmBankingResultCd.MSG_SUCC;
            ownerNm = result.fincMdaOwnerNm();
            firmTran.setBalAmt(combineAmount(result.balAmt(), result.balDcmAmt()));
            firmTran.setBalAmtSign(result.balAmtSign());
            if (StringUtils.hasText(result.antDivCd())) {
                firmTran.setAntDivCd(result.antDivCd());
            }
        }
        writeStatus(firmTran, pay, success ? ProcStat.SUCC.code() : ProcStat.FAIL.code(),
                respCd, respMsg, ownerNm);
    }

    /** Gateway business error → both rows FAIL. */
    private void applyFail(TbFirmTran firmTran, TbPayTarget pay, String rsCd, String rsMsg) {
        writeStatus(firmTran, pay, ProcStat.FAIL.code(), rsCd, rsMsg, null);
    }

    /** Persist the firm-tran U004 + pay-target U001 status (shared tail of every outcome). */
    private void writeStatus(TbFirmTran firmTran, TbPayTarget pay,
                             String procStat, String rsCd, String rsMsg, String ownerNm) {
        String dttm14 = LocalDateTime.now(ICT).format(DTTM14);

        firmTran.setProcStat(procStat);
        firmTran.setRsCd(rsCd);
        firmTran.setRsMsg(rsMsg);
        firmTran.setRsDttm(dttm14);
        firmTranRepository.save(firmTran);

        pay.setProcStat(procStat);
        pay.setProcDttm(dttm14);
        if (StringUtils.hasText(ownerNm)) {
            pay.setFincMdaOwnerNm(ownerNm); // legacy U001 coalesce — only overwrite when present
        }
        payTargetRepository.save(pay);
    }

    /** Split a scale-2 amount into [integer, decimal-portion] strings for the 202 request. */
    private static String[] splitAmount(BigDecimal amt) {
        BigDecimal a = amt == null ? BigDecimal.ZERO : amt;
        String itg = a.toBigInteger().toString();
        String dcm = a.subtract(new BigDecimal(a.toBigInteger())).movePointRight(2).abs().toBigInteger().toString();
        return new String[] {itg, dcm};
    }

    /** Combine integer + decimal-portion balance strings into one {@link BigDecimal} (null itg → null). */
    private static BigDecimal combineAmount(String itg, String dcm) {
        if (!StringUtils.hasText(itg)) {
            return null;
        }
        String d = StringUtils.hasText(dcm) ? dcm.trim() : "0";
        return new BigDecimal(itg.trim() + "." + d);
    }
}
