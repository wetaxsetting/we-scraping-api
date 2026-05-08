package com.kosign.wecms.domain.setting.firmbankinghistory.service;

import com.kosign.wecms.common.enums.FirmBankingSvcType;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.common.web.SortDirection;
import com.kosign.wecms.domain.setting.firmbankinghistory.dto.FirmBankingHistoryListResponse;
import com.kosign.wecms.domain.setting.firmbankinghistory.dto.FirmBankingHistoryRow;
import com.kosign.wecms.domain.setting.firmbankinghistory.entity.FirmBankingHistoryView;
import com.kosign.wecms.domain.setting.firmbankinghistory.repository.FirmBankingHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Settings &gt; Firm Banking History read service. Mirrors the legacy
 * {@code comm_0221_001_r001} read path
 * ({@code WECMS DEV/.../comm/comm_0221_001_r001_act.jsp}) over the
 * unified {@code V_FIRM_BANKING_HISTORY} view (V60) — five legs in,
 * one paged result out.
 *
 * <p>Validation parity with the legacy action JSP:
 * <ul>
 *   <li>{@code FROM_DT} / {@code TO_DT} required —
 *       {@code ValidationHelper.validateRequired(util, input, …)}
 *       (act:30–31).</li>
 *   <li>{@code FROM_DT} / {@code TO_DT} valid {@code YYYYMMDD} —
 *       {@code ValidationHelper.validateDate8(util, input, …)}
 *       (act:33–34).</li>
 *   <li>{@code FROM_DT &lt;= TO_DT} —
 *       {@code ValidationHelper.validatePeriod(util, fromDt, toDt)}
 *       (act:36).</li>
 * </ul>
 *
 * <p>Refactor enhancements (no JEX twin):
 * <ul>
 *   <li>{@code svc_type} validated against {@code {F, S, G, B}} — legacy
 *       silently accepted any 1-char string.</li>
 *   <li>{@code sort_field} validated against
 *       {@link FirmBankingHistorySortField} whitelist — legacy
 *       {@code SortHelper.getSortDynamic} accepted free-form input.</li>
 *   <li>{@code page} / {@code size} are zero-based + clamped to
 *       {@link #MAX_PAGE_SIZE}; legacy was 1-based and unclamped.</li>
 * </ul>
 *
 * <p>Tenant scoping: {@code BIZ_KEY} is read from
 * {@link TenantContext#currentBizKey()} and applied as the first
 * predicate of every query. The wire format intentionally has no
 * {@code biz_key} field; even if a caller sent one it would be ignored.
 */
@Service
@RequiredArgsConstructor
public class FirmBankingHistoryService {

    /** Largest page size honoured. Mirrors the global cap used across
     *  every refactor list endpoint. Excess values are clamped, not
     *  rejected, so an accidental large value still returns. */
    public static final int MAX_PAGE_SIZE = 100;

    /** Default page size — matches the legacy view's
     *  {@code comm_0221_001_view.jsp} default of {@code 15}. */
    public static final int DEFAULT_PAGE_SIZE = 15;

    /** Allowed {@code svc_type} discriminators per the unified view's
     *  five legs. */
    private static final Set<String> ALLOWED_SVC_TYPES = Set.of("F", "S", "G", "B");

    /** Default ORDER BY mirrors the legacy frontend's
     *  {@code _v2common.app.sortData} default sort (TARN_DTM DESC).
     *  {@code SEQ_NO DESC} is the deterministic tiebreaker so equal
     *  timestamps keep a stable order across paged requests. */
    private static final Sort DEFAULT_SORT =
            Sort.by(Sort.Order.desc("tarnDtm"), Sort.Order.desc("seqNo"));

    private static final DateTimeFormatter YYYYMMDD =
            DateTimeFormatter.ofPattern("yyyyMMdd");

    private final FirmBankingHistoryRepository repository;

    /**
     * Filtered + paginated list. Builds the {@link Pageable} from caller
     * primitives so the controller stays free of Spring Data types.
     *
     * @param fromDate      required {@code YYYYMMDD}
     * @param toDate        required {@code YYYYMMDD}, ≥ {@code fromDate}
     * @param svcType       optional; one of {@code F}/{@code S}/{@code G}/{@code B}
     * @param keyword       optional; partial match on {@code seq_no}
     * @param sortField     optional; whitelisted {@code FIELD_ID}
     * @param sortDirection optional; {@code ASC}/{@code DESC}
     * @param page          zero-based; defaults to {@code 0} when negative
     * @param size          rows per page; defaults to {@link #DEFAULT_PAGE_SIZE}
     *                      when {@code <= 0}, clamped to {@link #MAX_PAGE_SIZE}
     */
    @Transactional(readOnly = true)
    public FirmBankingHistoryListResponse list(
            String fromDate,
            String toDate,
            String svcType,
            String keyword,
            String sortField,
            String sortDirection,
            int page,
            int size) {

        String fromDt = validateDate("from_date", fromDate);
        String toDt   = validateDate("to_date",   toDate);
        validatePeriod(fromDt, toDt);
        String svcTypeFilter = validateSvcType(svcType);
        String keywordPattern = buildKeywordPattern(keyword);

        int safeSize = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        int safePage = Math.max(0, page);
        Sort sort = buildSort(
                FirmBankingHistorySortField.parse(sortField),
                SortDirection.parse(sortDirection));
        Pageable pageable = PageRequest.of(safePage, safeSize, sort);

        String bizKey = TenantContext.currentBizKey();
        Page<FirmBankingHistoryView> result = repository.search(
                bizKey, fromDt, toDt, svcTypeFilter, keywordPattern, pageable);

        List<FirmBankingHistoryRow> rows = result.getContent().stream()
                .map(FirmBankingHistoryService::toRow)
                .toList();

        return new FirmBankingHistoryListResponse(
                rows,
                result.getTotalElements(),
                result.getNumber(),
                result.getSize(),
                result.getTotalPages());
    }

    /* -----------------------------------------------------------------
     *  Validation
     * ----------------------------------------------------------------- */

    /** Required + 8-digit + parseable. Mirrors legacy
     *  {@code ValidationHelper.validateRequired} +
     *  {@code ValidationHelper.validateDate8}. */
    private static String validateDate(String fieldName, String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BusinessException("WBE_VALIDATION", fieldName + ": is required");
        }
        String trimmed = raw.trim();
        if (trimmed.length() != 8) {
            throw new BusinessException("WBE_VALIDATION", fieldName + ": invalid date format (YYYYMMDD)");
        }
        try {
            LocalDate.parse(trimmed, YYYYMMDD);
        } catch (DateTimeParseException ex) {
            throw new BusinessException("WBE_VALIDATION", fieldName + ": invalid date format (YYYYMMDD)");
        }
        return trimmed;
    }

    /** {@code from <= to}. Mirrors legacy
     *  {@code ValidationHelper.validatePeriod}. */
    private static void validatePeriod(String fromDt, String toDt) {
        if (fromDt.compareTo(toDt) > 0) {
            throw new BusinessException(
                    "WBE_VALIDATION",
                    "from_date: must be on or before to_date");
        }
    }

    /** {@code svc_type} optional; if present must be one of the four
     *  legs. Returns {@code null} for blank/missing so the JPQL
     *  {@code IS NULL OR} guard skips the predicate. */
    private static String validateSvcType(String svcType) {
        if (svcType == null) return null;
        String trimmed = svcType.trim();
        if (trimmed.isEmpty()) return null;
        String upper = trimmed.toUpperCase(Locale.ROOT);
        if (!ALLOWED_SVC_TYPES.contains(upper)) {
            throw new BusinessException(
                    "WBE_VALIDATION",
                    "svc_type: must be one of F, S, G, B");
        }
        return upper;
    }

    /* -----------------------------------------------------------------
     *  Keyword + sort plumbing
     * ----------------------------------------------------------------- */

    /**
     * Lower-case + escape LIKE meta-chars + wrap with {@code %…%}. Uses
     * {@code !} as the escape character so the JPQL pattern stays
     * portable (same convention as
     * {@link com.kosign.wecms.domain.banking.service.AccountService}'s
     * {@code buildKeywordPattern}). Returns {@code null} when blank so
     * the JPQL {@code IS NULL OR} guard skips the predicate.
     */
    private static String buildKeywordPattern(String raw) {
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

    /** Whitelist-driven sort. Unknown / blank field or direction →
     *  {@link #DEFAULT_SORT}. */
    private static Sort buildSort(FirmBankingHistorySortField field, SortDirection direction) {
        if (field == null || direction == null) {
            return DEFAULT_SORT;
        }
        Sort.Order primary = direction == SortDirection.ASC
                ? Sort.Order.asc(field.entityProperty())
                : Sort.Order.desc(field.entityProperty());
        // Tiebreaker preserves stable ordering across pages even when the
        // primary key has duplicates (e.g. svc_type='F' all sharing one
        // tarn_dtm second).
        return Sort.by(primary).and(Sort.by(Sort.Order.desc("seqNo")));
    }

    /* -----------------------------------------------------------------
     *  Row mapping
     * ----------------------------------------------------------------- */

    private static FirmBankingHistoryRow toRow(FirmBankingHistoryView v) {
        // Resolve the wire-side svc_name_key here so the DTO carries
        // both the raw legacy value (svc_nm) and the i18n key the
        // frontend renders. Source of truth for the F-leg is the
        // Spring TranTp enum (mirrors legacy wabooks.enums.TranTp);
        // S / G / B legs map against the V60 view's CASE-WHEN
        // English literals. See FirmBankingServiceNameResolver.
        //
        // svc_type_key (added 2026-05-08): F/S/G/B → frontend i18n key
        // resolved through FirmBankingSvcType. Replaces the legacy
        // frontend mappers.ts:coerceFirmBankingServiceType switch.
        return new FirmBankingHistoryRow(
                v.getTarnDtm(),
                v.getSvcType(),
                FirmBankingSvcType.resolveKey(v.getSvcType()),
                v.getSvcNm(),
                FirmBankingServiceNameResolver.resolve(v.getSvcType(), v.getSvcNm()),
                v.getSeqNo(),
                v.getDepBankCd(),
                v.getRsltCd(),
                v.getRsltMsg(),
                v.getJob(),
                v.getReqData(),
                v.getVanOrgCd());
    }
}
