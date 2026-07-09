package com.kosign.wecms.common.i18n;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * Server-side "X ago" relative-time formatter — the Java mirror of the
 * frontend {@code lib/datetime.ts humanizeDatetime}. Used wherever the
 * backend itself renders a Last-Update value (e.g. the Excel export,
 * which is produced by Apache POI server-side and so cannot reuse the
 * frontend helper). Mirrors the {@code AmountFormatter.forDisplay} ↔
 * {@code lib/currency.ts} precedent: one identical formatting contract on
 * both sides so the screen, print, and download read the same text.
 *
 * <p><b>UTC.</b> WeCMS stores audit timestamps as {@code UTC.now()}
 * wall-clock (no zone marker — {@code OffsetDateTime.now(ZoneOffset.UTC)}).
 * The string is therefore parsed back as UTC and diffed against the
 * absolute now, so the elapsed time is correct regardless of the viewer's
 * zone. This deliberately diverges from the legacy
 * {@code common.app.humanizeDatetime} (which parsed with a local-zone
 * {@code moment(...)} and over-stated the elapsed time by the viewer's
 * UTC offset) — the data being UTC, the local parse was a bug.
 *
 * <p><b>Bucketing</b> is byte-identical to the frontend so the screen and
 * the spreadsheet never disagree:
 * <pre>
 *   diff &lt; 0                 → just_now (clock skew)
 *   months ≥ 1                → 1 → month_1 · &gt;3 → months_3plus · else months_n
 *   weeks  ≥ 1                → 1 → week_1 · else weeks_n
 *   days   ≥ 1                → 1 → day_1 · 6 → week_1 · else days_n
 *   hours  ≥ 1                → 1 → hour_1 · else hours_n
 *   minutes &gt; 0              → &lt;5 → just_now · else minutes_n(bucket)
 *   else                      → just_now
 * </pre>
 * minute buckets: 5-9→5, 10-19→10, 20-29→20, 30-39→30, 40-49→40, 50+→50.
 *
 * <p>Localised via {@link MessageResolver} against the request locale
 * ({@code relative_time.*} keys in {@code messages*.properties}). Empty /
 * malformed input → {@code ""} (the screen renders an empty cell).
 */
@Component
@RequiredArgsConstructor
public class RelativeTimeFormatter {

    private final MessageResolver messages;

    /** Render a legacy {@code yyyyMMdd[HHmmss]} UTC timestamp as a
     *  localised "X ago" string. Returns {@code ""} for null / blank /
     *  malformed input. */
    public String humanize(String value) {
        Instant t = parseUtc(value);
        if (t == null) return "";

        long diffMs = Instant.now().toEpochMilli() - t.toEpochMilli();
        if (diffMs < 0) return messages.resolve("relative_time.just_now"); // clock skew / future

        double minutes = diffMs / 60000.0;
        double hours   = minutes / 60.0;
        double days    = hours / 24.0;
        double weeks   = days / 7.0;
        double months  = days / 30.0;

        if (months >= 1) {
            int m = (int) Math.floor(months);
            if (m == 1) return messages.resolve("relative_time.month_1");
            if (m > 3)  return messages.resolve("relative_time.months_3plus");
            return messages.resolve("relative_time.months_n", String.valueOf(m));
        }
        if (weeks >= 1) {
            int w = (int) Math.floor(weeks);
            return w == 1
                    ? messages.resolve("relative_time.week_1")
                    : messages.resolve("relative_time.weeks_n", String.valueOf(w));
        }
        if (days >= 1) {
            int dn = (int) Math.floor(days);
            if (dn == 1) return messages.resolve("relative_time.day_1");
            if (dn == 6) return messages.resolve("relative_time.week_1");
            return messages.resolve("relative_time.days_n", String.valueOf(dn));
        }
        if (hours >= 1) {
            int h = (int) Math.floor(hours);
            return h == 1
                    ? messages.resolve("relative_time.hour_1")
                    : messages.resolve("relative_time.hours_n", String.valueOf(h));
        }
        if (minutes > 0) {
            int mn = (int) Math.floor(minutes);
            if (mn < 5) return messages.resolve("relative_time.just_now");
            return messages.resolve("relative_time.minutes_n", String.valueOf(minuteBucket(mn)));
        }
        return messages.resolve("relative_time.just_now");
    }

    /** Parse {@code yyyyMMdd} (8) or {@code yyyyMMddHHmmss} (14) as a UTC
     *  instant. Any other length / non-numeric / unparseable input → null. */
    private static Instant parseUtc(String value) {
        if (value == null) return null;
        String s = value.trim();
        if ((s.length() != 8 && s.length() != 14) || !s.chars().allMatch(Character::isDigit)) {
            return null;
        }
        try {
            int year  = Integer.parseInt(s.substring(0, 4));
            int month = Integer.parseInt(s.substring(4, 6));
            int day   = Integer.parseInt(s.substring(6, 8));
            int hour  = s.length() == 14 ? Integer.parseInt(s.substring(8, 10)) : 0;
            int min   = s.length() == 14 ? Integer.parseInt(s.substring(10, 12)) : 0;
            int sec   = s.length() == 14 ? Integer.parseInt(s.substring(12, 14)) : 0;
            return LocalDateTime.of(year, month, day, hour, min, sec).toInstant(ZoneOffset.UTC);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static int minuteBucket(int mn) {
        if (mn < 10) return 5;
        if (mn < 20) return 10;
        if (mn < 30) return 20;
        if (mn < 40) return 30;
        if (mn < 50) return 40;
        return 50;
    }
}
