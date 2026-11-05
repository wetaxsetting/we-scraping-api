package com.kosign.wecms.domain.weinvoice.repository;

import com.kosign.wecms.domain.weinvoice.config.WeInvoiceDb;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * {@code winv_prvd_infm} — the provider catalogue (screen 16), with how many invoices of the
 * tenant's MSTs each provider issued and whether the engine has an adapter for it
 * ({@code einvoice_providers.source_id}).
 */
@Repository
public class ProviderRepository {

    private final WeInvoiceDb db;

    public ProviderRepository(WeInvoiceDb db) {
        this.db = db;
    }

    /**
     * @param msts      tenant MSTs whose invoices are counted (empty = count nothing)
     * @param keyword   name / MST / URL fragment or {@code null}
     * @param linkState {@code ok} (lookup link, no review note), {@code check} (review note),
     *                  {@code missing} (no lookup link) or {@code null}
     */
    public List<ProviderRow> list(List<String> msts, String keyword, String linkState) {
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("msts", msts.isEmpty() ? List.of("") : msts);
        StringBuilder where = new StringBuilder(" WHERE pv.use_yn = 'Y'");
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND (pv.prvd_nm ILIKE :kw OR pv.prvd_full_nm ILIKE :kw OR pv.prvd_mst LIKE :kw")
                    .append(" OR pv.web_url ILIKE :kw OR pv.lkup_url ILIKE :kw)");
            p.addValue("kw", "%" + InvoiceQueryRepository.escapeLike(keyword.trim()) + "%");
        }
        if ("ok".equals(linkState)) where.append(" AND pv.lkup_url IS NOT NULL AND pv.vrfy_note IS NULL");
        if ("check".equals(linkState)) where.append(" AND pv.vrfy_note IS NOT NULL");
        if ("missing".equals(linkState)) where.append(" AND pv.lkup_url IS NULL");
        return db.jdbc().query(db.sql("""
                        WITH usage AS (
                            SELECT split_part(COALESCE(NULLIF(x.mst_tcgp, ''), i.list_raw->>'msttcgp'), '-', 1) AS prvd,
                                   count(*) AS n
                              FROM {eng}.invoices i
                              LEFT JOIN {eng}.invoice_xml x ON x.invoice_id = i.id
                             WHERE i.company_mst IN (:msts)
                             GROUP BY 1
                        ), adapters AS (
                            SELECT split_part(e.mst_tcgp, '-', 1) AS prvd, bool_or(e.source_id IS NOT NULL) AS auto_pdf
                              FROM {eng}.einvoice_providers e
                             GROUP BY 1
                        )
                        SELECT pv.prvd_mst, pv.disp_ord, pv.prvd_nm, pv.prvd_full_nm, pv.web_url, pv.lkup_url, pv.cptch_url,
                               pv.alt_lkup_urls::text AS alt_urls, pv.vrfy_note,
                               COALESCE(u.n, 0) AS invoice_count, COALESCE(a.auto_pdf, false) AS auto_pdf
                          FROM {app}.winv_prvd_infm pv
                          LEFT JOIN usage u ON u.prvd = pv.prvd_mst
                          LEFT JOIN adapters a ON a.prvd = pv.prvd_mst
                        """ + where + " ORDER BY pv.disp_ord, pv.prvd_mst"),
                p,
                (rs, n) -> new ProviderRow(
                        rs.getString("prvd_mst"),
                        rs.getInt("disp_ord"),
                        rs.getString("prvd_nm"),
                        rs.getString("prvd_full_nm"),
                        rs.getString("web_url"),
                        rs.getString("lkup_url"),
                        rs.getString("cptch_url"),
                        rs.getString("alt_urls"),
                        rs.getString("vrfy_note"),
                        rs.getLong("invoice_count"),
                        rs.getBoolean("auto_pdf")));
    }

    public record ProviderRow(String taxId, int order, String name, String fullName, String web, String lookupUrl,
                              String captchaUrl, String altLookupUrlsJson, String verifyNote, long invoiceCount,
                              boolean autoPdf) {
    }
}
