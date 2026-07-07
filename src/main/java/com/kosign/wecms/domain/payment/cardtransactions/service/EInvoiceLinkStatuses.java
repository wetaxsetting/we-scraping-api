package com.kosign.wecms.domain.payment.cardtransactions.service;

import java.util.List;

/**
 * e-Invoice link eligibility by GDT invoice status (audit finding L1).
 *
 * <p>Legacy {@code card_0001_002_r001_act.jsp} only excluded documents by
 * {@code PROV_STTS ∈ {EE,VD}} and already-linked presence — it did <b>not</b>
 * filter on {@code EINV_INVOICESTATUS}, so a Cancelled / Replaced / Replacement
 * / Adjustment invoice could still be linked to a card payment. Per the PO
 * decision on the link-reference audit, only <b>Original</b> invoices (and
 * status-less rows) may be linked; every "superseded / cancelled" status is
 * excluded on both the list and the save path.
 *
 * <p>The stored {@code EINV_INVOICESTATUS} values are the canonical Vietnamese
 * strings (verified on JEX DEV — the {@code wb_get_invoice_status(value, lang)}
 * function localises them for display). The mapping is:
 * <ul>
 *   <li>{@code "Hóa đơn mới"} → Original — <b>linkable</b></li>
 *   <li>{@code "Hóa đơn đã bị hủy"} → Cancelled — excluded</li>
 *   <li>{@code "Hóa đơn thay thế"} → Replacement — excluded</li>
 *   <li>{@code "Hóa đơn điều chỉnh"} → Adjustment — excluded</li>
 *   <li>{@code "Hóa đơn đã bị thay thế"} → Replaced — excluded</li>
 *   <li>{@code "Hóa đơn đã bị điều chỉnh"} → Adjusted — excluded</li>
 *   <li>{@code null} / blank → treated as Original — linkable (preserves the
 *       pre-fix behaviour for the ~200 status-less scraped bills)</li>
 * </ul>
 *
 * <p>An exclude-list (rather than an Original-only allow-list) is used so an
 * unknown future status stays linkable exactly as it is today, and only the
 * five explicitly-superseded states are removed.
 */
public final class EInvoiceLinkStatuses {

    private EInvoiceLinkStatuses() {
    }

    /** Stored {@code EINV_INVOICESTATUS} values that must NOT be linkable. */
    public static final List<String> INVALID_LINK_STATUSES = List.of(
            "Hóa đơn đã bị hủy",        // Cancelled
            "Hóa đơn thay thế",         // Replacement
            "Hóa đơn điều chỉnh",       // Adjustment
            "Hóa đơn đã bị thay thế",   // Replaced
            "Hóa đơn đã bị điều chỉnh"  // Adjusted
    );

    /**
     * @return {@code true} when a document with this raw stored status may be
     *         linked to a card payment (Original / status-less), {@code false}
     *         for the five superseded / cancelled states.
     */
    public static boolean isLinkable(String einvInvoicestatus) {
        if (einvInvoicestatus == null || einvInvoicestatus.isBlank()) {
            return true;
        }
        return !INVALID_LINK_STATUSES.contains(einvInvoicestatus.trim());
    }
}
