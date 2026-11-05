package com.kosign.wecms.domain.weinvoice.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * One invoice in the list (screen 04). Amounts are in the invoice currency;
 * {@code total_vnd} is converted with the invoice exchange rate. File presence is given as
 * flags — the full file record is on {@code GET /invoices/{id}/files}.
 */
public record InvoiceRow(
        String id,
        String direction,
        String issueDate,
        String templateNo,
        String symbol,
        String number,
        String sellerTaxId,
        String sellerName,
        String buyerTaxId,
        String buyerName,
        String partnerTaxId,
        String partnerName,
        String currency,
        BigDecimal exchangeRate,
        BigDecimal preTax,
        BigDecimal tax,
        BigDecimal total,
        BigDecimal totalVnd,
        String status,
        String kind,
        String cqtCode,
        String provider,
        String providerTaxId,
        boolean providerKnown,
        boolean hasXml,
        boolean hasPdf,
        boolean needsLookupCode,
        List<String> tagIds,
        String source,
        String syncedAt) {
}
