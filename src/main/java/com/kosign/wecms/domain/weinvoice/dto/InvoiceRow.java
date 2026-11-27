package com.kosign.wecms.domain.weinvoice.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * One invoice in the list (screen 04). Amounts are in the invoice currency;
 * {@code total_vnd} is converted with the invoice exchange rate. File presence is given as
 * flags — the full file record is on {@code GET /invoices/{id}/files}.
 *
 * <p>UI Spec v1.2 (2026-11-27): {@code fee} (Σ fees / charges, not taxed), {@code trade_discount}, {@code po_number},
 * {@code due_date} (ISO when the TTKhac value is a date) + {@code due_text} (as written), {@code items} (first item names)
 * + {@code item_count}, {@code vat_rates}, {@code related} (original named by this invoice, and the invoices naming it).
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
        /* Lookup code / link for the provider portal, as in the detail's lookup block: typed by hand wins over the XML. */
        String lookupCode,
        String lookupCodeCompany,
        String lookupUrl,
        List<String> tagIds,
        String source,
        String syncedAt,
        BigDecimal fee,
        BigDecimal tradeDiscount,
        String poNumber,
        String dueDate,
        String dueText,
        List<String> items,
        int itemCount,
        List<String> vatRates,
        Related related) {

    /** How many item names the row previews. */
    public static final int ITEM_PREVIEW = 3;

    /** Row with the per-page extras (items, tax rates, related invoices). */
    public InvoiceRow withExtras(List<String> items, int itemCount, List<String> vatRates, Related related) {
        return new InvoiceRow(id, direction, issueDate, templateNo, symbol, number, sellerTaxId, sellerName, buyerTaxId,
                buyerName, partnerTaxId, partnerName, currency, exchangeRate, preTax, tax, total, totalVnd, status, kind,
                cqtCode, provider, providerTaxId, providerKnown, hasXml, hasPdf, needsLookupCode, lookupCode,
                lookupCodeCompany, lookupUrl, tagIds, source, syncedAt, fee, tradeDiscount, poNumber, dueDate, dueText,
                items, itemCount, vatRates, related);
    }

    /**
     * Related invoices (UI Spec v1.2 §3.15). {@code relation} = {@code replaces} / {@code adjusts} when this invoice
     * names an original ({@code original}; its {@code id} is null while the original is not stored — "⚠ chưa có HĐ
     * gốc"); {@code referenced_by} = the invoices that replace / adjust this one.
     */
    public record Related(String relation, Ref original, List<Ref> referencedBy) {
    }

    /** One related invoice; {@code relation} is that invoice's own relation to this one; {@code total} in its currency. */
    public record Ref(String id, String relation, String templateNo, String symbol, String number, String issueDate,
                      BigDecimal total) {
    }
}
