package com.kosign.wecms.domain.weinvoice.repository;

import com.kosign.wecms.domain.weinvoice.common.CompanyAccessGuard;
import com.kosign.wecms.domain.weinvoice.config.WeInvoiceDb;
import com.kosign.wecms.domain.weinvoice.dto.DashboardResponse;
import com.kosign.wecms.domain.weinvoice.repository.CompanyConnectionRepository.CompanyRow;
import com.kosign.wecms.domain.weinvoice.service.DashboardService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Dashboard "data status" per direction (we-scraping-api docs/issues/weinvoice-dashboard-data-status-by-direction-2026-11-17.md):
 * the dashboard row "HĐ có mã thiếu XML" added purchases and sales together but opened the purchase list, so an MST whose
 * missing XML were all sales showed a large number and an empty list. No database: the SQL text and the mapping are checked.
 */
class DashboardRepositoryDataStatusTest {

    private static final Map<String, Long> COUNTS = Map.of(
            "missing_xml", 60L, "missing_xml_purchase", 10L, "missing_xml_sales", 50L, "no_code_no_xml", 3L,
            "missing_pdf", 70L, "missing_pdf_purchase", 20L, "missing_pdf_sales", 50L);

    @SuppressWarnings("unchecked")
    private static DashboardRepository repoCapturing(ArgumentCaptor<String> sql) throws Exception {
        WeInvoiceDb db = mock(WeInvoiceDb.class);
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        when(db.sql(anyString())).thenAnswer(inv -> inv.getArgument(0));
        when(db.jdbc()).thenReturn(jdbc);
        ResultSet rs = mock(ResultSet.class);
        for (Map.Entry<String, Long> e : COUNTS.entrySet()) {
            when(rs.getLong(e.getKey())).thenReturn(e.getValue());
        }
        when(jdbc.queryForObject(sql.capture(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenAnswer(inv -> ((RowMapper<Object>) inv.getArgument(2)).mapRow(rs, 0));
        return new DashboardRepository(db);
    }

    @Test
    void dataStatusSplitsMissingXmlAndPdfByDirection_sameRulesAsTheTotals() throws Exception {
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        DashboardRepository repo = repoCapturing(sql);

        DashboardRepository.DataStatusRow row = repo.dataStatus("u@x", "3603379922",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));

        assertThat(sql.getValue()).contains(
                "count(*) FILTER (WHERE x.coded_no_xml AND c.direction = 'mua') AS missing_xml_purchase",
                "count(*) FILTER (WHERE x.coded_no_xml AND c.direction = 'ban') AS missing_xml_sales",
                "count(*) FILTER (WHERE NOT c.has_pdf AND c.direction = 'mua') AS missing_pdf_purchase",
                "count(*) FILTER (WHERE NOT c.has_pdf AND c.direction = 'ban') AS missing_pdf_sales",
                "NOT c.has_xml AND upper(left(c.khhdon, 1)) <> 'K' AS coded_no_xml",
                "WHERE c.tthai_eff NOT IN (4, 6)");
        assertThat(row).isEqualTo(new DashboardRepository.DataStatusRow(60, 10, 50, 3, 70, 20, 50));
    }

    @Test
    void serviceCarriesThePerDirectionCountsIntoTheResponse() {
        CompanyAccessGuard guard = mock(CompanyAccessGuard.class);
        DashboardRepository dashboard = mock(DashboardRepository.class);
        InvoiceQueryRepository invoices = mock(InvoiceQueryRepository.class);
        when(guard.requireCompany("3603379922")).thenReturn(new CompanyRow("u@x", "3603379922", "CTY", "", "u",
                true, "CONNECTED", null, null, true, false, null, null));
        when(guard.ownerId()).thenReturn("u@x");
        when(dashboard.monthly(anyString(), anyString(), any(), any())).thenReturn(List.of());
        when(dashboard.topSuppliers(anyString(), anyString(), any(), any(), anyInt())).thenReturn(List.of());
        when(dashboard.lastRun("3603379922")).thenReturn(Optional.empty());
        when(dashboard.dataStatus(eq("u@x"), eq("3603379922"), any(), any()))
                .thenReturn(new DashboardRepository.DataStatusRow(60, 10, 50, 3, 70, 20, 50));
        when(invoices.incompletePeriods(anyString(), any(), anyString(), anyString())).thenReturn(List.of());

        DashboardResponse.DataStatus ds = new DashboardService(guard, dashboard, invoices).get("3603379922", 2026)
                .dataStatus();

        assertThat(ds).isEqualTo(new DashboardResponse.DataStatus(60, 10, 50, 3, 70, 20, 50, List.of()));
    }
}
