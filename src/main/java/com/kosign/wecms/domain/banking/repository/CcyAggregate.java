package com.kosign.wecms.domain.banking.repository;

import java.math.BigDecimal;

/**
 * Per-currency aggregate row returned by the totals query on
 * {@link AcctMstrRepository#aggregateByCcy}. The query runs the SAME
 * filter chain as the paged list query but without {@code LIMIT/OFFSET},
 * so totals reflect the full filtered result regardless of which page
 * the client is viewing.
 *
 * <p>Mirrors the per-currency breakdown the legacy {@code
 * IDO.ACCT_MSTR_R001_CNT.xml} returns ({@code TOT_CNT}, {@code TOT_BAL},
 * {@code TOT_REAL_AMT}). Sums are wrapped in {@code COALESCE(…, 0)} at
 * the SQL layer so empty filter results emit zero rather than null.
 */
public record CcyAggregate(String ccyCd, long count, BigDecimal balance, BigDecimal availableBal) { }
