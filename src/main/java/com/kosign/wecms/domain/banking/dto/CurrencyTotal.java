package com.kosign.wecms.domain.banking.dto;

/**
 * Per-currency aggregation for the Account Inquiry response.
 *
 * <p>One entry per distinct {@code CCY_CD} present in the filtered row
 * set. Used by the frontend to render one summary tile per currency
 * group on the FCY page (USD card + KRW card + …) without doing decimal
 * arithmetic in JS. The VND page also receives this — its breakdown has
 * a single entry ({@code VND}).
 *
 * Wire names auto-derived from camelCase Java fields via the global
 * SNAKE_CASE Jackson naming strategy.
 */
public record CurrencyTotal(
        String ccyCd,
        int    count,
        String balance,
        String availableBal
) { }
