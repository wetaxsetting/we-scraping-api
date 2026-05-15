package com.kosign.wecms.domain.payment.dispatch;

import com.kosign.wecms.domain.payment.internaltransfer.entity.ScdulPymnSumrDetl;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

/**
 * Read-only dispatch-table aggregation. Computes the per-batch success
 * and error counts that drive the "Completed N✓ M✗" chips on every
 * Payment Result list (Internal Transfer / Vendor / Expense / Payroll).
 *
 * <p>The query joins {@code SCDUL_PYMN_SUMR_DETL} against the dispatch
 * tables via the shadow keys ({@code PAY_TRAN_DT}, {@code PAY_TRAN_SEQ},
 * {@code PAY_FILE_SEQ_NO}, {@code PAY_SEQ}) stamped on every detail row
 * at dispatch time. Two routes:
 *
 * <ul>
 *   <li><b>API route</b> (VND) — {@code TB_PAY_TARGET}. Success when
 *       {@code PROC_STAT='0'}, error when {@code PROC_STAT='3'}.</li>
 *   <li><b>File route</b> (USD / KRW) — {@code TB_PAY_FILE_MST} +
 *       {@code TB_PAY_FILE_DATA}. Success when both
 *       {@code TPFM.PROC_STAT='0'} AND {@code TPFD.RS_CD='0000'};
 *       error when {@code TPFM.PROC_STAT='3'} OR the RS_CD differs
 *       from {@code '0000'} on a settled file.</li>
 * </ul>
 *
 * <p>Mirrors the legacy {@code IDO.SCDUL_PYMN_SUMR_R007 / R010 / R002}
 * count expressions surfaced on {@code pymn_0010_001_r001} (Internal),
 * {@code pymn_0002_001_r001} (Vendor), {@code pymn_0004_001_r001}
 * (Expense), {@code pymn_0006_001_r001} (Payroll).
 *
 * <p>The dispatch tables ({@code TB_PAY_TARGET} / {@code TB_PAY_FILE_MST} /
 * {@code TB_PAY_FILE_DATA}) are not locally entity-mapped — the query
 * is native and the tables must exist on the connected database. On a
 * fresh local DB without these tables the query throws; the calling
 * service catches and returns empty counts so the page renders without
 * chips (matching Phase A behaviour).
 */
public interface DispatchCountRepository
        extends JpaRepository<ScdulPymnSumrDetl, Object> {

    @Query(value = """
            SELECT
                SPMD.SCDUL_PYMN_NO                                  AS scdul_pymn_no,
                CAST(SUM(
                    CASE
                        WHEN SPMD.PAY_FILE_SEQ_NO IS NOT NULL
                             AND TPFM.PROC_STAT = '0'
                             AND TRIM(COALESCE(TPFD.RS_CD, '')) = '0000' THEN 1
                        WHEN SPMD.PAY_FILE_SEQ_NO IS NULL
                             AND SPMD.PAY_TRAN_SEQ IS NOT NULL
                             AND TPT.PROC_STAT = '0' THEN 1
                        ELSE 0
                    END
                ) AS BIGINT)                                        AS succ_cnt,
                CAST(SUM(
                    CASE
                        WHEN SPMD.PAY_FILE_SEQ_NO IS NOT NULL
                             AND (TPFM.PROC_STAT = '3'
                                  OR (TPFM.PROC_STAT = '0'
                                      AND TRIM(COALESCE(TPFD.RS_CD, '')) NOT IN ('0000'))) THEN 1
                        WHEN SPMD.PAY_FILE_SEQ_NO IS NULL
                             AND SPMD.PAY_TRAN_SEQ IS NOT NULL
                             AND TPT.PROC_STAT = '3' THEN 1
                        ELSE 0
                    END
                ) AS BIGINT)                                        AS err_cnt
            FROM SCDUL_PYMN_SUMR_DETL SPMD
            LEFT JOIN TB_PAY_FILE_MST TPFM
                   ON TPFM.BIZ_KEY      = SPMD.BIZ_KEY
                  AND TPFM.TRAN_DT      = SPMD.PAY_TRAN_DT
                  AND TPFM.FILE_SEQ_NO  = SPMD.PAY_FILE_SEQ_NO
            LEFT JOIN TB_PAY_FILE_DATA TPFD
                   ON TPFD.BIZ_KEY      = SPMD.BIZ_KEY
                  AND TPFD.TRAN_DT      = SPMD.PAY_TRAN_DT
                  AND TPFD.FILE_SEQ_NO  = SPMD.PAY_FILE_SEQ_NO
                  AND TPFD.SEQ          = SPMD.PAY_SEQ
            LEFT JOIN TB_PAY_TARGET TPT
                   ON TPT.BIZ_KEY       = SPMD.BIZ_KEY
                  AND TPT.TRAN_DT       = SPMD.PAY_TRAN_DT
                  AND TPT.TRAN_SEQ      = SPMD.PAY_TRAN_SEQ
            WHERE SPMD.BIZ_KEY = :bizKey
              AND SPMD.SCDUL_PYMN_NO IN (:scdulPymnNos)
            GROUP BY SPMD.SCDUL_PYMN_NO
            """,
            nativeQuery = true)
    List<DispatchCountRow> countByBatch(
            @Param("bizKey")        String bizKey,
            @Param("scdulPymnNos")  Collection<String> scdulPymnNos);
}
