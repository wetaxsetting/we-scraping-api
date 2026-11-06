package com.kosign.wecms.domain.weinvoice.repository;

import com.kosign.wecms.domain.weinvoice.common.WeInvoiceTime;
import com.kosign.wecms.domain.weinvoice.config.WeInvoiceDb;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Mail the engine processed ({@code email_messages}, {@code email_files}, {@code email_links}),
 * scoped to tax codes.
 *
 * <p>The engine does not record which MST a mail was sent for, so a mail belongs to an MST
 * when one of its files or links was matched to an invoice of that MST, or — for an XML not
 * matched yet — when the buyer or seller MST read from that XML is the MST. Mails with
 * nothing usable (no file, no link) cannot be attributed and are not listed.
 */
@Repository
public class EmailRepository {

    private static final String MESSAGES = """
            WITH linked AS (
                SELECT ef.email_id
                  FROM {eng}.email_files ef
                  LEFT JOIN {eng}.invoices i ON i.id = ef.invoice_id
                 WHERE i.company_mst IN (:msts)
                    OR (ef.invoice_id IS NULL AND (ef.nmmst IN (:msts) OR ef.nbmst IN (:msts)))
                UNION
                SELECT el.email_id
                  FROM {eng}.email_links el
                  JOIN {eng}.invoices i ON i.id = el.invoice_id
                 WHERE i.company_mst IN (:msts)
            ), msgs AS (
                SELECT m.id, m.received_at, m.from_addr, m.subject, m.status, m.is_invoice,
                       CASE WHEN EXISTS (SELECT 1 FROM {eng}.email_files f WHERE f.email_id = m.id AND f.status = 'matched')
                              OR EXISTS (SELECT 1 FROM {eng}.email_links l
                                          WHERE l.email_id = m.id AND l.status = 'done' AND l.invoice_id IS NOT NULL)
                            THEN 'matched'
                            WHEN EXISTS (SELECT 1 FROM {eng}.email_files f WHERE f.email_id = m.id AND f.status = 'duplicate')
                            THEN 'duplicate'
                            WHEN EXISTS (SELECT 1 FROM {eng}.email_files f WHERE f.email_id = m.id AND f.status = 'unmatched')
                            THEN 'unmatched'
                            WHEN m.status = 'error' THEN 'error'
                            ELSE 'no_invoice' END AS result,
                       (SELECT min(f.invoice_id) FROM {eng}.email_files f
                         WHERE f.email_id = m.id AND f.invoice_id IS NOT NULL) AS invoice_id
                  FROM {eng}.email_messages m
                 WHERE m.id IN (SELECT email_id FROM linked)
            )
            """;

    private static final RowMapper<MessageRow> MAPPER = (rs, n) -> new MessageRow(
            rs.getLong("id"),
            WeInvoiceTime.iso(rs, "received_at"),
            rs.getString("from_addr"),
            rs.getString("subject"),
            rs.getString("status"),
            rs.getString("result"),
            Rows.longOrNull(rs, "invoice_id"));

    private final WeInvoiceDb db;

    public EmailRepository(WeInvoiceDb db) {
        this.db = db;
    }

    public List<MessageRow> list(List<String> msts, LocalDate from, LocalDate to, String result, int page, int size) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        String where = where(msts, from, to, result, p);
        p.addValue("limit", size).addValue("offset", (long) page * size);
        return db.jdbc().query(db.sql(MESSAGES + " SELECT * FROM msgs" + where
                + " ORDER BY received_at DESC NULLS LAST, id DESC LIMIT :limit OFFSET :offset"), p, MAPPER);
    }

    public long count(List<String> msts, LocalDate from, LocalDate to, String result) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        String where = where(msts, from, to, result, p);
        Long n = db.jdbc().queryForObject(db.sql(MESSAGES + " SELECT count(*) FROM msgs" + where), p, Long.class);
        return n == null ? 0L : n;
    }

    /** One message, only when it belongs to one of {@code msts}. */
    public Optional<MessageRow> find(List<String> msts, long id) {
        MapSqlParameterSource p = new MapSqlParameterSource("msts", msts).addValue("id", id);
        return db.jdbc().query(db.sql(MESSAGES + " SELECT * FROM msgs WHERE id = :id"), p, MAPPER).stream().findFirst();
    }

    public Map<Long, List<FileRow>> filesOf(List<Long> emailIds) {
        Map<Long, List<FileRow>> out = new HashMap<>();
        if (emailIds.isEmpty()) return out;
        db.jdbc().query(db.sql("""
                        SELECT email_id, id, filename, kind, status, invoice_id, note
                          FROM {eng}.email_files
                         WHERE email_id IN (:ids)
                         ORDER BY email_id, id
                        """),
                new MapSqlParameterSource("ids", emailIds),
                rs -> {
                    out.computeIfAbsent(rs.getLong("email_id"), k -> new ArrayList<>()).add(new FileRow(
                            rs.getLong("id"),
                            rs.getString("filename"),
                            rs.getString("kind"),
                            rs.getString("status"),
                            Rows.longOrNull(rs, "invoice_id"),
                            rs.getString("note")));
                });
        return out;
    }

    public List<LinkRow> links(long emailId) {
        return db.jdbc().query(db.sql("""
                        SELECT id, host, url, code, status, invoice_id, attempts, last_error
                          FROM {eng}.email_links
                         WHERE email_id = :id
                         ORDER BY id
                        """),
                new MapSqlParameterSource("id", emailId),
                (rs, n) -> new LinkRow(
                        rs.getLong("id"),
                        rs.getString("host"),
                        rs.getString("url"),
                        rs.getString("code"),
                        rs.getString("status"),
                        Rows.longOrNull(rs, "invoice_id"),
                        rs.getInt("attempts"),
                        rs.getString("last_error")));
    }

    private static String where(List<String> msts, LocalDate from, LocalDate to, String result, MapSqlParameterSource p) {
        p.addValue("msts", msts);
        StringBuilder w = new StringBuilder(" WHERE 1 = 1");
        if (from != null) {
            w.append(" AND received_at >= CAST(:from AS date)");
            p.addValue("from", from);
        }
        if (to != null) {
            w.append(" AND received_at < CAST(:to AS date) + 1");
            p.addValue("to", to);
        }
        if (result != null && !result.isBlank()) {
            w.append(" AND result = :result");
            p.addValue("result", result);
        }
        return w.toString();
    }

    /** {@code result}: matched | duplicate | unmatched | no_invoice | error; {@code engineStatus} as stored. */
    public record MessageRow(long id, String receivedAt, String from, String subject, String engineStatus,
                             String result, Long invoiceId) {
    }

    public record FileRow(long id, String name, String kind, String status, Long invoiceId, String note) {
    }

    public record LinkRow(long id, String host, String url, String code, String status, Long invoiceId,
                          int attempts, String lastError) {
    }
}
