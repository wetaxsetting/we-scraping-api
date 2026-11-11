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
        when(rs.getString(anyString())).thenAnswer(inv -> cols.get(inv.<String>getArgument(0)));
        when(rs.getString("khhdon")).thenReturn("C26TWE");
        when(rs.getInt("khmshdon")).thenReturn(1);
        when(rs.getLong("shdon")).thenReturn(4L);
        when(rs.getInt("tthai_eff")).thenReturn(1);
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
        InvoiceRow r = map(Map.of("prvd_mst", "0101360697", "pv_mst", "0101360697", "ma_tra_cuu", " "));
        assertThat(r.needsLookupCode()).isTrue();
        assertThat(r.lookupCode()).isNull();
    }
}
