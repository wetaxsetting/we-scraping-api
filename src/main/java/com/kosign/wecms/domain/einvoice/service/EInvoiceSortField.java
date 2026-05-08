package com.kosign.wecms.domain.einvoice.service;

import com.kosign.wecms.common.web.SortableField;

/**
 * Sort whitelist for the Sales / Bill e-Invoice listing. Constant names
 * mirror the View Setting {@code FIELD_ID} values that will be seeded
 * for screens {@code addon-sales-einvoices} / {@code addon-expenses-einvoices};
 * the mapped property is the matching JPA field on
 * {@link com.kosign.wecms.domain.einvoice.entity.BusaDocMstr}.
 *
 * <p>Lenient {@link #parse(String)} returns {@code null} for unknown /
 * blank wire values so the service falls back to the legacy default
 * ORDER BY ({@code ISSU_DT DESC, REG_DTM DESC}) rather than rejecting
 * the request — same contract as
 * {@link com.kosign.wecms.domain.banking.service.AccountSortField}.
 *
 * <p><b>Note on {@code STATUS} sorting under IR-4 Phase 2:</b> the
 * wire field {@code EINV_INVOICESTATUS} carries the raw stored value
 * (Phase 2 does not port {@code wb_get_invoice_status}). Sorting on
 * {@code STATUS} therefore sorts by the stored-locale string, not by
 * a canonical logical state. Acceptable for Phase 2 — full localised
 * sort lands when the localisation strategy is chosen.
 */
public enum EInvoiceSortField implements SortableField {

    DATE_TIME ("issuDt"),
    INVOICE_NO("einvInvoicesymbol"),
    BUYER     ("trcoCmnm"),
    CURRENCY  ("trcoTrscCcyCd"),
    TOTAL     ("totlAmt"),
    STATUS    ("einvInvoicestatus"),
    TAX       ("taxAmtSum"),
    PRETAX    ("splyAmtSum");

    private final String entityProperty;

    EInvoiceSortField(String entityProperty) {
        this.entityProperty = entityProperty;
    }

    @Override
    public String entityProperty() {
        return entityProperty;
    }

    public static EInvoiceSortField parse(String fieldId) {
        if (fieldId == null) return null;
        String trimmed = fieldId.trim();
        if (trimmed.isEmpty()) return null;
        try {
            return valueOf(trimmed.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
