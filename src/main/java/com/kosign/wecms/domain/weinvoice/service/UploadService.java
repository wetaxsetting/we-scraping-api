package com.kosign.wecms.domain.weinvoice.service;

import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.domain.weinvoice.common.CompanyAccessGuard;
import com.kosign.wecms.domain.weinvoice.common.StoredFiles;
import com.kosign.wecms.domain.weinvoice.common.WeInvoiceIds;
import com.kosign.wecms.domain.weinvoice.config.WeInvoiceDb;
import com.kosign.wecms.domain.weinvoice.config.WeInvoiceProperties;
import com.kosign.wecms.domain.weinvoice.dto.AdoptRequest;
import com.kosign.wecms.domain.weinvoice.dto.RequestAccepted;
import com.kosign.wecms.domain.weinvoice.dto.UploadResponse;
import com.kosign.wecms.domain.weinvoice.repository.CompanyConnectionRepository.CompanyRow;
import com.kosign.wecms.domain.weinvoice.repository.UploadRepository;
import com.kosign.wecms.domain.weinvoice.repository.UploadRepository.BatchRow;
import com.kosign.wecms.domain.weinvoice.repository.UploadRepository.FileRow;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Screen 09 — manual upload of XML / ZIP / PDF files, and screen 15's "add to the store".
 *
 * <p>The backend stores the files and queues an {@code IMPORT} request; the engine parses each
 * file with its own XML parser and applies the dedupe rules of UI Spec 3.2, writing the outcome
 * per file. Parsing is not duplicated here: one parser (the engine's) decides what an invoice is.
 */
@Service
public class UploadService {

    private static final List<String> ALLOWED = List.of(".xml", ".zip", ".pdf");
    static final int MAX_FILES = 200;

    private final CompanyAccessGuard guard;
    private final UploadRepository uploads;
    private final StoredFiles storage;
    private final EngineRequestService requests;
    private final CompanyService companies;
    private final WeInvoiceDb db;
    private final WeInvoiceProperties props;

    public UploadService(CompanyAccessGuard guard, UploadRepository uploads, StoredFiles storage,
                         EngineRequestService requests, CompanyService companies, WeInvoiceDb db,
                         WeInvoiceProperties props) {
        this.guard = guard;
        this.uploads = uploads;
        this.storage = storage;
        this.requests = requests;
        this.companies = companies;
        this.db = db;
        this.props = props;
    }

    public UploadResponse create(String companyMst, List<MultipartFile> files) {
        CompanyRow company = guard.requireCompany(companyMst);
        List<MultipartFile> list = files == null ? List.of() : files.stream().filter(f -> f != null && !f.isEmpty()).toList();
        if (list.isEmpty()) {
            throw new BusinessException("WBE3022");
        }
        if (list.size() > MAX_FILES) {
            throw new BusinessException("WBE3017", list.size(), MAX_FILES);
        }
        for (MultipartFile f : list) {
            String ext = StoredFiles.extensionOf(f.getOriginalFilename());
            if (!ALLOWED.contains(ext)) {
                throw new BusinessException("WBE3025", f.getOriginalFilename());
            }
        }
        String bizKey = guard.bizKey();
        // Files first (outside the transaction): a failed DB write leaves orphan files, never rows without files.
        List<StoredFiles.Saved> saved = new ArrayList<>(list.size());
        for (MultipartFile f : list) {
            saved.add(storage.save(bizKey, "uploads", f.getOriginalFilename(), bytes(f)));
        }
        Long uploadId = db.tx().execute(s -> {
            long id = uploads.insertBatch(bizKey, company.mst(), list.size(), guard.userId());
            for (int i = 0; i < list.size(); i++) {
                StoredFiles.Saved sv = saved.get(i);
                uploads.insertFile(id, i + 1, name(list.get(i)), sv.size(), sv.relativePath(), sv.sha256());
            }
            long rq = requests.queue(company.mst(), EngineRequestService.IMPORT, "MANUAL", Map.of("upload_id", id));
            uploads.linkRequest(id, rq);
            return id;
        });
        return get(String.valueOf(uploadId));
    }

    public UploadResponse get(String uploadId) {
        long id = WeInvoiceIds.parse(uploadId, "upload_id");
        BatchRow b = uploads.findBatch(guard.bizKey(), id).orElseThrow(() -> new BusinessException("WBE3015"));
        List<UploadResponse.FileItem> files = uploads.files(id).stream().map(UploadService::toItem).toList();
        return new UploadResponse(String.valueOf(b.id()), b.mst(), b.requestId() == null ? null : String.valueOf(b.requestId()),
                EngineRequestService.lower(b.requestStatus()), b.requestError(), b.createdAt(), files);
    }

    /**
     * "Thêm MST … và nhập" (UI Spec 3.2): adds the foreign MST without password and imports
     * the batch's files that belong to it.
     */
    public RequestAccepted adopt(String uploadId, AdoptRequest req) {
        long id = WeInvoiceIds.parse(uploadId, "upload_id");
        uploads.findBatch(guard.bizKey(), id).orElseThrow(() -> new BusinessException("WBE3015"));
        String mst = WeInvoiceIds.requireTaxId(req.companyMst());
        List<Integer> fileNos = uploads.files(id).stream()
                .filter(f -> "FOREIGN".equals(f.outcome()) && (mst.equals(f.buyerTaxId()) || mst.equals(f.sellerTaxId())))
                .map(FileRow::fileNo)
                .toList();
        if (fileNos.isEmpty()) {
            throw new BusinessException("WBE3026", mst);
        }
        Long rq = db.tx().execute(s -> {
            companies.addPending(mst);
            return requests.queue(mst, EngineRequestService.IMPORT, "MANUAL",
                    Map.of("upload_id", id, "file_nos", fileNos, "company_mst", mst));
        });
        return new RequestAccepted(String.valueOf(rq), mst);
    }

    public void delete(String uploadId) {
        long id = WeInvoiceIds.parse(uploadId, "upload_id");
        if (uploads.softDelete(guard.bizKey(), id, guard.userId()) == 0) {
            throw new BusinessException("WBE3015");
        }
    }

    /** Acceptance data set (M14) — the engine loads its TestData folder; dev / SIT only. */
    public RequestAccepted loadTestData(String companyMst) {
        if (!props.testDataEnabled()) {
            throw new BusinessException("WBE3018");
        }
        CompanyRow company = guard.requireCompany(companyMst);
        long rq = requests.queue(company.mst(), EngineRequestService.TEST_DATA, "MANUAL", Map.of());
        return new RequestAccepted(String.valueOf(rq), company.mst());
    }

    private static UploadResponse.FileItem toItem(FileRow f) {
        return new UploadResponse.FileItem(
                f.fileNo(),
                f.name(),
                f.size(),
                f.outcome() == null ? null : f.outcome().toLowerCase(Locale.ROOT),
                f.message(),
                f.invoiceId() == null ? null : String.valueOf(f.invoiceId()),
                f.sellerTaxId(),
                f.buyerTaxId(),
                f.partnerName(),
                f.total(),
                f.invoiceId() == null ? null : (f.hasPdf() ? "has" : "missing"),
                f.processedAt());
    }

    private static byte[] bytes(MultipartFile f) {
        try {
            return f.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String name(MultipartFile f) {
        String n = f.getOriginalFilename() == null ? "file" : f.getOriginalFilename().replace('\\', '/');
        n = n.substring(n.lastIndexOf('/') + 1);
        return n.length() > 255 ? n.substring(n.length() - 255) : n;
    }
}
