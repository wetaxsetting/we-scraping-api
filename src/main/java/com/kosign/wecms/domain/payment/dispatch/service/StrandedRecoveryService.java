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
 * <h2>C-12 — "nothing advances a JEX-written {@code '9'} row" (reframed 2026-08-03, verified against legacy)</h2>
 *
 * <p><b>Legacy does not solve C-12. It has the same gap, silently.</b> Both legacy selections are
 * <i>today-only</i>, so a {@code '9'} row is retried on every tick for the rest of the calendar day and
 * then falls out of the query permanently — still {@code '9'}, with nothing written to mark it. Legacy
 * simply has no detection, which is why the gap was never named there. Our pay-target {@code '9'} sweep
 * is the detection legacy lacks.
 *
 * <ul>
 *   <li><b>Neither query carries an origin, tenant or source predicate</b> (verified in full).
 *       {@code IDO.TB_PAY_TARGET_R001.xml:24-45} takes <i>no parameters at all</i> — an empty
 *       {@code <in>} block — and reads
 *       {@code WHERE TRAN_DT = TO_CHAR(now(),'yyyymmdd') AND PROC_STAT = '9'}, with no {@code BIZ_KEY}
 *       and no {@code ORDER BY} (the B-1 note). {@code IDO.TB_PAY_FILE_MST_R001.xml:24-44} binds only
 *       {@code :PROC_STAT} + {@code :TRAN_DT}, which {@code FILE_DEPOSIT_TRANSFER_BATCH1:86-87} sets to
 *       the constants {@code PROC_ST_WAIT} ({@code '9'}) and today. Legacy takes whatever is {@code '9'}
 *       today, from anyone.</li>
 *   <li><b>The provenance problem is a SHARED-DATABASE problem only.</b> On a separate database there
 *       are no JEX-written rows, so "who wrote this row" has one possible answer and the question
 *       dissolves. Legacy never encounters it because it is the sole driver against its own DB.</li>
 *   <li><b>The hazard is not misattribution — it is two independent drivers on one queue.</b> Both
 *       would select the same {@code '9'} row, and the atomic {@code '9'→'6'} claims plus ShedLock
 *       coordinate only <i>within</i> the refactor; the legacy JEX batch participates in neither. So the
 *       failure mode is a genuine double-dispatch — a duplicate payment — not a bookkeeping error.
 *       That is what makes the current double-gating proportionate rather than merely cautious.</li>
 *   <li><b>Legacy's precondition asymmetry — match it if this sweep ever grows its own checks.</b>
 *       {@code NAPAS_REAL_TIME_BATCH} writes a terminal {@code PROC_STAT='3'} when no
 *       {@code TB_BATCH_SCHEDULE_INFO} row exists for the bank ({@code :125-130}), when no schedule
 *       detail exists for the {@code ANT_DIV_CD} ({@code :155-161}), or when the bank/network window has
 *       CLOSED for the day ({@code EXE_DIV='E'}, {@code :171-175} / {@code :184-187}) — all via
 *       {@code TB_PAY_TARGET_U001} at {@code :191-205}, with an alert. But a row that is merely
 *       <i>not yet open</i> takes the bare {@code continue} at {@code :227-230} and <b>stays
 *       {@code '9'}</b>. "No rule for this bank" and "window closed today" are terminal; "not open yet"
 *       is not. (For completeness: a 202 no-response / {@code G999} moves the row to {@code '6'} at
 *       {@code :576-587}, and a 202 non-{@code 0000} to {@code '3'} at {@code :667-679}; the file leg
 *       writes {@code '3'} on a validation failure, {@code FILE_DEPOSIT_TRANSFER_BATCH1:234-239}.)</li>
 * </ul>
 *
 * <h2>C-12 — the gate this should be behind (PROPOSED, NOT IMPLEMENTED)</h2>
 *
 * <p>Because the problem is shared-database-only, the correct gate is on <b>whether the database is
 * shared</b>, not a permanent global OFF. Proposed shape — <b>do not implement without PO sign-off</b>:
 * a third property {@code wecms.dispatch.legacy-jex-shares-database}, <b>defaulting to {@code true}</b>
 * (fail-closed), with {@code recovery.enabled} effective only when it is <i>explicitly</i> {@code false},
 * and a startup log line stating which way it resolved and against which JDBC host.
 *
 * <p><b>It must be an operator declaration, not an inference from the DB target.</b> Three reasons:
 * <ol>
 *   <li>Memory {@code project_uat_prod_live_jex_db_target} records a standing PO directive that the
 *       refactor runs against the <b>live JEX DB in all environments</b>. SIT points at the refactor's
 *       own dev DB today (team decision 2026-05-26, memory {@code reference-sit-endpoints}), which makes
 *       it the separate-DB case — but that is a current-state fact, not a durable guarantee, and the
 *       directive points the other way.</li>
 *   <li>A JDBC URL cannot answer the question that actually matters — <i>"is the legacy
 *       {@code WECMS_BATCH} pointed at this same database?"</i> That target lives in JEX property files
 *       on a different host ({@code <target>WABOOKSVNDEV_DB</target>} in every IDO). A URL check would
 *       answer an easier question while appearing to answer the real one.</li>
 *   <li>Deployed config on these hosts already diverges from the committed {@code .env.*} (memories
 *       {@code project_batch_deployed_flags_diverge_from_repo}, {@code reference_sit_server_config_setenv}
 *       — SIT loads from host env, not the repo file). A gate keyed on an env-supplied URL would be
 *       exactly as fragile as the flags it is meant to reinforce.</li>
 * </ol>
 *
 * <p><b>Nothing in the code distinguishes the two cases today.</b> {@code WECMS_DB_URL} is a bare
 * placeholder in every profile and is never read or inspected; there is no property, profile check or
 * datasource inspection anywhere. The word "coexistence" appears only in Javadoc and YAML comments —
 * prose, not a guard. Enabling recovery on SIT right now is a pure {@code .env} change with no
 * code-level check that the database is in fact separate.
 *
 * <p><b>Operational caveat for whoever enables it first, independent of the gate.</b> This sweep has
 * <b>no date bound</b> where legacy's is today-only: {@link #recoverPayTargets} filters on the grace
 * window alone. That widening is the entire point of C-12 — it recovers exactly the rows legacy
 * abandons at midnight — but it means the first enabled pass sees <b>every historical {@code '9'} row in
 * the table, from any date</b>. {@code recovery.batch-size} (default 50) and oldest-first ordering bound
 * each pass, <b>not the total</b>. Plan the first run accordingly.
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
     * space at 100 ms or 3000 ms on consecutive days.
     *
     * <p><b>The residual concern is NOT addressed by moving the default (corrected 2026-08-05).</b> This
     * paragraph used to close with "addressed directly by {@code DispatchPacer.PER_DELAY_DEFAULT} =
     * 3000 ms". That raise has been withdrawn: handover {@code 05 §5.3} / {@code 06 §6.3} document 100 ms
     * as the 기본 딜레이 and name BIDV / Vietcombank / Kookmin as banks that run on it, while
     * {@code 08 §8.4} attributes 3000 ms to <b>Woori's</b> rate limit specifically. D-A is fixed by the
     * per-row keying — the structural half — and a new bank's throughput belongs in its own constant.
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
