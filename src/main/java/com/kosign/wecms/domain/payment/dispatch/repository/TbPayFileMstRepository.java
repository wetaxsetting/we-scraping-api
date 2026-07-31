package com.kosign.wecms.domain.payment.dispatch.repository;

import com.kosign.wecms.domain.payment.dispatch.entity.TbPayFileMst;
import com.kosign.wecms.domain.payment.dispatch.entity.TbPayFileMstId;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TbPayFileMstRepository
        extends JpaRepository<TbPayFileMst, TbPayFileMstId> {

    // FILE_SEQ_NO allocation now routes through
    // LegacyFunctionRepository.nextTbPayFileMstFileSeqNo (V140 #4 — the GLOBAL
    // firm_file_seq_no sequence) so every wb_get_next_val call goes through the
    // one funnel and lines up with the live JEX firm-banking file dispatch.

    /** The bulk pay-file target row (00301 reception flow + 01302 final-status aggregation). */
    Optional<TbPayFileMst> findByTranDtAndBizKeyAndFileSeqNo(String tranDt, String bizKey, String fileSeqNo);

    /**
     * R-1 stranded-record recovery (Item 4): bulk pay-files left at {@code PROC_STAT='9'}
     * (WAIT) whose {@code PROC_DTTM} predates the grace cutoff — i.e. a request-time
     * {@code receive()} hand-off that never advanced them (lost after-commit event, flag flipped
     * on after the row existed, crash in the commit→afterCommit window). {@code PROC_DTTM} is the
     * 14-char {@code yyyyMMddHHmmss} string, so a lexicographic {@code <} is a chronological {@code <}.
     * Null {@code PROC_DTTM} rows are excluded (never eligible) — safe by construction.
     *
     * <p><b>Unbounded — superseded by {@link #findStrandedPayFiles} (Phase 5, 2026-07-29).</b> Both
     * consumers (the recovery sweep and the stuck monitor) now use the bounded, ordered form. Retained
     * for ad-hoc diagnosis only; do not add new callers.
     */
    List<TbPayFileMst> findByProcStatAndProcDttmLessThan(String procStat, String procDttmCutoff);

    /**
     * Bounded, deterministically-ordered stranded-pay-file selection — the paginated replacement for
     * {@link #findByProcStatAndProcDttmLessThan} (Phase 5).
     *
     * <p>Serves <b>two</b> states from one query, chosen by the caller's {@code procStat}:
     * <ul>
     *   <li>{@code '9'} (WAIT) — never handed off (the original R-1 population);</li>
     *   <li>{@code '6'} (REQ) — <b>claimed and then stranded</b> (H-3 / N-3). The H-4 claim commits
     *       {@code '9'→'6'} in its own transaction before any file work, so every failure between the
     *       claim and a terminal status leaves a committed {@code '6'} that the WAIT-only sweeps could
     *       not see.</li>
     * </ul>
     *
     * <p><b>No tenant predicate</b>, matching the WAIT sweep it replaces and legacy's system-batch
     * scope. {@code FILE_SEQ_NO} is drawn from the global {@code firm_file_seq_no} sequence, so the
     * ordering below is already total without {@code BIZ_KEY}; it is included last purely so two nodes
     * observe an identical sequence.
     *
     * <p>Oldest-first, so a truncated sweep still drains rather than starving its tail — the same
     * property {@code TbFirmTranRepository.findResultCheckCandidates} relies on (see N-1: a limit with
     * the age filter applied afterwards is what caused the starvation there; here the age cutoff is
     * itself a SQL predicate, so nothing can be discarded after the limit).
     *
     * @param procStat        {@code '9'} or {@code '6'} — never a wildcard
     * @param procDttmCutoff  14-char {@code yyyyMMddHHmmss}; rows at or after this are too recent to
     *                        be considered stranded, and null {@code PROC_DTTM} rows never match
     * @param pageable        supplies the batch-size limit; always page 0, carrying no sort of its own
     */
    @Query("""
            SELECT p FROM TbPayFileMst p
             WHERE p.procStat  = :procStat
               AND p.procDttm IS NOT NULL
               AND p.procDttm  < :procDttmCutoff
             ORDER BY p.procDttm ASC, p.tranDt ASC, p.fileSeqNo ASC, p.bizKey ASC
            """)
    List<TbPayFileMst> findStrandedPayFiles(@Param("procStat") String procStat,
                                            @Param("procDttmCutoff") String procDttmCutoff,
                                            Pageable pageable);

    /**
     * H-3 / R-2 · Fail a pay-file that was <b>claimed and then stranded</b> — the terminal write that
     * stops a committed {@code '6'} from becoming permanent.
     *
     * <p><b>Why a separate conditional update rather than {@code save(pay)}.</b> The H-4 claim commits
     * {@code '9'→'6'} in its own transaction, and each split file then runs in its own
     * {@code REQUIRES_NEW} transaction. When a split file's transaction rolls back, any
     * {@code failBoth(...)} write it made rolls back with it — but the claim does not. So the failure
     * has to be recorded from <i>outside</i> that transaction, against a row the caller may hold only
     * as a detached entity.
     *
     * <p><b>Guarded on {@code '6'}</b> so it can only ever move a row <i>out of</i> the stranded state:
     * <ul>
     *   <li>a split file that already recorded FAIL ({@code '3'}) is untouched — 0 rows;</li>
     *   <li>a file a callback has already advanced or completed is untouched — 0 rows;</li>
     *   <li>a file still WAIT ({@code '9'}) is untouched — it was never claimed, so it is not this
     *       method's business.</li>
     * </ul>
     * The affected-row count therefore distinguishes "I recorded the failure" from "something else
     * already owns this row", and the caller logs accordingly.
     *
     * <p>{@code RS_MSG} only — {@code TB_PAY_FILE_MST} has no {@code RS_CD} column (legacy
     * {@code TB_PAY_FILE_MST_U001} parity, same as {@code BulkReceptionResultService.failFile}).
     *
     * @return {@code 1} when this call recorded the failure, {@code 0} when the row was not stranded
     */
    @Modifying
    @Query("UPDATE TbPayFileMst p SET p.procStat = '3', p.rsMsg = :rsMsg, p.procDttm = :procDttm "
            + "WHERE p.bizKey = :bizKey AND p.tranDt = :tranDt AND p.fileSeqNo = :fileSeqNo "
            + "AND p.procStat = '6'")
    int failClaimedPayFile(@Param("bizKey") String bizKey,
                           @Param("tranDt") String tranDt,
                           @Param("fileSeqNo") String fileSeqNo,
                           @Param("rsMsg") String rsMsg,
                           @Param("procDttm") String procDttm);

    /**
     * H-4 · Atomically claim one bulk pay-file for 00301 reception — the bulk-rail counterpart of
     * {@link TbPayTargetRepository#claimForDispatch}. Transitions {@code PROC_STAT '9'} (WAIT) →
     * {@code '6'} (REQ / in-process) <b>only if the row is still WAIT</b>, and reports how many rows
     * it moved.
     *
     * <p><b>Why this exists.</b> The reception flow previously gated on a read-then-check
     * ({@code findBy…} then {@code if (procStat != '9') return}) followed by an unconditional
     * {@code save(pay)}. Two drivers could both read {@code '9'}, both pass the check, both write
     * {@code '6'} (identical value — no constraint violated), and both go on to SFTP-upload the file
     * and issue EFS 00301. The deterministic {@code TB_FIRM_FILE_MST} PK could not save it: inside
     * the per-file transaction the insert is deferred to commit, which happens <i>after</i> both
     * egress calls. A single conditional UPDATE removes the check-then-act window entirely — the
     * database decides the winner, and the loser learns it moved 0 rows before any egress.
     *
     * <p><b>Legacy alignment.</b> Legacy {@code FILE_DEPOSIT_TRANSFER_BATCH1} writes the same
     * transition through {@code TB_PAY_FILE_MST_U001} (`:453-454`, {@code PROC_STAT = PROC_ST_REQ}).
     * This is that write made conditional; it introduces no new state and no new column. Legacy's own
     * duplicate protection was a different mechanism — it committed the {@code TB_FIRM_FILE_MST}
     * insert at {@code :696} <b>before</b> uploading at {@code :700}, so its deterministic
     * {@code FIRM_FILE_NM} PK was a genuine pre-egress guard. See
     * {@code docs/architecture/bulk-dispatch-concurrency-fix.md}.
     *
     * <p>Callers must invoke this through {@link
     * com.kosign.wecms.domain.payment.dispatch.service.BulkDispatchClaimTxService} so the claim
     * commits in its own short transaction <b>before</b> the upload — never as part of a transaction
     * that spans the network calls, which would hold the pay-file row locked for the duration of an
     * SFTP transfer.
     *
     * @return {@code 1} when this call claimed a still-WAIT row (caller owns the dispatch),
     *         {@code 0} otherwise (another driver owns it, or it is no longer WAIT)
     */
    @Modifying
    @Query("UPDATE TbPayFileMst p SET p.procStat = '6', p.procDttm = :procDttm "
            + "WHERE p.bizKey = :bizKey AND p.tranDt = :tranDt AND p.fileSeqNo = :fileSeqNo "
            + "AND p.procStat = '9'")
    int claimForReception(@Param("bizKey") String bizKey,
                          @Param("tranDt") String tranDt,
                          @Param("fileSeqNo") String fileSeqNo,
                          @Param("procDttm") String procDttm);
}
