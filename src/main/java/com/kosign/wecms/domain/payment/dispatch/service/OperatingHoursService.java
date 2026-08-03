package com.kosign.wecms.domain.payment.dispatch.service;

import com.kosign.wecms.domain.payment.dispatch.constant.AntDivChannel;
import com.kosign.wecms.domain.payment.dispatch.constant.BulkTransferConstants;
import com.kosign.wecms.domain.payment.dispatch.entity.TbBatchScheduleDtl;
import com.kosign.wecms.domain.payment.dispatch.entity.TbBatchScheduleInfo;
import com.kosign.wecms.domain.payment.dispatch.repository.TbBatchScheduleDtlRepository;
import com.kosign.wecms.domain.payment.dispatch.repository.TbBatchScheduleInfoRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.DateTimeException;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Set;

/**
 * Bank / channel operating-hours gate — refactor of the legacy JEX
 * {@code TB_BATCH_SCHEDULE_INFO} / {@code TB_BATCH_SCHEDULE_DTL} {@code EXE_DIV}
 * logic used by {@code NAPAS_REAL_TIME_BATCH} and {@code FILE_DEPOSIT_TRANSFER_BATCH1}.
 * See {@code docs/features/wecms-batch/batch-schedule-operating-hours-audit.md}.
 *
 * <p><b>EXE_DIV (verbatim legacy SQL, {@code IDO.TB_BATCH_SCHEDULE_INFO_R001.xml}):</b>
 * <pre>
 *   BATCH_START_TM > now(HHmmss) → 'N'   (before start)
 *   BATCH_END_TM   < now(HHmmss) → 'E'   (after end / cutoff)
 *   else                         → 'Y'   (in window)
 * </pre>
 * The comparison is a lexicographic {@code HHmmss} (6-char) string compare, matching
 * legacy {@code TO_CHAR(NOW(),'HH24MISS')}. Legacy used the DB server clock — which on
 * the JEX frame DB is {@code Asia/Seoul}, not ICT; this refactor evaluates in
 * {@code Asia/Ho_Chi_Minh} (ICT), the project-wide dispatch time zone (same as
 * {@code RealtimeDispatchService} / {@code BulkReceptionService} /
 * {@code CutoffValidator}). Overridable — see {@link #operatingHoursZone}.
 *
 * <p><b>Legacy rules preserved (do not change):</b>
 * <ul>
 *   <li><b>INFO key is shared</b> — BOTH batches look up {@code TB_BATCH_SCHEDULE_INFO}
 *       with {@code BATCH_ID = "NAPAS_REAL_TIME_BATCH"} (BATCH1: {@code
 *       FILE_DEPOSIT_TRANSFER_BATCH1.java:272}; NAPAS: {@code :113}).</li>
 *   <li><b>INFO missing → NO_CRITERIA</b> for both (legacy {@code BATCH_START_TM == null}).</li>
 *   <li><b>NAPAS DTL</b> keys on {@code BATCH_ID = "NAPAS_REAL_TIME_BATCH"} + the pay-target
 *       {@code ANT_DIV_CD}, <b>canonicalised</b> via {@link AntDivChannel#canonical(String)}
 *       (the column carries both 1-char and 5-char forms — see that class); only when
 *       {@code ANT_WHERE_USE_YN='Y'} AND the network is not {@code "INTER"}; a
 *       <b>missing DTL row → NO_CRITERIA</b> ({@code :155-160}).</li>
 *   <li><b>BATCH1 DTL</b> keys on {@code BATCH_ID = "FILE_DEPOSIT_TRANSFER_BATCH1"} + the
 *       per-channel name; a <b>missing DTL row → pass ('Y') / DISPATCH</b> ({@code :814-817}).
 *       Channel precedence within a file: any {@code N} → SKIP (legacy immediate
 *       {@code continue}); else any {@code E} → CUTOFF; else DISPATCH.</li>
 *   <li>{@code EXE_DIV='E'} → CUTOFF; {@code 'N'} → SKIP (silent); {@code 'Y'} → DISPATCH.</li>
 * </ul>
 *
 * <p>Read-only (two PK lookups); no writes, no transaction of its own.
 */
@Service
@RequiredArgsConstructor
public class OperatingHoursService {

    private static final DateTimeFormatter HHMMSS = DateTimeFormatter.ofPattern("HHmmss");

    private static final String YN_Y = "Y";

    /**
     * Wall-clock zone the {@code HHmmss} window comparison is evaluated in.
     *
     * <p><b>Deliberate, verified divergence from legacy — read before changing.</b>
     * Legacy computes {@code EXE_DIV} in SQL as {@code TO_CHAR(NOW(),'HH24MISS')},
     * i.e. the <b>database server's</b> local zone. That zone is <b>not</b> ICT:
     * the JEX DEV frame DB reports {@code Asia/Seoul} (UTC+9), so legacy evaluates
     * a {@code 090000-153000} window as 09:00-15:30 <b>KST</b> = 07:00-13:30
     * Vietnam time. The refactor evaluates in {@code Asia/Ho_Chi_Minh}, which
     * <ul>
     *   <li>matches the business meaning of the windows (Vietnamese bank cut-offs), and</li>
     *   <li>matches {@code CutoffValidator.VIETNAM_ZONE}, so the request-time gate and
     *       this dispatch-time gate cannot contradict each other — a payment accepted
     *       at 15:00 ICT is not then failed by a KST-evaluated cut-off.</li>
     * </ul>
     * Overridable per environment so ops can realign with a co-hosted legacy JEX
     * batch if a shared UAT/PROD DB requires bug-for-bug parity. Set
     * {@code wecms.dispatch.operating-hours.zone=Asia/Seoul} to reproduce legacy exactly.
     * The field keeps an in-code default, so an unset property can never abort startup.
     * A <i>misspelled</i> one does — deliberately; see {@link #validateOperatingHoursZone()}.
     */
    @Value("${wecms.dispatch.operating-hours.zone:Asia/Ho_Chi_Minh}")
    private String operatingHoursZone = "Asia/Ho_Chi_Minh";

    private final TbBatchScheduleInfoRepository infoRepository;
    private final TbBatchScheduleDtlRepository dtlRepository;

    /** The business zone — the only value that keeps every dispatch consumer mutually consistent. */
    static final String ZONE_BUSINESS_ICT = "Asia/Ho_Chi_Minh";
    /** The legacy JEX frame-DB clock. Accepted, but gate-only — see {@link #validateOperatingHoursZone()}. */
    static final String ZONE_LEGACY_KST = "Asia/Seoul";

    /**
     * The closed set of values this property accepts. Deliberately <b>not</b> "any resolvable IANA
     * zone": a zone that is merely parseable but not one of these two has no defined meaning here and
     * would silently skew the gate against every other timestamp the dispatch path writes.
     */
    static final java.util.Set<String> ACCEPTED_ZONES =
            java.util.Set.of(ZONE_BUSINESS_ICT, ZONE_LEGACY_KST);

    /**
     * Fail fast at bean creation on anything outside {@link #ACCEPTED_ZONES}.
     *
     * <p><b>Why not just "is it a valid zone id".</b> {@link #nowHms()} resolves the zone on every
     * evaluation. A typo ({@code Asia/Hochiminh}) would otherwise throw
     * {@link java.time.DateTimeException} inside the gate on <em>each</em> dispatch, and because both
     * registrars swallow per-row failures ({@code RealtimeDispatchRegistrar} /
     * {@code DispatchAutoReceiveRegistrar} catch and log so one bad row cannot block its siblings),
     * every affected payment would silently sit at {@code PROC_STAT='9'} with only a log line — a
     * configuration typo presenting as "payments never dispatch". Refusing to start is the safe
     * failure for a money path.
     *
     * <p>A merely-resolvable-but-unintended zone ({@code America/New_York}) is worse than a typo: it
     * does not throw at all, it just moves the window. Hence the closed set.
     *
     * <h2>Scope of the override — GATE ONLY (verified 2026-07-29)</h2>
     * This property is honoured by <b>this class alone</b>. Twelve other dispatch classes hold
     * {@code private static final ZoneId ICT = ZoneId.of("Asia/Ho_Chi_Minh")} and are not
     * configurable: {@code RealtimeDispatchService}, {@code RealtimeTransferService},
     * {@code BulkReceptionService}, {@code ResultCheckService}, {@code StrandedRecoveryService},
     * {@code BulkReceptionResultService}, {@code BulkExecutionResultService},
     * {@code StuckPayFileMonitorService}, {@code OpsAlertService}, {@code DepositorInquiryService},
     * {@code FirmTranInquiryAuditService}, {@code RecipientLookupService} — plus
     * {@code CutoffValidator.VIETNAM_ZONE}.
     *
     * <p>So setting {@link #ZONE_LEGACY_KST} shifts <b>only</b> the operating-hours window; every
     * {@code PROC_DTTM} / {@code RQ_DTIME} / {@code TRAN_TM} stamp, the request-time cut-off gate, the
     * recovery grace window and the monitor all stay on ICT. That is a deliberate, narrow escape hatch
     * for reproducing a Seoul-clock legacy batch's <i>window decision</i> — it is <b>not</b> a
     * system-wide timezone switch, and it must not be treated as one. Pinned by
     * {@code OperatingHoursZoneConsistencyTest}; rationale in
     * {@code docs/architecture/operating-hours-timezone-decision.md}.
     */
    @PostConstruct
    void validateOperatingHoursZone() {
        if (!ACCEPTED_ZONES.contains(operatingHoursZone)) {
            throw new IllegalStateException(
                    "wecms.dispatch.operating-hours.zone must be one of " + ACCEPTED_ZONES
                            + " but was '" + operatingHoursZone + "'. '" + ZONE_BUSINESS_ICT
                            + "' is the business default; '" + ZONE_LEGACY_KST + "' reproduces the "
                            + "legacy JEX frame-DB clock for the operating-hours window ONLY and "
                            + "does not shift any timestamp the dispatch path writes.");
        }
        try {
            ZoneId.of(operatingHoursZone);
        } catch (DateTimeException e) {
            // Unreachable for the closed set above; kept so a future widening cannot skip the parse.
            throw new IllegalStateException(
                    "wecms.dispatch.operating-hours.zone is not a resolvable zone id: '"
                            + operatingHoursZone + "'.", e);
        }
    }

    /** Legacy raw {@code EXE_DIV} value. */
    enum ExeDiv { N, E, Y }

    // ── NAPAS real-time (per pay-target) ─────────────────────────────────────

    /**
     * Gate one real-time transfer target — refactor of {@code NAPAS_REAL_TIME_BATCH.java:110-189}.
     *
     * @param outBankCd withdrawing bank code ({@code OUT_BANK_CD})
     * @param antDivCd  the pay-target network code ({@code ANT_DIV_CD}, e.g. {@code INTER}/{@code NAPAS})
     */
    public OperatingHoursDecision evaluateRealtime(String outBankCd, String antDivCd) {
        return evaluateRealtime(outBankCd, antDivCd, nowHms());
    }

    /** Package-private seam: {@code nowHms} injected for deterministic tests. */
    OperatingHoursDecision evaluateRealtime(String outBankCd, String antDivCd, String nowHms) {
        TbBatchScheduleInfo info = infoRepository
                .findByBatchIdAndOutBankCd(BulkTransferConstants.BATCH_ID_NAPAS_REAL_TIME, outBankCd)
                .orElse(null);
        if (info == null) {
            return OperatingHoursDecision.NO_CRITERIA;
        }
        ExeDiv infoDiv = exeDiv(info.getBatchStartTm(), info.getBatchEndTm(), nowHms);
        if (infoDiv == ExeDiv.E) {
            return OperatingHoursDecision.CUTOFF;
        }
        if (infoDiv == ExeDiv.N) {
            return OperatingHoursDecision.SKIP;
        }
        // Canonicalise the pay-target's ANT_DIV_CD to the schedule vocabulary before
        // both the INTER test and the lookup. TB_PAY_TARGET.ANT_DIV_CD is bpchar(5) and
        // legitimately carries BOTH widths — verified on JEX DEV 2026-07-28: CITAD/NAPAS/
        // INTER plus 1-char 'E' and 'S' rows. The raw value was previously used as the
        // schedule key, so a 1-char code could never match its 5-char schedule row and
        // the payment was failed with 수행 기준 없음 — observed in live legacy data
        // (tran_seq 13219/13220, ANT_DIV_CD='E', PROC_STAT='3' FAIL). Canonicalising also
        // strips bpchar padding, which the raw comparison did not. Unknown codes stay
        // verbatim and still miss the lookup, preserving the legacy fail-closed outcome.
        String channel = AntDivChannel.canonical(antDivCd);

        // EXE_DIV = 'Y' — bank window open. Check the other-bank network only when the
        // bank uses it AND the network is not the own-bank INTER code (legacy :138).
        if (!YN_Y.equals(info.getAntWhereUseYn()) || AntDivChannel.INTER.equals(channel)) {
            return OperatingHoursDecision.DISPATCH;
        }
        TbBatchScheduleDtl dtl = dtlRepository
                .findByBatchIdAndOutBankCdAndAntDivCd(
                        BulkTransferConstants.BATCH_ID_NAPAS_REAL_TIME, outBankCd, channel)
                .orElse(null);
        if (dtl == null) {
            // NAPAS: missing DTL row = no execution criteria (legacy :155-160).
            return OperatingHoursDecision.NO_CRITERIA;
        }
        return fromExeDiv(exeDiv(dtl.getBatchStartTm(), dtl.getBatchEndTm(), nowHms));
    }

    // ── BATCH1 bulk file (aggregated over present channels) ──────────────────

    /**
     * Gate one bulk pay-file — refactor of {@code FILE_DEPOSIT_TRANSFER_BATCH1.java:269-448}.
     *
     * @param outBankCd        withdrawing bank code
     * @param presentChannels  the channel names present in the file (subset of
     *                         {@code EBANK/VCB/CITAD/NAPAS/SWIFT}); the caller derives
     *                         these from each detail row's 1-char {@code ANT_DIV_CD}
     *                         (E/V/C/N/S — the verified {@code R005} classification).
     */
    public OperatingHoursDecision evaluateBulk(String outBankCd, Set<String> presentChannels) {
        return evaluateBulk(outBankCd, presentChannels, nowHms());
    }

    /** Package-private seam: {@code nowHms} injected for deterministic tests. */
    OperatingHoursDecision evaluateBulk(String outBankCd, Set<String> presentChannels, String nowHms) {
        TbBatchScheduleInfo info = infoRepository
                .findByBatchIdAndOutBankCd(BulkTransferConstants.BATCH_ID_NAPAS_REAL_TIME, outBankCd)
                .orElse(null);
        if (info == null) {
            return OperatingHoursDecision.NO_CRITERIA;
        }
        ExeDiv infoDiv = exeDiv(info.getBatchStartTm(), info.getBatchEndTm(), nowHms);
        if (infoDiv == ExeDiv.E) {
            return OperatingHoursDecision.CUTOFF;
        }
        if (infoDiv == ExeDiv.N) {
            return OperatingHoursDecision.SKIP;
        }
        // EXE_DIV = 'Y'. When the bank does not use the other-bank network gate, dispatch.
        if (!YN_Y.equals(info.getAntWhereUseYn())) {
            return OperatingHoursDecision.DISPATCH;
        }
        // Bank uses per-channel windows but the file has no recognised channel → legacy
        // never sets exeYN=true, so the file is silently skipped (:445 !exeYN continue).
        if (presentChannels == null || presentChannels.isEmpty()) {
            return OperatingHoursDecision.SKIP;
        }
        // Per-channel DTL. Precedence: any 'N' short-circuits to SKIP (legacy immediate
        // continue); else any 'E' → CUTOFF; else DISPATCH. Missing DTL row = pass ('Y').
        boolean anyCutoff = false;
        for (String channel : presentChannels) {
            TbBatchScheduleDtl dtl = dtlRepository
                    .findByBatchIdAndOutBankCdAndAntDivCd(
                            BulkTransferConstants.BATCH_ID_FILE_DEPOSIT_TRANSFER_1, outBankCd, channel)
                    .orElse(null);
            if (dtl == null) {
                continue; // BATCH1 missing DTL row = 'Y' / pass (:814-817)
            }
            switch (exeDiv(dtl.getBatchStartTm(), dtl.getBatchEndTm(), nowHms)) {
                case N -> {
                    return OperatingHoursDecision.SKIP;
                }
                case E -> anyCutoff = true;
                case Y -> { /* pass */ }
            }
        }
        return anyCutoff ? OperatingHoursDecision.CUTOFF : OperatingHoursDecision.DISPATCH;
    }

    // ── internals ────────────────────────────────────────────────────────────

    private static OperatingHoursDecision fromExeDiv(ExeDiv d) {
        return switch (d) {
            case Y -> OperatingHoursDecision.DISPATCH;
            case E -> OperatingHoursDecision.CUTOFF;
            case N -> OperatingHoursDecision.SKIP;
        };
    }

    /**
     * Legacy {@code EXE_DIV} CASE. {@code startTm}/{@code endTm} are {@code HHmmss};
     * {@code nowHms} is the current {@code HHmmss}. Lexicographic compare (both are
     * zero-padded 6-char), identical to the SQL.
     */
    static ExeDiv exeDiv(String startTm, String endTm, String nowHms) {
        if (startTm != null && startTm.compareTo(nowHms) > 0) {
            return ExeDiv.N;
        }
        if (endTm != null && endTm.compareTo(nowHms) < 0) {
            return ExeDiv.E;
        }
        return ExeDiv.Y;
    }

    /** Current wall-clock {@code HHmmss} in {@link #operatingHoursZone}. */
    private String nowHms() {
        return LocalTime.now(ZoneId.of(operatingHoursZone)).format(HHMMSS);
    }
}
