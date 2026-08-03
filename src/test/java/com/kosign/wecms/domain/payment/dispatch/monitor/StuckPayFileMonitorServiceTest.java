package com.kosign.wecms.domain.payment.dispatch.monitor;

import com.kosign.wecms.domain.payment.dispatch.alert.OpsAlertService;
import com.kosign.wecms.domain.payment.dispatch.entity.TbPayFileData;
import com.kosign.wecms.domain.payment.dispatch.entity.TbPayFileMst;
import com.kosign.wecms.domain.payment.dispatch.repository.TbFirmConfRepository;
import com.kosign.wecms.domain.payment.dispatch.repository.TbPayFileDataRepository;
import com.kosign.wecms.domain.payment.dispatch.repository.TbPayFileMstRepository;
import com.kosign.wecms.domain.payment.dispatch.service.OperatingHoursDecision;
import com.kosign.wecms.domain.payment.dispatch.service.OperatingHoursService;
import com.kosign.wecms.domain.setting.firmbankingsettings.entity.TbFirmConf;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the read-only stuck-pay-file monitor.
 *
 * <p>The {@code findByProcStatAndProcDttmLessThan} stub applies the <b>real</b> predicate
 * ({@code procStat} equality + {@code procDttm < cutoff}) over a canned row set, so the
 * "younger row ignored" / "terminal row ignored" / "threshold configurable" cases are
 * genuinely exercised end-to-end rather than assumed — they depend on the service passing
 * the correct status code and computing the correct cutoff.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StuckPayFileMonitorServiceTest {

    private static final DateTimeFormatter DTTM14 = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final ZoneId ICT = ZoneId.of("Asia/Ho_Chi_Minh");

    /** 2026-07-28 18:00:00 ICT. */
    private static final Instant NOW = Instant.parse("2026-07-28T11:00:00Z");

    private static final String BIZ     = "VN_VNM0004_000001";
    private static final String SHINHAN = "970424";
    private static final String VAN_ORG = "GV00000001";

    @Mock private TbPayFileMstRepository  payFileMstRepository;
    @Mock private TbPayFileDataRepository payFileDataRepository;
    @Mock private TbFirmConfRepository    firmConfRepository;
    @Mock private OperatingHoursService   operatingHoursService;
    @Mock private OpsAlertService         opsAlertService;
    @Mock private com.kosign.wecms.domain.payment.dispatch.repository.TbFirmFileMstRepository firmFileMstRepository;
    @Mock private com.kosign.wecms.domain.payment.dispatch.repository.TbFirmBatApiTranRepository firmBatApiTranRepository;
    @Mock private com.kosign.wecms.domain.payment.dispatch.repository.TbPayTargetRepository payTargetRepository;
    @Mock private com.kosign.wecms.domain.payment.dispatch.repository.TbFirmTranRepository firmTranRepository;

    /** Advanceable clock so the re-alert heartbeat is testable. */
    private static final class MutableClock extends Clock {
        private Instant instant;
        private final ZoneId zone;
        MutableClock(Instant instant, ZoneId zone) { this.instant = instant; this.zone = zone; }
        @Override public ZoneId getZone() { return zone; }
        @Override public Clock withZone(ZoneId z) { return new MutableClock(instant, z); }
        @Override public Instant instant() { return instant; }
        void advance(Duration d) { this.instant = this.instant.plus(d); }
    }

    private MutableClock clock;
    private StuckPayFileMonitorService service;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(NOW, ICT);
        service = new StuckPayFileMonitorService(
                payFileMstRepository, payFileDataRepository, firmConfRepository,
                operatingHoursService, opsAlertService, clock,
                firmFileMstRepository, firmBatApiTranRepository,
                payTargetRepository, firmTranRepository);
        // Enable the monitor; leave the DISPATCH flag ON so classification reaches the
        // channel logic (the flag-off short-circuit has its own test).
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "thresholdMinutes", 15L);
        ReflectionTestUtils.setField(service, "reAlertMinutes", 60L);
        ReflectionTestUtils.setField(service, "maxAlertKeys", 20);
        ReflectionTestUtils.setField(service, "autoReceiveEnabled", true);

        TbFirmConf conf = org.mockito.Mockito.mock(TbFirmConf.class);
        when(conf.getVanOrgCd()).thenReturn(VAN_ORG);
        when(firmConfRepository.findById(any())).thenReturn(Optional.of(conf));
        when(operatingHoursService.evaluateBulk(anyString(), any()))
                .thenReturn(OperatingHoursDecision.DISPATCH);
    }

    /* ───────────────────────── fixtures ───────────────────────── */

    private String dttm(int minutesAgo) {
        return LocalDateTime.ofInstant(NOW, ICT).minusMinutes(minutesAgo).format(DTTM14);
    }

    private TbPayFileMst file(String fileSeqNo, String procStat, int ageMinutes) {
        TbPayFileMst m = new TbPayFileMst();
        m.setBizKey(BIZ);
        m.setTranDt("20260728");
        m.setFileSeqNo(fileSeqNo);
        m.setOutBankCd(SHINHAN);
        m.setWdrlActNo("1001");
        m.setBankPrtnId("PRTN1");
        m.setFundTp("0");
        m.setTranTp("1");
        m.setProcStat(procStat);
        m.setReqCnt(BigDecimal.ONE);
        m.setRegDttm(dttm(ageMinutes));
        m.setProcDttm(dttm(ageMinutes));
        return m;
    }

    /** Stub the finder with the real filtering predicate over {@code rows}. */
    private void rows(TbPayFileMst... rows) {
        List<TbPayFileMst> all = Arrays.asList(rows);
        when(payFileMstRepository.findStrandedPayFiles(anyString(), anyString(), any(org.springframework.data.domain.Pageable.class)))
                .thenAnswer(inv -> {
                    String procStat = inv.getArgument(0);
                    String cutoff = inv.getArgument(1);
                    List<TbPayFileMst> hit = new ArrayList<>();
                    for (TbPayFileMst r : all) {
                        if (procStat.equals(r.getProcStat())
                                && r.getProcDttm().compareTo(cutoff) < 0) {
                            hit.add(r);
                        }
                    }
                    return hit;
                });
    }

    private TbPayFileData detail(String antDivCd) {
        TbPayFileData d = new TbPayFileData();
        d.setBizKey(BIZ);
        d.setTranDt("20260728");
        d.setFileSeqNo("1");
        d.setSeq(BigDecimal.ONE);
        d.setAntDivCd(antDivCd);
        return d;
    }

    private void details(TbPayFileData... rows) {
        when(payFileDataRepository.findByBizKeyAndTranDtAndFileSeqNo(anyString(), anyString(), anyString()))
                .thenReturn(Arrays.asList(rows));
    }

    /* ───────────────────── threshold / eligibility ───────────────────── */

    @Test
    void rowYoungerThanThreshold_isIgnored() {
        rows(file("1", "9", 5));            // 5 min old, threshold 15
        details(detail("C"));

        StuckPayFileReport r = service.monitorOnce();

        assertThat(r.stuckCount()).isZero();
        assertThat(r.alerted()).isFalse();
        verifyNoInteractions(opsAlertService);
    }

    @Test
    void rowOlderThanThreshold_isDetected() {
        rows(file("1", "9", 30));           // 30 min old
        details(detail("C"));

        StuckPayFileReport r = service.monitorOnce();

        assertThat(r.stuckCount()).isEqualTo(1);
        assertThat(r.alerted()).isTrue();
        verify(opsAlertService).notify(any(), any());
    }

    @Test
    void thresholdIsConfigurable() {
        rows(file("1", "9", 30));           // 30 min old
        details(detail("C"));

        ReflectionTestUtils.setField(service, "thresholdMinutes", 60L);
        assertThat(service.monitorOnce().stuckCount())
                .as("30m-old row must NOT be stuck at a 60m threshold").isZero();

        ReflectionTestUtils.setField(service, "thresholdMinutes", 10L);
        assertThat(service.monitorOnce().stuckCount())
                .as("same row IS stuck at a 10m threshold").isEqualTo(1);
    }

    @Test
    void queryUsesWaitStatusAndThresholdCutoff() {
        rows(file("1", "9", 30));
        details(detail("C"));

        service.monitorOnce();

        // The bounded finder is now called once per swept status ('9' WAIT, then '6' claimed — H-3),
        // so the WAIT call is asserted specifically rather than as the only invocation.
        ArgumentCaptor<String> cutoff = ArgumentCaptor.forClass(String.class);
        verify(payFileMstRepository).findStrandedPayFiles(
                eq("9"), cutoff.capture(), any(org.springframework.data.domain.Pageable.class));
        assertThat(cutoff.getValue()).isEqualTo("20260728174500");   // 18:00 ICT − 15m

        // The claimed-'6' sweep shares the same cutoff — one threshold governs every sweep.
        verify(payFileMstRepository).findStrandedPayFiles(
                eq("6"), eq("20260728174500"), any(org.springframework.data.domain.Pageable.class));
    }

    @Test
    void completedAndFailedRows_areIgnored() {
        // '0' SUCC and '3' FAIL are terminal — always out of scope for every sweep.
        //
        // UPDATED 2026-07-29 (H-3): this test previously also asserted that a '6' row is ignored, on
        // the reasoning that '6' means "actively processing". That reasoning was the H-3 blind spot.
        // H-4's claim commits '9'→'6' in its own transaction BEFORE any file work, so a failure in
        // between leaves a permanently committed '6' that no sweep could see. A '6' older than the
        // threshold is therefore no longer assumed to be in flight — the age window is what separates
        // "processing right now" from "stranded", exactly as it does for '9'. The '6' case is asserted
        // in inFlightRowOlderThanThreshold_isReportedAsClaimedStranded below.
        rows(file("1", "0", 90), file("2", "3", 90));
        details(detail("C"));

        StuckPayFileReport r = service.monitorOnce();

        assertThat(r.stuckCount()).isZero();
        assertThat(r.claimedStuckCount()).isZero();
        verifyNoInteractions(opsAlertService);
    }

    @Test
    void recentInFlightRow_isNotReported() {
        // A '6' row INSIDE the age window is genuinely being processed right now — the post-commit
        // path owns it, and reporting it would be a false positive on every normal dispatch.
        rows(file("1", "6", 1));
        details(detail("C"));

        StuckPayFileReport r = service.monitorOnce();

        assertThat(r.claimedStuckCount()).isZero();
        verifyNoInteractions(opsAlertService);
    }

    @Test
    void inFlightRowOlderThanThreshold_isReportedAsClaimedStranded() {
        // H-3 / N-3: the state that was previously invisible. A pay-file claimed at '6' and still
        // there 90 minutes later is not "processing" — the dispatch died after the claim committed.
        rows(file("1", "6", 90));
        details(detail("C"));

        StuckPayFileReport r = service.monitorOnce();

        assertThat(r.claimedStuckCount()).isEqualTo(1);
        assertThat(r.stuckCount()).as("it is not a WAIT row and must not be counted as one").isZero();
        // No TB_FIRM_FILE_MST row was stubbed, so nothing left the building — the one state in this
        // group that is safe to re-drive after a manual reset.
        assertThat(r.count(StuckPayFileCause.CLAIMED_NO_FIRM_FILE)).isEqualTo(1);
    }

    /* ───────────────────── cause classification ───────────────────── */

    @Test
    void allNullDetailChannels_classifiedAsAllChannelsNull() {
        rows(file("1", "9", 30));
        details(detail(null), detail("   "));

        StuckPayFileReport r = service.monitorOnce();

        assertThat(r.count(StuckPayFileCause.ALL_CHANNELS_NULL)).isEqualTo(1);
    }

    @Test
    void unknownChannelCode_isClassified() {
        rows(file("1", "9", 30));
        details(detail("T"));               // HAENGSUNG raw passthrough, not E/V/C/N/S

        StuckPayFileReport r = service.monitorOnce();

        assertThat(r.count(StuckPayFileCause.UNKNOWN_CHANNEL_CODE)).isEqualTo(1);
    }

    @Test
    void unknownChannelWins_evenWhenAnotherRowResolves() {
        rows(file("1", "9", 30));
        details(detail("C"), detail("T"));

        assertThat(service.monitorOnce().count(StuckPayFileCause.UNKNOWN_CHANNEL_CODE)).isEqualTo(1);
    }

    @Test
    void noDetailRows_isClassified() {
        rows(file("1", "9", 30));
        details();                          // empty

        assertThat(service.monitorOnce().count(StuckPayFileCause.NO_DETAIL_ROWS)).isEqualTo(1);
    }

    @Test
    void operatingHoursSkip_isClassified() {
        rows(file("1", "9", 30));
        details(detail("C"));
        when(operatingHoursService.evaluateBulk(anyString(), any()))
                .thenReturn(OperatingHoursDecision.SKIP);

        assertThat(service.monitorOnce().count(StuckPayFileCause.OPERATING_HOURS_SKIP)).isEqualTo(1);
    }

    @Test
    void dispatchFlagDisabled_isClassifiedFirst_withoutReadingDetails() {
        ReflectionTestUtils.setField(service, "autoReceiveEnabled", false);
        rows(file("1", "9", 30));

        StuckPayFileReport r = service.monitorOnce();

        assertThat(r.count(StuckPayFileCause.DISPATCH_FLAG_DISABLED)).isEqualTo(1);
        // Short-circuits before the detail read and before the gate re-check.
        verifyNoInteractions(payFileDataRepository);
        verifyNoInteractions(operatingHoursService);
    }

    @Test
    void resolvedChannelInsideWindow_isUnknownCause() {
        rows(file("1", "9", 30));
        details(detail("C"));               // gate says DISPATCH (default stub) → not a gate problem
        assertThat(service.monitorOnce().count(StuckPayFileCause.UNKNOWN)).isEqualTo(1);
    }

    /* ───────────────────── alert de-duplication ───────────────────── */

    @Test
    void alert_sentOnce_thenSuppressedWhileStuckSetUnchanged() {
        rows(file("1", "9", 30));
        details(detail("C"));

        assertThat(service.monitorOnce().alerted()).isTrue();
        assertThat(service.monitorOnce().alerted()).as("2nd sweep suppressed").isFalse();
        assertThat(service.monitorOnce().alerted()).as("3rd sweep suppressed").isFalse();

        verify(opsAlertService, times(1)).notify(any(), any());
    }

    @Test
    void alert_reSent_whenStuckSetChanges() {
        rows(file("1", "9", 30));
        details(detail("C"));
        assertThat(service.monitorOnce().alerted()).isTrue();

        // A second file joins the stuck set → fingerprint changes → re-alert.
        rows(file("1", "9", 30), file("2", "9", 30));
        assertThat(service.monitorOnce().alerted()).isTrue();

        verify(opsAlertService, times(2)).notify(any(), any());
    }

    @Test
    void alert_reSent_afterHeartbeatInterval() {
        rows(file("1", "9", 30));
        details(detail("C"));

        assertThat(service.monitorOnce().alerted()).isTrue();
        clock.advance(Duration.ofMinutes(59));
        assertThat(service.monitorOnce().alerted()).as("59m < 60m re-alert").isFalse();
        clock.advance(Duration.ofMinutes(2));
        assertThat(service.monitorOnce().alerted()).as("61m ≥ 60m re-alert").isTrue();

        verify(opsAlertService, times(2)).notify(any(), any());
    }

    @Test
    void cleanSweep_resetsFingerprint_soRecurrenceAlertsImmediately() {
        rows(file("1", "9", 30));
        details(detail("C"));
        assertThat(service.monitorOnce().alerted()).isTrue();

        rows();                                     // condition clears
        assertThat(service.monitorOnce().stuckCount()).isZero();

        rows(file("1", "9", 30));                   // same file stuck again
        assertThat(service.monitorOnce().alerted())
                .as("recurrence must alert immediately, not wait for the heartbeat").isTrue();

        verify(opsAlertService, times(2)).notify(any(), any());
    }

    @Test
    void alertSkipped_whenNoVanOrgCdResolves() {
        rows(file("1", "9", 30));
        details(detail("C"));
        when(firmConfRepository.findById(any())).thenReturn(Optional.empty());

        StuckPayFileReport r = service.monitorOnce();

        assertThat(r.stuckCount()).isEqualTo(1);    // still detected + logged
        assertThat(r.alerted()).isFalse();
        verifyNoInteractions(opsAlertService);
    }

    /* ───────────────────── safety invariants ───────────────────── */

    @Test
    void disabled_isNoOp() {
        ReflectionTestUtils.setField(service, "enabled", false);
        rows(file("1", "9", 30));

        assertThat(service.monitorOnce().stuckCount()).isZero();
        verifyNoInteractions(payFileMstRepository);
        verifyNoInteractions(opsAlertService);
    }

    @Test
    void neverMutatesPaymentStatus() {
        rows(file("1", "9", 30));
        details(detail("C"));

        service.monitorOnce();

        // Monitoring only — no writes anywhere (requirement 5).
        verify(payFileMstRepository, never()).save(any());
        verify(payFileMstRepository, never()).saveAll(any());
        verify(payFileDataRepository, never()).save(any());
    }

    @Test
    void claimedStrandedSweep_neverResendsAndNeverWrites() {
        // H-3 / N-3 requirement 7: a claimed-'6' file may ALREADY have reached SFTP or EFS — the
        // upload happens before the 00301 call, and a lost confirmation is indistinguishable from a
        // lost request. So this sweep must never re-drive and never mutate: re-issuing a 00301 the
        // bank did accept would double-instruct a live payment batch.
        //
        // The 4.3 constraint is explicit that ambiguous post-egress states go to monitoring and manual
        // investigation, not to an automatic retry. This test is what stops a future "just add a retry
        // here" change from landing silently.
        rows(file("1", "6", 90));
        details(detail("C"));

        StuckPayFileReport r = service.monitorOnce();

        assertThat(r.claimedStuckCount()).as("it must still be REPORTED").isEqualTo(1);
        // …but nothing may be written or re-driven.
        verify(payFileMstRepository, never()).save(any());
        verify(payFileMstRepository, never()).saveAll(any());
        verify(payFileMstRepository, never()).claimForReception(any(), any(), any(), any());
        verify(payFileMstRepository, never())
                .failClaimedPayFile(any(), any(), any(), any(), any());
        verify(firmFileMstRepository, never()).save(any());
    }

    @Test
    void sweepFailure_isSwallowed_soSchedulerThreadSurvives() {
        doThrow(new RuntimeException("db down"))
                .when(payFileMstRepository).findStrandedPayFiles(anyString(), anyString(), any(org.springframework.data.domain.Pageable.class));

        StuckPayFileReport r = service.monitorOnce();

        assertThat(r.stuckCount()).isZero();
        assertThat(r.alerted()).isFalse();
    }

    @Test
    void multipleCauses_areCountedSeparately() {
        rows(file("1", "9", 30), file("2", "9", 30));
        // file 1 → unknown code, file 2 → all-null
        when(payFileDataRepository.findByBizKeyAndTranDtAndFileSeqNo(anyString(), anyString(), anyString()))
                .thenAnswer(inv -> "1".equals(inv.getArgument(2))
                        ? List.of(detail("T"))
                        : List.of(detail(null)));

        StuckPayFileReport r = service.monitorOnce();

        assertThat(r.stuckCount()).isEqualTo(2);
        assertThat(r.count(StuckPayFileCause.UNKNOWN_CHANNEL_CODE)).isEqualTo(1);
        assertThat(r.count(StuckPayFileCause.ALL_CHANNELS_NULL)).isEqualTo(1);
    }
}
