package com.kosign.wecms.domain.weinvoice.common;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;

/**
 * Date / time conversions at the WeInvoice boundaries.
 *
 * <ul>
 *   <li>WeInvoice tables store audit / event times as {@code varchar(14)}
 *       {@code yyyyMMddHHmmss} in UTC (house convention, see V254).</li>
 *   <li>Engine tables use {@code timestamptz} and {@code date} (issue date already in
 *       Vietnam time).</li>
 *   <li>The API speaks ISO-8601: {@code yyyy-MM-dd} dates, {@code yyyy-MM} months and
 *       {@code 2026-11-02T08:00:00Z} instants.</li>
 * </ul>
 */
public final class WeInvoiceTime {

    private static final DateTimeFormatter DTM_14 = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private WeInvoiceTime() {
    }

    /** Current UTC time as {@code yyyyMMddHHmmss}. */
    public static String nowDtm() {
        return OffsetDateTime.now(ZoneOffset.UTC).format(DTM_14);
    }

    /** {@code yyyyMMddHHmmss} (UTC) → ISO instant; {@code null} / malformed → {@code null}. */
    public static String dtmToIso(String dtm) {
        if (dtm == null || dtm.length() != 14) return null;
        try {
            return LocalDateTime.parse(dtm, DTM_14).atOffset(ZoneOffset.UTC).format(ISO);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** {@code timestamptz} column → ISO instant in UTC, seconds precision. */
    public static String iso(ResultSet rs, String column) throws SQLException {
        OffsetDateTime t = rs.getObject(column, OffsetDateTime.class);
        return iso(t);
    }

    public static String iso(OffsetDateTime t) {
        return t == null ? null : t.withOffsetSameInstant(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS).format(ISO);
    }

    /** {@code date} column → {@code yyyy-MM-dd}. */
    public static String date(ResultSet rs, String column) throws SQLException {
        LocalDate d = rs.getObject(column, LocalDate.class);
        return d == null ? null : d.toString();
    }

    /** Seconds between two {@code yyyyMMddHHmmss} values, or {@code null} when either is missing. */
    public static Long secondsBetween(String startDtm, String endDtm) {
        if (startDtm == null || endDtm == null) return null;
        try {
            LocalDateTime a = LocalDateTime.parse(startDtm, DTM_14);
            LocalDateTime b = LocalDateTime.parse(endDtm, DTM_14);
            return ChronoUnit.SECONDS.between(a, b);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** Strict {@code yyyy-MM-dd} parse; {@code WBE_VALIDATION} wording comes from the caller. */
    public static LocalDate parseDate(String value, String field) {
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException | NullPointerException e) {
            throw new IllegalArgumentException(field + ": must be yyyy-MM-dd");
        }
    }

    /** Strict {@code yyyy-MM} parse. */
    public static YearMonth parseMonth(String value, String field) {
        try {
            return YearMonth.parse(value);
        } catch (DateTimeParseException | NullPointerException e) {
            throw new IllegalArgumentException(field + ": must be yyyy-MM");
        }
    }
}
