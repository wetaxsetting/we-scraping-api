package com.kosign.wecms.common.export;

import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.domain.setting.service.ScreenOptionService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExcelExportServiceErpAuditTest {
    static class FakeProvider implements ExportRowProvider {
        final boolean audits;
        final AtomicInteger calls = new AtomicInteger();
        String lastName;
        FakeProvider(boolean a) { this.audits = a; }
        public String screenId() { return "x-result"; }
        public Set<String> allowedFieldIds() { return Set.of("REG_DTM"); }
        public ExportFetch fetch(ExcelExportRequest r) {
            return ExportFetch.of(List.of(Map.of("REG_DTM", "2026")), "1 row");
        }
        public boolean auditsErpExport() { return audits; }
        public void recordErpExport(Path f, String n) { calls.incrementAndGet(); lastName = n; }
    }
    private ExcelExportRequest req() {
        return new ExcelExportRequest("x-result", "default", "File", "Title", Map.of(), null,
                List.of(new ExcelColumnSpec("REG_DTM", "Date", "TEXT", null, null, null)), null);
    }
    private ExcelExportService svc(ExportRowProvider p) {
        ExportableScreenRegistry reg = mock(ExportableScreenRegistry.class);
        when(reg.require(anyString())).thenReturn(p);
        ScreenOptionService sos = mock(ScreenOptionService.class);
        when(sos.load(anyString(), anyString())).thenThrow(new BusinessException("WBE2001"));
        return new ExcelExportService(reg, sos, new ExcelWorkbookWriter());
    }
    @Test void auditing_provider_records_exactly_once_and_still_streams() {
        FakeProvider p = new FakeProvider(true);
        MockHttpServletResponse resp = new MockHttpServletResponse();
        svc(p).exportTo(resp, req());
        assertThat(p.calls.get()).isEqualTo(1);
        assertThat(p.lastName).isEqualTo("File.xlsx");
        assertThat(resp.getContentAsByteArray().length).isGreaterThan(0);
    }
    @Test void non_auditing_provider_never_records_and_still_streams() {
        FakeProvider p = new FakeProvider(false);
        MockHttpServletResponse resp = new MockHttpServletResponse();
        svc(p).exportTo(resp, req());
        assertThat(p.calls.get()).isZero();
        assertThat(resp.getContentAsByteArray().length).isGreaterThan(0);
    }
}
