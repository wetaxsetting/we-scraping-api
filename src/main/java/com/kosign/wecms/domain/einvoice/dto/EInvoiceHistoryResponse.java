package com.kosign.wecms.domain.einvoice.dto;

import java.util.List;

/**
 * Response of {@code GET /api/v1/addon/einvoices/history} — the
 * e-Invoice "Latest Inquiry History" list (legacy {@code nxisas_einvhis}).
 * Newest call first; no server-side pagination (legacy loads the full
 * window — volume is bounded by the date-range chips).
 */
public record EInvoiceHistoryResponse(
        List<EInvoiceHistoryRow> rows
) { }
