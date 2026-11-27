package com.kosign.wecms.domain.weinvoice.repository;

import com.kosign.wecms.domain.weinvoice.config.WeInvoiceDb;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Engine rows for the WeCMS Partner API. No tenant here: the caller is authenticated by
 * API key and names the tax code; the row only has to be one of the engine's companies.
 */
@Repository
public class PartnerInvoiceRepository {

    private static final String SELECT = """
            SELECT i.id, i.khmshdon, i.khhdon, i.shdon, i.ngay_lap, i.tong_tien, i.tien_chua_thue, i.tien_thue,
                   i.tthai, i.sub_type, i.nbmst, i.nbten, i.nmmst, i.nmten, i.list_raw::text AS list_raw,
                   x.parsed::text AS parsed
              FROM {eng}.invoices i
              LEFT JOIN {eng}.invoice_xml x ON x.invoice_id = i.id
            """;

    private static final RowMapper<PartnerRow> MAPPER = (rs, n) -> new PartnerRow(
            rs.getLong("id"),
            rs.getInt("khmshdon"),
            rs.getString("khhdon"),
            rs.getLong("shdon"),
            rs.getObject("ngay_lap", LocalDate.class),
            rs.getBigDecimal("tong_tien"),
            rs.getBigDecimal("tien_chua_thue"),
            rs.getBigDecimal("tien_thue"),
            Rows.intOrNull(rs, "tthai"),
            rs.getString("sub_type"),
            rs.getString("nbmst"),
            rs.getString("nbten"),
            rs.getString("nmmst"),
            rs.getString("nmten"),
            rs.getString("list_raw"),
            rs.getString("parsed"));

    private final WeInvoiceDb db;

    public PartnerInvoiceRepository(WeInvoiceDb db) {
        this.db = db;
    }

    public boolean companyExists(String mst) {
        Integer n = db.jdbc().queryForObject(db.sql("SELECT count(*) FROM {eng}.companies WHERE company_mst = :mst"),
                new MapSqlParameterSource("mst", mst), Integer.class);
        return n != null && n > 0;
    }

    public List<PartnerRow> page(String mst, String engineDirection, LocalDate from, LocalDate to, int page, int size) {
        return db.jdbc().query(db.sql(SELECT + """
                         WHERE i.company_mst = :mst AND i.direction = :dir AND i.ngay_lap BETWEEN :from AND :to
                         ORDER BY i.ngay_lap, i.id
                         LIMIT :limit OFFSET :offset
                        """),
                new MapSqlParameterSource()
                        .addValue("mst", mst)
                        .addValue("dir", engineDirection)
                        .addValue("from", from)
                        .addValue("to", to)
                        .addValue("limit", size)
                        .addValue("offset", (long) page * size),
                MAPPER);
    }

    public long count(String mst, String engineDirection, LocalDate from, LocalDate to) {
        Long n = db.jdbc().queryForObject(db.sql("""
                        SELECT count(*) FROM {eng}.invoices i
                         WHERE i.company_mst = :mst AND i.direction = :dir AND i.ngay_lap BETWEEN :from AND :to
                        """),
                new MapSqlParameterSource()
                        .addValue("mst", mst)
                        .addValue("dir", engineDirection)
                        .addValue("from", from)
                        .addValue("to", to), Long.class);
        return n == null ? 0L : n;
    }

    /** Invoices of {@code mst} / direction matching the given GDT keys (unknown keys are simply absent). */
    public List<PartnerRow> byKeys(String mst, String engineDirection, List<Key> keys) {
        if (keys.isEmpty()) return List.of();
        MapSqlParameterSource p = new MapSqlParameterSource().addValue("mst", mst).addValue("dir", engineDirection);
        StringBuilder tuples = new StringBuilder();
        for (int k = 0; k < keys.size(); k++) {
            Key key = keys.get(k);
            if (k > 0) tuples.append(", ");
            tuples.append("(:t").append(k).append(", upper(:s").append(k).append("), :n").append(k)
                    .append(", :m").append(k).append(")");
            p.addValue("t" + k, key.templateNo())
                    .addValue("s" + k, key.symbol())
                    .addValue("n" + k, key.number())
                    .addValue("m" + k, key.sellerTaxId());
        }
        return db.jdbc().query(db.sql(SELECT + " WHERE i.company_mst = :mst AND i.direction = :dir"
                + " AND (i.khmshdon, upper(i.khhdon), i.shdon, i.nbmst) IN (" + tuples + ") ORDER BY i.id"), p, MAPPER);
    }

    public record Key(int templateNo, String symbol, long number, String sellerTaxId) {
    }

    public record PartnerRow(long id, int templateNo, String symbol, long number, LocalDate issueDate,
                             BigDecimal total, BigDecimal preTax, BigDecimal tax, Integer tthai, String subType,
                             String sellerTaxId, String sellerName, String buyerTaxId, String buyerName,
                             String listRawJson, String parsedJson) {
    }
}
