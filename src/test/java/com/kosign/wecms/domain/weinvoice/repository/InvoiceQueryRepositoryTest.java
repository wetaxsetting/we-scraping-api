package com.kosign.wecms.domain.weinvoice.repository;

import com.kosign.wecms.domain.weinvoice.WeInvoiceTestProps;
import com.kosign.wecms.domain.weinvoice.config.WeInvoiceDb;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SQL assembly of the invoice list. No database: the point is that every filter becomes a bound
 * parameter or a fixed fragment — the request never reaches the SQL text — and that the tenant /
 * MST scope is always present.
 */
class InvoiceQueryRepositoryTest {

    private static InvoiceCriteria criteria(String keyword, String provider, String kind) {
        return new InvoiceCriteria("BIZ", "1201496252", "mua", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31),
                keyword, "missing", "has", List.of(1, 6), kind, List.of(3L), provider, List.of("0101243150"),
                List.of(), InvoiceSortField.parse("issueDate"), true);
    }

    @Test
    void cteScopesToTheMstDirectionAndPeriod() {
        MapSqlParameterSource p = new MapSqlParameterSource();
        String sql = InvoiceQueryRepository.cte(criteria(null, null, null), p);
        assertThat(sql).contains("WHERE i.company_mst = :mst", "AND i.direction = :dir", "i.ngay_lap >= :from",
                "i.ngay_lap <= :to", "{eng}.invoices", "{app}.winv_invc_file");
        assertThat(p.getValue("mst")).isEqualTo("1201496252");
        assertThat(p.getValue("bizKey")).isEqualTo("BIZ");
        assertThat(p.getValue("dir")).isEqualTo("mua");
    }

    @Test
    void keywordIsBoundAndLikeEscaped() {
        MapSqlParameterSource p = new MapSqlParameterSource();
        String where = InvoiceQueryRepository.filterWhere(criteria("50%_off' OR 1=1 --", null, null), p);
        assertThat(where).contains("ILIKE :kw").doesNotContain("OR 1=1");
        assertThat(p.getValue("kw")).isEqualTo("%50\\%\\_off' OR 1=1 --%");
    }

    @Test
    void filtersMapToFixedFragments() {
        MapSqlParameterSource p = new MapSqlParameterSource();
        String where = InvoiceQueryRepository.filterWhere(criteria(null, "0101243150-001", "cash_register"), p);
        assertThat(where).contains("NOT c.has_xml", "AND c.has_pdf", "c.tthai_eff IN (:tthais)",
                "upper(substr(c.khhdon, 4, 1)) = 'M'", "t.tag_seq IN (:tagIds)", "split_part(c.prvd_mst, '-', 1) = :prvd",
                "c.ptnr_mst NOT IN (:excl)");
        assertThat(p.getValue("prvd")).isEqualTo("0101243150");
    }

    @Test
    void providerSpecialValues() {
        MapSqlParameterSource p = new MapSqlParameterSource();
        assertThat(InvoiceQueryRepository.filterWhere(criteria(null, "no_code", null), p)).contains("c.prvd_mst IS NULL");
        assertThat(InvoiceQueryRepository.filterWhere(criteria(null, "unknown", null), p)).contains("NOT EXISTS");
    }

    @Test
    void sortWhitelistAcceptsBothSpellingsAndKeepsPagesStable() {
        assertThat(InvoiceSortField.parse("issue_date")).isEqualTo(InvoiceSortField.ISSUE_DATE);
        assertThat(InvoiceSortField.parse("preTax")).isEqualTo(InvoiceSortField.PRE_TAX);
        assertThat(InvoiceSortField.parse("c.ngay_lap; DROP")).isNull();
        assertThat(InvoiceSortField.NUMBER.orderBy(false)).isEqualTo("c.khhdon ASC NULLS LAST, c.shdon ASC NULLS LAST, c.id ASC");
    }

    @Test
    void schemaPlaceholdersResolveToConfiguredNames() {
        WeInvoiceDb db = new WeInvoiceDb(WeInvoiceTestProps.disabled());
        MapSqlParameterSource p = new MapSqlParameterSource();
        String sql = db.sql(InvoiceQueryRepository.cte(criteria(null, null, null), p)
                + InvoiceQueryRepository.filterWhere(criteria(null, null, null), p));
        assertThat(sql).doesNotContain("{eng}", "{app}").contains("public.invoices", "weinvoice.winv_invc_tag");
    }
}
