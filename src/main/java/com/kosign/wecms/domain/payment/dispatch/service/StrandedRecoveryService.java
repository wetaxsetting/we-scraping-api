package com.kosign.wecms.domain.payment.dispatch.service;

import com.kosign.wecms.domain.payment.dispatch.entity.TbPayFileMst;
import com.kosign.wecms.domain.payment.dispatch.entity.TbPayTarget;
import com.kosign.wecms.domain.payment.dispatch.enums.ProcStat;
import com.kosign.wecms.domain.payment.dispatch.repository.TbPayFileMstRepository;
import com.kosign.wecms.domain.payment.dispatch.repository.TbPayTargetRepository;
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
 *       whose WAIT gate + firm-file PK make a re-run idempotent (a duplicate build collides on the
 *       {@code TB_FIRM_FILE_MST} PK).</li>
 *   <li>Eligible firm transactions stuck at {@code PROC_STAT='6'} → delegated to
 *       {@link ResultCheckService#runOnce} (the existing aged-202 → 203 scan, now atomic per Item&nbsp;1).</li>
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

    /** Master gate — OFF by default. Even with a cron set, the sweep is inert until this is true. */
    @Value("${wecms.dispatch.recovery.enabled:false}")
    private boolean enabled;

    /** Grace window (minutes): only rows whose PROC_DTTM is older than {@code now - grace} are eligible. */
    @Value("${wecms.dispatch.recovery.grace-minutes:30}")
    private long graceMinutes;

    public StrandedRecoveryService(TbPayTargetRepository payTargetRepository,
                                   TbPayFileMstRepository payFileMstRepository,
                                   RealtimeDispatchService realtimeDispatchService,
                                   BulkReceptionService bulkReceptionService,
                                   ResultCheckService resultCheckService) {
        this.payTargetRepository = payTargetRepository;
        this.payFileMstRepository = payFileMstRepository;
        this.realtimeDispatchService = realtimeDispatchService;
        this.bulkReceptionService = bulkReceptionService;
        this.resultCheckService = resultCheckService;
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
        try {
            resultCheckService.runOnce();
        } catch (Exception e) {
            log.warn("stranded-recovery: result-check delegation failed — {}", e.getMessage());
        }
    }

    private void recoverPayTargets(String cutoff) {
        List<TbPayTarget> aged = payTargetRepository
                .findByProcStatAndProcDttmLessThan(ProcStat.WAIT.code(), cutoff);
        if (aged.isEmpty()) {
            return;
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
        List<TbPayFileMst> aged = payFileMstRepository
                .findByProcStatAndProcDttmLessThan(ProcStat.WAIT.code(), cutoff);
        if (aged.isEmpty()) {
            return;
        }
        log.info("stranded-recovery: {} aged bulk pay-file(s) at PROC_STAT='9'", aged.size());
        for (TbPayFileMst pay : aged) {
            try {
                // Re-drive through the normal reception path; the WAIT gate + firm-file PK de-dupe.
                bulkReceptionService.receive(pay.getTranDt(), pay.getBizKey(), pay.getFileSeqNo());
            } catch (Exception e) {
                log.warn("stranded-recovery: pay-file re-receive failed bizKey={} tranDt={} fileSeqNo={} — {}",
                        pay.getBizKey(), pay.getTranDt(), pay.getFileSeqNo(), e.getMessage());
            }
        }
    }
}
