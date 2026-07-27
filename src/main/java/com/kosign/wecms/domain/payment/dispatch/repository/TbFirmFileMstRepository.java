package com.kosign.wecms.domain.payment.dispatch.repository;

import com.kosign.wecms.domain.payment.dispatch.entity.TbFirmFileMst;
import com.kosign.wecms.domain.payment.dispatch.entity.TbFirmFileMstId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Repository for {@link TbFirmFileMst} (firm-banking dispatch file header).
 *
 * <p>Finder methods mirror the legacy IDO reads in the bulk flow
 * ({@code WECMS_BATCH_ETC/xml/service/IDO/IDO.TB_FIRM_FILE_MST_R*.xml}):
 * <ul>
 *   <li>{@code R001} — BATCH2 picked files at {@code (TRAN_DT, PROC_STEP, PROC_STAT)};
 *       in the TOBE this selection moves into the 01301 callback / recovery path.</li>
 *   <li>{@code R004} — the 01301 callback looked a file up by
 *       {@code (TRAN_DT, FIRM_FILE_NM, PROC_STEP, PROC_STAT)}.</li>
 * </ul>
 *
 * <p><b>TODO(K9):</b> the legacy {@code R001} selection of files stranded at
 * {@code PROC_STEP='1' / PROC_STAT='6'} was BATCH2's self-healing sweep. Whether
 * the refactor exposes a recovery query here (reconciliation sweep vs outbox vs
 * manual) is the open K9 decision — finders are declared but no scheduled
 * recovery is wired.
 */
public interface TbFirmFileMstRepository
        extends JpaRepository<TbFirmFileMst, TbFirmFileMstId> {

    /** Legacy {@code TB_FIRM_FILE_MST_R001} (BATCH2 reception-confirmed pick). */
    List<TbFirmFileMst> findByTranDtAndProcStepAndProcStat(String tranDt, String procStep, String procStat);

    /** Legacy {@code TB_FIRM_FILE_MST_R004} (01301 callback file lookup). */
    Optional<TbFirmFileMst> findByTranDtAndFirmFileNmAndProcStepAndProcStat(
            String tranDt, String firmFileNm, String procStep, String procStat);

    /**
     * Legacy {@code TB_FIRM_FILE_MST_R006} (01302 execution-result file lookup, v20250910). The bank
     * returns the result file with a DIFFERENT leading 2-char prefix than the sent {@code S_…} name
     * (e.g. {@code R_…}), so the match ignores the first two characters —
     * {@code substring(FIRM_FILE_NM, 3) = substring(:fileNm, 3)} — and is further scoped by
     * {@code VAN_ORG_CD} + {@code FILE_SEQ} (audit C3/M-2). Without this an exact-name match drops
     * the result and strands the file at {@code PROC_STEP='2'}.
     */
    @Query("select f from TbFirmFileMst f where f.tranDt = :tranDt "
            + "and substring(f.firmFileNm, 3) = substring(:fileNm, 3) "
            + "and f.vanOrgCd = :vanOrgCd and f.fileSeq = :fileSeq "
            + "and f.procStep = :procStep and f.procStat = :procStat")
    Optional<TbFirmFileMst> findForExecutionResult(@Param("tranDt") String tranDt,
                                                   @Param("fileNm") String fileNm,
                                                   @Param("vanOrgCd") String vanOrgCd,
                                                   @Param("fileSeq") String fileSeq,
                                                   @Param("procStep") String procStep,
                                                   @Param("procStat") String procStat);

    /** All files for a tenant/date — generic detail/list support. */
    List<TbFirmFileMst> findByTranDtAndBizKey(String tranDt, String bizKey);

    /**
     * All sibling files of one bulk request (same {@code TRAN_DT, BIZ_KEY, FILE_SEQ_NO}).
     * Mirrors the legacy split-file gate (BATCH2 {@code TB_FIRM_FILE_MST_R003}): a single
     * requested file may be split into several bank files, and 00302 execution must not be
     * issued for the group until every sibling has been received. Used by the inline 01301
     * handler to (a) count siblings still at {@code PROC_STEP='0'} and (b) collect the
     * reception-success siblings ({@code PROC_STEP='1' / PROC_STAT='6'}) ready to execute.
     */
    List<TbFirmFileMst> findByTranDtAndBizKeyAndFileSeqNo(String tranDt, String bizKey, String fileSeqNo);

    /**
     * All files for a {@code (TRAN_DT, OUT_BANK_CD, BANK_PRTN_ID)} group — used by
     * the 00301 reception flow to compute the next file round ({@code FILE_SEQ}),
     * mirroring legacy {@code TB_FIRM_FILE_MST_R002} ({@code MAX(FILE_SEQ)+1}).
     */
    List<TbFirmFileMst> findByTranDtAndOutBankCdAndBankPrtnId(String tranDt, String outBankCd, String bankPrtnId);
}
