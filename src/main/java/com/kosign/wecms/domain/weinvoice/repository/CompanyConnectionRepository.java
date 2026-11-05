package com.kosign.wecms.domain.weinvoice.repository;

import com.kosign.wecms.domain.weinvoice.common.WeInvoiceTime;
import com.kosign.wecms.domain.weinvoice.config.WeInvoiceDb;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * {@code winv_cmpn_infm} — tax codes a user manages, joined with the engine's company /
 * partner name and last run. The password column is written here but never selected: the
 * row only says whether one is stored.
 */
@Repository
public class CompanyConnectionRepository {

    private static final String SELECT = """
            SELECT c.owner_id, c.cmpn_mst,
                   COALESCE(NULLIF(c.cmpn_nm, ''), ec.name, pt.name) AS cmpn_nm,
                   c.lbl_nm, c.gdt_user_id, (c.gdt_pw_enc IS NOT NULL) AS has_pw,
                   c.conn_stts, c.conn_msg, c.conn_chk_dtm, c.dflt_yn, c.del_yn, c.reg_dtm,
                   (SELECT max(j.finished_at) FROM {eng}.job_run_companies j
                     WHERE j.company_mst = c.cmpn_mst) AS last_sync_at
              FROM {app}.winv_cmpn_infm c
              LEFT JOIN {eng}.companies ec ON ec.company_mst = c.cmpn_mst
              LEFT JOIN {eng}.partners pt ON pt.mst = c.cmpn_mst
            """;

    private static final RowMapper<CompanyRow> MAPPER = (rs, n) -> new CompanyRow(
            rs.getString("owner_id"),
            rs.getString("cmpn_mst"),
            rs.getString("cmpn_nm"),
            rs.getString("lbl_nm"),
            rs.getString("gdt_user_id"),
            rs.getBoolean("has_pw"),
            rs.getString("conn_stts"),
            rs.getString("conn_msg"),
            WeInvoiceTime.dtmToIso(rs.getString("conn_chk_dtm")),
            "Y".equals(rs.getString("dflt_yn")),
            "Y".equals(rs.getString("del_yn")),
            WeInvoiceTime.dtmToIso(rs.getString("reg_dtm")),
            WeInvoiceTime.iso(rs, "last_sync_at"));

    private final WeInvoiceDb db;
    private final String listSql;
    private final String findSql;

    public CompanyConnectionRepository(WeInvoiceDb db) {
        this.db = db;
        this.listSql = db.sql(SELECT + " WHERE c.owner_id = :ownerId AND c.del_yn = 'N' ORDER BY c.dflt_yn DESC, c.reg_dtm, c.cmpn_mst");
        this.findSql = db.sql(SELECT + " WHERE c.owner_id = :ownerId AND c.cmpn_mst = :mst");
    }

    public List<CompanyRow> listActive(String ownerId) {
        return db.jdbc().query(listSql, new MapSqlParameterSource("ownerId", ownerId), MAPPER);
    }

    /** Row of the user for this MST, soft-deleted rows included. */
    public Optional<CompanyRow> find(String ownerId, String mst) {
        return db.jdbc().query(findSql, new MapSqlParameterSource("ownerId", ownerId).addValue("mst", mst), MAPPER)
                .stream().findFirst();
    }

    /** Active MSTs of the user — the scope of every engine query. */
    public List<String> activeMsts(String ownerId) {
        return db.jdbc().queryForList(
                db.sql("SELECT cmpn_mst FROM {app}.winv_cmpn_infm WHERE owner_id = :ownerId AND del_yn = 'N'"),
                new MapSqlParameterSource("ownerId", ownerId), String.class);
    }

    /** Inserts a new connection, or revives a soft-deleted one, as {@code status} with a fresh password. */
    public void upsert(String ownerId, String mst, String label, String gdtUserId, String passwordEnc,
                       String status, String userId) {
        String dtm = WeInvoiceTime.nowDtm();
        db.jdbc().update(db.sql("""
                INSERT INTO {app}.winv_cmpn_infm
                       (owner_id, cmpn_mst, lbl_nm, gdt_user_id, gdt_pw_enc, conn_stts, conn_msg, conn_chk_dtm,
                        dflt_yn, del_yn, reg_id, reg_dtm, mod_id, mod_dtm)
                VALUES (:ownerId, :mst, :label, :userIdGdt, :pw, :status, NULL, NULL, 'N', 'N', :userId, :dtm, :userId, :dtm)
                ON CONFLICT (owner_id, cmpn_mst) DO UPDATE
                   SET lbl_nm = EXCLUDED.lbl_nm, gdt_user_id = EXCLUDED.gdt_user_id,
                       gdt_pw_enc = EXCLUDED.gdt_pw_enc, conn_stts = EXCLUDED.conn_stts,
                       conn_msg = NULL, conn_chk_dtm = NULL, dflt_yn = 'N', del_yn = 'N',
                       mod_id = EXCLUDED.mod_id, mod_dtm = EXCLUDED.mod_dtm
                """), new MapSqlParameterSource()
                .addValue("ownerId", ownerId)
                .addValue("mst", mst)
                .addValue("label", label)
                .addValue("userIdGdt", gdtUserId)
                .addValue("pw", passwordEnc)
                .addValue("status", status)
                .addValue("userId", userId)
                .addValue("dtm", dtm));
    }

    public int updatePassword(String ownerId, String mst, String gdtUserId, String passwordEnc, String userId) {
        return db.jdbc().update(db.sql("""
                UPDATE {app}.winv_cmpn_infm
                   SET gdt_pw_enc = :pw, gdt_user_id = COALESCE(:userIdGdt, gdt_user_id),
                       conn_stts = 'PENDING', conn_msg = NULL, conn_chk_dtm = NULL,
                       mod_id = :userId, mod_dtm = :dtm
                 WHERE owner_id = :ownerId AND cmpn_mst = :mst AND del_yn = 'N'
                """), new MapSqlParameterSource()
                .addValue("ownerId", ownerId)
                .addValue("mst", mst)
                .addValue("userIdGdt", gdtUserId)
                .addValue("pw", passwordEnc)
                .addValue("userId", userId)
                .addValue("dtm", WeInvoiceTime.nowDtm()));
    }

    public int updateLabel(String ownerId, String mst, String label, String userId) {
        return db.jdbc().update(db.sql("""
                UPDATE {app}.winv_cmpn_infm SET lbl_nm = :label, mod_id = :userId, mod_dtm = :dtm
                 WHERE owner_id = :ownerId AND cmpn_mst = :mst AND del_yn = 'N'
                """), new MapSqlParameterSource()
                .addValue("ownerId", ownerId)
                .addValue("mst", mst)
                .addValue("label", label)
                .addValue("userId", userId)
                .addValue("dtm", WeInvoiceTime.nowDtm()));
    }

    /** Makes {@code mst} the user's stored default; clears the flag on the others. */
    public void setDefault(String ownerId, String mst, String userId) {
        db.jdbc().update(db.sql("""
                UPDATE {app}.winv_cmpn_infm
                   SET dflt_yn = CASE WHEN cmpn_mst = :mst THEN 'Y' ELSE 'N' END, mod_id = :userId, mod_dtm = :dtm
                 WHERE owner_id = :ownerId AND del_yn = 'N'
                """), new MapSqlParameterSource()
                .addValue("ownerId", ownerId)
                .addValue("mst", mst)
                .addValue("userId", userId)
                .addValue("dtm", WeInvoiceTime.nowDtm()));
    }

    /** Soft delete; the stored password is erased at the same time. */
    public int softDelete(String ownerId, String mst, String userId) {
        return db.jdbc().update(db.sql("""
                UPDATE {app}.winv_cmpn_infm
                   SET del_yn = 'Y', dflt_yn = 'N', gdt_pw_enc = NULL, mod_id = :userId, mod_dtm = :dtm
                 WHERE owner_id = :ownerId AND cmpn_mst = :mst AND del_yn = 'N'
                """), new MapSqlParameterSource()
                .addValue("ownerId", ownerId)
                .addValue("mst", mst)
                .addValue("userId", userId)
                .addValue("dtm", WeInvoiceTime.nowDtm()));
    }

    /** One managed tax code. {@code status} is the stored code ({@code PENDING}, {@code CONNECTED}, …). */
    public record CompanyRow(
            String ownerId,
            String mst,
            String companyName,
            String label,
            String gdtUserId,
            boolean hasPassword,
            String status,
            String statusMessage,
            String checkedAt,
            boolean isDefault,
            boolean deleted,
            String createdAt,
            String lastSyncAt) {
    }
}
