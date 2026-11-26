package com.kosign.wecms.domain.weinvoice.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Query contract of "Phát hành qua" (docs/bugs/weinvoice-provider-empty-for-cash-register-invoices-fix-2026-11-26.md):
 * written on the invoice first, then TTKhac MSTTCGP, the PDF adapter's provider, the seller's usual provider.
 */
class ProviderSqlTest {

    @Test
    @DisplayName("order: on the invoice → TTKhac → PDF adapter → seller's usual provider")
    void fallbackOrder() {
        String sql = ProviderSql.effective("sprv.m");
        int onInvoice = sql.indexOf("x.mst_tcgp");
        int ttkhac = sql.indexOf("'tt_khac'->>'MSTTCGP'");
        int adapter = sql.indexOf("einvoice_providers");
        int seller = sql.indexOf("sprv.m");
        assertThat(sql).startsWith("COALESCE(");
        assertThat(onInvoice).isPositive().isLessThan(ttkhac);
        assertThat(ttkhac).isLessThan(adapter);
        assertThat(adapter).isLessThan(seller);
    }

    @Test
    @DisplayName("PDF adapter: email: prefix stripped, parent MST preferred; TTKhac only when it looks like a tax code")
    void adapterAndTtkhac() {
        String sql = ProviderSql.effective("NULL");
        assertThat(sql).contains("regexp_replace(p.source_id, '^email:', '')", "ORDER BY length(a.m)", "('ipos', '0104789847')");
        assertThat(sql).contains("~ '^[0-9]{10}(-[0-9]{3})?$'");
    }

    @Test
    @DisplayName("seller's usual provider: once per query for the MST's sellers, parent MST, most frequent first")
    void sellerUsual() {
        assertThat(ProviderSql.SELLER_USUAL_CTE).startsWith("sprv AS (")
                .contains("i3.company_mst = :mst", "split_part(i2.nbmst, '-', 1)", "ORDER BY t.s, t.n DESC");
        assertThat(ProviderSql.SELLER_USUAL_JOIN).contains("sprv.s = split_part(i.nbmst, '-', 1)");
        assertThat(ProviderSql.SELLER_USUAL_ONE).contains("split_part(i2.nbmst, '-', 1) = split_part(i.nbmst, '-', 1)",
                "ORDER BY count(*) DESC");
    }
}
