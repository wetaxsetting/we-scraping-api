package com.kosign.wecms.domain.weinvoice.repository;

import com.kosign.wecms.domain.weinvoice.dto.InvoiceRow;
import org.junit.jupiter.api.Test;

import java.sql.ResultSet;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** List row lookup code: same resolution as the detail — hand-typed code / link wins over the XML's. */
class InvoiceRowLookupMappingTest {

    private static InvoiceRow map(Map<String, String> cols) throws Exception {
        ResultSet rs = mock(ResultSet.class);
        // prvd_mst_xml (provider written on the invoice) defaults to prvd_mst unless the test gives it
        when(rs.getString(anyString())).thenAnswer(inv -> {
            String k = inv.getArgument(0);
            return "prvd_mst_xml".equals(k) && !cols.containsKey(k) ? cols.get("prvd_mst") : cols.get(k);
        });
        when(rs.getString("khhdon")).thenReturn("C26TWE");
        when(rs.getInt("khmshdon")).thenReturn(1);
        when(rs.getLong("shdon")).thenReturn(4L);
        when(rs.getInt("tthai_eff")).thenReturn(1);
        when(rs.getBoolean("has_pdf")).thenReturn("Y".equals(cols.get("has_pdf")));
        return InvoiceQueryRepository.ROW_MAPPER.mapRow(rs, 0);
    }

    @Test
    void codeFromTheXmlIsCarriedAndNoWarning() throws Exception {
        InvoiceRow r = map(Map.of("prvd_mst", "0101243150", "pv_mst", "0101243150", "ma_tra_cuu", "EPF5HRV8KB2X"));
        assertThat(r.needsLookupCode()).isFalse();
        assertThat(r.lookupCode()).isEqualTo("EPF5HRV8KB2X");
        assertThat(r.lookupUrl()).isNull();
        assertThat(r.lookupCodeCompany()).isNull();
    }

    @Test
    void handTypedCodeWinsOverTheXml() throws Exception {
        InvoiceRow r = map(Map.of("prvd_mst", "0312303803", "pv_mst", "0312303803", "ma_tra_cuu", "XMLCODE",
                "manual_code", "ABC123", "manual_cd", "ABC123", "manual_cmpn_cd", "WIN01"));
        assertThat(r.lookupCode()).isEqualTo("ABC123");
        assertThat(r.lookupCodeCompany()).isEqualTo("WIN01");
    }

    @Test
    void knownProviderWithoutAnyCodeNeedsOne() throws Exception {
        InvoiceRow r = map(Map.of("prvd_mst", "0104128565", "pv_mst", "0104128565", "ma_tra_cuu", " "));   // FPT
        assertThat(r.needsLookupCode()).isTrue();
        assertThat(r.lookupCode()).isNull();
    }

    // docs/issues/weinvoice-needs-lookup-code-by-provider-2026-11-18.md
    @Test
    void noFlagOnceThePdfIsStoredOrWhenTheProviderLooksUpWithoutACode() throws Exception {
        assertThat(map(Map.of("prvd_mst", "0104128565", "pv_mst", "0104128565", "has_pdf", "Y")).needsLookupCode()).isFalse();
        // BKAV looks invoices up by DLHDon/@Id
        assertThat(map(Map.of("prvd_mst", "0101360697", "pv_mst", "0101360697")).needsLookupCode()).isFalse();
    }

    // docs/bugs/weinvoice-provider-empty-for-cash-register-invoices-fix-2026-11-26.md
    @Test
    void inferredProviderIsShownButNeverAsksForACode() throws Exception {
        // cash-register invoice: no MSTTCGP on it, provider inferred (FPT, a provider that needs a code)
        InvoiceRow r = map(new java.util.HashMap<>(Map.of("prvd_mst", "0104128565", "pv_mst", "0104128565",
                "prvd_nm", "FPT")) {{ put("prvd_mst_xml", null); }});
        assertThat(r.providerTaxId()).isEqualTo("0104128565");
        assertThat(r.provider()).isEqualTo("FPT");
        assertThat(r.needsLookupCode()).isFalse();
    }
}
