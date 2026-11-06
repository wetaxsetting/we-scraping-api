package com.kosign.wecms.domain.weinvoice.repository;

import com.kosign.wecms.domain.weinvoice.common.WeInvoiceTime;
import com.kosign.wecms.domain.weinvoice.config.WeInvoiceDb;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** {@code winv_invc_file} — files users attached to an invoice record (UI Spec 3.5). */
@Repository
public class InvoiceFileRepository {

    private static final String SELECT = """
            SELECT file_seq, invc_id, file_kind, file_orgn, file_nm, file_size, stor_path, file_sha256,
                   cnfm_yn, reg_id, reg_dtm
              FROM {app}.winv_invc_file
            """;

    private static final RowMapper<FileRow> MAPPER = (rs, n) -> new FileRow(
            rs.getLong("file_seq"),
            rs.getLong("invc_id"),
            rs.getString("file_kind"),
            rs.getString("file_orgn"),
            rs.getString("file_nm"),
            rs.getLong("file_size"),
            rs.getString("stor_path"),
            rs.getString("file_sha256"),
            "Y".equals(rs.getString("cnfm_yn")),
            rs.getString("reg_id"),
            WeInvoiceTime.dtmToIso(rs.getString("reg_dtm")));

    private final WeInvoiceDb db;

    public InvoiceFileRepository(WeInvoiceDb db) {
        this.db = db;
    }

    public List<FileRow> listActive(String ownerId, long invoiceId) {
        return db.jdbc().query(db.sql(SELECT + " WHERE owner_id = :ownerId AND invc_id = :id AND del_yn = 'N' ORDER BY file_seq"),
                new MapSqlParameterSource("ownerId", ownerId).addValue("id", invoiceId), MAPPER);
    }

    public Optional<FileRow> find(String ownerId, long invoiceId, long fileSeq) {
        return db.jdbc().query(db.sql(SELECT + """
                         WHERE owner_id = :ownerId AND invc_id = :id AND file_seq = :seq AND del_yn = 'N'
                        """),
                        new MapSqlParameterSource("ownerId", ownerId).addValue("id", invoiceId).addValue("seq", fileSeq), MAPPER)
                .stream().findFirst();
    }

    public long insert(String ownerId, String mst, long invoiceId, String kind, String name, long size,
                       String storPath, String sha256, boolean confirmed, String userId) {
        String dtm = WeInvoiceTime.nowDtm();
        Long seq = db.jdbc().queryForObject(db.sql("""
                INSERT INTO {app}.winv_invc_file
                       (file_seq, owner_id, cmpn_mst, invc_id, file_kind, file_orgn, file_nm, file_size, stor_path,
                        file_sha256, cnfm_yn, del_yn, reg_id, reg_dtm, mod_id, mod_dtm)
                VALUES (nextval('{app}.sq_winv_file_seq'), :ownerId, :mst, :id, :kind, 'UPLOAD', :name, :size, :path,
                        :sha, :cnfm, 'N', :userId, :dtm, :userId, :dtm)
                RETURNING file_seq
                """), new MapSqlParameterSource()
                .addValue("ownerId", ownerId)
                .addValue("mst", mst)
                .addValue("id", invoiceId)
                .addValue("kind", kind)
                .addValue("name", name)
                .addValue("size", size)
                .addValue("path", storPath)
                .addValue("sha", sha256)
                .addValue("cnfm", confirmed ? "Y" : "N")
                .addValue("userId", userId)
                .addValue("dtm", dtm), Long.class);
        return seq == null ? 0L : seq;
    }

    /** Soft-deletes uploaded files of a kind (a replaced PDF stays on disk for audit). */
    public int retireKind(String ownerId, long invoiceId, String kind, String userId) {
        return db.jdbc().update(db.sql("""
                UPDATE {app}.winv_invc_file SET del_yn = 'Y', mod_id = :userId, mod_dtm = :dtm
                 WHERE owner_id = :ownerId AND invc_id = :id AND file_kind = :kind AND del_yn = 'N'
                """), new MapSqlParameterSource()
                .addValue("ownerId", ownerId)
                .addValue("id", invoiceId)
                .addValue("kind", kind)
                .addValue("userId", userId)
                .addValue("dtm", WeInvoiceTime.nowDtm()));
    }

    public int softDelete(String ownerId, long invoiceId, long fileSeq, String userId) {
        return db.jdbc().update(db.sql("""
                UPDATE {app}.winv_invc_file SET del_yn = 'Y', mod_id = :userId, mod_dtm = :dtm
                 WHERE owner_id = :ownerId AND invc_id = :id AND file_seq = :seq AND del_yn = 'N'
                """), new MapSqlParameterSource()
                .addValue("ownerId", ownerId)
                .addValue("id", invoiceId)
                .addValue("seq", fileSeq)
                .addValue("userId", userId)
                .addValue("dtm", WeInvoiceTime.nowDtm()));
    }

    public record FileRow(
            long id,
            long invoiceId,
            String kind,
            String origin,
            String name,
            long size,
            String storPath,
            String sha256,
            boolean confirmed,
            String regId,
            String addedAt) {
    }
}
