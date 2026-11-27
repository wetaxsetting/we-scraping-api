package com.kosign.wecms.domain.weinvoice.service;

import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.domain.weinvoice.common.CompanyAccessGuard;
import com.kosign.wecms.domain.weinvoice.common.NotificationKey;
import com.kosign.wecms.domain.weinvoice.common.NotificationReadState;
import com.kosign.wecms.domain.weinvoice.common.WeInvoiceTime;
import com.kosign.wecms.domain.weinvoice.dto.NotificationItem;
import com.kosign.wecms.domain.weinvoice.dto.NotificationListResponse;
import com.kosign.wecms.domain.weinvoice.dto.UnreadCountResponse;
import com.kosign.wecms.domain.weinvoice.repository.CompanyConnectionRepository;
import com.kosign.wecms.domain.weinvoice.repository.CompanyConnectionRepository.CompanyRow;
import com.kosign.wecms.domain.weinvoice.repository.WeInvoiceNotificationRepository;
import com.kosign.wecms.domain.weinvoice.repository.WeInvoiceNotificationRepository.BucketRow;
import com.kosign.wecms.domain.weinvoice.repository.WeInvoiceNotificationRepository.Scope;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Header bell (record docs/features/addon-service/weinvoice/weinvoice-notifications-2026-11-12.md).
 *
 * <p>Notifications are aggregated on every call by {@link WeInvoiceNotificationRepository}; this service applies the
 * read state ({@link NotificationReadState}), sorts newest first and pages. Everything is scoped to the
 * signed-in user's active tax codes ({@link CompanyAccessGuard}).
 */
@Service
public class WeInvoiceNotificationService {

    static final int DEFAULT_SIZE = 20;
    static final int MAX_SIZE = 100;

    /** Newest first; equal times by id so pages stay stable. */
    private static final Comparator<NotificationItem> ORDER =
            Comparator.comparing(NotificationItem::at, Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(NotificationItem::id, Comparator.reverseOrder());

    /** {@code filter} query values. */
    enum Filter {
        MST, ALL, ACTION;

        static Filter parse(String value) {
            if (value == null || value.isBlank()) return MST;
            try {
                return valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("filter: must be mst, all or action");
            }
        }
    }

    private final WeInvoiceNotificationRepository notifications;
    private final CompanyConnectionRepository companies;
    private final CompanyAccessGuard guard;

    public WeInvoiceNotificationService(WeInvoiceNotificationRepository notifications, CompanyConnectionRepository companies,
                               CompanyAccessGuard guard) {
        this.notifications = notifications;
        this.companies = companies;
        this.guard = guard;
    }

    /**
     * @param filter {@code mst} (default — needs {@code companyMst}, else {@code WBE3037}), {@code all}
     *               (every tax code of the user) or {@code action} (the two error types, every tax code)
     */
    public NotificationListResponse list(String companyMst, String filter, int page, int size) {
        Filter f = Filter.parse(filter);
        String ownerId = guard.ownerId();
        String mst = null;
        Map<String, String> names = new HashMap<>();
        if (f == Filter.MST) {
            if (companyMst == null || companyMst.isBlank()) {
                throw new BusinessException("WBE3037");
            }
            CompanyRow company = guard.requireCompany(companyMst);
            mst = company.mst();
            names.put(mst, company.companyName());
        } else {
            for (CompanyRow c : companies.listActive(ownerId)) names.put(c.mst(), c.companyName());
        }

        List<BucketRow> rows = notifications.buckets(ownerId, mst, f == Filter.ACTION ? Scope.ACTION_ONLY : Scope.ALL_TYPES, false);
        NotificationReadState state = NotificationReadState.of(notifications.reads(ownerId));
        List<NotificationItem> items = new ArrayList<>(rows.size());
        long unread = 0;
        for (BucketRow row : rows) {
            NotificationItem item = toItem(row, names.get(row.mst()), state);
            if (!item.read()) unread++;
            items.add(item);
        }
        items.sort(ORDER);

        int p = Math.max(0, page);
        int s = size <= 0 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
        long from = (long) p * s;
        List<NotificationItem> pageItems = from >= items.size()
                ? List.of()
                : List.copyOf(items.subList((int) from, (int) Math.min(items.size(), from + s)));
        return new NotificationListResponse(pageItems, unread, items.size(), p, s);
    }

    /** Unread items across every tax code of the user — the bell badge, polled every 60 s. */
    public UnreadCountResponse unreadCount() {
        String ownerId = guard.ownerId();
        List<BucketRow> rows = notifications.buckets(ownerId, null, Scope.ALL_TYPES, true);
        NotificationReadState state = NotificationReadState.of(notifications.reads(ownerId));
        long unread = rows.stream()
                .filter(r -> !state.isRead(r.key().id(), r.mst(), r.atDtm()))
                .count();
        return new UnreadCountResponse(unread);
    }

    /**
     * Marks ids read. Ids that are not exactly a notification id of one of the user's tax codes (or of a
     * mailbox without tax code) are ignored, so nothing foreign or malformed is stored.
     */
    public void markRead(List<String> ids) {
        String ownerId = guard.ownerId();
        if (ids == null || ids.isEmpty()) return;
        Set<String> owned = new HashSet<>(guard.ownedMsts());
        Set<String> keys = new LinkedHashSet<>();
        for (String id : ids) {
            NotificationKey.parse(id)
                    .filter(k -> k.mst() == null || owned.contains(k.mst()))
                    .ifPresent(k -> keys.add(k.id()));
        }
        notifications.markRead(ownerId, keys);
    }

    /** "Read all" of one tax code ({@code *|<mst>}) or of every tax code ({@code *}). */
    public void markAllRead(String companyMst) {
        String ownerId = guard.ownerId();
        String marker = (companyMst == null || companyMst.isBlank())
                ? NotificationKey.marker(null)
                : NotificationKey.marker(guard.requireCompany(companyMst).mst());
        notifications.markAll(ownerId, marker);
    }

    static NotificationItem toItem(BucketRow row, String companyName, NotificationReadState state) {
        NotificationKey key = row.key();
        String id = key.id();
        NotificationKey.Type type = row.type();
        Long fromProvider = null;
        Long fromEmail = null;
        if (type == NotificationKey.Type.PDF_READY) {
            long total = row.count() == null ? 0 : row.count();
            fromEmail = row.fromEmail() == null ? 0 : row.fromEmail();
            fromProvider = total - fromEmail;
        }
        List<String> months = type == NotificationKey.Type.NEW_INVOICES
                ? (row.months() == null ? List.of() : row.months())
                : null;
        return new NotificationItem(
                id,
                type.name(),
                row.mst(),
                row.mst() == null ? null : companyName,
                key.direction(),
                type.action() ? null : row.count(),
                fromProvider,
                fromEmail,
                months,
                row.mailboxId() == null ? null : String.valueOf(row.mailboxId()),
                row.address(),
                type.action() ? row.status() : null,
                type.action() ? row.message() : null,
                WeInvoiceTime.dtmToIso(row.atDtm()),
                state.isRead(id, row.mst(), row.atDtm()),
                type.action());
    }
}
