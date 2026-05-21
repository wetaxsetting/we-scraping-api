package com.kosign.wecms.domain.payment.cardtransactions.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Update Cards request body — kicks the synchronous scrape sync for
 * every active card in the tenant that has a registered
 * {@code BANK_CERT_INFO} credential. Mirrors legacy
 * {@code nxisas_cardTranList} ("Real-Time Online Inquire" button),
 * collapsed into a single backend orchestrator instead of a
 * client-driven per-credential loop.
 *
 * <p>The frontend picks the date range. The legacy popup defaults to
 * {@code min(TRN_LST_SUC_DTM, today − 3 months)} → today; the
 * refactor frontend ships the same default but the user may narrow
 * or widen freely (subject to per-bank scrape caps the gateway
 * enforces).
 */
public record SyncCardTransactionsRequest(
        @NotBlank @Pattern(regexp = "^\\d{8}$", message = "from_dt: invalid format")
        String fromDt,
        @NotBlank @Pattern(regexp = "^\\d{8}$", message = "to_dt: invalid format")
        String toDt,
        /** When {@code true}, the orchestrator ignores {@code fromDt} and
         *  derives the range from each eligible card's
         *  {@code TRN_LST_SUC_DTM} (the timestamp of its previous
         *  successful sync). When any card has never been synced, falls
         *  back to {@code today &minus; 90 days}. {@code toDt} is also
         *  forced to today server-side so the UX matches the legacy
         *  popup's "from latest updated date to today" copy.
         *  <p>Frontend still ships {@code fromDt} / {@code toDt} when
         *  Auto is checked so the wire shape stays uniform — the
         *  values are ignored on the server. */
        Boolean auto) {
}
