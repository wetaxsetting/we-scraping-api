package com.kosign.wecms.domain.einvoice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * One decrypted GDT invoice DETAIL as scraped by the local NXiSAS engine
 * — mirrors the legacy engine {@code invoiceDetail[0]} element + the
 * {@code nxisas_einvoice_c002} REC_IN shape.
 *
 * <p>The identity fields ({@code numericSymbol} / {@code invoiceSymbol} /
 * {@code invoiceNo} / {@code sellerTaxCode} / {@code einvTestResults})
 * let the service recompose the master {@code DOC_ID} and locate the row
 * written by phase 1; the {@code products} / {@code fees} / {@code taxes}
 * arrays are persisted to {@code BUSA_DOC_DETL} / {@code EINV_FEE_DETL} /
 * {@code EINV_TAX_DETL}.
 *
 * <p>Decrypt boundary (OQ-S2): same stance as {@link ScrapedEInvoiceRow}
 * — the engine decrypts client-side; the backend never bundles Coocon
 * {@code ISASSeedCBC}. Wire shape lowercase snake_case (global Jackson
 * strategy).
 */
public record ScrapedEInvoiceDetailRow(

        @Size(max = 5)   String numericSymbol,
        @Size(max = 10)  String invoiceSymbol,
        @Size(max = 50)  String invoiceNo,
        @Size(max = 40)  String sellerTaxCode,
        @Size(max = 1)   String einvTestResults,

        @Size(max = 3)   String currencyCd,
        @Size(max = 500) String invoiceTitle,
        @Size(max = 500) String sellerName,
        @Size(max = 40)  String totalAmt,
        @Size(max = 40)  String totalAmtWithoutTax,
        @Size(max = 40)  String totalTaxAmt,
        @Size(max = 40)  String totalDiscount,

        /* ---- Full master enrichment (Scope 3) — raw engine
         * invoiceDetail fields. The backend resolves issuer vs
         * counterparty by EINV_TYPE (legacy c002:210-218). ---- */
        @Size(max = 500)  String buyerName,
        @Size(max = 40)   String buyerTaxCode,
        @Size(max = 2000) String buyerAddr,
        @Size(max = 200)  String buyerBankAcc,
        @Size(max = 200)  String buyerBankName,
        @Size(max = 20)   String buyerCitizenId,
        @Size(max = 2000) String sellerAddr,
        @Size(max = 200)  String sellerBankAcc,
        @Size(max = 200)  String sellerBankName,
        @Size(max = 40)   String sellerPhone,
        @Size(max = 50)   String mccqt,
        @Size(max = 200)  String originalInvoice,
        @Size(max = 50)   String paymentType,
        @Size(max = 50)   String listNumber,
        @Size(max = 50)   String listDate,
        @Size(max = 40)   String totalFee,
        @Size(max = 200)  String totalAmtInLetter,
        @Size(max = 200)  String buyerSignBy,
        @Size(max = 20)   String buyerSignDate,
        @Size(max = 20)   String buyerSignTime,
        @Size(max = 200)  String sellerSignBy,
        @Size(max = 20)   String sellerSignDate,
        @Size(max = 20)   String sellerSignTime,
        @Size(max = 2000) String qrCode,
        @Size(max = 40)   String rate,

        @Valid List<Product> products,
        @Valid List<Fee> fees,
        @Valid List<Tax> taxes
) {

    /** One line item — engine {@code productArr[]} → {@code BUSA_DOC_DETL}. */
    public record Product(
            int dispOrd,
            @Size(max = 500) String productName,
            @Size(max = 500) String product,
            @Size(max = 90)  String unit,
            @Size(max = 40)  String unitPrice,
            @Size(max = 40)  String quantity,
            @Size(max = 40)  String supplyAmt,
            @Size(max = 10)  String taxRate,
            @Size(max = 40)  String discount
    ) { }

    /** One fee line — engine {@code feeArr[]} → {@code EINV_FEE_DETL}. */
    public record Fee(
            int dispOrd,
            @Size(max = 100) String feeName,
            @Size(max = 40)  String fee
    ) { }

    /** One tax-rate line — engine {@code taxArr[]} → {@code EINV_TAX_DETL}. */
    public record Tax(
            int dispOrd,
            @Size(max = 50) String taxRate,
            @Size(max = 40) String amtWithoutTax,
            @Size(max = 40) String taxAmt
    ) { }
}
