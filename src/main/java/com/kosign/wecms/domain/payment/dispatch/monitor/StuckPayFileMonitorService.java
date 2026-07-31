package com.kosign.wecms.domain.payment.dispatch.monitor;

import com.kosign.wecms.domain.payment.dispatch.alert.OpsAlertService;
import com.kosign.wecms.domain.payment.dispatch.constant.AntDivChannel;
import com.kosign.wecms.domain.payment.dispatch.entity.TbFirmFileMst;
import com.kosign.wecms.domain.payment.dispatch.entity.TbPayFileData;
import com.kosign.wecms.domain.payment.dispatch.entity.TbPayFileMst;
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
 * Read-only operational monitor for bulk pay-files stranded at {@code PROC_STAT='9'} (WAIT).
 *
 * <h2>Why this exists</h2>
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

    /**
     * FIX H · How far back the firm-file sweep looks, in days, measured on {@code PROC_DTTM}.
     *
     * <p>Replaces the old {@code TRAN_DT = today} scope, which made a file stranded at 23:59 invisible
     * from 00:00 onward. This is the retention floor that keeps the now-dateless predicate from opening
     * a window over all history — see
     * {@code TbFirmFileMstRepository.findStrandedBulkExecutionFiles}.
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
                                      com.kosign.wecms.domain.payment.dispatch.repository.TbFirmBatApiTranRepository firmBatApiTranRepository) {
        this.payFileMstRepository = payFileMstRepository;
        this.payFileDataRepository = payFileDataRepository;
        this.firmConfRepository = firmConfRepository;
        this.operatingHoursService = operatingHoursService;
        this.opsAlertService = opsAlertService;
        this.clock = clock;
        this.firmFileMstRepository = firmFileMstRepository;
        this.firmBatApiTranRepository = firmBatApiTranRepository;
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

        // Prune tracking down to what is still stuck — a file that progressed must not keep
        // an entry (bounds the map, and makes a recurrence read as "newly stuck").
        Set<String> currentKeys = stuck.stream().map(StuckPayFileMonitorService::key)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        firmStuck.stream().map(StuckPayFileMonitorService::firmKey).forEach(currentKeys::add);
        claimedStuck.stream().map(StuckPayFileMonitorService::claimedKey).forEach(currentKeys::add);
        firstSeen.keySet().retainAll(currentKeys);

        if (stuck.isEmpty() && firmStuck.isEmpty() && claimedStuck.isEmpty()) {
            // Reset the fingerprint so the next occurrence alerts immediately rather than
            // being suppressed as "unchanged".
            lastFingerprint = null;
            log.debug("{} clean — no WAIT pay-file, no claimed-stranded pay-file and no stranded "
                    + "firm-file older than {}m (cutoff={})", LOG_TAG, thresholdMinutes, cutoff);
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

        // Cap visibility (Phase 5): a truncated sweep must never read as a complete picture. Unlike the
        // result check (N-1) truncation here only under-reports — the age cutoff is in the same SQL as
        // the limit, so nothing is selected and then discarded.
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
                        + "threshold_minutes={} cutoff={} by_cause={}",
                LOG_TAG, stuck.size(), claimedStuck.size(), firmStuck.size(),
                thresholdMinutes, cutoff, byCause);

        boolean alerted = maybeAlert(stuck, firmStuck, byCause, detailLines, now);
        return new StuckPayFileReport(stuck.size(), byCause, alerted, firmStuck.size(), claimedStuck.size());
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
     * {@code TbFirmFileMstRepository.findStrandedBulkExecutionFiles}.
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
        List<TbFirmFileMst> atReceptionReceived = firmFileMstRepository.findStrandedBulkExecutionFiles(
                ProcStep.REQ_RECV.code(), ProcStat.REQ.code(), cutoff, floor, page);

        // PROC_STEP='2' — 00302 accepted, 01302 result never received. Previously invisible to every
        // sweep: this finder filtered '1' only, and the parent pay-file reported the benign
        // AWAITING_EXECUTION_RESULT. Two PROD files sat here (20260407, 20260529 — 16 payment lines).
        List<TbFirmFileMst> atExecutionRequested = firmFileMstRepository.findStrandedBulkExecutionFiles(
                ProcStep.DEPO_REQ.code(), ProcStat.REQ.code(), cutoff, floor, page);

        List<TbFirmFileMst> all = new ArrayList<>(atReceptionReceived.size() + atExecutionRequested.size());
        all.addAll(atReceptionReceived);
        all.addAll(atExecutionRequested);
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
                               Map<StuckPayFileCause, Integer> byCause,
                               List<String> detailLines,
                               Instant now) {
        int total = stuck.size() + firmStuck.size();
        String fingerprint = fingerprint(stuck, firmStuck, byCause);
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
            // Do NOT mark as alerted — retry next sweep once a config row is resolvable.
            log.warn("{} alert skipped — no TB_FIRM_CONF row resolves a VAN_ORG_CD for any stuck file",
                    LOG_TAG);
            return false;
        }

        StringBuilder body = new StringBuilder()
                .append("대량이체 파일 지연 (bulk file stuck).")
                .append("\nWAIT_PAY_FILE_TOTAL : ").append(stuck.size())
                .append("\nSTRANDED_FIRM_FILE_TOTAL : ").append(firmStuck.size())
                .append("\nTHRESHOLD_MIN : ").append(thresholdMinutes)
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
                               Map<StuckPayFileCause, Integer> byCause) {
        return Stream.concat(
                        stuck.stream().map(StuckPayFileMonitorService::key),
                        firmStuck.stream().map(StuckPayFileMonitorService::firmKey))
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
