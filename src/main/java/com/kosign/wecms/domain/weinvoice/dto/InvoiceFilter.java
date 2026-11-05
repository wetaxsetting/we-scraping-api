package com.kosign.wecms.domain.weinvoice.dto;

import java.util.List;

/**
 * Invoice list filter (screen 04). Query parameters of {@code GET /invoices} and the
 * {@code filter} part of the export bodies; every field is optional except
 * {@code company_mst}.
 *
 * @param companyMst    working MST (must be managed by the tenant)
 * @param direction     {@code purchase} / {@code sales}; blank = both
 * @param from          issue date from, {@code yyyy-MM-dd} (inclusive)
 * @param to            issue date to, {@code yyyy-MM-dd} (inclusive)
 * @param keyword       partner name / MST, symbol, number or CQT code
 * @param xml           {@code has} / {@code missing}
 * @param pdf           {@code has} / {@code missing}
 * @param statuses      UI Spec statuses ({@code original}, {@code replacement}, …); empty = all
 * @param kind          {@code normal} / {@code cash_register} / {@code no_code}
 * @param tagIds        invoices carrying any of these tags
 * @param provider      provider MST, {@code no_code} (no MSTTCGP known) or {@code unknown} (not in the catalogue)
 * @param excludeTaxIds partner MSTs to leave out
 * @param ids           restrict to these invoice ids (exports of a selection)
 * @param sortField     {@code issue_date}, {@code number}, {@code partner}, {@code pre_tax}, {@code tax}, {@code total}
 * @param sortDirection {@code asc} / {@code desc}
 */
public record InvoiceFilter(
        String companyMst,
        String direction,
        String from,
        String to,
        String keyword,
        String xml,
        String pdf,
        List<String> statuses,
        String kind,
        List<String> tagIds,
        String provider,
        List<String> excludeTaxIds,
        List<String> ids,
        String sortField,
        String sortDirection) {
}
