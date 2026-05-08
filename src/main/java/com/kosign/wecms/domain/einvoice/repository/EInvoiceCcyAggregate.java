package com.kosign.wecms.domain.einvoice.repository;

import java.math.BigDecimal;

/**
 * Per-currency aggregate row returned by
 * {@link BusaDocMstrRepository#aggregateByCurrency}. Runs the SAME
 * filter chain as the paged list query but without {@code LIMIT/OFFSET}
 * so totals reflect the full filtered result regardless of which page
 * the client is viewing (rule IR-6 in
 * {@code docs/refactor-wecms/planning/addon/sales-e-invoices-plan.md}).
 *
 * <p>Mirrors the per-currency breakdown the legacy {@code
 * einv_0001_001_r001} returns ({@code TOT_VND_CNT} / {@code TOT_VND_AMT}
 * / {@code TOT_VND_TAX_AMT} / {@code TOT_VND_SPLY_AMT} and the USD twin)
 * — at a higher level: one row per distinct {@code TRCO_TRSC_CCY_CD},
 * ordered by code so the frontend renders footer rows deterministically.
 *
 * <p>Sums are wrapped in {@code COALESCE(…, 0)} at the JPQL layer so an
 * empty filter result emits zero rather than null.
 */
public record EInvoiceCcyAggregate(
        String ccyCd,
        long count,
        BigDecimal totalAmount,
        BigDecimal taxAmount,
        BigDecimal pretaxAmount) { }
