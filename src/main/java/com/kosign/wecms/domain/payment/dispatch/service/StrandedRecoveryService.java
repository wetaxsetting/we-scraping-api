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
 *
 * <p><b>The real-time leg hands off to the shared paced drain.</b> {@link #recoverPayTargets} enqueues
 * onto {@link RealtimeDispatchQueue} rather than dispatching inline, so the legacy per-bank inter-request
 * delay is applied once, on one worker thread, for both this rail and the request-thread rail (C-6). The
 * delay is keyed on <i>each row's own</i> {@code OUT_BANK_CD}, a deliberate divergence from legacy's
 * leaked {@code per_request_delay} (D-A). Full rationale, including the rate change it implies for
 * third-party banks, is on that method.
 */
@Slf4j
@Service
public class StrandedRecoveryService {

    private static final DateTimeFormatter DTTM14 = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final ZoneId ICT = ZoneId.of("Asia/Ho_Chi_Minh");

    private final TbPayTargetRepository payTargetRepository;
    private final TbPayFileMstRepository payFileMstRepository;
    private final BulkReceptionService bulkReceptionService;
    private final ResultCheckService resultCheckService;
    private final com.kosign.wecms.common.lock.JobLockGuard jobLockGuard;
    /**
     * The single paced drain shared with the request-thread rail — see {@link #recoverPayTargets}.
     *
     * <p><b>{@link RealtimeDispatchService} is deliberately NOT injected here (removed 2026-08-01).</b>
     * C-6 left it unused, and an unused collaborator on this class is precisely the seam a second
     * dispatch path would reappear through: adding one {@code realtimeDispatchService.dispatch(...)}
     * call back into the sweep would compile, pass review as "just re-driving a row", and silently
     * recreate a second unpaced producer against the same gateway. Removing the reference makes that
     * a change to the constructor — visible, and to every caller.</p>
     */
    private final RealtimeDispatchQueue realtimeDispatchQueue;

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
                                   BulkReceptionService bulkReceptionService,
                                   ResultCheckService resultCheckService,
                                   com.kosign.wecms.common.lock.JobLockGuard jobLockGuard,
                                   RealtimeDispatchQueue realtimeDispatchQueue) {
        this.payTargetRepository = payTargetRepository;
        this.payFileMstRepository = payFileMstRepository;
        this.bulkReceptionService = bulkReceptionService;
        this.resultCheckService = resultCheckService;
        this.jobLockGuard = jobLockGuard;
        this.realtimeDispatchQueue = realtimeDispatchQueue;
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
        // Bounds match ResultCheckJob exactly (PT30M / PT5S) so neither entry point can outlive the
        // other's ceiling. They did NOT match between 2026-07-30 and 2026-07-31, when the job's
        // lockAtMostFor was PT2H30M against the 30 minutes hardcoded below: a delegated pass lost the
        // lock at 30 min while a scheduled pass could hold it for 150, which is the concurrency the
        // shared identity exists to prevent. The job's ceiling is now deadline-derived and lands on
        // PT30M (see ResultCheckJob.run's derivation); if it moves again, move these two with it —
        // StrandedRecoveryResultCheckLockTest pins the pair.
        // Not acquired => ResultCheckJob is already running this body; skipping is the correct
        // outcome, and the rows stay at '6' for whichever pass gets there.
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

    /**
     * Hand the aged {@code PROC_STAT='9'} real-time rows to {@link RealtimeDispatchQueue} — the single
     * paced drain shared with the request-thread rail (C-6).
     *
     * <h2>Why this rail must be paced at all</h2>
     * Every other refactor path onto the 202 is one request per business event. This one is a
     * <b>drain</b>: it can materialise up to {@code recovery.batch-size} (default 50) stranded rows and
     * fire a 202 for each, back to back. That is the same shape as the legacy polling batch this service
     * replaced, and the shape the legacy per-request delay existed to throttle.
     *
     * <h2>One pacing point, not two (changed 2026-07-31)</h2>
     * This method briefly called {@code dispatchPacer.pause(...)} itself, in the same change that added
     * pacing to this rail. That was superseded within the day by {@link RealtimeDispatchQueue}: with the
     * request-thread rail also paced, two independent pacing points would mean two threads each honouring
     * a 100 ms/3000 ms delay against the same gateway — which is not a rate limit. Enqueueing here puts
     * both producers behind one FIFO worker, so the delay between consecutive gateway requests is the
     * delay, whichever rail produced them.
     *
     * <p><b>The sweep no longer waits for the dispatches it triggers.</b> {@code recoverOnce()} returns
     * once the rows are queued, so its ShedLock is released while work is still in flight. That is safe
     * and was already true of correctness: the {@code '9'→'6'} claim inside
     * {@link RealtimeDispatchService#dispatch} is atomic, so a later pass that re-selects a row still in
     * the queue enqueues a duplicate ticket whose dispatch claims 0 rows and no-ops.
     *
     * <h2>Pacing is per row, from that row's bank — a DELIBERATE divergence from legacy (D-A)</h2>
     * Legacy {@code NAPAS_REAL_TIME_BATCH} declares {@code int per_request_delay = PER_DELAY_DEFAULT} at
     * <b>{@code :68}, outside</b> its row loop ({@code :82}), and inside the loop only ever <b>raises</b>
     * it — {@code :276} Shinhan (100 ms), {@code :278} Woori (3000 ms) — <b>never resetting it to the
     * default</b>. So the variable leaks across iterations: once any Woori row is processed, every
     * subsequent row in that pass sleeps 3000 ms regardless of its own bank
     * ({@code Thread.sleep(per_request_delay)} at {@code :360 / :481 / :589 / :698}).
     *
     * <p><b>WITHDRAWN 2026-08-01 — this paragraph claimed a rate increase that has no subject.</b> It
     * used to read: <i>"legacy's effective rate toward non-Shinhan, non-Woori banks has often been
     * 3000 ms rather than the configured 100 ms … up to 30× faster than the rate they actually receive
     * today."</i> The leak can only mis-pace a bank outside {@code {970424, 970457}}, and <b>no such
     * bank has ever been on the 202 rail</b>: JEX DEV {@code TB_FIRM_TRAN TRAN_TP='202'} is
     * {@code 970424} × 46 + {@code 970457} × 6 and nothing else; VCB {@code 970436} appears only as 4
     * {@code PROC_STAT='3'} FAIL pay-targets that never dispatched, and is now file-routed. There is
     * no bank receiving 3000 ms today that would drop to 100 ms. Shinhan gets 100 ms in both systems.
     *
     * <p>Legacy's spacing was also never a <i>guarantee</i> to revert to: it is order-dependent on
     * {@code TB_PAY_TARGET_R001}, which has no {@code ORDER BY} (B-1), so identical workloads could
     * space at 100 ms or 3000 ms on consecutive days. The residual concern — an unknown bank pacing at
     * an unmeasured rate — is addressed directly by {@code DispatchPacer.PER_DELAY_DEFAULT} = 3000 ms,
     * not by importing the leak.
     *
     * <p><b>Why this reads as a legacy defect and not a design.</b> The sibling batch does it correctly:
     * {@code PROCESSING_RESULT_CHECK_BATCH:82} declares {@code per_request_delay} <b>inside</b> its loop,
     * so it resets every iteration. Same codebase, same idiom, same constants — one scopes the variable
     * per row and one does not. A 30× throttle applied to every bank that follows a Woori row is not a
     * policy anyone would write on purpose, and no comment claims it. We therefore match the sibling.
     */
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
        log.info("stranded-recovery: {} aged real-time pay-target(s) at PROC_STAT='9' — handing to the "
                + "paced dispatch drain", aged.size());
        int queued = 0;
        for (TbPayTarget pay : aged) {
            // Re-drive through the normal dispatch path (its G2 atomic '9'→'6' claim de-dupes), but via
            // the shared drain so pacing happens once, on one thread. A rejected ticket is logged by the
            // queue and leaves the row at '9' for the next pass — never rethrown, so one drop cannot
            // cost the rest of the sweep.
            if (realtimeDispatchQueue.enqueue(pay.getBizKey(), pay.getTranDt(), pay.getTranSeq())) {
                queued++;
            }
        }
        if (queued < aged.size()) {
            log.warn("stranded-recovery: queued only {} of {} aged pay-target(s) — the dispatch drain is "
                    + "saturated or shut down; the remainder stay PROC_STAT='9'", queued, aged.size());
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
