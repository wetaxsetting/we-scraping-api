package com.kosign.wecms.domain.payment.internaltransfer.dto;

import java.math.BigDecimal;

/**
 * One row in the per-line table inside the Scheduled Transfer detail
 * response. Carries the recipient, amount, ERP linkage, and free-text
 * fields users can edit on a draft batch.
 */
public record TransferLineDetail(
        String     scdulPymnDetlNo,
        String     antDivCd,
        String     rcptNm,
        String     tarBankCd,
        /** Human-readable name resolved from {@code tarBankCd} via
         *  {@code BankCdResolver}. Null when the code is missing or
         *  doesn't match a known {@code BANK_INFM} row. The frontend's
         *  Request Approval dialog renders this next to the bank code. */
        String     tarBankNm,
        String     fincMdaNo,
        String     tranUsag,
        BigDecimal tranAmt,
        String     ccyCd,
        String     trcoKey,
        String     handTrcoKey,
        String     docNo,
        String     note,
        String     citadBankCd,
        String     chartAcctCd,
        String     chartAcctSeqNo,
        /** Display breadcrumb for the chart-of-accounts entry — e.g.
         *  {@code "EXPENSES > PAYMENT"}. Built server-side by walking
         *  up the parent chain on {@code CHART_ACCT_INFM}. Null when
         *  the row carries no chart account or the lookup misses. */
        String     chartAcctBreadcrumb,
        String     depoCcyCd,
        String     bankTrnsShortId) { }
