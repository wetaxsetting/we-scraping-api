package com.kosign.wecms.domain.payment.dispatch.service;

import com.kosign.wecms.domain.payment.dispatch.constant.BulkTransferConstants;
import com.kosign.wecms.domain.payment.dispatch.entity.TbBatchScheduleDtl;
import com.kosign.wecms.domain.payment.dispatch.entity.TbBatchScheduleInfo;
import com.kosign.wecms.domain.payment.dispatch.repository.TbBatchScheduleDtlRepository;
import com.kosign.wecms.domain.payment.dispatch.repository.TbBatchScheduleInfoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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
 * legacy {@code TO_CHAR(NOW(),'HH24MISS')}. Legacy used the DB server clock; this
 * refactor uses {@code Asia/Ho_Chi_Minh} (ICT) — the project-wide dispatch time zone
 * (same as {@code RealtimeDispatchService} / {@code BulkReceptionService}).
 *
 * <p><b>Legacy rules preserved (do not change):</b>
 * <ul>
 *   <li><b>INFO key is shared</b> — BOTH batches look up {@code TB_BATCH_SCHEDULE_INFO}
 *       with {@code BATCH_ID = "NAPAS_REAL_TIME_BATCH"} (BATCH1: {@code
 *       FILE_DEPOSIT_TRANSFER_BATCH1.java:272}; NAPAS: {@code :113}).</li>
 *   <li><b>INFO missing → NO_CRITERIA</b> for both (legacy {@code BATCH_START_TM == null}).</li>
 *   <li><b>NAPAS DTL</b> keys on {@code BATCH_ID = "NAPAS_REAL_TIME_BATCH"} + the pay-target
 *       {@code ANT_DIV_CD}; only when {@code ANT_WHERE_USE_YN='Y'} AND the network is not
 *       {@code "INTER"}; a <b>missing DTL row → NO_CRITERIA</b> ({@code :155-160}).</li>
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

    private static final ZoneId ICT = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter HHMMSS = DateTimeFormatter.ofPattern("HHmmss");

    /** Legacy own-bank network code — NAPAS realtime skips the DTL check for it. */
    private static final String ANT_DIV_INTER = "INTER";
    private static final String YN_Y = "Y";

    private final TbBatchScheduleInfoRepository infoRepository;
    private final TbBatchScheduleDtlRepository dtlRepository;

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
        // EXE_DIV = 'Y' — bank window open. Check the other-bank network only when the
        // bank uses it AND the network is not the own-bank INTER code (legacy :138).
        if (!YN_Y.equals(info.getAntWhereUseYn()) || ANT_DIV_INTER.equals(antDivCd)) {
            return OperatingHoursDecision.DISPATCH;
        }
        TbBatchScheduleDtl dtl = dtlRepository
                .findByBatchIdAndOutBankCdAndAntDivCd(
                        BulkTransferConstants.BATCH_ID_NAPAS_REAL_TIME, outBankCd, antDivCd)
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

    private static String nowHms() {
        return LocalTime.now(ICT).format(HHMMSS);
    }
}
