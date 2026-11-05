package com.kosign.wecms.domain.weinvoice.service;

import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.domain.weinvoice.common.CompanyAccessGuard;
import com.kosign.wecms.domain.weinvoice.common.WeInvoiceIds;
import com.kosign.wecms.domain.weinvoice.common.WeInvoiceSecretCipher;
import com.kosign.wecms.domain.weinvoice.config.WeInvoiceDb;
import com.kosign.wecms.domain.weinvoice.config.WeInvoiceProperties;
import com.kosign.wecms.domain.weinvoice.dto.CompanyCreateRequest;
import com.kosign.wecms.domain.weinvoice.dto.CompanyItem;
import com.kosign.wecms.domain.weinvoice.dto.CompanyListResponse;
import com.kosign.wecms.domain.weinvoice.dto.CompanyPasswordRequest;
import com.kosign.wecms.domain.weinvoice.dto.CompanyPatchRequest;
import com.kosign.wecms.domain.weinvoice.dto.RequestAccepted;
import com.kosign.wecms.domain.weinvoice.repository.CompanyConnectionRepository;
import com.kosign.wecms.domain.weinvoice.repository.CompanyConnectionRepository.CompanyRow;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Screen 06 — the tax codes a user manages and their GDT account.
 *
 * <p>Adding an MST or changing its password stores the password encrypted
 * ({@link WeInvoiceSecretCipher}), sets the connection {@code PENDING} and queues a
 * {@code LOGIN_TEST} request: the engine solves the GDT captcha, logs in, and writes
 * {@code CONNECTED} or {@code ERROR} back. The UI Spec's "only save when the login works" is
 * therefore "saved as pending, confirmed by the engine"; a failed test leaves the row in
 * {@code ERROR} for the user to fix or remove.
 */
@Service
public class CompanyService {

    static final String PENDING = "PENDING";
    static final String NEED_PASSWORD = "NEED_PASSWORD";

    private final CompanyConnectionRepository companies;
    private final CompanyAccessGuard guard;
    private final WeInvoiceSecretCipher cipher;
    private final EngineRequestService requests;
    private final WeInvoiceDb db;
    private final WeInvoiceProperties props;

    public CompanyService(CompanyConnectionRepository companies, CompanyAccessGuard guard, WeInvoiceSecretCipher cipher,
                          EngineRequestService requests, WeInvoiceDb db, WeInvoiceProperties props) {
        this.companies = companies;
        this.guard = guard;
        this.cipher = cipher;
        this.requests = requests;
        this.db = db;
        this.props = props;
    }

    public CompanyListResponse list() {
        List<CompanyItem> items = companies.listActive(guard.ownerId()).stream().map(CompanyService::toItem).toList();
        return new CompanyListResponse(items, new CompanyListResponse.Quota(items.size(), props.maxCompanies()));
    }

    public RequestAccepted create(CompanyCreateRequest req) {
        String ownerId = guard.ownerId();
        String mst = WeInvoiceIds.requireTaxId(req.companyMst());
        Optional<CompanyRow> existing = companies.find(ownerId, mst);
        if (existing.isPresent() && !existing.get().deleted()) {
            throw new BusinessException("WBE3003", mst);
        }
        requireQuota(ownerId);
        String username = blankToNull(req.gdtUsername());
        String encrypted = cipher.encrypt(req.password());
        Long requestId = db.tx().execute(s -> {
            companies.upsert(ownerId, mst, blankToNull(req.label()), username == null ? mst : username, encrypted,
                    PENDING, guard.userId());
            return requests.queue(mst, EngineRequestService.LOGIN_TEST, "MANUAL", Map.of());
        });
        return new RequestAccepted(String.valueOf(requestId), mst);
    }

    /**
     * Adds an MST seen in an upload, without password ({@code NEED_PASSWORD}, UI Spec 3.2
     * "Thêm MST … và nhập"). Already managed → no change.
     */
    public void addPending(String mst) {
        String ownerId = guard.ownerId();
        Optional<CompanyRow> existing = companies.find(ownerId, mst);
        if (existing.isPresent() && !existing.get().deleted()) return;
        requireQuota(ownerId);
        companies.upsert(ownerId, mst, null, mst, null, NEED_PASSWORD, guard.userId());
    }

    public RequestAccepted changePassword(String mst, CompanyPasswordRequest req) {
        String ownerId = guard.ownerId();
        CompanyRow row = guard.requireCompany(mst);
        String encrypted = cipher.encrypt(req.password());
        Long requestId = db.tx().execute(s -> {
            companies.updatePassword(ownerId, row.mst(), blankToNull(req.gdtUsername()), encrypted, guard.userId());
            return requests.queue(row.mst(), EngineRequestService.LOGIN_TEST, "MANUAL", Map.of());
        });
        return new RequestAccepted(String.valueOf(requestId), row.mst());
    }

    public CompanyItem patch(String mst, CompanyPatchRequest req) {
        String ownerId = guard.ownerId();
        CompanyRow row = guard.requireCompany(mst);
        if (req.label() != null) {
            companies.updateLabel(ownerId, row.mst(), blankToNull(req.label()), guard.userId());
        }
        if (Boolean.TRUE.equals(req.defaultCompany())) {
            companies.setDefault(ownerId, row.mst(), guard.userId());
        }
        return companies.find(ownerId, row.mst()).map(CompanyService::toItem).orElseThrow();
    }

    /**
     * Removes the MST from the user's list (soft delete, password erased). Invoices already
     * collected stay in the engine tables — deleting them is a PO decision still open, and
     * the backend never writes engine tables.
     */
    public void delete(String mst) {
        CompanyRow row = guard.requireCompany(mst);
        companies.softDelete(guard.ownerId(), row.mst(), guard.userId());
    }

    private void requireQuota(String ownerId) {
        if (companies.activeMsts(ownerId).size() >= props.maxCompanies()) {
            throw new BusinessException("WBE3004", props.maxCompanies());
        }
    }

    public static CompanyItem toItem(CompanyRow r) {
        return new CompanyItem(
                r.mst(),
                r.companyName(),
                r.gdtUserId(),
                r.label(),
                r.status() == null ? null : r.status().toLowerCase(Locale.ROOT),
                r.statusMessage(),
                r.hasPassword(),
                r.isDefault(),
                r.lastSyncAt(),
                r.createdAt());
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
