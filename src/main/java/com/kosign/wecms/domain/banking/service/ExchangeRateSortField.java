package com.kosign.wecms.domain.banking.service;

import com.kosign.wecms.domain.banking.dto.ExchangeRateRow;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.function.Function;

/**
 * Sort whitelist for Exchange Rate Inquiry. Constant names mirror the
 * display FIELD_IDs used by the frontend table headers; the mapped extractor
 * resolves the comparable value from {@link ExchangeRateRow}.
 *
 * <p>Sorting is applied in-memory on the DTO list after the native-query
 * fetch, so we use a {@link Comparator}-based approach instead of Spring Data
 * {@code Sort} (which requires JPA entity property names and cannot be used
 * with {@code DISTINCT ON} native queries).</p>
 *
 * <p>Null values always sort last regardless of direction — matches the
 * frontend's "-" placeholder rendering for missing rates.</p>
 *
 * <p>The display currency is the foreign side of each rate pair:
 * {@code ccyCd} for Shinhan rows, {@code refCcyCd} for Woori rows whose
 * {@code ccyCd} is always "VND". {@link #CURRENCY} sorts by that resolved
 * display currency, not the raw {@code ccyCd}.</p>
 */
public enum ExchangeRateSortField {

    CURRENCY(r -> displayCcy(r)),
    CASH_BUY(ExchangeRateRow::buyAmt),
    CASH_SELL(ExchangeRateRow::sellAmt),
    TT_BUY(ExchangeRateRow::tranBuyAmt),
    TT_SELL(ExchangeRateRow::tranSellAmt);

    private final Function<ExchangeRateRow, Comparable<?>> extractor;

    ExchangeRateSortField(Function<ExchangeRateRow, Comparable<?>> extractor) {
        this.extractor = extractor;
    }

    /**
     * Builds an ascending {@link Comparator} for {@link ExchangeRateRow}.
     * Null values sort last.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public Comparator<ExchangeRateRow> comparator() {
        return (a, b) -> {
            Comparable va = extractor.apply(a);
            Comparable vb = extractor.apply(b);
            if (va == null && vb == null) return 0;
            if (va == null) return 1;
            if (vb == null) return -1;
            return va.compareTo(vb);
        };
    }

    /**
     * Lenient lookup for the wire {@code FIELD_ID}. Returns {@code null}
     * for unknown / blank values so the service falls back to the default
     * alphabetical ORDER BY rather than rejecting the request.
     */
    public static ExchangeRateSortField parse(String fieldId) {
        if (fieldId == null) return null;
        String trimmed = fieldId.trim();
        if (trimmed.isEmpty()) return null;
        try {
            return valueOf(trimmed.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** Resolves the foreign currency displayed as the row label. */
    private static String displayCcy(ExchangeRateRow row) {
        return "VND".equals(row.ccyCd()) ? row.refCcyCd() : row.ccyCd();
    }
}
