package com.kosign.wecms.domain.banking.service.cashflow;

import com.kosign.wecms.domain.banking.dto.CashFlowReportResponse.MonthlyAmounts;

import java.math.BigDecimal;

/**
 * Mutable 12-bucket sum + total accumulator. Used to aggregate monthly
 * amounts per {@code (chart_acct_cd)} from the filtered payment-data
 * rows and to roll those amounts up the hierarchy.
 *
 * <p>The constructor seeds all 12 months to zero. {@link #add(int, BigDecimal)}
 * is null-safe (a null amount contributes zero). {@link #snapshot()}
 * returns an immutable {@link MonthlyAmounts}; {@link #totalAmount()}
 * returns the sum of the 12 buckets.
 *
 * <p>Reference: docs/refactor-wecms/features/cash-flow-report.md §3.8.
 */
public final class MonthlyAmountAccumulator {

    private final BigDecimal[] m = new BigDecimal[12];

    public MonthlyAmountAccumulator() {
        for (int i = 0; i < 12; i++) m[i] = BigDecimal.ZERO;
    }

    /** Add {@code amount} into the bucket for {@code month} (1..12). */
    public void add(int month, BigDecimal amount) {
        if (month < 1 || month > 12 || amount == null) return;
        m[month - 1] = m[month - 1].add(amount);
    }

    /** Add another accumulator's monthly totals into this one. */
    public void addAll(MonthlyAmountAccumulator other) {
        for (int i = 0; i < 12; i++) m[i] = m[i].add(other.m[i]);
    }

    /** Add another accumulator's monthly totals, multiplying each by sign. */
    public void addSigned(MonthlyAmountAccumulator other, int sign) {
        for (int i = 0; i < 12; i++) {
            BigDecimal v = other.m[i];
            m[i] = m[i].add(sign < 0 ? v.negate() : v);
        }
    }

    /** Subtract another accumulator's monthly totals from this one. */
    public void subtractAll(MonthlyAmountAccumulator other) {
        for (int i = 0; i < 12; i++) m[i] = m[i].subtract(other.m[i]);
    }

    /** Get the value for month N (1..12). */
    public BigDecimal monthly(int month) {
        return m[month - 1];
    }

    /** Sum of the 12 buckets. */
    public BigDecimal totalAmount() {
        BigDecimal sum = BigDecimal.ZERO;
        for (int i = 0; i < 12; i++) sum = sum.add(m[i]);
        return sum;
    }

    /** Immutable snapshot suitable for the wire DTO. */
    public MonthlyAmounts snapshot() {
        return new MonthlyAmounts(
                m[0], m[1], m[2], m[3], m[4], m[5],
                m[6], m[7], m[8], m[9], m[10], m[11]);
    }
}
