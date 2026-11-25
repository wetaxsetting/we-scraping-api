package com.kosign.wecms.domain.weinvoice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.domain.weinvoice.common.CompanyAccessGuard;
import com.kosign.wecms.domain.weinvoice.common.StoredFiles;
import com.kosign.wecms.domain.weinvoice.common.WeInvoiceJson;
import com.kosign.wecms.domain.weinvoice.dto.InvoiceFilter;
import com.kosign.wecms.domain.weinvoice.dto.PdfJobRequest;
import com.kosign.wecms.domain.weinvoice.dto.PdfJobStatus;
import com.kosign.wecms.domain.weinvoice.repository.CompanyConnectionRepository.CompanyRow;
import com.kosign.wecms.domain.weinvoice.repository.EngineRequestRepository;
import com.kosign.wecms.domain.weinvoice.repository.EngineRequestRepository.RequestRow;
import com.kosign.wecms.domain.weinvoice.repository.InvoiceDetailRepository;
import com.kosign.wecms.domain.weinvoice.repository.InvoiceDetailRepository.ItemRow;
import com.kosign.wecms.domain.weinvoice.repository.PackRepository;
import com.kosign.wecms.domain.weinvoice.repository.PackRepository.PackRow;
import com.kosign.wecms.domain.weinvoice.service.PdfPackBuilder.Entry;
import com.kosign.wecms.domain.weinvoice.service.PdfPackBuilder.Kind;
import com.kosign.wecms.domain.weinvoice.service.PdfPackBuilder.Outcome;
import jakarta.annotation.PreDestroy;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ContentDisposition;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * "Tải PDF hàng loạt" (UI Spec v1.1 §3.12) processed on the server and delivered as one ZIP (user decision 2026-11-25).
 *
 * <p>A job runs on a small background pool: <b>step 1</b> queues a {@code FETCH_XML} engine request for coded
 * invoices without XML and follows its progress; <b>step 2</b> takes every original PDF the engine (or the user's
 * upload) already holds, classifies the rest from the engine {@code pdf} task ({@link PdfPackBuilder#classify}) and asks
 * the engine now ({@code FETCH_PDF}, one request per invoice, at most {@link #LIVE_LIMIT} per run) for those a provider
 * may still answer; <b>pack</b> writes the ZIP to a temp file. Retry re-asks the engine for the failed invoices only and
 * packs the full ZIP again.
 *
 * <p>Jobs live in memory of this API instance for {@link #KEEP} after they finish (a restart loses them — the web then
 * shows "not found" and the user starts again). The background threads have no security context, so the owner and user
 * ids are captured when the job is created and every repository call passes them.
 */
@Slf4j
@Service
public class PdfPackJobService {

    /** Invoices asked from providers right now per run — each costs the engine 3–10 s. */
    static final int LIVE_LIMIT = 20;
    static final Duration KEEP = Duration.ofHours(2);
    static final Duration XML_WAIT = Duration.ofMinutes(30);
    static final Duration PDF_WAIT = Duration.ofMinutes(15);
    private static final long POLL_MS = 3_000;
    private static final int MAX_ITEMS = 500;
    private static final Set<String> TERMINAL = Set.of("DONE", "PARTIAL", "FAILED", "CANCELLED");

    private final CompanyAccessGuard guard;
    private final InvoiceExportService exports;
    private final PackRepository pack;
    private final InvoiceDetailRepository details;
    private final EngineRequestRepository requests;
    private final StoredFiles storage;
    private final WeInvoiceJson json;
    private final Map<String, Job> jobs = new ConcurrentHashMap<>();
    private final ExecutorService pool = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "weinvoice-pdf-pack");
        t.setDaemon(true);
        return t;
    });

    public PdfPackJobService(CompanyAccessGuard guard, InvoiceExportService exports, PackRepository pack,
                             InvoiceDetailRepository details, EngineRequestRepository requests, StoredFiles storage,
                             WeInvoiceJson json) {
        this.guard = guard;
        this.exports = exports;
        this.pack = pack;
        this.details = details;
        this.requests = requests;
        this.storage = storage;
        this.json = json;
    }

    @PreDestroy
    void shutdown() {
        pool.shutdownNow();
        jobs.values().forEach(Job::deleteFile);
    }

    // ------------------------------------------------------------------ API

    public PdfJobStatus start(PdfJobRequest req) {
        CompanyRow company = guard.requireCompany(req.companyMst());
        String mode = PdfPackNames.normalizeMode(req.mode());
        List<Long> ids = exports.scopeIds(company.mst(), req.ids(), req.filter());
        if (ids.isEmpty()) {
            throw new BusinessException("WBE3005");
        }
        purge();
        InvoiceFilter f = req.filter();
        String dir = f == null || f.direction() == null || f.direction().isBlank() ? null
                : "sales".equalsIgnoreCase(f.direction()) ? "ban" : "purchase".equalsIgnoreCase(f.direction()) ? "mua" : null;
        Job job = new Job(UUID.randomUUID().toString(), guard.ownerId(), guard.userId(), company.mst(), mode, ids, dir,
                f == null ? null : blank(f.from()), f == null ? null : blank(f.to()));
        jobs.put(job.id, job);
        pool.submit(() -> run(job, null));
        return job.status();
    }

    public PdfJobStatus status(String jobId) {
        return find(jobId).status();
    }

    public PdfJobStatus cancel(String jobId) {
        Job job = find(jobId);
        job.cancelled = true;
        for (Long id : List.copyOf(job.openRequests)) {
            try {
                requests.cancel(job.ownerId, id, job.userId);
            } catch (RuntimeException e) {
                log.debug("cancel engine request {}: {}", id, e.toString());
            }
        }
        return job.status();
    }

    /** Retries the failed invoices (all, or one reason group) and packs the full ZIP again. */
    public PdfJobStatus retry(String jobId, String reason) {
        Job job = find(jobId);
        if ("running".equals(job.state)) {
            throw new BusinessException("WBE3043");
        }
        List<Long> failed = job.items.values().stream()
                .filter(i -> "failed".equals(i.phase) && (reason == null || reason.isBlank() || reason.equals(i.reason)))
                .map(i -> i.id).toList();
        if (failed.isEmpty()) {
            return job.status();
        }
        job.cancelled = false;
        job.state = "running";
        job.error = null;
        job.finishedAt = null;
        for (Long id : failed) {
            Item it = job.items.get(id);
            it.phase = "queued";
            it.reason = null;
        }
        pool.submit(() -> run(job, failed));
        return job.status();
    }

    public void writeFile(String jobId, HttpServletResponse response) {
        Job job = find(jobId);
        if (job.file == null || !Files.isRegularFile(job.file)) {
            throw new BusinessException("WBE3044");
        }
        headers(response, "application/zip", job.fileName);
        try (OutputStream out = response.getOutputStream()) {
            Files.copy(job.file, out);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** "Tải Excel HĐ thất bại": the failed invoices with reason and lookup link. */
    public void writeFailedExcel(String jobId, HttpServletResponse response) {
        Job job = find(jobId);
        List<InvoiceWorkbook.FailedRow> rows = new ArrayList<>();
        for (Item i : job.items.values()) {
            if (!"failed".equals(i.phase)) continue;
            PackRow r = i.row;
            rows.add(new InvoiceWorkbook.FailedRow(r.issueDate(), r.templateNo() + r.symbol(), String.valueOf(r.number()),
                    r.partnerTaxId(), r.partnerName(), r.providerName() != null ? r.providerName() : r.providerMst(),
                    r.lookupCode(), r.lookupUrl(), reasonText(i.reason)));
        }
        String base = job.fileName == null ? job.mst : job.fileName.replaceFirst("\\.zip$", "");
        headers(response, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", base + "_HD-THAT-BAI.xlsx");
        try (OutputStream out = response.getOutputStream()) {
            InvoiceWorkbook.write(out, List.of(InvoiceWorkbook.failedSheet(rows)));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // ------------------------------------------------------------------ job body

    private void run(Job job, List<Long> retryIds) {
        try {
            if (retryIds == null) {
                List<PackRow> rows = pack.packRows(job.ownerId, job.mst, job.ids);
                for (PackRow r : rows) {
                    job.items.put(r.id(), new Item(r));
                }
                if (!"rendered".equals(job.mode)) {
                    fetchMissingXml(job, rows);
                }
                job.step = "pdf";
                List<PackRow> fresh = pack.packRows(job.ownerId, job.mst, job.ids);
                List<Long> live = new ArrayList<>();
                for (PackRow r : fresh) {
                    Item it = job.items.computeIfAbsent(r.id(), k -> new Item(r));
                    it.row = r;
                    apply(it, PdfPackBuilder.classify(r, original(r) != null, job.mode), live);
                }
                fetchLive(job, live);
            } else {
                job.step = "pdf";
                List<Long> live = new ArrayList<>();
                for (PackRow r : pack.packRows(job.ownerId, job.mst, retryIds)) {
                    Item it = job.items.get(r.id());
                    it.row = r;
                    Outcome o = PdfPackBuilder.classify(r, original(r) != null, job.mode);
                    // A retry asks the provider again even when the first run only queued it.
                    apply(it, o, live);
                }
                fetchLive(job, live);
            }
            checkCancel(job);
            job.step = "pack";
            packZip(job);
            job.step = "done";
            job.state = "done";
        } catch (Cancelled c) {
            job.state = "cancelled";
        } catch (RuntimeException | IOException e) {
            log.warn("PDF pack job {} failed: {}", job.id, e.toString(), e);
            job.state = "failed";
            job.error = e.getMessage();
        } finally {
            job.openRequests.clear();
            job.finishedAt = Instant.now();
        }
    }

    private void apply(Item it, Outcome o, List<Long> live) {
        it.source = o.source();
        it.reason = o.reason();
        it.phase = switch (o.kind()) {
            case ORIGINAL, RENDERED -> "done";
            case FALLBACK -> "fallback";
            case FAILED -> "failed";
            case LIVE -> {
                live.add(it.id);
                yield "queued";
            }
        };
    }

    /** Step 1: one FETCH_XML request for the coded invoices without XML; waits for it (bounded). */
    private void fetchMissingXml(Job job, List<PackRow> rows) {
        List<Long> fetchable = rows.stream()
                .filter(r -> !r.hasXml() && !r.noCode() && r.tthai() != 6 && !"summary_only".equals(r.xmlErrorKind()))
                .map(PackRow::id).toList();
        job.xmlTotal = fetchable.size();
        if (fetchable.isEmpty()) return;
        fetchable.forEach(id -> job.items.get(id).phase = "xml");
        long reqId = requests.insert(job.ownerId, job.mst, EngineRequestService.FETCH_XML, "MANUAL",
                json.write(Map.of("invoice_ids", fetchable)), job.userId);
        job.openRequests.add(reqId);
        Instant until = Instant.now().plus(XML_WAIT);
        while (Instant.now().isBefore(until)) {
            checkCancel(job);
            RequestRow row = requests.find(job.ownerId, reqId).orElse(null);
            if (row == null) break;
            Integer done = progress(row, "xml_done");
            if (done != null) job.xmlDone = Math.min(done, job.xmlTotal);
            if (TERMINAL.contains(row.status())) break;
            sleep();
        }
        job.openRequests.remove(reqId);
        job.xmlDone = job.xmlTotal;
        fetchable.forEach(id -> job.items.get(id).phase = "queued");
    }

    /** Step 2 (live part): FETCH_PDF for up to {@link #LIVE_LIMIT} invoices; the rest fail as {@code pending}. */
    private void fetchLive(Job job, List<Long> live) {
        Map<Long, Long> asked = new LinkedHashMap<>();
        for (Long id : live) {
            Item it = job.items.get(id);
            if (asked.size() >= LIVE_LIMIT) {
                it.phase = "failed";
                it.reason = "pending";
                continue;
            }
            checkCancel(job);
            long reqId = requests.insert(job.ownerId, job.mst, EngineRequestService.FETCH_PDF, "MANUAL",
                    json.write(Map.of("invoice_ids", List.of(id))), job.userId);
            job.openRequests.add(reqId);
            asked.put(id, reqId);
            it.phase = "loading";
        }
        Instant until = Instant.now().plus(PDF_WAIT);
        while (!asked.isEmpty() && Instant.now().isBefore(until)) {
            checkCancel(job);
            sleep();
            for (Map.Entry<Long, Long> e : List.copyOf(asked.entrySet())) {
                RequestRow row = requests.find(job.ownerId, e.getValue()).orElse(null);
                if (row != null && !TERMINAL.contains(row.status())) continue;
                settle(job, e.getKey());
                asked.remove(e.getKey());
                job.openRequests.remove(e.getValue());
            }
        }
        for (Map.Entry<Long, Long> e : asked.entrySet()) {
            settle(job, e.getKey());
            job.openRequests.remove(e.getValue());
        }
    }

    private void settle(Job job, long id) {
        Item it = job.items.get(id);
        PackRow r = pack.packRows(job.ownerId, job.mst, List.of(id)).stream().findFirst().orElse(it.row);
        it.row = r;
        Outcome o = PdfPackBuilder.afterLive(r, original(r) != null);
        it.source = o.source();
        it.reason = o.reason();
        it.phase = o.kind() == Kind.ORIGINAL ? "done" : o.kind() == Kind.FALLBACK ? "fallback" : "failed";
    }

    private void packZip(Job job) throws IOException {
        List<PackRow> rows = job.items.values().stream().map(i -> i.row).toList();
        String from = job.from != null ? job.from
                : rows.stream().map(PackRow::issueDate).filter(java.util.Objects::nonNull).min(String::compareTo).orElse(null);
        String to = job.to != null ? job.to
                : rows.stream().map(PackRow::issueDate).filter(java.util.Objects::nonNull).max(String::compareTo).orElse(null);
        String dir = job.engineDirection;
        if (dir == null) {
            Set<String> dirs = new java.util.HashSet<>();
            rows.forEach(r -> dirs.add(r.direction()));
            dir = dirs.size() == 1 ? dirs.iterator().next() : null;
        }
        String base = PdfPackNames.zipBase(job.mst, dir, from, to, job.mode);
        List<Entry> entries = new ArrayList<>();
        List<Long> needLines = new ArrayList<>();
        for (Item i : job.items.values()) {
            PackRow r = i.row;
            Kind k = switch (i.phase) {
                case "done" -> "rendered".equals(job.mode) ? Kind.RENDERED : Kind.ORIGINAL;
                case "fallback" -> Kind.FALLBACK;
                default -> Kind.FAILED;
            };
            if ((k == Kind.RENDERED || k == Kind.FALLBACK) && gdtPdf(r) == null) needLines.add(r.id());
            entries.add(new Entry(r, new Outcome(k, i.reason, i.source), k == Kind.ORIGINAL ? original(r) : null, xml(r)));
        }
        Map<Long, List<ItemRow>> lines = new HashMap<>();
        for (int i = 0; i < needLines.size(); i += 2_000) {
            lines.putAll(details.itemsOf(needLines.subList(i, Math.min(needLines.size(), i + 2_000))));
        }
        Path tmp = Files.createTempFile("weinvoice-pdf-", ".zip");
        AtomicInteger rendered = new AtomicInteger();
        try (OutputStream out = Files.newOutputStream(tmp)) {
            PdfPackBuilder.writeZip(out, base, job.mode, entries, r -> {
                if (rendered.incrementAndGet() % 50 == 0) checkCancel(job);
                Path gdt = gdtPdf(r);
                return gdt != null ? Files.readAllBytes(gdt) : RenderedInvoicePdf.render(r, lines.get(r.id()));
            }, PdfPackJobService::reasonText);
        } catch (IOException | RuntimeException e) {
            Files.deleteIfExists(tmp);
            throw e;
        }
        job.deleteFile();
        job.file = tmp;
        job.fileName = base + ".zip";
    }

    // ------------------------------------------------------------------ files

    /** The original PDF on this server: the user's upload wins over the engine copy (UI Spec 3.5). */
    private Path original(PackRow r) {
        Path up = stored(r.uploadedPdfPath());
        return up != null ? up : engine(r.pdfPath());
    }

    private Path xml(PackRow r) {
        Path p = engine(r.xmlPath());
        return p != null ? p : stored(r.uploadedXmlPath());
    }

    private Path gdtPdf(PackRow r) {
        return engine(r.gdtPdfPath());
    }

    private Path engine(String rel) {
        if (rel == null) return null;
        try {
            return storage.engineFile(rel);
        } catch (BusinessException | IllegalArgumentException missing) {
            return null;
        }
    }

    private Path stored(String rel) {
        if (rel == null) return null;
        try {
            return storage.storedFile(rel);
        } catch (BusinessException | IllegalArgumentException missing) {
            return null;
        }
    }

    // ------------------------------------------------------------------ helpers

    private Job find(String jobId) {
        Job job = jobs.get(jobId);
        if (job == null || !job.ownerId.equals(guard.ownerId())) {
            throw new BusinessException("WBE3042");
        }
        return job;
    }

    private void purge() {
        Instant limit = Instant.now().minus(KEEP);
        jobs.values().removeIf(j -> {
            boolean old = j.finishedAt != null && j.finishedAt.isBefore(limit);
            if (old) j.deleteFile();
            return old;
        });
    }

    private Integer progress(RequestRow row, String field) {
        if (row.progressJson() == null) return null;
        JsonNode n = json.tree(row.progressJson());
        return n == null ? null : WeInvoiceJson.integer(n, field);
    }

    private static void checkCancel(Job job) {
        if (job.cancelled) throw new Cancelled();
    }

    private static void sleep() {
        try {
            Thread.sleep(POLL_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new Cancelled();
        }
    }

    /** Vietnamese reason text for doc_truoc.txt and the failed-invoices Excel (the web shows its own labels). */
    static String reasonText(String reason) {
        if (reason == null) return "";
        return switch (reason) {
            case "no_code_invoice" -> "Hoá đơn không mã / Cục Thuế không lưu hồ sơ gốc";
            case "no_lookup_code" -> "Nhà cung cấp không cho tra cứu (mã tra cứu không nằm trong XML)";
            case "not_connected" -> "WeInvoice chưa nối cổng tra cứu của nhà cung cấp";
            case "no_provider" -> "Chưa xác định nhà cung cấp phát hành";
            case "gateway" -> "Cổng bên phát hành hoá đơn đang lỗi — vui lòng thử lại sau ít phút";
            case "pending" -> "Chưa tới lượt tải từ cổng nhà cung cấp — thử lại sau";
            case "not_on_server" -> "Máy chủ chưa có tệp PDF — thử lại sau";
            case "xml_missing" -> "Chưa lấy được XML từ Cục Thuế — thử lại sau";
            default -> reason;
        };
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
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

    private static final class Cancelled extends RuntimeException {
        Cancelled() {
            super("cancelled", null, false, false);
        }
    }

    private static final class Item {
        final long id;
        volatile PackRow row;
        volatile String phase = "queued";
        volatile String source;
        volatile String reason;

        Item(PackRow row) {
            this.id = row.id();
            this.row = row;
        }

        PdfJobStatus.Item view() {
            PackRow r = row;
            return new PdfJobStatus.Item(String.valueOf(id), r.partnerName(), r.partnerTaxId(), r.templateNo() + r.symbol(),
                    String.valueOf(r.number()), r.issueDate(), phase, source, reason);
        }
    }

    private static final class Job {
        final String id;
        final String ownerId;
        final String userId;
        final String mst;
        final String mode;
        final List<Long> ids;
        final String engineDirection;
        final String from;
        final String to;
        final Map<Long, Item> items = java.util.Collections.synchronizedMap(new LinkedHashMap<>());
        final Set<Long> openRequests = ConcurrentHashMap.newKeySet();
        volatile String state = "running";
        volatile String step = "xml";
        volatile int xmlDone;
        volatile int xmlTotal;
        volatile boolean cancelled;
        volatile String error;
        volatile Path file;
        volatile String fileName;
        volatile Instant finishedAt;

        Job(String id, String ownerId, String userId, String mst, String mode, List<Long> ids, String engineDirection,
            String from, String to) {
            this.id = id;
            this.ownerId = ownerId;
            this.userId = userId;
            this.mst = mst;
            this.mode = mode;
            this.ids = ids;
            this.engineDirection = engineDirection;
            this.from = from;
            this.to = to;
        }

        void deleteFile() {
            Path f = file;
            file = null;
            if (f != null) {
                try {
                    Files.deleteIfExists(f);
                } catch (IOException e) {
                    log.debug("delete {}: {}", f, e.toString());
                }
            }
        }

        PdfJobStatus status() {
            List<Item> all;
            synchronized (items) {
                all = new ArrayList<>(items.values());
            }
            int done = 0, fallback = 0, failed = 0, loading = 0;
            Map<String, List<PdfJobStatus.Item>> failedBy = new LinkedHashMap<>();
            Map<String, List<PdfJobStatus.Item>> fallbackBy = new LinkedHashMap<>();
            for (Item i : all) {
                switch (i.phase) {
                    case "done" -> done++;
                    case "fallback" -> {
                        fallback++;
                        fallbackBy.computeIfAbsent(i.reason, k -> new ArrayList<>()).add(i.view());
                    }
                    case "failed" -> {
                        failed++;
                        failedBy.computeIfAbsent(i.reason, k -> new ArrayList<>()).add(i.view());
                    }
                    case "loading" -> loading++;
                    default -> { }
                }
            }
            List<PdfJobStatus.Item> shown = new ArrayList<>();
            all.stream().filter(i -> "loading".equals(i.phase) || "xml".equals(i.phase)).limit(MAX_ITEMS)
                    .forEach(i -> shown.add(i.view()));
            all.stream().filter(i -> !"loading".equals(i.phase) && !"xml".equals(i.phase))
                    .limit(Math.max(0, MAX_ITEMS - shown.size())).forEach(i -> shown.add(i.view()));
            boolean ready = file != null && "done".equals(state);
            return new PdfJobStatus(id, mode, state, step, xmlDone, xmlTotal, all.isEmpty() ? ids.size() : all.size(), done,
                    fallback, failed, loading, shown, groups(failedBy), groups(fallbackBy), ready ? fileName : null, ready,
                    error);
        }

        private static List<PdfJobStatus.Group> groups(Map<String, List<PdfJobStatus.Item>> by) {
            List<PdfJobStatus.Group> out = new ArrayList<>();
            by.forEach((reason, list) -> out.add(new PdfJobStatus.Group(reason, list.size(),
                    list.size() > MAX_ITEMS ? list.subList(0, MAX_ITEMS) : list)));
            return out;
        }
    }
}
