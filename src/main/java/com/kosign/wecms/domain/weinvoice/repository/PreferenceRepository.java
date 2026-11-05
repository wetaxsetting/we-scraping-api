package com.kosign.wecms.domain.weinvoice.repository;

import com.kosign.wecms.domain.weinvoice.common.WeInvoiceTime;
import com.kosign.wecms.domain.weinvoice.config.WeInvoiceDb;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/** {@code winv_user_pref} — WeInvoice language, default working MST and shortcuts per user. */
@Repository
public class PreferenceRepository {

    private final WeInvoiceDb db;

    public PreferenceRepository(WeInvoiceDb db) {
        this.db = db;
    }

    public Optional<PrefRow> find(String bizKey, String userId) {
        return db.jdbc().query(db.sql("""
                        SELECT lang_cd, dflt_cmpn_mst, shct_data::text AS shct
                          FROM {app}.winv_user_pref
                         WHERE biz_key = :bizKey AND user_id = :userId
                        """),
                        new MapSqlParameterSource("bizKey", bizKey).addValue("userId", userId),
                        (rs, n) -> new PrefRow(rs.getString("lang_cd"), rs.getString("dflt_cmpn_mst"), rs.getString("shct")))
                .stream().findFirst();
    }

    /** Insert or replace; {@code null} arguments keep the stored value. */
    public void upsert(String bizKey, String userId, String langCd, String defaultMst, String shortcutsJson) {
        String dtm = WeInvoiceTime.nowDtm();
        db.jdbc().update(db.sql("""
                INSERT INTO {app}.winv_user_pref AS t
                       (biz_key, user_id, lang_cd, dflt_cmpn_mst, shct_data, reg_id, reg_dtm, mod_id, mod_dtm)
                VALUES (:bizKey, :userId, :lang, :mst, CAST(:shct AS jsonb), :userId, :dtm, :userId, :dtm)
                ON CONFLICT (biz_key, user_id) DO UPDATE
                   SET lang_cd = COALESCE(EXCLUDED.lang_cd, t.lang_cd),
                       dflt_cmpn_mst = COALESCE(EXCLUDED.dflt_cmpn_mst, t.dflt_cmpn_mst),
                       shct_data = COALESCE(EXCLUDED.shct_data, t.shct_data),
                       mod_id = EXCLUDED.mod_id, mod_dtm = EXCLUDED.mod_dtm
                """), new MapSqlParameterSource()
                .addValue("bizKey", bizKey)
                .addValue("userId", userId)
                .addValue("lang", langCd)
                .addValue("mst", defaultMst)
                .addValue("shct", shortcutsJson)
                .addValue("dtm", dtm));
    }

    public record PrefRow(String langCd, String defaultMst, String shortcutsJson) {
    }
}
