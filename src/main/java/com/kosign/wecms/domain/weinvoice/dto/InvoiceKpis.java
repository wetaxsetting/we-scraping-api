package com.kosign.wecms.domain.weinvoice.dto;

import java.math.BigDecimal;

/**
 * Totals of the filtered list. {@code count} counts every matching invoice; amounts are in
 * VND and leave out cancelled and replaced invoices (UI Spec 3.3 / 3.9).
 * {@code missing_xml} = coded invoices (symbol C…) without XML — the ones "fetch missing XML"
 * can still get from GDT (UI Spec 3.6); {@code no_code_missing_xml} = invoices without code
 * waiting for mail / provider / upload.
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
        BigDecimal purchaseTotal) {
}
