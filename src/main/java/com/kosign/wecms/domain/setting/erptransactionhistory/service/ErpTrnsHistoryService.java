package com.kosign.wecms.domain.setting.erptransactionhistory.service;

import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.common.web.SortDirection;
import com.kosign.wecms.domain.setting.erptransactionhistory.dto.ErpTrnsHistoryCounts;
import com.kosign.wecms.domain.setting.erptransactionhistory.dto.ErpTrnsHistoryListResponse;
import com.kosign.wecms.domain.setting.erptransactionhistory.dto.ErpTrnsHistoryProjection;
import com.kosign.wecms.domain.setting.erptransactionhistory.dto.ErpTrnsHistoryRow;
import com.kosign.wecms.domain.setting.erptransactionhistory.entity.ErpTrnsHstr;
import com.kosign.wecms.domain.setting.erptransactionhistory.entity.ErpTrnsHstrId;
import com.kosign.wecms.domain.setting.erptransactionhistory.repository.ErpTrnsHstrRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Settings &gt; ERP Transaction History read service. Mirrors the legacy
 * {@code comm_0219_001_r001} (list + counts) and {@code comm_0219_001_r002}
 * (download) read paths from {@code WECMS DEV/.../comm/} over the
 * refactor's {@link ErpTrnsHstr} entity.
 *
 * <p><b>JEX parity</b> ({@code comm_0219_001_r001_act.jsp}):
 * <ul>
 *   <li>{@code FROM_DT} / {@code TO_DT} required + 8-digit + parseable —
 *       legacy {@code ValidationHelper.validateRequired} +
 *       {@code ValidationHelper.validateDate8} (act:30–34).</li>
 *   <li>{@code FROM_DT &lt;= TO_DT} —
 *       legacy {@code ValidationHelper.validatePeriod(util, fromDt, toDt)}
 *       (act:36).</li>
 *   <li>{@code SUBSTRING(REG_DTM FROM 1 FOR 8) BETWEEN ? AND ?} predicate
 *       — legacy {@code getDynamic} (act:46-48) and {@code getDynamicCNT}
 *       (act:81-83) both build it identically.</li>
 *   <li>Counts query intentionally drops {@code ERP_TYPE} predicate —
 *       legacy {@code getDynamicCNT} (act:77-108) constructs the count
 *       WHERE without {@code erp_type} so the four tab chips all reflect
 *       the same date / type / keyword scope.</li>
 *   <li>{@code WB_GET_USER_NM(REG_ID)} replaced by LEFT JOIN — see the
 *       repository's {@code search} method.</li>
 * </ul>
 *
 * <p><b>Refactor enhancements</b> (no JEX twin):
 * <ul>
 *   <li>{@code erp_type} validated against {@code {W, E, D}} — legacy
 *       silently accepted any 1-char string.</li>
 *   <li>{@code trns_type_cd} validated against {@code "01".."17"} —
 *       legacy passed through without validation.</li>
 *   <li>{@code sort_field} validated against
 *       {@link ErpTrnsHistorySortField} whitelist — legacy
 *       {@code SortHelper.getSortDynamic} accepted free-form input.</li>
 *   <li>{@code page} / {@code size} are zero-based + clamped to
 *       {@link #MAX_PAGE_SIZE}; legacy was 1-based and unclamped.</li>
 *   <li>{@code erp_type_key} / {@code trns_type_key} server-resolved i18n
 *       keys per {@code wecms-api/CLAUDE.md §"Domain enums"}.</li>
 * </ul>
 *
 * <p><b>Tenant scoping</b>: {@code BIZ_KEY} is read from
 * {@link TenantContext#currentBizKey()} and applied as the first
 * predicate of every query. The wire format intentionally has no
 * {@code biz_key} field; even if a caller sent one it would be ignored.
 */
@Service
@RequiredArgsConstructor
public class ErpTrnsHistoryService {

    /** Largest page size honoured. Mirrors the global cap used across
     *  every refactor list endpoint. Excess values are clamped, not
     *  rejected. */
    public static final int MAX_PAGE_SIZE = 100;

    /** Default page size — matches the legacy view's
     *  {@code comm_0219_001_view.jsp:373} default of {@code 15}. */
    public static final int DEFAULT_PAGE_SIZE = 15;

    /** Allowed {@code erp_type} discriminator values:
     *  <ul>
     *    <li>{@code W} — WeCMS → ERP</li>
     *    <li>{@code E} — ERP → WeCMS</li>
     *    <li>{@code D} — Deleted (soft-delete of an {@code E} row)</li>
     *  </ul> */
    private static final Set<String> ALLOWED_ERP_TYPES = Set.of("W", "E", "D");

    /** Default ORDER BY — OQ-7 resolved 2026-05-11 ({@code regDtm DESC,
     *  seqNo DESC}). The {@code seqNo} tiebreaker keeps a deterministic
     *  order across paged requests when multiple rows share the same
     *  {@code reg_dtm} second. */
    private static final Sort DEFAULT_SORT =
            Sort.by(Sort.Order.desc("regDtm"), Sort.Order.desc("seqNo"));

    private static final DateTimeFormatter YYYYMMDD =
            DateTimeFormatter.ofPattern("yyyyMMdd");

    private final ErpTrnsHstrRepository repository;
    private final ErpTrnsHistoryFileResolver fileResolver;

    /* =================================================================
     *  List + counts
     * ================================================================= */

    /**
     * Filtered + paginated list + four tab counts in one read-only
     * transaction. Builds the {@link Pageable} from caller primitives so
     * the controller stays free of Spring Data types.
     *
     * @param fromDate      required {@code YYYYMMDD}
     * @param toDate        required {@code YYYYMMDD}, ≥ {@code fromDate}
     * @param erpType       optional; one of {@code W}/{@code E}/{@code D}
     * @param trnsTypeCd    optional; {@code "01".."17"} (2-char numeric)
     * @param keyword       optional; partial match on {@code reg_id}
     * @param sortField     optional; whitelisted {@code FIELD_ID}
     * @param sortDirection optional; {@code ASC}/{@code DESC}
     * @param page          zero-based; defaults to {@code 0} when negative
     * @param size          rows per page; defaults to {@link #DEFAULT_PAGE_SIZE}
     *                      when {@code <= 0}, clamped to {@link #MAX_PAGE_SIZE}
     */
    @Transactional(readOnly = true)
    public ErpTrnsHistoryListResponse list(
            String fromDate,
            String toDate,
            String erpType,
            String trnsTypeCd,
            String keyword,
            String sortField,
            String sortDirection,
            int page,
            int size) {

        String fromDt = validateDate("from_date", fromDate);
        String toDt   = validateDate("to_date",   toDate);
        validatePeriod(fromDt, toDt);
        String erpTypeFilter  = validateErpType(erpType);
        String trnsTypeFilter = validateTrnsTypeCd(trnsTypeCd);
        String keywordPattern = buildKeywordPattern(keyword);

        int safeSize = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        int safePage = Math.max(0, page);
        Sort sort = buildSort(
                ErpTrnsHistorySortField.parse(sortField),
                SortDirection.parse(sortDirection));
        Pageable pageable = PageRequest.of(safePage, safeSize, sort);

        String bizKey = TenantContext.currentBizKey();

        Page<ErpTrnsHistoryProjection> result = repository.search(
                bizKey, fromDt, toDt, erpTypeFilter, trnsTypeFilter, keywordPattern, pageable);

        ErpTrnsHistoryCounts counts = repository.countAllDirections(
                bizKey, fromDt, toDt, trnsTypeFilter, keywordPattern);

        List<ErpTrnsHistoryRow> rows = result.getContent().stream()
                .map(ErpTrnsHistoryService::toRow)
                .toList();

        return new ErpTrnsHistoryListResponse(
                rows,
                counts.totalCount(),
                counts.totalCountWecms(),
                counts.totalCountErp(),
                counts.totalCountDeleted(),
                result.getNumber(),
                result.getSize(),
                result.getTotalPages());
    }

    /**
     * Same filters + sort as {@link #list}, but returns the entire
     * filtered set unpaged. Consumed only by
     * {@code ErpTrnsHistoryExportProvider} — the Excel export emits
     * every matching row regardless of the user's current page, capped
     * server-side by {@code ExcelExportService.MAX_ROWS = 50_000}.
     *
     * <p>Validation + tenant-scoping rules are identical to
     * {@link #list}. The helper indexes added in V71
     * ({@code IX_*_BIZ_REG_DTM}, {@code IX_*_BIZ_ERP_TYPE}) keep the
     * typical 30-day window cheap; a pathological "all-time" filter
     * would hit {@code WBE2011}.
     */
    @Transactional(readOnly = true)
    public List<ErpTrnsHistoryRow> listAll(
            String fromDate,
            String toDate,
            String erpType,
            String trnsTypeCd,
            String keyword,
            String sortField,
            String sortDirection) {

        String fromDt = validateDate("from_date", fromDate);
        String toDt   = validateDate("to_date",   toDate);
        validatePeriod(fromDt, toDt);
        String erpTypeFilter  = validateErpType(erpType);
        String trnsTypeFilter = validateTrnsTypeCd(trnsTypeCd);
        String keywordPattern = buildKeywordPattern(keyword);

        Sort sort = buildSort(
                ErpTrnsHistorySortField.parse(sortField),
                SortDirection.parse(sortDirection));

        String bizKey = TenantContext.currentBizKey();
        Page<ErpTrnsHistoryProjection> result = repository.search(
                bizKey, fromDt, toDt, erpTypeFilter, trnsTypeFilter, keywordPattern,
                Pageable.unpaged(sort));

        return result.getContent().stream()
                .map(ErpTrnsHistoryService::toRow)
                .toList();
    }

    /* =================================================================
     *  Download
     * ================================================================= */

    /**
     * Resolve the physical file behind a row's {@code seqNo}. Verifies
     * tenant ownership (row's {@code biz_key} must match
     * {@link TenantContext#currentBizKey()}), then composes the path
     * through {@link ErpTrnsHistoryFileResolver} and asserts the file
     * is readable.
     *
     * <ul>
     *   <li>Row not found <em>or</em> not in tenant → {@code WBE2014}
     *       (single envelope for both cases — never leak the existence
     *       of another tenant's row).</li>
     *   <li>{@code file_size = 0} (batch row with no physical file) or
     *       file missing / unreadable on storage root → {@code WBE2015}.</li>
     * </ul>
     */
    @Transactional(readOnly = true)
    public DownloadResource resolveDownload(long seqNo) {
        String bizKey = TenantContext.currentBizKey();
        ErpTrnsHstr row = repository.findById(new ErpTrnsHstrId(bizKey, seqNo))
                .orElseThrow(() -> new BusinessException("WBE2014"));

        // file_size = 0 means a batch row with no physical file behind
        // it — the JEX frontend already hides the download link in
        // this case (comm_0219_001.ts:330). Refuse the download cleanly
        // rather than producing a 0-byte stream.
        if (row.getFileSize() == null || row.getFileSize() == 0L) {
            throw new BusinessException("WBE2015");
        }

        Path path = fileResolver.resolve(row.getBizKey(), row.getSaveFileNm());
        if (!Files.isReadable(path) || !Files.isRegularFile(path)) {
            throw new BusinessException("WBE2015");
        }
        return new DownloadResource(path, row.getOrgFileNm(), row.getSaveFileNm(), row.getFileSize());
    }

    /** Controller-only carrier for the resolved file. Service exposes
     *  the trio (physical {@link Path}, original filename, saved
     *  filename) so the controller can compose RFC 5987 Content-Disposition
     *  without re-loading the row. */
    public record DownloadResource(Path path, String orgFileNm, String saveFileNm, long fileSize) { }

    /* =================================================================
     *  Validation
     * ================================================================= */

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

    /** {@code erp_type} optional; if present must be one of the three
     *  directions. Returns {@code null} for blank/missing so the JPQL
     *  {@code IS NULL OR} guard skips the predicate. */
    private static String validateErpType(String erpType) {
        if (erpType == null) return null;
        String trimmed = erpType.trim();
        if (trimmed.isEmpty()) return null;
        String upper = trimmed.toUpperCase(Locale.ROOT);
        if (!ALLOWED_ERP_TYPES.contains(upper)) {
            throw new BusinessException(
                    "WBE_VALIDATION",
                    "erp_type: must be one of W, E, D");
        }
        return upper;
    }

    /** {@code trns_type_cd} optional; if present must be 2-digit numeric
     *  within {@code "01".."17"} (the legacy {@code ErpTypeCd} enum
     *  range). Returns {@code null} for blank/missing. */
    private static String validateTrnsTypeCd(String trnsTypeCd) {
        if (trnsTypeCd == null) return null;
        String trimmed = trnsTypeCd.trim();
        if (trimmed.isEmpty()) return null;
        if (trimmed.length() != 2 || !trimmed.chars().allMatch(Character::isDigit)) {
            throw new BusinessException(
                    "WBE_VALIDATION",
                    "trns_type_cd: must be a 2-digit numeric code");
        }
        int code = Integer.parseInt(trimmed);
        if (code < 1 || code > 17) {
            throw new BusinessException(
                    "WBE_VALIDATION",
                    "trns_type_cd: must be in 01..17");
        }
        return trimmed;
    }

    /* =================================================================
     *  Keyword + sort plumbing
     * ================================================================= */

    /**
     * Lower-case + escape LIKE meta-chars + wrap with {@code %…%}. Uses
     * {@code !} as the escape character so the JPQL pattern stays
     * portable (same convention as
     * {@code FirmBankingHistoryService.buildKeywordPattern}). Returns
     * {@code null} when blank so the JPQL {@code IS NULL OR} guard
     * skips the predicate.
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
    private static Sort buildSort(ErpTrnsHistorySortField field, SortDirection direction) {
        if (field == null || direction == null) {
            return DEFAULT_SORT;
        }
        Sort.Order primary = direction == SortDirection.ASC
                ? Sort.Order.asc(field.entityProperty())
                : Sort.Order.desc(field.entityProperty());
        // Tiebreaker preserves stable ordering across pages even when
        // the primary sort key has duplicates.
        return Sort.by(primary).and(Sort.by(Sort.Order.desc("seqNo")));
    }

    /* =================================================================
     *  Row mapping — projection → wire DTO with resolved i18n keys
     * ================================================================= */

    private static ErpTrnsHistoryRow toRow(ErpTrnsHistoryProjection p) {
        return new ErpTrnsHistoryRow(
                p.seqNo(),
                p.orgFileNm(),
                p.saveFileNm(),
                p.fileSize() == null ? 0L : p.fileSize(),
                p.erpType(),
                resolveErpTypeKey(p.erpType()),
                p.trnsTypeCd(),
                resolveTrnsTypeKey(p.trnsTypeCd()),
                p.regDtm(),
                p.regId(),
                p.userNm());
    }

    /** Map {@code W} / {@code E} / {@code D} → frontend i18n key.
     *  Returns {@code null} for anything outside the known set so the
     *  frontend renders the raw code instead of a broken lookup. */
    private static String resolveErpTypeKey(String erpType) {
        if (erpType == null) return null;
        return switch (erpType) {
            case "W" -> "settings.erp_transaction_history.direction.wecms_to_erp";
            case "E" -> "settings.erp_transaction_history.direction.erp_to_wecms";
            case "D" -> "settings.erp_transaction_history.direction.deleted";
            default  -> null;
        };
    }

    /** Map {@code "01".."17"} → frontend i18n key. Outside the known
     *  range → {@code null}. Mirrors legacy
     *  {@code wabooks.enums.ErpTypeCd.getLabel()} which returns "" for
     *  unmapped codes; the refactor returns {@code null} so the frontend
     *  can fall through to the raw code. */
    private static String resolveTrnsTypeKey(String trnsTypeCd) {
        if (trnsTypeCd == null || trnsTypeCd.length() != 2) return null;
        // Defensive — guard against any non-numeric code that slipped past
        // validation (e.g. legacy rows imported from JEX). Range check
        // matches the enum's 17-value set.
        try {
            int n = Integer.parseInt(trnsTypeCd);
            if (n < 1 || n > 17) return null;
        } catch (NumberFormatException ex) {
            return null;
        }
        return "settings.erp_transaction_history.type." + trnsTypeCd;
    }
}
