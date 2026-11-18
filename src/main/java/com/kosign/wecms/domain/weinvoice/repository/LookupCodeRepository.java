package com.kosign.wecms.domain.weinvoice.repository;

import com.kosign.wecms.domain.weinvoice.common.WeInvoiceTime;
import com.kosign.wecms.domain.weinvoice.config.WeInvoiceDb;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Repository;

/** {@code winv_lkup_cd} — lookup code typed by a user; overrides the code guessed from the XML. */
@Repository
public class LookupCodeRepository {

    private final WeInvoiceDb db;

    public LookupCodeRepository(WeInvoiceDb db) {
        this.db = db;
    }

    public void upsert(String ownerId, long invoiceId, String mst, String code, String companyCode, String url,
                       String userId) {
        String dtm = WeInvoiceTime.nowDtm();
        db.jdbc().update(db.sql("""
                INSERT INTO {app}.winv_lkup_cd
                       (owner_id, invc_id, cmpn_mst, lkup_cd, cmpn_cd, lkup_url, reg_id, reg_dtm, mod_id, mod_dtm)
                VALUES (:ownerId, :id, :mst, :code, :cmpnCd, :url, :userId, :dtm, :userId, :dtm)
                ON CONFLICT (owner_id, invc_id) DO UPDATE
                   SET lkup_cd = EXCLUDED.lkup_cd, cmpn_cd = EXCLUDED.cmpn_cd, lkup_url = EXCLUDED.lkup_url,
                       mod_id = EXCLUDED.mod_id, mod_dtm = EXCLUDED.mod_dtm
                """), new MapSqlParameterSource()
                .addValue("ownerId", ownerId)
                .addValue("id", invoiceId)
                .addValue("mst", mst)
                .addValue("code", code)
                .addValue("cmpnCd", companyCode)
                .addValue("url", url)
                .addValue("userId", userId)
                .addValue("dtm", dtm));
    }
}
