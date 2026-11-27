package com.kosign.wecms.domain.weinvoice.repository;

import com.kosign.wecms.domain.weinvoice.common.CompanyAccessGuard;
import com.kosign.wecms.domain.weinvoice.config.WeInvoiceDb;
import com.kosign.wecms.domain.weinvoice.dto.InvoiceFacets;
import com.kosign.wecms.domain.weinvoice.dto.InvoiceFilter;
import com.kosign.wecms.domain.weinvoice.dto.InvoiceKpis;
import com.kosign.wecms.domain.weinvoice.dto.InvoiceListResponse;
import com.kosign.wecms.domain.weinvoice.repository.CompanyConnectionRepository.CompanyRow;
import com.kosign.wecms.domain.weinvoice.service.InvoiceQueryService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Server-side list rules the web needs to load the invoice list page by page
 * (docs/issues/weinvoice-invoice-list-server-paging-2026-11-18.md): the list card's missing-XML modes, missing PDF
 * without replaced / cancelled, the "needs a lookup code" provider filter, partners + needs-code facets and the
 * "Cả kỳ" period counts. No database: SQL fragments and mapping are checked.
 */
class InvoiceListServerPagingTest {

    private static InvoiceCriteria criteria(String xml, String pdf, String provider) {
        return new InvoiceCriteria("u@x", "1201496252", "mua", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31),
                null, xml, pdf, List.of(), null, List.of(), provider, List.of(), List.of(), null, true);
    }

    @Test
    void missingXmlModesAndMissingPdfLeaveReplacedAndCancelledOut() {
        MapSqlParameterSource p = new MapSqlParameterSource();
        assertThat(InvoiceQueryRepository.filterWhere(criteria("missing_all", null, null), p))
                .contains("NOT c.has_xml AND c.tthai_eff NOT IN (4, 6)").doesNotContain("<> 'K'");
        assertThat(InvoiceQueryRepository.filterWhere(criteria("missing_coded", null, null), p))
                .contains("NOT c.has_xml AND c.tthai_eff NOT IN (4, 6) AND upper(left(c.khhdon, 1)) <> 'K'");
        assertThat(InvoiceQueryRepository.filterWhere(criteria(null, "missing", null), p))
                .contains("NOT c.has_pdf AND c.tthai_eff NOT IN (4, 6)");
        // the generic XML select keeps its meaning
        assertThat(InvoiceQueryRepository.filterWhere(criteria("missing", null, null), p))
                .contains(" AND NOT c.has_xml").doesNotContain("NOT c.has_xml AND c.tthai_eff");
    }

    @Test
    void needsCodeProviderFilterMatchesTheRowFlag() {
        MapSqlParameterSource p = new MapSqlParameterSource();
        String where = InvoiceQueryRepository.filterWhere(criteria(null, null, "needs_code"), p);
        assertThat(where).contains("c.prvd_mst_xml IS NOT NULL", "{app}.winv_prvd_infm pv3",
                "NULLIF(btrim(c.ma_tra_cuu), '') IS NULL", "NULLIF(btrim(c.lookup_url), '') IS NULL",
                "{app}.winv_lkup_cd lc3", "lc3.owner_id = :ownerId");
        assertThat(where).doesNotContain(":prvd");
    }

    @Test
    void facetsAddPartnersAndTheNeedsCodeCount() throws Exception {
        WeInvoiceDb db = mock(WeInvoiceDb.class);
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        when(db.sql(anyString())).thenAnswer(inv -> inv.getArgument(0));
        when(db.jdbc()).thenReturn(jdbc);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> params = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        List<Object[]> rows = List.of(
                new Object[]{"provider", "0101243150", "MISA", true, 4L},
                new Object[]{"needs_code", "needs_code", null, null, 7L},
                new Object[]{"partner", "0300000002", "Beta", null, 2L},
                new Object[]{"partner", "0300000001", "alpha", null, 9L},
                new Object[]{"kind", "normal", null, null, 11L});
        doAnswer(inv -> {
            RowCallbackHandler h = inv.getArgument(2);
            for (Object[] r : rows) {
                ResultSet rs = mock(ResultSet.class);
                when(rs.getString("facet")).thenReturn((String) r[0]);
                when(rs.getString("k")).thenReturn((String) r[1]);
                when(rs.getString("label")).thenReturn((String) r[2]);
                when(rs.getBoolean("known")).thenReturn(Boolean.TRUE.equals(r[3]));
                when(rs.getLong("n")).thenReturn((Long) r[4]);
                h.processRow(rs);
            }
            return null;
        }).when(jdbc).query(sql.capture(), params.capture(), any(RowCallbackHandler.class));

        InvoiceFacets f = new InvoiceQueryRepository(db).facets(criteria(null, null, null));

        assertThat(sql.getValue()).contains("SELECT 'needs_code'", "SELECT 'partner', c.ptnr_mst", "LIMIT :partnerLimit");
        assertThat(params.getValue().getValue("partnerLimit")).isEqualTo(InvoiceQueryRepository.MAX_PARTNERS);
        assertThat(f.needsCode()).isEqualTo(7);
        assertThat(f.partners()).extracting(InvoiceFacets.PartnerCount::name).containsExactly("alpha", "Beta");
        assertThat(f.providers()).hasSize(1);
        assertThat(f.kinds()).hasSize(1);
    }

    @Test
    void listCarriesThePeriodCountsOfBothDirectionsAndAcceptsTheNewFilterValues() {
        CompanyAccessGuard guard = mock(CompanyAccessGuard.class);
        InvoiceQueryRepository repo = mock(InvoiceQueryRepository.class);
        when(guard.requireCompany("1201496252")).thenReturn(new CompanyRow("u@x", "1201496252", "CTY", "", "u",
                true, "CONNECTED", null, null, true, false, null, null));
        when(guard.ownerId()).thenReturn("u@x");
        when(repo.kpis(any())).thenReturn(new InvoiceKpis(3, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, 1, 1, 1,
                0, 3, BigDecimal.ZERO, BigDecimal.ONE, 2, 0, 0, BigDecimal.ZERO, List.of()));
        when(repo.periodCounts(eq("1201496252"), any(), any())).thenReturn(Map.of("ban", 246L, "mua", 846L));
        when(repo.list(any(), anyInt(), anyInt())).thenReturn(List.of());
        when(repo.incompletePeriods(anyString(), any(), any(), any())).thenReturn(List.of());
        InvoiceQueryService service = new InvoiceQueryService(guard, repo, null, null, null, null);

        InvoiceListResponse r = service.list(new InvoiceFilter("1201496252", "purchase", "2026-01-01", "2026-12-31",
                null, "missing_all", "missing", null, null, null, "needs_code", null, null, null, null), 0, 20);

        assertThat(r.kpis().periodSalesCount()).isEqualTo(246);
        assertThat(r.kpis().periodPurchaseCount()).isEqualTo(846);
        assertThat(r.kpis().missingXmlAll()).isEqualTo(2);
        InvoiceCriteria c = service.criteria(new InvoiceFilter("1201496252", null, null, null, null, "MISSING_CODED",
                null, null, null, null, "needs_code", null, null, null, null));
        assertThat(c.xml()).isEqualTo("missing_coded");
        assertThat(c.provider()).isEqualTo("needs_code");
        assertThatThrownBy(() -> service.criteria(new InvoiceFilter("1201496252", null, null, null, null, "bogus",
                null, null, null, null, null, null, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("missing_all");
    }
}
