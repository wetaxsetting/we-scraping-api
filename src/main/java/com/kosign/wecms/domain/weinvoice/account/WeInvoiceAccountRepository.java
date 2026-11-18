package com.kosign.wecms.domain.weinvoice.account;

import com.kosign.wecms.domain.weinvoice.common.WeInvoiceTime;
import com.kosign.wecms.domain.weinvoice.config.WeInvoiceDb;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/** {@code winv_user_infm} on the WeInvoice database (V4__winv_user.sql). Login ids match case-insensitively. */
@Repository
public class WeInvoiceAccountRepository {

    /** Consecutive wrong passwords that lock the account (same threshold as WeCMS F-10). */
    static final int LOCK_THRESHOLD = 5;

    private static final RowMapper<WeInvoiceAccount> MAPPER = (rs, n) -> new WeInvoiceAccount(
            rs.getString("user_id"),
            rs.getString("user_nm"),
            rs.getString("eml"),
            rs.getString("clph_no"),
            rs.getString("cmpn_nm"),
            rs.getString("user_pw"),
            rs.getString("lang_cd"),
            "Y".equals(rs.getString("actv_yn")),
            "Y".equals(rs.getString("lock_yn")),
            "Y".equals(rs.getString("del_yn")),
            rs.getInt("fail_cnt"));

    private final WeInvoiceDb db;

    public WeInvoiceAccountRepository(WeInvoiceDb db) {
        this.db = db;
    }

    public Optional<WeInvoiceAccount> find(String userId) {
        return db.jdbc().query(db.sql("""
                        SELECT user_id, user_nm, eml, clph_no, cmpn_nm, user_pw, lang_cd, actv_yn, lock_yn, del_yn, fail_cnt
                          FROM {app}.winv_user_infm
                         WHERE lower(user_id) = lower(:id)
                        """),
                new MapSqlParameterSource("id", userId), MAPPER).stream().findFirst();
    }

    /** Inserts a sign-up account; false when the id already exists (unique on lower(user_id)). */
    public boolean insertSignup(String userId, String userNm, String clphNo, String cmpnNm, String pwHash, String langCd) {
        String now = WeInvoiceTime.nowDtm();
        int rows = db.jdbc().update(db.sql("""
                        INSERT INTO {app}.winv_user_infm
                               (user_id, user_nm, eml, clph_no, cmpn_nm, user_pw, lang_cd, reg_src, reg_dtm, mod_dtm)
                        VALUES (:id, :nm, :id, :clph, :cmpn, :pw, :lang, 'SIGNUP', :now, :now)
                        ON CONFLICT DO NOTHING
                        """),
                new MapSqlParameterSource("id", userId).addValue("nm", userNm).addValue("clph", clphNo)
                        .addValue("cmpn", cmpnNm).addValue("pw", pwHash).addValue("lang", langCd).addValue("now", now));
        return rows == 1;
    }

    public void recordSuccess(String userId) {
        String now = WeInvoiceTime.nowDtm();
        db.jdbc().update(db.sql("""
                        UPDATE {app}.winv_user_infm SET fail_cnt = 0, lst_lgn_dtm = :now, mod_dtm = :now
                         WHERE user_id = :id
                        """),
                new MapSqlParameterSource("id", userId).addValue("now", now));
    }

    /** Counts a wrong password and locks at the threshold; returns true when this attempt locked the account. */
    public boolean recordFailure(String userId) {
        Boolean locked = db.jdbc().queryForObject(db.sql("""
                        UPDATE {app}.winv_user_infm
                           SET fail_cnt = fail_cnt + 1,
                               lock_yn  = CASE WHEN fail_cnt + 1 >= :limit THEN 'Y' ELSE lock_yn END,
                               mod_dtm  = :now
                         WHERE user_id = :id
                        RETURNING lock_yn = 'Y'
                        """),
                new MapSqlParameterSource("id", userId).addValue("limit", LOCK_THRESHOLD)
                        .addValue("now", WeInvoiceTime.nowDtm()), Boolean.class);
        return Boolean.TRUE.equals(locked);
    }
}
