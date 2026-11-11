package com.kosign.wecms.domain.weinvoice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.domain.weinvoice.common.CompanyAccessGuard;
import com.kosign.wecms.domain.weinvoice.common.CompanyAccessGuard.InvoiceRef;
import com.kosign.wecms.domain.weinvoice.common.StoredFiles;
import com.kosign.wecms.domain.weinvoice.common.WeInvoiceJson;
import com.kosign.wecms.domain.weinvoice.config.WeInvoiceDb;
import com.kosign.wecms.domain.weinvoice.dto.FileUploadResult;
import com.kosign.wecms.domain.weinvoice.repository.InvoiceDetailRepository;
import com.kosign.wecms.domain.weinvoice.repository.InvoiceDetailRepository.DetailRow;
import com.kosign.wecms.domain.weinvoice.repository.InvoiceFileRepository;
import com.kosign.wecms.domain.weinvoice.repository.InvoiceFileRepository.FileRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** UI Spec 3.5 — real PDF only, ask when not proven / already present, XML key must match, delete only uploads. */
class InvoiceFileServiceTest {

    private static final String BIZ = "VN_VNM0004_000001";
    private static final long ID = 77L;
    private static final byte[] PDF = "%PDF-1.4\n%%EOF\n".getBytes(StandardCharsets.ISO_8859_1);

    private final CompanyAccessGuard guard = mock(CompanyAccessGuard.class);
    private final InvoiceDetailRepository details = mock(InvoiceDetailRepository.class);
    private final InvoiceFileRepository files = mock(InvoiceFileRepository.class);
    private final StoredFiles storage = mock(StoredFiles.class);
    private final EngineRequestService requests = mock(EngineRequestService.class);
    private final WeInvoiceDb db = mock(WeInvoiceDb.class);
    private InvoiceFileService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        TransactionTemplate tx = mock(TransactionTemplate.class);
        when(tx.execute(any())).thenAnswer(inv -> ((TransactionCallback<Object>) inv.getArgument(0)).doInTransaction(null));
        when(db.tx()).thenReturn(tx);
        when(guard.ownerId()).thenReturn(BIZ);
        when(guard.userId()).thenReturn("u1");
        when(guard.requireInvoice(ID)).thenReturn(new InvoiceRef(ID, "1201496252", "mua", "0101243150", 1, "C26TAA", 123L));
        when(storage.save(eq(BIZ), eq("invoices"), any(), any())).thenReturn(new StoredFiles.Saved("b/invoices/x.pdf", 15, "ab"));
        when(storage.saveInvoiceFile(eq(BIZ), any(), any(), any(), any(), any()))
                .thenReturn(new StoredFiles.Saved("b/invoices/1201496252/2026/10/0101243150/1_C26TAA_123.pdf", 15, "ab"));
        when(files.insert(any(), any(), anyLong(), any(), any(), anyLong(), any(), any(), anyBoolean(), any())).thenReturn(9L);
        when(files.find(BIZ, ID, 9L)).thenReturn(Optional.of(
                new FileRow(9L, ID, "PDF", "UPLOAD", "f.pdf", 15, "b/invoices/x.pdf", "ab", false, "u1", "2026-11-02T00:00:00Z")));
        service = new InvoiceFileService(guard, details, files, storage, requests, db, new WeInvoiceJson(new ObjectMapper()));
    }

    private static DetailRow detail(String xmlPath, String pdfPath) {
        return new DetailRow(ID, "1201496252", "mua", "mua", "0101243150", "Seller", "1201496252", "Buyer", 1, "C26TAA",
                123L, "2026-10-01", null, null, null, 1, "{}", null, "{\"ma_cqt\":\"00ABCDEF\"}", "0101243150", null, null,
                xmlPath, null, pdfPath, pdfPath == null ? null : "misa_meinvoice", null, "xml", "0101243150", "MISA",
                null, null, null, null, null);
    }

    private static DetailRow withGdtPdf(DetailRow d, String gdtPdfPath) {
        return new DetailRow(d.id(), d.companyMst(), d.direction(), d.subType(), d.sellerTaxId(), d.sellerName(),
                d.buyerTaxId(), d.buyerName(), d.templateNo(), d.symbol(), d.number(), d.issueDate(), d.preTax(), d.tax(),
                d.total(), d.tthai(), d.listRawJson(), d.firstSeenAt(), d.parsedJson(), d.xmlProviderMst(),
                d.xmlLookupCode(), d.xmlLookupUrl(), d.xmlPath(), "2026-11-02T08:00:00Z", d.pdfPath(), d.pdfSource(),
                d.pdfFetchedAt(), d.detailSource(), d.catalogueMst(), d.providerName(), d.providerLookupUrl(),
                d.manualCode(), d.manualCompanyCode(), d.manualUrl(), gdtPdfPath);
    }

    private static final String GDT_PDF_PATH = "pdf_gdt/1201496252/2026/10/0101243150/1_C26TAA_123.pdf";

    @Test
    void gdtCopyIsListedWhileThereIsNoProviderOriginal() {
        when(details.find(BIZ, ID)).thenReturn(Optional.of(withGdtPdf(detail("xml/a.xml", null), GDT_PDF_PATH)));
        when(files.listActive(BIZ, ID)).thenReturn(List.of());
        var items = service.list("77");
        assertThat(items).extracting(i -> i.id() + ":" + i.kind() + ":" + i.origin())
                .containsExactly("xml:xml:gdt", "pdf-gdt:pdf_gdt:gdt");
        assertThat(items.get(1).deletable()).isFalse();
        assertThat(items.get(1).url()).endsWith("/invoices/77/files/pdf-gdt");
    }

    @Test
    void providerOriginalReplacesTheGdtCopy() {
        when(details.find(BIZ, ID)).thenReturn(Optional.of(withGdtPdf(detail("xml/a.xml", "pdf/a.pdf"), GDT_PDF_PATH)));
        when(files.listActive(BIZ, ID)).thenReturn(List.of());
        assertThat(service.list("77")).extracting(i -> i.kind()).containsExactly("xml", "pdf");
    }

    @Test
    void uploadedPdfAlsoReplacesTheGdtCopy() {
        when(details.find(BIZ, ID)).thenReturn(Optional.of(withGdtPdf(detail("xml/a.xml", null), GDT_PDF_PATH)));
        when(files.listActive(BIZ, ID)).thenReturn(List.of(
                new FileRow(9L, ID, "PDF", "UPLOAD", "f.pdf", 15, "b/invoices/x.pdf", "ab", false, "u1", "2026-11-02T00:00:00Z")));
        assertThat(service.list("77")).extracting(i -> i.kind()).containsExactly("xml", "pdf");
    }

    @Test
    void uploadedPdfReplacesTheProviderPdfInTheList() {
        when(details.find(BIZ, ID)).thenReturn(Optional.of(withGdtPdf(detail("xml/a.xml", "pdf/a.pdf"), GDT_PDF_PATH)));
        when(files.listActive(BIZ, ID)).thenReturn(List.of(
                new FileRow(9L, ID, "PDF", "UPLOAD", "1_C26TAA_123.pdf", 15, "b/x.pdf", "ab", false, "u1", "2026-11-11T00:00:00Z")));
        assertThat(service.list("77")).extracting(i -> i.id() + ":" + i.origin())
                .containsExactly("xml:gdt", "u9:upload");
    }

    @Test
    void providerPdfMissingOnThisServerKeepsNullSizeAndTheGdtCopyStandsIn() {
        when(details.find(BIZ, ID)).thenReturn(Optional.of(withGdtPdf(detail("xml/a.xml", "pdf/a.pdf"), GDT_PDF_PATH)));
        when(files.listActive(BIZ, ID)).thenReturn(List.of());
        when(storage.engineFileSize("pdf/a.pdf")).thenReturn(null);
        when(storage.engineFileSize(GDT_PDF_PATH)).thenReturn(4096L);
        var items = service.list("77");
        assertThat(items).extracting(i -> i.id()).containsExactly("xml", "pdf", "pdf-gdt");
        assertThat(items.get(1).size()).isNull();
    }

    @Test
    void uploadedXmlStaysListedNextToThePdf() {
        when(details.find(BIZ, ID)).thenReturn(Optional.of(detail(null, "pdf/a.pdf")));
        when(files.listActive(BIZ, ID)).thenReturn(List.of(
                new FileRow(8L, ID, "XML", "UPLOAD", "1_C26TAA_123.xml", 15, "b/x.xml", "ab", false, "u1", "2026-11-11T00:00:00Z")));
        assertThat(service.list("77")).extracting(i -> i.id()).containsExactly("pdf", "u8");
    }

    @Test
    void gdtCopyDownloadsFromTheEngineOutput(@org.junit.jupiter.api.io.TempDir java.nio.file.Path tmp) throws Exception {
        java.nio.file.Path f = java.nio.file.Files.write(tmp.resolve("g.pdf"), PDF);
        when(details.find(BIZ, ID)).thenReturn(Optional.of(withGdtPdf(detail("xml/a.xml", null), GDT_PDF_PATH)));
        when(storage.engineFile(GDT_PDF_PATH)).thenReturn(f);
        var res = service.download("77", "pdf-gdt");
        assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
        verify(storage).engineFile(GDT_PDF_PATH);
    }

    @Test
    void gdtCopyCannotBeDeleted() {
        assertThatThrownBy(() -> service.delete("77", "pdf-gdt")).isInstanceOf(BusinessException.class).hasMessage("WBE3007");
    }

    @Test
    void notAPdfIsWbe3008() {
        when(details.find(BIZ, ID)).thenReturn(Optional.of(detail(null, null)));
        MockMultipartFile f = new MockMultipartFile("file", "x.pdf", "application/pdf", "hello".getBytes());
        assertThatThrownBy(() -> service.upload("77", "pdf", f, null))
                .isInstanceOf(BusinessException.class).hasMessage("WBE3008");
    }

    @Test
    void pdfNamedAfterTheInvoiceIsStoredWithoutQuestions() {
        when(details.find(BIZ, ID)).thenReturn(Optional.of(detail(null, null)));
        when(files.listActive(BIZ, ID)).thenReturn(List.of());
        MockMultipartFile f = new MockMultipartFile("file", "1C26TAA_123.pdf", "application/pdf", PDF);
        FileUploadResult r = service.upload("77", "pdf", f, null);
        assertThat(r.status()).isEqualTo("ok");
        assertThat(r.file().id()).isEqualTo("u9");
        assertThat(r.file().deletable()).isTrue();
        verify(files).insert(eq(BIZ), eq("1201496252"), eq(ID), eq("PDF"), eq("1_C26TAA_123.pdf"), anyLong(), any(), any(),
                eq(false), eq("u1"));
    }

    @Test
    void unprovenPdfOverAnExistingOneAsksBothQuestionsAndStoresNothing() {
        when(details.find(BIZ, ID)).thenReturn(Optional.of(detail(null, "pdf/a.pdf")));
        when(files.listActive(BIZ, ID)).thenReturn(List.of());
        MockMultipartFile f = new MockMultipartFile("file", "scan.pdf", "application/pdf", PDF);
        FileUploadResult r = service.upload("77", "pdf", f, null);
        assertThat(r.status()).isEqualTo("needs_confirm");
        assertThat(r.reasons()).containsExactly("pdf_exists", "pdf_not_matched");
        verify(storage, never()).saveInvoiceFile(any(), any(), any(), any(), any(), any());
    }

    @Test
    void confirmedPdfIsStoredAndFlagged() {
        when(details.find(BIZ, ID)).thenReturn(Optional.of(detail(null, "pdf/a.pdf")));
        when(files.listActive(BIZ, ID)).thenReturn(List.of());
        MockMultipartFile f = new MockMultipartFile("file", "scan.pdf", "application/pdf", PDF);
        FileUploadResult r = service.upload("77", "pdf", f, List.of("replace", "ATTACH_ANYWAY"));
        assertThat(r.status()).isEqualTo("ok");
        verify(files).retireKind(BIZ, ID, "PDF", "u1");
        verify(files).insert(eq(BIZ), eq("1201496252"), eq(ID), eq("PDF"), eq("1_C26TAA_123.pdf"), anyLong(), any(), any(),
                eq(true), eq("u1"));
        // stored in the engine's layout by invoice date, not under the upload's own name
        verify(storage).saveInvoiceFile(eq(BIZ), eq("1201496252"), eq("2026-10-01"), eq("0101243150"),
                eq("1_C26TAA_123.pdf"), any());
    }

    @Test
    void xmlOfAnotherInvoiceAsksForConfirmation() {
        when(details.find(BIZ, ID)).thenReturn(Optional.of(detail(null, null)));
        when(files.listActive(BIZ, ID)).thenReturn(List.of());
        String other = XmlKeyReaderTest.XML.replace("0000123", "124");
        MockMultipartFile f = new MockMultipartFile("file", "a.xml", "text/xml", other.getBytes(StandardCharsets.UTF_8));
        FileUploadResult r = service.upload("77", "xml", f, null);
        assertThat(r.status()).isEqualTo("needs_confirm");
        assertThat(r.reasons()).containsExactly("xml_key_mismatch");
        verify(requests, never()).queue(any(), any(), any(), any());
    }

    @Test
    void matchingXmlIsStoredAndHandedToTheEngine() {
        when(details.find(BIZ, ID)).thenReturn(Optional.of(detail(null, null)));
        when(files.listActive(BIZ, ID)).thenReturn(List.of());
        when(requests.queue(eq("1201496252"), eq(EngineRequestService.IMPORT), eq("MANUAL"), any())).thenReturn(5L);
        MockMultipartFile f = new MockMultipartFile("file", "a.xml", "text/xml",
                XmlKeyReaderTest.XML.getBytes(StandardCharsets.UTF_8));
        FileUploadResult r = service.upload("77", "xml", f, null);
        assertThat(r.status()).isEqualTo("ok");
        assertThat(r.requestId()).isEqualTo("5");
        verify(requests).queue("1201496252", EngineRequestService.IMPORT, "MANUAL", Map.of("invoice_id", ID, "file_id", 9L));
    }

    @Test
    void xmlOnARecordThatHasOneIsDuplicate() {
        when(details.find(BIZ, ID)).thenReturn(Optional.of(detail("xml/a.xml", null)));
        when(files.listActive(BIZ, ID)).thenReturn(List.of());
        MockMultipartFile f = new MockMultipartFile("file", "a.xml", "text/xml",
                XmlKeyReaderTest.XML.getBytes(StandardCharsets.UTF_8));
        assertThat(service.upload("77", "xml", f, null).status()).isEqualTo("duplicate");
        verify(storage, never()).saveInvoiceFile(any(), any(), any(), any(), any(), any());
    }

    @Test
    void engineFilesCannotBeDeleted() {
        assertThatThrownBy(() -> service.delete("77", "xml")).hasMessage("WBE3007");
        assertThatThrownBy(() -> service.delete("77", "pdf")).hasMessage("WBE3007");
    }

    @Test
    void uploadedFileIsSoftDeleted() {
        when(files.softDelete(BIZ, ID, 9L, "u1")).thenReturn(1);
        service.delete("77", "u9");
        verify(files).softDelete(BIZ, ID, 9L, "u1");
        assertThatThrownBy(() -> service.delete("77", "u10")).hasMessage("WBE3006");
    }

    @Test
    void emptyFileIsWbe3022() {
        MockMultipartFile f = new MockMultipartFile("file", "a.pdf", "application/pdf", new byte[0]);
        assertThatThrownBy(() -> service.upload("77", "pdf", f, null)).hasMessage("WBE3022");
    }

    @Test
    void unknownKindIsValidationError() {
        MockMultipartFile f = new MockMultipartFile("file", "a.pdf", "application/pdf", PDF);
        assertThatThrownBy(() -> service.upload("77", "image", f, null)).isInstanceOf(IllegalArgumentException.class);
        verify(storage, never()).save(anyString(), anyString(), anyString(), any());
    }
}
