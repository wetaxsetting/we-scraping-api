package com.kosign.wecms.domain.weinvoice.dto;

import java.math.BigDecimal;

/**
 * Totals of the filtered list. {@code count} counts every matching invoice; amounts are in
 * VND and leave out cancelled and replaced invoices (UI Spec 3.3 / 3.9).
 * The missing counts leave cancelled and replaced invoices out too:
 * {@code missing_xml} = coded invoices (symbol C…) without XML — the ones "fetch missing XML"
 * can still get from GDT (UI Spec 3.6); {@code no_code_missing_xml} = invoices without code
 * waiting for mail / provider / upload; {@code missing_xml_all} = both (the list card).
 * {@code period_sales_count} / {@code period_purchase_count}: the MST's invoices of the period in
 * each direction, whatever the other filters (the card's "Cả kỳ" line).
 */
public record InvoiceKpis(
        long count,
        BigDecimal preTax,
        BigDecimal tax,
        BigDecimal total,
        long missingXml,
        long noCodeMissingXml,
        long missingPdf,
        long salesCount,
        long purchaseCount,
        BigDecimal salesTotal,
        BigDecimal purchaseTotal,
        long missingXmlAll,
        long periodSalesCount,
        long periodPurchaseCount) {

    /** Same totals with the period counts of both directions. */
    public InvoiceKpis withPeriodCounts(long sales, long purchase) {
        return new InvoiceKpis(count, preTax, tax, total, missingXml, noCodeMissingXml, missingPdf, salesCount,
                purchaseCount, salesTotal, purchaseTotal, missingXmlAll, sales, purchase);
    }
}
