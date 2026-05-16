package com.kosign.wecms.domain.setting.vasaccount.service;

import com.kosign.wecms.common.auditlog.service.AuditLogContext;
import com.kosign.wecms.common.auditlog.service.AuditLogService;
import com.kosign.wecms.common.enums.AStts;
import com.kosign.wecms.common.enums.VasAcctType;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.common.web.SortDirection;
import com.kosign.wecms.domain.setting.vasaccount.dto.VasAccountCreateRequest;
import com.kosign.wecms.domain.setting.vasaccount.dto.VasAccountListResponse;
import com.kosign.wecms.domain.setting.vasaccount.dto.VasAccountLookupResponse;
import com.kosign.wecms.domain.setting.vasaccount.dto.VasAccountLookupRow;
import com.kosign.wecms.domain.setting.vasaccount.dto.VasAccountRow;
import com.kosign.wecms.domain.setting.vasaccount.dto.VasAccountUpdateRequest;
import com.kosign.wecms.domain.setting.vasaccount.entity.BusiVasAcctInfm;
import com.kosign.wecms.domain.setting.vasaccount.repository.BusiVasAcctInfmRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Settings &gt; VAS Account — CRUD service. Mirrors the legacy
 * {@code comm_0217_001} family from {@code WECMS DEV/.../wabooks/}:
 * <ul>
 *   <li>{@link #list} → {@code comm_0217_001_r001} (legacy
 *       {@code IDO.BUSI_VAS_ACCT_INFM_R001}).</li>
 *   <li>{@link #detail} → {@code comm_0217_004_r001} row fetch.</li>
 *   <li>{@link #create} → {@code comm_0217_003_c001}.</li>
 *   <li>{@link #update} → {@code comm_0217_004_u001}.</li>
 *   <li>{@link #softDelete} → {@code comm_0217_001_d001} (sets
 *       {@code DEL_YN='Y'} — the legacy IDO defines a physical DELETE but
 *       it is never invoked in production).</li>
 * </ul>
 *
 * <p>Tenant scoping: every method reads {@link TenantContext#currentBizKey()}
 * and applies it as the first predicate. {@code biz_key} never appears on
 * the wire.
 *
 * <p>Audit trail: every write emits exactly one audit event via the shared
 * {@link AuditLogService} with {@code wsvc_id = "vas-account"} (slug, not
 * the legacy WSVC id — 2026-05-09 rule).
 *
 * <p>Surrogate keys: {@link VasAcctSeqNoGenerator} (Java-side, MAX+1)
 * replaces the legacy {@code wb_get_next_val} branch — refactor preference
 * per {@code wecms-api-main/CLAUDE.md} "DB Function / Legacy Function
 * Policy".
 *
 * <p>Refactor enhancements vs. JEX:
 * <ul>
 *   <li>{@code page} / {@code size} are zero-based + clamped to
 *       {@link #MAX_PAGE_SIZE}; legacy was a single-shot list.</li>
 *   <li>{@code sort_field} validated against
 *       {@link VasAccountSortField} whitelist; unknown → default sort.</li>
 *   <li>{@code vas_acct_type_key} / {@code stts_key} server-resolved i18n
 *       keys per {@code wecms-api-main/CLAUDE.md} §"Domain enums".</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VasAccountService {

    /** Largest page size honoured. Mirrors the cap used by every refactor
     *  list endpoint. Excess values are clamped, not rejected. */
    public static final int MAX_PAGE_SIZE = 100;

    /** Default page size — small Settings table, matches Settings-module
     *  conventions. */
    public static final int DEFAULT_PAGE_SIZE = 20;

    /** Default sort — code-ascending, matches the natural tenant ordering.
     *  Applied when the caller sends no (or unknown) {@code sort_field}. */
    private static final Sort DEFAULT_SORT =
            Sort.by(Sort.Order.asc("vasAcctCd"), Sort.Order.asc("vasAcctSeqNo"));

    /** Refactor route slug used as the audit-log {@code wsvc_id}
     *  discriminator — 2026-05-09 rule (workspace
     *  {@code CLAUDE.md} §"Refactor identifier naming"). The legacy WSVC
     *  ids ({@code comm_0217_001} / {@code _002} / {@code _003} / {@code _004})
     *  appear only in JEX file:line citations, never as a Java identifier
     *  or DB column value. */
    static final String AUDIT_WSVC_ID = "vas-account";

    /** Legacy {@code REF_NO} value referencing the source table. */
    private static final String REF_NO_BUSI_VAS_ACCT_INFM = "BUSI_VAS_ACCT_INFM";

    /** Audit message keys — seeded across all 4 locale files. */
    private static final String AUDIT_ACTIVITY_KEY        = "audit.vas_account.activity";
    private static final String AUDIT_CREATE_DETAIL_KEY   = "audit.vas_account.create.detail";
    private static final String AUDIT_UPDATE_DETAIL_KEY   = "audit.vas_account.update.detail";
    private static final String AUDIT_DELETE_DETAIL_KEY   = "audit.vas_account.delete.detail";

    /** Legacy {@code yyyyMMddHHmmss} timestamp shape — what
     *  {@code REG_DTM} / {@code MOD_DTM} store. Server time at write, UTC. */
    private static final DateTimeFormatter DTM14_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private static final String DEL_YN_LIVE    = "N";
    private static final String DEL_YN_DELETED = "Y";

    /** Default {@code vas_acct_type} when the wire omits it — the
     *  refactor UI no longer surfaces a Type selector (PO call,
     *  2026-05-11). Mirrors legacy {@code BusiVasAcctInfmDAO.create()}
     *  which defaulted unmapped types to DUAL via
     *  {@code VasAcctType.fromValue(null)} fallback. */
    private static final String DEFAULT_VAS_ACCT_TYPE = "0";

    private final BusiVasAcctInfmRepository repository;
    private final VasAcctSeqNoGenerator     seqGen;
    private final AuditLogService           auditLogService;

    /* =================================================================
     *  List
     * ================================================================= */

    /**
     * Filtered + paginated list. Two filters:
     * <ul>
     *   <li>{@code searchWd} — substring match against
     *       {@code vas_acct_cd} or {@code vas_acct_nm} (ILIKE).</li>
     *   <li>{@code statuses} — single-value filter (the legacy two-
     *       checkbox UI sends both A and I when both are ticked; the
     *       service interprets a {@code null}/empty list as "no filter"
     *       and a single value as "exact match"). When the wire passes
     *       multiple statuses, the service falls back to "no filter"
     *       because the only legitimate combination is A+I = no filter
     *       anyway. Mirrors the legacy
     *       {@code comm_0217_001_r001_act.jsp:54-64} treatment.</li>
     * </ul>
     *
     * @param searchWd      optional substring
     * @param statuses      optional list of {@code A} / {@code I}
     * @param sortField     optional whitelisted FIELD_ID
     * @param sortDirection optional ASC / DESC
     * @param page          zero-based; defaults to {@code 0} when negative
     * @param size          rows per page; defaults to {@link #DEFAULT_PAGE_SIZE}
     *                      when {@code <= 0}, clamped to {@link #MAX_PAGE_SIZE}
     */
    @Transactional(readOnly = true)
    public VasAccountListResponse list(
            String searchWd,
            List<String> statuses,
            String sortField,
            String sortDirection,
            int page,
            int size) {

        String bizKey = requireBizKey();
        String keywordPattern = buildKeywordPattern(searchWd);
        String statusFilter   = resolveStatusFilter(statuses);

        int safeSize = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        int safePage = Math.max(0, page);
        Sort sort = buildSort(
                VasAccountSortField.parse(sortField),
                SortDirection.parse(sortDirection));
        Pageable pageable = PageRequest.of(safePage, safeSize, sort);

        Page<BusiVasAcctInfm> result =
                repository.search(bizKey, statusFilter, keywordPattern, pageable);

        List<VasAccountRow> rows = result.getContent().stream()
                .map(VasAccountService::toRow)
                .toList();

        return new VasAccountListResponse(
                rows,
                result.getTotalElements(),
                result.getNumber(),
                result.getSize(),
                result.getTotalPages());
    }

    /**
     * Return every live row for the current tenant matching the filter
     * &amp; sort — no pagination. Consumed by the Excel export pipeline so
     * the spreadsheet captures the full filtered set regardless of which
     * page the user is currently viewing. Mirrors the
     * {@code AccountService.list(filter, sort..., Pageable.unpaged())}
     * precedent on the Banking side.
     *
     * <p>The shared {@link com.kosign.wecms.common.export.ExcelExportService#MAX_ROWS}
     * (50 000) cap is the upper bound; VAS Account row counts are
     * tenant-small (≤ 200 rows / tenant on DEV) so the cap is never
     * exercised in practice. Uses {@code PageRequest.of(0,
     * Integer.MAX_VALUE, sort)} as the "give me everything sorted" idiom
     * since {@code Pageable.unpaged(Sort)} requires a newer Spring Data
     * version than the current toolchain.
     */
    @Transactional(readOnly = true)
    public List<VasAccountRow> listAllForExport(
            String searchWd,
            List<String> statuses,
            String sortField,
            String sortDirection) {

        String bizKey = requireBizKey();
        String keywordPattern = buildKeywordPattern(searchWd);
        String statusFilter   = resolveStatusFilter(statuses);
        Sort sort = buildSort(
                VasAccountSortField.parse(sortField),
                SortDirection.parse(sortDirection));
        Pageable pageable = PageRequest.of(0, Integer.MAX_VALUE, sort);

        return repository.search(bizKey, statusFilter, keywordPattern, pageable)
                .getContent().stream()
                .map(VasAccountService::toRow)
                .toList();
    }

    /* =================================================================
     *  Lookup — active+live picker for the Edit Bank Account dialog.
     *  Mirrors legacy `comm_1002_001` (VAS Account TextBox / typeahead).
     * ================================================================= */

    /**
     * Returns the active + live VAS accounts for the current tenant,
     * filtered by an optional substring. Used by the Edit Bank Account
     * dialog's VAS Account picker — never call this for the management
     * panel, which uses {@link #list} so it can paginate inactive rows.
     *
     * <p>Returns at most {@link #LOOKUP_MAX_ROWS} rows (cheap UI guard —
     * the typeahead is not designed to render 10k items).
     */
    @Transactional(readOnly = true)
    public VasAccountLookupResponse lookup(String searchWd) {
        String bizKey = requireBizKey();
        String keywordPattern = buildKeywordPattern(searchWd);
        List<VasAccountLookupRow> items = repository.findLookup(bizKey, keywordPattern)
                .stream()
                .limit(LOOKUP_MAX_ROWS)
                .map(v -> new VasAccountLookupRow(
                        v.getVasAcctSeqNo(),
                        v.getVasAcctCd(),
                        v.getVasAcctNm()))
                .toList();
        return new VasAccountLookupResponse(items);
    }

    /** Hard cap on lookup rows — the picker is a typeahead, not a
     *  full list. Matches the {@code MAX_PAGE_SIZE} on the list path. */
    private static final int LOOKUP_MAX_ROWS = 500;

    /* =================================================================
     *  Detail
     * ================================================================= */

    @Transactional(readOnly = true)
    public VasAccountRow detail(Long vasAcctSeqNo) {
        if (vasAcctSeqNo == null || vasAcctSeqNo <= 0) {
            throw new BusinessException("WBE_VALIDATION", "vas_acct_seq_no: must be a positive number");
        }
        String bizKey = requireBizKey();
        BusiVasAcctInfm row = repository
                .findByBizKeyAndVasAcctSeqNoAndDelYn(bizKey, vasAcctSeqNo, DEL_YN_LIVE)
                .orElseThrow(() -> {
                    log.warn("vas-account detail not found bizKey={} vasAcctSeqNo={}", bizKey, vasAcctSeqNo);
                    return new BusinessException("WBE2040");
                });
        return toRow(row);
    }

    /* =================================================================
     *  Create
     * ================================================================= */

    @Transactional
    public VasAccountRow create(VasAccountCreateRequest req) {
        String bizKey  = requireBizKey();
        String regId   = requireUserId();
        String nowDtm  = OffsetDateTime.now(ZoneOffset.UTC).format(DTM14_FORMAT);

        if (repository.existsByBizKeyAndVasAcctCdAndDelYn(bizKey, req.vasAcctCd(), DEL_YN_LIVE)) {
            log.warn("vas-account create rejected duplicate vasAcctCd={} bizKey={}",
                    req.vasAcctCd(), bizKey);
            throw new BusinessException("WBE0003", "VAS_ACCT_CD");
        }

        long nextSeqNo = seqGen.next(bizKey);

        // UI no longer surfaces Type / English Name (PO call 2026-05-11).
        // Wire makes both optional; Create defaults Type to "0" (Dual),
        // leaves English Name null when the caller doesn't supply one.
        String resolvedType = isBlank(req.vasAcctType())
                ? DEFAULT_VAS_ACCT_TYPE
                : req.vasAcctType();

        BusiVasAcctInfm entity = new BusiVasAcctInfm();
        entity.setBizKey(bizKey);
        entity.setVasAcctSeqNo(nextSeqNo);
        entity.setVasAcctCd(req.vasAcctCd());
        entity.setVasAcctNm(req.vasAcctNm());
        entity.setVasAcctEnm(req.vasAcctEnm());
        entity.setVasAcctType(resolvedType);
        entity.setStts(req.stts());
        entity.setCont(req.cont());
        entity.setDelYn(DEL_YN_LIVE);
        entity.setRegId(regId);
        entity.setRegDtm(nowDtm);
        entity.setModId(regId);
        entity.setModDtm(nowDtm);
        BusiVasAcctInfm saved = repository.save(entity);

        appendAudit(AUDIT_CREATE_DETAIL_KEY, saved.getVasAcctSeqNo(), saved.getVasAcctCd());
        log.info("vas-account create vasAcctSeqNo={} vasAcctCd={} bizKey={}",
                saved.getVasAcctSeqNo(), saved.getVasAcctCd(), bizKey);
        return toRow(saved);
    }

    /* =================================================================
     *  Update
     * ================================================================= */

    @Transactional
    public VasAccountRow update(Long vasAcctSeqNo, VasAccountUpdateRequest req) {
        if (vasAcctSeqNo == null || vasAcctSeqNo <= 0) {
            throw new BusinessException("WBE_VALIDATION", "vas_acct_seq_no: must be a positive number");
        }
        String bizKey = requireBizKey();
        String modId  = requireUserId();
        String nowDtm = OffsetDateTime.now(ZoneOffset.UTC).format(DTM14_FORMAT);

        BusiVasAcctInfm entity = repository
                .findByBizKeyAndVasAcctSeqNoAndDelYn(bizKey, vasAcctSeqNo, DEL_YN_LIVE)
                .orElseThrow(() -> {
                    log.warn("vas-account update not found bizKey={} vasAcctSeqNo={}", bizKey, vasAcctSeqNo);
                    return new BusinessException("WBE2040");
                });

        // Duplicate-check only when the code actually changed (mirrors
        // comm_0217_004_u001_act.jsp:117-141 — the "same code → no
        // re-check" branch).
        if (!entity.getVasAcctCd().equals(req.vasAcctCd())
                && repository.existsByBizKeyAndVasAcctCdAndDelYn(bizKey, req.vasAcctCd(), DEL_YN_LIVE)) {
            log.warn("vas-account update rejected duplicate vasAcctCd={} bizKey={} (renaming from {})",
                    req.vasAcctCd(), bizKey, entity.getVasAcctCd());
            throw new BusinessException("WBE0003", "VAS_ACCT_CD");
        }

        // UI no longer surfaces Type / English Name / Notes (Type +
        // English Name retired by the PO call 2026-05-11; Notes by the
        // subsequent UI cleanup pass). Wire makes all three optional on
        // Update; preserve the existing entity value when the caller
        // omits them so a future tool that sets any of them can't be
        // silently wiped by an Edit dialog submit. The `null` / blank
        // guard treats either as "don't touch" — explicit-clear is
        // not currently a UI concern.
        entity.setVasAcctCd(req.vasAcctCd());
        entity.setVasAcctNm(req.vasAcctNm());
        if (!isBlank(req.vasAcctEnm())) {
            entity.setVasAcctEnm(req.vasAcctEnm());
        }
        if (!isBlank(req.vasAcctType())) {
            entity.setVasAcctType(req.vasAcctType());
        }
        entity.setStts(req.stts());
        if (!isBlank(req.cont())) {
            entity.setCont(req.cont());
        }
        entity.setModId(modId);
        entity.setModDtm(nowDtm);

        appendAudit(AUDIT_UPDATE_DETAIL_KEY, entity.getVasAcctSeqNo(), entity.getVasAcctCd());
        log.info("vas-account update vasAcctSeqNo={} vasAcctCd={} bizKey={}",
                entity.getVasAcctSeqNo(), entity.getVasAcctCd(), bizKey);
        return toRow(entity);
    }

    /* =================================================================
     *  Soft-delete
     * ================================================================= */

    @Transactional
    public void softDelete(Long vasAcctSeqNo) {
        if (vasAcctSeqNo == null || vasAcctSeqNo <= 0) {
            throw new BusinessException("WBE_VALIDATION", "vas_acct_seq_no: must be a positive number");
        }
        String bizKey = requireBizKey();
        String modId  = requireUserId();
        String nowDtm = OffsetDateTime.now(ZoneOffset.UTC).format(DTM14_FORMAT);

        BusiVasAcctInfm entity = repository
                .findByBizKeyAndVasAcctSeqNoAndDelYn(bizKey, vasAcctSeqNo, DEL_YN_LIVE)
                .orElseThrow(() -> {
                    log.warn("vas-account delete not found bizKey={} vasAcctSeqNo={}", bizKey, vasAcctSeqNo);
                    return new BusinessException("WBE2040");
                });

        String vasAcctCd = entity.getVasAcctCd();
        entity.setDelYn(DEL_YN_DELETED);
        entity.setModId(modId);
        entity.setModDtm(nowDtm);

        appendAudit(AUDIT_DELETE_DETAIL_KEY, entity.getVasAcctSeqNo(), vasAcctCd);
        log.info("vas-account soft-delete vasAcctSeqNo={} vasAcctCd={} bizKey={}",
                entity.getVasAcctSeqNo(), vasAcctCd, bizKey);
    }

    /* =================================================================
     *  Audit, validation, mapping
     * ================================================================= */

    private void appendAudit(String detailKey, Long vasAcctSeqNo, String vasAcctCd) {
        auditLogService.append(AuditLogContext.builder()
                .wsvcId(AUDIT_WSVC_ID)
                .refNo(REF_NO_BUSI_VAS_ACCT_INFM)
                .refNo1(String.valueOf(vasAcctSeqNo))
                .refNo2(vasAcctCd)
                .activityKey(AUDIT_ACTIVITY_KEY)
                .detailKey(detailKey)
                .build());
    }

    /** Read tenant from JWT-populated TenantContext. Returns the value or
     *  throws — never returns null. */
    private static String requireBizKey() {
        String bizKey = TenantContext.currentBizKey();
        if (bizKey == null || bizKey.isBlank()) {
            // Should never happen — SecurityConfig requires auth for every
            // /api/v1/* path and JwtAuthenticationFilter populates the
            // context. A null here is a wiring bug, not a request fault.
            throw new IllegalStateException("TenantContext is not populated");
        }
        return bizKey;
    }

    /** Treat null and whitespace-only as "absent" — wire-layer guard
     *  used by Create (default) and Update (preserve existing). */
    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String requireUserId() {
        String userId = TenantContext.currentUserId();
        if (userId == null || userId.isBlank()) {
            throw new IllegalStateException("TenantContext userId is not populated");
        }
        return userId;
    }

    /**
     * The legacy view sends a single status per request (one checkbox).
     * The refactor wire accepts a {@code stts} repeatable query param so
     * callers can pass both. Logic:
     * <ul>
     *   <li>{@code null} or empty list → no filter (returns both).</li>
     *   <li>Single value → exact-match filter.</li>
     *   <li>Two-or-more values → no filter (the only legitimate combo is
     *       A+I = "show everything").</li>
     * </ul>
     */
    private static String resolveStatusFilter(List<String> statuses) {
        if (statuses == null || statuses.isEmpty()) return null;
        if (statuses.size() > 1) return null;
        String single = statuses.get(0);
        if (single == null) return null;
        String trimmed = single.trim().toUpperCase(Locale.ROOT);
        if (trimmed.isEmpty()) return null;
        if (AStts.fromCode(trimmed).isEmpty()) {
            throw new BusinessException("WBE_VALIDATION", "stts: must be A or I");
        }
        return trimmed;
    }

    /** Build a LIKE pattern from the raw search keyword. Lowercase + escape
     *  LIKE meta-chars (using {@code !} as the escape) + wrap with
     *  {@code %…%}. Returns {@code null} for blank input so the JPQL
     *  {@code IS NULL OR} guard skips the predicate. */
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

    private static Sort buildSort(VasAccountSortField field, SortDirection direction) {
        if (field == null || direction == null) {
            return DEFAULT_SORT;
        }
        Sort.Order primary = direction == SortDirection.ASC
                ? Sort.Order.asc(field.entityProperty())
                : Sort.Order.desc(field.entityProperty());
        // Stable tiebreaker — keeps pagination deterministic when the
        // primary sort key has duplicates.
        return Sort.by(primary).and(Sort.by(Sort.Order.asc("vasAcctSeqNo")));
    }

    /** Entity → wire row. Resolves the type/status i18n keys via the
     *  shared common/enums (fall through to {@code null} for unknown
     *  codes so the frontend can render the raw value). */
    private static VasAccountRow toRow(BusiVasAcctInfm e) {
        String typeKey = VasAcctType.fromCode(e.getVasAcctType())
                .map(VasAcctType::i18nKey).orElse(null);
        String sttsKey = AStts.fromCode(e.getStts())
                .map(AStts::i18nKey).orElse(null);
        return new VasAccountRow(
                String.valueOf(e.getVasAcctSeqNo()),
                e.getVasAcctCd(),
                e.getVasAcctNm(),
                e.getVasAcctEnm(),
                e.getVasAcctType(),
                typeKey,
                e.getStts(),
                sttsKey,
                e.getCont(),
                e.getModId(),
                e.getModDtm());
    }
}
