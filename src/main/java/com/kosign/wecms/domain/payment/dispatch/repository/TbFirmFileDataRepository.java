package com.kosign.wecms.domain.payment.dispatch.repository;

import com.kosign.wecms.domain.payment.dispatch.entity.TbFirmFileData;
import com.kosign.wecms.domain.payment.dispatch.entity.TbFirmFileDataId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Repository for {@link TbFirmFileData} (firm-banking dispatch file detail
 * lines). Mirrors legacy {@code IDO.TB_FIRM_FILE_DATA_*} — BATCH1 inserted one
 * row per beneficiary line ({@code C001}); reads are by file identity.
 *
 * <p>PK is {@code (TRAN_DT, FILE_SEQ_NO, FIRM_FILE_NM, SEQ)} — note DEV omits
 * {@code BIZ_KEY} from this PK (see {@link TbFirmFileData}).
 */
public interface TbFirmFileDataRepository
        extends JpaRepository<TbFirmFileData, TbFirmFileDataId> {

    /**
     * Detail lines of one dispatched file, ordered by line sequence.
     *
     * <p><b>Exact {@code FIRM_FILE_NM} match — only valid with the name WeCMS SENT ({@code S_…}).</b>
     * Do NOT call this with a filename taken from a bank callback: the 01302 execution-result callback
     * carries the bank's {@code R_…} result name, which never equals the stored sent name. Use
     * {@link #findForExecutionResult} on that path (audit H-1).
     */
    List<TbFirmFileData> findByTranDtAndFileSeqNoAndFirmFileNmOrderBySeqAsc(
            String tranDt, String fileSeqNo, String firmFileNm);

    /**
     * Legacy {@code TB_FIRM_FILE_DATA_U001} (01302 per-line result reflection, v20251028210943) —
     * the detail-side counterpart of {@link TbFirmFileMstRepository#findForExecutionResult}.
     *
     * <p><b>H-1.</b> Legacy strips the leading 2 characters on <b>both</b> sides of the join
     * ({@code IDO.TB_FIRM_FILE_DATA_U001.xml:34}):
     * <pre>
     * AND substring(A.FIRM_FILE_NM,3,length(A.FIRM_FILE_NM))
     *   = substring(B.FIRM_FILE_NM,3,length(B.FIRM_FILE_NM))
     * </pre>
     * where {@code A} = {@code TB_FIRM_FILE_DATA} (holds the sent {@code S_…} name) and {@code B} =
     * {@code TB_FIRM_FILE_RSLT} (holds the callback's {@code R_…} name). An exact-name match here
     * silently returns zero rows on every real callback, so {@code PROC_RSLT_DIV} / {@code RS_CD}
     * never reach {@code TB_FIRM_FILE_DATA} — and, because the {@code TB_PAY_FILE_DATA} write is
     * chained off this lookup via {@code ORG_SEQ}, they never reach the pay-detail rows either. The
     * file still advances to {@code PROC_STEP='3'}, so nothing looks wrong.
     *
     * <p>Join keys mirror legacy's {@code A}-side predicates exactly: {@code TRAN_DT} +
     * {@code FILE_SEQ} (the 3-digit round) + the prefix-stripped name. {@code FILE_SEQ_NO} is
     * deliberately <b>not</b> a predicate — legacy does not constrain on it here, and adding it would
     * narrow the match beyond legacy.
     */
    @Query("select d from TbFirmFileData d where d.tranDt = :tranDt "
            + "and d.fileSeq = :fileSeq "
            + "and substring(d.firmFileNm, 3) = substring(:fileNm, 3) "
            + "order by d.seq asc")
    List<TbFirmFileData> findForExecutionResult(@Param("tranDt") String tranDt,
                                               @Param("fileSeq") String fileSeq,
                                               @Param("fileNm") String fileNm);
}
