package com.kosign.wecms.domain.weinvoice.service;

import com.kosign.wecms.domain.weinvoice.repository.PackRepository.PackRow;
import com.kosign.wecms.domain.weinvoice.service.PdfPackBuilder.Entry;
import com.kosign.wecms.domain.weinvoice.service.PdfPackBuilder.Kind;
import com.kosign.wecms.domain.weinvoice.service.PdfPackBuilder.Outcome;
import com.lowagie.text.pdf.PdfReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;

/** UI Spec v1.1 §3.12 — names, classification and ZIP layout of the bulk PDF pack. */
class PdfPackBuilderTest {

    @TempDir
    Path tmp;

    // ------------------------------------------------------------------ names

    @Test
    void zipName_followsSpecPattern() {
        assertThat(PdfPackNames.zipBase("0101234567", "mua", "2026-10-01", "2026-10-31", "merge"))
                .isEqualTo("0101234567_MUAVAO_01-10-2026-31-10-2026_NOI-PDF");
        assertThat(PdfPackNames.zipBase("0101234567", "ban", "2026-10-01", "2026-10-31", "flat"))
                .isEqualTo("0101234567_BANRA_01-10-2026-31-10-2026");
        assertThat(PdfPackNames.zipBase("0101234567", "ban", "2026-10-01", "2026-10-31", "rendered")).endsWith("_BAN-THE-HIEN");
        assertThat(PdfPackNames.zipBase("0101234567", "ban", "2026-10-01", "2026-10-31", "pdf_xml")).endsWith("_PDF-XML");
        assertThat(PdfPackNames.zipBase("0101234567", "ban", "2026-10-01", "2026-10-31", "by_company")).endsWith("_THEO-CONG-TY");
        assertThat(PdfPackNames.zipBase("0101234567", "ban", "2026-10-01", "2026-10-31", "by_month")).endsWith("_THEO-THANG");
    }

    @Test
    void pdfName_isAsciiUpperWithoutSpaces() {
        assertThat(PdfPackNames.pdfBase("2026-10-05", "1234", "Công ty TNHH Đông Á"))
                .isEqualTo("2026-10-05-1234-CONGTYTNHHDONGA");
        assertThat(PdfPackNames.xmlAltName("2026-10-05", "1234", "1", "C26TAA", "0101234567"))
                .isEqualTo("2026-10-05-1234-1C26TAA-0101234567.xml");
        assertThat(PdfPackNames.safeDir("Công ty A/B: \"C\"", "x")).isEqualTo("Công ty A B C");
    }

    @Test
    void uniqueName_addsCounterBeforeExtension() {
        var used = new HashSet<String>();
        assertThat(PdfPackNames.unique(used, "a/b.pdf")).isEqualTo("a/b.pdf");
        assertThat(PdfPackNames.unique(used, "a/b.pdf")).isEqualTo("a/b (2).pdf");
    }

    // ------------------------------------------------------------------ classification

    @Test
    void classify_originalOnDiskWins() {
        Outcome o = PdfPackBuilder.classify(row(1, "C26TAA", "x.xml", "p.pdf", null, null), true, "flat");
        assertThat(o.kind()).isEqualTo(Kind.ORIGINAL);
    }

    @Test
    void classify_renderedModeRendersEverything() {
        assertThat(PdfPackBuilder.classify(row(1, "C26TAA", "x.xml", null, null, null), false, "rendered").kind())
                .isEqualTo(Kind.RENDERED);
    }

    @Test
    void classify_reasonsFromEngineTask() {
        assertThat(PdfPackBuilder.classify(row(1, "K26TAA", null, null, null, null), false, "flat"))
                .extracting(Outcome::kind, Outcome::reason).containsExactly(Kind.FALLBACK, "no_code_invoice");
        assertThat(PdfPackBuilder.classify(row(1, "C26TAA", "x.xml", null, "dead", "no_code"), false, "flat").reason())
                .isEqualTo("no_lookup_code");
        assertThat(PdfPackBuilder.classify(row(1, "C26TAA", "x.xml", null, "dead", "no_portal"), false, "flat").reason())
                .isEqualTo("not_connected");
        assertThat(PdfPackBuilder.classify(row(1, "C26TAA", "x.xml", null, "no_source", null), false, "flat").reason())
                .isEqualTo("no_provider");
        assertThat(PdfPackBuilder.classify(row(1, "C26TAA", null, null, null, null), false, "flat"))
                .extracting(Outcome::kind, Outcome::reason).containsExactly(Kind.FAILED, "xml_missing");
        // PDF known to the DB but not on this server
        assertThat(PdfPackBuilder.classify(row(1, "C26TAA", "x.xml", "p.pdf", null, null), false, "flat").reason())
                .isEqualTo("not_on_server");
    }

    @Test
    void classify_transientOrPendingTaskIsAskedLive_thenGatewayFailure() {
        PackRow pending = row(1, "C26TAA", "x.xml", null, "pending", "server");
        assertThat(PdfPackBuilder.classify(pending, false, "flat").kind()).isEqualTo(Kind.LIVE);
        assertThat(PdfPackBuilder.afterLive(pending, false))
                .extracting(Outcome::kind, Outcome::reason).containsExactly(Kind.FAILED, "gateway");
        assertThat(PdfPackBuilder.afterLive(pending, true).kind()).isEqualTo(Kind.ORIGINAL);
    }

    // ------------------------------------------------------------------ ZIP layout

    @Test
    void flat_packsUnderRootFolder_fallbackAndXmlInSpecFolders() throws Exception {
        List<Entry> entries = sample();
        Map<String, byte[]> zip = zip("flat", entries);
        String root = "0101234567_MUAVAO_01-10-2026-31-10-2026/";
        assertThat(zip).containsKey(root + "2026-10-01-1-CONGTYA.pdf");
        assertThat(zip).containsKey(root + "_ban_the_hien_khong_co_pdf_goc/2026-10-02-2-CONGTYB.pdf");
        assertThat(zip).containsKey(root + "_ban_the_hien_khong_co_pdf_goc/doc_truoc.txt");
        assertThat(zip).containsKey(root + "_thay_bang_xml/2026-10/2026-10-02-2-1C26TBB-0300000002.xml");
        // the failed invoice is not packed
        assertThat(zip.keySet()).noneMatch(k -> k.contains("-3-"));
        assertThat(new String(zip.get(root + "_ban_the_hien_khong_co_pdf_goc/doc_truoc.txt"), StandardCharsets.UTF_8))
                .contains("ĐỌC TRƯỚC").contains("no_lookup_code-text: 1 hoá đơn");
    }

    @Test
    void pdfXml_putsSameNamesInPdfAndXmlFolders() throws Exception {
        Map<String, byte[]> zip = zip("pdf_xml", sample());
        String root = "0101234567_MUAVAO_01-10-2026-31-10-2026_PDF-XML/";
        assertThat(zip).containsKeys(root + "PDF/2026-10-01-1-CONGTYA.pdf", root + "XML/2026-10-01-1-CONGTYA.xml",
                root + "PDF/2026-10-02-2-CONGTYB.pdf", root + "XML/2026-10-02-2-CONGTYB.xml");
    }

    @Test
    void byCompanyAndByMonth_folders() throws Exception {
        assertThat(zip("by_company", sample())).containsKeys("Công ty A/2026-10-01-1-CONGTYA.pdf",
                "_ban_the_hien_khong_co_pdf_goc/2026-10-02-2-CONGTYB.pdf");
        assertThat(zip("by_month", sample())).containsKeys("2026-10/mua_vao/2026-10-01-1-CONGTYA.pdf",
                "_thay_bang_xml/2026-10/2026-10-02-2-1C26TBB-0300000002.xml");
    }

    @Test
    void merge_onePdfOfOriginals_fallbackOnlyAsXml() throws Exception {
        Map<String, byte[]> zip = zip("merge", sample());
        String pdf = "0101234567_MUAVAO_01-10-2026-31-10-2026_NOI-PDF.pdf";
        assertThat(zip).containsKey(pdf);
        assertThat(new PdfReader(zip.get(pdf)).getNumberOfPages()).isEqualTo(1);
        assertThat(zip).containsKeys("_thay_bang_xml/2026-10/2026-10-02-2-1C26TBB-0300000002.xml",
                "_thay_bang_xml/doc_truoc.txt");
        assertThat(zip.keySet()).noneMatch(k -> k.contains("_ban_the_hien"));
    }

    @Test
    void renderedInvoicePdf_isAPdf() throws Exception {
        byte[] pdf = RenderedInvoicePdf.render(row(9, "K26TAA", null, null, null, null), List.of());
        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        assertThat(new PdfReader(pdf).getNumberOfPages()).isGreaterThanOrEqualTo(1);
    }

    // ------------------------------------------------------------------ fixtures

    private List<Entry> sample() throws Exception {
        Path pdf = tmp.resolve("a.pdf");
        Files.write(pdf, RenderedInvoicePdf.render(row(1, "C26TAA", null, null, null, null), List.of()));
        Path xml = tmp.resolve("a.xml");
        Files.writeString(xml, "<HDon/>");
        List<Entry> e = new ArrayList<>();
        e.add(new Entry(named(1, "2026-10-01", "C26TAA", "Công ty A", "0300000001"), new Outcome(Kind.ORIGINAL, null, "MISA"),
                pdf, xml));
        e.add(new Entry(named(2, "2026-10-02", "C26TBB", "Công ty B", "0300000002"),
                new Outcome(Kind.FALLBACK, "no_lookup_code", null), null, xml));
        e.add(new Entry(named(3, "2026-10-03", "C26TCC", "Công ty C", "0300000003"),
                new Outcome(Kind.FAILED, "gateway", null), null, xml));
        return e;
    }

    private static Map<String, byte[]> zip(String mode, List<Entry> entries) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PdfPackBuilder.writeZip(out, PdfPackNames.zipBase("0101234567", "mua", "2026-10-01", "2026-10-31", mode), mode,
                entries, r -> RenderedInvoicePdf.render(r, List.of()), r -> r + "-text");
        Map<String, byte[]> files = new LinkedHashMap<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(out.toByteArray()), StandardCharsets.UTF_8)) {
            ZipEntry z;
            while ((z = in.getNextEntry()) != null) {
                files.put(z.getName(), in.readAllBytes());
            }
        }
        return files;
    }

    static PackRow row(long id, String symbol, String xmlPath, String pdfPath, String taskStatus, String errorKind) {
        return new PackRow(id, "mua", "mua", "2026-10-01", 1, symbol, id, "0300000001", "Công ty A", "Hà Nội",
                "0101234567", "Công ty Mua", "TP HCM", new BigDecimal("1000000"), new BigDecimal("80000"),
                new BigDecimal("1080000"), 1, null, "VND", xmlPath, null, pdfPath, null, null, null, "0101243150", "MISA",
                null, null, taskStatus, errorKind, null, null);
    }

    private static PackRow named(long id, String date, String symbol, String seller, String sellerMst) {
        return new PackRow(id, "mua", "mua", date, 1, symbol, id, sellerMst, seller, null, "0101234567", "Công ty Mua",
                null, BigDecimal.TEN, BigDecimal.ONE, BigDecimal.valueOf(11), 1, "M1", "VND", "x.xml", null, null, null, null,
                null, null, null, null, null, null, null, null, null);
    }
}
