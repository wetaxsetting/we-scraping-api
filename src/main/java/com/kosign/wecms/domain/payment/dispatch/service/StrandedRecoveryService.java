package com.kosign.wecms.domain.payment.dispatch.service;

import com.kosign.wecms.domain.payment.dispatch.entity.TbPayFileMst;
import com.kosign.wecms.domain.payment.dispatch.entity.TbPayTarget;
import com.kosign.wecms.domain.payment.dispatch.enums.ProcStat;
import com.kosign.wecms.domain.payment.dispatch.repository.TbPayFileMstRepository;
import com.kosign.wecms.domain.payment.dispatch.repository.TbPayTargetRepository;
import org.springframework.data.domain.PageRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * R-1 stranded-record recovery safety net (Item 4) — a bounded, opt-in backstop for records that
 * the approved event-driven TO-BE leaves stranded (a lost/failed after-commit hand-off, a flag
 * flipped ON after rows already existed, a crash in the commit→afterCommit window). It does
 * <b>NOT</b> restore the four legacy polling batches; the event-driven path stays primary.
 *
 * <p><b>Recovers only genuinely-stranded rows.</b> Eligibility = the row is still in its WAIT/in-flight
 * state AND its {@code PROC_DTTM} predates a configurable grace window (default 30&nbsp;min), so a row
 * the normal after-commit path is actively processing right now is never grabbed:
 * <ul>
 *   <li>{@code TB_PAY_TARGET PROC_STAT='9'} → re-drives {@link RealtimeDispatchService#dispatch},
 *       which performs the G2 <b>atomic {@code '9'→'6'} claim</b> — a concurrent/duplicate driver
 *       claims 0 rows and no-ops, so the 202 (and any Woori 204) never double-runs.</li>
 *   <li>{@code TB_PAY_FILE_MST PROC_STAT='9'} → re-drives {@link BulkReceptionService#receive},
 *       which performs the H-4 <b>atomic {@code '9'→'6'} claim</b> before any file build, SFTP upload
 *       or EFS 00301 — so a sweep that races the normal after-commit hand-off claims 0 rows and
 *       no-ops without any external call.
 *       <p><b>Corrected 2026-07-29.</b> This previously read "whose WAIT gate + firm-file PK make a
 *       re-run idempotent (a duplicate build collides on the {@code TB_FIRM_FILE_MST} PK)". That was
 *       wrong on both counts: the WAIT gate was a non-atomic read-then-check, and the PK collision is
 *       raised at commit — i.e. <i>after</i> the upload and the 00301 call — so it could not prevent a
 *       duplicate submission. This sweep was the concrete second driver that made the race reachable.
 *       See {@code docs/architecture/bulk-dispatch-concurrency-fix.md}.</li>
 *   <li>Eligible firm transactions stuck at {@code PROC_STAT='6'} → delegated to
 *       {@link ResultCheckService#runOnce} (the existing aged-202 → 203 scan, now atomic per Item&nbsp;1).
 *       <b>This leg has a second gate of its own:</b> {@code wecms.dispatch.result-check.enabled}
 *       (default false). {@code wecms.dispatch.recovery.enabled=true} alone does <i>not</i> enable it,
 *       so a pass can legitimately recover the two {@code '9'} classes and skip this one — which it
 *       reports at WARN rather than passing over silently.</li>
 *   <li>Firm files stuck mid-step ({@code TB_FIRM_FILE_MST} step {@code 0}/{@code 1}) → <b>logged
 *       only, not auto-driven</b> — re-driving a partially-processed bulk file safely needs the
 *       01301/01302 callback state machine and a per-step design that is deliberately out of scope
 *       here (documented TO-BE open item K9).</li>
 * </ul>
 *
 * <p><b>DISABLED by default — coexistence-gated.</b> {@code recoverOnce()} is a no-op unless
 * {@code wecms.dispatch.recovery.enabled=true} <i>and</i> the job's cron is set (both default OFF).
 * The double gate exists because refactor-originated rows are <b>not distinguishable</b> from
 * live-JEX rows on a shared DB (both draw the same global {@code firm_seq_no}/{@code firm_file_seq_no}
 * sequences; there is no source column / application id / cutover marker). If the legacy JEX batch
 * runs against the same database, a recovery sweep could double-process JEX-originated rows — so the
 * cron MUST stay disabled until the coexistence rule is resolved (owner: PO / Mr. Kang). The atomic
 * {@code '9'→'6'} claim guards concurrency <i>within</i> the refactor only, not across the two systems.
 *
 * <p>Per-row failures are isolated (logged, never rethrown) so one bad row never blocks the sweep.
 */
@Slf4j
@Service
public class StrandedRecoveryService {

    private static final DateTimeFormatter DTTM14 = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final ZoneId ICT = ZoneId.of("Asia/Ho_Chi_Minh");

    private final TbPayTargetRepository payTargetRepository;
    private final TbPayFileMstRepository payFileMstRepository;
    private final RealtimeDispatchService realtimeDispatchService;
    private final BulkReceptionService bulkReceptionService;
    private final ResultCheckService resultCheckService;
    private final com.kosign.wecms.common.lock.JobLockGuard jobLockGuard;

    /** Master gate — OFF by default. Even with a cron set, the sweep is inert until this is true. */
    @Value("${wecms.dispatch.recovery.enabled:false}")
    private boolean enabled;

    /** Grace window (minutes): only rows whose PROC_DTTM is older than {@code now - grace} are eligible. */
    /**
     * H-2 batch cap per sweep, per rail. Bounds how many stranded rows one pass may materialise and
     * re-drive. Sized like the sibling sweeps' defaults; oldest-first ordering means a cap only defers
     * work to the next pass, never drops it.
     */
    @Value("${wecms.dispatch.recovery.batch-size:50}")
    private int batchSize;

    @Value("${wecms.dispatch.recovery.grace-minutes:30}")
    private long graceMinutes;

    public StrandedRecoveryService(TbPayTargetRepository payTargetRepository,
                                   TbPayFileMstRepository payFileMstRepository,
                                   RealtimeDispatchService realtimeDispatchService,
                                   BulkReceptionService bulkReceptionService,
                                   ResultCheckService resultCheckService,
                                   com.kosign.wecms.common.lock.JobLockGuard jobLockGuard) {
        this.payTargetRepository = payTargetRepository;
        this.payFileMstRepository = payFileMstRepository;
        this.realtimeDispatchService = realtimeDispatchService;
        this.bulkReceptionService = bulkReceptionService;
        this.resultCheckService = resultCheckService;
        this.jobLockGuard = jobLockGuard;
    }

    /**
     * One recovery pass. No-op unless {@link #enabled}. Re-drives stranded {@code '9'} pay-targets
     * and pay-files older than the grace window through the existing (atomic/idempotent) dispatch and
     * reception paths, and delegates aged {@code '6'} firm-transaction confirmation to the result-check.
     */
    public void recoverOnce() {
        if (!enabled) {
            log.debug("stranded-recovery disabled (wecms.dispatch.recovery.enabled=false) — no-op");
            return;
        }
        String cutoff = LocalDateTime.now(ICT).minusMinutes(graceMinutes).format(DTTM14);
        log.info("stranded-recovery: sweeping rows older than {} (grace {}m)", cutoff, graceMinutes);

        recoverPayTargets(cutoff);
        recoverPayFiles(cutoff);

        // Aged firm-transaction (202/'6') confirmation is the existing result-check's job — atomic
        // per Item 1. Delegated here so a single recovery pass covers all four strand classes.
        //
        // The delegation is guarded by the result-check body's OWN lock identity
        // (JobLockNames.DISPATCH_RESULT_CHECK — the same name ResultCheckJob's @SchedulerLock
        // uses), NOT by this job's lock. Without it, enabling both jobs would let the identical
        // runOnce() body execute concurrently under two different lock names, double-processing
        // live PROC_STAT='6' payment rows (scheduler-audit finding F-8 / parity-audit F-9).
        // Bounds match ResultCheckJob exactly so neither entry point can outlive the other's
        // ceiling. Not acquired => ResultCheckJob is already running this body; skipping is the
        // correct outcome, and the rows stay at '6' for whichever pass gets there.
        // The result-check body has its OWN master gate (wecms.dispatch.result-check.enabled, default
        // false), which answers "may this instance drive EFS 203 traffic and finalize payment rows?" —
        // a question whose answer cannot depend on which entry point asked. So enabling recovery alone
        // does NOT enable this leg, and a sweep that quietly recovered three of its four strand classes
        // would look like a complete pass. Say so, at WARN: partial recovery with no signal is the trap.
        if (!resultCheckService.isEnabled()) {
            log.warn("stranded-recovery: aged firm-transaction (202/PROC_STAT='6') recovery SKIPPED — "
                    + "wecms.dispatch.result-check.enabled=false. The pay-target and pay-file legs above "
                    + "DID run, so this pass is partial: rows stuck at '6' awaiting 203 confirmation were "
                    + "not touched. Set wecms.dispatch.result-check.enabled=true (subject to the same "
                    + "legacy-coexistence precondition as ResultCheckJob) to cover this class too.");
        } else {
            try {
                boolean ran = jobLockGuard.runIfFree(
                        com.kosign.wecms.common.lock.JobLockGuard.LockKey.of(
                                com.kosign.wecms.common.lock.JobLockNames.DISPATCH_RESULT_CHECK,
                                java.time.Duration.ofMinutes(30),
                                java.time.Duration.ofSeconds(5)),
                        resultCheckService::runOnce);
                if (!ran) {
                    log.info("stranded-recovery: result-check already running elsewhere — delegation skipped");
                }
            } catch (Exception e) {
                log.warn("stranded-recovery: result-check delegation failed — {}", e.getMessage());
            }
        }
    }

    /** Clamp the configured cap to at least 1 so a mis-set 0 cannot silently disable the sweep. */
    private int effectiveBatchSize() {
        return Math.max(1, batchSize);
    }

    private void recoverPayTargets(String cutoff) {
        // H-2: bounded + oldest-first. Previously findByProcStatAndProcDttmLessThan, which had no
        // LIMIT and no ORDER BY — against the live JEX DB that materialises every historical '9' row
        // on every sweep. Ordering oldest-first means a truncated sweep drains its backlog instead of
        // starving the tail; the age cutoff is a SQL predicate applied before the limit (the N-1
        // lesson from the result-check scan).
        int limit = effectiveBatchSize();
        List<TbPayTarget> aged = payTargetRepository
                .findStrandedPayTargets(ProcStat.WAIT.code(), cutoff, PageRequest.of(0, limit));
        if (aged.isEmpty()) {
            return;
        }
        if (aged.size() >= limit) {
            log.warn("stranded-recovery: pay-target sweep hit the batch cap ({}) — more stranded rows "
                    + "remain and will be picked up by the next sweep", limit);
        }
        log.info("stranded-recovery: {} aged real-time pay-target(s) at PROC_STAT='9'", aged.size());
        for (TbPayTarget pay : aged) {
            try {
                // Re-drive through the normal dispatch path; its G2 atomic '9'→'6' claim de-dupes.
                realtimeDispatchService.dispatch(pay.getBizKey(), pay.getTranDt(), pay.getTranSeq());
            } catch (Exception e) {
                log.warn("stranded-recovery: pay-target re-dispatch failed bizKey={} tranDt={} tranSeq={} — {}",
                        pay.getBizKey(), pay.getTranDt(), pay.getTranSeq(), e.getMessage());
            }
        }
    }

    private void recoverPayFiles(String cutoff) {
        // H-2: bounded + oldest-first, same rationale as recoverPayTargets. findStrandedPayFiles
        // already existed for this purpose; this sweep had never been switched over to it.
        int limit = effectiveBatchSize();
        List<TbPayFileMst> aged = payFileMstRepository
                .findStrandedPayFiles(ProcStat.WAIT.code(), cutoff, PageRequest.of(0, limit));
        if (aged.isEmpty()) {
            return;
        }
        if (aged.size() >= limit) {
            log.warn("stranded-recovery: pay-file sweep hit the batch cap ({}) — more stranded rows "
                    + "remain and will be picked up by the next sweep", limit);
        }
        log.info("stranded-recovery: {} aged bulk pay-file(s) at PROC_STAT='9'", aged.size());
        for (TbPayFileMst pay : aged) {
            try {
                // Re-drive through the normal reception path; its H-4 atomic '9'→'6' claim de-dupes
                // before any SFTP upload or EFS 00301, so racing the after-commit hand-off is safe.
                bulkReceptionService.receive(pay.getTranDt(), pay.getBizKey(), pay.getFileSeqNo());
            } catch (Exception e) {
                log.warn("stranded-recovery: pay-file re-receive failed bizKey={} tranDt={} fileSeqNo={} — {}",
                        pay.getBizKey(), pay.getTranDt(), pay.getFileSeqNo(), e.getMessage());
            }
        }
    }
}
