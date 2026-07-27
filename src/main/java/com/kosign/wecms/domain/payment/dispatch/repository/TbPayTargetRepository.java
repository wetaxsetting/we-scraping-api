package com.kosign.wecms.domain.payment.dispatch.repository;

import com.kosign.wecms.domain.payment.dispatch.entity.TbPayTarget;
import com.kosign.wecms.domain.payment.dispatch.entity.TbPayTargetId;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TbPayTargetRepository
        extends JpaRepository<TbPayTarget, TbPayTargetId> {

    /**
     * G2 (audit F2) — atomic {@code PROC_STAT '9' → '6'} claim for the real-time dispatch
     * pickup. A single conditional UPDATE that transitions the row out of WAIT only if it is
     * still WAIT, returning the affected-row count so the caller can tell whether <i>this</i>
     * invocation won the row.
     *
     * <p>This is the idempotency guard for the event-driven TO-BE: a duplicate after-commit
     * hand-off (or a future re-dispatch) that reaches the pickup after the row already left
     * WAIT gets {@code 0} rows and no-ops, so it can never double-run the 202 (and, on the
     * Woori path, never fire a second 204 FX inquiry). Placed <b>after</b> the operating-hours
     * gate and the 204 FX pre-step, so the legacy paths that must leave the row at WAIT
     * (operating-hours SKIP, a failed 204) still return before the claim.
     *
     * @return {@code 1} when this call claimed a still-WAIT row, {@code 0} otherwise
     */
    @Modifying
    @Query("UPDATE TbPayTarget t SET t.procStat = '6', t.procDttm = :procDttm "
            + "WHERE t.bizKey = :bizKey AND t.tranDt = :tranDt AND t.tranSeq = :tranSeq "
            + "AND t.procStat = '9'")
    int claimForDispatch(@Param("bizKey") String bizKey,
                         @Param("tranDt") String tranDt,
                         @Param("tranSeq") String tranSeq,
                         @Param("procDttm") String procDttm);

    // TRAN_SEQ allocation now routes through
    // LegacyFunctionRepository.nextTbPayTargetTranSeq (V140 #2 — the GLOBAL
    // firm_seq_no sequence) so every wb_get_next_val call goes through the one
    // funnel and lines up with the live JEX firm-banking dispatch.

    /**
     * Bulk-fetch dispatch rows for the API path. One query per Result-detail
     * page render — the resolver builds an in-memory map keyed by
     * {@code tranSeq} to join back to the detail lines.
     *
     * <p>Used by {@link com.kosign.wecms.domain.payment.dispatch.service.DispatchResultResolver}
     * to surface {@code PROC_STAT} / {@code PROC_DTTM} on the Result
     * detail page.
     */
    List<TbPayTarget> findByBizKeyAndTranDtAndTranSeqIn(
            String bizKey, String tranDt, Collection<String> tranSeqs);

    /**
     * Re-read a single committed dispatch row for the after-commit real-time
     * dispatch (C2). The {@link com.kosign.wecms.domain.payment.dispatch.service.RealtimeDispatchService}
     * gates on {@code PROC_STAT='9'} so a row already advanced (or absent) is a
     * safe no-op — mirrors the legacy {@code TB_PAY_TARGET_R001}
     * {@code WHERE ... PROC_STAT='9'} pickup.
     */
    Optional<TbPayTarget> findByBizKeyAndTranDtAndTranSeq(
            String bizKey, String tranDt, String tranSeq);

    /**
     * R-1 stranded-record recovery (Item 4): real-time pay-targets left at {@code PROC_STAT='9'}
     * (WAIT) whose {@code PROC_DTTM} predates the grace cutoff — a committed dispatch whose
     * after-commit 202 hand-off never fired (crash/restart in the commit→afterCommit window, or
     * the flag flipped on after the row was created). {@code PROC_DTTM} is the 14-char
     * {@code yyyyMMddHHmmss} string, so lexicographic {@code <} is chronological {@code <}. Null
     * {@code PROC_DTTM} rows are excluded (never eligible) — safe by construction.
     */
    List<TbPayTarget> findByProcStatAndProcDttmLessThan(String procStat, String procDttmCutoff);
}
