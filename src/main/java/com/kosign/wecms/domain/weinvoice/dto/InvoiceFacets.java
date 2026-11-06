package com.kosign.wecms.domain.weinvoice.dto;

import java.util.List;

/** Counts behind the list filters (provider, status, kind) for one MST, direction and period. */
public record InvoiceFacets(
        List<ProviderCount> providers,
        List<KeyCount> statuses,
        List<KeyCount> kinds) {

    /**
     * @param key   provider MST, or {@code no_code} when no MSTTCGP is known
     * @param name  catalogue name; {@code null} when the MST is not in the catalogue
     * @param known whether the MST is in the catalogue
     */
    public record ProviderCount(String key, String name, boolean known, long count) {
    }

    public record KeyCount(String key, long count) {
    }
}
