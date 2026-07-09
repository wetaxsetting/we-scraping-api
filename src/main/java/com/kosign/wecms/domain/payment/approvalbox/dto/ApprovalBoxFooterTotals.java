package com.kosign.wecms.domain.payment.approvalbox.dto;

import java.math.BigDecimal;

/**
 * Payment &gt; Approval Box footer-summary totals. Mirrors the legacy
 * {@code resl_0001_001} footer bar — document count + per-currency
 * amount totals (Total USD / Total VND / Total KRW).
 *
 * <p><b>Tab-unfiltered, full result set.</b> Both the count and the
 * per-currency sums are computed over every row matching the date /
 * search / visibility scope, with the {@code APPR_TYPE_CD} tab predicate
 * intentionally dropped — mirrors the legacy footer which is fed the
 * "count" dynamic ({@code resl_0001_001_r001_act.jsp:80-86,345}) rather
 * than the tab-filtered list dynamic. This is distinct from
 * {@link ApprovalBoxListResponse#totalCount} (the tab-filtered row count
 * that drives pagination).
 *
 * <p>Amount logic matches legacy {@code IDO.EXPT_RESOL_MSTR_R003_CNT.xml}
 * — each row's amount is bucketed by its resolved {@code CCY_CD}; card
 * approvals ({@code APPR_DV='33'}) sum {@code CARD_TRNS_HSTR.TRSC_AMT}
 * (IDO lines 49-52). Only USD / VND / KRW are surfaced (the three the
 * footer renders); other currencies fall outside the three buckets, as
 * in legacy.
 *
 * <p>Wire names auto-derived from camelCase via the global SNAKE_CASE
 * Jackson strategy: {@code total_document_count} / {@code total_usd_amount}
 * / {@code total_vnd_amount} / {@code total_krw_amount}.
 */
public record ApprovalBoxFooterTotals(
        long       totalDocumentCount,
        BigDecimal totalUsdAmount,
        BigDecimal totalVndAmount,
        BigDecimal totalKrwAmount
) {
    public static ApprovalBoxFooterTotals empty() {
        return new ApprovalBoxFooterTotals(
                0L, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }
}
