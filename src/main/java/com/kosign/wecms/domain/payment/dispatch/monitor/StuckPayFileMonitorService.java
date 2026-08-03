package com.kosign.wecms.domain.payment.dispatch.monitor;

import com.kosign.wecms.domain.payment.dispatch.alert.OpsAlertService;
import com.kosign.wecms.domain.payment.dispatch.constant.AntDivChannel;
import com.kosign.wecms.domain.payment.dispatch.entity.TbFirmFileMst;
import com.kosign.wecms.domain.payment.dispatch.entity.TbPayFileData;
import com.kosign.wecms.domain.payment.dispatch.entity.TbPayFileMst;
import com.kosign.wecms.domain.payment.dispatch.entity.TbPayTarget;
import com.kosign.wecms.domain.payment.dispatch.enums.ApiTranTp;
import com.kosign.wecms.domain.payment.dispatch.enums.ProcStat;
import com.kosign.wecms.domain.payment.dispatch.enums.ProcStep;
import com.kosign.wecms.domain.payment.dispatch.repository.TbPayFileDataRepository;
import com.kosign.wecms.domain.payment.dispatch.repository.TbPayFileMstRepository;
import com.kosign.wecms.domain.payment.dispatch.service.OperatingHoursDecision;
import com.kosign.wecms.domain.payment.dispatch.service.OperatingHoursService;
import com.kosign.wecms.domain.setting.firmbankingsettings.entity.TbFirmConf;
import com.kosign.wecms.domain.payment.dispatch.repository.TbFirmConfRepository;
import com.kosign.wecms.domain.setting.firmbankingsettings.entity.TbFirmConfId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Read-only operational monitor for stranded firm-banking dispatch rows — four sweeps over three
 * tables, sharing one cause map, one aggregated alert and one ShedLock-serialised job:
 * <ol>
 *   <li>{@code TB_PAY_FILE_MST} at {@code PROC_STAT='9'} — bulk file never handed to reception;</li>
 *   <li>{@code TB_FIRM_FILE_MST} at {@code PROC_STEP='0'}/{@code '1'}/{@code '2'} with
 *       {@code PROC_STAT='6'} — reception never reconciled, or past {@code 00301} and awaiting a
 *       confirmed {@code 00302} or its {@code 01302} result. <b>The {@code '0'} leg was added
 *       2026-07-31</b>: before it, a batch whose phase B broke part-way left its unsent siblings visible
 *       to nothing at all (the parent pay-file goes {@code '3'}, so both pay-file sweeps skip it), and so
 *       did every file whose {@code 01301} was lost after the bank had accepted it;</li>
 *   <li>{@code TB_PAY_FILE_MST} at {@code PROC_STAT='6'} — claimed but never finished (H-3 / N-3);</li>
 *   <li>{@code TB_PAY_TARGET} at {@code PROC_STAT='9'} — the <b>real-time (202) rail</b>, added
 *       2026-07-31, with its own grace window ({@link #payTargetThresholdMinutes}).</li>
 * </ol>
 *
 * <p><b>Why the fourth sweep has its own window.</b> Sweeps 1–3 watch a rail that legitimately waits —
 * for an operating-hours window, for a callback, for a sibling. The real-time rail waits for nothing:
 * {@code RealtimeDispatchRegistrar} hands each committed {@code TRAN_SEQ} to the 202 in an
 * {@code afterCommit} hook, on the same thread, milliseconds after the approval transaction commits. A
 * row still at WAIT minutes later is not early — the hand-off ran and returned or threw. Reusing
 * {@link #thresholdMinutes} would either make sweeps 1–3 noisy or make sweep 4 blind for 15 minutes;
 * separate knobs are what let both be tuned honestly. {@code classifyPayTarget} names which early exit
 * happened.
 *
 * <h2>Why the first sweep exists</h2>
 * {@code OperatingHoursService.evaluateBulk} returns {@code SKIP} when a file's channel set is
 * empty, and {@code BulkReceptionService} then returns without writing anything — <b>no FAIL and
 * no ops alert</b>. That is deliberate legacy behaviour (legacy {@code !exeYN → continue}) and is
 * NOT changed here. The consequence is that such a file can sit at WAIT forever, invisibly. The
 * F-1 fix removed the systemic Payroll case, but three routes remain:
 * <ul>
 *   <li>the two <b>intentional legacy NULL branches</b> — VCB foreign currency, Woori VND
 *       same-bank ({@code pymn_0005_001_c001_act.jsp:221-223} / {@code :256-259});</li>
 *   <li>an <b>unrecognised</b> {@code ANT_DIV_CD} (HAENGSUNG ERP raw passthrough);</li>
 *   <li>the dispatch flag being off when the rows were written.</li>
 * </ul>
 * This monitor makes all of them visible. It is the answer to open question <b>Q-4</b> in
 * {@code docs/audits/payment-dispatch-operating-hours-flow.md} — <i>observe</i> the condition
 * rather than change the gate's semantics.
 *
 * <h2>Monitoring only — never mutates</h2>
 * The sweep performs <b>no writes</b>. It does not touch {@code PROC_STAT}, does not re-drive
 * dispatch, and does not enable anything. It is deliberately <b>not</b> part of
 * {@code StrandedRecoveryService}, which <i>does</i> re-drive rows: that service is gated behind
 * {@code wecms.dispatch.recovery.enabled}, which is coexistence-gated and must stay OFF until the
 * shared-DB question is resolved (PO / Mr. Kang). Folding read-only monitoring into it would make
 * observability unavailable exactly while the mutating gate is closed. The two share the
 * repository finder, the age-window eligibility rule, the ShedLock table and {@link OpsAlertService};
 * only the schedule and the enable flag are separate.
 *
 * <h2>Gating — ON in SIT and UAT, OFF in local and prod</h2>
 * Still double-gated ({@link #monitorOnce()} is a no-op unless
 * {@code wecms.dispatch.stuck-monitor.enabled=true}, <b>and</b> the driving job needs a cron), but the
 * per-profile defaults now differ:
 * <ul>
 *   <li><b>{@code sit} / {@code uat}: {@code enabled=true}, {@code cron=0 *}{@code /5 * * * *}</b> —
 *       both gates open by default. Changed deliberately (C-14 / batch-1 follow-up): after the
 *       fail-closed fix in {@code BulkReceptionService} this sweep is the <b>only</b> detection for
 *       {@link StuckPayFileCause#BULK_EXECUTION_UNCONFIRMED} and
 *       {@link StuckPayFileCause#BULK_EXECUTION_NOT_ISSUED}, two states with no automatic retry that
 *       resolve solely by operator action. Off, they are invisible until someone queries
 *       {@code TB_FIRM_FILE_MST} by hand. The reconciliation procedure for each is
 *       {@code docs/deployment/bulk-file-reconciliation-procedure.md}.</li>
 *   <li><b>{@code local} / {@code prod}: both default off</b> — local has no dispatch traffic to
 *       observe, and prod activation follows the runbook rather than a file default.</li>
 * </ul>
 *
 * <p>Enabling it by default is safe here in a way it is <b>not</b> for
 * {@code StrandedRecoveryService}, and the reason is not "monitoring is less important": that sweep
 * must stay off because it <i>re-drives</i> rows and cannot tell refactor rows from legacy-JEX rows on
 * a shared DB. This sweep performs no writes at all, so the coexistence hazard does not exist for it.
 * Reading a legacy-written row and reporting it is the intended behaviour.
 *
 * <p>Every gate still carries an in-yaml default bound to an environment variable, so an unset
 * variable cannot abort startup (readiness finding H-1) — the safety property comes from the defaults,
 * not from the keys' absence. Alerting remains separately gated by
 * {@code wecms.dispatch.alert.enabled}; with alerts off the sweep logs only.
 *
 * <h2>Eligibility — and why in-flight rows cannot be caught</h2>
 * Reuses {@code TbPayFileMstRepository.findStrandedPayFiles} (bounded + oldest-first since H-2), the same rule
 * {@code StrandedRecoveryService} uses. The schema's reliable in-progress indicator is
 * {@code PROC_STAT} itself — {@code '6'} ({@link ProcStat#REQ}) means in-flight, {@code '0'}/{@code '3'}
 * terminal — so selecting only {@code '9'} already excludes anything being processed or finished.
 * The {@code PROC_DTTM} age window then excludes a row the post-commit path is handling right now.
 * On insert every executor stamps {@code PROC_DTTM = REG_DTTM}, so for a never-dispatched file the
 * age window measures time since registration.
 *
 * <h2>Alert de-duplication (no existing history table was suitable)</h2>
 * There is no persistent alert-history to key on: {@code OpsAlertService} writes none, and
 * {@code MSG_SND_HIS} belongs to the unrelated BLTB notification module (repurposing a legacy table
 * for scheduler bookkeeping is not allowed, and a new refactor-only table needs PO approval —
 * precedent {@code project_oat_idempotency_dropped}). So de-duplication is <b>in-memory</b>, which
 * is an established pattern in this codebase ({@code TokenRevocationService},
 * {@code RateLimiter} — both documented single-instance stores):
 * <ul>
 *   <li>One <b>aggregated</b> alert per sweep, never one per file — inherently flood-resistant.</li>
 *   <li>A <b>fingerprint</b> of the stuck set (sorted file keys + their causes). An alert is sent
 *       only when the fingerprint <i>changes</i>, or when {@code re-alert-minutes} has elapsed since
 *       the last alert (heartbeat for a persistent condition). Default 60 min.</li>
 *   <li>The fingerprint resets when nothing is stuck, so a recurrence alerts immediately.</li>
 *   <li>{@code ShedLock} already serialises the job to one node per run, so the practical
 *       multi-instance exposure is limited to duplicate alerts across a restart / failover.
 *       Bounded and acceptable for an observability signal; noted in the readiness doc.</li>
 * </ul>
 *
 * <h2>Metrics</h2>
 * The project has <b>no Micrometer / actuator dependency</b> (readiness blocker C-5), so there is no
 * meter registry to publish to. Counters are emitted in the structured summary log line instead, and
 * {@link StuckPayFileReport} returns them so a future actuator/health wiring needs no rework here.
 */
@Slf4j
@Service
public class StuckPayFileMonitorService {

    private static final DateTimeFormatter DTTM14 = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    // The yyyyMMdd formatter that used to build the firm-file sweep's TRAN_DT scope is gone with FIX H
    // — the sweep is now bounded on PROC_DTTM (14-char) at both ends and never derives a date at all.
    private static final ZoneId ICT = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final String LOG_TAG = "feature=stuck-pay-file-monitor";

    private final TbPayFileMstRepository payFileMstRepository;
    private final TbPayFileDataRepository payFileDataRepository;
    private final TbFirmConfRepository firmConfRepository;
    private final OperatingHoursService operatingHoursService;
    private final OpsAlertService opsAlertService;
    private final Clock clock;
    /** Second sweep: files stranded between {@code 00301} acceptance and a confirmed {@code 00302}. */
    private final com.kosign.wecms.domain.payment.dispatch.repository.TbFirmFileMstRepository firmFileMstRepository;
    /** Attempt history, to tell "retry pending" from "retry exhausted" without a new DB column. */
    private final com.kosign.wecms.domain.payment.dispatch.repository.TbFirmBatApiTranRepository firmBatApiTranRepository;
    /** Fourth sweep: real-time (202) pay-targets stranded at {@code PROC_STAT='9'}. */
    private final com.kosign.wecms.domain.payment.dispatch.repository.TbPayTargetRepository payTargetRepository;
    /** Fourth sweep: distinguishes the permanent {@code REALTIME_DISPATCHED_NOT_CLAIMED} strand. */
    private final com.kosign.wecms.domain.payment.dispatch.repository.TbFirmTranRepository firmTranRepository;

    /** Master gate — OFF by default. The sweep is inert until flipped on. */
    @Value("${wecms.dispatch.stuck-monitor.enabled:false}")
    private boolean enabled = false;

    /** A WAIT row older than this many minutes is reported. Default 15. */
    @Value("${wecms.dispatch.stuck-monitor.threshold-minutes:15}")
    private long thresholdMinutes = 15;

    /** Re-alert heartbeat for an unchanged stuck set. Default 60 min. 0 = alert every sweep. */
    @Value("${wecms.dispatch.stuck-monitor.re-alert-minutes:60}")
    private long reAlertMinutes = 60;

    /** Cap on file keys listed in the alert body / per-row warn logs. Default 20. */
    @Value("${wecms.dispatch.stuck-monitor.max-alert-keys:20}")
    private int maxAlertKeys = 20;

    /**
     * Read-only view of the dispatch flag, used purely for cause classification
     * ({@link StuckPayFileCause#DISPATCH_FLAG_DISABLED}). This monitor never changes it.
     */
    @Value("${wecms.dispatch.auto-receive:false}")
    private boolean autoReceiveEnabled = false;

    /** First time each still-stuck file key was observed. Pruned to the current stuck set each sweep. */
    private final Map<String, Instant> firstSeen = new ConcurrentHashMap<>();

    private volatile String lastFingerprint;
    private volatile Instant lastAlertAt;

    /** Cap on the second sweep's selection, so monitoring cannot become an unbounded scan. */
    @Value("${wecms.dispatch.stuck-monitor.firm-file-batch-size:200}")
    private int firmFileBatchSize = 200;

    /**
     * H-2 · Cap on the <b>first</b> (pay-file) sweep, for the same reason as
     * {@link #firmFileBatchSize}. This sweep previously used the unbounded finder, so the second
     * sweep was capped while the first was not.
     */
    @Value("${wecms.dispatch.stuck-monitor.pay-file-batch-size:200}")
    private int payFileBatchSize = 200;

    /** Cap on the FOURTH ({@code TB_PAY_TARGET}) sweep. Same rationale as the three above. */
    @Value("${wecms.dispatch.stuck-monitor.pay-target-batch-size:200}")
    private int payTargetBatchSize = 200;

    /**
     * FOURTH sweep · its <b>own</b> grace window, in minutes. Default 5.
     *
     * <p>Deliberately <b>not</b> {@link #thresholdMinutes}. The two rails wait for different things, so
     * one number cannot serve both:
     * <ul>
     *   <li>A <b>bulk pay-file</b> at WAIT is frequently waiting on an operating-hours window that opens
     *       later in the day. 15 minutes is already aggressive there, and shortening it would report
     *       files that are behaving correctly.</li>
     *   <li>A <b>real-time pay-target</b> at WAIT is waiting on nothing. {@code RealtimeDispatchRegistrar}
     *       hands the row to the 202 in an {@code afterCommit} hook — milliseconds after the approval
     *       transaction commits, on the same thread. There is no queue, no schedule and no batch window
     *       between the two. So a row still at {@code '9'} five minutes later did not "not start yet":
     *       the hand-off ran and returned early (flag off, operating-hours SKIP, 204 failure) or threw
     *       (missing {@code TB_FIRM_CONF}, unprovisioned API key).</li>
     * </ul>
     *
     * <p>Five minutes rather than one: the {@code afterCommit} hook loops every {@code TRAN_SEQ} the
     * payment committed and each 202 can take up to the EFS read timeout, so a large multi-line payment
     * legitimately has late lines in flight for minutes. One minute would report rows the hook is still
     * working through. Five clears that with margin while still being an order of magnitude tighter than
     * the bulk window — which is the point of giving this sweep its own knob.
     *
     * <p>Coerced to at least 1 so a mis-set {@code 0} cannot turn the sweep into "report everything,
     * including rows committed one second ago".
     */
    @Value("${wecms.dispatch.stuck-monitor.pay-target-threshold-minutes:5}")
    private long payTargetThresholdMinutes = 5;

    /**
     * Read-only view of the real-time dispatch flag, for cause classification
     * ({@link StuckPayFileCause#REALTIME_DISPATCH_FLAG_DISABLED}) — the real-time twin of
     * {@link #autoReceiveEnabled}. This monitor never changes it.
     */
    @Value("${wecms.dispatch.realtime-dispatch:false}")
    private boolean realtimeDispatchEnabled = false;

    /**
     * FIX H · How far back the firm-file sweep looks, in days, measured on {@code PROC_DTTM}.
     *
     * <p>Replaces the old {@code TRAN_DT = today} scope, which made a file stranded at 23:59 invisible
     * from 00:00 onward. This is the retention floor that keeps the now-dateless predicate from opening
     * a window over all history — see
     * {@code TbFirmFileMstRepository.findStrandedFirmFiles}.
     *
     * <p>Seven days so a Friday-night strand is still reported on Monday. Raising it widens the window
     * and the scan; a value below {@code 1} is coerced to {@code 1} rather than disabling the floor,
     * because a floor of {@code 0} would silently reintroduce the same-day cliff this fix removed.
     */
    @Value("${wecms.dispatch.stuck-monitor.retention-days:7}")
    private int retentionDays = 7;

    public StuckPayFileMonitorService(TbPayFileMstRepository payFileMstRepository,
                                      TbPayFileDataRepository payFileDataRepository,
                                      TbFirmConfRepository firmConfRepository,
                                      OperatingHoursService operatingHoursService,
                                      OpsAlertService opsAlertService,
                                      Clock clock,
                                      com.kosign.wecms.domain.payment.dispatch.repository.TbFirmFileMstRepository firmFileMstRepository,
                                      com.kosign.wecms.domain.payment.dispatch.repository.TbFirmBatApiTranRepository firmBatApiTranRepository,
                                      com.kosign.wecms.domain.payment.dispatch.repository.TbPayTargetRepository payTargetRepository,
                                      com.kosign.wecms.domain.payment.dispatch.repository.TbFirmTranRepository firmTranRepository) {
        this.payFileMstRepository = payFileMstRepository;
        this.payFileDataRepository = payFileDataRepository;
        this.firmConfRepository = firmConfRepository;
        this.operatingHoursService = operatingHoursService;
        this.opsAlertService = opsAlertService;
        this.clock = clock;
        this.firmFileMstRepository = firmFileMstRepository;
        this.firmBatApiTranRepository = firmBatApiTranRepository;
        this.payTargetRepository = payTargetRepository;
        this.firmTranRepository = firmTranRepository;
    }

    /**
     * One monitoring pass. Read-only. Never throws — a monitor must not be able to break the
     * scheduler thread it shares with the dispatch jobs
     * ({@code spring.task.scheduling.pool.size: 1}).
     *
     * @return the sweep's counts, for tests and future consumers
     */
    @Transactional(readOnly = true)
    public StuckPayFileReport monitorOnce() {
        if (!enabled) {
            log.debug("{} disabled (wecms.dispatch.stuck-monitor.enabled=false) — no-op", LOG_TAG);
            return StuckPayFileReport.empty();
        }
        try {
            return sweep();
        } catch (Exception e) {
            log.warn("{} sweep failed — swallowed so the scheduler thread survives", LOG_TAG, e);
            return StuckPayFileReport.empty();
        }
    }

    private StuckPayFileReport sweep() {
        Instant now = clock.instant();
        String cutoff = LocalDateTime.now(clock.withZone(ICT))
                .minusMinutes(thresholdMinutes)
                .format(DTTM14);

        // H-2: bounded + oldest-first (was findByProcStatAndProcDttmLessThan, which had no LIMIT and
        // no ORDER BY). A monitor that materialises every historical '9' row on every tick is the same
        // unbounded-scan problem as the result-check sweep, just on a read-only path — and this job is
        // the one that stays enabled longest, so it is the most likely to meet a large backlog.
        List<TbPayFileMst> stuck = payFileMstRepository.findStrandedPayFiles(
                ProcStat.WAIT.code(), cutoff, PageRequest.of(0, Math.max(1, payFileBatchSize)));

        // Second sweep — files stranded AFTER dispatch, between 00301 acceptance and a confirmed
        // 00302. Read-only, same threshold, same alert channel. Runs even when the retry sweep is
        // disabled (its default), which is precisely when this state would otherwise be invisible.
        List<TbFirmFileMst> firmStuck = findStrandedFirmFiles(cutoff);

        // THIRD sweep (H-3 / N-3, 4.3) — pay-files stranded at PROC_STAT='6': CLAIMED but never
        // finished. The H-4 claim commits '9'→'6' in its own transaction before any file work, so any
        // failure in between leaves a committed '6' that the WAIT sweep above cannot see and that no
        // recovery path selected. The fail-closed guards in BulkReceptionService now convert the known
        // failure modes to '3' at source; this sweep is the backstop for the rest (a hard kill, or a DB
        // failure during the terminal write itself) and the audit trail that the backstop is not firing.
        //
        // DETECTION ONLY — deliberately no re-drive. Two of the three states this can report are
        // ambiguous about whether the file already reached SFTP or EFS, and re-issuing a 00301 the bank
        // accepted would double-instruct. Per the 4.3 constraint, ambiguous post-egress states go to
        // monitoring and manual investigation, never to an automatic retry.
        List<TbPayFileMst> claimedStuck = findStrandedClaimedPayFiles(cutoff);

        // FOURTH sweep — the real-time (202) rail: TB_PAY_TARGET rows still at PROC_STAT='9'. Its own
        // grace window (see payTargetThresholdMinutes), because the after-commit hand-off fires
        // immediately and has nothing to wait for, unlike a bulk file's operating-hours window.
        //
        // DETECTION ONLY, like the three above. StrandedRecoveryService re-drives this exact population
        // through the atomic '9'→'6' claim — but it is coexistence-gated OFF and must stay off while the
        // legacy batch may share the DB, which is precisely when this population is invisible. Read-only
        // monitoring carries no such hazard (see the class Javadoc), so it can run while the mutating
        // sweep cannot.
        String payTargetCutoff = LocalDateTime.now(clock.withZone(ICT))
                .minusMinutes(Math.max(1, payTargetThresholdMinutes))
                .format(DTTM14);
        List<TbPayTarget> payTargetStuck = findStrandedPayTargets(payTargetCutoff);

        // Prune tracking down to what is still stuck — a file that progressed must not keep
        // an entry (bounds the map, and makes a recurrence read as "newly stuck").
        Set<String> currentKeys = stuck.stream().map(StuckPayFileMonitorService::key)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        firmStuck.stream().map(StuckPayFileMonitorService::firmKey).forEach(currentKeys::add);
        claimedStuck.stream().map(StuckPayFileMonitorService::claimedKey).forEach(currentKeys::add);
        payTargetStuck.stream().map(StuckPayFileMonitorService::payTargetKey).forEach(currentKeys::add);
        firstSeen.keySet().retainAll(currentKeys);

        if (stuck.isEmpty() && firmStuck.isEmpty() && claimedStuck.isEmpty() && payTargetStuck.isEmpty()) {
            // Reset the fingerprint so the next occurrence alerts immediately rather than
            // being suppressed as "unchanged".
            lastFingerprint = null;
            log.debug("{} clean — no WAIT pay-file, no claimed-stranded pay-file, no stranded firm-file "
                            + "older than {}m (cutoff={}) and no WAIT pay-target older than {}m "
                            + "(cutoff={})",
                    LOG_TAG, thresholdMinutes, cutoff, payTargetThresholdMinutes, payTargetCutoff);
            return StuckPayFileReport.empty();
        }

        Map<StuckPayFileCause, Integer> byCause = new EnumMap<>(StuckPayFileCause.class);
        List<String> detailLines = new ArrayList<>();
        int logged = 0;

        for (TbPayFileMst pay : stuck) {
            String k = key(pay);
            firstSeen.putIfAbsent(k, now);
            StuckPayFileCause cause = classify(pay);
            byCause.merge(cause, 1, Integer::sum);

            long stuckMinutes = minutesSince(pay.getProcDttm(), now);
            if (logged < maxAlertKeys) {
                logged++;
                // Structured warning log (requirement 6). One line per row, capped.
                log.warn("{} stuck pay-file biz_key={} tran_dt={} file_seq_no={} out_bank_cd={} "
                                + "fund_tp={} req_cnt={} proc_dttm={} stuck_minutes={} cause={}",
                        LOG_TAG, pay.getBizKey(), pay.getTranDt(), pay.getFileSeqNo(),
                        pay.getOutBankCd(), pay.getFundTp(), pay.getReqCnt(),
                        pay.getProcDttm(), stuckMinutes, cause);
                detailLines.add(k + " cause=" + cause + " stuck_minutes=" + stuckMinutes);
            }
        }
        if (stuck.size() > logged) {
            log.warn("{} … {} further stuck pay-file(s) not itemised (max-alert-keys={})",
                    LOG_TAG, stuck.size() - logged, maxAlertKeys);
        }

        // Second sweep's rows share the cause map, the detail lines and the alert.
        for (TbFirmFileMst f : firmStuck) {
            String k = firmKey(f);
            firstSeen.putIfAbsent(k, now);
            StuckPayFileCause cause = classifyFirmFile(f);
            byCause.merge(cause, 1, Integer::sum);

            long stuckMinutes = minutesSince(f.getProcDttm(), now);
            if (logged < maxAlertKeys) {
                logged++;
                log.warn("{} stranded firm-file biz_key={} tran_dt={} file_seq_no={} firm_file_nm={} "
                                + "out_bank_cd={} req_cnt={} proc_step={} proc_stat={} proc_dttm={} "
                                + "stuck_minutes={} cause={}",
                        LOG_TAG, f.getBizKey(), f.getTranDt(), f.getFileSeqNo(), f.getFirmFileNm(),
                        f.getOutBankCd(), f.getReqCnt(), f.getProcStep(), f.getProcStat(),
                        f.getProcDttm(), stuckMinutes, cause);
                detailLines.add(k + " cause=" + cause + " stuck_minutes=" + stuckMinutes);
            }
        }

        // Third sweep's rows share the cause map, the detail lines and the alert (H-3 / N-3).
        for (TbPayFileMst pay : claimedStuck) {
            String k = claimedKey(pay);
            firstSeen.putIfAbsent(k, now);
            StuckPayFileCause cause = classifyClaimedPayFile(pay);
            byCause.merge(cause, 1, Integer::sum);

            long stuckMinutes = minutesSince(pay.getProcDttm(), now);
            if (logged < maxAlertKeys) {
                logged++;
                log.warn("{} claimed-stranded pay-file biz_key={} tran_dt={} file_seq_no={} "
                                + "out_bank_cd={} req_cnt={} proc_stat={} proc_dttm={} rs_msg={} "
                                + "stuck_minutes={} cause={}",
                        LOG_TAG, pay.getBizKey(), pay.getTranDt(), pay.getFileSeqNo(),
                        pay.getOutBankCd(), pay.getReqCnt(), pay.getProcStat(), pay.getProcDttm(),
                        pay.getRsMsg(), stuckMinutes, cause);
                detailLines.add(k + " cause=" + cause + " stuck_minutes=" + stuckMinutes);
            }
        }

        // Fourth sweep's rows share the cause map, the detail lines and the alert.
        for (TbPayTarget pay : payTargetStuck) {
            String k = payTargetKey(pay);
            firstSeen.putIfAbsent(k, now);
            StuckPayFileCause cause = classifyPayTarget(pay);
            byCause.merge(cause, 1, Integer::sum);

            long stuckMinutes = minutesSince(pay.getProcDttm(), now);
            if (logged < maxAlertKeys) {
                logged++;
                log.warn("{} stranded pay-target biz_key={} tran_dt={} tran_seq={} out_bank_cd={} "
                                + "ant_div_cd={} wdrl_act_no={} proc_stat={} proc_dttm={} "
                                + "stuck_minutes={} cause={}",
                        LOG_TAG, pay.getBizKey(), pay.getTranDt(), pay.getTranSeq(),
                        pay.getOutBankCd(), pay.getAntDivCd(), pay.getWdrlActNo(),
                        pay.getProcStat(), pay.getProcDttm(), stuckMinutes, cause);
                detailLines.add(k + " cause=" + cause + " stuck_minutes=" + stuckMinutes);
            }
        }

        // Cap visibility (Phase 5): a truncated sweep must never read as a complete picture. Unlike the
        // result check (N-1) truncation here only under-reports — the age cutoff is in the same SQL as
        // the limit, so nothing is selected and then discarded.
        if (payTargetStuck.size() >= Math.max(1, payTargetBatchSize)) {
            log.warn("{} pay-target sweep cap reached (limit {}) — counts are a lower bound; raise "
                            + "wecms.dispatch.stuck-monitor.pay-target-batch-size or shorten the interval",
                    LOG_TAG, payTargetBatchSize);
        }
        if (stuck.size() >= Math.max(1, payFileBatchSize)
                || claimedStuck.size() >= Math.max(1, payFileBatchSize)) {
            log.warn("{} pay-file sweep cap reached (limit {}) — counts are a lower bound; raise "
                            + "wecms.dispatch.stuck-monitor.pay-file-batch-size or shorten the interval",
                    LOG_TAG, payFileBatchSize);
        }
        if (firmStuck.size() >= Math.max(1, firmFileBatchSize)) {
            log.warn("{} firm-file sweep cap reached (limit {}) — counts are a lower bound",
                    LOG_TAG, firmFileBatchSize);
        }

        // Summary counters — the metrics substitute while actuator is absent (blocker C-5).
        log.warn("{} SUMMARY stuck_total={} claimed_stuck_total={} firm_file_stuck_total={} "
                        + "pay_target_stuck_total={} threshold_minutes={} cutoff={} "
                        + "pay_target_threshold_minutes={} pay_target_cutoff={} by_cause={}",
                LOG_TAG, stuck.size(), claimedStuck.size(), firmStuck.size(), payTargetStuck.size(),
                thresholdMinutes, cutoff, payTargetThresholdMinutes, payTargetCutoff, byCause);

        boolean alerted = maybeAlert(stuck, firmStuck, payTargetStuck, byCause, detailLines, now);
        return new StuckPayFileReport(stuck.size(), byCause, alerted, firmStuck.size(),
                claimedStuck.size(), payTargetStuck.size());
    }

    /**
     * Select {@code TB_PAY_TARGET} rows stranded at {@code PROC_STAT='9'} past the pay-target grace
     * window — the real-time (202) rail's WAIT population.
     *
     * <p>Reuses {@code TbPayTargetRepository.findStrandedPayTargets}, the same bounded, oldest-first,
     * age-cutoff-in-SQL finder {@code StrandedRecoveryService.recoverPayTargets} drives. Sharing the
     * finder is deliberate: the monitor must report exactly the population the recovery sweep would act
     * on, or the two disagree about what is stranded.
     *
     * <p>Not tenant-scoped, matching that finder and legacy {@code TB_PAY_TARGET_R001} — see the
     * repository method's Javadoc. Safe here in a way it is not there, because this sweep never writes.
     */
    private List<TbPayTarget> findStrandedPayTargets(String cutoff) {
        return payTargetRepository.findStrandedPayTargets(
                ProcStat.WAIT.code(), cutoff, PageRequest.of(0, Math.max(1, payTargetBatchSize)));
    }

    /**
     * Classify a real-time pay-target stranded at {@code PROC_STAT='9'} by <b>which early return or
     * throw in {@code RealtimeDispatchService.dispatch} left it there</b> — evaluated in the order that
     * method reaches them, most-explanatory first.
     *
     * <p>Derived entirely from existing rows and config (workspace rule §1 — no new column, no new
     * table). All reads; {@code evaluateRealtime} performs two PK lookups and no writes.
     */
    private StuckPayFileCause classifyPayTarget(TbPayTarget pay) {
        // The flag being off explains every stranded row on its own — report it first, exactly as the
        // pay-file classifier does with auto-receive.
        if (!realtimeDispatchEnabled) {
            return StuckPayFileCause.REALTIME_DISPATCH_FLAG_DISABLED;
        }

        // Step 2 of dispatch(): a firm-tran row on the PAY-TARGET's TRAN_SEQ short-circuits as "already
        // dispatched", so with the row still at WAIT that guard is permanent — every re-drive, including
        // the recovery sweep's, is a no-op. Checked before the config and window tests because it
        // outranks them: even a correctly-configured row inside its window cannot progress from here.
        //
        // Our own writes cannot produce this (the claim and the 202 insert are atomic, and the 204's
        // sequence is independent), so a hit means a legacy-JEX-written or hand-edited row — see
        // REALTIME_DISPATCHED_NOT_CLAIMED.
        if (firmTranRepository.existsByTranDtAndBizKeyAndTranSeq(
                pay.getTranDt(), pay.getBizKey(), pay.getTranSeq())) {
            log.warn("{} stranded pay-target has a TB_FIRM_TRAN row on its OWN TRAN_SEQ — dispatch "
                            + "step 2 will skip it as 'already dispatched' on EVERY re-drive, so it "
                            + "cannot progress without manual intervention. biz_key={} tran_dt={} "
                            + "tran_seq={} — this shape is not producible by the refactor (claim + 202 "
                            + "insert are atomic), so it is a legacy-written or hand-edited row. Read "
                            + "that row's TRAN_TP/RS_CD/PROC_STAT: a '202' row means a transfer "
                            + "instruction exists and may have moved money. Reconcile with the VAN "
                            + "Center and correct forward; do NOT reset the target to WAIT",
                    LOG_TAG, pay.getBizKey(), pay.getTranDt(), pay.getTranSeq());
            return StuckPayFileCause.REALTIME_DISPATCHED_NOT_CLAIMED;
        }

        // Step 3: dispatch() throws IllegalStateException when this row is missing, which the
        // after-commit registrar swallows — so the row stays at '9' and will on every retry.
        boolean confMissing = firmConfRepository
                .findById(new TbFirmConfId(pay.getBizKey(), pay.getOutBankCd(), pay.getWdrlActNo()))
                .isEmpty();
        if (confMissing) {
            return StuckPayFileCause.REALTIME_NO_FIRM_CONF;
        }

        // Step 3b: SKIP is the only operating-hours outcome that LEAVES a row at '9' — CUTOFF and
        // NO_CRITERIA both write PROC_STAT='3'. So a '9' row that evaluates SKIP now is the window case;
        // any other verdict means the window has since opened and nothing re-drove the row, which is the
        // same actionable state (see the cause's Javadoc on why it is not self-healing).
        if (operatingHoursService.evaluateRealtime(pay.getOutBankCd(), pay.getAntDivCd())
                == OperatingHoursDecision.SKIP) {
            return StuckPayFileCause.REALTIME_OPERATING_HOURS_SKIP;
        }

        return StuckPayFileCause.UNKNOWN;
    }

    /** Tracking key for the pay-target sweep, namespaced so it cannot collide with a pay-file key. */
    private static String payTargetKey(TbPayTarget pay) {
        return "TARGET|" + pay.getBizKey() + "|" + pay.getTranDt() + "|" + pay.getTranSeq();
    }

    /**
     * Select {@code TB_PAY_FILE_MST} rows stranded at {@code PROC_STAT='6'} past the threshold — the
     * H-3 / N-3 population (claimed, never finished).
     *
     * <p>Same bounded finder, ordering and cap as the WAIT sweep; only {@code PROC_STAT} differs. No
     * {@code TRAN_DT} scope, unlike the firm-file sweep: a stranded claim is not self-healing, so an
     * older one must stay visible rather than ageing out of the report.
     */
    private List<TbPayFileMst> findStrandedClaimedPayFiles(String cutoff) {
        return payFileMstRepository.findStrandedPayFiles(
                ProcStat.REQ.code(), cutoff, PageRequest.of(0, Math.max(1, payFileBatchSize)));
    }

    /**
     * Classify a pay-file stranded at {@code PROC_STAT='6'} by <b>how far the dispatch got</b> — which
     * is what determines whether anything left the building, and therefore what an operator may safely
     * do (4.3: distinguish the states as far as existing data permits).
     *
     * <p>Derived entirely from existing rows — no new column, no new table (workspace rule §1):
     * <ol>
     *   <li>no {@code TB_FIRM_FILE_MST} row → {@link StuckPayFileCause#CLAIMED_NO_FIRM_FILE};</li>
     *   <li>firm-file row(s) all still at {@code PROC_STEP='0'} → the reception leg did not complete
     *       → {@link StuckPayFileCause#RECEPTION_UNCONFIRMED};</li>
     *   <li>any firm-file row advanced past {@code PROC_STEP='0'} → it is inside the callback pipeline
     *       → {@link StuckPayFileCause#AWAITING_EXECUTION_RESULT}.</li>
     * </ol>
     *
     * <p>Case 2 is refined by the {@code 00301} audit row: its presence means a request was actually
     * issued (so the file may be at the bank), its absence means the failure was earlier. Both map to
     * the same cause because both are ambiguous enough to require manual reconciliation — the audit row
     * is logged alongside so the operator can tell them apart without a second cause constant.
     */
    private StuckPayFileCause classifyClaimedPayFile(TbPayFileMst pay) {
        List<TbFirmFileMst> firmFiles = firmFileMstRepository
                .findByTranDtAndBizKeyAndFileSeqNo(pay.getTranDt(), pay.getBizKey(), pay.getFileSeqNo());
        if (firmFiles.isEmpty()) {
            return StuckPayFileCause.CLAIMED_NO_FIRM_FILE;
        }
        // A sibling stranded at PROC_STEP='2'/PROC_STAT='6' is NOT "awaiting a result in the normal
        // course" — the 00302 was accepted and the 01302 never came back. Reporting it as
        // AWAITING_EXECUTION_RESULT is actively misleading: that cause is documented "often not a
        // defect … simply has not been finalised yet", and it defers the actionable per-file detail to
        // the firm-file sweep. Check it BEFORE the generic advanced test.
        boolean anyExecutionResultMissing = firmFiles.stream().anyMatch(f ->
                ProcStep.DEPO_REQ.code().equals(f.getProcStep()) && ProcStat.REQ.code().equals(f.getProcStat()));
        if (anyExecutionResultMissing) {
            return StuckPayFileCause.BULK_EXECUTION_RESULT_NOT_RECEIVED;
        }
        // Genuinely mid-flight: a sibling past reception ('1'/'6') or already terminal, with nothing
        // stranded at '2'. This remains the benign "not finalised yet" case.
        boolean anyAdvanced = firmFiles.stream()
                .anyMatch(f -> !ProcStep.REQ.code().equals(f.getProcStep()));
        if (anyAdvanced) {
            return StuckPayFileCause.AWAITING_EXECUTION_RESULT;
        }
        // Still at PROC_STEP='0'. Log whether a 00301 was issued so the operator can distinguish
        // "never sent" from "sent, unconfirmed" — the audit ledger is the reconciliation starting point.
        boolean receptionRequested = firmFiles.stream().anyMatch(f ->
                firmBatApiTranRepository.existsByTranDtAndBizKeyAndFirmFileNmAndApiTranTp(
                        f.getTranDt(), f.getBizKey(), f.getFirmFileNm(), ApiTranTp.BULK_RECEPTION.code()));
        log.warn("{} claimed-stranded pay-file at PROC_STEP=0 biz_key={} file_seq_no={} "
                        + "reception_audit_row_present={} — reconcile with the VAN Center before any "
                        + "manual action (the file may already be on the bank's SFTP server)",
                LOG_TAG, pay.getBizKey(), pay.getFileSeqNo(), receptionRequested);
        return StuckPayFileCause.RECEPTION_UNCONFIRMED;
    }

    /** Tracking key for the claimed-{@code '6'} sweep, namespaced so it cannot collide with the WAIT key. */
    private static String claimedKey(TbPayFileMst pay) {
        return "claimed:" + pay.getBizKey() + "/" + pay.getTranDt() + "/" + pay.getFileSeqNo();
    }

    /**
     * Select {@code TB_FIRM_FILE_MST} rows stranded at {@code PROC_STEP='1' / PROC_STAT='6'}.
     *
     * <h2>FIX H — no longer scoped to today</h2>
     * This used to pass {@code TRAN_DT = now(ICT)}, so a file stranded at 23:59 vanished from the sweep
     * at 00:00 and was never reported again — in the one state that has no automatic recovery. Legacy
     * scoped to today too ({@code FILE_DEPOSIT_TRANSFER_BATCH2.java:49}), but legacy re-sent {@code 00302}
     * every 60 seconds, so its files self-healed regardless of any sweep's window; that re-drive was
     * removed as K9 Option A. Keeping the scope without the mechanism that made it harmless is what
     * created the blind spot, so the window is now a {@code PROC_DTTM} range instead: older than the
     * stuck threshold, newer than {@link #retentionDays}. Rationale + the index implication are on
     * {@code TbFirmFileMstRepository.findStrandedFirmFiles}.
     *
     * @param cutoff the 14-char {@code yyyyMMddHHmmss} age threshold — rows at or after it are too
     *               recent to be considered stranded
     */
    private List<TbFirmFileMst> findStrandedFirmFiles(String cutoff) {
        String floor = LocalDateTime.now(clock.withZone(ICT))
                .minusDays(Math.max(1, retentionDays))
                .format(DTTM14);
        PageRequest page = PageRequest.of(0, Math.max(1, firmFileBatchSize));

        // Two calls, one per stranded step, rather than one IN (...) query. The result sets map 1:1 onto
        // the causes they produce, so classifyFirmFile never has to re-derive which state it is looking
        // at, and each keeps its own oldest-first bound. Cost is one extra bounded query per tick over a
        // table with 93 rows on JEX DEV.
        List<TbFirmFileMst> atReceptionReceived = firmFileMstRepository.findStrandedFirmFiles(
                ProcStep.REQ_RECV.code(), ProcStat.REQ.code(), cutoff, floor, page);

        // PROC_STEP='2' — 00302 accepted, 01302 result never received. Previously invisible to every
        // sweep: this finder filtered '1' only, and the parent pay-file reported the benign
        // AWAITING_EXECUTION_RESULT. Two PROD files sat here (20260407, 20260529 — 16 payment lines).
        List<TbFirmFileMst> atExecutionRequested = firmFileMstRepository.findStrandedFirmFiles(
                ProcStep.DEPO_REQ.code(), ProcStat.REQ.code(), cutoff, floor, page);

        // PROC_STEP='0' — reception never reconciled (2026-07-31). The third blind spot, and the one
        // two-phase dispatch turned from rare into routine: a phase-B break at file k of N leaves files
        // k+1..N committed here, unsent, while the parent pay-file goes '3' — so BOTH pay-file sweeps
        // skip them and this finder used to filter '1'/'2' only. Same state also holds every lost-01301
        // file, where the bank DOES have the batch (27 of the 30 rows at this state on JEX DEV carry a
        // 00301 audit row, verified read-only 2026-07-31).
        //
        // Same finder as the two above. It ages on COALESCE(NULLIF(PROC_DTTM,''), REG_DTTM) because
        // neither legacy nor phase A writes PROC_DTTM on insert, so a PROC_DTTM-only predicate would
        // select nothing for this state; unifying the rule across all three steps was the 2026-08-01
        // decision — see TbFirmFileMstRepository.findStrandedFirmFiles.
        List<TbFirmFileMst> atReceptionRequested = firmFileMstRepository.findStrandedFirmFiles(
                ProcStep.REQ.code(), ProcStat.REQ.code(), cutoff, floor, page);

        List<TbFirmFileMst> all = new ArrayList<>(atReceptionReceived.size()
                + atExecutionRequested.size() + atReceptionRequested.size());
        all.addAll(atReceptionReceived);
        all.addAll(atExecutionRequested);
        all.addAll(atReceptionRequested);
        return all;
    }

    /**
     * Distinguish "the {@code 00302} was never sent" from "it was sent and never confirmed".
     *
     * <p>These two states require <b>opposite</b> manual actions, which is the whole diagnostic value
     * of this sweep: the first is safe to re-drive, the second must be reconciled with the VAN Center
     * first because the bank may already have executed the batch.
     *
     * <p>Derived from the presence of a {@code 00302} audit row — no new column (workspace rule §1),
     * and no notion of an attempt <i>ceiling</i>: there is no automatic retry to exhaust.
     *
     * <h2>FIX G — the probe carries no date predicate, and must not</h2>
     * This used to probe {@code existsByTranDtAndBizKeyAndFirmFileNmAndApiTranTp(file.getTranDt(), …)}.
     * That is wrong for the same reason the {@code 00302} guard itself was wrong (FIX D), and the
     * consequence here is worse:
     * <ul>
     *   <li>{@code TB_FIRM_FILE_MST.TRAN_DT} is stamped at file-BUILD time (ICT);</li>
     *   <li>the {@code 00302} audit row is stamped at ISSUE time — the {@code 01301} callback clock
     *       ({@code BulkExecutionInlineTxService}).</li>
     * </ul>
     * For a file built at 23:58 whose callback landed at 00:02 those differ by a day, so the probe
     * missed a {@code 00302} row that exists and the file was classified
     * {@link StuckPayFileCause#BULK_EXECUTION_NOT_ISSUED}. That is not a cosmetic mislabel:
     * {@code docs/deployment/bulk-file-reconciliation-procedure.md} §5.1 tells the operator that
     * {@code NOT_ISSUED} plus a "not executed" answer means <b>re-drive is routine</b> — so a
     * misclassification here converts a safe stranded file into an <b>operator-mediated duplicate
     * 00302</b>, i.e. a possible double-executed bulk payment. Fail-safe direction for this classifier
     * is {@code UNCONFIRMED} ("reconcile before acting"), and dropping the date predicate is what makes
     * it reach that verdict whenever a row exists at all.
     *
     * <p>Contrast {@link #classifyClaimedPayFile}, which probes the <b>{@code 00301}</b> row and
     * legitimately still passes {@code f.getTranDt()}: {@code BulkReceptionService} writes that audit
     * row with the same {@code tranDt} it stamps on the file, so there writer and reader agree by
     * construction. The asymmetry is in the writers, not in the classifier.
     */
    private StuckPayFileCause classifyFirmFile(TbFirmFileMst file) {
        // PROC_STEP='2' is decided FIRST and is never overridden by the parent's state — see the
        // parent-check block below for why.
        // PROC_STEP='2' — the step itself is the evidence: only BulkExecutionInlineTxService writes it,
        // and only after the 00302 returned 0000/BT00. So there is nothing to infer from an audit row;
        // the instruction is confirmed-accepted and the missing piece is the 01302 RESULT.
        if (ProcStep.DEPO_REQ.code().equals(file.getProcStep())) {
            // Log whether the 01302 callback ever arrived — it splits "gateway lost it" from "it
            // arrived and our download/parse kept rolling back", and the second is our defect to fix.
            // Prefix-stripped match: legacy stores this audit row under the callback's R_ name
            // (API2_act.jsp:189) while we store the sent S_ name, so an exact-name probe would report
            // "no callback" for every legacy-written row regardless of the truth.
            boolean resultCallbackSeen = firmBatApiTranRepository
                    .existsForFileIgnoringNamePrefix(file.getBizKey(), file.getFirmFileNm(),
                            ApiTranTp.BULK_EXECUTION_RESULT.code());
            log.warn("{} firm file stranded at PROC_STEP=2/PROC_STAT=6 biz_key={} firm_file_nm={} "
                            + "req_cnt={} result_callback_audit_present={} — the 00302 was ACCEPTED, so the "
                            + "bank has most likely executed this batch. NEVER re-drive; confirm the "
                            + "per-line outcome with the VAN Center and reconcile forward",
                    LOG_TAG, file.getBizKey(), file.getFirmFileNm(), file.getReqCnt(), resultCallbackSeen);
            return StuckPayFileCause.BULK_EXECUTION_RESULT_NOT_RECEIVED;
        }

        // ── Parent-batch check, for PROC_STEP='0' and '1' only ────────────────────────────────────
        // A file whose parent TB_PAY_FILE_MST is already '3' belongs to an ABANDONED batch: phase B
        // marks the pay-file FAIL and `break`s on the first failure, so the siblings after that point
        // were never sent. Classifying such a file as *_NOT_ISSUED is actively dangerous — the runbook
        // says re-drive is routine for that cause, and re-driving one file of an abandoned batch
        // part-executes a payment the system already reported as failed. Checked BEFORE the audit-row
        // split so it wins over both NOT_ISSUED verdicts.
        //
        // Deliberately not applied to PROC_STEP='2' (handled and returned above): there the 00302 was
        // accepted, so BULK_EXECUTION_RESULT_NOT_RECEIVED carries the stronger warning and the parent's
        // state would only dilute it.
        boolean parentFailed = payFileMstRepository
                .findByTranDtAndBizKeyAndFileSeqNo(file.getTranDt(), file.getBizKey(), file.getFileSeqNo())
                .map(pay -> ProcStat.FAIL.code().equals(pay.getProcStat()))
                .orElse(false);
        if (parentFailed) {
            log.warn("{} firm file stranded in an ABANDONED batch biz_key={} tran_dt={} file_seq_no={} "
                            + "firm_file_nm={} proc_step={} — the parent TB_PAY_FILE_MST is PROC_STAT='3', "
                            + "so an earlier sibling failed and phase B stopped. Do NOT re-drive this "
                            + "file on its own: confirm with the VAN Center which siblings the bank "
                            + "received, then re-create or reconcile the batch as a unit",
                    LOG_TAG, file.getBizKey(), file.getTranDt(), file.getFileSeqNo(),
                    file.getFirmFileNm(), file.getProcStep());
            return StuckPayFileCause.BULK_BATCH_PARENT_FAILED;
        }

        // PROC_STEP='0' — reception never reconciled. The 00301 audit row splits the two dispositions:
        // present => the request went out and the bank has the file (reconcile forward, never re-drive);
        // absent  => the call was never reached, nothing was sent (safe to re-dispatch).
        //
        // The probe carries no date predicate, matching the 00302 probe above (FIX G). Verified on JEX
        // DEV 2026-07-31 that both variants agree on this leg (27 of 30 either way) — BulkReceptionService
        // writes this audit row with the same tranDt it stamps on the file — but dropping the predicate
        // costs nothing and keeps the fail-safe direction (a row that exists is always found).
        if (ProcStep.REQ.code().equals(file.getProcStep())) {
            boolean receptionIssued = firmBatApiTranRepository.existsByBizKeyAndFirmFileNmAndApiTranTp(
                    file.getBizKey(), file.getFirmFileNm(), ApiTranTp.BULK_RECEPTION.code());
            if (receptionIssued) {
                log.warn("{} firm file stranded at PROC_STEP=0/PROC_STAT=6 WITH a 00301 audit row "
                                + "biz_key={} firm_file_nm={} req_cnt={} — the reception request WAS "
                                + "issued, so the bank has this file and the 01301 result was lost. "
                                + "NEVER re-drive; reconcile forward with the VAN Center",
                        LOG_TAG, file.getBizKey(), file.getFirmFileNm(), file.getReqCnt());
                return StuckPayFileCause.BULK_RECEPTION_RESULT_NOT_RECEIVED;
            }
            log.warn("{} firm file stranded at PROC_STEP=0/PROC_STAT=6 with NO 00301 audit row "
                            + "biz_key={} firm_file_nm={} req_cnt={} — never sent (staging/SFTP failure, "
                            + "or a phase-B break at an earlier sibling). Parent pay-file is not FAIL, so "
                            + "re-dispatch is safe once the cause is understood",
                    LOG_TAG, file.getBizKey(), file.getFirmFileNm(), file.getReqCnt());
            return StuckPayFileCause.BULK_RECEPTION_NOT_ISSUED;
        }

        boolean issued = firmBatApiTranRepository.existsByBizKeyAndFirmFileNmAndApiTranTp(
                file.getBizKey(), file.getFirmFileNm(), ApiTranTp.BULK_EXECUTION.code());
        return issued
                ? StuckPayFileCause.BULK_EXECUTION_UNCONFIRMED
                : StuckPayFileCause.BULK_EXECUTION_NOT_ISSUED;
    }

    /**
     * Classify the likely cause. Read-only: the operating-hours re-check calls
     * {@code evaluateBulk}, which performs two PK lookups and no writes.
     */
    private StuckPayFileCause classify(TbPayFileMst pay) {
        // The flag being off explains every stuck row on its own — report it first.
        if (!autoReceiveEnabled) {
            return StuckPayFileCause.DISPATCH_FLAG_DISABLED;
        }
        List<TbPayFileData> details = payFileDataRepository
                .findByBizKeyAndTranDtAndFileSeqNo(pay.getBizKey(), pay.getTranDt(), pay.getFileSeqNo());
        if (details == null || details.isEmpty()) {
            return StuckPayFileCause.NO_DETAIL_ROWS;
        }

        boolean anyPopulated = false;
        boolean anyUnknown = false;
        Set<String> channels = new LinkedHashSet<>();
        for (TbPayFileData d : details) {
            String raw = d.getAntDivCd();
            if (raw == null || raw.isBlank()) {
                continue;                      // legacy NULL branch / ETC
            }
            anyPopulated = true;
            String channel = AntDivChannel.fromStorageCode(raw);
            if (channel == null) {
                anyUnknown = true;             // not one of E/V/C/N/S
            } else {
                channels.add(channel);
            }
        }

        if (!anyPopulated) {
            return StuckPayFileCause.ALL_CHANNELS_NULL;
        }
        if (anyUnknown) {
            // More actionable than the resulting SKIP, even when other rows resolve.
            return StuckPayFileCause.UNKNOWN_CHANNEL_CODE;
        }
        if (channels.isEmpty()) {
            return StuckPayFileCause.ALL_CHANNELS_NULL;
        }
        OperatingHoursDecision decision =
                operatingHoursService.evaluateBulk(pay.getOutBankCd(), channels);
        if (decision == OperatingHoursDecision.SKIP) {
            return StuckPayFileCause.OPERATING_HOURS_SKIP;
        }
        return StuckPayFileCause.UNKNOWN;
    }

    /**
     * Send at most one aggregated alert per sweep, suppressed while the stuck set is unchanged
     * and the re-alert heartbeat has not elapsed.
     *
     * @return whether an alert was dispatched
     */
    private boolean maybeAlert(List<TbPayFileMst> stuck,
                               List<TbFirmFileMst> firmStuck,
                               List<TbPayTarget> payTargetStuck,
                               Map<StuckPayFileCause, Integer> byCause,
                               List<String> detailLines,
                               Instant now) {
        int total = stuck.size() + firmStuck.size() + payTargetStuck.size();
        String fingerprint = fingerprint(stuck, firmStuck, payTargetStuck, byCause);
        boolean changed = !fingerprint.equals(lastFingerprint);
        boolean heartbeatDue = lastAlertAt == null
                || Duration.between(lastAlertAt, now).toMinutes() >= reAlertMinutes;

        if (!changed && !heartbeatDue) {
            log.debug("{} alert suppressed — unchanged stuck set, {}m since last alert (re-alert {}m)",
                    LOG_TAG, Duration.between(lastAlertAt, now).toMinutes(), reAlertMinutes);
            return false;
        }

        String vanOrgCd = resolveVanOrgCd(stuck);
        if (vanOrgCd == null) {
            // A stranded firm-file already carries its own VAN_ORG_CD — use it before giving up.
            vanOrgCd = firmStuck.stream()
                    .map(TbFirmFileMst::getVanOrgCd)
                    .filter(v -> v != null && !v.isBlank())
                    .map(String::trim)
                    .findFirst()
                    .orElse(null);
        }
        if (vanOrgCd == null) {
            // A stranded pay-target resolves its own TB_FIRM_CONF the same way a pay-file does — and on
            // a real-time-only strand it is the ONLY row that can, since the two pay-file lists are
            // empty in that case.
            vanOrgCd = resolveVanOrgCdFromPayTargets(payTargetStuck);
        }
        if (vanOrgCd == null) {
            // Do NOT mark as alerted — retry next sweep once a config row is resolvable.
            log.warn("{} alert skipped — no TB_FIRM_CONF row resolves a VAN_ORG_CD for any stuck file "
                    + "or pay-target", LOG_TAG);
            return false;
        }

        StringBuilder body = new StringBuilder()
                .append("대량이체 파일 지연 (bulk file stuck).")
                .append("\nWAIT_PAY_FILE_TOTAL : ").append(stuck.size())
                .append("\nSTRANDED_FIRM_FILE_TOTAL : ").append(firmStuck.size())
                .append("\nWAIT_PAY_TARGET_TOTAL : ").append(payTargetStuck.size())
                .append("\nTHRESHOLD_MIN : ").append(thresholdMinutes)
                .append("\nPAY_TARGET_THRESHOLD_MIN : ").append(payTargetThresholdMinutes)
                .append("\nBY_CAUSE : ").append(byCause);
        for (String line : detailLines) {
            body.append("\n- ").append(line);
        }
        if (total > detailLines.size()) {
            body.append("\n- … ").append(total - detailLines.size()).append(" more");
        }

        // Fire-and-forget; itself a no-op when wecms.dispatch.alert.enabled=false.
        opsAlertService.notify(vanOrgCd, body.toString());

        lastFingerprint = fingerprint;
        lastAlertAt = now;
        return true;
    }

    /**
     * Stable identity of the stuck set: sorted {@code key=cause} pairs. Any file entering or
     * leaving the set, or changing cause, produces a different fingerprint and re-alerts.
     */
    private String fingerprint(List<TbPayFileMst> stuck, List<TbFirmFileMst> firmStuck,
                               List<TbPayTarget> payTargetStuck,
                               Map<StuckPayFileCause, Integer> byCause) {
        return Stream.concat(
                        Stream.concat(
                                stuck.stream().map(StuckPayFileMonitorService::key),
                                firmStuck.stream().map(StuckPayFileMonitorService::firmKey)),
                        payTargetStuck.stream().map(StuckPayFileMonitorService::payTargetKey))
                .sorted(Comparator.naturalOrder())
                .collect(Collectors.joining(",")) + "|" + byCause;
    }

    /** First resolvable {@code TB_FIRM_CONF.VAN_ORG_CD} among the stuck files. */
    private String resolveVanOrgCd(List<TbPayFileMst> stuck) {
        for (TbPayFileMst pay : stuck) {
            Optional<TbFirmConf> conf = firmConfRepository.findById(
                    new TbFirmConfId(pay.getBizKey(), pay.getOutBankCd(), pay.getWdrlActNo()));
            String vanOrgCd = conf.map(TbFirmConf::getVanOrgCd).orElse(null);
            if (vanOrgCd != null && !vanOrgCd.isBlank()) {
                return vanOrgCd.trim();
            }
        }
        return null;
    }

    /**
     * First resolvable {@code TB_FIRM_CONF.VAN_ORG_CD} among the stranded pay-targets — the same
     * {@code (BIZ_KEY, OUT_BANK_CD, WDRL_ACT_NO)} key {@code RealtimeDispatchService} uses.
     *
     * <p>Returns null for a {@link StuckPayFileCause#REALTIME_NO_FIRM_CONF} strand, which is correct:
     * the missing config row is the reason there is no VAN to alert. If every stranded row has that
     * cause the alert is skipped and the WARN logs above are the signal — which is why the per-row
     * logging is not conditional on alerting.
     */
    private String resolveVanOrgCdFromPayTargets(List<TbPayTarget> payTargetStuck) {
        for (TbPayTarget pay : payTargetStuck) {
            String vanOrgCd = firmConfRepository
                    .findById(new TbFirmConfId(pay.getBizKey(), pay.getOutBankCd(), pay.getWdrlActNo()))
                    .map(TbFirmConf::getVanOrgCd)
                    .orElse(null);
            if (vanOrgCd != null && !vanOrgCd.isBlank()) {
                return vanOrgCd.trim();
            }
        }
        return null;
    }

    /** Minutes between a 14-char {@code yyyyMMddHHmmss} ICT stamp and now; 0 when unparseable. */
    private long minutesSince(String dttm14, Instant now) {
        if (dttm14 == null || dttm14.trim().length() != 14) {
            return 0L;
        }
        try {
            LocalDateTime then = LocalDateTime.parse(dttm14.trim(), DTTM14);
            return Duration.between(then.atZone(ICT).toInstant(), now).toMinutes();
        } catch (RuntimeException e) {
            return 0L;
        }
    }

    /** Pay-file identity — the {@code TB_PAY_FILE_MST} primary key. */
    private static String key(TbPayFileMst pay) {
        return pay.getBizKey() + "|" + pay.getTranDt() + "|" + pay.getFileSeqNo();
    }

    /**
     * Firm-file identity — the {@code TB_FIRM_FILE_MST} primary key, prefixed so it can never
     * collide with a pay-file key in the shared {@code firstSeen} map or the fingerprint.
     */
    private static String firmKey(TbFirmFileMst f) {
        return "FIRM|" + f.getBizKey() + "|" + f.getTranDt() + "|" + f.getFileSeqNo()
                + "|" + f.getFirmFileNm();
    }
}
