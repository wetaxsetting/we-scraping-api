package com.kosign.wecms.domain.banking.dto;

import java.util.List;

/**
 * Account Inquiry list response.
 *
 * <p>{@link #balance} / {@link #availableBal} are scalar sums across
 * every row in the filtered result (NOT the page slice). They are only
 * meaningful when the wire {@code currency} filter is a single concrete
 * CCY (VND, USD, KRW, …); when {@code currency=FCY} (multi-currency
 * mode) the scalars are mathematically valid but cross-currency, so the
 * FCY page must NOT render them as a single-currency total.
 *
 * <p>{@link #totalsByCcy} is the per-currency breakdown — one entry per
 * distinct {@code CCY_CD} in the filtered rows. The FCY page renders
 * one summary tile per entry; the VND page also reads from this list
 * (its breakdown is always a single VND entry).
 *
 * <p><b>Pagination semantics:</b>
 * <ul>
 *   <li>{@link #accounts} is the rows on the requested page (size
 *       {@link #pageSize}). For the export path or callers that pass an
 *       unpaged {@code Pageable}, this is the full filtered set.</li>
 *   <li>{@link #totalCount} is the unpaged filtered total — the number
 *       the on-screen "TOTAL ACCOUNTS" pill shows. It does NOT collapse
 *       to the page slice.</li>
 *   <li>{@link #pageNumber} / {@link #pageSize} / {@link #totalPages}
 *       drive the pagination chrome. {@code pageNumber} is zero-based
 *       per Spring Data convention; the frontend converts to
 *       one-based for display.</li>
 * </ul>
 *
 * Wire names auto-derived from camelCase Java fields via the global
 * SNAKE_CASE Jackson naming strategy.
 */
public record AccountListResponse(
        List<AccountRow> accounts,
        long totalCount,
        String availableBal,
        String balance,
        List<CurrencyTotal> totalsByCcy,
        int pageNumber,
        int pageSize,
        int totalPages,
        /**
         * Distinct bank list for the tenant in the current currency
         * scope. Drives the bank-filter dropdown options on the
         * frontend; intentionally independent of the {@code bankCd}
         * and {@code keyword} filters so selecting one bank or typing
         * in the search box never narrows the dropdown's option set.
         */
        List<BankOption> banksAvailable
) { }
