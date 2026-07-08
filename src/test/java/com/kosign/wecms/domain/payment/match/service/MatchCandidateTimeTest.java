package com.kosign.wecms.domain.payment.match.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P3 — unit test for the UTC→VN-local {@code HHmmss} conversion that drives the
 * manual candidate transfer-time floor (legacy {@code comm_0197}
 * {@code normalizeResult(sumr.getTrnfDtm())} → {@code substring(8,14)}).
 */
class MatchCandidateTimeTest {

    @Test
    void convertsUtcToVietnamLocalPlus7() {
        // 03:00:00 UTC → 10:00:00 Asia/Ho_Chi_Minh (+7)
        assertThat(MatchCandidateTime.localTransferTime("20260708030000")).isEqualTo("100000");
    }

    @Test
    void rollsOverMidnight_takingLocalTimeOfDay() {
        // 20:00:00 UTC → 03:00:00 next-day VN; the filter compares time-of-day only
        assertThat(MatchCandidateTime.localTransferTime("20260708200000")).isEqualTo("030000");
    }

    @Test
    void nullOrTooShort_returnsNull_soFilterIsSkipped() {
        assertThat(MatchCandidateTime.localTransferTime(null)).isNull();
        assertThat(MatchCandidateTime.localTransferTime("")).isNull();
        assertThat(MatchCandidateTime.localTransferTime("2026")).isNull();
        assertThat(MatchCandidateTime.localTransferTime("2026070803000")).isNull(); // 13 chars
    }

    @Test
    void unparseable_returnsNull() {
        assertThat(MatchCandidateTime.localTransferTime("XXXXXXXXXXXXXX")).isNull();
    }
}
