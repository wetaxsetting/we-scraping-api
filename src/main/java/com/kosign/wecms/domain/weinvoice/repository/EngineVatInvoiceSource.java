package com.kosign.wecms.domain.weinvoice.repository;

import com.kosign.wecms.domain.vatdeclaration.dto.AnnexInvoiceProjection;
import com.kosign.wecms.domain.vatdeclaration.entity.VatDeclaration;
import com.kosign.wecms.domain.vatdeclaration.repository.AnnexTaxGroupProjection;
import com.kosign.wecms.domain.vatdeclaration.repository.VatSyncBucketProjection;
import com.kosign.wecms.domain.vatdeclaration.service.VatInvoiceSource;
import com.kosign.wecms.domain.weinvoice.common.InvoiceCodes;
import com.kosign.wecms.domain.weinvoice.config.WeInvoiceDb;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Invoices of a WeInvoice VAT declaration, read from the engine tables ({@code invoices}, {@code invoice_xml},
 * {@code invoice_details}) of the declaration's tax code ({@code VAT_DECL.TP_TAX_CODE}) — the twin of the WeCMS
 * {@code VatAnnexInvoiceRepository}, same result shapes.
 *
 * <p>Mapping (record docs/features/addon-service/weinvoice/weinvoice-vat-declaration-engine-invoices-2026-11-25.md):
 * <ul>
 *   <li>Direction {@code ban} = output ({@code I}), {@code mua} = input ({@code B}); period on {@code ngay_lap};
 *       effective {@code tthai} 4 (replaced) and 6 (cancelled) excluded — the engine codes of the WeCMS labels the
 *       service passes, which are therefore not read here.</li>
 *   <li>One row per (invoice, VAT rate), first source that exists: the XML per-rate summary
 *       ({@code thanh_toan.theo_thue_suat}, i.e. {@code THTTLTSuat}) → the XML lines grouped by {@code thue_suat}
 *       (discount lines subtracted, a missing line VAT = floor(base × rate)) → the header totals with the rate
 *       derived from VAT ÷ base (the WeCMS {@code HDR_RATE} buckets, PO 2026-08-22).</li>
 *   <li>Amounts × exchange rate when the invoice currency is not VND; VAT is the invoice's own VAT for both
 *       directions; the 8% reduction is floor(base × 2 / 100) as in WeCMS.</li>
 *   <li>Engine rows are never written: {@link #markProcessed} / {@link #clearProcessed} do nothing and an annex row
 *       reports "processed" exactly when its declaration is locked.</li>
 * </ul>
 */
@Repository
public class EngineVatInvoiceSource implements VatInvoiceSource {

    private static final String STATUS_LOCKED = "LOCKED";

    private static final String NUM = "^-?[0-9]+(\\.[0-9]+)?([eE][-+]?[0-9]+)?$";

    /** Text → numeric, NULL when it is not a number (JSON values come back as text). */
    private static String num(String expr) {
        return "(CASE WHEN " + expr + " ~ '" + NUM + "' THEN (" + expr + ")::numeric END)";
    }

    /** Percent of a rate string ({@code '8%'} → 8); non-numeric codes (KCT / KKKNT) yield 0. */
    private static final String LINE_PCT =
            "(CASE WHEN btrim(d.thue_suat) ~ '^[0-9]+(\\.[0-9]+)?%$' THEN replace(btrim(d.thue_suat), '%', '')::numeric ELSE 0 END)";

    /** Discount lines ({@code tinh_chat 3}) reduce the base whatever sign the XML used. */
    private static final String LINE_BASE =
            "(CASE WHEN d.tinh_chat = 3 THEN -abs(coalesce(d.thanh_tien, 0)) ELSE coalesce(d.thanh_tien, 0) END)";

    private static final String LINE_VAT =
            "(CASE WHEN d.tien_thue IS NOT NULL THEN (CASE WHEN d.tinh_chat = 3 THEN -abs(d.tien_thue) ELSE d.tien_thue END)"
                    + " ELSE floor(" + LINE_BASE + " * " + LINE_PCT + " / 100) END)";

    /** Header-only rate, same buckets as the WeCMS {@code HDR_RATE}. */
    private static final String HDR_RATE =
            "(CASE WHEN coalesce(inv.tien_thue, 0) = 0 OR coalesce(inv.tien_chua_thue, 0) = 0 THEN 'KCT'"
                    + " WHEN round(inv.tien_thue / inv.tien_chua_thue * 100) < 3 THEN '0%'"
                    + " WHEN round(inv.tien_thue / inv.tien_chua_thue * 100) < 7 THEN '5%'"
                    + " WHEN round(inv.tien_thue / inv.tien_chua_thue * 100) < 9 THEN '8%'"
                    + " ELSE '10%' END)";

    /** 01-1/GTGT section order (KKKNT, 0%, 5%, 8%, 10%, KCT, anything else last) — as the WeCMS {@code RATE_SEQ}. */
    private static final String RATE_SEQ =
            "(CASE p.rate WHEN 'KKKNT' THEN 1 WHEN '0%' THEN 2 WHEN '5%' THEN 3 WHEN '8%' THEN 4"
                    + " WHEN '10%' THEN 5 WHEN 'KCT' THEN 6 ELSE 99 END)";

    private static final String CURRENCY =
            "upper(coalesce(nullif(btrim(coalesce(x.parsed->'thong_tin_chung'->>'don_vi_tt', i.list_raw->>'dvtte')), ''), 'VND'))";

    /** Exchange rate to VND: 1 for VND, else {@code ty_gia} of the XML, else {@code tgia} of the list row, else 1. */
    private static final String FX =
            "(CASE WHEN " + CURRENCY + " IN ('VND', 'VNĐ', 'Đ') THEN 1"
                    + " ELSE coalesce("
                    + "   (CASE WHEN " + num("x.parsed->'thong_tin_chung'->>'ty_gia'") + " > 0 THEN "
                    + num("x.parsed->'thong_tin_chung'->>'ty_gia'") + " END),"
                    + "   (CASE WHEN " + num("i.list_raw->>'tgia'") + " > 0 THEN " + num("i.list_raw->>'tgia'") + " END),"
                    + "   1) END)";

    /**
     * The period's invoices folded to {@code p} = one row per (invoice, rate), plus {@code inv} for the header
     * columns. Filters {@code :rate} / {@code :search} apply to {@code p}.
     */
    private static final String PER_INV_RATE = """
            WITH inv AS (
                SELECT i.id, i.direction, i.khmshdon, i.khhdon, i.shdon, i.ngay_lap,
                       i.nbmst, i.nbten, i.nmmst, i.nmten, i.tien_chua_thue, i.tien_thue, x.parsed,
                       %s AS fx
                  FROM {eng}.invoices i
                  LEFT JOIN {eng}.invoice_xml x ON x.invoice_id = i.id
                 WHERE i.company_mst = :mst
                   AND i.ngay_lap BETWEEN :fromDt AND :toDt
                   AND i.direction IN (:directions)
                   AND %s NOT IN (4, 6)
            ),
            summ AS (
                SELECT inv.id, coalesce(btrim(ts->>'thue_suat'), '') AS rate,
                       coalesce(%s, 0) * inv.fx AS sply,
                       coalesce(%s, 0) * inv.fx AS vat,
                       'S' AS src
                  FROM inv
                  CROSS JOIN LATERAL jsonb_array_elements(
                       CASE WHEN jsonb_typeof(inv.parsed->'thanh_toan'->'theo_thue_suat') = 'array'
                            THEN inv.parsed->'thanh_toan'->'theo_thue_suat' ELSE '[]'::jsonb END) ts
            ),
            lines AS (
                SELECT inv.id, coalesce(btrim(d.thue_suat), '') AS rate,
                       sum(%s) * inv.fx AS sply,
                       sum(%s) * inv.fx AS vat,
                       'L' AS src
                  FROM inv
                  JOIN {eng}.invoice_details d ON d.invoice_id = inv.id
                 WHERE coalesce(d.tinh_chat, 1) IN (1, 3)
                   AND NOT EXISTS (SELECT 1 FROM summ WHERE summ.id = inv.id)
                 GROUP BY inv.id, coalesce(btrim(d.thue_suat), ''), inv.fx
            ),
            hdr AS (
                SELECT inv.id, %s AS rate,
                       coalesce(inv.tien_chua_thue, 0) * inv.fx AS sply,
                       coalesce(inv.tien_thue, 0) * inv.fx AS vat,
                       'H' AS src
                  FROM inv
                 WHERE NOT EXISTS (SELECT 1 FROM summ WHERE summ.id = inv.id)
                   AND NOT EXISTS (SELECT 1 FROM lines WHERE lines.id = inv.id)
            ),
            p AS (
                SELECT u.*, inv.direction
                  FROM (SELECT * FROM summ UNION ALL SELECT * FROM lines UNION ALL SELECT * FROM hdr) u
                  JOIN inv ON inv.id = u.id
                 WHERE (cast(:rate AS text) IS NULL OR u.rate = cast(:rate AS text))
                   AND (cast(:search AS text) IS NULL
                        OR inv.shdon::text LIKE concat('%%', cast(:search AS text), '%%')
                        OR lower(coalesce(inv.nbten, '') || ' ' || coalesce(inv.nmten, ''))
                           LIKE lower(concat('%%', cast(:search AS text), '%%'))
                        OR EXISTS (SELECT 1 FROM {eng}.invoice_details d
                                    WHERE d.invoice_id = u.id
                                      AND lower(coalesce(d.ten_hhdv, '')) LIKE lower(concat('%%', cast(:search AS text), '%%'))))
            )
            """.formatted(FX, InvoiceCodes.SQL_TTHAI_EFF,
            num("ts->>'thanh_tien'"), num("ts->>'tien_thue'"),
            LINE_BASE, LINE_VAT, HDR_RATE);

    private static final String REDUCED = "(CASE WHEN p.rate = '8%' THEN floor(p.sply * 2 / 100) ELSE 0 END)";

    static final String SQL_SYNC = PER_INV_RATE + """
                SELECT CASE WHEN p.direction = 'ban' THEN 'I' ELSE 'B' END AS doc_type, p.rate,
                       coalesce(sum(p.sply), 0) AS amt, coalesce(sum(p.vat), 0) AS tax_amt
                  FROM p
                 GROUP BY 1, 2
                """;

    static final String SQL_SALES_VAT = PER_INV_RATE + """
                SELECT p.rate, coalesce(sum(p.vat), 0) AS tax_amt
                  FROM p
                 GROUP BY p.rate
                """;

    static final String SQL_GROUPS = PER_INV_RATE + """
                SELECT p.rate AS group_cd, count(*) AS row_count,
                       coalesce(sum(p.sply), 0) AS total_sply, coalesce(sum(p.vat), 0) AS total_vat,
                       coalesce(sum(%s), 0) AS total_reduced
                  FROM p
                 GROUP BY p.rate
                 ORDER BY min(%s), p.rate
                """.formatted(REDUCED, RATE_SEQ);

    static final String SQL_ROWS = PER_INV_RATE + """
                SELECT p.id::text AS src_doc_id,
                       CASE WHEN p.direction = 'ban' THEN 'O' ELSE 'I' END AS io_dv,
                       inv.shdon::text AS inv_no,
                       inv.khmshdon::text || inv.khhdon AS inv_symbol,
                       to_char(inv.ngay_lap, 'YYYYMMDD') AS inv_dt,
                       CASE WHEN p.direction = 'ban' THEN nullif(inv.nmmst, '') ELSE inv.nbmst END AS trco_tax_code,
                       CASE WHEN p.direction = 'ban' THEN inv.nmten ELSE inv.nbten END AS trco_nm,
                       (SELECT string_agg(DISTINCT d.ten_hhdv, ', ')
                          FROM {eng}.invoice_details d
                         WHERE d.invoice_id = p.id AND coalesce(d.tinh_chat, 1) IN (1, 3)
                           AND coalesce(btrim(d.thue_suat), '') = p.rate) AS prod_nm,
                       p.sply AS sply_amt, p.rate AS vat_rate, p.vat AS vat_amt, %s AS reduced_amt,
                       (dense_rank() OVER (ORDER BY %s))::int AS vat_rate_order
                  FROM p
                  JOIN inv ON inv.id = p.id
                 ORDER BY %s, inv.ngay_lap, inv.shdon, p.id
                 OFFSET :offset LIMIT :size
                """.formatted(REDUCED, RATE_SEQ, RATE_SEQ);

    private final WeInvoiceDb db;

    public EngineVatInvoiceSource(WeInvoiceDb db) {
        this.db = db;
    }

    @Override
    public List<VatSyncBucketProjection> syncBuckets(VatDeclaration decl, List<String> docTypes,
                                                     List<String> excludedStatuses) {
        String sql = SQL_SYNC;
        return query(sql, params(decl, docTypes, null, null), (rs, n) -> new Bucket(
                rs.getString("doc_type"), rs.getString("rate"), rs.getBigDecimal("amt"), rs.getBigDecimal("tax_amt")));
    }

    @Override
    public List<VatSyncBucketProjection> salesVatByRate(VatDeclaration decl, List<String> excludedStatuses) {
        String sql = SQL_SALES_VAT;
        return query(sql, params(decl, List.of("I"), null, null), (rs, n) -> new Bucket(
                "I", rs.getString("rate"), BigDecimal.ZERO, rs.getBigDecimal("tax_amt")));
    }

    @Override
    public List<AnnexTaxGroupProjection> groupTotals(VatDeclaration decl, List<String> docTypes, String rate,
                                                     String search, List<String> excludedStatuses) {
        String sql = SQL_GROUPS;
        return query(sql, params(decl, docTypes, rate, search), (rs, n) -> new Group(
                rs.getString("group_cd"), rs.getLong("row_count"), rs.getBigDecimal("total_sply"),
                rs.getBigDecimal("total_vat"), rs.getBigDecimal("total_reduced")));
    }

    @Override
    public List<AnnexInvoiceProjection> findRows(VatDeclaration decl, List<String> docTypes, String rate,
                                                 String search, List<String> excludedStatuses, long offset, int size) {
        String sql = SQL_ROWS;
        MapSqlParameterSource params = params(decl, docTypes, rate, search)
                .addValue("offset", Math.max(offset, 0))
                .addValue("size", Math.max(size, 0));
        boolean locked = STATUS_LOCKED.equals(decl.getStatus());
        Long sid = locked ? decl.getSid() : null;
        String procYn = locked ? "Y" : "N";
        return query(sql, params, (rs, n) -> new Line(
                rs.getString("src_doc_id"), rs.getString("io_dv"), rs.getString("inv_no"),
                rs.getString("inv_symbol"), rs.getString("inv_dt"), rs.getString("trco_tax_code"),
                rs.getString("trco_nm"), rs.getString("prod_nm"), rs.getBigDecimal("sply_amt"),
                rs.getString("vat_rate"), rs.getBigDecimal("vat_amt"), rs.getBigDecimal("reduced_amt"),
                procYn, sid, rs.getInt("vat_rate_order")));
    }

    /** Engine rows are read-only for the API — nothing to stamp (see class comment). */
    @Override
    public void markProcessed(VatDeclaration decl, List<String> docTypes, List<String> excludedStatuses) {
        // intentionally empty
    }

    @Override
    public void clearProcessed(VatDeclaration decl) {
        // intentionally empty
    }

    // ----------------------------------------------------------------------------------------------- helpers

    private <T> List<T> query(String sql, MapSqlParameterSource params,
                              org.springframework.jdbc.core.RowMapper<? extends T> mapper) {
        db.requireEnabled();
        List<T> out = new ArrayList<>();
        out.addAll(db.jdbc().query(db.sql(sql), params, mapper));
        return out;
    }

    /** Declaration scope → SQL parameters; WeCMS doc types I / B become engine directions ban / mua. */
    static MapSqlParameterSource params(VatDeclaration decl, List<String> docTypes, String rate, String search) {
        List<String> directions = new ArrayList<>();
        for (String t : docTypes == null ? List.<String>of() : docTypes) {
            if ("I".equals(t)) directions.add("ban");
            else if ("B".equals(t)) directions.add("mua");
        }
        if (directions.isEmpty()) {
            directions.add("-");   // IN () is invalid SQL; matches nothing
        }
        return new MapSqlParameterSource()
                .addValue("mst", decl.getTpTaxCode())
                .addValue("fromDt", decl.getFromDt())
                .addValue("toDt", decl.getToDt())
                .addValue("directions", directions)
                .addValue("rate", rate)
                .addValue("search", search == null || search.isBlank() ? null : search.trim());
    }

    record Bucket(String docType, String taxRate, BigDecimal amt, BigDecimal taxAmt)
            implements VatSyncBucketProjection {
        @Override public String getDocType() { return docType; }
        @Override public String getTaxRate() { return taxRate; }
        @Override public BigDecimal getAmt() { return amt; }
        @Override public BigDecimal getTaxAmt() { return taxAmt; }
    }

    record Group(String groupCd, long rowCount, BigDecimal totalSplyAmt, BigDecimal totalVatAmt,
                 BigDecimal totalReducedAmt) implements AnnexTaxGroupProjection {
        @Override public String getGroupCd() { return groupCd; }
        @Override public long getRowCount() { return rowCount; }
        @Override public BigDecimal getTotalSplyAmt() { return totalSplyAmt; }
        @Override public BigDecimal getTotalVatAmt() { return totalVatAmt; }
        @Override public BigDecimal getTotalReducedAmt() { return totalReducedAmt; }
    }

    record Line(String srcDocId, String ioDv, String invNo, String invSymbol, String invDt, String trcoTaxCode,
                String trcoNm, String prodNm, BigDecimal splyAmt, String vatRate, BigDecimal vatAmt,
                BigDecimal reducedAmt, String vatDeclProcYn, Long vatDeclSid, int vatRateOrder)
            implements AnnexInvoiceProjection {
        @Override public String getSrcDocId() { return srcDocId; }
        @Override public String getIoDv() { return ioDv; }
        @Override public String getInvNo() { return invNo; }
        @Override public String getInvSymbol() { return invSymbol; }
        @Override public String getInvDt() { return invDt; }
        @Override public String getTrcoTaxCode() { return trcoTaxCode; }
        @Override public String getTrcoNm() { return trcoNm; }
        @Override public String getProdNm() { return prodNm; }
        @Override public BigDecimal getSplyAmt() { return splyAmt; }
        @Override public String getVatRate() { return vatRate; }
        @Override public BigDecimal getVatAmt() { return vatAmt; }
        @Override public BigDecimal getReducedAmt() { return reducedAmt; }
        @Override public String getVatDeclProcYn() { return vatDeclProcYn; }
        @Override public Long getVatDeclSid() { return vatDeclSid; }
        @Override public int getVatRateOrder() { return vatRateOrder; }
    }
}
