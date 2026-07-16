package com.kosign.wecms.domain.einvoice.dto;

import java.util.List;

/**
 * Full single-invoice document for the "View e-Invoice Details" popup —
 * refactor of the legacy {@code einv_0002_001_r001} response (shared by
 * Sales and Expenses). See
 * {@code docs/jex/features/view-e-invoice-details.md}.
 *
 * <p><b>Seller / buyer resolution is done server-side</b> so the frontend
 * renders {@code seller.*} / {@code buyer.*} verbatim (JEX audit §5):
 * <ul>
 *   <li>Company identity (name / tax id / address / phone) <b>swaps</b> by
 *       {@code doc_type} — {@code 'I'} (sale): own company (ISSU_*) is the
 *       seller; {@code 'B'} (bill): own company is the buyer.</li>
 *   <li>Bank account, buyer full name / citizen id, and the seller signature
 *       block come from dedicated {@code einv_seller*} / {@code einv_buyer*}
 *       columns and are <b>not</b> swapped.</li>
 * </ul>
 *
 * <p>Amounts ship as raw plain-decimal strings (frontend formats via
 * {@code lib/currency.ts}); dates/times ship as the raw legacy strings
 * ({@code issu_dt} = {@code YYYYMMDD}; sign date/time = {@code YYYYMMDD} /
 * {@code HHmmss}) — the frontend formats them exactly as the list does.
 *
 * <p>Wire names are lowercase snake_case via the global Jackson strategy.
 */
public record EInvoiceDetailResponse(
        String docId,
        String docType,
        String invoiceTitle,
        String numericSymbol,
        String invoiceSymbol,
        String handDocId,
        String invoiceStatus,
        String mccqt,
        String qrcode,
        String originalInvoice,
        String issuDt,
        String currencyCd,
        String rate,
        String listNumber,
        String listDate,
        String paymentType,
        String testResult,
        Party seller,
        Party buyer,
        List<ItemRow> items,
        List<FeeRow> fees,
        List<TaxRow> taxes,
        Totals totals
) {

    /** One party block. {@code fullName} / {@code citizenId} are only
     *  populated for the buyer; {@code signBy} / {@code signDate} /
     *  {@code signTime} only for the seller (matching the legacy invoice
     *  layout). Unused slots are {@code null}. */
    public record Party(
            String name,
            String taxId,
            String address,
            String phone,
            String fullName,
            String citizenId,
            String bankAcc,
            String bankName,
            String signBy,
            String signDate,
            String signTime
    ) { }

    /** Line item (legacy {@code REC} / {@code BUSA_DOC_DETL}). */
    public record ItemRow(
            Integer dispOrd,
            String itemNm,
            String productName,
            String itemSpec,
            String itemQunt,
            String itemUnitAmt,
            String dcAmt,
            String taxRate,
            String splyAmt
    ) { }

    /** Fee line (legacy {@code REC2} / {@code EINV_FEE_DETL}). */
    public record FeeRow(
            Integer dispOrd,
            String feeName,
            String fee
    ) { }

    /** Tax-rate breakdown line (legacy {@code REC3} / {@code EINV_TAX_DETL}). */
    public record TaxRow(
            String taxRate,
            String amt,
            String taxAmt
    ) { }

    /** Invoice totals block. */
    public record Totals(
            String splyAmtSum,
            String taxAmtSum,
            String totlFeeAmt,
            String totlDisAmt,
            String totlAmt,
            String totalAmtInLetter
    ) { }
}
