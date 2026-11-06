package com.kosign.wecms.domain.weinvoice.repository;

import com.kosign.wecms.domain.weinvoice.common.WeInvoiceTime;
import com.kosign.wecms.domain.weinvoice.config.WeInvoiceDb;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Aggregates of screen 03. Same derived columns as the list ({@link InvoiceQueryRepository#cte});
 * amounts in VND; cancelled and replaced invoices are left out, as the prototype does.
 */
@Repository
public class DashboardRepository {

    private final WeInvoiceDb db;

    public DashboardRepository(WeInvoiceDb db) {
        this.db = db;
    }

    /** Per month and direction: count, total and VAT. */
    public List<MonthRow> monthly(String ownerId, String mst, LocalDate from, LocalDate to) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        String sql = db.sql(InvoiceQueryRepository.cte(period(ownerId, mst, from, to), p) + """
                SELECT to_char(c.ngay_lap, 'YYYY-MM') AS ym, c.direction, count(*) AS n,
                       COALESCE(sum(c.tong_tien * c.vnd_rate), 0) AS total,
                       COALESCE(sum(c.tien_thue * c.vnd_rate), 0) AS tax
                  FROM conv c
                 WHERE c.tthai_eff NOT IN (4, 6) AND c.ngay_lap IS NOT NULL
                 GROUP BY 1, 2
                 ORDER BY 1, 2
                """);
        return db.jdbc().query(sql, p, (rs, n) -> new MonthRow(
                rs.getString("ym"), rs.getString("direction"), rs.getLong("n"),
                rs.getBigDecimal("total"), rs.getBigDecimal("tax")));
    }

    /** "Data status" box: coded without XML, without code and without XML, without PDF. */
    public DataStatusRow dataStatus(String ownerId, String mst, LocalDate from, LocalDate to) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        String sql = db.sql(InvoiceQueryRepository.cte(period(ownerId, mst, from, to), p) + """
                SELECT count(*) FILTER (WHERE NOT c.has_xml AND upper(left(c.khhdon, 1)) <> 'K') AS missing_xml,
                       count(*) FILTER (WHERE NOT c.has_xml AND upper(left(c.khhdon, 1)) = 'K') AS no_code_no_xml,
                       count(*) FILTER (WHERE NOT c.has_pdf) AS missing_pdf
                  FROM conv c
                 WHERE c.tthai_eff NOT IN (4, 6)
                """);
        return db.jdbc().queryForObject(sql, p, (rs, n) -> new DataStatusRow(
                rs.getLong("missing_xml"), rs.getLong("no_code_no_xml"), rs.getLong("missing_pdf")));
    }

    /** Top purchase partners by total (P2 in the Function List; cheap enough to ship now). */
    public List<PartnerRow> topSuppliers(String ownerId, String mst, LocalDate from, LocalDate to, int limit) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        String sql = db.sql(InvoiceQueryRepository.cte(period(ownerId, mst, from, to), p) + """
                SELECT c.ptnr_mst, max(c.ptnr_nm) AS ptnr_nm, count(*) AS n, COALESCE(sum(c.tong_tien * c.vnd_rate), 0) AS total
                  FROM conv c
                 WHERE c.direction = 'mua' AND c.tthai_eff NOT IN (4, 6)
                 GROUP BY c.ptnr_mst
                 ORDER BY total DESC
                 LIMIT :limit
                """);
        p.addValue("limit", limit);
        return db.jdbc().query(sql, p, (rs, n) -> new PartnerRow(
                rs.getString("ptnr_mst"), rs.getString("ptnr_nm"), rs.getLong("n"), rs.getBigDecimal("total")));
    }

    /** Last finished engine run for the MST (any trigger). */
    public Optional<LastRunRow> lastRun(String mst) {
        return db.jdbc().query(db.sql("""
                        SELECT j.finished_at, j.status
                          FROM {eng}.job_run_companies j
                         WHERE j.company_mst = :mst AND j.finished_at IS NOT NULL
                         ORDER BY j.finished_at DESC
                         LIMIT 1
                        """),
                        new MapSqlParameterSource("mst", mst),
                        (rs, n) -> new LastRunRow(WeInvoiceTime.iso(rs, "finished_at"), rs.getString("status")))
                .stream().findFirst();
    }

    private static InvoiceCriteria period(String ownerId, String mst, LocalDate from, LocalDate to) {
        return new InvoiceCriteria(ownerId, mst, null, from, to, null, null, null, List.of(), null, List.of(),
                null, List.of(), List.of(), null, false);
    }

    public record MonthRow(String month, String engineDirection, long count, BigDecimal total, BigDecimal tax) {
    }

    public record DataStatusRow(long missingXml, long noCodeNoXml, long missingPdf) {
    }

    public record PartnerRow(String taxId, String name, long count, BigDecimal total) {
    }

    public record LastRunRow(String finishedAt, String status) {
    }
}
