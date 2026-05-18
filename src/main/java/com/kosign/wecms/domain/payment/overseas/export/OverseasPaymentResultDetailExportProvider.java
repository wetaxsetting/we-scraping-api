package com.kosign.wecms.domain.payment.overseas.export;

import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.export.ExcelExportRequest;
import com.kosign.wecms.common.export.ExportFetch;
import com.kosign.wecms.common.export.ExportRowProvider;
import com.kosign.wecms.domain.payment.dto.OverseasPaymentDetailResponse;
import com.kosign.wecms.domain.payment.dto.OverseasPaymentDetailRowResponse;
import com.kosign.wecms.domain.payment.service.OverseasPaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Payment &gt; Overseas Payment &gt; Payment Result &gt; Detail — Excel export.
 *
 * <p>The controller injects {@code ovrsPymnNo} into the request's
 * {@code filters} map before forwarding to {@link com.kosign.wecms.common.export.ExcelExportService}.
 * The provider reads that key, loads the detail via
 * {@link OverseasPaymentService#getDetail}, and maps each
 * {@link OverseasPaymentDetailRowResponse} to a FIELD_ID-keyed row map.
 *
 * <p>Mirrors the legacy {@code pymn_0007_001} detail view data.
 * ScreenId: {@code payment-overseas-payment-result-detail}
 * (seeded in V161).
 */
@Component
@RequiredArgsConstructor
public class OverseasPaymentResultDetailExportProvider implements ExportRowProvider {

    private static final Set<String> ALLOWED = Set.of(
            "BNFC_BANK_NM", "BNFC_ACCT_NO", "BNFC_NM",
            "CCY_CD", "TRNF_AMT", "MEMO", "TRNF_YN");

    private final OverseasPaymentService overseasPaymentService;

    @Override
    public String screenId() {
        return "payment-overseas-payment-result-detail";
    }

    @Override
    public Set<String> allowedFieldIds() {
        return ALLOWED;
    }

    @Override
    public ExportFetch fetch(ExcelExportRequest request) {
        Map<String, Object> filters = request.filters() != null ? request.filters() : Map.of();
        Object rawId = filters.get("ovrsPymnNo");
        if (rawId == null || rawId.toString().isBlank()) {
            throw new BusinessException("WBE_VALIDATION", "ovrsPymnNo filter required");
        }

        OverseasPaymentDetailResponse detail =
                overseasPaymentService.getDetail(rawId.toString().trim());

        List<Map<String, Object>> rows = new ArrayList<>();
        for (OverseasPaymentDetailRowResponse r : detail.rows()) {
            Map<String, Object> m = new HashMap<>();
            m.put("BNFC_BANK_NM", r.bnfcBankNm() != null ? r.bnfcBankNm() : "");
            m.put("BNFC_ACCT_NO", r.bnfcActNo()  != null ? r.bnfcActNo()  : "");
            m.put("BNFC_NM",      r.bnfcNm()     != null ? r.bnfcNm()     : "");
            m.put("CCY_CD",       r.ccyCd()       != null ? r.ccyCd()      : "");
            m.put("TRNF_AMT",     r.trnfAmt()     != null ? r.trnfAmt()    : BigDecimal.ZERO);
            m.put("MEMO",         r.memo()        != null ? r.memo()       : "");
            m.put("TRNF_YN",      "Y".equals(r.trnfYn()) ? "Completed" : "In Progress");
            rows.add(m);
        }

        int cnt = rows.size();
        BigDecimal total = detail.totalTranAmt() != null ? detail.totalTranAmt() : BigDecimal.ZERO;
        String ccy = detail.ccyCd() != null ? detail.ccyCd() : "";
        String formattedAmt = formatAmount(total, ccy);

        return ExportFetch.of(rows,
                cnt + " transaction(s)  ·  Total (" + ccy + "): " + formattedAmt);
    }

    private String formatAmount(BigDecimal amount, String ccy) {
        if ("VND".equalsIgnoreCase(ccy)) {
            long whole = amount.setScale(0, RoundingMode.HALF_UP).longValue();
            return NumberFormat.getInstance(Locale.forLanguageTag("vi-VN")).format(whole) + "₫";
        }
        // USD / EUR / KRW / JPY — en-US locale + currency prefix
        if (Set.of("USD", "EUR", "KRW", "JPY").contains(ccy != null ? ccy.toUpperCase() : "")) {
            String symbol = switch (ccy.toUpperCase()) {
                case "USD" -> "$";
                case "EUR" -> "€";
                case "KRW" -> "₩";
                case "JPY" -> "¥";
                default -> ccy + " ";
            };
            return symbol + NumberFormat.getInstance(Locale.US)
                    .format(amount.setScale(2, RoundingMode.HALF_UP));
        }
        // everything else — vi-VN locale, no symbol
        return NumberFormat.getInstance(Locale.forLanguageTag("vi-VN"))
                .format(amount.setScale(2, RoundingMode.HALF_UP));
    }
}
