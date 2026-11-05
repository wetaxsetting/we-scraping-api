package com.kosign.wecms.domain.weinvoice.service;

import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.export.ExcelColumnSpec;
import com.kosign.wecms.common.export.ExcelExportService;
import com.kosign.wecms.common.export.ExcelWorkbookWriter;
import com.kosign.wecms.common.i18n.MessageResolver;
import com.kosign.wecms.domain.weinvoice.common.CompanyAccessGuard;
import com.kosign.wecms.domain.weinvoice.common.StoredFiles;
import com.kosign.wecms.domain.weinvoice.common.WeInvoiceIds;
import com.kosign.wecms.domain.weinvoice.dto.ExportZipRequest;
import com.kosign.wecms.domain.weinvoice.dto.InvoiceFilter;
import com.kosign.wecms.domain.weinvoice.dto.InvoiceRow;
import com.kosign.wecms.domain.weinvoice.repository.CompanyConnectionRepository.CompanyRow;
import com.kosign.wecms.domain.weinvoice.repository.InvoiceCriteria;
import com.kosign.wecms.domain.weinvoice.repository.InvoiceDetailRepository;
import com.kosign.wecms.domain.weinvoice.repository.InvoiceDetailRepository.ExportFileRow;
import com.kosign.wecms.domain.weinvoice.repository.InvoiceQueryRepository;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ContentDisposition;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Exports of screen 04 (UI Spec 3.6).
 *
 * <ul>
 *   <li><b>Excel</b> — the filtered list (or a selection), one row per invoice, through the
 *       shared {@link ExcelWorkbookWriter}. The UI asks "fetch missing XML first?" from
 *       {@code kpis.missing_xml} of the list before calling this.</li>
 *   <li><b>ZIP</b> — streamed straight into the response (no temp file, no second request):
 *       XML and / or original PDF of the selected invoices in one of five layouts, optionally
 *       with the Excel list. The rendered view is produced in the browser and is not part of
 *       the server ZIP.</li>
 * </ul>
 */
@Slf4j
@Service
public class InvoiceExportService {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final InvoiceQueryService queries;
    private final InvoiceQueryRepository invoices;
    private final InvoiceDetailRepository details;
    private final CompanyAccessGuard guard;
    private final StoredFiles storage;
    private final ExcelWorkbookWriter writer;
    private final MessageResolver messages;

    public InvoiceExportService(InvoiceQueryService queries, InvoiceQueryRepository invoices,
                                InvoiceDetailRepository details, CompanyAccessGuard guard, StoredFiles storage,
                                ExcelWorkbookWriter writer, MessageResolver messages) {
        this.queries = queries;
        this.invoices = invoices;
        this.details = details;
        this.guard = guard;
        this.storage = storage;
        this.writer = writer;
        this.messages = messages;
    }

    public void excel(InvoiceFilter filter, HttpServletResponse response) {
        InvoiceCriteria c = queries.criteria(filter);
        long count = invoices.kpis(c).count();
        if (count > ExcelExportService.MAX_ROWS) {
            throw new BusinessException("WBE3021", count, ExcelExportService.MAX_ROWS);
        }
        List<InvoiceRow> rows = invoices.list(c, 0, (int) Math.max(count, 1));
        String title = messages.resolve(c.engineDirection() == null ? "weinvoice.export.title.all"
                : "ban".equals(c.engineDirection()) ? "weinvoice.export.title.sales" : "weinvoice.export.title.purchase");
        String subHeader = messages.resolve("weinvoice.export.subheader", c.mst(), rows.size(),
                c.from() == null ? "…" : c.from().toString(), c.to() == null ? "…" : c.to().toString());
        headers(response, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                fileName(title, c.mst()) + ".xlsx");
        try (OutputStream out = response.getOutputStream()) {
            writer.write(out, title, subHeader, columns(), toExcelRows(rows));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void zip(ExportZipRequest req, HttpServletResponse response) {
        CompanyRow company = guard.requireCompany(req.companyMst());
        String bizKey = guard.bizKey();
        List<Long> ids = WeInvoiceIds.parseAll(req.ids(), "ids");
        String mode = req.mode() == null || req.mode().isBlank() ? "flat" : req.mode().trim().toLowerCase(Locale.ROOT);
        if (!List.of("flat", "by_month", "by_partner", "by_month_partner", "per_invoice").contains(mode)) {
            throw new IllegalArgumentException("mode: must be flat, by_month, by_partner, by_month_partner or per_invoice");
        }
        Set<String> include = new HashSet<>();
        (req.include() == null || req.include().isEmpty() ? List.of("xml", "pdf") : req.include())
                .forEach(i -> include.add(i.trim().toLowerCase(Locale.ROOT)));

        List<ExportFileRow> files = details.exportFiles(bizKey, company.mst(), ids);
        if (files.isEmpty()) {
            throw new BusinessException("WBE3005");
        }
        byte[] summary = null;
        if (include.contains("summary")) {
            InvoiceCriteria c = new InvoiceCriteria(bizKey, company.mst(), null, null, null, null, null, null, List.of(),
                    null, List.of(), null, List.of(), ids, null, false);
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            try {
                writer.write(buf, messages.resolve("weinvoice.export.title.all"), null, columns(),
                        toExcelRows(invoices.list(c, 0, ids.size())));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            summary = buf.toByteArray();
        }
        headers(response, "application/zip", "WeInvoice_" + company.mst() + "_" + LocalDate.now().format(STAMP) + ".zip");
        Set<String> used = new HashSet<>();
        try (ZipOutputStream zip = new ZipOutputStream(response.getOutputStream(), StandardCharsets.UTF_8)) {
            for (ExportFileRow f : files) {
                String folder = folder(mode, f);
                String base = f.templateNo() + "_" + f.symbol() + "_" + f.number();
                if (include.contains("xml")) {
                    addFile(zip, used, folder + base + ".xml", f.xmlPath() != null ? enginePath(f.xmlPath())
                            : storedPath(f.uploadedXmlPath()));
                }
                if (include.contains("pdf")) {
                    // A PDF the user uploaded wins over the engine's copy (UI Spec 3.5 "thay thế").
                    addFile(zip, used, folder + base + ".pdf", f.uploadedPdfPath() != null ? storedPath(f.uploadedPdfPath())
                            : enginePath(f.pdfPath()));
                }
            }
            if (summary != null) {
                zip.putNextEntry(new ZipEntry("invoices.xlsx"));
                zip.write(summary);
                zip.closeEntry();
            }
        } catch (IOException e) {
            log.warn("ZIP export interrupted: {}", e.toString());
            throw new UncheckedIOException(e);
        }
    }

    private static String folder(String mode, ExportFileRow f) {
        String month = f.issueDate() == null ? "unknown" : f.issueDate().substring(0, 7);
        String partner = f.partnerTaxId() == null || f.partnerTaxId().isBlank() ? "unknown" : safe(f.partnerTaxId());
        return switch (mode) {
            case "by_month" -> month + "/";
            case "by_partner" -> partner + "/";
            case "by_month_partner" -> month + "/" + partner + "/";
            case "per_invoice" -> f.templateNo() + "_" + f.symbol() + "_" + f.number() + "/";
            default -> "";
        };
    }

    private Path enginePath(String rel) {
        if (rel == null) return null;
        try {
            return storage.engineFile(rel);
        } catch (BusinessException missing) {
            return null;
        }
    }

    private Path storedPath(String rel) {
        if (rel == null) return null;
        try {
            return storage.storedFile(rel);
        } catch (BusinessException missing) {
            return null;
        }
    }

    private static void addFile(ZipOutputStream zip, Set<String> used, String name, Path path) throws IOException {
        if (path == null || !used.add(name)) return;
        zip.putNextEntry(new ZipEntry(name));
        Files.copy(path, zip);
        zip.closeEntry();
    }

    private List<ExcelColumnSpec> columns() {
        List<ExcelColumnSpec> cols = new ArrayList<>();
        cols.add(col("issue_date", "TEXT", 12));
        cols.add(col("symbol", "TEXT", 12));
        cols.add(col("number", "TEXT", 12));
        cols.add(col("partner_tax_id", "TEXT", 16));
        cols.add(col("partner_name", "TEXT", 40));
        cols.add(col("currency", "TEXT", 8));
        cols.add(col("exchange_rate", "AMOUNT", 12));
        cols.add(col("pre_tax", "AMOUNT", 18));
        cols.add(col("tax", "AMOUNT", 16));
        cols.add(col("total", "AMOUNT", 18));
        cols.add(col("total_vnd", "AMOUNT", 18));
        cols.add(col("status", "TEXT", 16));
        cols.add(col("cqt_code", "TEXT", 36));
        cols.add(col("provider", "TEXT", 20));
        cols.add(col("xml", "TEXT", 6));
        cols.add(col("pdf", "TEXT", 6));
        return cols;
    }

    private ExcelColumnSpec col(String field, String format, int width) {
        return new ExcelColumnSpec(field, messages.resolve("weinvoice.export.col." + field), format, null, null, width);
    }

    private List<Map<String, Object>> toExcelRows(List<InvoiceRow> rows) {
        String yes = messages.resolve("weinvoice.export.yes");
        String no = messages.resolve("weinvoice.export.no");
        List<Map<String, Object>> out = new ArrayList<>(rows.size());
        for (InvoiceRow r : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("issue_date", r.issueDate());
            m.put("symbol", r.templateNo() + r.symbol());
            m.put("number", r.number());
            m.put("partner_tax_id", r.partnerTaxId());
            m.put("partner_name", r.partnerName());
            m.put("currency", r.currency());
            m.put("exchange_rate", r.exchangeRate());
            m.put("pre_tax", r.preTax());
            m.put("tax", r.tax());
            m.put("total", r.total());
            m.put("total_vnd", r.totalVnd());
            m.put("status", messages.resolve("weinvoice.status." + r.status()));
            m.put("cqt_code", r.cqtCode());
            m.put("provider", r.provider() != null ? r.provider() : r.providerTaxId());
            m.put("xml", r.hasXml() ? yes : no);
            m.put("pdf", r.hasPdf() ? yes : no);
            out.add(m);
        }
        return out;
    }

    private static void headers(HttpServletResponse response, String contentType, String fileName) {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(contentType);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader("Content-Disposition",
                ContentDisposition.builder("attachment").filename(fileName, StandardCharsets.UTF_8).build().toString());
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Access-Control-Expose-Headers", "Content-Disposition");
    }

    private static String fileName(String title, String mst) {
        return safe(title) + "_" + mst + "_" + LocalDate.now().format(STAMP);
    }

    private static String safe(String s) {
        return s.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim();
    }
}
