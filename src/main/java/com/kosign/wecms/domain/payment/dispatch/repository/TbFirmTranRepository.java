package com.kosign.wecms.domain.payment.dispatch.repository;

import com.kosign.wecms.domain.payment.dispatch.entity.TbFirmTran;
import com.kosign.wecms.domain.payment.dispatch.entity.TbFirmTranId;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Read-only access to {@code TB_FIRM_TRAN} for the Phase 2 Result-detail
 * resolver — supplies the API-path {@code RS_CD} + {@code RS_MSG} for
 * each dispatched line.
 */
public interface TbFirmTranRepository
        extends JpaRepository<TbFirmTran, TbFirmTranId> {

    /**
     * Bulk-fetch the response rows for one batch of API-path dispatches.
     * The resolver builds an in-memory map keyed by {@code tranSeq} to
     * join back to the detail lines.
     */
    List<TbFirmTran> findByBizKeyAndTranDtAndTranSeqIn(
            String bizKey, String tranDt, Collection<String> tranSeqs);

    /**
     * No-response real-time transfers awaiting a result check — legacy
     * {@code PROCESSING_RESULT_CHECK_BATCH} / {@code TB_FIRM_TRAN_R001}
     * ({@code TRAN_TP='202' AND PROC_STAT='6'}). The caller further filters by
     * date + the 2-minute age window before issuing the 203 inquiry.
     *
     * <p><b>Unbounded — no longer used by the scheduled scan (H-2).</b> It loads every matching row
     * across every date and every tenant into memory. {@link #findResultCheckCandidates} is the
     * bounded equivalent the sweep now drives. Retained for tests and ad-hoc diagnosis only.
     */
    List<TbFirmTran> findByTranTpAndProcStat(String tranTp, String procStat);

    /**
     * Bounded, deterministically-ordered candidate selection for the scheduled result check (H-2).
     *
     * <p><b>Legacy predicate — verified 2026-07-29 against the BATCH-side IDO</b></p>
     * The runtime predicate lives in
     * {@code WECMS_BATCH DEV/source/WECMS_BATCH/WECMS_BATCH_ETC/xml/service/IDO/IDO.TB_FIRM_TRAN_R001.xml}
     * (the {@code WECMS_ETC} copy of the same id is a {@code WHERE 1=1 ??} dynamic shell and
     * {@code PROCESSING_RESULT_CHECK_BATCH:62-64} passes it no dynamic clause, so the batch-side file
     * is the one that runs):
     * <pre>
     * WHERE TRAN_DT   = TO_CHAR(now(),'YYYYMMDD')
     *   AND PROC_STAT = '6'
     *   AND TRAN_TM  &lt;= TO_CHAR(now() - INTERVAL '2 minute','hh24miss')
     *   AND TO_CHAR(now() - INTERVAL '2 minute','hh24miss') &lt; '235800'
     *   AND TRAN_TP   = '202'
     * </pre>
     * with <b>no</b> {@code BIZ_KEY} predicate, <b>no</b> {@code ORDER BY} and <b>no</b> {@code LIMIT}.
     * The absence of a tenant predicate is <b>by design</b> — this is a system batch that services
     * every tenant, so "cross-tenant" is the intended scope, not a defect.
     *
     * <p><b>What is and is not pushed into SQL (N-1, 2026-07-29)</b></p>
     * {@code TRAN_TP}, {@code PROC_STAT} <b>and {@code TRAN_DT}</b> are DB predicates. Note that
     * having {@code TRAN_DT} in SQL is <b>legacy's own shape</b> — an earlier revision moved it into
     * Java, which was the regression this restores.
     *
     * <p>Only the <b>age</b> comparison stays in {@code ResultCheckService.selectAged}: that tiered
     * rule (treat {@code RQ_DTTM} as the authority when present; fall back to {@code TRAN_DT} /
     * {@code TRAN_TM} for legacy-JEX-written rows) is the documented D-1 improvement over legacy's
     * plain wrapping {@code HHmmss} compare, and collapsing it into SQL would reintroduce the UTC/ICT
     * boundary bug. The <i>date</i> bound is a plain equality on a single column and carries none of
     * that subtlety, so it belongs in SQL.
     *
     * <p><b>Why the date bound must be in SQL, not after the limit (N-1)</b></p>
     * Rows are ordered oldest-first and then truncated by {@code LIMIT}. With the date filter applied
     * <i>after</i> the limit, any row parked at {@code 202}/{@code '6'} from an older date — a
     * legacy-JEX row, a row abandoned by a failed run, or one skipped forever because its
     * {@code TB_FIRM_CONF} is missing — sorts to the <b>front</b> of every page, consumes the whole
     * batch window, and is then discarded in Java. Once such rows reach the batch size the sweep
     * processes nothing at all, permanently. Filtering in SQL means they can never enter the page.
     *
     * <p><b>Widening beyond legacy (deliberate, unchanged by N-1)</b></p>
     * {@code tranDts} carries UTC <b>today and yesterday</b>, where legacy carries today only. A row
     * dispatched shortly before UTC midnight would otherwise be orphaned by the rollover; legacy's
     * answer was the {@code … &lt; '235800'} guard, which avoids the wrap without ever recovering the
     * affected row. Passing the set as a parameter keeps the widening a caller-side decision and
     * keeps this query a pure projection of it.
     *
     * <p><b>Ordering + limit</b></p>
     * Oldest-first, so a young row can never displace an aged one, and a truncated sweep still makes
     * forward progress: the rows it processes leave {@code PROC_STAT='6'} and the next cycle continues
     * from the next oldest. No offset pagination (offsets skip rows as statuses change underneath).
     * {@code TRAN_SEQ} is the final tiebreaker so the order is total and two nodes agree.
     *
     * @param tranDts  the eligible {@code TRAN_DT} values in the {@link java.time.ZoneOffset#UTC}
     *                 zone the column is written in — never empty
     * @param pageable supplies the batch-size limit; always page 0, and must carry no sort of its own
     *                 (a {@code Pageable} sort would be appended to the {@code ORDER BY} above)
     */
    @Query("""
            SELECT t FROM TbFirmTran t
             WHERE t.tranTp   = :tranTp
               AND t.procStat = :procStat
               AND t.tranDt  IN :tranDts
             ORDER BY t.tranDt ASC, t.tranTm ASC, t.tranSeq ASC
            """)
    List<TbFirmTran> findResultCheckCandidates(@Param("tranTp") String tranTp,
                                               @Param("procStat") String procStat,
                                               @Param("tranDts") Collection<String> tranDts,
                                               Pageable pageable);

    /**
     * Idempotency guard for the real-time 202 write seam (C2): a firm-tran row
     * already keyed by {@code (TRAN_DT, BIZ_KEY, TRAN_SEQ)} means the transfer was
     * already dispatched (the 202 row reuses the originating {@code TB_PAY_TARGET}
     * {@code TRAN_SEQ} as its own — the legacy {@code dep_seq} link). The PK has no
     * {@code TRAN_TP} component, and the 204 leg allocates a distinct sequence, so
     * this triple uniquely identifies the 202 row.
     */
    boolean existsByTranDtAndBizKeyAndTranSeq(String tranDt, String bizKey, String tranSeq);
}
