package com.kosign.wecms.domain.weinvoice.common;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * When an invoice needs a lookup code from the user ("!" pill, provider filter {@code needs_code}) — one rule for the
 * list rows, the filter / facet SQL and the detail (docs/issues/weinvoice-needs-lookup-code-by-provider-2026-11-18.md).
 */
public final class ProviderLookup {

    /**
     * Providers whose original the engine fetches without a lookup code: BKAV (DLHDon/@Id = InvoiceGUID), Vĩnh Hy and
     * Minh Khang (DLHDon/@Id), My Software and VININVOICE (MCCQT), Mắt Bão (template + symbol + number).
     */
    public static final Set<String> WITHOUT_CODE = Set.of(
            "0101360697", "0314743623", "0314209362", "0108971656", "0109282176", "0302712571");

    /** {@link #WITHOUT_CODE} as a SQL list literal — constants only, never user input. */
    public static final String WITHOUT_CODE_SQL = WITHOUT_CODE.stream().sorted()
            .map(m -> "'" + m + "'").collect(Collectors.joining(", "));

    private ProviderLookup() {
    }

    /**
     * Provider in the catalogue, no lookup code / link (XML or typed by the user), no original PDF yet, and the
     * provider needs a code to find the invoice.
     *
     * @param providerMst MSTTCGP, branch suffix allowed ({@code 0106026495-001})
     */
    public static boolean needsCode(String providerMst, boolean known, boolean hasCode, boolean hasPdf) {
        if (providerMst == null || !known || hasCode || hasPdf) {
            return false;
        }
        return !WITHOUT_CODE.contains(providerMst.split("-")[0]);
    }
}
