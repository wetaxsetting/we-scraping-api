package com.kosign.wecms.domain.setting.vasaccount.export;

import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.export.ExcelExportRequest;
import com.kosign.wecms.common.export.ExcelSortSpec;
import com.kosign.wecms.common.export.ExportFetch;
import com.kosign.wecms.common.i18n.MessageResolver;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.setting.vasaccount.dto.VasAccountRow;
import com.kosign.wecms.domain.setting.vasaccount.service.VasAccountService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit-level coverage for the VAS Account Excel export provider —
 * verifies the registry contract ({@code screenId}, {@code allowedFieldIds}),
 * filter / sort wiring through to {@link VasAccountService#listAllForExport},
 * and per-cell label resolution (Type / Status codes → localized labels
 * via {@link MessageResolver}). End-to-end POI write + filename header
 * tests live with the shared {@code ExcelExportService} suite.
 */
@ExtendWith(MockitoExtension.class)
class VasAccountExportProviderTest {

    @Mock VasAccountService service;
    @Mock MessageResolver   messages;

    private VasAccountExportProvider provider;

    private static final String BIZ  = "VN_VNM0004_000001";
    private static final String USER = "tester@example.com";

    @BeforeEach
    void setUp() {
        provider = new VasAccountExportProvider(service, messages);
        TenantContext.set(USER, USER, BIZ, LangCd.EN);

        // Echo the key as the resolved text — lets the test assert
        // which key was looked up without baking real translations in.
        lenient().when(messages.resolve(anyString(), any(Locale.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void screenIdMatchesRegistryConvention() {
        assertThat(provider.screenId()).isEqualTo("vas-account");
    }

    @Test
    void allowedFieldIdsCoverTheFourSeededColumns() {
        assertThat(provider.allowedFieldIds())
                .containsExactlyInAnyOrder(
                        "VAS_ACCT_CD", "VAS_ACCT_NM", "VAS_ACCT_TYPE", "STTS");
    }

    @Test
    void fetchPassesFilterAndSortThroughToService() {
        when(service.listAllForExport(eq("Tien"), eq(List.of("A")), eq("VAS_ACCT_CD"), eq("DESC")))
                .thenReturn(List.of());

        Map<String, Object> filters = new HashMap<>();
        filters.put("searchWd", "Tien");
        filters.put("statuses", List.of("A"));

        provider.fetch(new ExcelExportRequest(
                "vas-account", "default",
                "VAS Account export",
                "VAS Account",
                filters,
                new ExcelSortSpec("VAS_ACCT_CD", "DESC"),
                null, null));

        verify(service).listAllForExport("Tien", List.of("A"), "VAS_ACCT_CD", "DESC");
    }

    @Test
    void fetchCoercesCommaSeparatedStatuses() {
        when(service.listAllForExport(any(), any(), any(), any())).thenReturn(List.of());

        Map<String, Object> filters = new HashMap<>();
        filters.put("statuses", "A, I");

        provider.fetch(new ExcelExportRequest(
                "vas-account", "default", "f", "t", filters, null, null, null));

        verify(service).listAllForExport(any(), eq(List.of("A", "I")), any(), any());
    }

    @Test
    void fetchEmitsLocalizedTypeAndStatusLabels() {
        when(service.listAllForExport(any(), any(), any(), any()))
                .thenReturn(List.of(row("111", "Tiền mặt", "0", "A")));

        ExportFetch fetch = provider.fetch(new ExcelExportRequest(
                "vas-account", "default", "f", "t",
                Map.of(), null, null, null));

        assertThat(fetch.rows()).hasSize(1);
        Map<String, Object> first = fetch.rows().get(0);
        assertThat(first).containsEntry("VAS_ACCT_CD", "111");
        assertThat(first).containsEntry("VAS_ACCT_NM", "Tiền mặt");
        // Resolver echoes the key — verifies the provider asked for the
        // right one. Real labels come from messages.properties.
        assertThat(first).containsEntry("VAS_ACCT_TYPE", "settings.vas_account.type.dual");
        assertThat(first).containsEntry("STTS", "settings.vas_account.status.active");
    }

    @Test
    void fetchFallsBackToRawCodeWhenTypeUnknown() {
        when(service.listAllForExport(any(), any(), any(), any()))
                .thenReturn(List.of(row("999", "Bogus", "9", "X")));

        ExportFetch fetch = provider.fetch(new ExcelExportRequest(
                "vas-account", "default", "f", "t",
                Map.of(), null, null, null));

        Map<String, Object> first = fetch.rows().get(0);
        // Unknown code → raw code passes through (defensive — should
        // never happen with seeded data but protects against corrupt rows).
        assertThat(first).containsEntry("VAS_ACCT_TYPE", "9");
        assertThat(first).containsEntry("STTS", "X");
    }

    @Test
    void subHeaderUsesAuditActivityKeyForTheNoun() {
        when(service.listAllForExport(any(), any(), any(), any()))
                .thenReturn(List.of(
                        row("1", "Cash", "0", "A"),
                        row("2", "Receivable", "1", "A")));

        ExportFetch fetch = provider.fetch(new ExcelExportRequest(
                "vas-account", "default", "f", "t",
                Map.of(), null, null, null));

        // Resolver echoes — subheader is "<count> <noun>(s)".
        assertThat(fetch.subHeader())
                .isEqualTo("2 audit.vas_account.activity(s)");
    }

    @Test
    void fetchPassesNullSortFieldsWhenWireSortIsAbsent() {
        when(service.listAllForExport(any(), any(), any(), any())).thenReturn(List.of());

        provider.fetch(new ExcelExportRequest(
                "vas-account", "default", "f", "t", Map.of(), null, null, null));

        ArgumentCaptor<String> field = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> direction = ArgumentCaptor.forClass(String.class);
        verify(service).listAllForExport(any(), any(), field.capture(), direction.capture());
        assertThat(field.getValue()).isNull();
        assertThat(direction.getValue()).isNull();
    }

    private static VasAccountRow row(String cd, String nm, String type, String stts) {
        return new VasAccountRow(
                "100", cd, nm, null,
                type, "settings.vas_account.type." + (type.equals("0") ? "dual" : "x"),
                stts, "settings.vas_account.status." + (stts.equals("A") ? "active" : "x"),
                null, USER, "20260101000000");
    }
}
