package com.kosign.wecms.domain.weinvoice.dto;

import java.util.List;

/**
 * Counts behind the list filters (provider, status, kind) for one MST, direction and period.
 *
 * @param partners  partners of the period ("Loại trừ MST"), the {@code 500} most frequent, by name
 * @param needsCode invoices whose provider needs a lookup code that is still missing (provider filter {@code needs_code})
 */
public record InvoiceFacets(
        List<ProviderCount> providers,
        List<KeyCount> statuses,
        List<KeyCount> kinds,
        List<PartnerCount> partners,
        long needsCode) {

    /**
     * @param key   provider MST, or {@code no_code} when no MSTTCGP is known
     * @param name  catalogue name; {@code null} when the MST is not in the catalogue
     * @param known whether the MST is in the catalogue
     */
    public record ProviderCount(String key, String name, boolean known, long count) {
    }

    public record KeyCount(String key, long count) {
    }

    /** @param taxId partner MST (seller of a purchase, buyer of a sale) */
    public record PartnerCount(String taxId, String name, long count) {
    }
}
