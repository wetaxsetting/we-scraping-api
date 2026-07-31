package com.kosign.wecms.domain.payment.dispatch.service;

import com.kosign.wecms.domain.payment.dispatch.entity.TbFirmTran;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * D-1 regression suite — {@link ResultCheckService#selectAged} across every date/time boundary.
 *
 * <h2>What broke, and why 2060 passing tests missed it</h2>
 *
 * <p>{@code TB_FIRM_TRAN.TRAN_DT} is written in <b>UTC</b>: it is the originating
 * {@code TB_PAY_TARGET.TRAN_DT}, which legacy stamps from {@code DateTimeUtils.utcNow()}
 * ({@code PymnHelper.pymn_0001_001_c001:2197}) and the refactor's API-rail executors stamp the
 * same way ({@code ExpenseDispatchExecutor:150-152}). The result-check filter, however, computed
 * "today" in {@code Asia/Ho_Chi_Minh}. ICT is UTC+7, so for the 7 hours between 00:00 and 07:00
 * ICT the ICT date is one day ahead of the UTC date, every row dispatched in that window failed
 * the date filter, and — because the filter was today-only — it was never picked up again. The
 * row stayed at {@code PROC_STAT='6'} forever: a payment sent to the bank with no result
 * confirmation.
 *
 * <p>The old fixtures built their rows with the <i>ICT</i> date, which is the same string as the
 * UTC date for 17 hours a day. So the suite agreed with the bug instead of catching it. Every
 * fixture here is built the way production actually writes: {@code TRAN_DT} from UTC,
 * {@code TRAN_TM} / {@code RQ_DTTM} from ICT ({@code RealtimeDispatchService:146-148,234,240}).
 *
 * <h2>Legacy reference</h2>
 *
 * <p>{@code IDO.TB_FIRM_TRAN_R001.xml}:
 * {@code WHERE TRAN_DT = TO_CHAR(now(),'YYYYMMDD') AND PROC_STAT='6'
 * AND TRAN_TM <= TO_CHAR(now() - INTERVAL '2 minute','hh24miss')
 * AND TO_CHAR(now() - INTERVAL '2 minute','hh24miss') < '235800' AND TRAN_TP='202'}.
 * Legacy reads {@code TRAN_DT} with the <b>database's own</b> {@code now()} — the same clock its
 * writer used — which is the invariant restored here. The trailing {@code < '235800'} guard is
 * legacy's way of dodging the {@code HHmmss} midnight wrap by simply not running in the last two
 * minutes of the day; {@link ResultCheckService#isAged} removes the wrap instead, so that guard is
 * intentionally not reproduced (recorded as a TO-BE divergence in {@code runOnce}'s Javadoc).
 *
 * <p>The configurable-zone knob is a different concern and is covered by
 * {@code OperatingHoursServiceTest#operatingHoursZone_defaultsToIct_andIsOverridable} — the
 * {@code TRAN_DT} zone is deliberately <b>not</b> configurable, because it is fixed by the legacy
 * storage contract above.
 */
class ResultCheckSelectionBoundaryTest {

    private static final ZoneId ICT = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter DT8 = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter TM6 = DateTimeFormatter.ofPattern("HHmmss");
    private static final DateTimeFormatter DTTM14 = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /** The 2-minute aging window ({@code ResultCheckService.RESULT_CHECK_DELAY_MINUTES}). */
    private static final int DELAY_MIN = 2;

    /* ── fixtures that mirror production writes exactly ─────────────────────────── */

    /**
     * A pending 202 row as the dispatch path writes it at the given ICT wall-clock moment:
     * {@code TRAN_DT} = the UTC date of that instant, {@code TRAN_TM} / {@code RQ_DTTM} = ICT.
     */
    private static TbFirmTran dispatchedAtIct(String ictWallClock) {
        ZonedDateTime ict = LocalDateTime.parse(ictWallClock).atZone(ICT);
        TbFirmTran t = new TbFirmTran();
        t.setBizKey("VN_VNM0004_000001");
        t.setTranSeq("1");
        t.setTranTp("202");
        t.setProcStat("6");
        t.setTranDt(ict.withZoneSameInstant(ZoneOffset.UTC).toLocalDate().format(DT8));
        t.setTranTm(ict.format(TM6));
        t.setRqDttm(ict.format(DTTM14));
        return t;
    }

    /** Run the real selection as of the given ICT wall-clock moment (both clocks derived from it). */
    private static List<TbFirmTran> selectAtIct(String ictWallClock, TbFirmTran... rows) {
        ZonedDateTime ict = LocalDateTime.parse(ictWallClock).atZone(ICT);
        return ResultCheckService.selectAged(
                List.of(rows),
                ict.toLocalDateTime(),
                ict.withZoneSameInstant(ZoneOffset.UTC).toLocalDate());
    }

    /* ── 1. the D-1 defect itself ───────────────────────────────────────────────── */

    @Test
    @DisplayName("D-1: a row dispatched at 03:00 ICT is selected (UTC date is still the previous day)")
    void rowDispatchedInsideTheUtcOffsetWindow_isSelected() {
        // 03:00 ICT on the 29th = 20:00 UTC on the 28th → TRAN_DT = 20260728, ICT "today" = 20260729.
        TbFirmTran row = dispatchedAtIct("2026-07-29T03:00");
        assertThat(row.getTranDt()).isEqualTo("20260728");

        assertThat(selectAtIct("2026-07-29T03:03", row))
                .as("this is the exact row the ICT-today filter dropped for 7 hours a day")
                .containsExactly(row);
    }

    /* ── 2. the required ICT clock boundaries ───────────────────────────────────── */

    @ParameterizedTest(name = "dispatched {0} ICT, checked {1} ICT")
    @CsvSource({
            // 23:59 ICT — last minute of the ICT day (UTC still mid-afternoon, same UTC date)
            "2026-07-29T23:56, 2026-07-29T23:59",
            // 00:00 ICT — ICT date has rolled, UTC has not
            "2026-07-29T23:57, 2026-07-30T00:00",
            // 00:01 ICT
            "2026-07-29T23:58, 2026-07-30T00:01",
            // 06:59 ICT — last minute before the UTC/ICT dates realign
            "2026-07-30T06:55, 2026-07-30T06:59",
            // 07:00 ICT — UTC midnight; the dates realign here
            "2026-07-30T06:56, 2026-07-30T07:00",
            // straddling: dispatched before UTC midnight, checked after
            "2026-07-30T06:58, 2026-07-30T07:01",
    })
    void agedRowIsSelectedAtEveryIctBoundary(String dispatchedIct, String checkedIct) {
        TbFirmTran row = dispatchedAtIct(dispatchedIct);

        assertThat(selectAtIct(checkedIct, row)).containsExactly(row);
    }

    @ParameterizedTest(name = "checked at {0} ICT")
    @ValueSource(strings = {
            "2026-07-29T23:59", "2026-07-30T00:00", "2026-07-30T00:01",
            "2026-07-30T06:59", "2026-07-30T07:00",
    })
    void rowInsideTheDelayWindowIsNeverSelected(String checkedIct) {
        // Dispatched 1 minute ago — still inside the 2-minute window, on any clock.
        ZonedDateTime justNow = LocalDateTime.parse(checkedIct).atZone(ICT).minusMinutes(1);
        TbFirmTran fresh = dispatchedAtIct(justNow.toLocalDateTime().toString());

        assertThat(selectAtIct(checkedIct, fresh))
                .as("the aging window must still hold — the fix must not make the poll trigger-happy")
                .isEmpty();
    }

    /* ── 3. previous-day / month / year rollovers ───────────────────────────────── */

    @Test
    @DisplayName("an unresolved row from the previous UTC date is still selected")
    void previousDayUnresolvedRow_isSelected() {
        TbFirmTran row = dispatchedAtIct("2026-07-29T10:00");   // TRAN_DT = 20260729 (UTC)
        // Checked ~21h later, after the UTC date has rolled to the 30th.
        assertThat(selectAtIct("2026-07-30T08:00", row)).containsExactly(row);
    }

    @Test
    @DisplayName("month boundary: 31 Jul → 01 Aug")
    void monthBoundary() {
        TbFirmTran row = dispatchedAtIct("2026-07-31T23:58");
        assertThat(selectAtIct("2026-08-01T00:05", row)).containsExactly(row);
    }

    @Test
    @DisplayName("year boundary: 31 Dec → 01 Jan")
    void yearBoundary() {
        TbFirmTran row = dispatchedAtIct("2026-12-31T23:58");
        assertThat(selectAtIct("2027-01-01T00:05", row)).containsExactly(row);
    }

    @Test
    @DisplayName("leap-day boundary: 28 Feb → 29 Feb 2028")
    void leapDayBoundary() {
        TbFirmTran row = dispatchedAtIct("2028-02-28T23:58");
        assertThat(selectAtIct("2028-02-29T00:05", row)).containsExactly(row);
    }

    /* ── 4. the no-permanent-stranding property ─────────────────────────────────── */

    @Test
    @DisplayName("no dispatch minute in a 24h day can be stranded by a date boundary")
    void everyDispatchMinuteBecomesSelectable() {
        // Sweep all 1440 minutes of an ICT day (which spans two UTC dates) and assert each row is
        // picked up once its aging window elapses. This is the property the fix has to guarantee:
        // a successfully dispatched transaction can never be permanently invisible to result-check.
        ZonedDateTime start = LocalDateTime.parse("2026-07-29T00:00").atZone(ICT);
        for (int minute = 0; minute < 1440; minute++) {
            ZonedDateTime dispatchedAt = start.plusMinutes(minute);
            TbFirmTran row = dispatchedAtIct(dispatchedAt.toLocalDateTime().toString());
            ZonedDateTime checkAt = dispatchedAt.plusMinutes(DELAY_MIN + 1);

            assertThat(selectAtIct(checkAt.toLocalDateTime().toString(), row))
                    .as("dispatched %s ICT (TRAN_DT=%s), checked %s ICT",
                            dispatchedAt.toLocalTime(), row.getTranDt(), checkAt.toLocalTime())
                    .containsExactly(row);
        }
    }

    @Test
    @DisplayName("a row is selectable for a full day, not just for the minute after its window")
    void rowStaysSelectableUntilTheWindowMovesPast() {
        TbFirmTran row = dispatchedAtIct("2026-07-29T23:50");   // TRAN_DT = 20260729 (UTC)

        // Selectable immediately after aging, and still selectable many hours later — so a poll
        // that misses a pass (scheduler contention, node restart) does not lose the row.
        assertThat(selectAtIct("2026-07-29T23:53", row)).containsExactly(row);
        assertThat(selectAtIct("2026-07-30T02:00", row)).containsExactly(row);
        assertThat(selectAtIct("2026-07-30T12:00", row)).containsExactly(row);
    }

    /* ── 5. the bounded window (recovery job owns anything older) ────────────────── */

    @Test
    @DisplayName("rows older than the previous UTC date fall out of scope, not into a silent gap")
    void rowsOlderThanYesterdayAreOutOfScope() {
        TbFirmTran ancient = dispatchedAtIct("2026-07-20T10:00");

        // Deliberate: legacy selects today only; this pass selects today + yesterday. Anything
        // older is a stranded-record concern owned by StrandedRecoveryService (R-1), not by the
        // per-minute poll — widening further would re-scan the whole table every minute.
        assertThat(selectAtIct("2026-07-30T10:00", ancient)).isEmpty();
    }

    /* ── 6. tiered aging (legacy fallback for rows without RQ_DTTM) ─────────────── */

    @Test
    @DisplayName("tier 2: an earlier TRAN_DT is aged without any time compare (no RQ_DTTM)")
    void earlierTranDt_isAgedRegardlessOfTranTm() {
        // A late-evening row (TRAN_TM 235900) checked just after midnight: the legacy HHmmss
        // compare would reject it (235900 > 000300). Tier 1 accepts it on the date alone.
        TbFirmTran row = dispatchedAtIct("2026-07-29T23:59");
        row.setRqDttm(null);                       // force the legacy path
        assertThat(row.getTranTm()).isEqualTo("235900");

        assertThat(selectAtIct("2026-07-30T07:03", row))
                .as("no RQ_DTTM → falls through to the date shortcut; TRAN_DT 20260729 vs UTC today 20260730")
                .containsExactly(row);
    }

    @Test
    @DisplayName("tier 3: same-date rows with no RQ_DTTM still use the legacy HHmmss compare")
    void sameDateWithoutRqDttm_usesLegacyTranTmCompare() {
        TbFirmTran aged = dispatchedAtIct("2026-07-30T10:00");
        aged.setRqDttm("");
        TbFirmTran fresh = dispatchedAtIct("2026-07-30T10:09");
        fresh.setRqDttm("");
        fresh.setTranSeq("2");

        assertThat(selectAtIct("2026-07-30T10:10", aged, fresh))
                .as("10:00 is past the 2-minute window; 10:09 is not")
                .containsExactly(aged);
    }

    @Test
    @DisplayName("tier 1: RQ_DTTM is the authority and is checked before the date shortcut")
    void rqDttmTakesPrecedence() {
        TbFirmTran row = dispatchedAtIct("2026-07-30T10:00");
        // A TRAN_TM that would look fresh; RQ_DTTM is the authority and says otherwise.
        row.setTranTm("235959");

        assertThat(selectAtIct("2026-07-30T10:05", row)).containsExactly(row);
    }

    /* ── 7. defensive input ─────────────────────────────────────────────────────── */

    @Test
    @DisplayName("null / blank / padded TRAN_DT never throws and never leaks into the aged set")
    void malformedTranDtIsIgnored() {
        TbFirmTran nullDt = dispatchedAtIct("2026-07-30T10:00");
        nullDt.setTranDt(null);
        TbFirmTran blankDt = dispatchedAtIct("2026-07-30T10:00");
        blankDt.setTranDt("   ");
        TbFirmTran paddedDt = dispatchedAtIct("2026-07-30T10:00");
        paddedDt.setTranDt(" " + paddedDt.getTranDt() + " ");   // bpchar padding must still match

        List<TbFirmTran> selected = selectAtIct("2026-07-30T10:05", nullDt, blankDt, paddedDt);

        assertThat(selected).containsExactly(paddedDt);
    }

    @Test
    @DisplayName("an empty candidate list is a no-op")
    void emptyInput() {
        assertThat(ResultCheckService.selectAged(
                List.of(), LocalDateTime.parse("2026-07-30T10:00"), LocalDate.parse("2026-07-30")))
                .isEmpty();
    }
}
