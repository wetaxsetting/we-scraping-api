package com.kosign.wecms.domain.setting.firmbankinghistory.export;

import com.kosign.wecms.common.export.ExcelExportRequest;
import com.kosign.wecms.common.export.ExcelSortSpec;
import com.kosign.wecms.common.export.ExportFetch;
import com.kosign.wecms.common.export.ExportRowProvider;
import com.kosign.wecms.domain.setting.firmbankinghistory.dto.FirmBankingHistoryRow;
import com.kosign.wecms.domain.setting.firmbankinghistory.service.FirmBankingHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Settings &gt; Firm Banking History — Excel export.
 *
 * <p>Binds {@code screenId = "firm-banking-history"}. Reuses
 * {@link FirmBankingHistoryService#listAll} so the export and on-screen
 * list draw from one query path — same validation, same tenant scoping,
 * same default sort. Subheader is the row count in legacy {@code N
 * record(s)} format (no per-leg / per-type total — the unified view
 * carries no aggregable amount column).
 *
 * <p>Wire filters consumed: {@code fromDate}, {@code toDate} (both
 * required by {@code FirmBankingHistoryService.validateDate}),
 * {@code svcType} (optional, one of F/S/G/B), {@code keyword}
 * (optional partial match on {@code SEQ_NO}). Sort is honoured via
 * {@code FirmBankingHistorySortField} whitelist.
 */
@Component
@RequiredArgsConstructor
public class FirmBankingHistoryExportProvider implements ExportRowProvider {

    /** FIELD_IDs allowed in the Excel export — matches the View Setting
     *  seed minus the special CHECKBOX / ETC rows (which {@code
     *  ScreenOptionService.SPECIAL_FIELD_IDS} drops regardless). */
    private static final Set<String> ALLOWED_FIELD_IDS = Set.of(
            "REG_DTM",
            "SVC_TYPE",
            "SVC_NM",
            "SEQ_NO",
            "DEP_BANK_CD",
            "RS_CD",
            "RS_MSG"
    );

    private final FirmBankingHistoryService service;

    @Override
    public String screenId() {
        return "firm-banking-history";
    }

    @Override
    public Set<String> allowedFieldIds() {
        return ALLOWED_FIELD_IDS;
    }

    @Override
    public ExportFetch fetch(ExcelExportRequest request) {
        Map<String, Object> filters = request.filters() != null ? request.filters() : Map.of();
        String fromDate = stringOrNull(filters.get("fromDate"));
        String toDate   = stringOrNull(filters.get("toDate"));
        String svcType  = stringOrNull(filters.get("svcType"));
        String keyword  = stringOrNull(filters.get("keyword"));

        ExcelSortSpec sort = request.sort();
        String sortField     = sort != null ? sort.field() : null;
        String sortDirection = sort != null ? sort.direction() : null;

        List<FirmBankingHistoryRow> rows = service.listAll(
                fromDate, toDate, svcType, keyword, sortField, sortDirection);

        List<Map<String, Object>> mapped = new ArrayList<>(rows.size());
        for (FirmBankingHistoryRow r : rows) {
            mapped.add(toFieldMap(r));
        }

        String subHeader = String.format(Locale.ROOT, "%d record(s)", mapped.size());
        return ExportFetch.of(mapped, subHeader);
    }

    /** Translate a {@link FirmBankingHistoryRow} into a FIELD_ID-keyed
     *  map. The {@code SVC_TYPE} cell renders the raw 1-letter code
     *  (F/S/G/B) — the Excel writer doesn't have the locale context to
     *  resolve {@code svcTypeKey}, and the legacy export
     *  ({@code comm_0221_001} has none) had no labelled variant either.
     *  The {@code SVC_NM} cell renders the raw legacy value (per-leg
     *  semantics — F leg is the TRAN_TP code; S/G/B are stable English
     *  literals from the V60 view). */
    private static Map<String, Object> toFieldMap(FirmBankingHistoryRow r) {
        Map<String, Object> m = new HashMap<>();
        m.put("REG_DTM", r.tarnDtm());
        m.put("SVC_TYPE", nullToEmpty(r.svcType()));
        m.put("SVC_NM", nullToEmpty(r.svcNm()));
        m.put("SEQ_NO", nullToEmpty(r.seqNo()));
        m.put("DEP_BANK_CD", nullToEmpty(r.depBankCd()));
        m.put("RS_CD", nullToEmpty(r.rsltCd()));
        m.put("RS_MSG", nullToEmpty(r.rsltMsg()));
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
