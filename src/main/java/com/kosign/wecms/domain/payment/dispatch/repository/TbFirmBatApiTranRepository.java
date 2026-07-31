package com.kosign.wecms.domain.payment.dispatch.repository;

import com.kosign.wecms.domain.payment.dispatch.entity.TbFirmBatApiTran;
import com.kosign.wecms.domain.payment.dispatch.entity.TbFirmBatApiTranId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Repository for {@link TbFirmBatApiTran} (batch-level EFS API audit ledger).
 * Legacy {@code IDO.TB_FIRM_BAT_API_TRAN_C001/U001} inserted one row per API
 * leg (00301/00302/01301/01302) and updated it with the response.
 *
 * <p>PK is the six-column composite
 * {@code (TRAN_DT, BIZ_KEY, TRAN_SEQ, API_TRAN_TP, OUT_BANK_CD, BANK_PRTN_ID)}.
 *
 * <p><b>TODO(K9):</b> this ledger + the {@code PROC_STEP} state guard are the
 * intended idempotency key for the 01301→00302 trigger (a duplicate callback
 * must not issue 00302 twice). The dedupe finder below supports that check, but
 * the idempotent-trigger mechanism itself is part of the open K9 decision and
 * is not wired here.
 */
public interface TbFirmBatApiTranRepository
        extends JpaRepository<TbFirmBatApiTran, TbFirmBatApiTranId> {

    /** Audit rows for one file (any API leg) — supports the K9 dedupe guard. */
    List<TbFirmBatApiTran> findByTranDtAndBizKeyAndFirmFileNm(String tranDt, String bizKey, String firmFileNm);

    /**
     * Whether a given API leg has already been logged for a file, <b>within one {@code TRAN_DT}</b>.
     *
     * <p><b>Diagnostic use only.</b> Its one remaining consumer is
     * {@code StuckPayFileMonitorService}, which already holds the file row and passes that row's own
     * {@code TRAN_DT} — so the date predicate is a free narrowing there, not a clock comparison.
     *
     * <p><b>Not for idempotency.</b> The {@code 00302} guard used this and it was wrong: see
     * {@link #existsByBizKeyAndFirmFileNmAndApiTranTp} for why the audit row's {@code TRAN_DT} and the
     * file row's {@code TRAN_DT} are not the same value. Do not reintroduce it there.
     *
     * <p>Because there is no automatic retry in the approved architecture, a file carries <b>at most
     * one</b> {@code 00302} row, so a boolean is sufficient — no attempt-counting finder is needed.
     * (One was added on 2026-07-29 for a scheduled retry sweep and removed with it the same day.)
     */
    boolean existsByTranDtAndBizKeyAndFirmFileNmAndApiTranTp(
            String tranDt, String bizKey, String firmFileNm, String apiTranTp);

    /**
     * FIX D · <b>Date-proof</b> "has this API leg already been issued for this file?" — the
     * idempotency guard for the inline {@code 01301}→{@code 00302} path.
     *
     * <h2>Why no {@code TRAN_DT} predicate</h2>
     * The guard has to answer a question about the <i>file</i>, and the two rows it correlates are
     * dated by <b>different clocks</b>:
     * <ul>
     *   <li>{@code TB_FIRM_FILE_MST.TRAN_DT} is stamped at file-BUILD time
     *       ({@code BulkReceptionService}, ICT);</li>
     *   <li>the {@code TB_FIRM_BAT_API_TRAN} row this method probes for is stamped at
     *       {@code 00302}-ISSUE time — i.e. the {@code 01301} callback clock
     *       ({@code BulkExecutionInlineTxService}), deliberately, because that column records when the
     *       call happened.</li>
     * </ul>
     * Those coincide only while a build and its callback fall on the same ICT calendar day. A file
     * built at 23:58 whose {@code 01301} arrives at 00:02 writes its audit row under {@code D+1}; a
     * second invocation moments later probes a different keyspace, finds nothing, and <b>re-sends
     * {@code 00302} to the bank</b>. Neither date is "the" right one to probe with — the fix is to stop
     * probing by date at all. (Sibling defects of the same class: C-2 on the {@code 01301} lookup, R-F
     * on the result-check scan.)
     *
     * <h2>Why {@code (BIZ_KEY, FIRM_FILE_NM)} alone is a sufficient key — verified</h2>
     * {@code FIRM_FILE_NM} is {@code S_{VAN_ORG_CD}_{FILE_TYPE}_{round}_{TRAN_DT}}
     * ({@code BulkReceptionService:512}), so <b>the name carries its own date</b>:
     * <ul>
     *   <li>the round resets daily — it is {@code MAX(FILE_SEQ)+1} scoped to
     *       {@code (TRAN_DT, OUT_BANK_CD, BANK_PRTN_ID)} ({@code BulkReceptionService.nextFileRound})
     *       — but that cannot alias across dates, because the date is a separate component of the
     *       name;</li>
     *   <li>verified on live JEX DEV 2026-07-30: all 122 {@code TB_FIRM_FILE_MST} rows have the name's
     *       5th component equal to their own {@code TRAN_DT} (0 mismatches), and no
     *       {@code FIRM_FILE_NM} occurs under more than one {@code TRAN_DT}.</li>
     * </ul>
     * So dropping {@code TRAN_DT} cannot make the guard match a <i>different day's</i> file. It only
     * makes it match the file whose 00302 was actually recorded, whichever day the recording clock
     * said.
     *
     * <p><b>Known narrowness of {@code FIRM_FILE_NM}, and why it does not weaken the guard.</b> The
     * name is not a strict per-file identifier: the round is allocated per
     * {@code (TRAN_DT, OUT_BANK_CD, BANK_PRTN_ID)} while the name only encodes
     * {@code (VAN_ORG_CD, FILE_TYPE)}, and JEX DEV holds three distinct {@code BANK_PRTN_ID} values
     * under {@code VAN_ORG_CD='GV00000001'} / {@code FILE_TYPE='SHB'} — so two files of the same tenant
     * and date could in principle share a name (one such pair exists in DEV:
     * {@code S_GV00000001_SHB_002_20250711} on two {@code FILE_SEQ_NO}s). That over-breadth is
     * <b>pre-existing and unchanged by this method</b> — the predicate it replaces did not carry
     * {@code OUT_BANK_CD}, {@code BANK_PRTN_ID} or {@code FILE_SEQ_NO} either, and
     * {@code TB_FIRM_BAT_API_TRAN} has <b>no {@code FILE_SEQ_NO} column</b> to narrow on. It also errs
     * in the safe direction for a money path: an over-broad guard can only <i>suppress</i> a second
     * 00302, never authorise one.
     *
     * <h2>Index — read before adding callers</h2>
     * <b>No index supports this predicate.</b> {@code TB_FIRM_BAT_API_TRAN} has exactly one index on
     * both DBs — the PK {@code (TRAN_DT, BIZ_KEY, TRAN_SEQ, API_TRAN_TP, OUT_BANK_CD, BANK_PRTN_ID)} —
     * which <b>leads with {@code TRAN_DT}</b> and is therefore unusable once that column leaves the
     * predicate; {@code FIRM_FILE_NM} is not indexed at all. This is a sequential scan, and it is
     * accepted for now on size (JEX DEV: 1,304 rows / 360 kB; refactor DEV: 0 rows) and on frequency
     * (once per bulk file). {@code Claude Code (AI)/sql/tb_firm_bat_api_tran_idempotency_index_20260730.sql}
     * carries the covering index as a DBA-run script; until it is applied, do not add a second caller
     * on a hot path.
     */
    boolean existsByBizKeyAndFirmFileNmAndApiTranTp(
            String bizKey, String firmFileNm, String apiTranTp);

    /**
     * As {@link #existsByBizKeyAndFirmFileNmAndApiTranTp}, but matching {@code FIRM_FILE_NM} with the
     * leading 2 characters stripped on both sides — the same rule as legacy's
     * {@code substring(FIRM_FILE_NM,3)} joins.
     *
     * <h2>Why a prefix-insensitive probe is required for the 01302 leg</h2>
     * The two systems store a <b>different name</b> on the {@code 01302} audit row:
     * <ul>
     *   <li><b>Legacy</b> writes the callback's name — the bank's {@code R_…} result file
     *       ({@code FILE_DEPOSIT_TRANSFER_API2_act.jsp:189}, {@code put("FIRM_FILE_NM", file_nm)});</li>
     *   <li><b>the refactor</b> writes the MST's sent {@code S_…} name
     *       ({@code BulkExecutionResultService.writeApiTran}).</li>
     * </ul>
     * So an exact-name probe against a firm file (which always holds the {@code S_} name) returns
     * {@code false} for <b>every legacy-written row</b>, whether or not a callback ever arrived. That is
     * not a harmless miss: it is the difference between "the gateway lost the result" and "the result
     * arrived and our handler kept rolling back", and those have different owners and different fixes.
     *
     * <p><b>Diagnostic use only.</b> This must NOT be used as an idempotency guard: prefix-insensitivity
     * widens the match, and for the {@code 00302} send-guard a wider match is the wrong direction —
     * {@link #existsByBizKeyAndFirmFileNmAndApiTranTp} stays the guard for that path.
     *
     * <p>Same index caveat as the method above — sequential scan, one call per stranded file per sweep.
     */
    @Query("""
            SELECT COUNT(a) > 0 FROM TbFirmBatApiTran a
             WHERE a.bizKey = :bizKey
               AND a.apiTranTp = :apiTranTp
               AND substring(a.firmFileNm, 3) = substring(:firmFileNm, 3)
            """)
    boolean existsForFileIgnoringNamePrefix(@Param("bizKey") String bizKey,
                                            @Param("firmFileNm") String firmFileNm,
                                            @Param("apiTranTp") String apiTranTp);
}
