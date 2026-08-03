package com.kosign.wecms.domain.payment.dispatch.monitor;

import com.kosign.wecms.domain.payment.dispatch.alert.OpsAlertService;
import com.kosign.wecms.domain.payment.dispatch.entity.TbFirmBatApiTran;
import com.kosign.wecms.domain.payment.dispatch.entity.TbFirmFileMst;
import com.kosign.wecms.domain.payment.dispatch.entity.TbPayFileMst;
import com.kosign.wecms.domain.payment.dispatch.enums.ApiTranTp;
import com.kosign.wecms.domain.payment.dispatch.enums.ProcStat;
import com.kosign.wecms.domain.payment.dispatch.enums.ProcStep;
import com.kosign.wecms.domain.payment.dispatch.repository.TbFirmBatApiTranRepository;
import com.kosign.wecms.domain.payment.dispatch.repository.TbFirmConfRepository;
import com.kosign.wecms.domain.payment.dispatch.repository.TbFirmFileMstRepository;
import com.kosign.wecms.domain.payment.dispatch.repository.TbPayFileDataRepository;
import com.kosign.wecms.domain.payment.dispatch.repository.TbPayFileMstRepository;
import com.kosign.wecms.domain.payment.dispatch.service.OperatingHoursService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The monitor's <b>second sweep</b> — {@code TB_FIRM_FILE_MST} rows stranded at
 * {@code PROC_STEP='1' / PROC_STAT='6'} (the bank has the file; the {@code 00302} execution
 * instruction was never confirmed).
 *
 * <p>The point of this sweep is that it works <b>with the retry sweep disabled</b>, which is its
 * default. Before it existed, nothing observed this state: {@code StuckPayFileMonitorService} looked
 * only at {@code PROC_STAT='9'} and {@code StrandedRecoveryService} only re-drove pay-files at
 * {@code '9'}, so a file stranded after {@code 00301} was invisible to every component.
 *
 * <p>The pay-file (WAIT) sweep is covered by {@link StuckPayFileMonitorServiceTest}; this class only
 * exercises the firm-file half.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StuckFirmFileMonitorTest {

    private static final ZoneId ICT = ZoneId.of("Asia/Ho_Chi_Minh");
    /** 2026-07-29 18:00:00 ICT. */
    private static final Instant NOW = Instant.parse("2026-07-29T11:00:00Z");
    private static final String BIZ = "VN_VNM0004_000001";
    private static final String FILE_SEQ = "0000001";
    private static final String FILE_NM = "S_FB_20260729_0001.txt";
    private static final String VAN_ORG = "GV00000001";

    @Mock TbPayFileMstRepository payFileMstRepository;
    @Mock TbPayFileDataRepository payFileDataRepository;
    @Mock TbFirmConfRepository firmConfRepository;
    @Mock OperatingHoursService operatingHoursService;
    @Mock OpsAlertService opsAlertService;
    @Mock TbFirmFileMstRepository firmFileMstRepository;
    @Mock TbFirmBatApiTranRepository firmBatApiTranRepository;
    @Mock com.kosign.wecms.domain.payment.dispatch.repository.TbPayTargetRepository payTargetRepository;
    @Mock com.kosign.wecms.domain.payment.dispatch.repository.TbFirmTranRepository firmTranRepository;

    private StuckPayFileMonitorService service;

    @BeforeEach
    void setUp() {
        service = new StuckPayFileMonitorService(payFileMstRepository, payFileDataRepository,
                firmConfRepository, operatingHoursService, opsAlertService,
                Clock.fixed(NOW, ICT), firmFileMstRepository, firmBatApiTranRepository,
                payTargetRepository, firmTranRepository);
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "thresholdMinutes", 15L);
        ReflectionTestUtils.setField(service, "reAlertMinutes", 60L);
        ReflectionTestUtils.setField(service, "maxAlertKeys", 20);
        ReflectionTestUtils.setField(service, "autoReceiveEnabled", true);
            ReflectionTestUtils.setField(service, "firmFileBatchSize", 200);

        // The pay-file (WAIT) sweep finds nothing in these tests.
        when(payFileMstRepository.findStrandedPayFiles(anyString(), anyString(), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(List.of());
    }

    private static TbFirmFileMst stranded() {
        TbFirmFileMst f = new TbFirmFileMst();
        f.setTranDt("20260729");
        f.setBizKey(BIZ);
        f.setFileSeqNo(FILE_SEQ);
        f.setFirmFileNm(FILE_NM);
        f.setVanOrgCd(VAN_ORG);
        f.setOutBankCd("970424");
        f.setBankPrtnId("PRTN0001");
        f.setProcStep(ProcStep.REQ_RECV.code());
        f.setProcStat(ProcStat.REQ.code());
        f.setReqCnt(new BigDecimal("3"));
        f.setProcDttm("20260729170000");   // 1 h old — past the 15 min threshold
        return f;
    }

    /**
     * Files stranded at {@code PROC_STEP='1'} (the reception-received sweep).
     *
     * <p>Stubbed on the <b>step</b> argument, not {@code anyString()}: since 2026-07-31 the sweep makes
     * two calls — one for {@code '1'}, one for {@code '2'} — and a step-agnostic stub would return the
     * same list to both, double-counting every file. See {@link #strandedAtExecutionRequested}.
     */
    private void stranded(TbFirmFileMst... files) {
        // FIX H arg shape: (procStep, procStat, procDttmCutoff, procDttmFloor, pageable) — no tranDt.
        when(firmFileMstRepository.findStrandedFirmFiles(
                eq(ProcStep.REQ_RECV.code()), anyString(), anyString(), anyString(), any(Pageable.class)))
                .thenReturn(List.of(files));
    }

    /** Files stranded at {@code PROC_STEP='2'} — 00302 accepted, 01302 result never received. */
    private void strandedAtExecutionRequested(TbFirmFileMst... files) {
        when(firmFileMstRepository.findStrandedFirmFiles(
                eq(ProcStep.DEPO_REQ.code()), anyString(), anyString(), anyString(), any(Pageable.class)))
                .thenReturn(List.of(files));
    }

    /** A file stranded at {@code PROC_STEP='2'} / {@code PROC_STAT='6'}, past the threshold. */
    private TbFirmFileMst strandedAtStep2() {
        TbFirmFileMst f = stranded();
        f.setProcStep(ProcStep.DEPO_REQ.code());
        f.setProcStat(ProcStat.REQ.code());
        return f;
    }

    /**
     * A file stranded at {@code PROC_STEP='0'} / {@code PROC_STAT='6'} — reception never reconciled.
     *
     * <p>{@code PROC_DTTM} is deliberately left <b>null</b> and only {@code REG_DTTM} set, because that
     * is what both systems actually write on insert (legacy {@code BATCH1:542-555}, refactor
     * {@code BulkReceptionService:603-607}; 28 of 30 such rows on JEX DEV have a null {@code PROC_DTTM}).
     * A fixture that set {@code PROC_DTTM} would silently stop exercising the reason
     * {@code findStrandedFirmFiles} exists.
     */
    private TbFirmFileMst strandedAtStep0() {
        TbFirmFileMst f = stranded();
        f.setProcStep(ProcStep.REQ.code());
        f.setProcStat(ProcStat.REQ.code());
        f.setProcDttm(null);
        f.setRegDttm("20260729170000");
        return f;
    }

    /** Files stranded at {@code PROC_STEP='0'} — served by the COALESCE-aging finder. */
    private void strandedAtReceptionRequested(TbFirmFileMst... files) {
        when(firmFileMstRepository.findStrandedFirmFiles(
                eq(ProcStep.REQ.code()), anyString(), anyString(), anyString(), any(Pageable.class)))
                .thenReturn(List.of(files));
    }

    /**
     * Whether a 00302 audit row exists for the file — the NOT_ISSUED vs UNCONFIRMED discriminator.
     *
     * <p>FIX G: the date-proof predicate. The classifier must not carry a {@code TRAN_DT} — see
     * {@link StuckFirmFileClassifierDateRolloverTest} for why, and for the rollover case this
     * boolean stub cannot express.
     */
    private void executionIssued(boolean issued) {
        when(firmBatApiTranRepository.existsByBizKeyAndFirmFileNmAndApiTranTp(
                anyString(), anyString(), eq(ApiTranTp.BULK_EXECUTION.code())))
                .thenReturn(issued);
    }

    // ── 13z. PROC_STEP='2' — execution accepted, result never received ──────
    //
    // Four PROD files (20251128, 20251201, 20260407 WRB, 20260529 SHB — 27 payment lines; count
    // corrected 2026-08-01, it read "two / 16") sat in this state undetected:
    // this sweep filtered PROC_STEP='1' only, and the parent pay-file reported the benign
    // AWAITING_EXECUTION_RESULT. The 00302 was ACCEPTED (that is what wrote step '2'), so the bank has
    // most likely executed the batch — the opposite disposition to BULK_EXECUTION_NOT_ISSUED.

    @Test
    @DisplayName("13z: a file stranded at PROC_STEP='2'/PROC_STAT='6' past the threshold is detected")
    void detectsFileStrandedAfterExecutionRequest() {
        strandedAtExecutionRequested(strandedAtStep2());

        StuckPayFileReport report = service.monitorOnce();

        assertThat(report.firmFileStuckCount()).isEqualTo(1);
        assertThat(report.count(StuckPayFileCause.BULK_EXECUTION_RESULT_NOT_RECEIVED)).isEqualTo(1);
        // Must NOT be reported as either PROC_STEP='1' cause — their dispositions are opposite.
        assertThat(report.count(StuckPayFileCause.BULK_EXECUTION_NOT_ISSUED)).isZero();
        assertThat(report.count(StuckPayFileCause.BULK_EXECUTION_UNCONFIRMED)).isZero();
    }

    @Test
    @DisplayName("13z-i: the sweep issues a second query for PROC_STEP='2' with the same bounds")
    void queriesTheExecutionRequestedStateToo() {
        stranded();

        service.monitorOnce();

        verify(firmFileMstRepository).findStrandedFirmFiles(
                eq(ProcStep.DEPO_REQ.code()), eq(ProcStat.REQ.code()),
                eq("20260729174500"),      // same cutoff as the '1' sweep
                eq("20260722180000"),      // same retention floor
                any(Pageable.class));
    }

    @Test
    @DisplayName("13z-ii: both stranded steps are reported together, each under its own cause")
    void reportsBothStrandedStepsIndependently() {
        stranded(stranded());                                   // one at '1' — no 00302 audit row
        strandedAtExecutionRequested(strandedAtStep2());        // one at '2'
        executionIssued(false);

        StuckPayFileReport report = service.monitorOnce();

        assertThat(report.firmFileStuckCount()).isEqualTo(2);
        assertThat(report.count(StuckPayFileCause.BULK_EXECUTION_NOT_ISSUED)).isEqualTo(1);
        assertThat(report.count(StuckPayFileCause.BULK_EXECUTION_RESULT_NOT_RECEIVED)).isEqualTo(1);
    }

    @Test
    @DisplayName("13z-iii: the '2' classification never consults the 00302 audit row — the step IS the evidence")
    void step2ClassificationDoesNotDependOnThe00302AuditRow() {
        strandedAtExecutionRequested(strandedAtStep2());
        // Whether or not a 00302 row is findable, a PROC_STEP='2' file is confirmed-instructed:
        // only BulkExecutionInlineTxService writes that step, and only after a 0000/BT00 response.
        executionIssued(false);

        StuckPayFileReport report = service.monitorOnce();

        assertThat(report.count(StuckPayFileCause.BULK_EXECUTION_RESULT_NOT_RECEIVED)).isEqualTo(1);
        assertThat(report.count(StuckPayFileCause.BULK_EXECUTION_NOT_ISSUED))
                .as("classifying a step-2 file as NOT_ISSUED would tell the operator a re-drive is "
                        + "routine — that is a duplicate execution of a batch the bank already ran")
                .isZero();
    }

    // ── 13. detection ───────────────────────────────────────────────────────

    @Test
    @DisplayName("13: a file stranded at PROC_STEP='1'/PROC_STAT='6' past the threshold is detected")
    void detectsStrandedFirmFile() {
        stranded(stranded());
        executionIssued(false);   // no 00302 audit row

        StuckPayFileReport report = service.monitorOnce();

        assertThat(report.firmFileStuckCount()).isEqualTo(1);
        assertThat(report.count(StuckPayFileCause.BULK_EXECUTION_NOT_ISSUED)).isEqualTo(1);
        assertThat(report.totalStuck()).isEqualTo(1);
    }

    @Test
    @DisplayName("13b: the sweep queries the legacy BATCH2 state predicate, bounded by PROC_DTTM at both ends")
    void queriesTheLegacyStatePredicate() {
        stranded();

        service.monitorOnce();

        // Legacy :50-52 — PROC_STEP = PROC_STP_REQ_RECV ('1'), PROC_STAT = PROC_ST_REQ ('6').
        //
        // FIX H: no TRAN_DT argument any more. The window is now PROC_DTTM on both sides:
        //   cutoff = 18:00 ICT − 15 min threshold   (too recent to be stranded)
        //   floor  = 18:00 ICT − 7 days retention   (too old to keep reporting)
        // Legacy scoped to TRAN_DT = today, which made a 23:59 strand invisible from 00:00; legacy got
        // away with it because BATCH2 re-sent 00302 every minute, and that re-drive is gone (K9).
        verify(firmFileMstRepository).findStrandedFirmFiles(
                eq(ProcStep.REQ_RECV.code()), eq(ProcStat.REQ.code()),
                eq("20260729174500"),      // cutoff
                eq("20260722180000"),      // floor — 7 days back
                any(Pageable.class));
    }

    @Test
    @DisplayName("13b-i: the retention floor is configurable and is what bounds the now-dateless sweep")
    void retentionFloorIsConfigurable() {
        ReflectionTestUtils.setField(service, "retentionDays", 2);
        stranded();

        service.monitorOnce();

        verify(firmFileMstRepository).findStrandedFirmFiles(
                eq(ProcStep.REQ_RECV.code()), anyString(), eq("20260729174500"),
                eq("20260727180000"),      // 18:00 ICT − 2 days
                any(Pageable.class));
    }

    @Test
    @DisplayName("13b-ii: a retention-days of 0 or less is coerced to 1, never to 'today only'")
    void retentionFloorNeverCollapsesToToday() {
        // A floor of 0 would put the floor at "now", selecting nothing; a negative value would put it
        // in the future. Either would silently reproduce the same-day blind spot FIX H removed, so the
        // value is clamped rather than trusted.
        ReflectionTestUtils.setField(service, "retentionDays", 0);
        stranded();

        service.monitorOnce();

        verify(firmFileMstRepository).findStrandedFirmFiles(
                eq(ProcStep.REQ_RECV.code()), anyString(), anyString(),
                eq("20260728180000"),      // clamped to 1 day, NOT 18:00 today
                any(Pageable.class));
    }

    @Test
    @DisplayName("13b-iii: a file stranded before midnight is still reported after midnight")
    void strandedBeforeMidnightIsStillReportedTheNextDay() {
        // The defect FIX H closes, expressed at the level the sweep operates on: the row's TRAN_DT and
        // PROC_DTTM are yesterday's, "now" is 18:00 today, and it must still come back. Under the old
        // TRAN_DT = today predicate this row could not be selected at all — the finder was called with
        // today's date and the row carried yesterday's.
        TbFirmFileMst yesterday = stranded();
        yesterday.setTranDt("20260728");
        yesterday.setProcDttm("20260728235900");   // 23:59 the previous day
        stranded(yesterday);
        executionIssued(false);

        StuckPayFileReport report = service.monitorOnce();

        assertThat(report.firmFileStuckCount())
                .as("a strand from before midnight must not fall out of view at 00:00")
                .isEqualTo(1);
        // And it is inside the window the finder asked for: floor 20260722180000 <= 20260728235900 < cutoff.
        verify(firmFileMstRepository).findStrandedFirmFiles(
                eq(ProcStep.REQ_RECV.code()), anyString(), eq("20260729174500"), eq("20260722180000"), any(Pageable.class));
    }

    @Test
    @DisplayName("13c: detection needs no retry sweep at all — the monitor is the whole residual-recovery signal")
    void detectionIsIndependentOfTheRetrySweep() {
        // There is no retry sweep in the approved architecture (K9 Option A was not selected), so this
        // read-only sweep plus manual recovery IS the residual model — not a fallback for a disabled job.
        stranded(stranded());
        executionIssued(false);

        assertThat(service.monitorOnce().firmFileStuckCount()).isEqualTo(1);
    }

    // ── cause classification — the two states need OPPOSITE manual actions ──

    @Test
    @DisplayName("a 00302 audit row present ⇒ UNCONFIRMED (sent, never confirmed — reconcile first)")
    void classifiesUnconfirmedWhenExecutionWasIssued() {
        stranded(stranded());
        executionIssued(true);

        StuckPayFileReport report = service.monitorOnce();

        // The bank may already have executed the batch — an operator must reconcile with the VAN
        // Center before doing anything, so this must never be conflated with NOT_ISSUED.
        assertThat(report.count(StuckPayFileCause.BULK_EXECUTION_UNCONFIRMED)).isEqualTo(1);
        assertThat(report.count(StuckPayFileCause.BULK_EXECUTION_NOT_ISSUED)).isZero();
    }

    @Test
    @DisplayName("no 00302 audit row ⇒ NOT_ISSUED (lost callback / crash — safe to re-drive)")
    void classifiesNotIssuedWhenExecutionNeverRan() {
        stranded(stranded());
        executionIssued(false);

        StuckPayFileReport report = service.monitorOnce();

        assertThat(report.count(StuckPayFileCause.BULK_EXECUTION_NOT_ISSUED)).isEqualTo(1);
        assertThat(report.count(StuckPayFileCause.BULK_EXECUTION_UNCONFIRMED)).isZero();
    }

    @Test
    @DisplayName("classification never counts attempts — there is no automatic retry to exhaust")
    void classificationUsesPresenceNotCount() {
        // Guards the K9 alignment: the monitor asks "was 00302 sent?", not "how many times?".
        // An attempt-counting finder existed here on 2026-07-29 for a scheduled retry sweep and was
        // removed with it. If a count-based finder reappears, the sweep has probably come back too.
        stranded(stranded());
        executionIssued(true);

        service.monitorOnce();

        verify(firmBatApiTranRepository).existsByBizKeyAndFirmFileNmAndApiTranTp(
                anyString(), anyString(), eq(ApiTranTp.BULK_EXECUTION.code()));
    }

    // ── alerting ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("the alert names the stranded firm-file and resolves VAN_ORG_CD from the row itself")
    void alertsUsingTheFileOwnVanOrgCd() {
        stranded(stranded());
        executionIssued(false);

        StuckPayFileReport report = service.monitorOnce();

        assertThat(report.alerted()).isTrue();
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        // No TB_FIRM_CONF row is stubbed, so the fallback to TbFirmFileMst.VAN_ORG_CD is what
        // makes this alert deliverable at all.
        verify(opsAlertService).notify(eq(VAN_ORG), body.capture());
        assertThat(body.getValue())
                .contains("STRANDED_FIRM_FILE_TOTAL : 1")
                .contains("BULK_EXECUTION_NOT_ISSUED");
    }

    @Test
    @DisplayName("nothing stranded and nothing waiting — no alert, empty report")
    void cleanSweepIsSilent() {
        stranded();

        StuckPayFileReport report = service.monitorOnce();

        assertThat(report.firmFileStuckCount()).isZero();
        assertThat(report.stuckCount()).isZero();
        assertThat(report.alerted()).isFalse();
    }

    @Test
    @DisplayName("the second sweep is bounded — monitoring cannot become an unbounded scan")
    void secondSweepIsBounded() {
        ReflectionTestUtils.setField(service, "firmFileBatchSize", 25);
        stranded();

        service.monitorOnce();

        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(firmFileMstRepository).findStrandedFirmFiles(
                eq(ProcStep.REQ_RECV.code()), anyString(), anyString(), anyString(), page.capture());
        assertThat(page.getValue().getPageSize()).isEqualTo(25);
    }

    @Test
    @DisplayName("disabled monitor does not run the second sweep either")
    void disabledMonitorSkipsBothSweeps() {
        ReflectionTestUtils.setField(service, "enabled", false);

        StuckPayFileReport report = service.monitorOnce();

        assertThat(report.firmFileStuckCount()).isZero();
        org.mockito.Mockito.verifyNoInteractions(firmFileMstRepository);
    }

    /* ───────── PROC_STEP='0' — reception never reconciled (added 2026-07-31) ───────── */

    /**
     * The lost-{@code 01301} case: a {@code 00301} audit row exists, so the request went out and the bank
     * has the file. This is the majority of the population — 27 of the 30 rows at this state on JEX DEV.
     */
    @Test
    @DisplayName("PROC_STEP=0 with a 00301 audit row => BULK_RECEPTION_RESULT_NOT_RECEIVED (never re-drive)")
    void step0WithReceptionAudit_isResultNotReceived() {
        strandedAtReceptionRequested(strandedAtStep0());
        when(firmBatApiTranRepository.existsByBizKeyAndFirmFileNmAndApiTranTp(
                BIZ, FILE_NM, ApiTranTp.BULK_RECEPTION.code())).thenReturn(true);

        StuckPayFileReport r = service.monitorOnce();

        assertThat(r.firmFileStuckCount()).isEqualTo(1);
        assertThat(r.count(StuckPayFileCause.BULK_RECEPTION_RESULT_NOT_RECEIVED)).isEqualTo(1);
        // Must NOT borrow the execution-leg constants — those describe a file past 00301 reception.
        assertThat(r.count(StuckPayFileCause.BULK_EXECUTION_NOT_ISSUED)).isZero();
        assertThat(r.count(StuckPayFileCause.BULK_EXECUTION_UNCONFIRMED)).isZero();
    }

    /** The phase-B-orphan case: no audit row, so the 00301 call was never reached and nothing was sent. */
    @Test
    @DisplayName("PROC_STEP=0 with no 00301 audit row => BULK_RECEPTION_NOT_ISSUED (safe to re-dispatch)")
    void step0WithoutReceptionAudit_isNotIssued() {
        strandedAtReceptionRequested(strandedAtStep0());
        when(firmBatApiTranRepository.existsByBizKeyAndFirmFileNmAndApiTranTp(
                BIZ, FILE_NM, ApiTranTp.BULK_RECEPTION.code())).thenReturn(false);

        StuckPayFileReport r = service.monitorOnce();

        assertThat(r.count(StuckPayFileCause.BULK_RECEPTION_NOT_ISSUED)).isEqualTo(1);
    }

    /**
     * All three firm-file steps go through the <b>same</b> finder, each bounded, each at
     * {@code PROC_STAT='6'}.
     *
     * <p>Rewritten 2026-08-01. It previously asserted that the {@code '0'} step used a dedicated
     * {@code COALESCE}-aging finder and that the {@code PROC_DTTM}-only sibling was never called for it —
     * an assertion that became self-contradictory when the two finders were unified (they are now one
     * method, so "never called with step 0" and "called with step 0" were the same verification). The
     * property worth keeping is the one the unification bought: <b>one aging rule for every step</b>, so
     * no step can silently age differently from another. The JPQL half of that is pinned in
     * {@code FirmFileDataExecutionResultQueryContractTest}; this is the wiring half.
     */
    @Test
    @DisplayName("all three firm-file steps use the one finder, each bounded by the batch size")
    void allThreeStepsUseTheOneFinderAndAreBounded() {
        ReflectionTestUtils.setField(service, "firmFileBatchSize", 25);
        strandedAtReceptionRequested(strandedAtStep0());

        service.monitorOnce();

        for (String step : List.of(ProcStep.REQ.code(), ProcStep.REQ_RECV.code(), ProcStep.DEPO_REQ.code())) {
            ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
            verify(firmFileMstRepository).findStrandedFirmFiles(
                    eq(step), eq(ProcStat.REQ.code()), anyString(), anyString(), page.capture());
            assertThat(page.getValue().getPageSize())
                    .as("step %s must carry the configured cap", step)
                    .isEqualTo(25);
        }
        // Exactly three selections — one per step, no extra scan.
        verify(firmFileMstRepository, org.mockito.Mockito.times(3)).findStrandedFirmFiles(
                anyString(), anyString(), anyString(), anyString(), any(Pageable.class));
    }

    /* ───────── the abandoned-batch mislabel fix ───────── */

    /**
     * A file at {@code '1'/'6'} whose parent pay-file is {@code '3'} must NOT be
     * {@link StuckPayFileCause#BULK_EXECUTION_NOT_ISSUED}.
     *
     * <p>That cause's runbook entry says re-driving is routine. Here it is the opposite: phase B marked
     * the pay-file FAIL and stopped, so siblings after the failure point were never sent, and re-driving
     * this one part-executes a batch already reported as failed.
     */
    @Test
    @DisplayName("PROC_STEP=1 in an abandoned batch => BULK_BATCH_PARENT_FAILED, not *_NOT_ISSUED")
    void step1WithFailedParent_isNotMislabelledAsNotIssued() {
        stranded(stranded());
        when(firmBatApiTranRepository.existsByBizKeyAndFirmFileNmAndApiTranTp(
                BIZ, FILE_NM, ApiTranTp.BULK_EXECUTION.code())).thenReturn(false);
        when(payFileMstRepository.findByTranDtAndBizKeyAndFileSeqNo("20260729", BIZ, FILE_SEQ))
                .thenReturn(java.util.Optional.of(failedParent()));

        StuckPayFileReport r = service.monitorOnce();

        assertThat(r.count(StuckPayFileCause.BULK_BATCH_PARENT_FAILED)).isEqualTo(1);
        assertThat(r.count(StuckPayFileCause.BULK_EXECUTION_NOT_ISSUED))
                .as("the dangerous mislabel: this cause tells the operator to re-drive")
                .isZero();
    }

    /** Same guard on the reception leg — an abandoned batch's unsent orphan is not "safe to re-dispatch". */
    @Test
    @DisplayName("PROC_STEP=0 in an abandoned batch => BULK_BATCH_PARENT_FAILED, not BULK_RECEPTION_NOT_ISSUED")
    void step0WithFailedParent_isNotMislabelledAsNotIssued() {
        strandedAtReceptionRequested(strandedAtStep0());
        when(firmBatApiTranRepository.existsByBizKeyAndFirmFileNmAndApiTranTp(
                BIZ, FILE_NM, ApiTranTp.BULK_RECEPTION.code())).thenReturn(false);
        when(payFileMstRepository.findByTranDtAndBizKeyAndFileSeqNo("20260729", BIZ, FILE_SEQ))
                .thenReturn(java.util.Optional.of(failedParent()));

        StuckPayFileReport r = service.monitorOnce();

        assertThat(r.count(StuckPayFileCause.BULK_BATCH_PARENT_FAILED)).isEqualTo(1);
        assertThat(r.count(StuckPayFileCause.BULK_RECEPTION_NOT_ISSUED)).isZero();
    }

    /**
     * {@code PROC_STEP='2'} is exempt from the parent check, deliberately. Its {@code 00302} was
     * accepted, so {@link StuckPayFileCause#BULK_EXECUTION_RESULT_NOT_RECEIVED} carries the stronger
     * warning ("the bank has most likely executed this batch"); replacing it with the parent's state
     * would lose that and read as merely "the batch failed".
     */
    @Test
    @DisplayName("PROC_STEP=2 keeps BULK_EXECUTION_RESULT_NOT_RECEIVED even when the parent is FAIL")
    void step2IsExemptFromTheParentCheck() {
        strandedAtExecutionRequested(strandedAtStep2());
        when(payFileMstRepository.findByTranDtAndBizKeyAndFileSeqNo("20260729", BIZ, FILE_SEQ))
                .thenReturn(java.util.Optional.of(failedParent()));

        StuckPayFileReport r = service.monitorOnce();

        assertThat(r.count(StuckPayFileCause.BULK_EXECUTION_RESULT_NOT_RECEIVED)).isEqualTo(1);
        assertThat(r.count(StuckPayFileCause.BULK_BATCH_PARENT_FAILED)).isZero();
    }

    /** A healthy (non-FAIL) parent leaves the audit-row split in charge. */
    @Test
    void healthyParent_doesNotTriggerTheAbandonedBatchCause() {
        stranded(stranded());
        when(firmBatApiTranRepository.existsByBizKeyAndFirmFileNmAndApiTranTp(
                BIZ, FILE_NM, ApiTranTp.BULK_EXECUTION.code())).thenReturn(false);
        TbPayFileMst parent = failedParent();
        parent.setProcStat(ProcStat.REQ.code());   // '6' — still in flight
        when(payFileMstRepository.findByTranDtAndBizKeyAndFileSeqNo("20260729", BIZ, FILE_SEQ))
                .thenReturn(java.util.Optional.of(parent));

        StuckPayFileReport r = service.monitorOnce();

        assertThat(r.count(StuckPayFileCause.BULK_EXECUTION_NOT_ISSUED)).isEqualTo(1);
        assertThat(r.count(StuckPayFileCause.BULK_BATCH_PARENT_FAILED)).isZero();
    }

    private static TbPayFileMst failedParent() {
        TbPayFileMst p = new TbPayFileMst();
        p.setBizKey(BIZ);
        p.setTranDt("20260729");
        p.setFileSeqNo(FILE_SEQ);
        p.setProcStat(ProcStat.FAIL.code());
        return p;
    }
}
