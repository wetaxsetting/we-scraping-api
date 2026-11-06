package com.kosign.wecms.domain.weinvoice.service;

import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.domain.weinvoice.common.CompanyAccessGuard;
import com.kosign.wecms.domain.weinvoice.common.WeInvoiceIds;
import com.kosign.wecms.domain.weinvoice.common.WeInvoiceSecretCipher;
import com.kosign.wecms.domain.weinvoice.config.WeInvoiceProperties;
import com.kosign.wecms.domain.weinvoice.dto.CompanyCreateRequest;
import com.kosign.wecms.domain.weinvoice.dto.CompanyItem;
import com.kosign.wecms.domain.weinvoice.dto.CompanyListResponse;
import com.kosign.wecms.domain.weinvoice.dto.CompanyPasswordRequest;
import com.kosign.wecms.domain.weinvoice.dto.CompanyPatchRequest;
import com.kosign.wecms.domain.weinvoice.repository.CompanyConnectionRepository;
import com.kosign.wecms.domain.weinvoice.repository.CompanyConnectionRepository.CompanyRow;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Screen 06 — the tax codes a user manages and their GDT account.
 *
 * <p>Adding an MST or changing its password first logs in to the GDT portal through the scraping
 * engine ({@link ScrapingClient#loginTest}: captcha solved by the engine's model, retried on a wrong
 * captcha). Only an accepted account is stored — encrypted ({@link WeInvoiceSecretCipher}),
 * {@code CONNECTED} — and the call answers with the saved connection, so the screen never waits on
 * a queue (UI Spec "đúng thì mới lưu"). A rejected account answers {@code WBE3027} with the portal's
 * message; when the account cannot be checked (captcha, network, engine down) {@code WBE3028}.
 * Nothing is written in either case; a password change keeps the old password.
 */
@Service
public class CompanyService {

    static final String CONNECTED = "CONNECTED";
    static final String NEED_PASSWORD = "NEED_PASSWORD";

    private final CompanyConnectionRepository companies;
    private final CompanyAccessGuard guard;
    private final WeInvoiceSecretCipher cipher;
    private final ScrapingClient scraping;
    private final WeInvoiceProperties props;

    public CompanyService(CompanyConnectionRepository companies, CompanyAccessGuard guard, WeInvoiceSecretCipher cipher,
                          ScrapingClient scraping, WeInvoiceProperties props) {
        this.companies = companies;
        this.guard = guard;
        this.cipher = cipher;
        this.scraping = scraping;
        this.props = props;
    }

    public CompanyListResponse list() {
        List<CompanyItem> items = companies.listActive(guard.ownerId()).stream().map(CompanyService::toItem).toList();
        return new CompanyListResponse(items, new CompanyListResponse.Quota(items.size(), props.maxCompanies()));
    }

    public CompanyItem create(CompanyCreateRequest req) {
        String ownerId = guard.ownerId();
        String mst = WeInvoiceIds.requireTaxId(req.companyMst());
        Optional<CompanyRow> existing = companies.find(ownerId, mst);
        if (existing.isPresent() && !existing.get().deleted()) {
            throw new BusinessException("WBE3003", mst);
        }
        requireQuota(ownerId);
        String username = Optional.ofNullable(blankToNull(req.gdtUsername())).orElse(mst);
        requireAccepted(mst, scraping.loginTest(mst, username, req.password()));
        companies.upsert(ownerId, mst, blankToNull(req.label()), username, cipher.encrypt(req.password()),
                CONNECTED, guard.userId());
        return companies.find(ownerId, mst).map(CompanyService::toItem).orElseThrow();
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

    public CompanyItem changePassword(String mst, CompanyPasswordRequest req) {
        String ownerId = guard.ownerId();
        CompanyRow row = guard.requireCompany(mst);
        String newUsername = blankToNull(req.gdtUsername());
        String username = Optional.ofNullable(newUsername)
                .or(() -> Optional.ofNullable(blankToNull(row.gdtUserId())))
                .orElse(row.mst());
        requireAccepted(row.mst(), scraping.loginTest(row.mst(), username, req.password()));
        companies.updatePassword(ownerId, row.mst(), newUsername, cipher.encrypt(req.password()), guard.userId());
        return companies.find(ownerId, row.mst()).map(CompanyService::toItem).orElseThrow();
    }

    /** {@code WBE3027} when GDT rejected the account, {@code WBE3028} when it could not be checked. */
    private static void requireAccepted(String mst, ScrapingClient.LoginResult result) {
        switch (result.outcome()) {
            case OK -> {
            }
            case REJECTED -> throw new BusinessException("WBE3027", mst, result.message());
            case UNAVAILABLE -> throw new BusinessException("WBE3028", mst, result.message());
        }
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
