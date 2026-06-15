package com.kosign.wecms.domain.payment.dispatch.recipient;

import com.kosign.wecms.domain.payment.dispatch.entity.TbFirmTran;
import com.kosign.wecms.domain.payment.dispatch.entity.TbFirmTranId;
import com.kosign.wecms.domain.payment.dispatch.enums.ProcStat;
import com.kosign.wecms.domain.payment.dispatch.repository.TbFirmTranRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * {@code TB_FIRM_TRAN} audit lifecycle for the standalone depositor-inquiry
 * (WGVN_EFS_201) leg — refactor of legacy {@code WB_00000100}'s
 * {@code TB_FIRM_TRAN_C001} insert + {@code TB_FIRM_TRAN_U002} update.
 *
 * <p>Both methods run in their own {@code REQUIRES_NEW} transaction — mirroring
 * legacy's three independent {@code beginTransaction…commit…endTransaction}
 * units and the {@link com.kosign.wecms.domain.auth.service.LoginHistoryService}
 * precedent. The request row is durably committed <b>before</b> the network call,
 * so a crash mid-call still leaves a {@code PROC_STAT='6'} audit trail, and the
 * result update commits independently of the caller's transaction.
 *
 * <h2>Legacy parity notes</h2>
 * <ul>
 *   <li>The 201 C001 insert ({@code IDO.TB_FIRM_TRAN_C001.xml}) is the shared
 *       insert used by 201/202/204; for the inquiry leg only the audit columns
 *       are populated — amount / usage / balance / FX columns stay null.</li>
 *   <li>{@code TB_FIRM_TRAN_U002} ({@code IDO.TB_FIRM_TRAN_U002.xml}) updates
 *       exactly {@code RS_DTTM} / {@code PROC_STAT} / {@code RS_CD} / {@code RS_MSG}
 *       / {@code FINC_MDA_OWNER_NM} (coalesced). It deliberately does <b>not</b>
 *       update {@code ANT_DIV_CD} — the legacy SQL omits it even though the Java
 *       puts it into the IDO input ({@code WB_00000100.java:181}). This service
 *       preserves that: {@link #recordResult} never touches {@code ANT_DIV_CD}.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FirmTranInquiryAuditService {

    private static final DateTimeFormatter DTTM14 = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final ZoneId ICT = ZoneId.of("Asia/Ho_Chi_Minh");

    /** Legacy {@code WB_00000100.java:90} — {@code TRAN_TP} "201" = 예금주 조회 (depositor inquiry). */
    static final String TRAN_TP_INQUIRY = "201";

    private final TbFirmTranRepository firmTranRepository;

    /**
     * Insert the 201 inquiry request row at {@code PROC_STAT='6'} (legacy
     * {@code TB_FIRM_TRAN_C001}). The PK {@code (TRAN_DT, BIZ_KEY, TRAN_SEQ)} is
     * supplied by the orchestrator, which allocated {@code TRAN_SEQ} from the
     * global {@code firm_seq_no} sequence.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordRequest(String tranDt, String bizKey, String tranSeq,
                              String tranTm, String rqDttm, RecipientLookupCommand cmd) {
        TbFirmTran row = new TbFirmTran();
        row.setTranDt(tranDt);
        row.setBizKey(bizKey);
        row.setTranSeq(tranSeq);
        row.setTranTp(TRAN_TP_INQUIRY);
        row.setTranTm(tranTm);
        row.setRqDttm(rqDttm);
        row.setProcStat(ProcStat.REQ.code());            // '6' — in progress
        row.setVanOrgCd(cmd.vanOrgCd());                 // ID
        row.setOutBankCd(cmd.bankCd());                  // BANK_CD
        row.setBankPrtnId(cmd.prtnId());                 // PRTN_ID
        row.setWdrlActNo(cmd.withdAcctNo());             // WITHD_ACCT_NO
        row.setTarBankCd(cmd.tarBankCd());               // TAR_BANK_CD
        row.setFincMdaDiv(cmd.tarFincMdaDiv());          // 'A' (account)
        row.setFincMdaNo(cmd.tarFincMdaNo());            // TAR_FINC_MDA_NO
        // FINC_MDA_OWNER_NM stays null at request time — the NAPAS inquiry is what
        // RESOLVES the owner name; the U002 success update fills it in (legacy
        // WB_00000100 inserts input.FINC_MDA_OWNER_NM, which is empty for these callers).
        firmTranRepository.saveAndFlush(row);
    }

    /**
     * Apply the inquiry outcome (legacy {@code TB_FIRM_TRAN_U002}). Updates
     * {@code RS_DTTM} (now), {@code PROC_STAT}, {@code RS_CD}, {@code RS_MSG} and
     * {@code FINC_MDA_OWNER_NM} (only when {@code ownerNm} is non-blank — legacy
     * {@code coalesce(nullif(:FINC_MDA_OWNER_NM,''), FINC_MDA_OWNER_NM)}).
     * {@code ANT_DIV_CD} is never written (legacy parity). A missing row is logged
     * and ignored (should be impossible — {@link #recordRequest} just committed it).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordResult(String tranDt, String bizKey, String tranSeq,
                             String procStat, String rsCd, String rsMsg, String ownerNm) {
        TbFirmTran row = firmTranRepository
                .findById(new TbFirmTranId(tranDt, bizKey, tranSeq))
                .orElse(null);
        if (row == null) {
            log.warn("recipient-inquiry result update — TB_FIRM_TRAN row missing "
                    + "tranDt={} bizKey={} tranSeq={}", tranDt, bizKey, tranSeq);
            return;
        }
        row.setProcStat(procStat);
        row.setRsCd(rsCd);
        row.setRsMsg(rsMsg);
        row.setRsDttm(LocalDateTime.now(ICT).format(DTTM14));
        if (StringUtils.hasText(ownerNm)) {
            row.setFincMdaOwnerNm(ownerNm.trim());
        }
        firmTranRepository.save(row);
    }
}
