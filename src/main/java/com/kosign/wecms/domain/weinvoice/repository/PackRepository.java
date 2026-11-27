package com.kosign.wecms.domain.weinvoice.repository;

import com.kosign.wecms.domain.weinvoice.common.InvoiceCodes;
import com.kosign.wecms.domain.weinvoice.common.InvoiceExtraSql;
import com.kosign.wecms.domain.weinvoice.common.ProviderSql;
import com.kosign.wecms.domain.weinvoice.common.WeInvoiceTime;
import com.kosign.wecms.domain.weinvoice.config.WeInvoiceDb;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads behind the v1.1 downloads (UI Spec v1.1 §3.12): the bulk PDF pack and the GDT original list ("Bảng kê gốc").
 * Engine tables are read only; the owner's uploads, manual lookup codes and the provider catalogue come from the app
 * schema. Ids are read in chunks so a long period never builds one huge IN list.
 */
@Repository
public class PackRepository {

    private static final int CHUNK = 5_000;

    private final WeInvoiceDb db;

    public PackRepository(WeInvoiceDb db) {
        this.db = db;
    }

    /** Everything the pack needs per invoice, ordered by issue date then number. */
    public List<PackRow> packRows(String ownerId, String mst, List<Long> ids) {
        List<PackRow> out = new ArrayList<>(ids.size());
        for (int i = 0; i < ids.size(); i += CHUNK) {
            out.addAll(packChunk(ownerId, mst, ids.subList(i, Math.min(ids.size(), i + CHUNK))));
        }
        out.sort(java.util.Comparator.comparing((PackRow r) -> r.issueDate() == null ? "" : r.issueDate())
                .thenComparingLong(PackRow::number).thenComparingLong(PackRow::id));
        return out;
    }

    private List<PackRow> packChunk(String ownerId, String mst, List<Long> ids) {
        if (ids.isEmpty()) return List.of();
        return db.jdbc().query(db.sql("WITH " + ProviderSql.SELLER_USUAL_CTE + "\n" + """
                        SELECT i.id, i.direction, i.sub_type, i.ngay_lap, i.khmshdon, i.khhdon, i.shdon,
                               i.nbmst, i.nbten, i.nmmst, i.nmten, i.tien_chua_thue, i.tien_thue, i.tong_tien,
                        """ + "       " + InvoiceCodes.SQL_TTHAI_EFF + " AS tthai_eff,\n" + """
                               COALESCE(NULLIF(x.parsed->>'ma_cqt', ''), NULLIF(i.list_raw->>'mhdon', '')) AS cqt_code,
                               COALESCE(NULLIF(x.parsed->'nguoi_ban'->>'dia_chi', ''), i.list_raw->>'nbdchi') AS nb_addr,
                               COALESCE(NULLIF(x.parsed->'nguoi_mua'->>'dia_chi', ''), i.list_raw->>'nmdchi') AS nm_addr,
                               COALESCE(NULLIF(x.parsed->'thong_tin_chung'->>'don_vi_tt', ''), i.list_raw->>'dvtte') AS ccy,
                               x.xml_path, x.gdt_pdf_path, p.pdf_path, p.source_id AS pdf_src,
                               pe.prvd_mst,
                               pv.prvd_nm,
                               COALESCE(lc.lkup_cd, NULLIF(x.ma_tra_cuu, '')) AS lkup_cd,
                               COALESCE(lc.lkup_url, NULLIF(x.lookup_url, '')) AS lkup_url,
                               tp.status AS pdf_task_status, tp.error_kind AS pdf_error_kind,
                               tx.status AS xml_task_status, tx.error_kind AS xml_error_kind,
                               (SELECT f.stor_path FROM {app}.winv_invc_file f
                                 WHERE f.owner_id = :ownerId AND f.invc_id = i.id AND f.file_kind = 'XML' AND f.del_yn = 'N'
                                 ORDER BY f.file_seq DESC LIMIT 1) AS up_xml,
                               (SELECT f.stor_path FROM {app}.winv_invc_file f
                                 WHERE f.owner_id = :ownerId AND f.invc_id = i.id AND f.file_kind = 'PDF' AND f.del_yn = 'N'
                                 ORDER BY f.file_seq DESC LIMIT 1) AS up_pdf
                          FROM {eng}.invoices i
                          LEFT JOIN {eng}.invoice_xml x ON x.invoice_id = i.id
                          LEFT JOIN {eng}.invoice_pdf p ON p.invoice_id = i.id
                          LEFT JOIN {eng}.tasks tp ON tp.invoice_id = i.id AND tp.task_type = 'pdf'
                          LEFT JOIN {eng}.tasks tx ON tx.invoice_id = i.id AND tx.task_type = 'xml'
                        """ + "  " + ProviderSql.SELLER_USUAL_JOIN + "\n"
                        + "  CROSS JOIN LATERAL (SELECT " + ProviderSql.effective("sprv.m") + " AS prvd_mst) pe\n" + """
                          LEFT JOIN {app}.winv_prvd_infm pv ON pv.prvd_mst = split_part(pe.prvd_mst, '-', 1)
                          LEFT JOIN {app}.winv_lkup_cd lc ON lc.owner_id = :ownerId AND lc.invc_id = i.id
                         WHERE i.company_mst = :mst AND i.id IN (:ids)
                        """),
                new MapSqlParameterSource("ownerId", ownerId).addValue("mst", mst).addValue("ids", ids),
                (rs, n) -> new PackRow(
                        rs.getLong("id"),
                        rs.getString("direction"),
                        rs.getString("sub_type"),
                        WeInvoiceTime.date(rs, "ngay_lap"),
                        rs.getInt("khmshdon"),
                        rs.getString("khhdon"),
                        rs.getLong("shdon"),
                        rs.getString("nbmst"),
                        rs.getString("nbten"),
                        rs.getString("nb_addr"),
                        rs.getString("nmmst"),
                        rs.getString("nmten"),
                        rs.getString("nm_addr"),
                        rs.getBigDecimal("tien_chua_thue"),
                        rs.getBigDecimal("tien_thue"),
                        rs.getBigDecimal("tong_tien"),
                        rs.getInt("tthai_eff"),
                        rs.getString("cqt_code"),
                        InvoiceCodes.currency(rs.getString("ccy")),
                        rs.getString("xml_path"),
                        rs.getString("up_xml"),
                        rs.getString("pdf_path"),
                        rs.getString("pdf_src"),
                        rs.getString("up_pdf"),
                        rs.getString("gdt_pdf_path"),
                        rs.getString("prvd_mst"),
                        rs.getString("prvd_nm"),
                        rs.getString("lkup_cd"),
                        rs.getString("lkup_url"),
                        rs.getString("pdf_task_status"),
                        rs.getString("pdf_error_kind"),
                        rs.getString("xml_task_status"),
                        rs.getString("xml_error_kind")));
    }

    /**
     * Rows of the GDT list ("Bảng kê gốc") — invoices that came from GDT (uploads and test data left out), with the
     * raw list row the engine stored ({@code list_raw}: the GDT Excel columns, or the GDT JSON list fields).
     */
    public List<CqtRow> cqtRows(String mst, List<Long> ids) {
        List<CqtRow> out = new ArrayList<>(ids.size());
        for (int i = 0; i < ids.size(); i += CHUNK) {
            List<Long> part = ids.subList(i, Math.min(ids.size(), i + CHUNK));
            out.addAll(db.jdbc().query(db.sql("""
                            SELECT i.id, i.direction, i.sub_type, i.ngay_lap, i.khmshdon, i.khhdon, i.shdon,
                                   i.nbmst, i.nbten, i.nmmst, i.nmten, i.tien_chua_thue, i.tien_thue, i.tong_tien,
                                   i.tthai, i.list_raw::text AS list_raw,
                            """ + "       " + InvoiceExtraSql.TRADE_DISCOUNT + " AS trade_discount, " + InvoiceExtraSql.FEE
                            + " AS fee" + """

                              FROM {eng}.invoices i
                              LEFT JOIN {eng}.invoice_xml x ON x.invoice_id = i.id
                             WHERE i.company_mst = :mst AND i.id IN (:ids)
                               AND COALESCE(i.list_raw->>'nguon', '') NOT IN ('upload', 'test_data')
                            """),
                    new MapSqlParameterSource("mst", mst).addValue("ids", part),
                    (rs, n) -> new CqtRow(
                            rs.getLong("id"),
                            rs.getString("direction"),
                            rs.getString("sub_type"),
                            WeInvoiceTime.date(rs, "ngay_lap"),
                            rs.getInt("khmshdon"),
                            rs.getString("khhdon"),
                            rs.getLong("shdon"),
                            rs.getString("nbmst"),
                            rs.getString("nbten"),
                            rs.getString("nmmst"),
                            rs.getString("nmten"),
                            rs.getBigDecimal("tien_chua_thue"),
                            rs.getBigDecimal("tien_thue"),
                            rs.getBigDecimal("tong_tien"),
                            Rows.intOrNull(rs, "tthai"),
                            rs.getString("list_raw"),
                            rs.getBigDecimal("trade_discount"),
                            rs.getBigDecimal("fee"))));
        }
        return out;
    }

    /** One invoice of the bulk PDF pack; file paths are relative (engine output dir / WeInvoice storage root). */
    public record PackRow(
            long id,
            String direction,
            String subType,
            String issueDate,
            int templateNo,
            String symbol,
            long number,
            String sellerTaxId,
            String sellerName,
            String sellerAddress,
            String buyerTaxId,
            String buyerName,
            String buyerAddress,
            BigDecimal preTax,
            BigDecimal tax,
            BigDecimal total,
            int tthai,
            String cqtCode,
            String currency,
            String xmlPath,
            String uploadedXmlPath,
            String pdfPath,
            String pdfSource,
            String uploadedPdfPath,
            String gdtPdfPath,
            String providerMst,
            String providerName,
            String lookupCode,
            String lookupUrl,
            String pdfTaskStatus,
            String pdfErrorKind,
            String xmlTaskStatus,
            String xmlErrorKind) {

        public boolean sales() {
            return "ban".equals(direction);
        }

        public String partnerTaxId() {
            return sales() ? buyerTaxId : sellerTaxId;
        }

        public String partnerName() {
            return sales() ? buyerName : sellerName;
        }

        public boolean hasXml() {
            return xmlPath != null || uploadedXmlPath != null;
        }

        public boolean noCode() {
            return symbol != null && symbol.toUpperCase(java.util.Locale.ROOT).startsWith("K");
        }
    }

    public record CqtRow(
            long id,
            String direction,
            String subType,
            String issueDate,
            int templateNo,
            String symbol,
            long number,
            String sellerTaxId,
            String sellerName,
            String buyerTaxId,
            String buyerName,
            BigDecimal preTax,
            BigDecimal tax,
            BigDecimal total,
            Integer tthai,
            String listRawJson,
            /* UI Spec v1.2 §3.11: from the XML (TTCKTMai, Σ DSLPhi), else the GDT list. */
            BigDecimal tradeDiscount,
            BigDecimal fee) {
    }
}
