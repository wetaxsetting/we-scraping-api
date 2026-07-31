package com.kosign.wecms.domain.payment.dispatch.repository;

import com.kosign.wecms.domain.payment.dispatch.entity.TbFirmFileMst;
import com.kosign.wecms.domain.payment.dispatch.entity.TbFirmFileMstId;
import org.springframework.data.domain.Pageable;
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

    /**
     * Legacy {@code TB_FIRM_FILE_MST_R001} (BATCH2 reception-confirmed pick) — verbatim shape.
     *
     * <p><b>Not used by the retry sweep.</b> Legacy ran this unbounded, once per minute, over the
     * whole day's rows across every tenant ({@code FILE_DEPOSIT_TRANSFER_BATCH2.java:48-54} — no age
     * filter, no ORDER BY, no LIMIT). {@link #findStrandedBulkExecutionFiles} is the bounded
     * equivalent the refactor actually drives. Kept because it is the exact legacy selection and is
     * the natural probe for ad-hoc operator diagnosis.
     */
    List<TbFirmFileMst> findByTranDtAndProcStepAndProcStat(String tranDt, String procStep, String procStat);

    /**
     * Bounded, <b>read-only</b> selection of files stranded after a failed or unanswered {@code 00302}
     * — the detection query behind {@code StuckPayFileMonitorService}'s firm-file sweep.
     *
     * <p><b>Detection only. There is no automatic re-drive.</b> A scheduled sweep that re-issued
     * {@code 00302} from this selection existed briefly (2026-07-29) and was removed the same day: it
     * was K9 Option A ("Recovery Scheduler (reconciliation)"), which Mr. Kang was offered and did not
     * select. The approved TO-BE replaces legacy BATCH2 with inline {@code 00302} from the {@code 01301}
     * callback and leaves the residual edge to <b>monitoring + manual recovery</b>. If a future caller
     * of this method mutates payment state, that decision has been reversed — check
     * {@code docs/audits/final-four-to-one-batch-architecture-correction.md} first.
     *
     * <p>Same state predicate as the legacy BATCH2 pick ({@code PROC_STEP='1'} + {@code PROC_STAT='6'})
     * so the monitor reports exactly the population legacy would have re-picked. That population now has
     * one member legacy never had: a file whose reception advance committed but whose 00302 fan-out was
     * lost with the JVM, because the fan-out runs in an {@code afterCommit} hook rather than in the
     * callback transaction (see {@code BulkReceptionResultService}'s "Known residual" section). Such a
     * file is at exactly {@code '1'/'6'} with no 00302 audit row, so this selection catches it and
     * {@code StuckPayFileMonitorService.classifyFirmFile} reports it as
     * {@code BULK_EXECUTION_NOT_ISSUED} — which is the correct diagnosis, and the reason that cause is
     * kept distinct from {@code BULK_EXECUTION_UNCONFIRMED}. This query is the <b>only</b> detection for
     * that residual; nothing re-drives it.
     *
     * <h2>FIX H — bounded by {@code PROC_DTTM} on both sides, NOT by {@code TRAN_DT}</h2>
     * This query used to carry {@code TRAN_DT = :tranDt}, with the caller passing today's ICT date.
     * A file stranded at 23:59 therefore <b>disappeared from the sweep at 00:00 and was never reported
     * again</b> — permanently invisible, in the one state that has no automatic recovery.
     *
     * <p><b>Legacy parity is not a defence here, and this is the reasoning.</b> Legacy scoped its pick
     * to today as well ({@code FILE_DEPOSIT_TRANSFER_BATCH2.java:49}) — but legacy BATCH2 re-selected
     * {@code PROC_STEP='1'/PROC_STAT='6'} <b>every 60 seconds and re-sent 00302</b>, so a stranded file
     * self-healed within a minute whether or not any sweep could still see it tomorrow. The day-scope
     * was survivable only because of that re-drive, and the re-drive is exactly what the approved TO-BE
     * removed (K9 Option A, declined). Inheriting the limitation without the mechanism that made it
     * harmless turns a benign scope into a permanent blind spot. So the refactor diverges deliberately:
     * detection must outlive the calendar day, because nothing else will catch the row.
     *
     * <p>{@code PROC_DTTM} is also the semantically correct column — "stranded for how long" is a
     * statement about the last state change, not about the build date — and it is a 14-char
     * {@code yyyyMMddHHmmss} string, so a lexicographic comparison is a chronological one.
     *
     * <p>Bounds a scheduled reader needs:
     * <ul>
     *   <li><b>Age threshold</b> — {@code PROC_DTTM < :procDttmCutoff}, so a file whose {@code 01301}
     *       callback is still mid-flight is never reported as stranded.</li>
     *   <li><b>Retention floor</b> — {@code PROC_DTTM >= :procDttmFloor}, so removing the date predicate
     *       does not open the sweep over all history. The caller derives it from
     *       {@code stuck-monitor.retention-days} (default 7).</li>
     *   <li><b>Deterministic ordering</b> — oldest first, then the full PK, so successive sweeps and
     *       multiple nodes see the same order, and a truncated sweep drains rather than starving its
     *       tail.</li>
     *   <li><b>Batch size</b> — bounded by the caller's {@link Pageable}, so monitoring can never
     *       become an unbounded scan (the H-2 concern).</li>
     * </ul>
     *
     * <p><b>A file older than the retention floor still falls out of view</b> — the cliff is moved, not
     * abolished. That is intentional and is the trade the floor buys: an unbounded window over a table
     * that only ever grows would eventually make every sweep scan all history. Seven days is chosen so
     * a file stranded on a Friday night is still reported the following Monday; if that is not enough
     * for a given environment, raise {@code retention-days} rather than removing the floor.
     *
     * <h2>Index — no index supports this predicate</h2>
     * {@code TB_FIRM_FILE_MST} has exactly one index on both DBs (verified read-only 2026-07-30): the
     * PK {@code (TRAN_DT, BIZ_KEY, FILE_SEQ_NO, FIRM_FILE_NM)}. The old predicate used its leading
     * {@code TRAN_DT} column for a range scan; this one has no indexed column at all, so it is a
     * <b>sequential scan</b> — accepted for now on size (JEX DEV: 122 rows / 88 kB, 6 of them at
     * {@code '1'/'6'}; refactor DEV: 0 rows) and on frequency (one sweep per 5 minutes, and the sweep
     * is off unless {@code stuck-monitor.enabled=true}). The covering index ships as a DBA-run script,
     * {@code Claude Code (AI)/sql/tb_firm_file_mst_stranded_sweep_index_20260730.sql}; it is not
     * applied. Apply it before this table grows past the low thousands, and re-check the plan then —
     * at 122 rows the planner will and should still choose a seq scan.
     *
     * <p><b>No tenant predicate, deliberately.</b> Legacy {@code TB_FIRM_FILE_MST_R001} has none
     * either — this is a system-level view with no request principal. {@code BIZ_KEY} scoping would
     * hide the tenants the monitor exists to report on.
     */
    /*
     * PROC_DTTM emptiness — both NULL and '' must be excluded, explicitly.
     *
     * `procDttm <> ''` is NOT redundant with the retention floor. A blank sorts before every 14-digit
     * timestamp, so it satisfies `< cutoff`; today it is excluded only because it also fails
     * `>= floor`. That is accidental protection: the Javadoc above contemplates widening this predicate
     * ("drop the WHERE clause") for index reasons, and the moment the floor goes, every blank row
     * alerts on every tick forever. JEX DEV holds 2 such rows (verified 2026-07-31). Making it explicit
     * costs nothing and removes the coupling.
     *
     * Legacy writes PROC_DTTM on U001 (1->2 and later) but NOT on U002 (the 0->1 reception advance,
     * IDO.TB_FIRM_FILE_MST_U002.xml), which is why every legacy-written '1'/'6' row has it NULL and is
     * invisible to this sweep. The refactor DOES set it on 0->1
     * (BulkReceptionResultService:308), so refactor-created rows are visible. In a shared UAT/PROD DB
     * that means this sweep reports the refactor's stranded files and not the legacy batch's —
     * deliberate scoping, recorded here so it is not mistaken for a bug.
     */
    @Query("""
            SELECT f FROM TbFirmFileMst f
             WHERE f.procStep = :procStep
               AND f.procStat = :procStat
               AND f.procDttm IS NOT NULL
               AND f.procDttm <> ''
               AND f.procDttm <  :procDttmCutoff
               AND f.procDttm >= :procDttmFloor
             ORDER BY f.procDttm ASC, f.tranDt ASC, f.bizKey ASC, f.fileSeqNo ASC, f.firmFileNm ASC
            """)
    List<TbFirmFileMst> findStrandedBulkExecutionFiles(@Param("procStep") String procStep,
                                                       @Param("procStat") String procStat,
                                                       @Param("procDttmCutoff") String procDttmCutoff,
                                                       @Param("procDttmFloor") String procDttmFloor,
                                                       Pageable pageable);

    /** Legacy {@code TB_FIRM_FILE_MST_R004} (01301 callback file lookup). */
    Optional<TbFirmFileMst> findByTranDtAndFirmFileNmAndProcStepAndProcStat(
            String tranDt, String firmFileNm, String procStep, String procStat);

    /**
     * Every file with this name on this date, <b>in any state</b> — the disambiguation probe for a
     * 01301 callback whose {@code R004} lookup found nothing.
     *
     * <p>{@code R004} returning empty conflates three situations that need opposite acknowledgements:
     * a genuine replay of an already-processed callback (a row exists at a later {@code PROC_STEP} →
     * ack success so the gateway stops resending), an unknown file (nothing at all → a retryable
     * error so the gateway does resend), and a row in an unexpected state (also retryable). Without
     * this finder the handler cannot tell them apart, and answered {@code RC=0000} — the strongest
     * "stop resending" signal there is — to all three.
     *
     * <p>Not scoped by {@code BIZ_KEY} or {@code VAN_ORG_CD}: resolving the tenant is what the failed
     * lookup was for, and {@code FIRM_FILE_NM} already carries the VAN org code as a name segment.
     * Bounded in practice by the {@code (TRAN_DT, FIRM_FILE_NM)} pair — the name is unique per file,
     * so this returns 0 or 1 rows.
     */
    List<TbFirmFileMst> findByTranDtAndFirmFileNm(String tranDt, String firmFileNm);

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
