package com.kosign.wecms.domain.weinvoice.repository;

import com.kosign.wecms.domain.weinvoice.common.NotificationKey;
import com.kosign.wecms.domain.weinvoice.common.NotificationReadState;
import com.kosign.wecms.domain.weinvoice.config.WeInvoiceDb;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Repository;

import java.sql.Array;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.LongSupplier;

/**
 * Bell notifications (record docs/features/addon-service/weinvoice/weinvoice-notifications-2026-11-12.md).
 *
 * <p>Nothing is stored per notification. One SQL statement aggregates, for the owner's active tax codes and
 * the last 30 days, one row per (type, MST, direction, UTC hour) from the engine tables — new invoices
 * ({@code invoices.first_seen_at}), XML downloaded ({@code invoice_xml.fetched_at}), original PDFs
 * ({@code invoice_pdf.fetched_at}) — plus one row per failing GDT connection and per failing mailbox.
 * The engine tables are only read. {@code winv_ntfc_read} holds what the owner has read.
 *
 * <p>{@code winv_mlbx_infm} (V2) may not exist yet on an environment: {@link #mailboxTableExists()} checks
 * with {@code to_regclass} and the mailbox part is left out of the SQL until it does.
 */
@Repository
public class WeInvoiceNotificationRepository {

    /** Re-check a missing V2 table this often (a DBA may apply it without a restart). */
    static final long MAILBOX_RECHECK_MS = 5 * 60_000L;

    /** NEW_INVOICES lists at most this many issue months (newest first). */
    static final int MAX_MONTHS = 3;

    private static final DateTimeFormatter DTM_14 = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /** Which notification types a query covers. */
    public enum Scope {
        /** Counts and errors. */
        ALL_TYPES,
        /** Only the two error (action) types. */
        ACTION_ONLY
    }

    /** One aggregated notification as read from SQL. {@code atDtm} = latest event, yyyyMMddHHmmss UTC. */
    public record BucketRow(
            NotificationKey.Type type,
            String mst,
            String engineDirection,
            String bucket,
            String atDtm,
            Long count,
            Long fromEmail,
            List<String> months,
            Long mailboxId,
            String address,
            String status,
            String message) {

        public NotificationKey key() {
            return new NotificationKey(type, mst, NotificationKey.wireDirection(engineDirection), bucket);
        }
    }

    /** Package-private for the mapping test. */
    static final RowMapper<BucketRow> MAPPER = (rs, n) -> {
        OffsetDateTime at = rs.getObject("at", OffsetDateTime.class);
        String atDtm = at != null
                ? at.withOffsetSameInstant(ZoneOffset.UTC).format(DTM_14)
                : rs.getString("at_dtm");
        Array months = rs.getArray("months");
        List<String> monthList = null;
        if (months != null) {
            Object raw = months.getArray();
            // newest first; at most MAX_MONTHS (sliced here: an SQL array slice "[1:3]" would be read by
            // NamedParameterJdbcTemplate as a named parameter ":3")
            monthList = raw instanceof String[] values
                    ? List.copyOf(Arrays.asList(values).subList(0, Math.min(MAX_MONTHS, values.length)))
                    : List.of();
        }
        return new BucketRow(
                NotificationKey.Type.valueOf(rs.getString("ntfc_type")),
                rs.getString("mst"),
                rs.getString("dir"),
                rs.getString("bucket"),
                atDtm,
                Rows.longOrNull(rs, "cnt"),
                Rows.longOrNull(rs, "from_email"),
                monthList,
                Rows.longOrNull(rs, "mailbox_id"),
                rs.getString("address"),
                rs.getString("status"),
                rs.getString("message"));
    };

    private final WeInvoiceDb db;
    private final LongSupplier clock;
    private volatile boolean mailboxTableSeen;
    private volatile long mailboxCheckedAt = Long.MIN_VALUE;

    @Autowired
    public WeInvoiceNotificationRepository(WeInvoiceDb db) {
        this(db, System::currentTimeMillis);
    }

    WeInvoiceNotificationRepository(WeInvoiceDb db, LongSupplier clock) {
        this.db = db;
        this.clock = clock;
    }

    /**
     * Aggregated notifications of the owner.
     *
     * @param mst        restrict to this tax code (already checked to be the owner's), {@code null} = all of them
     * @param unreadOnly count path: skip the events at or before the owner's read-all marker of each MST, so a
     *                   bell poll after "read all" only scans what is newer. Buckets left out this way are
     *                   exactly the ones the read rule would call read; month lists are not computed.
     */
    public List<BucketRow> buckets(String ownerId, String mst, Scope scope, boolean unreadOnly) {
        MapSqlParameterSource p = new MapSqlParameterSource("ownerId", ownerId);
        if (mst != null) p.addValue("mst", mst);
        String sql = bucketSql(mst != null, scope, unreadOnly, mailboxTableExists());
        return db.jdbc().query(db.sql(sql), p, MAPPER);
    }

    /** Rows of {@code winv_ntfc_read} for the owner (markers and ids). */
    public List<NotificationReadState.Row> reads(String ownerId) {
        return db.jdbc().query(db.sql("SELECT ntfc_key, read_dtm FROM {app}.winv_ntfc_read WHERE owner_id = :ownerId"),
                new MapSqlParameterSource("ownerId", ownerId),
                (rs, n) -> new NotificationReadState.Row(rs.getString("ntfc_key"), rs.getString("read_dtm")));
    }

    /**
     * Per-id rows older than 31 days name buckets that have left the 30-day window (a bucket starts at or
     * before the moment it is read), so they can never match again; markers are kept.
     */
    static final String PRUNE_SQL = """
            DELETE FROM {app}.winv_ntfc_read
             WHERE owner_id = :ownerId
               AND ntfc_key NOT LIKE '*%'
               AND read_dtm < to_char((now() - interval '31 days') AT TIME ZONE 'UTC', 'YYYYMMDDHH24MISS')
            """;

    /** Marks ids read (database clock; an id already read keeps its first time) and prunes old ids. */
    public void markRead(String ownerId, Collection<String> keys) {
        if (keys.isEmpty()) return;
        MapSqlParameterSource[] batch = keys.stream()
                .map(k -> new MapSqlParameterSource("ownerId", ownerId).addValue("key", k))
                .toArray(MapSqlParameterSource[]::new);
        db.tx().executeWithoutResult(s -> {
            db.jdbc().batchUpdate(db.sql("""
                    INSERT INTO {app}.winv_ntfc_read (owner_id, ntfc_key, read_dtm)
                    VALUES (:ownerId, :key, to_char(now() AT TIME ZONE 'UTC', 'YYYYMMDDHH24MISS'))
                    ON CONFLICT (owner_id, ntfc_key) DO NOTHING
                    """), batch);
            db.jdbc().update(db.sql(PRUNE_SQL), new MapSqlParameterSource("ownerId", ownerId));
        });
    }

    /**
     * "Read all": sets the marker ({@code *} or {@code *|<mst>}) to now (database clock, the same clock as
     * the engine's event times), and prunes the owner's old per-id rows ({@link #PRUNE_SQL}).
     */
    public void markAll(String ownerId, String markerKey) {
        MapSqlParameterSource p = new MapSqlParameterSource("ownerId", ownerId).addValue("key", markerKey);
        db.tx().executeWithoutResult(s -> {
            db.jdbc().update(db.sql("""
                    INSERT INTO {app}.winv_ntfc_read (owner_id, ntfc_key, read_dtm)
                    VALUES (:ownerId, :key, to_char(now() AT TIME ZONE 'UTC', 'YYYYMMDDHH24MISS'))
                    ON CONFLICT (owner_id, ntfc_key) DO UPDATE SET read_dtm = EXCLUDED.read_dtm
                    """), p);
            db.jdbc().update(db.sql(PRUNE_SQL), p);
        });
    }

    /**
     * Whether V2 ({@code winv_mlbx_infm}) is installed. A positive answer is kept for the life of the process;
     * a negative one is re-checked every {@link #MAILBOX_RECHECK_MS}.
     */
    boolean mailboxTableExists() {
        if (mailboxTableSeen) return true;
        long now = clock.getAsLong();
        if (mailboxCheckedAt != Long.MIN_VALUE && now - mailboxCheckedAt < MAILBOX_RECHECK_MS) return false;
        Boolean exists = db.jdbc().queryForObject("SELECT to_regclass(:t) IS NOT NULL",
                new MapSqlParameterSource("t", db.sql("{app}.winv_mlbx_infm")), Boolean.class);
        mailboxCheckedAt = now;
        if (Boolean.TRUE.equals(exists)) mailboxTableSeen = true;
        return mailboxTableSeen;
    }

    // ------------------------------------------------------------------ SQL

    /** Lower bound of every event filter: 30 days back. */
    private static final String WINDOW = "now() - interval '30 days'";
    /** Same bound as a 14-digit UTC dtm, for the varchar(14) error times. */
    private static final String WINDOW_DTM = "to_char((now() - interval '30 days') AT TIME ZONE 'UTC', 'YYYYMMDDHH24MISS')";
    /**
     * Count path: the later of the window and one second after the MST's read-all marker ({@code *} or
     * {@code *|<mst>}, whichever is newer). Markers are whole seconds; an event at 10:00:05.300 is covered
     * by a 10:00:05 marker, hence {@code >= marker + 1 s} for "newer than the marker".
     */
    private static final String SINCE_AFTER_MARKER = """
            GREATEST(now() - interval '30 days',
                     (SELECT to_timestamp(max(r.read_dtm), 'YYYYMMDDHH24MISS')::timestamp AT TIME ZONE 'UTC'
                        FROM {app}.winv_ntfc_read r
                       WHERE r.owner_id = c.owner_id AND r.ntfc_key IN ('*', '*|' || c.cmpn_mst))
                     + interval '1 second')""";

    private static final String NOT_UPLOADED_INVOICE = "COALESCE(i.list_raw ->> 'nguon', '') NOT IN ('upload', 'test_data')";

    /**
     * Assembles the aggregation. Every value is a bound parameter ({@code :ownerId}, {@code :mst}); the
     * flags only choose fixed fragments. Columns of every branch:
     * {@code ntfc_type, mst, dir, bucket, at, at_dtm, cnt, from_email, months, mailbox_id, address, status, message}.
     */
    static String bucketSql(boolean oneMst, Scope scope, boolean unreadOnly, boolean withMailbox) {
        StringBuilder s = new StringBuilder();
        s.append("WITH own AS (\n")
                .append("    SELECT c.cmpn_mst AS mst, c.conn_stts, c.conn_msg,\n")
                .append("           COALESCE(c.conn_chk_dtm, c.mod_dtm, c.reg_dtm) AS err_dtm,\n")
                .append("           ").append(unreadOnly ? SINCE_AFTER_MARKER : WINDOW).append(" AS since\n")
                .append("      FROM {app}.winv_cmpn_infm c\n")
                .append("     WHERE c.owner_id = :ownerId AND c.del_yn = 'N'");
        if (oneMst) s.append(" AND c.cmpn_mst = :mst");
        s.append("\n)\n");

        List<String> parts = new ArrayList<>();
        if (scope == Scope.ALL_TYPES) {
            String months = unreadOnly
                    ? "NULL::text[]"
                    : "(array_agg(DISTINCT to_char(i.ngay_lap, 'YYYY-MM') ORDER BY to_char(i.ngay_lap, 'YYYY-MM') DESC)"
                    + " FILTER (WHERE i.ngay_lap IS NOT NULL))";
            parts.add("""
                    SELECT 'NEW_INVOICES'::text AS ntfc_type, i.company_mst::text AS mst, i.direction::text AS dir,
                           to_char(date_trunc('hour', i.first_seen_at AT TIME ZONE 'UTC'), 'YYYYMMDDHH24') AS bucket,
                           max(i.first_seen_at) AS at, NULL::text AS at_dtm, count(*) AS cnt, NULL::bigint AS from_email,
                           %s AS months,
                           NULL::bigint AS mailbox_id, NULL::text AS address, NULL::text AS status, NULL::text AS message
                      FROM own o
                      JOIN {eng}.invoices i ON i.company_mst = o.mst
                     WHERE i.first_seen_at >= o.since
                       AND %s
                     GROUP BY 2, 3, 4""".formatted(months, NOT_UPLOADED_INVOICE));
            parts.add("""
                    SELECT 'XML_READY'::text, i.company_mst::text, i.direction::text,
                           to_char(date_trunc('hour', x.fetched_at AT TIME ZONE 'UTC'), 'YYYYMMDDHH24'),
                           max(x.fetched_at), NULL::text, count(*), NULL::bigint, NULL::text[],
                           NULL::bigint, NULL::text, NULL::text, NULL::text
                      FROM own o
                      JOIN {eng}.invoices i ON i.company_mst = o.mst
                      JOIN {eng}.invoice_xml x ON x.invoice_id = i.id
                     WHERE x.fetched_at >= o.since
                       AND %s
                     GROUP BY 2, 3, 4""".formatted(NOT_UPLOADED_INVOICE));
            parts.add("""
                    SELECT 'PDF_READY'::text, i.company_mst::text, i.direction::text,
                           to_char(date_trunc('hour', p.fetched_at AT TIME ZONE 'UTC'), 'YYYYMMDDHH24'),
                           max(p.fetched_at), NULL::text, count(*),
                           count(*) FILTER (WHERE p.source_id = 'email' OR p.source_id LIKE 'email:%'),
                           NULL::text[], NULL::bigint, NULL::text, NULL::text, NULL::text
                      FROM own o
                      JOIN {eng}.invoices i ON i.company_mst = o.mst
                      JOIN {eng}.invoice_pdf p ON p.invoice_id = i.id
                     WHERE p.fetched_at >= o.since
                       AND COALESCE(p.source_id, '') NOT IN ('upload', 'test_data')
                     GROUP BY 2, 3, 4""");
        }
        // Aliased: with Scope.ACTION_ONLY this is the first branch, and a UNION takes its column names
        // from the first branch — the mapper reads by name.
        parts.add("""
                SELECT 'GDT_LOGIN_ERROR'::text AS ntfc_type, o.mst::text AS mst, NULL::text AS dir,
                       o.err_dtm::text AS bucket, NULL::timestamptz AS at, o.err_dtm::text AS at_dtm,
                       NULL::bigint AS cnt, NULL::bigint AS from_email, NULL::text[] AS months,
                       NULL::bigint AS mailbox_id, NULL::text AS address, o.conn_stts::text AS status,
                       o.conn_msg::text AS message
                  FROM own o
                 WHERE o.conn_stts IN ('ERROR', 'EXPIRED')
                   AND o.err_dtm ~ '^[0-9]{14}$'
                   AND o.err_dtm >= %s""".formatted(WINDOW_DTM));
        if (withMailbox) {
            parts.add("""
                    SELECT 'MAILBOX_ERROR'::text, m.cmpn_mst::text, NULL::text, e.dtm || '.' || m.mlbx_seq,
                           NULL::timestamptz, e.dtm::text, NULL::bigint, NULL::bigint, NULL::text[],
                           m.mlbx_seq::bigint, m.mail_addr::text, m.conn_stts::text, m.conn_msg::text
                      FROM {app}.winv_mlbx_infm m
                     CROSS JOIN LATERAL (SELECT COALESCE(m.mod_dtm, m.reg_dtm) AS dtm) e
                     WHERE m.owner_id = :ownerId AND m.del_yn = 'N'
                       AND m.conn_stts IN ('ERROR', 'EXPIRED')
                       AND (m.cmpn_mst IN (SELECT o.mst FROM own o)%s)
                       AND e.dtm ~ '^[0-9]{14}$'
                       AND e.dtm >= %s""".formatted(oneMst ? "" : " OR m.cmpn_mst IS NULL", WINDOW_DTM));
        }
        s.append(String.join("\nUNION ALL\n", parts));
        return s.toString();
    }
}
