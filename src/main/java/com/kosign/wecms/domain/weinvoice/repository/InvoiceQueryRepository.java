package com.kosign.wecms.domain.weinvoice.repository;

import com.kosign.wecms.domain.weinvoice.common.InvoiceCodes;
import com.kosign.wecms.domain.weinvoice.common.WeInvoiceTime;
import com.kosign.wecms.domain.weinvoice.config.WeInvoiceDb;
import com.kosign.wecms.domain.weinvoice.dto.IncompletePeriod;
import com.kosign.wecms.domain.weinvoice.dto.InvoiceFacets;
import com.kosign.wecms.domain.weinvoice.dto.InvoiceKpis;
import com.kosign.wecms.domain.weinvoice.dto.InvoiceRow;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Read side of the invoice list (screens 03, 04) over the engine tables.
 *
 * <p>Every query starts from the same two CTEs:
 * <ul>
 *   <li>{@code base} — the invoices of one MST with what the UI needs derived in one place:
 *       effective status ({@link InvoiceCodes#SQL_TTHAI_EFF}), XML / PDF presence (engine file
 *       or a WeInvoice upload), provider MST ({@code MSTTCGP} from the XML, else the GDT list),
 *       CQT code, currency ({@code DVTTe}, {@code VNĐ} → {@code VND}), exchange rate
 *       ({@code TGia}) and the partner seen from the working MST.</li>
 *   <li>{@code conv} — adds {@code vnd_rate}: 1 for VND, else the invoice rate (1 when the
 *       invoice carries none, so the amount is at least visible).</li>
 * </ul>
 * Engine values that only exist in JSONB ({@code list_raw}, {@code parsed}) are read here
 * rather than copied, so the backend never writes an engine table.
 */
@Repository
public class InvoiceQueryRepository {

    private static final String BASE_SELECT = """
                SELECT i.id, i.company_mst, i.direction, i.sub_type, i.nbmst, i.nbten, i.nmmst, i.nmten,
                       i.khmshdon, i.khhdon, i.shdon, i.ngay_lap, i.tien_chua_thue, i.tien_thue, i.tong_tien,
                       i.first_seen_at, x.ma_tra_cuu, x.lookup_url, i.list_raw->>'nguon' AS rec_src,
            """
            + "           " + InvoiceCodes.SQL_TTHAI_EFF + " AS tthai_eff,\n"
            + """
                       (x.invoice_id IS NOT NULL OR EXISTS (
                            SELECT 1 FROM {app}.winv_invc_file uf
                             WHERE uf.owner_id = :ownerId AND uf.invc_id = i.id
                               AND uf.file_kind = 'XML' AND uf.del_yn = 'N')) AS has_xml,
                       (p.invoice_id IS NOT NULL OR EXISTS (
                            SELECT 1 FROM {app}.winv_invc_file uf
                             WHERE uf.owner_id = :ownerId AND uf.invc_id = i.id
                               AND uf.file_kind = 'PDF' AND uf.del_yn = 'N')) AS has_pdf,
                       NULLIF(COALESCE(NULLIF(x.mst_tcgp, ''), i.list_raw->>'msttcgp'), '') AS prvd_mst,
                       COALESCE(NULLIF(x.parsed->>'ma_cqt', ''), NULLIF(i.list_raw->>'mhdon', '')) AS cqt_code,
                       CASE WHEN cc.raw_ccy IN ('', 'VND', 'VNĐ', 'Đ') THEN 'VND' ELSE cc.raw_ccy END AS ccy,
                       rr.raw_rate,
                       CASE WHEN i.direction = 'ban' THEN i.nmmst ELSE i.nbmst END AS ptnr_mst,
                       CASE WHEN i.direction = 'ban' THEN i.nmten ELSE i.nbten END AS ptnr_nm
                  FROM {eng}.invoices i
                  LEFT JOIN {eng}.invoice_xml x ON x.invoice_id = i.id
                  LEFT JOIN {eng}.invoice_pdf p ON p.invoice_id = i.id
                 CROSS JOIN LATERAL (
                       SELECT upper(btrim(COALESCE(NULLIF(x.parsed->'thong_tin_chung'->>'don_vi_tt', ''),
                                                   i.list_raw->>'dvtte', ''))) AS raw_ccy) cc
                 CROSS JOIN LATERAL (
                       SELECT COALESCE(
                                CASE WHEN x.parsed->'thong_tin_chung'->>'ty_gia' ~ '^[0-9]+(\\.[0-9]+)?$'
                                     THEN (x.parsed->'thong_tin_chung'->>'ty_gia')::numeric END,
                                CASE WHEN i.list_raw->>'tgia' ~ '^[0-9]+(\\.[0-9]+)?$'
                                     THEN (i.list_raw->>'tgia')::numeric END) AS raw_rate) rr
            """;

    private static final String CONV = """
            ), conv AS (
                SELECT b.*, CASE WHEN b.ccy = 'VND' THEN 1 ELSE COALESCE(NULLIF(b.raw_rate, 0), 1) END AS vnd_rate
                  FROM base b
            )
            """;

    /** Engine list types per direction (used for the coverage banner). */
    private static final List<String> PURCHASE_TYPES = List.of("mua", "mua-khma", "mua-mtt");
    private static final List<String> SALES_TYPES = List.of("ban", "ban-khma", "ban-mtt");

    /** Hard cap of {@link #ids} — "select all" over a larger set is refused by the service. */
    public static final int MAX_IDS = 20_000;

    private final WeInvoiceDb db;

    public InvoiceQueryRepository(WeInvoiceDb db) {
        this.db = db;
    }

    /** One page of rows. */
    public List<InvoiceRow> list(InvoiceCriteria c, int page, int size) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        String cte = cte(c, p);
        String where = filterWhere(c, p);
        String order = c.sort() == null ? "c.ngay_lap DESC NULLS LAST, c.id DESC" : c.sort().orderBy(c.sortDesc());
        p.addValue("limit", size).addValue("offset", (long) page * size);
        String sql = db.sql(cte + """
                SELECT c.*, pv.prvd_nm, pv.prvd_mst AS pv_mst, COALESCE(lc.lkup_cd, lc.lkup_url) AS manual_code,
                       (SELECT string_agg(t.tag_seq::text, ',' ORDER BY t.tag_seq)
                          FROM {app}.winv_invc_tag t
                         WHERE t.owner_id = :ownerId AND t.invc_id = c.id) AS tag_ids
                  FROM conv c
                  LEFT JOIN {app}.winv_prvd_infm pv ON pv.prvd_mst = split_part(c.prvd_mst, '-', 1)
                  LEFT JOIN {app}.winv_lkup_cd lc ON lc.owner_id = :ownerId AND lc.invc_id = c.id
                """ + where + " ORDER BY " + order + " LIMIT :limit OFFSET :offset");
        return db.jdbc().query(sql, p, ROW_MAPPER);
    }

    /** Count and totals of the whole filtered set (not just the page). */
    public InvoiceKpis kpis(InvoiceCriteria c) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        String sql = db.sql(cte(c, p) + """
                SELECT count(*) AS cnt,
                       COALESCE(sum(c.tien_chua_thue * c.vnd_rate) FILTER (WHERE c.tthai_eff NOT IN (4, 6)), 0) AS pre_tax,
                       COALESCE(sum(c.tien_thue * c.vnd_rate) FILTER (WHERE c.tthai_eff NOT IN (4, 6)), 0) AS tax,
                       COALESCE(sum(c.tong_tien * c.vnd_rate) FILTER (WHERE c.tthai_eff NOT IN (4, 6)), 0) AS total,
                       count(*) FILTER (WHERE NOT c.has_xml AND upper(left(c.khhdon, 1)) <> 'K') AS missing_xml,
                       count(*) FILTER (WHERE NOT c.has_xml AND upper(left(c.khhdon, 1)) = 'K') AS no_code_missing_xml,
                       count(*) FILTER (WHERE NOT c.has_pdf) AS missing_pdf,
                       count(*) FILTER (WHERE c.direction = 'ban') AS sales_count,
                       count(*) FILTER (WHERE c.direction = 'mua') AS purchase_count,
                       COALESCE(sum(c.tong_tien * c.vnd_rate)
                                FILTER (WHERE c.direction = 'ban' AND c.tthai_eff NOT IN (4, 6)), 0) AS sales_total,
                       COALESCE(sum(c.tong_tien * c.vnd_rate)
                                FILTER (WHERE c.direction = 'mua' AND c.tthai_eff NOT IN (4, 6)), 0) AS purchase_total
                  FROM conv c
                """ + filterWhere(c, p));
        return db.jdbc().queryForObject(sql, p, (rs, n) -> new InvoiceKpis(
                rs.getLong("cnt"),
                rs.getBigDecimal("pre_tax"),
                rs.getBigDecimal("tax"),
                rs.getBigDecimal("total"),
                rs.getLong("missing_xml"),
                rs.getLong("no_code_missing_xml"),
                rs.getLong("missing_pdf"),
                rs.getLong("sales_count"),
                rs.getLong("purchase_count"),
                rs.getBigDecimal("sales_total"),
                rs.getBigDecimal("purchase_total")));
    }

    /** Every matching id, newest first, at most {@code limit}. */
    public List<Long> ids(InvoiceCriteria c, int limit) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        String sql = db.sql(cte(c, p) + " SELECT c.id FROM conv c " + filterWhere(c, p)
                + " ORDER BY c.ngay_lap DESC NULLS LAST, c.id DESC LIMIT :limit");
        p.addValue("limit", limit);
        return db.jdbc().queryForList(sql, p, Long.class);
    }

    /**
     * Ids among {@code ids} that are coded (symbol C…), have no XML yet and are not cancelled —
     * the invoices "fetch missing XML" can still get from GDT.
     */
    public List<Long> fetchableXmlIds(String ownerId, String mst, List<Long> ids) {
        InvoiceCriteria c = new InvoiceCriteria(ownerId, mst, null, null, null, null, "missing", null,
                List.of(), null, List.of(), null, List.of(), ids, null, false);
        MapSqlParameterSource p = new MapSqlParameterSource();
        String sql = db.sql(cte(c, p) + " SELECT c.id FROM conv c " + filterWhere(c, p)
                + " AND upper(left(c.khhdon, 1)) <> 'K' AND c.tthai_eff <> 6 ORDER BY c.id");
        return db.jdbc().queryForList(sql, p, Long.class);
    }

    /** Provider / status / kind counts for the period and direction (other filters ignored). */
    public InvoiceFacets facets(InvoiceCriteria c) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        String sql = db.sql(cte(c, p) + """
                SELECT 'provider' AS facet, COALESCE(split_part(c.prvd_mst, '-', 1), 'no_code') AS k,
                       max(pv.prvd_nm) AS label, bool_or(pv.prvd_mst IS NOT NULL) AS known, count(*) AS n
                  FROM conv c
                  LEFT JOIN {app}.winv_prvd_infm pv ON pv.prvd_mst = split_part(c.prvd_mst, '-', 1)
                 GROUP BY 2
                UNION ALL
                SELECT 'status', c.tthai_eff::text, NULL, NULL, count(*) FROM conv c GROUP BY 2
                UNION ALL
                SELECT 'kind',
                       CASE WHEN upper(left(c.khhdon, 1)) = 'K' THEN 'no_code'
                            WHEN upper(substr(c.khhdon, 4, 1)) = 'M' THEN 'cash_register'
                            ELSE 'normal' END,
                       NULL, NULL, count(*)
                  FROM conv c GROUP BY 2
                """);
        List<InvoiceFacets.ProviderCount> providers = new ArrayList<>();
        List<InvoiceFacets.KeyCount> statuses = new ArrayList<>();
        List<InvoiceFacets.KeyCount> kinds = new ArrayList<>();
        db.jdbc().query(sql, p, rs -> {
            String facet = rs.getString("facet");
            String key = rs.getString("k");
            long n = rs.getLong("n");
            switch (facet) {
                case "provider" -> providers.add(new InvoiceFacets.ProviderCount(
                        key, rs.getString("label"), rs.getBoolean("known"), n));
                case "status" -> statuses.add(new InvoiceFacets.KeyCount(
                        InvoiceCodes.status(Integer.parseInt(key)), n));
                default -> kinds.add(new InvoiceFacets.KeyCount(key, n));
            }
        });
        providers.sort((a, b) -> Long.compare(b.count(), a.count()));
        return new InvoiceFacets(providers, statuses, kinds);
    }

    /**
     * Months of the period whose GDT list is not complete, from {@code winv_sync_cvrg} (written
     * by the engine). Empty while the engine does not write coverage yet.
     */
    public List<IncompletePeriod> incompletePeriods(String mst, String engineDirection, String fromYm, String toYm) {
        List<String> types = new ArrayList<>();
        if (engineDirection == null || "mua".equals(engineDirection)) types.addAll(PURCHASE_TYPES);
        if (engineDirection == null || "ban".equals(engineDirection)) types.addAll(SALES_TYPES);
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("mst", mst)
                .addValue("types", types)
                .addValue("fromYm", fromYm == null ? "000000" : fromYm)
                .addValue("toYm", toYm == null ? "999999" : toYm);
        return db.jdbc().query(db.sql("""
                SELECT prd_ym, sum(got_cnt) AS got, sum(gdt_cnt) AS total
                  FROM {app}.winv_sync_cvrg
                 WHERE cmpn_mst = :mst AND sub_type IN (:types)
                   AND prd_ym BETWEEN :fromYm AND :toYm AND cmpl_yn = 'N'
                 GROUP BY prd_ym
                 ORDER BY prd_ym
                """), p, (rs, n) -> {
            String ym = rs.getString("prd_ym");
            return new IncompletePeriod(ym.substring(0, 4) + "-" + ym.substring(4), intOrNull(rs, "got"), intOrNull(rs, "total"));
        });
    }

    // ---------------------------------------------------------------- SQL assembly

    /** {@code WITH base …, conv …} for the criteria's MST, direction, period and ids. */
    static String cte(InvoiceCriteria c, MapSqlParameterSource p) {
        StringBuilder w = new StringBuilder(" WHERE i.company_mst = :mst");
        p.addValue("mst", c.mst()).addValue("ownerId", c.ownerId());
        if (c.engineDirection() != null) {
            w.append(" AND i.direction = :dir");
            p.addValue("dir", c.engineDirection());
        }
        if (c.from() != null) {
            w.append(" AND i.ngay_lap >= :from");
            p.addValue("from", c.from());
        }
        if (c.to() != null) {
            w.append(" AND i.ngay_lap <= :to");
            p.addValue("to", c.to());
        }
        if (c.ids() != null && !c.ids().isEmpty()) {
            w.append(" AND i.id IN (:ids)");
            p.addValue("ids", c.ids());
        }
        return "WITH base AS (\n" + BASE_SELECT + w + "\n" + CONV;
    }

    /** {@code WHERE} over alias {@code c} (the {@code conv} CTE) for the list filters. */
    static String filterWhere(InvoiceCriteria c, MapSqlParameterSource p) {
        StringBuilder w = new StringBuilder(" WHERE 1 = 1");
        if (c.keyword() != null && !c.keyword().isBlank()) {
            w.append(" AND (c.nbten ILIKE :kw OR c.nmten ILIKE :kw OR c.nbmst ILIKE :kw OR c.nmmst ILIKE :kw")
                    .append(" OR c.khhdon ILIKE :kw OR CAST(c.shdon AS text) ILIKE :kw OR c.cqt_code ILIKE :kw)");
            p.addValue("kw", "%" + escapeLike(c.keyword().trim()) + "%");
        }
        if ("has".equals(c.xml())) w.append(" AND c.has_xml");
        if ("missing".equals(c.xml())) w.append(" AND NOT c.has_xml");
        if ("has".equals(c.pdf())) w.append(" AND c.has_pdf");
        if ("missing".equals(c.pdf())) w.append(" AND NOT c.has_pdf");
        if (c.tthais() != null && !c.tthais().isEmpty()) {
            w.append(" AND c.tthai_eff IN (:tthais)");
            p.addValue("tthais", c.tthais());
        }
        if ("normal".equals(c.kind())) {
            w.append(" AND upper(left(c.khhdon, 1)) <> 'K' AND upper(substr(c.khhdon, 4, 1)) <> 'M'");
        } else if ("cash_register".equals(c.kind())) {
            w.append(" AND upper(left(c.khhdon, 1)) <> 'K' AND upper(substr(c.khhdon, 4, 1)) = 'M'");
        } else if ("no_code".equals(c.kind())) {
            w.append(" AND upper(left(c.khhdon, 1)) = 'K'");
        }
        if (c.tagIds() != null && !c.tagIds().isEmpty()) {
            w.append(" AND EXISTS (SELECT 1 FROM {app}.winv_invc_tag t")
                    .append(" WHERE t.owner_id = :ownerId AND t.invc_id = c.id AND t.tag_seq IN (:tagIds))");
            p.addValue("tagIds", c.tagIds());
        }
        if (c.provider() != null && !c.provider().isBlank()) {
            switch (c.provider()) {
                case "no_code" -> w.append(" AND c.prvd_mst IS NULL");
                case "unknown" -> w.append(" AND c.prvd_mst IS NOT NULL AND NOT EXISTS (SELECT 1 FROM {app}.winv_prvd_infm pv2")
                        .append(" WHERE pv2.prvd_mst = split_part(c.prvd_mst, '-', 1))");
                default -> {
                    w.append(" AND split_part(c.prvd_mst, '-', 1) = :prvd");
                    p.addValue("prvd", c.provider().split("-")[0]);
                }
            }
        }
        if (c.excludeTaxIds() != null && !c.excludeTaxIds().isEmpty()) {
            w.append(" AND (c.ptnr_mst IS NULL OR c.ptnr_mst NOT IN (:excl))");
            p.addValue("excl", c.excludeTaxIds());
        }
        return w.toString();
    }

    static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static final RowMapper<InvoiceRow> ROW_MAPPER = (rs, n) -> {
        String symbol = rs.getString("khhdon");
        BigDecimal total = rs.getBigDecimal("tong_tien");
        BigDecimal vndRate = rs.getBigDecimal("vnd_rate");
        String providerMst = rs.getString("prvd_mst");
        boolean known = rs.getString("pv_mst") != null;
        boolean hasCode = rs.getString("manual_code") != null
                || notBlank(rs.getString("ma_tra_cuu")) || notBlank(rs.getString("lookup_url"));
        String tagIds = rs.getString("tag_ids");
        return new InvoiceRow(
                String.valueOf(rs.getLong("id")),
                InvoiceCodes.direction(rs.getString("direction")),
                WeInvoiceTime.date(rs, "ngay_lap"),
                String.valueOf(rs.getInt("khmshdon")),
                symbol,
                String.valueOf(rs.getLong("shdon")),
                rs.getString("nbmst"),
                rs.getString("nbten"),
                rs.getString("nmmst"),
                rs.getString("nmten"),
                rs.getString("ptnr_mst"),
                rs.getString("ptnr_nm"),
                rs.getString("ccy"),
                rs.getBigDecimal("raw_rate"),
                rs.getBigDecimal("tien_chua_thue"),
                rs.getBigDecimal("tien_thue"),
                total,
                total == null || vndRate == null ? null : total.multiply(vndRate),
                InvoiceCodes.status(rs.getInt("tthai_eff")),
                InvoiceCodes.kind(symbol),
                rs.getString("cqt_code"),
                rs.getString("prvd_nm"),
                providerMst,
                known,
                rs.getBoolean("has_xml"),
                rs.getBoolean("has_pdf"),
                providerMst != null && known && !hasCode,
                tagIds == null ? List.of() : Arrays.asList(tagIds.split(",")),
                InvoiceCodes.recordOrigin(rs.getString("rec_src")),
                WeInvoiceTime.iso(rs, "first_seen_at"));
    };

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static Integer intOrNull(ResultSet rs, String col) throws SQLException {
        long v = rs.getLong(col);
        return rs.wasNull() ? null : (int) v;
    }
}
