package com.kosign.wecms.domain.weinvoice.repository;

import com.kosign.wecms.domain.weinvoice.common.WeInvoiceTime;
import com.kosign.wecms.domain.weinvoice.config.WeInvoiceDb;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * One invoice with everything screen 05 shows: the engine row, its XML metadata and parsed
 * JSON, the original PDF, the catalogue provider, a manual lookup code, the line items, the
 * xml / pdf task state and the related (replacement / adjustment) invoice.
 */
@Repository
public class InvoiceDetailRepository {

    private final WeInvoiceDb db;

    public InvoiceDetailRepository(WeInvoiceDb db) {
        this.db = db;
    }

    public Optional<DetailRow> find(String bizKey, long id) {
        return db.jdbc().query(db.sql("""
                        SELECT i.id, i.company_mst, i.direction, i.sub_type, i.nbmst, i.nbten, i.nmmst, i.nmten,
                               i.khmshdon, i.khhdon, i.shdon, i.ngay_lap, i.tien_chua_thue, i.tien_thue, i.tong_tien,
                               i.tthai, i.list_raw::text AS list_raw, i.first_seen_at,
                               x.parsed::text AS parsed, x.mst_tcgp, x.ma_tra_cuu, x.lookup_url, x.xml_path,
                               x.fetched_at AS xml_fetched_at,
                               p.pdf_path, p.source_id AS pdf_source, p.fetched_at AS pdf_fetched_at,
                               (SELECT d.nguon FROM {eng}.invoice_details d
                                 WHERE d.invoice_id = i.id ORDER BY d.line_no LIMIT 1) AS detail_source,
                               pv.prvd_mst AS pv_mst, pv.prvd_nm, pv.lkup_url AS prvd_lkup_url,
                               lc.lkup_cd, lc.cmpn_cd, lc.lkup_url AS manual_url
                          FROM {eng}.invoices i
                          LEFT JOIN {eng}.invoice_xml x ON x.invoice_id = i.id
                          LEFT JOIN {eng}.invoice_pdf p ON p.invoice_id = i.id
                          LEFT JOIN {app}.winv_prvd_infm pv
                                 ON pv.prvd_mst = split_part(COALESCE(NULLIF(x.mst_tcgp, ''), i.list_raw->>'msttcgp'), '-', 1)
                          LEFT JOIN {app}.winv_lkup_cd lc ON lc.biz_key = :bizKey AND lc.invc_id = i.id
                         WHERE i.id = :id
                        """),
                new MapSqlParameterSource("id", id).addValue("bizKey", bizKey),
                (rs, n) -> new DetailRow(
                        rs.getLong("id"),
                        rs.getString("company_mst"),
                        rs.getString("direction"),
                        rs.getString("sub_type"),
                        rs.getString("nbmst"),
                        rs.getString("nbten"),
                        rs.getString("nmmst"),
                        rs.getString("nmten"),
                        rs.getInt("khmshdon"),
                        rs.getString("khhdon"),
                        rs.getLong("shdon"),
                        WeInvoiceTime.date(rs, "ngay_lap"),
                        rs.getBigDecimal("tien_chua_thue"),
                        rs.getBigDecimal("tien_thue"),
                        rs.getBigDecimal("tong_tien"),
                        Rows.intOrNull(rs, "tthai"),
                        rs.getString("list_raw"),
                        WeInvoiceTime.iso(rs, "first_seen_at"),
                        rs.getString("parsed"),
                        rs.getString("mst_tcgp"),
                        rs.getString("ma_tra_cuu"),
                        rs.getString("lookup_url"),
                        rs.getString("xml_path"),
                        WeInvoiceTime.iso(rs, "xml_fetched_at"),
                        rs.getString("pdf_path"),
                        rs.getString("pdf_source"),
                        WeInvoiceTime.iso(rs, "pdf_fetched_at"),
                        rs.getString("detail_source"),
                        rs.getString("pv_mst"),
                        rs.getString("prvd_nm"),
                        rs.getString("prvd_lkup_url"),
                        rs.getString("lkup_cd"),
                        rs.getString("cmpn_cd"),
                        rs.getString("manual_url")))
                .stream().findFirst();
    }

    public List<ItemRow> items(long id) {
        return db.jdbc().query(db.sql("""
                        SELECT line_no, tinh_chat, ma_hhdv, ten_hhdv, don_vi_tinh, so_luong, don_gia, tl_ck, st_ck,
                               thanh_tien, thue_suat, tien_thue, tong_tien
                          FROM {eng}.invoice_details
                         WHERE invoice_id = :id
                         ORDER BY line_no
                        """),
                new MapSqlParameterSource("id", id),
                (rs, n) -> new ItemRow(
                        rs.getInt("line_no"),
                        Rows.intOrNull(rs, "tinh_chat"),
                        rs.getString("ma_hhdv"),
                        rs.getString("ten_hhdv"),
                        rs.getString("don_vi_tinh"),
                        rs.getBigDecimal("so_luong"),
                        rs.getBigDecimal("don_gia"),
                        rs.getBigDecimal("tl_ck"),
                        rs.getBigDecimal("st_ck"),
                        rs.getBigDecimal("thanh_tien"),
                        rs.getString("thue_suat"),
                        rs.getBigDecimal("tien_thue"),
                        rs.getBigDecimal("tong_tien")));
    }

    /** Line items of several invoices (Partner API details), keyed by invoice id, lines in order. */
    public Map<Long, List<ItemRow>> itemsOf(List<Long> ids) {
        Map<Long, List<ItemRow>> out = new HashMap<>();
        if (ids.isEmpty()) return out;
        db.jdbc().query(db.sql("""
                        SELECT invoice_id, line_no, tinh_chat, ma_hhdv, ten_hhdv, don_vi_tinh, so_luong, don_gia, tl_ck,
                               st_ck, thanh_tien, thue_suat, tien_thue, tong_tien
                          FROM {eng}.invoice_details
                         WHERE invoice_id IN (:ids)
                         ORDER BY invoice_id, line_no
                        """),
                new MapSqlParameterSource("ids", ids),
                rs -> {
                    out.computeIfAbsent(rs.getLong("invoice_id"), k -> new ArrayList<>()).add(new ItemRow(
                            rs.getInt("line_no"),
                            Rows.intOrNull(rs, "tinh_chat"),
                            rs.getString("ma_hhdv"),
                            rs.getString("ten_hhdv"),
                            rs.getString("don_vi_tinh"),
                            rs.getBigDecimal("so_luong"),
                            rs.getBigDecimal("don_gia"),
                            rs.getBigDecimal("tl_ck"),
                            rs.getBigDecimal("st_ck"),
                            rs.getBigDecimal("thanh_tien"),
                            rs.getString("thue_suat"),
                            rs.getBigDecimal("tien_thue"),
                            rs.getBigDecimal("tong_tien")));
                });
        return out;
    }

    /** Files of the selected invoices of one MST for the ZIP export; an upload is listed next to the engine copy. */
    public List<ExportFileRow> exportFiles(String bizKey, String mst, List<Long> ids) {
        if (ids.isEmpty()) return List.of();
        return db.jdbc().query(db.sql("""
                        SELECT i.id, i.khmshdon, i.khhdon, i.shdon, i.ngay_lap,
                               CASE WHEN i.direction = 'ban' THEN i.nmmst ELSE i.nbmst END AS ptnr_mst,
                               x.xml_path, p.pdf_path,
                               (SELECT f.stor_path FROM {app}.winv_invc_file f
                                 WHERE f.biz_key = :bizKey AND f.invc_id = i.id AND f.file_kind = 'XML' AND f.del_yn = 'N'
                                 ORDER BY f.file_seq DESC LIMIT 1) AS up_xml,
                               (SELECT f.stor_path FROM {app}.winv_invc_file f
                                 WHERE f.biz_key = :bizKey AND f.invc_id = i.id AND f.file_kind = 'PDF' AND f.del_yn = 'N'
                                 ORDER BY f.file_seq DESC LIMIT 1) AS up_pdf
                          FROM {eng}.invoices i
                          LEFT JOIN {eng}.invoice_xml x ON x.invoice_id = i.id
                          LEFT JOIN {eng}.invoice_pdf p ON p.invoice_id = i.id
                         WHERE i.company_mst = :mst AND i.id IN (:ids)
                         ORDER BY i.ngay_lap, i.id
                        """),
                new MapSqlParameterSource("bizKey", bizKey).addValue("mst", mst).addValue("ids", ids),
                (rs, n) -> new ExportFileRow(
                        rs.getLong("id"),
                        rs.getInt("khmshdon"),
                        rs.getString("khhdon"),
                        rs.getLong("shdon"),
                        WeInvoiceTime.date(rs, "ngay_lap"),
                        rs.getString("ptnr_mst"),
                        rs.getString("xml_path"),
                        rs.getString("pdf_path"),
                        rs.getString("up_xml"),
                        rs.getString("up_pdf")));
    }

    public List<TaskRow> tasks(long id) {
        return db.jdbc().query(db.sql("""
                        SELECT task_type, status, attempts, error_kind, last_error, next_run_at, updated_at
                          FROM {eng}.tasks
                         WHERE invoice_id = :id
                        """),
                new MapSqlParameterSource("id", id),
                (rs, n) -> new TaskRow(
                        rs.getString("task_type"),
                        rs.getString("status"),
                        rs.getInt("attempts"),
                        rs.getString("error_kind"),
                        rs.getString("last_error"),
                        WeInvoiceTime.iso(rs, "next_run_at"),
                        WeInvoiceTime.iso(rs, "updated_at")));
    }

    /** Id of the invoice with this GDT key in the same MST, if stored. */
    public Optional<Long> findByKey(String mst, String sellerTaxId, int templateNo, String symbol, long number) {
        return db.jdbc().queryForList(db.sql("""
                        SELECT i.id FROM {eng}.invoices i
                         WHERE i.company_mst = :mst AND i.nbmst = :seller AND i.khmshdon = :tpl
                           AND upper(i.khhdon) = upper(:sym) AND i.shdon = :no
                         LIMIT 1
                        """),
                new MapSqlParameterSource()
                        .addValue("mst", mst)
                        .addValue("seller", sellerTaxId)
                        .addValue("tpl", templateNo)
                        .addValue("sym", symbol)
                        .addValue("no", number), Long.class)
                .stream().findFirst();
    }

    /**
     * Id of an invoice that names this one as its related invoice (the replacement or
     * adjustment of a replaced / adjusted invoice): in the XML ({@code TTHDLQuan}) or in the
     * GDT list ({@code khhdgoc} / {@code shdgoc}).
     */
    public Optional<Long> findReferencing(long selfId, String mst, String sellerTaxId, int templateNo, String symbol,
                                          long number) {
        return db.jdbc().queryForList(db.sql("""
                        SELECT i2.id FROM {eng}.invoices i2
                          LEFT JOIN {eng}.invoice_xml x2 ON x2.invoice_id = i2.id
                         WHERE i2.company_mst = :mst AND i2.nbmst = :seller AND i2.id <> :self
                           AND ((upper(x2.parsed->'thong_tin_chung'->'hoa_don_lien_quan'->>'ky_hieu') IN (upper(:sym), upper(:tplSym))
                                 AND ltrim(regexp_replace(COALESCE(x2.parsed->'thong_tin_chung'->'hoa_don_lien_quan'->>'so_hd', ''),
                                                          '[^0-9]', '', 'g'), '0') = :no)
                             OR (upper(i2.list_raw->>'khhdgoc') IN (upper(:sym), upper(:tplSym))
                                 AND ltrim(regexp_replace(COALESCE(i2.list_raw->>'shdgoc', ''), '[^0-9]', '', 'g'), '0') = :no))
                         ORDER BY i2.ngay_lap DESC, i2.id DESC
                         LIMIT 1
                        """),
                new MapSqlParameterSource()
                        .addValue("mst", mst)
                        .addValue("seller", sellerTaxId)
                        .addValue("self", selfId)
                        .addValue("sym", symbol)
                        .addValue("tplSym", templateNo + symbol)
                        .addValue("no", String.valueOf(number)), Long.class)
                .stream().findFirst();
    }

    /** Engine row + XML / PDF metadata + catalogue + manual lookup code of one invoice. */
    public record DetailRow(
            long id,
            String companyMst,
            String direction,
            String subType,
            String sellerTaxId,
            String sellerName,
            String buyerTaxId,
            String buyerName,
            int templateNo,
            String symbol,
            long number,
            String issueDate,
            BigDecimal preTax,
            BigDecimal tax,
            BigDecimal total,
            Integer tthai,
            String listRawJson,
            String firstSeenAt,
            String parsedJson,
            String xmlProviderMst,
            String xmlLookupCode,
            String xmlLookupUrl,
            String xmlPath,
            String xmlFetchedAt,
            String pdfPath,
            String pdfSource,
            String pdfFetchedAt,
            String detailSource,
            String catalogueMst,
            String providerName,
            String providerLookupUrl,
            String manualCode,
            String manualCompanyCode,
            String manualUrl) {
    }

    public record ItemRow(
            int lineNo,
            Integer lineKind,
            String code,
            String name,
            String unit,
            BigDecimal qty,
            BigDecimal price,
            BigDecimal discountRate,
            BigDecimal discount,
            BigDecimal amount,
            String vatRate,
            BigDecimal taxAmount,
            BigDecimal totalAmount) {
    }

    /** Paths are relative: engine files to the engine output dir, uploads to the WeInvoice storage root. */
    public record ExportFileRow(
            long id,
            int templateNo,
            String symbol,
            long number,
            String issueDate,
            String partnerTaxId,
            String xmlPath,
            String pdfPath,
            String uploadedXmlPath,
            String uploadedPdfPath) {
    }

    public record TaskRow(
            String type,
            String status,
            int attempts,
            String errorKind,
            String lastError,
            String nextRunAt,
            String updatedAt) {
    }
}
