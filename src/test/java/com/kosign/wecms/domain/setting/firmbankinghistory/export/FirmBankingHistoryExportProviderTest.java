package com.kosign.wecms.domain.setting.firmbankinghistory.export;

import com.kosign.wecms.common.export.ExcelExportRequest;
import com.kosign.wecms.common.export.ExcelSortSpec;
import com.kosign.wecms.common.export.ExportFetch;
import com.kosign.wecms.domain.setting.firmbankinghistory.dto.FirmBankingHistoryRow;
import com.kosign.wecms.domain.setting.firmbankinghistory.service.FirmBankingHistoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit coverage for the Firm Banking History Excel export provider —
 * verifies the registry contract ({@code screenId}, {@code allowedFieldIds}),
 * filter / sort wiring through to {@link FirmBankingHistoryService#listAll},
 * the FIELD_ID row mapping, and the subheader format. End-to-end POI
 * write + filename header tests live with the shared
 * {@code ExcelExportService} suite.
 */
@ExtendWith(MockitoExtension.class)
class FirmBankingHistoryExportProviderTest {

    @Mock FirmBankingHistoryService service;

    private FirmBankingHistoryExportProvider provider;

    @BeforeEach
    void setUp() {
        provider = new FirmBankingHistoryExportProvider(service);
    }

    @Test
    void screenIdMatchesRegistryConvention() {
        assertThat(provider.screenId()).isEqualTo("firm-banking-history");
    }

    @Test
    void allowedFieldIdsCoverTheSevenSeededColumns() {
        assertThat(provider.allowedFieldIds())
                .containsExactlyInAnyOrder(
                        "REG_DTM", "SVC_TYPE", "SVC_NM", "SEQ_NO",
                        "DEP_BANK_CD", "RS_CD", "RS_MSG");
    }

    @Test
    void fetchPassesFilterAndSortThroughToService() {
        when(service.listAll(
                eq("20260101"), eq("20260131"), eq("F"), eq("13768"),
                eq("REG_DTM"), eq("DESC")))
                .thenReturn(List.of());

        Map<String, Object> filters = new HashMap<>();
        filters.put("fromDate", "20260101");
        filters.put("toDate",   "20260131");
        filters.put("svcType",  "F");
        filters.put("keyword",  "13768");

        provider.fetch(new ExcelExportRequest(
                "firm-banking-history", "default",
                "Firm Banking History",
                "Firm Banking History",
                filters,
                new ExcelSortSpec("REG_DTM", "DESC"),
                null, null));

        verify(service).listAll(
                "20260101", "20260131", "F", "13768", "REG_DTM", "DESC");
    }

    @Test
    void fetchMapsRowsToFieldIdKeyedMaps() {
        when(service.listAll(any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(sampleRow()));

        ExportFetch fetch = provider.fetch(new ExcelExportRequest(
                "firm-banking-history", "default", "f", "t",
                Map.of("fromDate", "20260101", "toDate", "20260131"),
                null, null, null));

        assertThat(fetch.rows()).hasSize(1);
        Map<String, Object> first = fetch.rows().get(0);
        assertThat(first).containsEntry("REG_DTM",     "20260115101010");
        assertThat(first).containsEntry("SVC_TYPE",    "F");
        assertThat(first).containsEntry("SVC_NM",      "201");
        assertThat(first).containsEntry("SEQ_NO",      "13768");
        assertThat(first).containsEntry("DEP_BANK_CD", "Shinhan VN - 970424");
        assertThat(first).containsEntry("RS_CD",       "00000000");
        assertThat(first).containsEntry("RS_MSG",      "00000000");
    }

    @Test
    void fetchEmitsRecordCountSubHeader() {
        when(service.listAll(any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(sampleRow(), sampleRow()));

        ExportFetch fetch = provider.fetch(new ExcelExportRequest(
                "firm-banking-history", "default", "f", "t",
                Map.of("fromDate", "20260101", "toDate", "20260131"),
                null, null, null));

        assertThat(fetch.subHeader()).isEqualTo("2 record(s)");
    }

    @Test
    void fetchPassesNullSortFieldsWhenWireSortIsAbsent() {
        when(service.listAll(any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of());

        provider.fetch(new ExcelExportRequest(
                "firm-banking-history", "default", "f", "t",
                Map.of("fromDate", "20260101", "toDate", "20260131"),
                null, null, null));

        verify(service).listAll(
                eq("20260101"), eq("20260131"),
                (String) eq(null), (String) eq(null),
                (String) eq(null), (String) eq(null));
    }

    @Test
    void fetchHandlesNullFiltersMap() {
        when(service.listAll(any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of());

        ExportFetch fetch = provider.fetch(new ExcelExportRequest(
                "firm-banking-history", "default", "f", "t",
                null, null, null, null));

        assertThat(fetch.rows()).isEmpty();
        assertThat(fetch.subHeader()).isEqualTo("0 record(s)");
    }

    private static FirmBankingHistoryRow sampleRow() {
        return new FirmBankingHistoryRow(
                "20260115101010",
                "F",
                "settings.firm_banking_history.service_type.firm_banking_transaction",
                "201",
                "fb_history.service.201",
                "13768",
                "Shinhan VN - 970424",
                "00000000",
                "00000000",
                "201",
                null,
                "GV00000001");
    }
}
