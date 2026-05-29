package com.kosign.wecms.domain.payment.common;

import com.kosign.wecms.common.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Shared cross-row consistency checks for Excel uploads in the four
 * SCDUL_PYMN-family modules (Internal Transfer / Vendor / Expense /
 * Payroll). Runs AFTER per-row validation completes; throws when the
 * file as a whole violates a global rule.
 *
 * <p>Legacy parity:
 * <ul>
 *   <li>{@link #assertSingleCurrency} — mirrors
 *       {@code PymnHelper.java:427-436} ({@code WBW1160} on mixed
 *       VND/USD in a single file). The legacy code rejects with a
 *       per-row warning; refactor surfaces a single batch-level
 *       error ({@code WBE2120}) listing all currencies seen.</li>
 *   <li>{@link #assertSingleNetwork} — mirrors
 *       {@code PymnHelper.validateUploadedPayments:1261-1297}
 *       ({@code WBE1191} on mixed CITAD/NAPAS in a single file).
 *       Refactor surfaces {@code WBE2121}.</li>
 *   <li>{@link #assertCurrencyWhitelist} — mirrors
 *       {@code PymnHelper.java:420-422} ({@code WBE1162} when a
 *       row's currency is outside the {@code {VND, USD}} whitelist).
 *       Refactor folds this into {@code WBE2120}'s "found:" message
 *       so the user sees both the bad currency and the offending file.</li>
 * </ul>
 *
 * <p>Empty / blank values are dropped before comparison so an
 * unfilled row doesn't trigger the guard accidentally; the per-row
 * required-field check handles those.
 */
@Component
public class UploadConsistencyValidator {

    /** Currencies the SCDUL_PYMN modules support. Mirrors legacy
     *  {@code PymnHelper.java:420-422}. Overseas Payment is NOT in
     *  this set — it has its own multi-currency policy and routes
     *  through a different validator. */
    private static final Set<String> SCDUL_ALLOWED_CCY = Set.of("VND", "USD");

    /**
     * Throw {@link BusinessException} ({@code WBE2120}) when the
     * supplied row currencies are not all the same. Blank values are
     * ignored.
     *
     * @param rowCurrencies one currency value per parsed row, in order.
     */
    public void assertSingleCurrency(List<String> rowCurrencies) {
        Set<String> distinct = distinctNonBlank(rowCurrencies);
        if (distinct.size() > 1) {
            throw new BusinessException("WBE2120", String.join(", ", distinct));
        }
    }

    /**
     * Throw when any row's currency is outside the
     * {@code {VND, USD}} whitelist. Blank values are ignored
     * (already caught by the per-row required check). Called
     * AFTER {@link #assertSingleCurrency} so the message lists the
     * single offending currency, not a mix.
     */
    public void assertCurrencyWhitelist(List<String> rowCurrencies) {
        Set<String> distinct = distinctNonBlank(rowCurrencies);
        Set<String> badCcys = distinct.stream()
                .filter(ccy -> !SCDUL_ALLOWED_CCY.contains(ccy))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (!badCcys.isEmpty()) {
            throw new BusinessException("WBE2120", String.join(", ", badCcys));
        }
    }

    /**
     * Throw {@link BusinessException} ({@code WBE2121}) when the
     * supplied row networks ({@code NAPAS} / {@code CITAD}) are not
     * all the same. Blank values are ignored.
     *
     * <p>Input values are the normalised network names returned by
     * {@link com.kosign.wecms.domain.payment.internaltransfer.service.TransferNetwork}
     * ({@code "NAPAS"}, {@code "CITAD"}). Pass {@code null} for rows
     * whose network couldn't be resolved (parsing fault or unmapped
     * bank); the assertion treats those as blanks.
     */
    public void assertSingleNetwork(List<String> rowNetworks) {
        Set<String> distinct = distinctNonBlank(rowNetworks);
        if (distinct.size() > 1) {
            throw new BusinessException("WBE2121");
        }
    }

    /** Helper — uppercase, trim, drop blanks, return distinct set. */
    private static Set<String> distinctNonBlank(List<String> values) {
        Set<String> out = new HashSet<>();
        if (values == null) return out;
        for (String v : values) {
            if (v == null) continue;
            String t = v.trim().toUpperCase();
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }
}
