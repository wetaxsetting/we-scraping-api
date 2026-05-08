package com.kosign.wecms.domain.operation.export;

import com.kosign.wecms.common.export.ExcelExportRequest;
import com.kosign.wecms.common.export.ExcelSortSpec;
import com.kosign.wecms.common.export.ExportFetch;
import com.kosign.wecms.common.export.ExportRowProvider;
import com.kosign.wecms.domain.operation.dto.OperationStatusListResponse;
import com.kosign.wecms.domain.operation.dto.OperationStatusRow;
import com.kosign.wecms.domain.operation.service.OperationStatusFilter;
import com.kosign.wecms.domain.operation.service.OperationStatusService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Add-on &gt; Operation Status Inquiry — Excel export provider.
 *
 * <p>Reuses {@link OperationStatusService#list} so the export and the
 * on-screen list draw from one query path. Allowed FIELD_IDs match the
 * View Setting seed for {@code addon-operation-status}; {@code CHECKBOX}
 * is filtered out by the writer regardless via
 * {@code ScreenOptionService.SPECIAL_FIELD_IDS}.
 *
 * <p>Subheader is computed server-side per CLAUDE.md decision 5 —
 * {@code "<count> companies · As of <YYYY-MM-DD>"}.
 */
@Component
@RequiredArgsConstructor
public class OperationStatusExportProvider implements ExportRowProvider {

    private static final Set<String> ALLOWED_FIELD_IDS = Set.of(
            "COMPANY",
            "ADDRESS",
            "OPERATION_STATUS",
            "LAST_UPDATE"
    );

    private static final DateTimeFormatter AS_OF_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;

    private final OperationStatusService operationStatusService;

    @Override
    public String screenId() {
        return "addon-operation-status";
    }

    @Override
    public Set<String> allowedFieldIds() {
        return ALLOWED_FIELD_IDS;
    }

    @Override
    public ExportFetch fetch(ExcelExportRequest request) {
        Map<String, Object> filters = request.filters() != null ? request.filters() : Map.of();
        String keyword       = stringOrNull(filters.get("keyword"));
        if (keyword == null) {
            // Frontend sends `q` (matches the list endpoint's query param);
            // accept either alias so the wire stays forgiving.
            keyword = stringOrNull(filters.get("q"));
        }
        String companyStatus = stringOrNull(filters.get("companyStatus"));

        ExcelSortSpec sort = request.sort();
        String sortField     = sort != null ? sort.field()     : null;
        String sortDirection = sort != null ? sort.direction() : null;

        OperationStatusListResponse data = operationStatusService.list(
                new OperationStatusFilter(keyword, companyStatus),
                sortField,
                sortDirection,
                Pageable.unpaged());

        List<OperationStatusRow> rows = data.rows() != null ? data.rows() : List.of();
        List<Map<String, Object>> mapped = new ArrayList<>(rows.size());
        for (OperationStatusRow r : rows) {
            mapped.add(toFieldMap(r));
        }

        return ExportFetch.of(mapped, composeSubHeader(mapped.size()));
    }

    /**
     * Subheader format: {@code "<count> companies · As of <YYYY-MM-DD>"}.
     * The "as of" date is the export-run date — distinct from any
     * individual row's {@code LAST_UPDATE} (which is the per-row GDT poll
     * timestamp). Plain text for now; Sprint 2 may push the labels
     * through {@code MessageResolver} for full i18n.
     */
    private static String composeSubHeader(int rowCount) {
        return String.format(
                Locale.ROOT,
                "%d compan%s  ·  As of %s",
                rowCount,
                rowCount == 1 ? "y" : "ies",
                LocalDate.now().format(AS_OF_FORMAT)
        );
    }

    /**
     * Translate an {@link OperationStatusRow} into a {@code FIELD_ID}-
     * keyed map. {@code COMPANY} merges name + tax code on one line
     * (matches the on-screen cell composition). {@code OPERATION_STATUS}
     * emits the raw Vietnamese string so the spreadsheet preserves the
     * full GDT response — losing detail to the 3-state canonical key
     * would be a regression on the export side.
     */
    private static Map<String, Object> toFieldMap(OperationStatusRow r) {
        Map<String, Object> m = new HashMap<>();
        m.put("COMPANY", composeCompanyCell(r));
        m.put("ADDRESS", nullToEmpty(r.officeAdd()));
        m.put("OPERATION_STATUS", nullToEmpty(r.companyStatus()));
        m.put("LAST_UPDATE", r.lastUpdate()); // legacy YYYYMMDDHHmmss
        return m;
    }

    /**
     * "{companyName} / {taxCode}" so a spreadsheet user can identify the
     * row at a glance. Falls back gracefully when one half is missing
     * (legacy {@code COMPANYNAME} is nullable; {@code TAXCODE} is part
     * of the PK and never null).
     */
    private static String composeCompanyCell(OperationStatusRow r) {
        String name = nullToEmpty(r.companyNm()).trim();
        String tax  = nullToEmpty(r.taxcode()).trim();
        if (name.isEmpty()) return tax;
        if (tax.isEmpty())  return name;
        return name + " / " + tax;
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
