package com.kosign.wecms.domain.setting.vasaccount.export;

import com.kosign.wecms.common.enums.AStts;
import com.kosign.wecms.common.enums.VasAcctType;
import com.kosign.wecms.common.export.ExcelExportRequest;
import com.kosign.wecms.common.export.ExcelSortSpec;
import com.kosign.wecms.common.export.ExportFetch;
import com.kosign.wecms.common.export.ExportRowProvider;
import com.kosign.wecms.common.i18n.MessageResolver;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.setting.vasaccount.dto.VasAccountRow;
import com.kosign.wecms.domain.setting.vasaccount.service.VasAccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Excel export provider for Settings &gt; VAS Account. Implements the
 * shared {@link ExportRowProvider} contract; registered automatically by
 * {@link com.kosign.wecms.common.export.ExportableScreenRegistry} via the
 * {@code @Component} annotation.
 *
 * <p>Reuses {@link VasAccountService#listAllForExport} so the export and
 * the on-screen list draw from one query path — no second source of
 * truth, no risk of divergence. The shared
 * {@link com.kosign.wecms.common.export.ExcelExportService} handles
 * everything else (visibility intersection, column ordering, POI
 * streaming, RFC 5987 filename).
 *
 * <p>Subheader is backend-computed per CLAUDE.md decision 5
 * ("Backend is the authority on subheader"). VAS Account has no
 * monetary totals so the subheader is just the count
 * ({@code "N VAS Account(s)"}). The PO call (2026-05-11) retired Type
 * / English Name from the UI so the export defaults to the four columns
 * actually rendered by {@code VasAccountTable.tsx} — {@code VAS_ACCT_CD},
 * {@code VAS_ACCT_NM}, {@code VAS_ACCT_TYPE} (hidden in the popover by
 * default; admin can opt in), {@code STTS}. {@code CHECKBOX} / {@code ETC}
 * are special FIELD_IDs filtered by the shared writer.
 *
 * <p>Type + status cells emit the localized label (resolved server-side
 * via {@link MessageResolver}) so the spreadsheet user sees "Active" /
 * "Inactive" / "Dual Balance" / etc. instead of the raw legacy codes
 * "A" / "I" / "0". Mirrors the wire shape on the list endpoint —
 * which ships both the raw code AND the i18n key so the frontend can
 * resolve via {@code t(key)}. The export-side resolver picks the same
 * key and ships the resolved text directly into the cell since Excel
 * has no client-side i18n layer.
 */
@Component
@RequiredArgsConstructor
public class VasAccountExportProvider implements ExportRowProvider {

    /** FIELD_IDs allowed in the export. Mirrors the V79
     *  {@code SYS_SCR_OPTION_WECMS} seed minus the special ids
     *  ({@code ETC} is filtered out by {@code ScreenOptionService.SPECIAL_FIELD_IDS}
     *  on the writer side regardless; {@code CHECKBOX} is intentionally
     *  not seeded — DN-8 closed). */
    private static final Set<String> ALLOWED_FIELD_IDS = Set.of(
            "VAS_ACCT_CD",
            "VAS_ACCT_NM",
            "VAS_ACCT_TYPE",
            "STTS"
    );

    private final VasAccountService service;
    private final MessageResolver   messages;

    /** {@inheritDoc}
     *
     *  <p>Matches the View Setting {@code WEB_SVC_ID} seeded in V79 —
     *  this string is the registry key shared with the frontend's
     *  {@code SCREEN_IDS.SETTINGS_VAS_ACCOUNT} constant in
     *  {@code wecms-web-main/lib/viewSettingScreens.ts}. */
    @Override
    public String screenId() {
        return "vas-account";
    }

    @Override
    public Set<String> allowedFieldIds() {
        return ALLOWED_FIELD_IDS;
    }

    @Override
    public ExportFetch fetch(ExcelExportRequest request) {
        Map<String, Object> filters = request.filters() != null
                ? request.filters() : Map.of();
        String searchWd = stringOrNull(filters.get("searchWd"));
        List<String> statuses = coerceStatuses(filters.get("statuses"));

        ExcelSortSpec sort = request.sort();
        String sortField    = sort != null ? sort.field() : null;
        String sortDirection = sort != null ? sort.direction() : null;

        List<VasAccountRow> rows =
                service.listAllForExport(searchWd, statuses, sortField, sortDirection);

        Locale locale = currentLocale();
        List<Map<String, Object>> mapped = new ArrayList<>(rows.size());
        for (VasAccountRow r : rows) {
            mapped.add(toFieldMap(r, locale));
        }

        String subHeader = composeSubHeader(rows.size(), locale);
        return ExportFetch.of(mapped, subHeader);
    }

    /**
     * Translate a {@link VasAccountRow} into a {@code FIELD_ID}-keyed
     * map. Type + status resolve through {@link MessageResolver} into
     * the localized label (e.g. "Dual Balance" / "Active") so the
     * spreadsheet reader sees text, not the raw legacy code.
     */
    private Map<String, Object> toFieldMap(VasAccountRow r, Locale locale) {
        Map<String, Object> m = new HashMap<>();
        m.put("VAS_ACCT_CD",   nullToEmpty(r.vasAcctCd()));
        m.put("VAS_ACCT_NM",   nullToEmpty(r.vasAcctNm()));
        m.put("VAS_ACCT_TYPE", resolveTypeLabel(r.vasAcctType(), locale));
        m.put("STTS",          resolveStatusLabel(r.stts(), locale));
        return m;
    }

    /** Plain-text count label. Caller-side i18n is a Sprint follow-up
     *  (see CLAUDE.md "Excel export" rule — labels via MessageResolver
     *  is the future direction). */
    private String composeSubHeader(int rowCount, Locale locale) {
        // Reuse the audit-activity key as the "VAS Account" noun so the
        // subheader stays in lockstep with the audit ledger's vocabulary.
        String label = messages.resolve("audit.vas_account.activity", locale);
        return String.format(Locale.ROOT, "%d %s(s)", rowCount, label);
    }

    private String resolveTypeLabel(String code, Locale locale) {
        return VasAcctType.fromCode(code)
                .map(VasAcctType::i18nKey)
                .map(key -> messages.resolve(key, locale))
                .orElse(nullToEmpty(code));
    }

    private String resolveStatusLabel(String code, Locale locale) {
        return AStts.fromCode(code)
                .map(AStts::i18nKey)
                .map(key -> messages.resolve(key, locale))
                .orElse(nullToEmpty(code));
    }

    private static Locale currentLocale() {
        var lang = TenantContext.currentLangCd();
        return lang == null ? Locale.ENGLISH : lang.toLocale();
    }

    /** Coerce a free-form {@code filters.statuses} value into a
     *  {@code List<String>}. Accepts a JSON array, a comma-separated
     *  string, or a single value; returns an empty list for anything
     *  unparseable so the service's "no filter" branch kicks in. */
    private static List<String> coerceStatuses(Object raw) {
        if (raw == null) return List.of();
        if (raw instanceof List<?> list) {
            return list.stream()
                    .filter(Objects::nonNull)
                    .map(Object::toString)
                    .filter(s -> !s.isBlank())
                    .toList();
        }
        String s = raw.toString().trim();
        if (s.isEmpty()) return List.of();
        if (s.contains(",")) {
            List<String> out = new ArrayList<>();
            for (String part : s.split(",")) {
                String t = part.trim();
                if (!t.isEmpty()) out.add(t);
            }
            return out;
        }
        return List.of(s);
    }

    private static String stringOrNull(Object raw) {
        if (raw == null) return null;
        String s = raw.toString().trim();
        return s.isEmpty() ? null : s;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
