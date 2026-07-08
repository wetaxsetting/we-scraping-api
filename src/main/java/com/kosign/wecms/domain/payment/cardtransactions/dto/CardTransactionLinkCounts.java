package com.kosign.wecms.domain.payment.cardtransactions.dto;

/**
 * Per-link-status row counts for the Card Transactions filter tabs
 * (All / Linked / Unlinked). Computed over the same date / bank / card /
 * search filter set as the paged ledger, but <b>independent of the active
 * {@code link_yn} tab</b> — so the frontend can render all three counts
 * simultaneously ("All 36 | Linked 12 | Unlinked 24") regardless of which
 * tab is selected.
 *
 * <p>{@code linked} keys on the presence of a linked document in
 * {@code BUSA_DOC_RCV_PAY_HIS} ({@code RCV_PAY_WAY_CD = 'R'}) — the same
 * predicate {@code CardTrnsHstrRepository.findLedgerPage} uses for the
 * {@code link_yn} filter, so the counts agree with the rows each tab shows.
 * {@code all == linked + unlinked} always holds.
 *
 * <p>Deliberately distinct from the legacy {@code TOTL_LINK_CNT} /
 * {@code TOTL_UN_LINK_CNT} envelope fields, which JEX hardcodes to {@code 0}
 * ({@code card_0001_001_r001_act.jsp:242-243}) and which
 * {@link CardTransactionTotals} intentionally drops. These are real computed
 * counts requested for the refactor's tab UI.
 */
public record CardTransactionLinkCounts(
        long all,
        long linked,
        long unlinked) {
}
