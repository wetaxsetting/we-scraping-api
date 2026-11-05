package com.kosign.wecms.domain.weinvoice.repository;

import com.kosign.wecms.domain.weinvoice.common.WeInvoiceTime;
import com.kosign.wecms.domain.weinvoice.config.WeInvoiceDb;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** {@code winv_tag_infm} / {@code winv_invc_tag} — tenant tags and their invoices. */
@Repository
public class TagRepository {

    private final WeInvoiceDb db;

    public TagRepository(WeInvoiceDb db) {
        this.db = db;
    }

    /** Tags of the tenant with the number of invoices carrying each. */
    public List<TagRow> list(String bizKey) {
        return db.jdbc().query(db.sql("""
                        SELECT t.tag_seq, t.tag_nm, t.colr_cd,
                               (SELECT count(*) FROM {app}.winv_invc_tag it
                                 WHERE it.biz_key = t.biz_key AND it.tag_seq = t.tag_seq) AS cnt
                          FROM {app}.winv_tag_infm t
                         WHERE t.biz_key = :bizKey
                         ORDER BY t.tag_nm
                        """),
                new MapSqlParameterSource("bizKey", bizKey),
                (rs, n) -> new TagRow(rs.getLong("tag_seq"), rs.getString("tag_nm"), rs.getString("colr_cd"), rs.getLong("cnt")));
    }

    public Optional<TagRow> find(String bizKey, long tagId) {
        return list(bizKey).stream().filter(t -> t.id() == tagId).findFirst();
    }

    public boolean nameTaken(String bizKey, String name, Long exceptId) {
        Integer n = db.jdbc().queryForObject(db.sql("""
                        SELECT count(*) FROM {app}.winv_tag_infm
                         WHERE biz_key = :bizKey AND lower(tag_nm) = lower(:name) AND tag_seq <> :except
                        """),
                new MapSqlParameterSource("bizKey", bizKey).addValue("name", name).addValue("except", exceptId == null ? -1L : exceptId),
                Integer.class);
        return n != null && n > 0;
    }

    public long insert(String bizKey, String name, String color, String userId) {
        String dtm = WeInvoiceTime.nowDtm();
        Long id = db.jdbc().queryForObject(db.sql("""
                INSERT INTO {app}.winv_tag_infm (tag_seq, biz_key, tag_nm, colr_cd, reg_id, reg_dtm, mod_id, mod_dtm)
                VALUES (nextval('{app}.sq_winv_tag_seq'), :bizKey, :name, :color, :userId, :dtm, :userId, :dtm)
                RETURNING tag_seq
                """), new MapSqlParameterSource()
                .addValue("bizKey", bizKey)
                .addValue("name", name)
                .addValue("color", color)
                .addValue("userId", userId)
                .addValue("dtm", dtm), Long.class);
        return id == null ? 0L : id;
    }

    public int update(String bizKey, long tagId, String name, String color, String userId) {
        return db.jdbc().update(db.sql("""
                UPDATE {app}.winv_tag_infm
                   SET tag_nm = COALESCE(:name, tag_nm), colr_cd = COALESCE(:color, colr_cd),
                       mod_id = :userId, mod_dtm = :dtm
                 WHERE biz_key = :bizKey AND tag_seq = :id
                """), new MapSqlParameterSource()
                .addValue("bizKey", bizKey)
                .addValue("id", tagId)
                .addValue("name", name)
                .addValue("color", color)
                .addValue("userId", userId)
                .addValue("dtm", WeInvoiceTime.nowDtm()));
    }

    /** Deletes the tag; its assignments go with it ({@code ON DELETE CASCADE}). */
    public int delete(String bizKey, long tagId) {
        return db.jdbc().update(db.sql("DELETE FROM {app}.winv_tag_infm WHERE biz_key = :bizKey AND tag_seq = :id"),
                new MapSqlParameterSource("bizKey", bizKey).addValue("id", tagId));
    }

    public void assign(String bizKey, long tagId, List<Long> invoiceIds, String userId) {
        if (invoiceIds.isEmpty()) return;
        String dtm = WeInvoiceTime.nowDtm();
        SqlParameterSource[] batch = invoiceIds.stream()
                .map(id -> new MapSqlParameterSource()
                        .addValue("bizKey", bizKey)
                        .addValue("tag", tagId)
                        .addValue("id", id)
                        .addValue("userId", userId)
                        .addValue("dtm", dtm))
                .toArray(SqlParameterSource[]::new);
        db.jdbc().batchUpdate(db.sql("""
                INSERT INTO {app}.winv_invc_tag (biz_key, tag_seq, invc_id, reg_id, reg_dtm)
                VALUES (:bizKey, :tag, :id, :userId, :dtm)
                ON CONFLICT (biz_key, tag_seq, invc_id) DO NOTHING
                """), batch);
    }

    public int unassign(String bizKey, long tagId, List<Long> invoiceIds) {
        if (invoiceIds.isEmpty()) return 0;
        return db.jdbc().update(db.sql("""
                DELETE FROM {app}.winv_invc_tag
                 WHERE biz_key = :bizKey AND tag_seq = :tag AND invc_id IN (:ids)
                """), new MapSqlParameterSource("bizKey", bizKey).addValue("tag", tagId).addValue("ids", invoiceIds));
    }

    public List<String> tagIdsOf(String bizKey, long invoiceId) {
        return db.jdbc().queryForList(db.sql("""
                        SELECT tag_seq::text FROM {app}.winv_invc_tag
                         WHERE biz_key = :bizKey AND invc_id = :id ORDER BY tag_seq
                        """),
                new MapSqlParameterSource("bizKey", bizKey).addValue("id", invoiceId), String.class);
    }

    public record TagRow(long id, String name, String color, long invoiceCount) {
    }
}
