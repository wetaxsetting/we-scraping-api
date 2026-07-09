package com.kosign.wecms.common.i18n;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for {@link RelativeTimeFormatter} — the server-side mirror of
 * the frontend {@code humanizeDatetime}.
 *
 * <p>The {@link MessageResolver} is stubbed to echo {@code key} (no args)
 * or {@code key|arg} (one arg) so the assertions check which bucket the
 * formatter picked, independent of the actual localized strings.
 */
class RelativeTimeFormatterTest {

    private static final DateTimeFormatter DTM_14 =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneOffset.UTC);

    private final MessageResolver messages = mock(MessageResolver.class);
    private final RelativeTimeFormatter fmt = new RelativeTimeFormatter(messages);

    RelativeTimeFormatterTest() {
        lenient().when(messages.resolve(any(String.class))).thenAnswer(i -> i.getArgument(0));
        lenient().when(messages.resolve(any(String.class), any(Object[].class)))
                .thenAnswer(i -> {
                    // Mockito flattens varargs into getArguments():
                    // [code, arg0, arg1, ...]. Echo code|arg0 so the test
                    // can assert which bucket + count the formatter chose.
                    Object[] all = i.getArguments();
                    return all.length <= 1 ? all[0] : all[0] + "|" + all[1];
                });
    }

    /** Stored value is UTC wall-clock; a timestamp from a few seconds ago
     *  MUST read "just now" — NOT "7hr ago". This is the regression guard
     *  for the UTC-vs-local bug: a local parse would inflate the elapsed
     *  by the +7 (Asia/Ho_Chi_Minh) offset and bucket it into hours. */
    @Test
    void currentUtcTimestamp_isJustNow_notOffsetInflated() {
        String nowUtc = DTM_14.format(Instant.now().minusSeconds(10));
        assertThat(fmt.humanize(nowUtc)).isEqualTo("relative_time.just_now");
    }

    @Test
    void twoHoursAgo_isHoursBucket() {
        String t = DTM_14.format(Instant.now().minusSeconds(2 * 3600 + 30));
        assertThat(fmt.humanize(t)).isEqualTo("relative_time.hours_n|2");
    }

    @Test
    void oneDayAgo_isDay1() {
        String t = DTM_14.format(Instant.now().minusSeconds(25 * 3600));
        assertThat(fmt.humanize(t)).isEqualTo("relative_time.day_1");
    }

    @Test
    void minuteBucket_roundsDownToBand() {
        // 23 minutes ago → band 20
        String t = DTM_14.format(Instant.now().minusSeconds(23 * 60));
        assertThat(fmt.humanize(t)).isEqualTo("relative_time.minutes_n|20");
    }

    @Test
    void blankOrMalformed_isEmptyString() {
        assertThat(fmt.humanize(null)).isEmpty();
        assertThat(fmt.humanize("")).isEmpty();
        assertThat(fmt.humanize("not-a-date")).isEmpty();
        assertThat(fmt.humanize("2026061710")).isEmpty(); // 10 digits — neither 8 nor 14
    }

    @Test
    void dateOnly8Char_parsesAsUtcMidnight() {
        // yyyyMMdd today (UTC) → within the day → just now / minutes / hours,
        // never empty and never a negative-diff future bucket.
        String today = DateTimeFormatter.ofPattern("yyyyMMdd")
                .withZone(ZoneOffset.UTC).format(Instant.now());
        assertThat(fmt.humanize(today)).isNotEmpty();
    }
}
