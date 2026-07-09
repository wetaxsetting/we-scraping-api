package com.kosign.wecms.domain.einvoice.service;

import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.common.web.SortDirection;
import com.kosign.wecms.domain.einvoice.dto.EInvoiceListResponse;
import com.kosign.wecms.domain.einvoice.dto.EInvoiceListResponse.CurrencyTotal;
import com.kosign.wecms.domain.einvoice.dto.EInvoiceRow;
import com.kosign.wecms.domain.einvoice.entity.BusaDocMstr;
import com.kosign.wecms.domain.einvoice.repository.BusaDocMstrRepository;
import com.kosign.wecms.domain.einvoice.repository.EInvoiceCcyAggregate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;

/**
 * Sales / Bill e-Invoice list service. Mirrors the legacy
 * {@code einv_0001_001_r001} read path over {@code BUSA_DOC_MSTR}.
 *
 * <p><b>Phase 2 implementation rules (locked)</b> — see
 * {@code docs/refactor-wecms/planning/addon/sales-e-invoices-plan.md}:
 * <ul>
 *   <li><b>IR-1</b> {@code DOC_TYPE} mapping — wire {@code SALES} → {@code 'I'},
 *       wire {@code EXPENSE} / {@code BILL} → {@code 'B'}. Anything else
 *       (including legacy enum constants {@code BusaDocType.EXPENSE='E'}
 *       and {@code BusaDocType.PAYROLLBOOK='S'}) is rejected with
 *       {@code WBE2002} — the legacy listing service rejects them too.</li>
 *   <li><b>IR-2</b> Always-applied baseline filters — {@code BIZ_KEY},
 *       {@code DOC_TYPE}, {@code PROV_STTS NOT IN ('VD','EE')},
 *       {@code HAND_REG_YN <> 'Y'}, {@code ISSU_DT BETWEEN ?}.
 *       Live in the repository's {@code findFiltered} +
 *       {@code aggregateByCurrency} so the WHERE chain stays
 *       byte-for-byte identical between the paged list query and the
 *       unpaged totals query (rule IR-6).</li>
 *   <li><b>IR-3</b> Date range required — both {@code from} and {@code to}
 *       are mandatory; {@code YYYYMMDD} format; {@code from ≤ to}.
 *       Format validation runs at the controller ({@code @Pattern});
 *       calendar-validity + ordering run here.</li>
 *   <li><b>IR-4</b> {@code EINV_INVOICESTATUS} — the wire row still
 *       returns the raw stored value (frontend regex handles display +
 *       badge tint; no port of {@code wb_get_invoice_status}). The
 *       <b>status filter is now server-side</b> (2026-06-30): an exact
 *       {@code WHERE EINV_INVOICESTATUS = :status} match, mirroring legacy
 *       {@code einv_0001_001_r001_act.jsp:88-92}. Stored values are always
 *       the GDT Vietnamese strings (verified on JEX DEV: "Hóa đơn mới" /
 *       "Hóa đơn thay thế" / "Hóa đơn điều chỉnh" / "Hóa đơn đã bị thay thế"
 *       / "Hóa đơn đã bị điều chỉnh" / "Hóa đơn đã bị hủy"), so the frontend
 *       sends the canonical Vietnamese value per dropdown option (see
 *       {@code wecms-web/lib/api/einvoiceStatus.ts}). Applied to both the
 *       paged query AND the totals aggregate so pagination + currency
 *       totals stay status-aware (rule IR-6).</li>
 *   <li><b>IR-5</b> {@code keyword} search — fixed 5-column subset
 *       ({@code DOC_ID}, {@code TRCO_CMNM}, {@code TRCO_TAX_ID},
 *       {@code EINV_INVOICESYMBOL}, {@code DISP_DOC_ID}). Plain
 *       {@code LOWER(...)} {@code LIKE} with {@code !}-escape; no
 *       {@code wb_get_translate_text} accent fold. Full View-Setting
 *       walk deferred.</li>
 *   <li><b>IR-6</b> Multi-currency totals — computed by a sibling
 *       unpaged aggregation query. Stays stable across pagination.</li>
 *   <li><b>IR-7</b> {@code INV_TYPE} cleanup — dropped from the wire row.</li>
 * </ul>
 *
 * <p>Default ORDER BY mirrors the legacy {@code einv_0001_001_r001}
 * listing: {@code ISSU_DT DESC, REG_DTM DESC} (newest first). The
 * user's primary sort (when a valid {@code sortField} is passed) is
 * always followed by {@code REG_DTM DESC} as a deterministic
 * tiebreaker (rule from "Common table sort" in
 * {@code wecms-api/CLAUDE.md}).
 */
@Service
@RequiredArgsConstructor
public class EInvoiceService {

    private static final String CODE_BAD_REQUEST = "WBE2002";
    private static final String CODE_VALIDATION  = "WBE_VALIDATION";

    /** Largest {@code size} the controller will honour. Matches the
     *  frontend {@code PAGE_SIZE_OPTIONS} maximum. Requests exceeding
     *  it are clamped down rather than rejected so an accidental large
     *  value still returns a usable response. */
    public static final int MAX_PAGE_SIZE = 100;

    /** Default page size when the caller omits {@code size}. Matches
     *  the frontend {@code PAGE_SIZE_OPTIONS[0] = 10}. */
    public static final int DEFAULT_PAGE_SIZE = 10;

    /** Legacy {@code FROM_DT} / {@code TO_DT} format — see
     *  {@code wabooks.helper.ValidationHelper.validateDate8} called
     *  from {@code einv_0001_001_r001_act.jsp.validateInput}. */
    private static final DateTimeFormatter LEGACY_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    /** Default ORDER BY — legacy parity with {@code einv_0001_001_r001}.
     *  PostgreSQL sorts NULLs last on DESC, so unset {@code REG_DTM}
     *  values fall through to {@code ISSU_DT}'s order. */
    private static final Sort DEFAULT_SORT =
            Sort.by(Sort.Order.desc("issuDt"), Sort.Order.desc("regDtm"));

    private final BusaDocMstrRepository repo;

    /* ------------------------------------------------------------------
     *  Public list — single entry point. Controller passes raw wire
     *  primitives (strings + ints); the service does all parsing,
     *  clamping, and validation.
     * ------------------------------------------------------------------ */

    @Transactional(readOnly = true)
    public EInvoiceListResponse list(
            String type,
            String fromDt,
            String toDt,
            String keyword,
            String status,
            int page,
            int size,
            String sortField,
            String sortDirection) {
        int safeSize = clampSize(size);
        int safePage = Math.max(0, page);
        Sort sort = buildSort(EInvoiceSortField.parse(sortField), SortDirection.parse(sortDirection));
        Pageable pageable = PageRequest.of(safePage, safeSize, sort);
        return list(type, fromDt, toDt, keyword, status, pageable, sortField, sortDirection);
    }

    /**
     * {@link Pageable}-accepting overload. The 8-arg variant funnels here
     * after clamping {@code size} / {@code page} and building the
     * {@link Sort}. The Excel-export path calls this directly with
     * {@link Pageable#unpaged()} so the spreadsheet contains every row in
     * the filtered result regardless of which page the user is currently
     * viewing (rule IR-6).
     *
     * <p>When the caller passes an already-sorted {@code Pageable}, that
     * order wins. When the {@code Pageable}'s {@link Pageable#getSort()}
     * is {@link Sort#unsorted()} (the default for {@code Pageable.unpaged()}
     * built without a {@link Sort}), the user-driven {@code sortField} /
     * {@code sortDirection} are resolved here and woven in — the export
     * pipeline therefore honours the same sort the on-screen list
     * currently shows.
     */
    @Transactional(readOnly = true)
    public EInvoiceListResponse list(
            String type,
            String fromDt,
            String toDt,
            String keyword,
            String status,
            Pageable pageable,
            String sortField,
            String sortDirection) {

        final String bizKey  = TenantContext.currentBizKey();
        final String docType = resolveDocType(type);
        validateDateRange(fromDt, toDt);

        Pageable effective = pageable.getSort().isSorted()
                ? pageable
                : applyDefaultSortIfNeeded(pageable, sortField, sortDirection);

        String keywordPattern = buildKeywordPattern(keyword);
        String statusFilter   = blankToNull(status);

        Page<BusaDocMstr> result = repo.findFiltered(bizKey, docType, fromDt, toDt, statusFilter, keywordPattern, effective);
        List<EInvoiceCcyAggregate> aggregates = repo.aggregateByCurrency(bizKey, docType, fromDt, toDt, statusFilter, keywordPattern);

        List<EInvoiceRow> rows = result.getContent().stream().map(this::toRow).toList();
        List<CurrencyTotal> totals = aggregates.stream()
                .map(a -> new CurrencyTotal(
                        a.ccyCd() == null ? "" : a.ccyCd().trim(),
                        Math.toIntExact(a.count()),
                        wireAmount(a.totalAmount()),
                        wireAmount(a.taxAmount()),
                        wireAmount(a.pretaxAmount())))
                .toList();

        return new EInvoiceListResponse(
                rows,
                result.getTotalElements(),
                result.getTotalPages(),
                result.getNumber(),
                result.getSize(),
                totals);
    }

    /**
     * Full-entity variant for the "Download Excel with Details" export
     * (legacy {@code einv_0001_001_r002_excel} / IDO
     * {@code BUSA_DOC_MSTR_R024}). Reuses the exact same filter chain as
     * {@link #list} (IR-1 doc-type, IR-2 baseline {@code PROV_STTS}/{@code
     * HAND_REG_YN}, IR-3 required date range, IR-5 keyword) via
     * {@code repo.findFiltered}, but returns the full {@link BusaDocMstr}
     * entities instead of the slim {@link EInvoiceRow} projection so the
     * detail export can read the 40+ master-enrichment columns the list
     * row doesn't carry. Unpaged (IR-6) — every matching invoice.
     */
    @Transactional(readOnly = true)
    public List<BusaDocMstr> listMastersForExport(
            String type,
            String fromDt,
            String toDt,
            String keyword,
            String status,
            String sortField,
            String sortDirection) {

        final String bizKey  = TenantContext.currentBizKey();
        final String docType = resolveDocType(type);
        validateDateRange(fromDt, toDt);

        Pageable effective = applyDefaultSortIfNeeded(Pageable.unpaged(), sortField, sortDirection);
        String keywordPattern = buildKeywordPattern(keyword);
        String statusFilter   = blankToNull(status);

        return repo.findFiltered(bizKey, docType, fromDt, toDt, statusFilter, keywordPattern, effective).getContent();
    }

    /** When a caller passes a {@link Pageable} with no sort (e.g. the
     *  export path's {@code Pageable.unpaged()}), weave the user-driven
     *  sort (or the default ORDER BY) in. Mirrors the {@code AccountService}
     *  pattern. */
    private static Pageable applyDefaultSortIfNeeded(
            Pageable pageable, String sortField, String sortDirection) {
        Sort sort = buildSort(EInvoiceSortField.parse(sortField), SortDirection.parse(sortDirection));
        if (pageable.isUnpaged()) {
            return Pageable.unpaged(sort);
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
    }

    /* ------------------------------------------------------------------
     *  Validation (IR-1, IR-3)
     * ------------------------------------------------------------------ */

    /**
     * Rule IR-1 — see class Javadoc. Verified against
     * {@code einv_0001_001_r001_act.jsp:268-284} and
     * {@code wabooks.enums.BusaDocType}.
     */
    String resolveDocType(String type) {
        if (type == null || type.isBlank()) {
            throw new BusinessException(CODE_BAD_REQUEST);
        }
        String upper = type.toUpperCase(Locale.ROOT);
        switch (upper) {
            case "SALES":
            case "I":
                return "I";
            case "EXPENSE":
            case "BILL":
            case "B":
                return "B";
            default:
                throw new BusinessException(CODE_BAD_REQUEST);
        }
    }

    /**
     * Rule IR-3 — both {@code from} and {@code to} required; valid
     * {@code YYYYMMDD}; {@code from ≤ to}.
     */
    void validateDateRange(String fromDt, String toDt) {
        if (fromDt == null || fromDt.isBlank() || toDt == null || toDt.isBlank()) {
            throw new BusinessException(CODE_VALIDATION, "from and to are required (YYYYMMDD)");
        }
        LocalDate from;
        LocalDate to;
        try {
            from = LocalDate.parse(fromDt, LEGACY_DATE);
            to   = LocalDate.parse(toDt,   LEGACY_DATE);
        } catch (DateTimeParseException ex) {
            throw new BusinessException(CODE_VALIDATION, "from / to must be valid YYYYMMDD dates");
        }
        if (from.isAfter(to)) {
            throw new BusinessException(CODE_VALIDATION, "from must be on or before to");
        }
    }

    /* ------------------------------------------------------------------
     *  Pagination + sort plumbing
     * ------------------------------------------------------------------ */

    static int clampSize(int size) {
        if (size <= 0) return DEFAULT_PAGE_SIZE;
        return Math.min(size, MAX_PAGE_SIZE);
    }

    /**
     * Build a Spring Data {@link Sort} from the validated whitelist enum +
     * direction. The user's primary sort is always followed by
     * {@code REG_DTM DESC} as a tiebreaker (and by {@code ISSU_DT DESC}
     * when the primary isn't already on it) so equal-keyed rows keep a
     * deterministic order across requests.
     */
    static Sort buildSort(EInvoiceSortField field, SortDirection direction) {
        if (field == null || direction == null) {
            return DEFAULT_SORT;
        }
        Sort.Order primary = direction == SortDirection.ASC
                ? Sort.Order.asc(field.entityProperty())
                : Sort.Order.desc(field.entityProperty());
        // When the user already sorts on issuDt, only append regDtm as
        // tiebreaker; otherwise weave both default columns in.
        if ("issuDt".equals(field.entityProperty())) {
            return Sort.by(primary, Sort.Order.desc("regDtm"));
        }
        return Sort.by(primary).and(DEFAULT_SORT);
    }

    /* ------------------------------------------------------------------
     *  Keyword search (IR-5)
     * ------------------------------------------------------------------ */

    /**
     * Rule IR-5 — lower-case + escape LIKE meta-chars + wrap with
     * {@code %…%}. Uses {@code !} as the escape character (mirrors
     * {@link com.kosign.wecms.domain.banking.service.AccountService}'s
     * keyword pattern) so the JPQL stays portable across PostgreSQL's
     * {@code standard_conforming_strings} settings — a backslash
     * literal inside a JPA string requires Java double-escape AND
     * DB-side configuration, which {@code !} sidesteps entirely.
     *
     * <p>Returns {@code null} when the keyword is blank so the JPQL
     * guard skips the predicate entirely.
     */
    static String buildKeywordPattern(String raw) {
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
     * Rule IR-4 status filter — trim and collapse blank to {@code null}
     * so the JPQL {@code (:status IS NULL OR …)} guard skips the predicate.
     * The value is matched verbatim against the stored (Vietnamese)
     * {@code EINV_INVOICESTATUS} — no case-folding, no locale resolution
     * (legacy did a plain {@code = ?} too).
     */
    static String blankToNull(String raw) {
        if (raw == null) return null;
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /* ------------------------------------------------------------------
     *  Row mapping
     * ------------------------------------------------------------------ */

    private EInvoiceRow toRow(BusaDocMstr r) {
        // REG_DTM is YYYYMMDDhhmmss; static UI shows time as HH:mm:ss.
        // ISSU_TM is a refactor-only proxy — legacy doesn't expose this.
        String issuTm = r.getRegDtm() != null && r.getRegDtm().length() >= 14
                ? r.getRegDtm().substring(8, 14)
                : "";
        return new EInvoiceRow(
                r.getDocId(),
                r.getDispDocId(),
                r.getIssuDt(),
                issuTm,
                r.getEinvInvoicesymbol(),
                r.getTrcoCmnm() != null ? r.getTrcoCmnm() : r.getTrcoCmenm(),
                r.getTrcoTaxId(),
                r.getTrcoTrscCcyCd() != null ? r.getTrcoTrscCcyCd().trim() : "",
                wireAmount(r.getTotlAmt()),
                wireAmount(r.getSplyAmtSum()),
                wireAmount(r.getTaxAmtSum()),
                r.getEinvInvoicestatus(),
                r.getEinvInvoicestatusBef(),
                r.getProvStts() != null ? r.getProvStts().trim() : null,
                r.getDueDt(),
                r.getOverDueDays()
        );
    }

    static String wireAmount(BigDecimal value) {
        if (value == null) return "0";
        return value.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }
}
