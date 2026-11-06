package com.kosign.wecms.domain.weinvoice.service;

import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.file.SecureFileDownload;
import com.kosign.wecms.domain.weinvoice.common.CompanyAccessGuard;
import com.kosign.wecms.domain.weinvoice.common.CompanyAccessGuard.InvoiceRef;
import com.kosign.wecms.domain.weinvoice.common.StoredFiles;
import com.kosign.wecms.domain.weinvoice.common.WeInvoiceIds;
import com.kosign.wecms.domain.weinvoice.common.WeInvoiceJson;
import com.kosign.wecms.domain.weinvoice.config.WeInvoiceDb;
import com.kosign.wecms.domain.weinvoice.dto.FileUploadResult;
import com.kosign.wecms.domain.weinvoice.dto.InvoiceFileItem;
import com.kosign.wecms.domain.weinvoice.repository.InvoiceDetailRepository;
import com.kosign.wecms.domain.weinvoice.repository.InvoiceDetailRepository.DetailRow;
import com.kosign.wecms.domain.weinvoice.repository.InvoiceFileRepository;
import com.kosign.wecms.domain.weinvoice.repository.InvoiceFileRepository.FileRow;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The invoice record's files (UI Spec 3.5, screen 05).
 *
 * <ul>
 *   <li>{@code xml} — the XML the engine stored (from GDT, or from mail when
 *       {@code invoice_details.nguon = 'xml_email'});</li>
 *   <li>{@code pdf} — the original PDF the engine stored (from the provider portal, or mail
 *       when {@code invoice_pdf.source_id} starts with {@code email});</li>
 *   <li>{@code u<n>} — files users uploaded ({@code winv_invc_file}).</li>
 * </ul>
 * Only uploads can be deleted: the spec also allows deleting mail files, but those are engine
 * rows and the backend never writes engine tables (follow-up noted in the feature record).
 */
@Service
public class InvoiceFileService {

    private static final String UPLOAD_PREFIX = "u";

    private final CompanyAccessGuard guard;
    private final InvoiceDetailRepository details;
    private final InvoiceFileRepository files;
    private final StoredFiles storage;
    private final EngineRequestService requests;
    private final WeInvoiceDb db;
    private final WeInvoiceJson json;

    public InvoiceFileService(CompanyAccessGuard guard, InvoiceDetailRepository details, InvoiceFileRepository files,
                              StoredFiles storage, EngineRequestService requests, WeInvoiceDb db, WeInvoiceJson json) {
        this.guard = guard;
        this.details = details;
        this.files = files;
        this.storage = storage;
        this.requests = requests;
        this.db = db;
        this.json = json;
    }

    public List<InvoiceFileItem> list(String invoiceId) {
        long id = WeInvoiceIds.parse(invoiceId, "id");
        guard.requireInvoice(id);
        DetailRow d = details.find(guard.ownerId(), id).orElseThrow(() -> new BusinessException("WBE3005"));
        return list(d);
    }

    /** Files of an invoice already loaded by the caller (ownership check done). */
    List<InvoiceFileItem> list(DetailRow d) {
        List<InvoiceFileItem> out = new ArrayList<>();
        String base = "/api/v1/weinvoice/invoices/" + d.id() + "/files/";
        if (d.xmlPath() != null) {
            out.add(new InvoiceFileItem("xml", "xml", StoredFiles.baseName(d.xmlPath()), storage.engineFileSize(d.xmlPath()),
                    "xml_email".equals(d.detailSource()) ? "email" : "gdt", d.xmlFetchedAt(), false, base + "xml"));
        }
        if (d.pdfPath() != null) {
            boolean fromMail = d.pdfSource() != null && d.pdfSource().startsWith("email");
            out.add(new InvoiceFileItem("pdf", "pdf", StoredFiles.baseName(d.pdfPath()), storage.engineFileSize(d.pdfPath()),
                    fromMail ? "email" : "vendor", d.pdfFetchedAt(), false, base + "pdf"));
        }
        for (FileRow f : files.listActive(guard.ownerId(), d.id())) {
            out.add(toItem(f, base));
        }
        return out;
    }

    public ResponseEntity<Resource> download(String invoiceId, String fileId) {
        long id = WeInvoiceIds.parse(invoiceId, "id");
        guard.requireInvoice(id);
        String ownerId = guard.ownerId();
        Path path;
        String name;
        if ("xml".equals(fileId) || "pdf".equals(fileId)) {
            DetailRow d = details.find(ownerId, id).orElseThrow(() -> new BusinessException("WBE3005"));
            String rel = "xml".equals(fileId) ? d.xmlPath() : d.pdfPath();
            if (rel == null) throw new BusinessException("WBE3006");
            path = storage.engineFile(rel);
            name = StoredFiles.baseName(rel);
        } else {
            FileRow f = files.find(ownerId, id, uploadSeq(fileId)).orElseThrow(() -> new BusinessException("WBE3006"));
            path = storage.storedFile(f.storPath());
            name = f.name();
        }
        try {
            return SecureFileDownload.attachment(new FileSystemResource(path), name, Files.size(path));
        } catch (IOException e) {
            throw new BusinessException("WBE3010");
        }
    }

    /**
     * Adds a file to the record. {@code confirm} carries the user's answers to a previous
     * {@code needs_confirm}: {@code attach_anyway} and / or {@code replace}.
     */
    public FileUploadResult upload(String invoiceId, String kind, MultipartFile file, List<String> confirm) {
        long id = WeInvoiceIds.parse(invoiceId, "id");
        InvoiceRef ref = guard.requireInvoice(id);
        String ownerId = guard.ownerId();
        String k = kind == null ? "" : kind.trim().toLowerCase(Locale.ROOT);
        if (!Set.of("xml", "pdf", "attachment").contains(k)) {
            throw new IllegalArgumentException("kind: must be xml, pdf or attachment");
        }
        byte[] bytes = bytesOf(file);
        Set<String> confirms = new HashSet<>();
        if (confirm != null) confirm.forEach(c -> confirms.add(c.trim().toLowerCase(Locale.ROOT)));
        DetailRow d = details.find(ownerId, id).orElseThrow(() -> new BusinessException("WBE3005"));
        List<FileRow> uploaded = files.listActive(ownerId, id);
        String base = "/api/v1/weinvoice/invoices/" + id + "/files/";

        return switch (k) {
            case "pdf" -> {
                if (!PdfInspector.isPdf(bytes)) throw new BusinessException("WBE3008");
                List<String> reasons = new ArrayList<>();
                boolean hasPdf = d.pdfPath() != null || uploaded.stream().anyMatch(f -> "PDF".equals(f.kind()));
                if (hasPdf && !confirms.contains("replace")) reasons.add("pdf_exists");
                String lookupCode = WeInvoiceJson.firstNonBlank(d.manualCode(), d.xmlLookupCode());
                String cqtCode = WeInvoiceJson.text(json.tree(d.parsedJson()), "ma_cqt");
                boolean matched = PdfInspector.belongsTo(bytes, file.getOriginalFilename(), ref.symbol(),
                        String.valueOf(ref.number()), ref.sellerTaxId(), cqtCode, lookupCode);
                if (!matched && !confirms.contains("attach_anyway")) reasons.add("pdf_not_matched");
                if (!reasons.isEmpty()) {
                    yield new FileUploadResult("needs_confirm", reasons, null, null);
                }
                StoredFiles.Saved saved = storage.save(ownerId, "invoices", file.getOriginalFilename(), bytes);
                FileRow row = db.tx().execute(s -> {
                    files.retireKind(ownerId, id, "PDF", guard.userId());
                    long seq = files.insert(ownerId, ref.companyMst(), id, "PDF", nameOf(file, "invoice.pdf"),
                            saved.size(), saved.relativePath(), saved.sha256(), !matched, guard.userId());
                    return files.find(ownerId, id, seq).orElseThrow();
                });
                yield new FileUploadResult("ok", List.of(), toItem(row, base), null);
            }
            case "xml" -> {
                XmlKeyReader.XmlKey key = XmlKeyReader.read(bytes);
                boolean hasXml = d.xmlPath() != null || uploaded.stream().anyMatch(f -> "XML".equals(f.kind()));
                if (hasXml) {
                    yield new FileUploadResult("duplicate", List.of(), null, null);
                }
                boolean keyOk = key.matches(ref.templateNo(), ref.symbol(), ref.number(), ref.sellerTaxId());
                if (!keyOk && !confirms.contains("attach_anyway")) {
                    yield new FileUploadResult("needs_confirm", List.of("xml_key_mismatch"), null, null);
                }
                StoredFiles.Saved saved = storage.save(ownerId, "invoices", file.getOriginalFilename(), bytes);
                Stored stored = db.tx().execute(s -> {
                    long seq = files.insert(ownerId, ref.companyMst(), id, "XML", nameOf(file, "invoice.xml"),
                            saved.size(), saved.relativePath(), saved.sha256(), !keyOk, guard.userId());
                    // Only an XML that is this invoice's goes to the engine to fill invoice_xml / details.
                    Long rq = keyOk ? requests.queue(ref.companyMst(), EngineRequestService.IMPORT, "MANUAL",
                            Map.of("invoice_id", id, "file_id", seq)) : null;
                    return new Stored(files.find(ownerId, id, seq).orElseThrow(), rq);
                });
                yield new FileUploadResult("ok", List.of(), toItem(stored.row(), base),
                        stored.requestId() == null ? null : String.valueOf(stored.requestId()));
            }
            default -> {
                StoredFiles.Saved saved = storage.save(ownerId, "invoices", file.getOriginalFilename(), bytes);
                long seq = files.insert(ownerId, ref.companyMst(), id, "ATTACHMENT", nameOf(file, "attachment"),
                        saved.size(), saved.relativePath(), saved.sha256(), false, guard.userId());
                yield new FileUploadResult("ok", List.of(), toItem(files.find(ownerId, id, seq).orElseThrow(), base), null);
            }
        };
    }

    /** Deletes an uploaded file; engine files (GDT, provider, mail) answer {@code WBE3007}. */
    public void delete(String invoiceId, String fileId) {
        long id = WeInvoiceIds.parse(invoiceId, "id");
        guard.requireInvoice(id);
        if ("xml".equals(fileId) || "pdf".equals(fileId)) {
            throw new BusinessException("WBE3007");
        }
        if (files.softDelete(guard.ownerId(), id, uploadSeq(fileId), guard.userId()) == 0) {
            throw new BusinessException("WBE3006");
        }
    }

    private record Stored(FileRow row, Long requestId) {
    }

    private static InvoiceFileItem toItem(FileRow f, String base) {
        String id = UPLOAD_PREFIX + f.id();
        return new InvoiceFileItem(id, f.kind().toLowerCase(Locale.ROOT), f.name(), f.size(), "upload", f.addedAt(),
                true, base + id);
    }

    private static long uploadSeq(String fileId) {
        if (fileId == null || !fileId.startsWith(UPLOAD_PREFIX)) {
            throw new BusinessException("WBE3006");
        }
        return WeInvoiceIds.parse(fileId.substring(UPLOAD_PREFIX.length()), "file_id");
    }

    private static byte[] bytesOf(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("WBE3022");
        }
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String nameOf(MultipartFile file, String fallback) {
        String n = file.getOriginalFilename();
        if (n == null || n.isBlank()) return fallback;
        n = n.replace('\\', '/');
        n = n.substring(n.lastIndexOf('/') + 1);
        return n.length() > 255 ? n.substring(n.length() - 255) : n;
    }
}
