package com.kosign.wecms.domain.weinvoice.dto;

import java.util.List;

/** {@code GET /invoices}. {@code page} is zero-based. */
public record InvoiceListResponse(
        List<InvoiceRow> rows,
        long totalCount,
        int page,
        int size,
        InvoiceKpis kpis,
        List<IncompletePeriod> incompletePeriods) {
}
