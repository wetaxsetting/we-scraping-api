package com.kosign.wecms.domain.payment.dispatch.service;

import com.kosign.wecms.domain.payment.dispatch.entity.TbFirmTran;
import com.kosign.wecms.domain.payment.dispatch.entity.TbFirmTranId;
import com.kosign.wecms.domain.payment.dispatch.entity.TbPayTarget;
import com.kosign.wecms.domain.payment.dispatch.repository.TbFirmTranRepository;
import com.kosign.wecms.domain.payment.dispatch.repository.TbPayTargetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Per-row transactional tail of the processing-result check — commits the confirmed outcome
 * of one original transaction onto {@code TB_FIRM_TRAN} (U004) <b>and</b> {@code TB_PAY_TARGET}
 * (U001) as a single atomic unit.
 *
 * <p>Legacy {@code PROCESSING_RESULT_CHECK_BATCH:298-333/337-372} wraps both updates in one
 * {@code idoCon.beginTransaction()/commit()}. The refactor's scheduled scan
 * ({@link ResultCheckService#runOnce}) is intentionally non-transactional (per-row isolation),
 * so this bean supplies the atomic pair via a {@code REQUIRES_NEW} method:
 * <ul>
 *   <li>Both rows are reloaded <b>by key inside this transaction</b> (the scan holds detached
 *       entities), updated, and committed together.</li>
 *   <li>If either update fails, the transaction rolls back <b>both</b> — the original firm-tran
 *       is never finalized while the pay-target is left at {@code '6'}.</li>
 *   <li>A throw propagates to the caller, which logs it and moves on — one failed row never rolls
 *       back the other rows in the scan (each row gets its own {@code REQUIRES_NEW} unit).</li>
 * </ul>
 *
 * <p>The {@code TRAN_TP='203'} audit row is finalized separately by {@link ResultCheckService}
 * (legacy commits it before this transaction), so it survives even if this atomic pair rolls back.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DispatchResultTxService {

    private final TbFirmTranRepository firmTranRepository;
    private final TbPayTargetRepository payTargetRepository;

    /**
     * Atomically finalize the original 202 firm-tran (U004) + its originating pay-target (U001)
     * with the 203-confirmed outcome.
     *
     * @param bizKey     tenant key of the original transaction
     * @param tranDt     original transaction date
     * @param tranSeq    original transaction sequence (the 202 row PK / pay-target link)
     * @param procStat   final {@code PROC_STAT} ({@code '0'} success / {@code '3'} fail)
     * @param orgRespCd  confirmed original response code (→ RS_CD)
     * @param orgRespMsg confirmed original response message (→ RS_MSG)
     * @param rsDttm     result timestamp {@code yyyyMMddHHmmss}
     * @param ownerNm    confirmed account owner name (coalesced — only overwrites when present)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void finalizeOriginalAndPayTarget(String bizKey, String tranDt, String tranSeq,
                                             String procStat, String orgRespCd, String orgRespMsg,
                                             String rsDttm, String ownerNm) {
        TbFirmTran orig = firmTranRepository
                .findById(new TbFirmTranId(tranDt, bizKey, tranSeq))
                .orElse(null);
        if (orig == null) {
            // The scan selected this row moments ago; a disappearance here is anomalous. Log and
            // return without touching the pay-target (nothing to reconcile it against).
            log.warn("result-check: original TB_FIRM_TRAN vanished for bizKey={} tranDt={} tranSeq={} "
                    + "— finalize skipped", bizKey, tranDt, tranSeq);
            return;
        }

        // Original 202 firm-tran row (legacy TB_FIRM_TRAN_U004).
        orig.setOrgRespCd(orgRespCd);
        orig.setOrgRespMsg(orgRespMsg);
        orig.setProcStat(procStat);
        orig.setRsDttm(rsDttm);
        orig.setRsCd(orgRespCd);   // legacy U004 sets RS_CD on both success (='0000') and fail branches
        orig.setRsMsg(orgRespMsg);
        if (StringUtils.hasText(ownerNm)) {
            orig.setFincMdaOwnerNm(ownerNm);
        }
        firmTranRepository.save(orig);

        // Originating TB_PAY_TARGET (legacy TB_PAY_TARGET_U001). A missing row is a no-op (legacy
        // U001 would update 0 rows without error) and does NOT roll back the firm-tran update.
        TbPayTarget pay = payTargetRepository
                .findByBizKeyAndTranDtAndTranSeq(bizKey, tranDt, tranSeq)
                .orElse(null);
        if (pay == null) {
            log.warn("result-check: no TB_PAY_TARGET for bizKey={} tranDt={} tranSeq={} — firm-tran "
                    + "finalized, pay-target propagation skipped", bizKey, tranDt, tranSeq);
            return;
        }
        pay.setProcStat(procStat);
        pay.setProcDttm(rsDttm);
        if (StringUtils.hasText(ownerNm)) {
            pay.setFincMdaOwnerNm(ownerNm);
        }
        payTargetRepository.save(pay);
        log.info("result-check: finalized original + pay-target atomically tranSeq={} -> PROC_STAT={}",
                tranSeq, procStat);
    }
}
