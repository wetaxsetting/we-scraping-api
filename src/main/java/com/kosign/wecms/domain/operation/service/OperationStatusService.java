package com.kosign.wecms.domain.operation.service;

import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.common.web.SortDirection;
import com.kosign.wecms.domain.operation.dto.OperationStatusListResponse;
import com.kosign.wecms.domain.operation.dto.OperationStatusRow;
import com.kosign.wecms.domain.operation.entity.OprtSttsInfo;
import com.kosign.wecms.domain.operation.repository.OprtSttsInfoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

/**
 * Add-on &gt; Operation Status Inquiry service. Reads
 * {@code OPRT_STTS_INFO} (legacy {@code aosv_0001_001_r001}) — every
 * row is a tax-authority operating-status snapshot for one counterparty
 * (per (BIZ_KEY, TAXCODE) pair).
 *
 * <p>Refactor R&amp;D scope is local-only — the live OSMC → GDT refresh
 * path (legacy NXISAS / {@code TRCO_TRCO001_00003}) is intentionally
 * NOT wired. The Inquire / Update buttons on the page either re-fetch
 * the local snapshot ({@code refreshKey++} on the frontend) or stay
 * inert. Bulk Inquire + audit-log writes to {@code EXT_TRAN_CALL_HIS}
 * land if/when the NXISAS bridge is ported.
 */
@Service
@RequiredArgsConstructor
public class OperationStatusService {

    /** Largest {@code size} the controller will honour. Matches the
     *  frontend {@code PAGE_SIZE_OPTIONS} maximum ({@code 100}). */
    public static final int MAX_PAGE_SIZE = 100;

    /** Default page size when the caller omits {@code size}. Matches
     *  {@code PAGE_SIZE_OPTIONS[0] = 10}. */
    public static final int DEFAULT_PAGE_SIZE = 10;

    private final OprtSttsInfoRepository repo;

    /**
     * Default ORDER BY mirrors the legacy listing's practical order —
     * latest poll first, with TAXCODE as the deterministic tiebreaker
     * so pagination stays stable across rows that share a poll
     * timestamp. Legacy uses {@code SortHelper.getSortDynamic(...)}
     * which is View-Setting-driven; in real DEV data the seeded
     * View Setting resolves to {@code SEARCHDATETIME DESC}.
     */
    private static final Sort DEFAULT_SORT =
            Sort.by(Sort.Order.desc("searchdatetime"),
                    Sort.Order.asc("taxcode"));

    /* ------------------------------------------------------------------
     *  Public list overloads
     * ------------------------------------------------------------------ */

    /**
     * Filtered + paginated list. Builds the {@link Pageable} from caller
     * primitives. {@code page} is zero-based; {@code size} is clamped to
     * {@link #MAX_PAGE_SIZE} and defaults to {@link #DEFAULT_PAGE_SIZE}
     * when {@code size <= 0}.
     */
    @Transactional(readOnly = true)
    public OperationStatusListResponse list(
            OperationStatusFilter filter,
            String sortField,
            String sortDirection,
            int page,
            int size) {
        int safeSize = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        int safePage = Math.max(0, page);
        Sort sort = buildSort(OperationStatusSortField.parse(sortField),
                              SortDirection.parse(sortDirection));
        Pageable pageable = PageRequest.of(safePage, safeSize, sort);
        return list(filter, sortField, sortDirection, pageable);
    }

    /**
     * Core list method — every other overload funnels here. Pass
     * {@link Pageable#unpaged()} for the export path and any caller that
     * needs the full filtered set.
     */
    @Transactional(readOnly = true)
    public OperationStatusListResponse list(
            OperationStatusFilter filter,
            String sortField,
            String sortDirection,
            Pageable pageable) {
        final String bizKey = TenantContext.currentBizKey();
        final OperationStatusFilter effective = filter == null ? OperationStatusFilter.empty() : filter;

        // Apply DEFAULT_SORT only when the caller's Pageable carries no
        // sort of its own (export path passes Sort.unsorted()). When the
        // caller already built a Sort (controller's PageRequest above
        // already wove DEFAULT_SORT in via buildSort), we must not double
        // it.
        Pageable effectivePageable = pageable.getSort().isSorted()
                ? pageable
                : applyDefaultSortIfNeeded(pageable, sortField, sortDirection);

        String keywordPattern       = buildLikePattern(effective.keyword());
        String companyStatusPattern = buildLikePattern(effective.companyStatus());

        Page<OprtSttsInfo> p = repo.findFiltered(
                bizKey,
                companyStatusPattern,
                keywordPattern,
                effectivePageable);

        List<OperationStatusRow> rows = p.getContent().stream().map(this::toRow).toList();

        // Page.getSize() and getTotalPages() handle the unpaged case:
        // Spring Data's Chunk#getSize() returns content.size() when the
        // underlying Pageable is unpaged, and PageImpl#getTotalPages()
        // collapses to 1.
        return new OperationStatusListResponse(
                rows,
                p.getTotalElements(),
                p.getNumber(),
                p.getSize(),
                p.getTotalPages()
        );
    }

    /* ------------------------------------------------------------------
     *  Filter / sort plumbing
     * ------------------------------------------------------------------ */

    /**
     * Lower-case + escape LIKE meta-chars + wrap with {@code %…%}.
     * Uses {@code !} as the escape character so the JPQL pattern stays
     * portable — pairs with the {@code ESCAPE '!'} clause on every
     * predicate in {@link OprtSttsInfoRepository#findFiltered}.
     *
     * <p>Returns {@code null} when the value is null/blank so the JPQL
     * guard skips the predicate entirely.
     */
    private static String buildLikePattern(String raw) {
        if (raw == null) return null;
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) return null;
        StringBuilder b = new StringBuilder(trimmed.length() + 2).append('%');
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (c == '%' || c == '_' || c == '!') b.append('!');
            b.append(c);
        }
        b.append('%');
        return b.toString().toLowerCase(Locale.ROOT);
    }

    /**
     * Build a Spring Data {@link Sort} from the validated whitelist enum
     * + direction. The user's primary sort is always followed by the
     * default order so equal-keyed rows keep a deterministic order
     * across requests.
     */
    private static Sort buildSort(OperationStatusSortField field, SortDirection direction) {
        if (field == null || direction == null) {
            return DEFAULT_SORT;
        }
        Sort.Order primary = direction == SortDirection.ASC
                ? Sort.Order.asc(field.entityProperty())
                : Sort.Order.desc(field.entityProperty());
        return Sort.by(primary).and(DEFAULT_SORT);
    }

    /** When a caller passes a {@link Pageable} with no sort, weave the
     *  user-driven sort (or DEFAULT_SORT) in. Used by the export path
     *  which builds {@code Pageable.unpaged()} without a Sort. */
    private static Pageable applyDefaultSortIfNeeded(
            Pageable pageable, String sortField, String sortDirection) {
        Sort sort = buildSort(OperationStatusSortField.parse(sortField),
                              SortDirection.parse(sortDirection));
        if (pageable.isUnpaged()) {
            return Pageable.unpaged(sort);
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
    }

    /* ------------------------------------------------------------------
     *  Row mapping
     * ------------------------------------------------------------------ */

    private OperationStatusRow toRow(OprtSttsInfo r) {
        return new OperationStatusRow(
                r.getTaxcode(),
                r.getCompanyname(),
                r.getCompanystatus(),
                resolveStatusCd(r.getCompanystatus()),
                r.getOfficeadd(),
                r.getSearchdatetime()
        );
    }

    /**
     * Map the GDT free-text Vietnamese {@code COMPANYSTATUS} into a
     * compact 3-state code the static UI's {@code OperationStatusCode}
     * enum expects.
     *
     * <p>Observed DEV strings (audit 2026-05-04, tenant
     * {@code VN_VNM0004_000001}, 6 distinct values across 202 rows):
     * <ul>
     *   <li>"NNT đang hoạt động (đã được cấp GCN ĐKT)"  → active</li>
     *   <li>"NNT đang hoạt động"                          → active</li>
     *   <li>"NNT đang hoạt động (được cấp thông báo MST)" → active</li>
     *   <li>"NNT ngừng hoạt động và đã đóng MST"          → suspended</li>
     *   <li>"NNT ngừng hoạt động và đã hoàn thành thủ tục
     *        chấm dứt hiệu lực MST"                       → suspended</li>
     *   <li>"NNT tạm nghỉ kinh doanh có thời hạn"         → suspended</li>
     * </ul>
     *
     * <p>Anything we don't recognise → {@code not_registered}. This is a
     * defensive fallback; if it starts firing on real data the regex
     * needs tightening — extend the list above.
     */
    static String resolveStatusCd(String status) {
        if (status == null) return "not_registered";
        String s = status.toLowerCase(Locale.ROOT);
        if (s.contains("đang hoạt động")) return "active_certificate_issued";
        if (s.contains("ngừng hoạt động")) return "suspended";
        if (s.contains("tạm ngừng"))       return "suspended";
        if (s.contains("tạm nghỉ"))        return "suspended";
        return "not_registered";
    }
}
