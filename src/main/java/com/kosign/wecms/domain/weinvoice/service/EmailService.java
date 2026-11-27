package com.kosign.wecms.domain.weinvoice.service;

import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.domain.weinvoice.common.CompanyAccessGuard;
import com.kosign.wecms.domain.weinvoice.common.WeInvoiceIds;
import com.kosign.wecms.domain.weinvoice.common.WeInvoicePaging;
import com.kosign.wecms.domain.weinvoice.common.WeInvoiceTime;
import com.kosign.wecms.domain.weinvoice.config.WeInvoiceProperties;
import com.kosign.wecms.domain.weinvoice.dto.EmailAddressResponse;
import com.kosign.wecms.domain.weinvoice.dto.EmailMessageDetail;
import com.kosign.wecms.domain.weinvoice.dto.EmailMessageItem;
import com.kosign.wecms.domain.weinvoice.dto.WeInvoicePage;
import com.kosign.wecms.domain.weinvoice.repository.EmailRepository;
import com.kosign.wecms.domain.weinvoice.repository.EmailRepository.FileRow;
import com.kosign.wecms.domain.weinvoice.repository.EmailRepository.MessageRow;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Screen 07 — invoices received by mail, as the engine processed them. */
@Service
public class EmailService {

    private static final List<String> RESULTS = List.of("matched", "duplicate", "unmatched", "no_invoice", "error");

    private final CompanyAccessGuard guard;
    private final EmailRepository emails;
    private final WeInvoiceProperties props;

    public EmailService(CompanyAccessGuard guard, EmailRepository emails, WeInvoiceProperties props) {
        this.guard = guard;
        this.emails = emails;
        this.props = props;
    }

    /** {@code hd-<mst>@<domain>}; the engine routes mail to the MST by that address. */
    public EmailAddressResponse address(String companyMst) {
        String mst = guard.requireCompany(companyMst).mst();
        String domain = props.mailDomain();
        if (domain == null || domain.isBlank() || domain.startsWith("${")) {
            return new EmailAddressResponse(mst, null, false);
        }
        return new EmailAddressResponse(mst, "hd-" + mst + "@" + domain.trim().toLowerCase(Locale.ROOT), true);
    }

    public WeInvoicePage<EmailMessageItem> messages(String companyMst, String from, String to, String result,
                                                    int page, int size) {
        String mst = guard.requireCompany(companyMst).mst();
        LocalDate f = from == null || from.isBlank() ? null : WeInvoiceTime.parseDate(from, "from");
        LocalDate t = to == null || to.isBlank() ? null : WeInvoiceTime.parseDate(to, "to");
        if (f != null && t != null && f.isAfter(t)) {
            throw new BusinessException("WBE3016");
        }
        String r = result == null || result.isBlank() ? null : result.trim().toLowerCase(Locale.ROOT);
        if (r != null && !RESULTS.contains(r)) {
            throw new IllegalArgumentException("result: must be one of " + RESULTS);
        }
        int p = WeInvoicePaging.page(page);
        int s = WeInvoicePaging.size(size);
        List<String> msts = List.of(mst);
        List<MessageRow> rows = emails.list(msts, f, t, r, p, s);
        Map<Long, List<FileRow>> files = emails.filesOf(rows.stream().map(MessageRow::id).toList());
        List<EmailMessageItem> items = rows.stream().map(m -> toItem(m, files.getOrDefault(m.id(), List.of()))).toList();
        return new WeInvoicePage<>(items, emails.count(msts, f, t, r), p, s);
    }

    public EmailMessageDetail message(String id) {
        long emailId = WeInvoiceIds.parse(id, "id");
        List<String> msts = guard.ownedMsts();
        if (msts.isEmpty()) {
            throw new BusinessException("WBE3011");
        }
        MessageRow m = emails.find(msts, emailId).orElseThrow(() -> new BusinessException("WBE3011"));
        List<FileRow> files = emails.filesOf(List.of(emailId)).getOrDefault(emailId, List.of());
        List<EmailMessageDetail.FileItem> fileItems = files.stream()
                .map(f -> new EmailMessageDetail.FileItem(String.valueOf(f.id()), f.name(), f.kind(), f.status(),
                        f.invoiceId() == null ? null : String.valueOf(f.invoiceId()), f.note()))
                .toList();
        List<EmailMessageDetail.LinkItem> links = emails.links(emailId).stream()
                .map(l -> new EmailMessageDetail.LinkItem(String.valueOf(l.id()), l.host(), l.url(), l.code(), l.status(),
                        l.invoiceId() == null ? null : String.valueOf(l.invoiceId()), l.attempts(), l.lastError()))
                .toList();
        return new EmailMessageDetail(toItem(m, files), fileItems, links);
    }

    private static EmailMessageItem toItem(MessageRow m, List<FileRow> files) {
        return new EmailMessageItem(
                String.valueOf(m.id()),
                m.receivedAt(),
                m.from(),
                m.subject(),
                files.stream().map(FileRow::name).toList(),
                m.result(),
                m.engineStatus(),
                m.invoiceId() == null ? null : String.valueOf(m.invoiceId()));
    }
}
