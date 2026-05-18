package com.kosign.wecms.domain.banking.service.cashflow;

import java.math.BigDecimal;

/**
 * Common shape for one row in the Cash Flow report's source UNION.
 * Mirrors the {@code all_payment_data} CTE of the legacy
 * {@code get_cashflow_report()} function: every source table
 * (bank / card / overseas / scheduled) projects to this shape before
 * filtering, FX conversion, and grouping.
 *
 * <p>{@code apprSeqNo} is non-null only for the three non-bank sources
 * (overseas / card / scheduled). Bank rows always set it to
 * {@code null} — bank-side classification is gated by the
 * {@code ACCT_TRNS_RCV_PAY_INFM.CNFM_YN = 'Y'} predicate inside the
 * bank aggregator, not by approval-status.
 *
 * <p>{@code originalCurrency} carries the source row's currency code
 * verbatim ({@code "VND"} / {@code "USD"} / {@code "KRW"} or whatever
 * legacy data contains). {@code FxConverter} maps to the request's
 * base currency via the user-supplied rates.
 */
public record PaymentDataRow(
        String chartAcctSeqNo,
        String chartAcctCd,
        BigDecimal originalAmount,
        String originalCurrency,
        String bankCd,
        String acctNo,
        int paymentYear,
        int paymentMonth,
        /** Null for bank-source rows. */
        String apprSeqNo
) { }
