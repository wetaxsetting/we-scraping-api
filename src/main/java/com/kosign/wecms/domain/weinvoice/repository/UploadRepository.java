package com.kosign.wecms.domain.weinvoice.repository;

import com.kosign.wecms.domain.weinvoice.common.WeInvoiceTime;
import com.kosign.wecms.domain.weinvoice.config.WeInvoiceDb;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * {@code winv_upld_infm} / {@code winv_upld_file} — upload batches (screen 09). The backend
 * stores the files and the batch; the engine import writes each file's outcome.
 */
@Repository
public class UploadRepository {

    private final WeInvoiceDb db;

    public UploadRepository(WeInvoiceDb db) {
        this.db = db;
    }

    public long insertBatch(String ownerId, String mst, int fileCount, String userId) {
        String dtm = WeInvoiceTime.nowDtm();
        Long id = db.jdbc().queryForObject(db.sql("""
                INSERT INTO {app}.winv_upld_infm
                       (upld_seq, owner_id, cmpn_mst, rqst_seq, file_cnt, del_yn, reg_id, reg_dtm, mod_id, mod_dtm)
                VALUES (nextval('{app}.sq_winv_upld_seq'), :ownerId, :mst, NULL, :cnt, 'N', :userId, :dtm, :userId, :dtm)
                RETURNING upld_seq
                """), new MapSqlParameterSource()
                .addValue("ownerId", ownerId)
                .addValue("mst", mst)
                .addValue("cnt", fileCount)
                .addValue("userId", userId)
                .addValue("dtm", dtm), Long.class);
        return id == null ? 0L : id;
    }

    public void insertFile(long uploadId, int fileNo, String name, long size, String storPath, String sha256) {
        db.jdbc().update(db.sql("""
                INSERT INTO {app}.winv_upld_file (upld_seq, file_no, file_nm, file_size, stor_path, file_sha256)
                VALUES (:id, :no, :name, :size, :path, :sha)
                """), new MapSqlParameterSource()
                .addValue("id", uploadId)
                .addValue("no", fileNo)
                .addValue("name", name)
                .addValue("size", size)
                .addValue("path", storPath)
                .addValue("sha", sha256));
    }

    public void linkRequest(long uploadId, long requestId) {
        db.jdbc().update(db.sql("UPDATE {app}.winv_upld_infm SET rqst_seq = :rq WHERE upld_seq = :id"),
                new MapSqlParameterSource("id", uploadId).addValue("rq", requestId));
    }

    public Optional<BatchRow> findBatch(String ownerId, long uploadId) {
        return db.jdbc().query(db.sql("""
                        SELECT u.upld_seq, u.cmpn_mst, u.rqst_seq, u.file_cnt, u.reg_dtm,
                               r.rqst_stts, r.err_msg
                          FROM {app}.winv_upld_infm u
                          LEFT JOIN {app}.winv_engn_rqst r ON r.rqst_seq = u.rqst_seq
                         WHERE u.owner_id = :ownerId AND u.upld_seq = :id AND u.del_yn = 'N'
                        """),
                        new MapSqlParameterSource("ownerId", ownerId).addValue("id", uploadId),
                        (rs, n) -> new BatchRow(
                                rs.getLong("upld_seq"),
                                rs.getString("cmpn_mst"),
                                Rows.longOrNull(rs, "rqst_seq"),
                                rs.getInt("file_cnt"),
                                WeInvoiceTime.dtmToIso(rs.getString("reg_dtm")),
                                rs.getString("rqst_stts"),
                                rs.getString("err_msg")))
                .stream().findFirst();
    }

    public List<FileRow> files(long uploadId) {
        return db.jdbc().query(db.sql("""
                        SELECT f.file_no, f.file_nm, f.file_size, f.rslt_cd, f.rslt_msg, f.invc_id, f.nbmst, f.nmmst,
                               f.ptnr_nm, f.totl_amt, f.prcs_dtm,
                               (p.invoice_id IS NOT NULL) AS has_pdf
                          FROM {app}.winv_upld_file f
                          LEFT JOIN {eng}.invoice_pdf p ON p.invoice_id = f.invc_id
                         WHERE f.upld_seq = :id
                         ORDER BY f.file_no
                        """),
                new MapSqlParameterSource("id", uploadId),
                (rs, n) -> new FileRow(
                        rs.getInt("file_no"),
                        rs.getString("file_nm"),
                        rs.getLong("file_size"),
                        rs.getString("rslt_cd"),
                        rs.getString("rslt_msg"),
                        Rows.longOrNull(rs, "invc_id"),
                        rs.getString("nbmst"),
                        rs.getString("nmmst"),
                        rs.getString("ptnr_nm"),
                        rs.getBigDecimal("totl_amt"),
                        WeInvoiceTime.dtmToIso(rs.getString("prcs_dtm")),
                        rs.getBoolean("has_pdf")));
    }

    public int softDelete(String ownerId, long uploadId, String userId) {
        return db.jdbc().update(db.sql("""
                UPDATE {app}.winv_upld_infm SET del_yn = 'Y', mod_id = :userId, mod_dtm = :dtm
                 WHERE owner_id = :ownerId AND upld_seq = :id AND del_yn = 'N'
                """), new MapSqlParameterSource()
                .addValue("ownerId", ownerId)
                .addValue("id", uploadId)
                .addValue("userId", userId)
                .addValue("dtm", WeInvoiceTime.nowDtm()));
    }

    public record BatchRow(long id, String mst, Long requestId, int fileCount, String createdAt,
                           String requestStatus, String requestError) {
    }

    /** {@code outcome} is the engine's {@code rslt_cd}; {@code null} while not processed. */
    public record FileRow(int fileNo, String name, long size, String outcome, String message, Long invoiceId,
                          String sellerTaxId, String buyerTaxId, String partnerName, BigDecimal total,
                          String processedAt, boolean hasPdf) {
    }
}
