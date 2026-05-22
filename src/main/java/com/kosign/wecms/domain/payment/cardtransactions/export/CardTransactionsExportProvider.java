package com.kosign.wecms.domain.payment.cardtransactions.export;

import com.kosign.wecms.common.export.ExcelExportRequest;
import com.kosign.wecms.common.export.ExcelSortSpec;
import com.kosign.wecms.common.export.ExportFetch;
import com.kosign.wecms.common.export.ExportRowProvider;
import com.kosign.wecms.domain.payment.cardtransactions.dto.CardTransactionRow;
import com.kosign.wecms.domain.payment.cardtransactions.service.CardTransactionsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Payment &gt; Card Transactions — Excel export.
 *
 * <p>Binds {@code screenId = "card-transactions"}. Reuses
 * {@link CardTransactionsService#listAllRows} so the export and the
 * on-screen list draw from one query path — same validation, same
 * tenant scoping, same default sort.
 *
 * <p>Wire filters consumed (mirrors the list endpoint):
 * {@code fromDt}, {@code toDt} (both required), {@code srchWd}
 * (optional), {@code bankCds}, {@code cardNos} (optional lists),
 * {@code linkYn} (optional Y/N). Sort is honoured via
 * {@code CardTrnsHstrSortField} whitelist.
 *
 * <p>Allowed FIELD_ID set mirrors the V204 View Setting reseed for
 * {@code dym_area_id='default'} minus the special CHECKBOX / MEMO_ICON
 * rows (those are admin-controlled and never reach the export pipeline
 * per {@code ScreenOptionService.SPECIAL_FIELD_IDS}). FIELD_IDs that
 * have not yet been wired through the service projection
 * ({@code LINK_REF}) are intentionally omitted — add when the Phase B
 * linked-document join ships its dedicated wire field.
 */
@Component
@RequiredArgsConstructor
public class CardTransactionsExportProvider implements ExportRowProvider {

    private static final Set<String> ALLOWED_FIELD_IDS = Set.of(
            "TRSC_DT",
            "CARD_NO",
            "MERCH_NM",
            "CARD_NICK_NM",
            "CARD_HLDR_NM",
            "CCY_CD",
            "TRSC_AMT",
            "STTS",
            "CARD_TRNS_USAGE_NM",
            "CONT",
            "APPR_SEQ_NO",
            "CHART_ACCT_CD"
    );

    private final CardTransactionsService service;

    @Override
    public String screenId() {
        return "card-transactions";
    }

    @Override
    public Set<String> allowedFieldIds() {
        return ALLOWED_FIELD_IDS;
    }

    @Override
    public ExportFetch fetch(ExcelExportRequest request) {
        Map<String, Object> filters = request.filters() != null ? request.filters() : Map.of();
        String fromDt = stringOrNull(filters.get("fromDt"));
        String toDt   = stringOrNull(filters.get("toDt"));
        String srchWd = stringOrNull(filters.get("srchWd"));
        String linkYn = stringOrNull(filters.get("linkYn"));
        List<String> bankCds = stringListOrEmpty(filters.get("bankCds"));
        List<String> cardNos = stringListOrEmpty(filters.get("cardNos"));

        ExcelSortSpec sort  = request.sort();
        String sortField    = sort != null ? sort.field()     : null;
        String sortDirection= sort != null ? sort.direction() : null;

        List<CardTransactionRow> rows = service.listAllRows(
                fromDt, toDt, srchWd, bankCds, cardNos, linkYn,
                sortField, sortDirection);

        List<Map<String, Object>> mapped = new ArrayList<>(rows.size());
        for (CardTransactionRow r : rows) {
            mapped.add(toFieldMap(r));
        }

        String subHeader = String.format(Locale.ROOT, "%d transaction(s)", mapped.size());
        return ExportFetch.of(mapped, subHeader);
    }

    /** FIELD_ID-keyed map for the workbook writer. {@code TRSC_AMT} is
     *  emitted as the raw {@code BigDecimal} so the Excel cell's
     *  {@code format: "amount"} re-formatter renders the value
     *  natively (and Excel can re-sum the column). Status / purpose /
     *  reporting-account columns ship raw codes — frontend i18n labels
     *  do not apply to the spreadsheet, same precedent as
     *  ErpTrnsHistoryExportProvider. */
    private static Map<String, Object> toFieldMap(CardTransactionRow r) {
        Map<String, Object> m = new HashMap<>();
        m.put("TRSC_DT",      nullToEmpty(r.trscDt()));
        m.put("CARD_NO",      nullToEmpty(r.cardNo()));
        m.put("MERCH_NM",     nullToEmpty(r.merchNm()));
        m.put("CARD_NICK_NM", nullToEmpty(r.cardNickNm()));
        m.put("CARD_HLDR_NM", nullToEmpty(r.cardHldrNm()));
        m.put("CCY_CD",       nullToEmpty(r.ccyCd()));
        m.put("TRSC_AMT",     r.trscAmt());
        m.put("STTS",         nullToEmpty(r.stts()));
        m.put("CARD_TRNS_USAGE_NM", nullToEmpty(r.cardTrnsUsageCode()));
        m.put("CONT",         nullToEmpty(r.cont()));
        m.put("APPR_SEQ_NO",  nullToEmpty(r.apprSeqNo()));
        m.put("CHART_ACCT_CD",nullToEmpty(r.chartAcctCd()));
        return m;
    }

    private static String stringOrNull(Object raw) {
        if (raw == null) return null;
        String s = raw.toString().trim();
        return s.isEmpty() ? null : s;
    }

    private static List<String> stringListOrEmpty(Object raw) {
        if (raw == null) return List.of();
        if (raw instanceof List<?> list) {
            List<String> out = new ArrayList<>(list.size());
            for (Object o : list) {
                if (o == null) continue;
                String s = o.toString().trim();
                if (!s.isEmpty()) out.add(s);
            }
            return out;
        }
        // Comma-separated fallback for clients that ship the wire
        // values as a single string — same defensive shape used by
        // other export providers when the wire schema is open.
        String s = raw.toString().trim();
        if (s.isEmpty()) return List.of();
        List<String> out = new ArrayList<>();
        for (String token : s.split(",")) {
            String t = token.trim();
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
