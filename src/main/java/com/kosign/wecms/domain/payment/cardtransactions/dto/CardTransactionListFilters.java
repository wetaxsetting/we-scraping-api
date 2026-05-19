package com.kosign.wecms.domain.payment.cardtransactions.dto;

import java.util.List;

/**
 * Service-internal carrier for the resolved (trimmed, lower-cased,
 * defaulted) filter set the list endpoint operates on. Controllers
 * never see this record — they pass raw {@code @RequestParam} strings,
 * the service normalises them once, and both
 * {@code findLedgerPage} / {@code findLedgerTotals} consume the result.
 *
 * <p>Keeps the service code path single-source-of-truth for filter
 * semantics: filter shape changes once here, both queries see the
 * same shape, no drift between page and totals.
 */
public record CardTransactionListFilters(
        String       fromDt,
        String       toDt,
        List<String> bankCds,
        List<String> cardNos,
        String       srchWdPattern,
        String       linkYn) {
}
