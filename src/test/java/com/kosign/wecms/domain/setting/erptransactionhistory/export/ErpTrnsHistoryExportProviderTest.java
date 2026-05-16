package com.kosign.wecms.domain.setting.erptransactionhistory.export;

import com.kosign.wecms.common.export.ExcelExportRequest;
import com.kosign.wecms.common.export.ExcelSortSpec;
import com.kosign.wecms.common.export.ExportFetch;
import com.kosign.wecms.domain.setting.erptransactionhistory.dto.ErpTrnsHistoryRow;
import com.kosign.wecms.domain.setting.erptransactionhistory.service.ErpTrnsHistoryService;
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
 * Unit coverage for the ERP Transaction History Excel export provider —
 * verifies the registry contract ({@code screenId}, {@code allowedFieldIds}),
 * filter / sort wiring through to {@link ErpTrnsHistoryService#listAll},
 * the FIELD_ID row mapping, and the subheader format.
 */
@ExtendWith(MockitoExtension.class)
class ErpTrnsHistoryExportProviderTest {

    @Mock ErpTrnsHistoryService service;

    private ErpTrnsHistoryExportProvider provider;

    @BeforeEach
    void setUp() {
        provider = new ErpTrnsHistoryExportProvider(service);
    }

    @Test
    void screenIdMatchesRegistryConvention() {
        assertThat(provider.screenId()).isEqualTo("erp-transaction-history");
    }

    @Test
    void allowedFieldIdsCoverTheSevenSeededColumns() {
        assertThat(provider.allowedFieldIds())
                .containsExactlyInAnyOrder(
                        "REG_DTM", "ERP_TYPE", "TRNS_TYPE_CD",
                        "ORG_FILE_NM", "FILE_SIZE", "REG_USER", "REG_ID");
    }

    @Test
    void fetchPassesFilterAndSortThroughToService() {
        when(service.listAll(
                eq("20260101"), eq("20260131"), eq("W"), eq("09"), eq("alice"),
                eq("REG_DTM"), eq("DESC")))
                .thenReturn(List.of());

        Map<String, Object> filters = new HashMap<>();
        filters.put("fromDate",   "20260101");
        filters.put("toDate",     "20260131");
        filters.put("erpType",    "W");
        filters.put("trnsTypeCd", "09");
        filters.put("keyword",    "alice");

        provider.fetch(new ExcelExportRequest(
                "erp-transaction-history", "default",
                "ERP Transaction History",
                "ERP Transaction History",
                filters,
                new ExcelSortSpec("REG_DTM", "DESC"),
                null, null));

        verify(service).listAll(
                "20260101", "20260131", "W", "09", "alice", "REG_DTM", "DESC");
    }

    @Test
    void fetchMapsRowsToFieldIdKeyedMaps() {
        when(service.listAll(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(sampleRow()));

        ExportFetch fetch = provider.fetch(new ExcelExportRequest(
                "erp-transaction-history", "default", "f", "t",
                Map.of("fromDate", "20260101", "toDate", "20260131"),
                null, null, null));

        assertThat(fetch.rows()).hasSize(1);
        Map<String, Object> first = fetch.rows().get(0);
        assertThat(first).containsEntry("REG_DTM",      "20260115101010");
        assertThat(first).containsEntry("ERP_TYPE",     "W");
        assertThat(first).containsEntry("TRNS_TYPE_CD", "09");
        assertThat(first).containsEntry("ORG_FILE_NM",  "account_inquiry_vnd.xlsx");
        // FILE_SIZE stays as a raw Long so Excel's "number" format can re-sum.
        assertThat(first).containsEntry("FILE_SIZE",    12345L);
        assertThat(first).containsEntry("REG_USER",     "Alice Nguyen");
        assertThat(first).containsEntry("REG_ID",       "alice");
    }

    @Test
    void fetchEmitsRecordCountSubHeader() {
        when(service.listAll(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(sampleRow(), sampleRow(), sampleRow()));

        ExportFetch fetch = provider.fetch(new ExcelExportRequest(
                "erp-transaction-history", "default", "f", "t",
                Map.of("fromDate", "20260101", "toDate", "20260131"),
                null, null, null));

        assertThat(fetch.subHeader()).isEqualTo("3 record(s)");
    }

    @Test
    void fetchHandlesNullFiltersMap() {
        when(service.listAll(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of());

        ExportFetch fetch = provider.fetch(new ExcelExportRequest(
                "erp-transaction-history", "default", "f", "t",
                null, null, null, null));

        assertThat(fetch.rows()).isEmpty();
        assertThat(fetch.subHeader()).isEqualTo("0 record(s)");
    }

    private static ErpTrnsHistoryRow sampleRow() {
        return new ErpTrnsHistoryRow(
                100L,
                "account_inquiry_vnd.xlsx",
                "20260115101010_account_inquiry_vnd.xlsx",
                12345L,
                "W",
                "settings.erp_transaction_history.direction.wecms_to_erp",
                "09",
                "settings.erp_transaction_history.type.09",
                "20260115101010",
                "alice",
                "Alice Nguyen");
    }
}
