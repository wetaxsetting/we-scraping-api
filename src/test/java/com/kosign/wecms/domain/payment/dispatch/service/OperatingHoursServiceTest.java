package com.kosign.wecms.domain.payment.dispatch.service;

import com.kosign.wecms.domain.payment.dispatch.entity.TbBatchScheduleDtl;
import com.kosign.wecms.domain.payment.dispatch.entity.TbBatchScheduleInfo;
import com.kosign.wecms.domain.payment.dispatch.repository.TbBatchScheduleDtlRepository;
import com.kosign.wecms.domain.payment.dispatch.repository.TbBatchScheduleInfoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link OperatingHoursService} — the legacy {@code EXE_DIV}
 * operating-hours gate. Uses the package-private {@code nowHms} overloads with a
 * fixed {@code "120000"} so the wall-clock comparison is deterministic.
 */
@ExtendWith(MockitoExtension.class)
class OperatingHoursServiceTest {

    private static final String NOW = "120000"; // 12:00:00
    private static final String BANK = "970457";
    private static final String NAPAS_KEY = "NAPAS_REAL_TIME_BATCH";
    private static final String BATCH1_KEY = "FILE_DEPOSIT_TRANSFER_BATCH1";

    @Mock TbBatchScheduleInfoRepository infoRepository;
    @Mock TbBatchScheduleDtlRepository dtlRepository;
    @InjectMocks OperatingHoursService service;

    private TbBatchScheduleInfo info(String start, String end, String antWhereUseYn) {
        TbBatchScheduleInfo i = new TbBatchScheduleInfo();
        i.setBatchStartTm(start);
        i.setBatchEndTm(end);
        i.setAntWhereUseYn(antWhereUseYn);
        return i;
    }

    private TbBatchScheduleDtl dtl(String start, String end) {
        TbBatchScheduleDtl d = new TbBatchScheduleDtl();
        d.setBatchStartTm(start);
        d.setBatchEndTm(end);
        return d;
    }

    private void infoReturns(TbBatchScheduleInfo i) {
        lenient().when(infoRepository.findByBatchIdAndOutBankCd(eq(NAPAS_KEY), eq(BANK)))
                .thenReturn(Optional.ofNullable(i));
    }

    // ── INFO-level ────────────────────────────────────────────────────────────

    @Test
    void infoMissing_isNoCriteria() {
        infoReturns(null);
        assertThat(service.evaluateBulk(BANK, Set.of(), NOW)).isEqualTo(OperatingHoursDecision.NO_CRITERIA);
        assertThat(service.evaluateRealtime(BANK, "NAPAS", NOW)).isEqualTo(OperatingHoursDecision.NO_CRITERIA);
        verify(dtlRepository, never()).findByBatchIdAndOutBankCdAndAntDivCd(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
    }

    /* ── R-4: window boundary + zone semantics ──────────────────────────────────
     *
     * The stored HHmmss values are VIETNAM local time. Proven by legacy
     * PymnHelper.getCutoffConfig:3352-3374, whose request-time windows — evaluated in
     * Vietnam time via DateTimeUtils.gmtNowVanCenter() — are the SAME numbers as the
     * TB_BATCH_SCHEDULE_DTL rows:
     *     Shinhan NAPAS 00:05-23:45 == 000500-234500
     *     Woori   NAPAS 00:15-23:55 == 001500-235500
     *     Shinhan CITAD 09:00-15:30 == 090000-153000
     *     Woori   CITAD 08:30-17:40 == 083000-174000
     *     Woori   SWIFT 08:30-19:10 == 083000-191000
     * Legacy nonetheless evaluates them with TO_CHAR(NOW(),'HH24MISS') on a DB whose
     * TimeZone is Asia/Seoul (verified on JEX DEV 2026-07-29: current_setting('TimeZone')
     * = 'Asia/Seoul'), i.e. 2h ahead of ICT — so legacy closes the window 2h early. The
     * refactor evaluates in Asia/Ho_Chi_Minh, which makes the request-time and
     * dispatch-time gates agree. Intentional divergence, OperatingHoursService:68-83.
     *
     * These tests pin the boundary ARITHMETIC (inclusive start, inclusive end) so the
     * zone decision cannot silently shift a boundary. The zone itself is a config knob
     * (wecms.dispatch.operating-hours.zone) and is asserted separately below. ── */

    @Test
    void windowBoundaries_startIsInclusive_endIsInclusive() {
        // Shinhan CITAD 09:00:00-15:30:00 (JEX DEV row, both batch ids).
        assertThat(OperatingHoursService.exeDiv("090000", "153000", "085959"))
                .as("one second before start").isEqualTo(OperatingHoursService.ExeDiv.N);
        assertThat(OperatingHoursService.exeDiv("090000", "153000", "090000"))
                .as("exactly at start — inclusive").isEqualTo(OperatingHoursService.ExeDiv.Y);
        assertThat(OperatingHoursService.exeDiv("090000", "153000", "090001"))
                .as("one second after start").isEqualTo(OperatingHoursService.ExeDiv.Y);
        assertThat(OperatingHoursService.exeDiv("090000", "153000", "152959"))
                .as("one second before end").isEqualTo(OperatingHoursService.ExeDiv.Y);
        assertThat(OperatingHoursService.exeDiv("090000", "153000", "153000"))
                .as("exactly at end — inclusive, legacy uses < not <=")
                .isEqualTo(OperatingHoursService.ExeDiv.Y);
        assertThat(OperatingHoursService.exeDiv("090000", "153000", "153001"))
                .as("one second after end").isEqualTo(OperatingHoursService.ExeDiv.E);
    }

    @Test
    void windowBoundaries_oneMinuteEitherSide_ofBothEdges() {
        // Minute-granularity restatement — the form the business states cut-offs in.
        assertThat(OperatingHoursService.exeDiv("083000", "174000", "082900")).isEqualTo(OperatingHoursService.ExeDiv.N);
        assertThat(OperatingHoursService.exeDiv("083000", "174000", "083100")).isEqualTo(OperatingHoursService.ExeDiv.Y);
        assertThat(OperatingHoursService.exeDiv("083000", "174000", "173900")).isEqualTo(OperatingHoursService.ExeDiv.Y);
        assertThat(OperatingHoursService.exeDiv("083000", "174000", "174100")).isEqualTo(OperatingHoursService.ExeDiv.E);
    }

    @Test
    void nearMidnightWindow_doesNotWrap() {
        // Woori NAPAS 00:15:00-23:55:00. A lexicographic compare cannot express a
        // wrapping window, and legacy's SQL CASE has the same limitation — 00:00:00 is
        // before start ('N'), not "still inside yesterday's window".
        assertThat(OperatingHoursService.exeDiv("001500", "235500", "000000")).isEqualTo(OperatingHoursService.ExeDiv.N);
        assertThat(OperatingHoursService.exeDiv("001500", "235500", "001500")).isEqualTo(OperatingHoursService.ExeDiv.Y);
        assertThat(OperatingHoursService.exeDiv("001500", "235500", "235500")).isEqualTo(OperatingHoursService.ExeDiv.Y);
        assertThat(OperatingHoursService.exeDiv("001500", "235500", "235959")).isEqualTo(OperatingHoursService.ExeDiv.E);
    }

    @Test
    void sameStoredWindow_yieldsDifferentDecisionsInIctVsKst() {
        // The R-4 divergence, made explicit. Shinhan CITAD closes 15:30. At the instant
        // 2026-07-29T08:00:00Z it is 15:00 ICT (inside) but 17:00 KST (past cutoff).
        // Legacy — Asia/Seoul DB clock — would CUTOFF; the refactor — ICT — DISPATCHes.
        java.time.Instant instant = java.time.Instant.parse("2026-07-29T08:00:00Z");
        String ict = java.time.LocalTime.ofInstant(instant, java.time.ZoneId.of("Asia/Ho_Chi_Minh"))
                .format(java.time.format.DateTimeFormatter.ofPattern("HHmmss"));
        String kst = java.time.LocalTime.ofInstant(instant, java.time.ZoneId.of("Asia/Seoul"))
                .format(java.time.format.DateTimeFormatter.ofPattern("HHmmss"));
        assertThat(ict).isEqualTo("150000");
        assertThat(kst).isEqualTo("170000");

        assertThat(OperatingHoursService.exeDiv("090000", "153000", ict))
                .as("refactor / ICT — inside the Vietnam business window")
                .isEqualTo(OperatingHoursService.ExeDiv.Y);
        assertThat(OperatingHoursService.exeDiv("090000", "153000", kst))
                .as("legacy / KST — 2h early close")
                .isEqualTo(OperatingHoursService.ExeDiv.E);
    }

    @Test
    void operatingHoursZone_defaultsToIct_andIsOverridable() {
        // The knob ops would use to reproduce legacy exactly on a shared UAT/PROD DB.
        assertThat(org.springframework.test.util.ReflectionTestUtils
                .getField(service, "operatingHoursZone")).isEqualTo("Asia/Ho_Chi_Minh");

        org.springframework.test.util.ReflectionTestUtils
                .setField(service, "operatingHoursZone", "Asia/Seoul");
        // No exception + a well-formed HHmmss proves the override is honoured; the value
        // itself is wall-clock dependent so it is not asserted.
        infoReturns(info("000000", "235959", "N"));
        assertThat(service.evaluateBulk(BANK, Set.of("CITAD")))
                .isEqualTo(OperatingHoursDecision.DISPATCH);
        org.springframework.test.util.ReflectionTestUtils
                .setField(service, "operatingHoursZone", "Asia/Ho_Chi_Minh");
    }

    @Test
    void invalidZone_failsFastAtBeanCreation_notOncePerPayment() {
        // Before the @PostConstruct guard, a typo'd zone threw DateTimeException from nowHms()
        // on EVERY dispatch instead of at startup. Both dispatch registrars swallow per-row
        // failures so the payment silently stayed at PROC_STAT='9' with only a log line —
        // a config typo presenting as "payments never dispatch". Refusing to start is safer.
        org.springframework.test.util.ReflectionTestUtils
                .setField(service, "operatingHoursZone", "Asia/Hochiminh"); // missing underscores

        // Tightened 2026-07-29: validation is now a closed accepted-value set, so a typo is rejected by
        // the set check before any ZoneId parse is attempted — the failure therefore carries no
        // DateTimeException cause (it never gets that far). The property name and the offending value
        // still appear, which is what an operator needs. The set check is strictly stronger: it also
        // rejects zones that PARSE fine but are not approved (e.g. America/New_York), which the old
        // parse-only guard let through silently. See OperatingHoursZoneConsistencyTest.
        assertThatThrownBy(() -> service.validateOperatingHoursZone())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("wecms.dispatch.operating-hours.zone")
                .hasMessageContaining("Asia/Hochiminh")
                .hasMessageContaining("must be one of");

        org.springframework.test.util.ReflectionTestUtils
                .setField(service, "operatingHoursZone", "Asia/Ho_Chi_Minh");
    }

    @Test
    void bothSupportedZones_passValidation() {
        // The default and the documented legacy-parity override must both start cleanly.
        for (String zone : new String[] {"Asia/Ho_Chi_Minh", "Asia/Seoul"}) {
            org.springframework.test.util.ReflectionTestUtils
                    .setField(service, "operatingHoursZone", zone);
            assertThatCode(() -> service.validateOperatingHoursZone())
                    .as("zone %s must be accepted", zone)
                    .doesNotThrowAnyException();
        }
        org.springframework.test.util.ReflectionTestUtils
                .setField(service, "operatingHoursZone", "Asia/Ho_Chi_Minh");
    }

    @Test
    void vcb_hasNoInfoRow_bulkAndRealtime_areNoCriteria() {
        // VCB-specific pin of the generic infoMissing case above. Verified on JEX DEV
        // 2026-07-29: TB_BATCH_SCHEDULE_INFO holds exactly 4 rows — (NAPAS_REAL_TIME_BATCH,
        // FILE_DEPOSIT_TRANSFER_BATCH1) x (970424, 970457). There is NO 970436 row, and
        // TB_BATCH_SCHEDULE_DTL has none either.
        //
        // Legacy parity — a missing INFO row is an ERROR, not a pass, on BOTH legs:
        //   FILE_DEPOSIT_TRANSFER_BATCH1.java:284-291  BATCH_START_TM == null
        //       -> exeErrYN = true; exeYN = false;
        //          vrfi_msg = "No data to perform bulk transfer batch. Please check
        //                      TB_BATCH_SCHEDULE_INFO."
        //   NAPAS_REAL_TIME_BATCH.java:125-129         BATCH_START_TM == null
        //       -> exeErrYN = true; exeYN = false; -> TB_PAY_TARGET PROC_STAT = FAIL
        //
        // This is the ONLY schedule-lookup miss that fails. A missing *DTL* row differs by
        // leg (BATCH1 passes, NAPAS fails) and is covered by batch1_dtlMissing_isDispatch /
        // napas_dtlMissing_isNoCriteria.
        //
        // DECIDED 2026-07-29 — exact legacy parity, no VCB INFO row. A 970436 row had been
        // seeded on the refactor DEV DB (2026-07-28) and made VCB dispatch succeed, diverging
        // from legacy; it was rolled back by
        // Claude Code (AI)/sql/tb_batch_schedule_info_vcb_rollback_20260729.sql and the seed
        // script's VCB tuple is now commented out. Do NOT re-seed a 970436 INFO row to "make
        // VCB work" — VCB dispatch failing here IS the legacy behaviour, and reviving the row
        // is a PO (Mr. Jung) decision that would also need a fresh ANT_WHERE_USE_YN decision
        // (legacy supplies no value to copy). See docs/references/support-matrix.md §11a.
        String vcb = "970436";
        lenient().when(infoRepository.findByBatchIdAndOutBankCd(eq(NAPAS_KEY), eq(vcb)))
                .thenReturn(Optional.empty());

        assertThat(service.evaluateBulk(vcb, Set.of("CITAD"), NOW))
                .isEqualTo(OperatingHoursDecision.NO_CRITERIA);
        assertThat(service.evaluateRealtime(vcb, "NAPAS", NOW))
                .isEqualTo(OperatingHoursDecision.NO_CRITERIA);

        // Legacy never reaches the DTL lookup once INFO is absent.
        verify(dtlRepository, never()).findByBatchIdAndOutBankCdAndAntDivCd(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void infoAfterEnd_isCutoff() {
        infoReturns(info("000000", "010000", "Y")); // end 01:00 < now 12:00 → E
        assertThat(service.evaluateBulk(BANK, Set.of("EBANK"), NOW)).isEqualTo(OperatingHoursDecision.CUTOFF);
        assertThat(service.evaluateRealtime(BANK, "NAPAS", NOW)).isEqualTo(OperatingHoursDecision.CUTOFF);
    }

    @Test
    void infoBeforeStart_isSkip() {
        infoReturns(info("130000", "235959", "Y")); // start 13:00 > now 12:00 → N
        assertThat(service.evaluateBulk(BANK, Set.of("EBANK"), NOW)).isEqualTo(OperatingHoursDecision.SKIP);
        assertThat(service.evaluateRealtime(BANK, "NAPAS", NOW)).isEqualTo(OperatingHoursDecision.SKIP);
    }

    @Test
    void infoInWindow_antWhereUseNotY_isDispatch() {
        infoReturns(info("000000", "235959", "N")); // Y window, other-bank gate off
        assertThat(service.evaluateBulk(BANK, Set.of("EBANK"), NOW)).isEqualTo(OperatingHoursDecision.DISPATCH);
        assertThat(service.evaluateRealtime(BANK, "NAPAS", NOW)).isEqualTo(OperatingHoursDecision.DISPATCH);
    }

    // ── DTL-level ───────────────────────────────────────────────────────────

    @Test
    void batch1_dtlMissing_isDispatch() {
        infoReturns(info("000000", "235959", "Y"));
        when(dtlRepository.findByBatchIdAndOutBankCdAndAntDivCd(BATCH1_KEY, BANK, "EBANK"))
                .thenReturn(Optional.empty());
        assertThat(service.evaluateBulk(BANK, Set.of("EBANK"), NOW)).isEqualTo(OperatingHoursDecision.DISPATCH);
    }

    /* ── the missing-DTL-row rule, pinned INDEPENDENTLY OF SEED CONTENTS ────────
     *
     * Why this exists as its own test rather than leaning on batch1_dtlMissing_isDispatch above.
     *
     * The claim "a bulk file can dispatch at any hour, including across midnight" — which the FIX A /
     * FIX D rollover tests depend on for their reachability argument — was verified by QUERYING the
     * SEEDED DEV DATA: TB_BATCH_SCHEDULE_INFO is 000000-235959, and the BATCH1 DTL rows that exist
     * happen to be wide enough (Woori NAPAS 001500-235500) or absent. That is a fact about a DBA-run
     * seed script (Claude Code (AI)/sql/tb_batch_schedule_seed_refactor.sql), not about this code —
     * so a seed change could close the midnight window and no test would notice.
     *
     * What this pins instead is the RULE the seed cannot alter: for BATCH1, a channel with NO DTL row
     * passes unconditionally ('Y'), at every hour, for every channel — legacy operTimeChk
     * FILE_DEPOSIT_TRANSFER_BATCH1.java:814-817 `exeYNE = "Y"`. All rows come from mocks, so the
     * assertions hold whatever any environment's tables contain.
     *
     * The complementary NAPAS asymmetry (missing DTL = NO_CRITERIA = FAIL) is pinned in
     * napas_dtlMissing_isNoCriteria and re-asserted here side by side, because the two legs reading
     * the same absent row and reaching opposite conclusions is the part a reader will assume is a bug
     * and "tidy up". ── */

    /** The five channel names the bulk path can ever produce (R005 classification E/V/C/N/S). */
    private static final java.util.List<String> BULK_CHANNELS =
            java.util.List.of("EBANK", "VCB", "CITAD", "NAPAS", "SWIFT");

    /** Hours that matter: both midnight edges, plus midday as the control. */
    private static final java.util.List<String> CLOCK_SAMPLES =
            java.util.List.of("000000", "000210", "120000", "235830", "235959");

    @Test
    void batch1_missingDtlRow_passes_forEveryChannel_atEveryHour() {
        infoReturns(info("000000", "235959", "Y"));   // INFO open + other-bank gate ON → DTL consulted
        // EVERY BATCH1 channel lookup misses. This is the state a fresh / partially-seeded DB is in,
        // and the state DEV is in for any channel the seed script does not list.
        lenient().when(dtlRepository.findByBatchIdAndOutBankCdAndAntDivCd(
                        eq(BATCH1_KEY), eq(BANK), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(Optional.empty());

        for (String hms : CLOCK_SAMPLES) {
            for (String channel : BULK_CHANNELS) {
                assertThat(service.evaluateBulk(BANK, Set.of(channel), hms))
                        .as("BATCH1 %s at %s — a missing DTL row must pass ('Y'), legacy :814-817", channel, hms)
                        .isEqualTo(OperatingHoursDecision.DISPATCH);
            }
            // …and a multi-channel file, which is the shape branch-split batches actually take.
            assertThat(service.evaluateBulk(BANK, new java.util.LinkedHashSet<>(BULK_CHANNELS), hms))
                    .as("BATCH1 all five channels at %s — all rows missing must still pass", hms)
                    .isEqualTo(OperatingHoursDecision.DISPATCH);
        }
    }

    @Test
    void batch1_missingDtlRow_passesEvenWhenAnotherChannelsRowIsClosed() {
        // The precedence rule must not be reachable by ABSENCE. A present-and-closed channel decides
        // (any N → SKIP), but an absent one contributes nothing — it must not be treated as closed,
        // which is the failure mode that would silently shut the bulk rail on an unseeded DB.
        infoReturns(info("000000", "235959", "Y"));
        lenient().when(dtlRepository.findByBatchIdAndOutBankCdAndAntDivCd(BATCH1_KEY, BANK, "CITAD"))
                .thenReturn(Optional.empty());                       // absent
        lenient().when(dtlRepository.findByBatchIdAndOutBankCdAndAntDivCd(BATCH1_KEY, BANK, "EBANK"))
                .thenReturn(Optional.of(dtl("000000", "235959")));   // present + open

        assertThat(service.evaluateBulk(BANK,
                new java.util.LinkedHashSet<>(java.util.List.of("CITAD", "EBANK")), "235959"))
                .isEqualTo(OperatingHoursDecision.DISPATCH);
    }

    @Test
    void missingDtlRow_divergesByLeg_batch1Passes_napasFails() {
        // The asymmetry, stated in one place. Same absent row, opposite outcomes — deliberate legacy
        // behaviour (BATCH1 :814-817 'Y' vs NAPAS :155-160 no-criteria), not an inconsistency to fix.
        infoReturns(info("000000", "235959", "Y"));
        lenient().when(dtlRepository.findByBatchIdAndOutBankCdAndAntDivCd(
                        eq(BATCH1_KEY), eq(BANK), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(Optional.empty());
        lenient().when(dtlRepository.findByBatchIdAndOutBankCdAndAntDivCd(
                        eq(NAPAS_KEY), eq(BANK), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(Optional.empty());

        assertThat(service.evaluateBulk(BANK, Set.of("NAPAS"), NOW))
                .as("bulk / BATCH1 — missing row passes")
                .isEqualTo(OperatingHoursDecision.DISPATCH);
        assertThat(service.evaluateRealtime(BANK, "NAPAS", NOW))
                .as("realtime / NAPAS — missing row is no-criteria (the payment FAILs)")
                .isEqualTo(OperatingHoursDecision.NO_CRITERIA);
    }

    @Test
    void batch1_missingDtlRow_readsTheBatch1Key_notTheInfoKey() {
        // The split-key behaviour the seed review calls out: INFO is looked up under
        // NAPAS_REAL_TIME_BATCH while bulk DTL is looked up under FILE_DEPOSIT_TRANSFER_BATCH1
        // (verbatim legacy). If the bulk leg ever read DTL under the INFO key it would pick up the
        // REALTIME rows — whose windows are narrower AND whose missing-row semantics are the opposite.
        infoReturns(info("000000", "235959", "Y"));
        lenient().when(dtlRepository.findByBatchIdAndOutBankCdAndAntDivCd(
                        eq(BATCH1_KEY), eq(BANK), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(Optional.empty());

        assertThat(service.evaluateBulk(BANK, Set.of("CITAD"), NOW)).isEqualTo(OperatingHoursDecision.DISPATCH);

        verify(dtlRepository).findByBatchIdAndOutBankCdAndAntDivCd(BATCH1_KEY, BANK, "CITAD");
        verify(dtlRepository, never()).findByBatchIdAndOutBankCdAndAntDivCd(NAPAS_KEY, BANK, "CITAD");
    }

    @Test
    void napas_dtlMissing_isNoCriteria() {
        infoReturns(info("000000", "235959", "Y"));
        when(dtlRepository.findByBatchIdAndOutBankCdAndAntDivCd(NAPAS_KEY, BANK, "NAPAS"))
                .thenReturn(Optional.empty());
        assertThat(service.evaluateRealtime(BANK, "NAPAS", NOW)).isEqualTo(OperatingHoursDecision.NO_CRITERIA);
    }

    @Test
    void dtlAfterEnd_isCutoff() {
        infoReturns(info("000000", "235959", "Y"));
        when(dtlRepository.findByBatchIdAndOutBankCdAndAntDivCd(BATCH1_KEY, BANK, "EBANK"))
                .thenReturn(Optional.of(dtl("000000", "010000"))); // end < now → E
        assertThat(service.evaluateBulk(BANK, Set.of("EBANK"), NOW)).isEqualTo(OperatingHoursDecision.CUTOFF);
    }

    @Test
    void dtlBeforeStart_isSkip() {
        infoReturns(info("000000", "235959", "Y"));
        when(dtlRepository.findByBatchIdAndOutBankCdAndAntDivCd(NAPAS_KEY, BANK, "NAPAS"))
                .thenReturn(Optional.of(dtl("130000", "235959"))); // start > now → N
        assertThat(service.evaluateRealtime(BANK, "NAPAS", NOW)).isEqualTo(OperatingHoursDecision.SKIP);
    }

    @Test
    void dtlInWindow_isDispatch() {
        infoReturns(info("000000", "235959", "Y"));
        when(dtlRepository.findByBatchIdAndOutBankCdAndAntDivCd(BATCH1_KEY, BANK, "EBANK"))
                .thenReturn(Optional.of(dtl("000000", "235959"))); // in window → Y
        assertThat(service.evaluateBulk(BANK, Set.of("EBANK"), NOW)).isEqualTo(OperatingHoursDecision.DISPATCH);
    }

    // ── legacy NAPAS INTER short-circuit + BATCH1 channel precedence ──────────

    @Test
    void napas_interNetwork_skipsDtl_isDispatch() {
        infoReturns(info("000000", "235959", "Y"));
        assertThat(service.evaluateRealtime(BANK, "INTER", NOW)).isEqualTo(OperatingHoursDecision.DISPATCH);
        verify(dtlRepository, never()).findByBatchIdAndOutBankCdAndAntDivCd(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void batch1_nBeatsE_isSkip() {
        infoReturns(info("000000", "235959", "Y"));
        lenient().when(dtlRepository.findByBatchIdAndOutBankCdAndAntDivCd(BATCH1_KEY, BANK, "EBANK"))
                .thenReturn(Optional.of(dtl("000000", "010000"))); // E
        lenient().when(dtlRepository.findByBatchIdAndOutBankCdAndAntDivCd(BATCH1_KEY, BANK, "VCB"))
                .thenReturn(Optional.of(dtl("130000", "235959"))); // N
        // any N present → SKIP regardless of an E on another channel
        assertThat(service.evaluateBulk(BANK, new java.util.LinkedHashSet<>(java.util.List.of("EBANK", "VCB")), NOW))
                .isEqualTo(OperatingHoursDecision.SKIP);
    }

    @Test
    void batch1_antWhereUseY_noChannels_isSkip() {
        infoReturns(info("000000", "235959", "Y")); // window open, other-bank gate on
        // No recognised channel present → legacy never sets exeYN=true → silent skip.
        assertThat(service.evaluateBulk(BANK, Set.of(), NOW)).isEqualTo(OperatingHoursDecision.SKIP);
    }

    // ── ANT_DIV_CD canonicalisation on the realtime path ──────────────────────
    // TB_PAY_TARGET.ANT_DIV_CD is bpchar(5) and carries BOTH the 5-char names and
    // 1-char codes (verified on JEX DEV 2026-07-28: CITAD/NAPAS/INTER + 'E' + 'S').
    // Before canonicalisation the raw value was the schedule key, so 'N' could never
    // match the 'NAPAS' row and the payment was failed with NO_CRITERIA — observed in
    // live legacy data (tran_seq 13219/13220, ANT_DIV_CD='E', PROC_STAT='3').

    @Test
    void realtime_oneCharNapasAlias_resolvesToNapasRow() {
        infoReturns(info("000000", "235959", "Y"));
        when(dtlRepository.findByBatchIdAndOutBankCdAndAntDivCd(NAPAS_KEY, BANK, "NAPAS"))
                .thenReturn(Optional.of(dtl("000000", "235959")));
        // 'N' must hit the SAME schedule row as 'NAPAS'.
        assertThat(service.evaluateRealtime(BANK, "N", NOW)).isEqualTo(OperatingHoursDecision.DISPATCH);
    }

    @Test
    void realtime_oneCharCitadAlias_resolvesToCitadRow() {
        infoReturns(info("000000", "235959", "Y"));
        when(dtlRepository.findByBatchIdAndOutBankCdAndAntDivCd(NAPAS_KEY, BANK, "CITAD"))
                .thenReturn(Optional.of(dtl("000000", "010000"))); // end < now → E
        assertThat(service.evaluateRealtime(BANK, "C", NOW)).isEqualTo(OperatingHoursDecision.CUTOFF);
    }

    @Test
    void realtime_bpcharPaddedValue_isTrimmedBeforeLookup() {
        infoReturns(info("000000", "235959", "Y"));
        when(dtlRepository.findByBatchIdAndOutBankCdAndAntDivCd(NAPAS_KEY, BANK, "NAPAS"))
                .thenReturn(Optional.of(dtl("000000", "235959")));
        // bpchar(5) reads come back blank-padded; the raw value must not be used as the key.
        assertThat(service.evaluateRealtime(BANK, "N    ", NOW)).isEqualTo(OperatingHoursDecision.DISPATCH);
    }

    @Test
    void realtime_paddedInter_stillSkipsDtl() {
        infoReturns(info("000000", "235959", "Y"));
        assertThat(service.evaluateRealtime(BANK, "INTER", NOW)).isEqualTo(OperatingHoursDecision.DISPATCH);
        verify(dtlRepository, never()).findByBatchIdAndOutBankCdAndAntDivCd(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void realtime_unknownCode_stillFailsClosed() {
        infoReturns(info("000000", "235959", "Y"));
        // HAENGSUNG ERP passes raw codes through (documented unresolved 'T').
        // An unknown code must NOT be coerced onto a channel — it must keep missing
        // the lookup and keep failing, exactly as legacy did.
        when(dtlRepository.findByBatchIdAndOutBankCdAndAntDivCd(NAPAS_KEY, BANK, "T"))
                .thenReturn(Optional.empty());
        assertThat(service.evaluateRealtime(BANK, "T", NOW)).isEqualTo(OperatingHoursDecision.NO_CRITERIA);
    }
}
