package com.kosign.wecms.domain.setting.erptransactionhistory.export;

import com.kosign.wecms.common.export.ExcelExportRequest;
import com.kosign.wecms.common.export.ExcelSortSpec;
import com.kosign.wecms.common.export.ExportFetch;
import com.kosign.wecms.common.export.ExportRowProvider;
import com.kosign.wecms.domain.setting.erptransactionhistory.dto.ErpTrnsHistoryRow;
import com.kosign.wecms.domain.setting.erptransactionhistory.service.ErpTrnsHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Settings &gt; ERP Transaction History — Excel export.
 *
 * <p>Binds {@code screenId = "erp-transaction-history"}. Reuses
 * {@link ErpTrnsHistoryService#listAll} so the export and on-screen
 * list draw from one query path — same validation, same tenant
 * scoping, same default sort. Subheader is the row count in legacy
 * {@code N record(s)} format (file size is per-row, not aggregable).
 *
 * <p>Wire filters consumed: {@code fromDate}, {@code toDate} (both
 * required), {@code erpType} (optional W/E/D), {@code trnsTypeCd}
 * (optional "01".."17"), {@code keyword} (optional partial match on
 * {@code REG_ID}). Sort is honoured via {@code ErpTrnsHistorySortField}
 * whitelist.
 */
@Component
@RequiredArgsConstructor
public class ErpTrnsHistoryExportProvider implements ExportRowProvider {

    /** FIELD_IDs allowed in the Excel export — matches the View Setting
     *  seed minus the special CHECKBOX / ETC rows. */
    private static final Set<String> ALLOWED_FIELD_IDS = Set.of(
            "REG_DTM",
            "ERP_TYPE",
            "TRNS_TYPE_CD",
            "ORG_FILE_NM",
            "FILE_SIZE",
            "REG_USER",
            "REG_ID"
    );

    private final ErpTrnsHistoryService service;

    @Override
    public String screenId() {
        return "erp-transaction-history";
    }

    @Override
    public Set<String> allowedFieldIds() {
        return ALLOWED_FIELD_IDS;
    }

    @Override
    public ExportFetch fetch(ExcelExportRequest request) {
        Map<String, Object> filters = request.filters() != null ? request.filters() : Map.of();
        String fromDate   = stringOrNull(filters.get("fromDate"));
        String toDate     = stringOrNull(filters.get("toDate"));
        String erpType    = stringOrNull(filters.get("erpType"));
        String trnsTypeCd = stringOrNull(filters.get("trnsTypeCd"));
        String keyword    = stringOrNull(filters.get("keyword"));

        ExcelSortSpec sort = request.sort();
        String sortField     = sort != null ? sort.field() : null;
        String sortDirection = sort != null ? sort.direction() : null;

        List<ErpTrnsHistoryRow> rows = service.listAll(
                fromDate, toDate, erpType, trnsTypeCd, keyword, sortField, sortDirection);

        List<Map<String, Object>> mapped = new ArrayList<>(rows.size());
        for (ErpTrnsHistoryRow r : rows) {
            mapped.add(toFieldMap(r));
        }

        String subHeader = String.format(Locale.ROOT, "%d record(s)", mapped.size());
        return ExportFetch.of(mapped, subHeader);
    }

    /** Translate an {@link ErpTrnsHistoryRow} into a FIELD_ID-keyed
     *  map. {@code ERP_TYPE} and {@code TRNS_TYPE_CD} render the raw
     *  legacy code (W/E/D and 01..17); the frontend's
     *  {@code erp_type_key} / {@code trns_type_key} resolution does
     *  not apply server-side (the writer ships raw codes, the page's
     *  i18n labelling lives in the wire DTO not the spreadsheet).
     *  {@code FILE_SIZE} stays as a raw {@code Long} so the
     *  {@code format: "number"} cell can re-sum natively in Excel. */
    private static Map<String, Object> toFieldMap(ErpTrnsHistoryRow r) {
        Map<String, Object> m = new HashMap<>();
        m.put("REG_DTM", r.regDtm());
        m.put("ERP_TYPE", nullToEmpty(r.erpType()));
        m.put("TRNS_TYPE_CD", nullToEmpty(r.trnsTypeCd()));
        m.put("ORG_FILE_NM", nullToEmpty(r.orgFileNm()));
        m.put("FILE_SIZE", r.fileSize());
        m.put("REG_USER", nullToEmpty(r.userNm()));
        m.put("REG_ID", nullToEmpty(r.regId()));
        return m;
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
