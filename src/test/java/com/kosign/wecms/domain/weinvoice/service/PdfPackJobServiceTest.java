package com.kosign.wecms.domain.weinvoice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.domain.weinvoice.common.CompanyAccessGuard;
import com.kosign.wecms.domain.weinvoice.common.StoredFiles;
import com.kosign.wecms.domain.weinvoice.common.WeInvoiceJson;
import com.kosign.wecms.domain.weinvoice.dto.PdfJobRequest;
import com.kosign.wecms.domain.weinvoice.dto.PdfJobStatus;
import com.kosign.wecms.domain.weinvoice.repository.CompanyConnectionRepository.CompanyRow;
import com.kosign.wecms.domain.weinvoice.repository.EngineRequestRepository;
import com.kosign.wecms.domain.weinvoice.repository.InvoiceDetailRepository;
import com.kosign.wecms.domain.weinvoice.repository.PackRepository;
import com.kosign.wecms.domain.weinvoice.repository.PackRepository.PackRow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Bulk PDF job (UI Spec v1.1 §3.12): runs on the server, one ZIP; owner-scoped; nothing live when not needed. */
class PdfPackJobServiceTest {

    @TempDir
    Path tmp;

    @Test
    void packsOriginalAndFallback_withoutAskingTheEngine() throws Exception {
        Path pdf = tmp.resolve("p.pdf");
        Files.write(pdf, RenderedInvoicePdf.render(PdfPackBuilderTest.row(1, "C26TAA", null, null, null, null), List.of()));
        CompanyAccessGuard guard = mock(CompanyAccessGuard.class);
        when(guard.requireCompany("0101234567")).thenReturn(new CompanyRow("owner", "0101234567", "A", null, null, true,
                "CONNECTED", null, null, true, false, null, null));
        when(guard.ownerId()).thenReturn("owner");
        when(guard.userId()).thenReturn("user");
        InvoiceExportService exports = mock(InvoiceExportService.class);
        when(exports.scopeIds(eq("0101234567"), any(), any())).thenReturn(List.of(1L, 2L));
        PackRepository pack = mock(PackRepository.class);
        PackRow original = PdfPackBuilderTest.row(1, "C26TAA", "x.xml", "p.pdf", "done", null);
        PackRow noCode = PdfPackBuilderTest.row(2, "K26TAA", null, null, null, null);
        when(pack.packRows(eq("owner"), eq("0101234567"), anyList())).thenReturn(List.of(original, noCode));
        StoredFiles storage = mock(StoredFiles.class);
        when(storage.engineFile("p.pdf")).thenReturn(pdf);
        when(storage.engineFile("x.xml")).thenThrow(new BusinessException("WBE3010"));
        EngineRequestRepository requests = mock(EngineRequestRepository.class);
        InvoiceDetailRepository details = mock(InvoiceDetailRepository.class);
        when(details.itemsOf(anyList())).thenReturn(Map.of());

        PdfPackJobService svc = new PdfPackJobService(guard, exports, pack, details, requests, storage,
                new WeInvoiceJson(new ObjectMapper()));
        PdfJobStatus started = svc.start(new PdfJobRequest("0101234567", List.of(), null, "flat"));
        PdfJobStatus st = waitDone(svc, started.jobId());

        assertThat(st.state()).isEqualTo("done");
        assertThat(st.done()).isEqualTo(1);
        assertThat(st.fallback()).isEqualTo(1);
        assertThat(st.failed()).isZero();
        assertThat(st.fallbackGroups()).extracting(PdfJobStatus.Group::reason).containsExactly("no_code_invoice");
        assertThat(st.fileReady()).isTrue();
        assertThat(st.fileName()).isEqualTo("0101234567_MUAVAO_01-10-2026-01-10-2026.zip");
        verify(requests, never()).insert(anyString(), anyString(), anyString(), anyString(), anyString(), anyString());

        MockHttpServletResponse res = new MockHttpServletResponse();
        svc.writeFile(started.jobId(), res);
        List<String> names = new ArrayList<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(res.getContentAsByteArray()),
                StandardCharsets.UTF_8)) {
            ZipEntry e;
            while ((e = in.getNextEntry()) != null) names.add(e.getName());
        }
        assertThat(names).contains("0101234567_MUAVAO_01-10-2026-01-10-2026/2026-10-01-1-CONGTYA.pdf",
                "0101234567_MUAVAO_01-10-2026-01-10-2026/_ban_the_hien_khong_co_pdf_goc/2026-10-01-2-CONGTYA.pdf",
                "0101234567_MUAVAO_01-10-2026-01-10-2026/_ban_the_hien_khong_co_pdf_goc/doc_truoc.txt");

        // another owner cannot see the job
        when(guard.ownerId()).thenReturn("someone-else");
        assertThatThrownBy(() -> svc.status(started.jobId())).isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "WBE3042");
        svc.shutdown();
    }

    @Test
    void unknownMode_isRejected() {
        CompanyAccessGuard guard = mock(CompanyAccessGuard.class);
        when(guard.requireCompany(any())).thenReturn(new CompanyRow("o", "1", null, null, null, true, null, null, null,
                true, false, null, null));
        PdfPackJobService svc = new PdfPackJobService(guard, mock(InvoiceExportService.class), mock(PackRepository.class),
                mock(InvoiceDetailRepository.class), mock(EngineRequestRepository.class), mock(StoredFiles.class),
                new WeInvoiceJson(new ObjectMapper()));
        assertThatThrownBy(() -> svc.start(new PdfJobRequest("1", List.of("1"), null, "zip")))
                .isInstanceOf(IllegalArgumentException.class);
        svc.shutdown();
    }

    private static PdfJobStatus waitDone(PdfPackJobService svc, String id) throws InterruptedException {
        for (int i = 0; i < 200; i++) {
            PdfJobStatus s = svc.status(id);
            if (!"running".equals(s.state())) return s;
            Thread.sleep(50);
        }
        throw new AssertionError("job did not finish");
    }
}
