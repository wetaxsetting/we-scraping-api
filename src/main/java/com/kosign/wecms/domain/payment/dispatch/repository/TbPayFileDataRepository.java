package com.kosign.wecms.domain.payment.dispatch.repository;

import com.kosign.wecms.domain.payment.dispatch.entity.TbPayFileData;
import com.kosign.wecms.domain.payment.dispatch.entity.TbPayFileDataId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TbPayFileDataRepository
        extends JpaRepository<TbPayFileData, TbPayFileDataId> {

    /**
     * Bulk-fetch every dispatch row for one batch's file header. The
     * resolver builds an in-memory map keyed by {@code seq} (stored on
     * the detail line as {@code pay_tran_seq}, varchar) to surface
     * per-line {@code RS_CD} on the Result detail page.
     */
    List<TbPayFileData> findByBizKeyAndTranDtAndFileSeqNo(
            String bizKey, String tranDt, String fileSeqNo);

    /**
     * Network/currency aggregate for the BATCH1 pre-flight validator —
     * a verbatim port of legacy IDO {@code TB_PAY_FILE_DATA_R005}
     * ({@code IDO.TB_PAY_FILE_DATA_R005.xml:24-47}). Read-only; params are
     * bound (no SQL string interpolation). Returns exactly one row.
     */
    @Query(value = """
            SELECT
                COALESCE(SUM(CASE WHEN COALESCE(ant_div_cd,'') = 'E' THEN cnt ELSE 0 END), 0)          AS ebankCnt,
                COALESCE(SUM(CASE WHEN COALESCE(ant_div_cd,'') IN ('E','') THEN 0 ELSE cnt END), 0)     AS ebankOuterCnt,
                COALESCE(SUM(CASE WHEN COALESCE(ant_div_cd,'') = 'V' THEN cnt ELSE 0 END), 0)          AS vcbCnt,
                COALESCE(SUM(CASE WHEN COALESCE(ant_div_cd,'') IN ('V','') THEN 0 ELSE cnt END), 0)     AS vcbOuterCnt,
                COALESCE(SUM(CASE WHEN COALESCE(ant_div_cd,'') = 'N' THEN cnt ELSE 0 END), 0)          AS napasCnt,
                COALESCE(SUM(CASE WHEN COALESCE(ant_div_cd,'') IN ('S','') THEN 0 ELSE cnt END), 0)     AS swiftOuterCnt,
                COUNT(DISTINCT ccy_tp)                                                                  AS ccyCnt,
                COALESCE(SUM(exchange_tran_cnt), 0)                                                     AS exchangeTranCnt
            FROM (
                SELECT ant_div_cd, bank_chnl_div, trsc_ccy_cd || remit_ccy_cd AS ccy_tp, COUNT(*) AS cnt,
                       SUM(CASE WHEN trsc_ccy_cd = remit_ccy_cd THEN 0 ELSE 1 END) AS exchange_tran_cnt
                FROM tb_pay_file_data
                WHERE tran_dt = :tranDt
                  AND biz_key = :bizKey
                  AND file_seq_no = :fileSeqNo
                GROUP BY ant_div_cd, bank_chnl_div, trsc_ccy_cd || remit_ccy_cd
            ) v
            """, nativeQuery = true)
    BulkValidationCounts aggregateValidationCounts(@Param("bizKey") String bizKey,
                                                   @Param("tranDt") String tranDt,
                                                   @Param("fileSeqNo") String fileSeqNo);
}
