package com.kosign.wecms.domain.payment.cardtransactions.dto;

import java.math.BigDecimal;

/**
 * Aggregate totals over the same filter set as the paged ledger rows.
 * Renders the three dashboard hero cards on the Card Transactions page:
 * Total / Approval Requested / Approval Not Requested.
 *
 * <p>{@code TOTL_LINK_CNT} and {@code TOTL_UN_LINK_CNT} from the legacy
 * envelope are intentionally dropped — they're hardcoded to {@code 0}
 * in {@code card_0001_001_r001_act.jsp:242-243} and carry no signal.
 * See migration doc §3.2.
 */
public record CardTransactionTotals(
        long       totalCount,
        BigDecimal totlAmt,
        long       totlApprReqCnt,
        BigDecimal totlApprReqAmt,
        long       totlApprNotReqCnt,
        BigDecimal totlApprNotReqAmt) {
}
