package com.kosign.wecms.domain.weinvoice.repository;

import java.time.LocalDate;
import java.util.List;

/**
 * Validated, typed form of {@link com.kosign.wecms.domain.weinvoice.dto.InvoiceFilter}.
 * Built by the service after the owner / MST checks; the repository trusts it.
 *
 * @param ownerId          owner user id (scopes tags, uploads and manual lookup codes)
 * @param mst             working MST ({@code invoices.company_mst})
 * @param engineDirection {@code mua} / {@code ban}, {@code null} = both
 * @param from            issue date from, {@code null} = open
 * @param to              issue date to, {@code null} = open
 * @param keyword         raw keyword or {@code null}
 * @param xml             {@code has} / {@code missing} / {@code null}
 * @param pdf             {@code has} / {@code missing} / {@code null}
 * @param tthais          effective {@code tthai} codes, empty = all
 * @param kind            {@code normal} / {@code cash_register} / {@code no_code} / {@code null}
 * @param tagIds          tag ids, empty = no tag filter
 * @param provider        provider MST, {@code no_code}, {@code unknown} or {@code null}
 * @param excludeTaxIds   partner MSTs to leave out, empty = none
 * @param ids             invoice ids, empty = no id filter
 * @param sort            sort column or {@code null} for the default (newest first)
 * @param sortDesc        descending when {@code sort} is set
 */
public record InvoiceCriteria(
        String ownerId,
        String mst,
        String engineDirection,
        LocalDate from,
        LocalDate to,
        String keyword,
        String xml,
        String pdf,
        List<Integer> tthais,
        String kind,
        List<Long> tagIds,
        String provider,
        List<String> excludeTaxIds,
        List<Long> ids,
        InvoiceSortField sort,
        boolean sortDesc) {
}
