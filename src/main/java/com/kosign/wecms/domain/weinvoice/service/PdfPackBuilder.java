package com.kosign.wecms.domain.weinvoice.service;

import com.kosign.wecms.domain.weinvoice.repository.PackRepository.PackRow;
import com.lowagie.text.Document;
import com.lowagie.text.pdf.PdfCopy;
import com.lowagie.text.pdf.PdfReader;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Classification and ZIP layout of the bulk PDF pack (UI Spec v1.1 §3.12). No I/O besides the streams and paths handed
 * in, so the rules are unit-tested without a database.
 */
public final class PdfPackBuilder {

    /** Fallback reasons (spec) — the invoice is packed with a rendered copy. */
    public static final List<String> FALLBACK_REASONS = List.of("no_code_invoice", "no_lookup_code", "not_connected",
            "no_provider");
    /** Failure reasons — the invoice is left out of the ZIP and offered for retry. */
    public static final List<String> FAIL_REASONS = List.of("gateway", "pending", "not_on_server", "xml_missing");

    /** Engine error kinds that are temporary: the provider can be asked again now. */
    private static final Set<String> TRANSIENT = Set.of("network", "server", "rate_limit", "captcha", "timeout", "other",
            "auth", "bad_content", "breaker");

    public enum Kind { ORIGINAL, RENDERED, FALLBACK, FAILED, LIVE }

    /** What happens to one invoice. {@code source} = where an original PDF came from. */
    public record Outcome(Kind kind, String reason, String source) {
        static Outcome of(Kind k, String reason) {
            return new Outcome(k, reason, null);
        }
    }

    private PdfPackBuilder() {
    }

    /**
     * Outcome of an invoice from what the engine holds. {@code originalOnDisk}: the uploaded or engine PDF exists on
     * this server. {@code LIVE} = no original yet, but the provider may still give one — the job asks the engine now.
     */
    public static Outcome classify(PackRow r, boolean originalOnDisk, String mode) {
        if ("rendered".equals(mode)) return new Outcome(Kind.RENDERED, null, "rendered");
        if (originalOnDisk) return new Outcome(Kind.ORIGINAL, null, source(r));
        if (r.uploadedPdfPath() != null || r.pdfPath() != null) return Outcome.of(Kind.FAILED, "not_on_server");
        String pdfKind = lower(r.pdfErrorKind());
        String pdfStatus = lower(r.pdfTaskStatus());
        if ("summary_only".equals(lower(r.xmlErrorKind())) || "summary_only".equals(pdfKind)
                || (r.noCode() && !r.hasXml())) {
            return Outcome.of(Kind.FALLBACK, "no_code_invoice");
        }
        if (!r.hasXml()) return Outcome.of(Kind.FAILED, "xml_missing");
        if ("no_code".equals(pdfKind)) return Outcome.of(Kind.FALLBACK, "no_lookup_code");
        if ("no_portal".equals(pdfKind)) return Outcome.of(Kind.FALLBACK, "not_connected");
        if ("no_source".equals(pdfStatus) || "no_source".equals(pdfKind)) return Outcome.of(Kind.FALLBACK, "no_provider");
        if ("dead".equals(pdfStatus) && "no_pdf".equals(pdfKind)) return Outcome.of(Kind.FALLBACK, "no_lookup_code");
        if ("dead".equals(pdfStatus) && !TRANSIENT.contains(pdfKind)) return Outcome.of(Kind.FALLBACK, "not_connected");
        if (r.providerMst() == null && r.lookupUrl() == null && "done".equals(pdfStatus)) {
            return Outcome.of(Kind.FALLBACK, "no_provider");
        }
        return Outcome.of(Kind.LIVE, null);
    }

    /** Outcome after the engine was asked now and still gave no original: a fallback reason, else a provider failure. */
    public static Outcome afterLive(PackRow r, boolean originalOnDisk) {
        Outcome o = classify(r, originalOnDisk, "flat");
        return o.kind() == Kind.LIVE ? Outcome.of(Kind.FAILED, "gateway") : o;
    }

    static String source(PackRow r) {
        if (r.uploadedPdfPath() != null) return "upload";
        String s = r.pdfSource();
        if (s != null && s.startsWith("email")) return "email";
        if (r.providerName() != null) return r.providerName();
        return s == null ? "vendor" : s;
    }

    private static String lower(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
    }

    // ------------------------------------------------------------------ packing

    /** One invoice to pack: its row, outcome, and files on this server ({@code null} when absent). */
    public record Entry(PackRow row, Outcome outcome, Path originalPdf, Path xml) {
    }

    /** Builds a rendered copy (GDT print when the engine has it, else the WeInvoice template). */
    public interface Renderer {
        byte[] render(PackRow row) throws IOException;
    }

    /**
     * Writes the ZIP. Layout (prototype v1.1 + file 02 §3.12): modes {@code flat} / {@code rendered} / {@code pdf_xml}
     * pack under a root folder named like the ZIP; {@code by_company} by partner folder, {@code by_month} by
     * {@code yyyy-mm/mua_vao|ban_ra}; {@code merge} one PDF {@code <base>.pdf} in issue-date order. Invoices without an
     * original go to {@code _ban_the_hien_khong_co_pdf_goc/} (+ {@code doc_truoc.txt}) with their XML under
     * {@code _thay_bang_xml/yyyy-mm/}; {@code merge} keeps only the XML; {@code pdf_xml} puts the rendered copy in
     * {@code PDF/}. Failed invoices are not packed. Returns the number of PDF files / merged invoices written.
     */
    public static int writeZip(OutputStream out, String base, String mode, List<Entry> entries, Renderer renderer,
                               Function<String, String> reasonText) throws IOException {
        String root = switch (mode) {
            case "flat", "rendered", "pdf_xml" -> base + "/";
            default -> "";
        };
        int written = 0;
        Set<String> used = new HashSet<>();
        Map<String, Integer> fallbackReasons = new LinkedHashMap<>();
        try (ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            if ("merge".equals(mode)) {
                written = writeMerged(zip, base + ".pdf", entries, renderer);
            }
            for (Entry e : entries) {
                PackRow r = e.row();
                Kind k = e.outcome().kind();
                if (k == Kind.FAILED || k == Kind.LIVE) continue;
                String pdfName = PdfPackNames.pdfBase(r.issueDate(), String.valueOf(r.number()), r.partnerName());
                String ym = r.issueDate() == null || r.issueDate().length() < 7 ? "khong-ro-ngay" : r.issueDate().substring(0, 7);
                if (k == Kind.FALLBACK) fallbackReasons.merge(e.outcome().reason(), 1, Integer::sum);
                if ("merge".equals(mode)) {
                    if (k == Kind.FALLBACK && e.xml() != null) {
                        put(zip, used, xmlDir(ym) + xmlAlt(r), e.xml());
                    }
                    continue;
                }
                String folder = switch (mode) {
                    case "pdf_xml" -> root + "PDF/";
                    case "by_company" -> PdfPackNames.safeDir(r.partnerName(), r.partnerTaxId() == null ? "Khong ro doi tac"
                            : r.partnerTaxId()) + "/";
                    case "by_month" -> PdfPackNames.monthDir(r.issueDate(), r.direction());
                    default -> root;
                };
                if (k == Kind.ORIGINAL) {
                    put(zip, used, folder + pdfName + ".pdf", e.originalPdf());
                } else if (k == Kind.RENDERED) {
                    putBytes(zip, used, folder + pdfName + ".pdf", renderer.render(r));
                } else {
                    String dir = "pdf_xml".equals(mode) ? folder : root + PdfPackNames.FALLBACK_DIR;
                    putBytes(zip, used, dir + pdfName + ".pdf", renderer.render(r));
                }
                written++;
                if ("pdf_xml".equals(mode)) {
                    if (e.xml() != null) put(zip, used, root + "XML/" + pdfName + ".xml", e.xml());
                } else if (k == Kind.FALLBACK && e.xml() != null) {
                    put(zip, used, root + xmlDir(ym) + xmlAlt(r), e.xml());
                }
            }
            if (!fallbackReasons.isEmpty()) {
                String readme = "merge".equals(mode) ? PdfPackNames.XML_DIR + "doc_truoc.txt"
                        : "pdf_xml".equals(mode) ? root + "doc_truoc.txt" : root + PdfPackNames.FALLBACK_DIR + "doc_truoc.txt";
                putBytes(zip, used, readme, readme(mode, fallbackReasons, reasonText).getBytes(StandardCharsets.UTF_8));
            }
        }
        return written;
    }

    private static String xmlDir(String ym) {
        return PdfPackNames.XML_DIR + ym + "/";
    }

    private static String xmlAlt(PackRow r) {
        return PdfPackNames.xmlAltName(r.issueDate(), String.valueOf(r.number()), String.valueOf(r.templateNo()), r.symbol(),
                r.sellerTaxId());
    }

    /** Merges the originals (issue-date order; an unreadable original is replaced by its rendered copy). */
    private static int writeMerged(ZipOutputStream zip, String name, List<Entry> entries, Renderer renderer)
            throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int merged = 0;
        Document doc = new Document();
        PdfCopy copy = null;
        try {
            for (Entry e : entries) {
                if (e.outcome().kind() != Kind.ORIGINAL) continue;
                PdfReader reader;
                try {
                    reader = new PdfReader(Files.readAllBytes(e.originalPdf()));
                } catch (IOException | RuntimeException unreadable) {
                    reader = new PdfReader(renderer.render(e.row()));
                }
                if (copy == null) {
                    copy = new PdfCopy(doc, buf);
                    doc.open();
                }
                for (int p = 1; p <= reader.getNumberOfPages(); p++) {
                    copy.addPage(copy.getImportedPage(reader, p));
                }
                copy.freeReader(reader);
                reader.close();
                merged++;
            }
        } catch (com.lowagie.text.DocumentException ex) {
            throw new IOException("merge PDF: " + ex.getMessage(), ex);
        } finally {
            if (copy != null) doc.close();
        }
        if (merged > 0) {
            zip.putNextEntry(new ZipEntry(name));
            buf.writeTo(zip);
            zip.closeEntry();
        }
        return merged;
    }

    private static void put(ZipOutputStream zip, Set<String> used, String name, Path file) throws IOException {
        if (file == null) return;
        zip.putNextEntry(new ZipEntry(PdfPackNames.unique(used, name)));
        Files.copy(file, zip);
        zip.closeEntry();
    }

    private static void putBytes(ZipOutputStream zip, Set<String> used, String name, byte[] bytes) throws IOException {
        zip.putNextEntry(new ZipEntry(PdfPackNames.unique(used, name)));
        zip.write(bytes);
        zip.closeEntry();
    }

    /** {@code doc_truoc.txt}: why some invoices have a rendered copy, and what it is good for. */
    static String readme(String mode, Map<String, Integer> reasons, Function<String, String> reasonText) {
        List<String> lines = new ArrayList<>();
        lines.add("ĐỌC TRƯỚC — HOÁ ĐƠN KHÔNG CÓ PDF GỐC");
        lines.add("");
        lines.add("Các hoá đơn dưới đây không lấy được PDF gốc của bên phát hành (nhà cung cấp hoá đơn điện tử).");
        lines.add("merge".equals(mode)
                ? "Chúng KHÔNG được nối vào file PDF; XML gốc (dữ liệu pháp lý của hoá đơn) nằm trong thư mục _thay_bang_xml."
                : "Mỗi hoá đơn có 1 BẢN THỂ HIỆN do WeInvoice dựng từ dữ liệu Cục Thuế, kèm XML gốc trong thư mục _thay_bang_xml.");
        lines.add("");
        lines.add("Lý do:");
        reasons.forEach((reason, n) -> lines.add("  - " + reasonText.apply(reason) + ": " + n + " hoá đơn"));
        lines.add("");
        lines.add("Giá trị sử dụng:");
        lines.add("  - XML là hoá đơn điện tử gốc đã được Cục Thuế cấp mã / tiếp nhận — dùng để kê khai, lưu trữ, đối chiếu.");
        lines.add("  - Bản thể hiện chỉ để xem và in nhanh; không thay thế PDF gốc của bên phát hành.");
        lines.add("  - Cần PDF gốc: tra cứu tại cổng của nhà cung cấp bằng mã tra cứu (trong thư/email người bán gửi),");
        lines.add("    hoặc nhập mã tra cứu vào hoá đơn trên WeInvoice rồi tải lại.");
        return String.join("\r\n", lines) + "\r\n";
    }
}
