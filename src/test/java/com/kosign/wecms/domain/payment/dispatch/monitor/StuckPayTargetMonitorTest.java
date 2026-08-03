package com.kosign.wecms.domain.payment.dispatch.monitor;

import com.kosign.wecms.domain.payment.dispatch.alert.OpsAlertService;
import com.kosign.wecms.domain.payment.dispatch.entity.TbPayTarget;
import com.kosign.wecms.domain.payment.dispatch.repository.TbFirmBatApiTranRepository;
import com.kosign.wecms.domain.payment.dispatch.repository.TbFirmConfRepository;
import com.kosign.wecms.domain.payment.dispatch.repository.TbFirmFileMstRepository;
import com.kosign.wecms.domain.payment.dispatch.repository.TbFirmTranRepository;
import com.kosign.wecms.domain.payment.dispatch.repository.TbPayFileDataRepository;
import com.kosign.wecms.domain.payment.dispatch.repository.TbPayFileMstRepository;
import com.kosign.wecms.domain.payment.dispatch.repository.TbPayTargetRepository;
import com.kosign.wecms.domain.payment.dispatch.service.OperatingHoursDecision;
import com.kosign.wecms.domain.payment.dispatch.service.OperatingHoursService;
import com.kosign.wecms.domain.setting.firmbankingsettings.entity.TbFirmConf;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The FOURTH monitor sweep — {@code TB_PAY_TARGET} stranded at {@code PROC_STAT='9'}, the real-time
 * (202) rail.
 *
 * <h2>What was invisible before this sweep</h2>
 * {@code StrandedRecoveryService.recoverPayTargets} re-drives exactly this population, but it is
 * coexistence-gated OFF (and must stay off while the legacy batch may share the DB) — so on every
 * environment today a real-time payment whose after-commit 202 hand-off returned early or threw sits at
 * WAIT with nothing reporting it. The three existing sweeps cannot see it: two watch
 * {@code TB_PAY_FILE_MST} and one watches {@code TB_FIRM_FILE_MST}.
 *
 * <h2>Two properties these tests pin</h2>
 * <ol>
 *   <li><b>Its own grace window.</b> The pay-target window is deliberately shorter than the pay-file
 *       one, because the real-time hand-off has nothing to wait for. A test that only checked "old rows
 *       are reported" would pass with the two windows collapsed into one.</li>
 *   <li><b>{@code REALTIME_DISPATCHED_NOT_CLAIMED} outranks the config and window causes.</b> That
 *       state is permanent — dispatch step 2 short-circuits on the firm-tran row keyed by the
 *       pay-target's own {@code TRAN_SEQ} — so classifying it as a benign window skip would tell an
 *       operator to wait for something that will never happen. The refactor cannot produce the shape
 *       itself (claim + 202 insert are atomic, and the 204's sequence is independent), so the row is
 *       legacy-written or hand-edited and may already have moved money.</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StuckPayTargetMonitorTest {

    private static final DateTimeFormatter DTTM14 = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final ZoneId ICT = ZoneId.of("Asia/Ho_Chi_Minh");
    /** 2026-07-28 18:00:00 ICT. */
    private static final Instant NOW = Instant.parse("2026-07-28T11:00:00Z");

    private static final String BIZ = "VN_VNM0004_000001";
    private static final String WOORI = "970457";
    private static final String VAN_ORG = "GV00000001";

    @Mock TbPayFileMstRepository payFileMstRepository;
    @Mock TbPayFileDataRepository payFileDataRepository;
    @Mock TbFirmConfRepository firmConfRepository;
    @Mock OperatingHoursService operatingHoursService;
    @Mock OpsAlertService opsAlertService;
    @Mock TbFirmFileMstRepository firmFileMstRepository;
    @Mock TbFirmBatApiTranRepository firmBatApiTranRepository;
    @Mock TbPayTargetRepository payTargetRepository;
    @Mock TbFirmTranRepository firmTranRepository;

    private StuckPayFileMonitorService service;

    @BeforeEach
    void setUp() {
        service = new StuckPayFileMonitorService(payFileMstRepository, payFileDataRepository,
                firmConfRepository, operatingHoursService, opsAlertService,
                Clock.fixed(NOW, ICT), firmFileMstRepository, firmBatApiTranRepository,
                payTargetRepository, firmTranRepository);
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "thresholdMinutes", 15L);
        ReflectionTestUtils.setField(service, "payTargetThresholdMinutes", 5L);
        ReflectionTestUtils.setField(service, "reAlertMinutes", 60L);
        ReflectionTestUtils.setField(service, "maxAlertKeys", 20);
        ReflectionTestUtils.setField(service, "autoReceiveEnabled", true);
        // Default: the real-time flag is ON, so classification reaches the interesting branches. The
        // flag-off short-circuit has its own test.
        ReflectionTestUtils.setField(service, "realtimeDispatchEnabled", true);

        TbFirmConf conf = org.mockito.Mockito.mock(TbFirmConf.class);
        when(conf.getVanOrgCd()).thenReturn(VAN_ORG);
        when(firmConfRepository.findById(any())).thenReturn(Optional.of(conf));
        when(operatingHoursService.evaluateRealtime(anyString(), any()))
                .thenReturn(OperatingHoursDecision.DISPATCH);
    }

    private String dttm(int ageMinutes) {
        return LocalDateTime.ofInstant(NOW, ICT).minusMinutes(ageMinutes).format(DTTM14);
    }

    private TbPayTarget target(String tranSeq, String procStat, int ageMinutes) {
        TbPayTarget t = new TbPayTarget();
        t.setBizKey(BIZ);
        t.setTranDt("20260728");
        t.setTranSeq(tranSeq);
        t.setOutBankCd(WOORI);
        t.setWdrlActNo("1001");
        t.setAntDivCd("NAPAS");
        t.setTranAmt(BigDecimal.TEN);
        t.setProcStat(procStat);
        t.setProcDttm(dttm(ageMinutes));
        return t;
    }

    /** Stub the finder with the real predicate (status equality + procDttm &lt; cutoff) over `rows`. */
    private void targets(TbPayTarget... rows) {
        List<TbPayTarget> all = Arrays.asList(rows);
        when(payTargetRepository.findStrandedPayTargets(anyString(), anyString(), any(Pageable.class)))
                .thenAnswer(inv -> {
                    String procStat = inv.getArgument(0);
                    String cutoff = inv.getArgument(1);
                    List<TbPayTarget> hit = new ArrayList<>();
                    for (TbPayTarget r : all) {
                        if (procStat.equals(r.getProcStat())
                                && r.getProcDttm().compareTo(cutoff) < 0) {
                            hit.add(r);
                        }
                    }
                    return hit;
                });
    }

    /* ───────────────────── grace window ───────────────────── */

    @Test
    void payTargetOlderThanItsOwnWindow_isDetected() {
        targets(target("T1", "9", 30));

        StuckPayFileReport r = service.monitorOnce();

        assertThat(r.payTargetStuckCount()).isEqualTo(1);
        assertThat(r.totalStuck()).isEqualTo(1);
    }

    @Test
    void payTargetYoungerThanItsOwnWindow_isIgnored() {
        targets(target("T1", "9", 2));      // 2 min old, pay-target window 5

        StuckPayFileReport r = service.monitorOnce();

        assertThat(r.payTargetStuckCount()).isZero();
        assertThat(r.alerted()).isFalse();
    }

    @Test
    @DisplayName("the pay-target window is independent of the pay-file window — not a shared threshold")
    void payTargetWindowIsSeparateFromThePayFileWindow() {
        // 8 minutes: past the 5-minute pay-target window, INSIDE the 15-minute pay-file window. If the
        // sweep reused thresholdMinutes this row would be invisible — which is exactly the blind spot
        // giving the real-time rail its own knob removes.
        targets(target("T1", "9", 8));

        assertThat(service.monitorOnce().payTargetStuckCount())
                .as("8-minute-old real-time row must be reported despite the 15-minute pay-file window")
                .isEqualTo(1);

        // And the separation holds in the other direction: widening only the pay-target window hides it
        // again without touching pay-file detection.
        ReflectionTestUtils.setField(service, "payTargetThresholdMinutes", 30L);
        assertThat(service.monitorOnce().payTargetStuckCount()).isZero();
    }

    @Test
    void nonWaitStatus_isNeverSelected() {
        // '6' (claimed) and '0'/'3' (terminal) belong to other sweeps or to nobody — the finder is
        // called with '9' and only '9'.
        targets(target("T1", "6", 30), target("T2", "3", 30), target("T3", "9", 30));

        assertThat(service.monitorOnce().payTargetStuckCount()).isEqualTo(1);
    }

    /* ───────────────────── classification ───────────────────── */

    @Test
    void flagOff_explainsEveryStrandedRow_andIsReportedFirst() {
        ReflectionTestUtils.setField(service, "realtimeDispatchEnabled", false);
        targets(target("T1", "9", 30));

        StuckPayFileReport r = service.monitorOnce();

        assertThat(r.count(StuckPayFileCause.REALTIME_DISPATCH_FLAG_DISABLED)).isEqualTo(1);
        // The bulk flag's constant must NOT be used — it points at a different switch.
        assertThat(r.count(StuckPayFileCause.DISPATCH_FLAG_DISABLED)).isZero();
        // Nothing else was probed: the flag alone is the whole explanation.
        verify(firmTranRepository, never()).existsByTranDtAndBizKeyAndTranSeq(any(), any(), any());
    }

    @Test
    @DisplayName("a firm-tran row on the pay-target's own TRAN_SEQ => REALTIME_DISPATCHED_NOT_CLAIMED")
    void firmTranOnTheTargetsOwnSeq_isThePermanentStrand() {
        // The observable: dispatch step 2 probes the PAY-TARGET's TRAN_SEQ, so a firm-tran row on that
        // key makes every re-drive a no-op — including StrandedRecoveryService's.
        //
        // The refactor cannot produce this shape (claimForDispatch and the 202 insert share one
        // REQUIRES_NEW transaction), so it means a legacy-written or hand-edited row. Legacy has no
        // claim at all and no exists-guard, so its next tick would RE-SEND the 202 — our permanent
        // block is the safer behaviour.
        targets(target("T1", "9", 30));
        when(firmTranRepository.existsByTranDtAndBizKeyAndTranSeq("20260728", BIZ, "T1"))
                .thenReturn(true);
        // Config present and window open: neither is the reason, and neither may win the classification.
        when(operatingHoursService.evaluateRealtime(anyString(), any()))
                .thenReturn(OperatingHoursDecision.DISPATCH);

        StuckPayFileReport r = service.monitorOnce();

        assertThat(r.count(StuckPayFileCause.REALTIME_DISPATCHED_NOT_CLAIMED)).isEqualTo(1);
        assertThat(r.count(StuckPayFileCause.UNKNOWN)).isZero();
    }

    @Test
    void thePermanentStrand_outranksAnOpenWindowSkip() {
        // Same row, but the window ALSO reports SKIP. The permanent cause must still win: telling an
        // operator "waiting for the window" about a row that can never dispatch is the misdiagnosis
        // this ordering exists to prevent.
        targets(target("T1", "9", 30));
        when(firmTranRepository.existsByTranDtAndBizKeyAndTranSeq(any(), any(), any())).thenReturn(true);
        when(operatingHoursService.evaluateRealtime(anyString(), any()))
                .thenReturn(OperatingHoursDecision.SKIP);

        StuckPayFileReport r = service.monitorOnce();

        assertThat(r.count(StuckPayFileCause.REALTIME_DISPATCHED_NOT_CLAIMED)).isEqualTo(1);
        assertThat(r.count(StuckPayFileCause.REALTIME_OPERATING_HOURS_SKIP)).isZero();
    }

    @Test
    void missingFirmConf_isItsOwnCause() {
        // dispatch() throws IllegalStateException on this and the after-commit registrar swallows it, so
        // the row stays at '9' and will on every retry. A config fix, not a retry.
        targets(target("T1", "9", 30));
        when(firmConfRepository.findById(any())).thenReturn(Optional.empty());

        StuckPayFileReport r = service.monitorOnce();

        assertThat(r.count(StuckPayFileCause.REALTIME_NO_FIRM_CONF)).isEqualTo(1);
        // No VAN_ORG_CD is resolvable in this state, so the alert is skipped rather than sent blind —
        // the per-row WARN logs are the signal. Asserted so a future change does not start alerting
        // with a null VAN.
        assertThat(r.alerted()).isFalse();
        verify(opsAlertService, never()).notify(any(), any());
    }

    @Test
    void operatingHoursSkip_isReportedRatherThanFilteredOut() {
        // SKIP is the ONLY operating-hours verdict that leaves a row at '9' (CUTOFF and NO_CRITERIA both
        // write '3'). It is legitimate at the moment of evaluation but NOT self-healing in the TO-BE:
        // the after-commit hook already ran and no scheduled job re-drives the 202. So it is reported.
        targets(target("T1", "9", 30));
        when(operatingHoursService.evaluateRealtime(anyString(), any()))
                .thenReturn(OperatingHoursDecision.SKIP);

        StuckPayFileReport r = service.monitorOnce();

        assertThat(r.count(StuckPayFileCause.REALTIME_OPERATING_HOURS_SKIP)).isEqualTo(1);
    }

    @Test
    void everythingHealthyButStillWaiting_isUNKNOWN() {
        // Flag on, no firm-tran row, config present, window open — and yet the row never left WAIT.
        // Nothing in the code explains it, so say so rather than guessing.
        targets(target("T1", "9", 30));

        assertThat(service.monitorOnce().count(StuckPayFileCause.UNKNOWN)).isEqualTo(1);
    }

    /* ───────────────────── read-only ───────────────────── */

    @Test
    void neverMutatesAndNeverReDrives() {
        // The whole sweep is detection. StrandedRecoveryService owns re-driving and is gated separately;
        // folding a re-drive in here would put a mutating path behind a monitoring flag.
        targets(target("T1", "9", 30));

        service.monitorOnce();

        verify(payTargetRepository, never()).save(any());
        verify(payTargetRepository, never()).saveAll(any());
        verify(payTargetRepository, never()).claimForDispatch(any(), any(), any(), any());
        verify(firmTranRepository, never()).save(any());
    }

    @Test
    void sweepFailure_isSwallowed_soSchedulerThreadSurvives() {
        // The pay-target sweep shares the single-threaded scheduler pool with the dispatch jobs, so it
        // must not be able to kill the thread — same contract as the three sweeps before it.
        when(payTargetRepository.findStrandedPayTargets(anyString(), anyString(), any(Pageable.class)))
                .thenThrow(new RuntimeException("db down"));

        StuckPayFileReport r = service.monitorOnce();

        assertThat(r.totalStuck()).isZero();
        assertThat(r.alerted()).isFalse();
    }

    @Test
    void disabled_isANoOp() {
        ReflectionTestUtils.setField(service, "enabled", false);
        targets(target("T1", "9", 30));

        assertThat(service.monitorOnce().payTargetStuckCount()).isZero();
        org.mockito.Mockito.verifyNoInteractions(payTargetRepository, opsAlertService);
    }

    /* ───────────────────── alerting ───────────────────── */

    @Test
    void aRealtimeOnlyStrand_stillAlerts_resolvingTheVanFromThePayTarget() {
        // With both pay-file lists empty the pay-target's own TB_FIRM_CONF row is the ONLY source of a
        // VAN_ORG_CD. Before that fallback existed a real-time-only strand logged but never alerted.
        targets(target("T1", "9", 30));

        StuckPayFileReport r = service.monitorOnce();

        assertThat(r.alerted()).isTrue();
        verify(opsAlertService).notify(org.mockito.ArgumentMatchers.eq(VAN_ORG), anyString());
    }

    @Test
    void alertBodyCarriesThePayTargetTotalAndItsOwnThreshold() {
        targets(target("T1", "9", 30));

        service.monitorOnce();

        org.mockito.ArgumentCaptor<String> body = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(opsAlertService).notify(anyString(), body.capture());
        assertThat(body.getValue())
                .contains("WAIT_PAY_TARGET_TOTAL : 1")
                // Both thresholds are reported, so an operator reading the alert can tell which window
                // produced which count instead of assuming one number governs everything.
                .contains("PAY_TARGET_THRESHOLD_MIN : 5")
                .contains("THRESHOLD_MIN : 15");
    }
}
