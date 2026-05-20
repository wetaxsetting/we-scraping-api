package com.kosign.wecms.domain.banking.service.cashflow;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Currency conversion mirroring the legacy {@code filtered_payment_data}
 * CTE in {@code get_cashflow_report()}. Per-row CASE with {@code ROUND()}
 * precision varying by base currency:
 *
 * <pre>
 *   source\base   VND          USD                  KRW
 *   VND           × 1 (no-op)  ÷ usdRate  ROUND 4   ÷ krwRate  ROUND 2
 *   USD           × usdRate    × 1 (no-op)          × usdRate ÷ krwRate ROUND 2
 *                 ROUND 2
 *   KRW           × krwRate    × krwRate ÷ usdRate  × 1 (no-op)
 *                 ROUND 2      ROUND 4
 *   Other         passthrough  ÷ usdRate  ROUND 4   ÷ krwRate  ROUND 2
 * </pre>
 *
 * <p>{@link RoundingMode#HALF_UP} matches Postgres {@code ROUND()}.
 * Precisions copied verbatim from the function body.
 *
 * <p>Validation lives upstream in {@code CashFlowReportService.validate}:
 * {@code usdRate} is non-null/positive whenever {@code baseCurrency != USD};
 * {@code krwRate} same for {@code KRW}. This component trusts its inputs.
 *
 * <p>Reference: docs/refactor-wecms/features/cash-flow-report.md §3.9.
 */
@Component
public class FxConverter {

    private static final String VND = "VND";
    private static final String USD = "USD";
    private static final String KRW = "KRW";

    /**
     * Convert {@code amount} from {@code sourceCcy} to {@code baseCcy}
     * using the user-supplied rates. Returns the original amount when
     * source == base (no-op branch).
     */
    public BigDecimal convert(BigDecimal amount,
                              String sourceCcy,
                              String baseCcy,
                              BigDecimal usdRate,
                              BigDecimal krwRate) {
        if (amount == null) return null;
        if (sourceCcy == null) sourceCcy = VND;

        return switch (baseCcy) {
            case VND -> toVnd(amount, sourceCcy, usdRate, krwRate);
            case USD -> toUsd(amount, sourceCcy, usdRate, krwRate);
            case KRW -> toKrw(amount, sourceCcy, usdRate, krwRate);
            // Defensive: legacy CASE has an ELSE branch that defaults
            // to VND-style conversion. Preserved for parity.
            default -> toVnd(amount, sourceCcy, usdRate, krwRate);
        };
    }

    private BigDecimal toVnd(BigDecimal amount, String src, BigDecimal usdRate, BigDecimal krwRate) {
        return switch (src) {
            case VND -> amount;
            case USD -> amount.multiply(usdRate).setScale(2, RoundingMode.HALF_UP);
            case KRW -> amount.multiply(krwRate).setScale(2, RoundingMode.HALF_UP);
            default  -> amount;
        };
    }

    private BigDecimal toUsd(BigDecimal amount, String src, BigDecimal usdRate, BigDecimal krwRate) {
        return switch (src) {
            case USD -> amount;
            case VND -> amount.divide(usdRate, 4, RoundingMode.HALF_UP);
            case KRW -> amount.multiply(krwRate)
                              .divide(usdRate, 4, RoundingMode.HALF_UP);
            default  -> amount.divide(usdRate, 4, RoundingMode.HALF_UP);
        };
    }

    private BigDecimal toKrw(BigDecimal amount, String src, BigDecimal usdRate, BigDecimal krwRate) {
        return switch (src) {
            case KRW -> amount;
            case VND -> amount.divide(krwRate, 2, RoundingMode.HALF_UP);
            case USD -> amount.multiply(usdRate)
                              .divide(krwRate, 2, RoundingMode.HALF_UP);
            default  -> amount.divide(krwRate, 2, RoundingMode.HALF_UP);
        };
    }
}
