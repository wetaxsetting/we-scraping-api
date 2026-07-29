package com.kosign.wecms.domain.vatdeclaration.export;

import com.kosign.wecms.common.export.ExcelExportRequest;
import com.kosign.wecms.common.export.ExportFetch;
import com.kosign.wecms.common.export.ExportRowProvider;
import com.kosign.wecms.domain.vatdeclaration.dto.AnnexRow;
import com.kosign.wecms.domain.vatdeclaration.service.VatDeclarationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Excel export for the annex invoice modal (interface spec §2.13,
 * {@code POST /addon/vat-declarations/{sid}/annex/export}). Reuses the exact
 * derived-invoice data path as {@code GET .../annex/invoices}
 * ({@link VatDeclarationService#annexRowsForExport}) — one query for list +
 * export per the common/export rule.
 *
 * <p>Export-only screen: no {@code SYS_SCR_OPTION_WECMS} seed. The provider
 * allow-list is authoritative and {@link com.kosign.wecms.common.export.ExcelExportService}
 * skips the View-Setting intersection when the screen has no persisted rows
 * (WBE2001 → treated as "no persisted view"), same as the e-Invoice providers.
 *
 * <p>{@code sid} is not part of {@link ExcelExportRequest}; the controller
 * injects it into {@code filters["decl_id"]} from the path before forwarding,
 * so the resource id is always the trusted path value, never client body.
 */
@Component
@RequiredArgsConstructor
public class VatAnnexExportProvider implements ExportRowProvider {

    /** Filter key the controller injects from the path variable. */
    public static final String FILTER_DECL_ID = "decl_id";

    private static final Set<String> ALLOWED_FIELD_IDS = Set.of(
            "IO_DV", "SRC_DOC_ID", "INV_NO", "INV_SYMBOL", "INV_DT",
            "TRCO_TAX_CODE", "TRCO_NM", "PROD_NM",
            "SPLY_AMT", "VAT_RATE", "VAT_AMT", "REDUCED_AMT");

    private final VatDeclarationService service;

    @Override
    public String screenId() {
        return "addon-vat-declaration-annex";
    }

    @Override
    public Set<String> allowedFieldIds() {
        return ALLOWED_FIELD_IDS;
    }

    @Override
    public ExportFetch fetch(ExcelExportRequest request) {
        Map<String, Object> filters = request.filters() != null ? request.filters() : Map.of();
        Long sid = asLong(filters.get(FILTER_DECL_ID));
        String annexType = asString(filters.get("annex_type"));
        String search = asString(filters.get("search"));
        String ioDv = asString(filters.get("io_dv"));

        List<AnnexRow> rows = sid == null
                ? List.of()
                : service.annexRowsForExport(sid, annexType, search, ioDv);

        List<Map<String, Object>> mapped = new ArrayList<>(rows.size());
        BigDecimal totSply = BigDecimal.ZERO, totVat = BigDecimal.ZERO, totReduced = BigDecimal.ZERO;
        for (AnnexRow r : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("IO_DV", r.ioDv());
            m.put("SRC_DOC_ID", r.srcDocId());
            m.put("INV_NO", r.invNo());
            m.put("INV_SYMBOL", r.invSymbol());
            m.put("INV_DT", r.invDt());
            m.put("TRCO_TAX_CODE", r.trcoTaxCode());
            m.put("TRCO_NM", r.trcoNm());
            m.put("PROD_NM", r.prodNm());
            m.put("SPLY_AMT", r.splyAmt());
            m.put("VAT_RATE", r.vatRate());
            m.put("VAT_AMT", r.vatAmt());
            m.put("REDUCED_AMT", r.reducedAmt());
            mapped.add(m);
            totSply = totSply.add(dec(r.splyAmt()));
            totVat = totVat.add(dec(r.vatAmt()));
            totReduced = totReduced.add(dec(r.reducedAmt()));
        }

        String left = rows.size() + " dòng";
        String right = "Doanh thu: " + plain(totSply) + " · VAT: " + plain(totVat)
                + " · Giảm: " + plain(totReduced);
        return ExportFetch.of(mapped, left, right);
    }

    private static Long asLong(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(v.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String asString(Object v) {
        if (v == null) return null;
        String s = v.toString().trim();
        return s.isEmpty() ? null : s;
    }

    private static BigDecimal dec(String raw) {
        if (raw == null || raw.isBlank()) return BigDecimal.ZERO;
        try {
            return new BigDecimal(raw.trim());
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    private static String plain(BigDecimal v) {
        return v.stripTrailingZeros().toPlainString();
    }
}
