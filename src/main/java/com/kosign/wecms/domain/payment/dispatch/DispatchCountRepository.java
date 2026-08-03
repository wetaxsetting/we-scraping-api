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
 * <p>Mirrors the legacy {@code IDO.SCDUL_PYMN_SUMR_R007} {@code TOT_TRNS_SUCC_CNT} /
 * {@code TOT_TRNS_ERROR_CNT} expressions (`:58-144`) surfaced on
 * {@code pymn_0010_001_r001} (Internal), {@code pymn_0002_001_r001} (Vendor),
 * {@code pymn_0004_001_r001} (Expense), {@code pymn_0006_001_r001} (Payroll).
 *
 * <h2>Route selection — the <b>master</b> decides (Q-1, fixed 2026-07-30)</h2>
 *
 * Legacy picks the route once per batch, from the master
 * {@code SCDUL_PYMN_SUMR} ({@code SPS}), with an {@code EXISTS} that carries no
 * per-line predicate (`R007:60-65`, `:105-110`):
 *
 * <pre>
 * EXISTS (SELECT 1 FROM TB_PAY_FILE_DATA TPFD
 *          WHERE TPFD.BIZ_KEY     = SPS.BIZ_KEY
 *            AND TPFD.TRAN_DT     = SPS.PAY_TRAN_DT
 *            AND TPFD.FILE_SEQ_NO = SPS.PAY_FILE_SEQ_NO)
 * </pre>
 *
 * and on the file route joins the per-line result with a <b>varchar cast</b>
 * against {@code PAY_TRAN_SEQ} (`R007:78`, `:123` — identical to
 * {@code IDO.SCDUL_PYMN_SUMR_R006.xml:150-153}, the query
 * {@link com.kosign.wecms.domain.payment.dispatch.service.DispatchResultResolver}
 * replaces):
 *
 * <pre>
 * AND CAST(TPFD.SEQ AS VARCHAR) = SPMD.PAY_TRAN_SEQ
 * </pre>
 *
 * <p><b>What was wrong.</b> This query previously routed per line on the
 * <i>detail</i> {@code SPMD.PAY_FILE_SEQ_NO} and joined
 * {@code TPFD.SEQ = SPMD.PAY_SEQ}. Both columns are always {@code NULL}: the
 * dispatch executors stamp only {@code line.payTranSeq} plus
 * {@code master.payFileSeqNo} and explicitly null the other two
 * ({@code InternalTransferDispatchExecutor:276-277,319},
 * {@code ExpenseDispatchExecutor:198-199,241}, {@code PayrollDispatchExecutor:184,284},
 * {@code OverseasDispatchExecutor:215,378}) — which matches legacy JEX data, where
 * {@code SCDUL_PYMN_SUMR_DETL.PAY_SEQ} and {@code .PAY_FILE_SEQ_NO} are null on
 * every row while the master carries the file key. The file-route arm was therefore
 * unreachable and every file-route line fell through to the API arm, where it has no
 * {@code TB_PAY_TARGET} row — counting as neither success nor error. Result: the
 * Result-list chips read {@code 0 / 0} for every file-route batch while the Result
 * <i>detail</i> page (which uses the master key, i.e. legacy's rule) showed the real
 * per-line statuses. Verified on JEX DEV against 20+ batches.
 *
 * <p>Two routes, after the fix:
 *
 * <ul>
 *   <li><b>API route</b> (VND) — {@code TB_PAY_TARGET}. Success when
 *       {@code PROC_STAT='0'}, error when {@code PROC_STAT='3'}.</li>
 *   <li><b>File route</b> (non-VND / Payroll / SWIFT) — {@code TB_PAY_FILE_MST} +
 *       {@code TB_PAY_FILE_DATA}. Success when both
 *       {@code TPFM.PROC_STAT='0'} AND {@code TRIM(COALESCE(TPFD.RS_CD,''))='0000'};
 *       error when {@code TPFM.PROC_STAT='3'} OR the RS_CD differs
 *       from {@code '0000'} on a settled file.</li>
 * </ul>
 *
 * <p>No fan-out risk: each of the three {@code LEFT JOIN}s is on the full primary key
 * of its table, and the added {@code SCDUL_PYMN_SUMR} join is on that table's PK — so
 * the {@code GROUP BY} still aggregates exactly one row per detail line.
 *
 * <p>Tenant scope is unchanged and enforced twice: {@code SPMD.BIZ_KEY = :bizKey} in
 * the {@code WHERE} clause, and {@code BIZ_KEY} equality on every join.
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
                        WHEN EXISTS (
                                 SELECT 1
                                 FROM TB_PAY_FILE_DATA RTE
                                 WHERE RTE.BIZ_KEY     = SPS.BIZ_KEY
                                   AND RTE.TRAN_DT     = SPS.PAY_TRAN_DT
                                   AND RTE.FILE_SEQ_NO = SPS.PAY_FILE_SEQ_NO
                             )
                        THEN CASE WHEN TPFM.PROC_STAT = '0'
                                   AND TRIM(COALESCE(TPFD.RS_CD, '')) = '0000' THEN 1 ELSE 0 END
                        ELSE CASE WHEN TPT.PROC_STAT = '0' THEN 1 ELSE 0 END
                    END
                ) AS BIGINT)                                        AS succ_cnt,
                CAST(SUM(
                    CASE
                        WHEN EXISTS (
                                 SELECT 1
                                 FROM TB_PAY_FILE_DATA RTE
                                 WHERE RTE.BIZ_KEY     = SPS.BIZ_KEY
                                   AND RTE.TRAN_DT     = SPS.PAY_TRAN_DT
                                   AND RTE.FILE_SEQ_NO = SPS.PAY_FILE_SEQ_NO
                             )
                        THEN CASE WHEN TPFM.PROC_STAT = '3'
                                    OR (TPFM.PROC_STAT = '0'
                                        AND TRIM(COALESCE(TPFD.RS_CD, '')) NOT IN ('0000')) THEN 1 ELSE 0 END
                        ELSE CASE WHEN TPT.PROC_STAT = '3' THEN 1 ELSE 0 END
                    END
                ) AS BIGINT)                                        AS err_cnt
            FROM SCDUL_PYMN_SUMR_DETL SPMD
            JOIN SCDUL_PYMN_SUMR SPS
                   ON SPS.BIZ_KEY        = SPMD.BIZ_KEY
                  AND SPS.SCDUL_PYMN_NO  = SPMD.SCDUL_PYMN_NO
            LEFT JOIN TB_PAY_FILE_MST TPFM
                   ON TPFM.BIZ_KEY      = SPMD.BIZ_KEY
                  AND TPFM.TRAN_DT      = SPS.PAY_TRAN_DT
                  AND TPFM.FILE_SEQ_NO  = SPS.PAY_FILE_SEQ_NO
            LEFT JOIN TB_PAY_FILE_DATA TPFD
                   ON TPFD.BIZ_KEY      = SPMD.BIZ_KEY
                  AND TPFD.TRAN_DT      = SPMD.PAY_TRAN_DT
                  AND TPFD.FILE_SEQ_NO  = SPS.PAY_FILE_SEQ_NO
                  AND CAST(TPFD.SEQ AS VARCHAR) = SPMD.PAY_TRAN_SEQ
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
