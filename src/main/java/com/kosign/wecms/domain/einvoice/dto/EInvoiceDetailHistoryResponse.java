package com.kosign.wecms.domain.einvoice.dto;

import java.util.List;

/**
 * Response envelope for the e-Invoice detail-history popup
 * ({@code GET /api/v1/addon/einvoices/detail-history}). Mirrors the
 * legacy {@code nxisas_einvhis_dt_r001} {@code REC} list.
 */
public record EInvoiceDetailHistoryResponse(
        List<EInvoiceDetailHistoryRow> rows
) { }
