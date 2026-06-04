package com.kosign.wecms.domain.einvoice.service;

import com.kosign.wecms.common.web.SortableField;

/**
 * Sort whitelist for the e-Invoice detail-history popup — mirrors the
 * legacy {@code SortHelper.getSortDynamic("nxisas_einvhis_dt_r001")}
 * whitelist ({@code SortHelper.java:2271-2299}). Constant names match the
 * legacy sort keys; the mapped value is the matching JPA property on
 * {@link com.kosign.wecms.domain.einvoice.entity.BusaDocMstr}.
 *
 * <p>Legacy default ORDER BY is {@code issu_dt:desc} — see
 * {@link EInvoiceHistoryService#detailHistory}.
 *
 * <p>Legacy applies {@code collate "C"} on the text columns
 * ({@code TRCO_CMNM}, {@code einv_invoiceStatus}); the refactor relies on
 * the DB's default collation instead (a documented minor deviation,
 * consistent with the other refactor sort whitelists).
 */
public enum EInvoiceDetailHistorySortField implements SortableField {

    DOC_ID("docId"),
    ISSU_DT("issuDt"),
    TRCO_CMNM("trcoCmnm"),
    TOTL_AMT("totlAmt"),
    EINV_INVOICESTATUS("einvInvoicestatus");

    private final String entityProperty;

    EInvoiceDetailHistorySortField(String entityProperty) {
        this.entityProperty = entityProperty;
    }

    @Override
    public String entityProperty() {
        return entityProperty;
    }

    /**
     * Lenient lookup for the wire {@code FIELD_ID}. Returns {@code null}
     * for unknown / blank values so the service falls back to the legacy
     * default ORDER BY rather than rejecting the request. The legacy
     * {@code einv_invoiceStatus} key upper-cases cleanly to the
     * {@code EINV_INVOICESTATUS} constant.
     */
    public static EInvoiceDetailHistorySortField parse(String fieldId) {
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
