package com.kosign.wecms.domain.payment.cardtransactions.dto;

import java.math.BigDecimal;

/**
 * Payment &gt; Card Transactions &mdash; one e-Invoice row eligible to
 * be linked to a card transaction. Surfaced on the
 * "Link Card Payments with the Expenses" popup's
 * <i>Expenses e-Invoice</i> tab (legacy {@code card_0001_002_r001}).
 *
 * <p>Sourced from {@code BUSA_DOC_MSTR} (the GDT e-Invoice ledger),
 * filtered to:
 * <ul>
 *   <li>BILL documents auto-synced from GDT ({@code DOC_TYPE='B'} +
 *       {@code HAND_REG_YN='N'})</li>
 *   <li>not already linked to any receive / payment record
 *       ({@code BUSA_DOC_RCV_PAY_HIS} NOT EXISTS)</li>
 *   <li>not Rejected / Voided ({@code PROV_STTS NOT IN ('EE','VD')})</li>
 *   <li>{@code ISSU_DT} inside the caller's date range</li>
 * </ul>
 *
 * <p>Wire shape (Jackson SNAKE_CASE): {@code doc_id} / {@code issu_dt} /
 * {@code disp_doc_id} / {@code einv_invoicestatus} / {@code merch_nm} /
 * {@code merch_tax_id} / {@code totl_amt} / {@code ccy_cd}.
 */
public record LinkableEInvoiceRow(
        /** Primary key — used when the user picks rows to link. */
        String     docId,
        /** Issue date {@code YYYYMMDD}. */
        String     issuDt,
        /** Display-friendly document id (e.g. {@code 1C25MGA-4510332}). */
        String     dispDocId,
        /** GDT invoice status — Original / Adjustment / Replacement /
         *  Cancelled. Localised verbatim on the wire — the frontend
         *  renders the raw string for now (Phase B will localise). */
        String     einvInvoicestatus,
        /** Merchant name — vi / en fallback chain
         *  ({@code TRCO_CMNM} else {@code TRCO_CMENM}). */
        String     merchNm,
        /** Merchant tax id ({@code MST:} sub-line in Figma). */
        String     merchTaxId,
        BigDecimal totlAmt,
        String     ccyCd) {
}
