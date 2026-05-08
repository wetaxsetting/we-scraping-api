package com.kosign.wecms.domain.einvoice.dto;

/**
 * Wire row for the Sales / Bill e-Invoice listing.
 *
 * <p>{@code INV_TYPE} is deliberately NOT exposed (rule IR-7 in
 * {@code docs/refactor-wecms/planning/addon/sales-e-invoices-plan.md}):
 * the legacy WSVC drops it, and the values rendered as the static UI's
 * "invoice type" sub-line ({@code Original} / {@code Replacement} /
 * {@code Adjustment}) are actually values of {@code EINV_INVOICESTATUS},
 * not {@code INV_TYPE} (which is a legacy {@code 'C'} = Commercial /
 * else = Tax classifier).
 *
 * <p>{@code issu_tm} is a refactor-only proxy sliced from
 * {@code REG_DTM[8..14]} — legacy doesn't expose any issue-time field.
 * Kept for the static-UI handoff; UI policy decision (drop / relabel /
 * source from another column) is deferred per IR-9.
 *
 * Wire names auto-derived from camelCase Java fields via the global
 * SNAKE_CASE Jackson naming strategy (e.g.
 * {@code einvInvoicesymbol} → {@code einv_invoicesymbol}).
 */
public record EInvoiceRow(
        String docId,
        String dispDocId,
        String issuDt,
        String issuTm,
        String einvInvoicesymbol,
        String trcoCmnm,
        String trcoTaxId,
        String trcoTrscCcyCd,
        String totlAmt,
        String splyAmtSum,
        String taxAmtSum,
        String einvInvoicestatus,
        String einvInvoicestatusBef,
        String provStts,
        String dueDt,
        String overDueDays
) { }
