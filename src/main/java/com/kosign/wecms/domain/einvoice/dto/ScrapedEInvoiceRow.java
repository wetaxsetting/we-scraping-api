package com.kosign.wecms.domain.einvoice.dto;

import jakarta.validation.constraints.Size;

/**
 * One decrypted GDT invoice as scraped by the local NXiSAS engine —
 * mirrors a single element of the engine's {@code invoiceList[]}
 * (legacy {@code nxisas_einvoice_c001_act.jsp:142-271}).
 *
 * <p><b>Decrypt boundary (OQ-S2):</b> the legacy flow posts the
 * SEED-CBC-encrypted engine {@code Result} and decrypts it server-side
 * via {@code com.coocon.securty.util.ISASSeedCBC}. The refactor does NOT
 * bundle the Coocon ISAS library (same stance as
 * {@code OatNxisasService.passthrough}); instead the browser/engine
 * provides the already-decrypted {@code invoiceList} rows and posts them
 * here as structured fields — the canonical "browser scraped, POST
 * verbatim, server re-validates + persists" pattern
 * ({@code ScrapingResultIntakeRequest}). No cipher is invented on the
 * backend.
 *
 * <p>Amount fields ride as strings exactly as the engine emits them
 * ({@code "1000000"}); the service parses to {@code BigDecimal}.
 *
 * <p>Wire shape is lowercase snake_case via the global Jackson naming
 * strategy ({@code invoiceNo} → {@code invoice_no}).
 */
public record ScrapedEInvoiceRow(

        /** First numeric-symbol char — engine {@code numericSymbol}.
         *  Part of the composed DOC_ID. */
        @Size(max = 5)   String numericSymbol,

        /** Invoice symbol — engine {@code invoiceSymbol}. */
        @Size(max = 10)  String invoiceSymbol,

        /** Raw GDT invoice number — engine {@code invoiceNo}
         *  (legacy {@code HAND_DOC_ID}). */
        @Size(max = 50)  String invoiceNo,

        /** Issue date {@code YYYYMMDD} — engine {@code invoiceDate}. */
        @Size(max = 8)   String invoiceDate,

        /** Total incl. tax — engine {@code totalAmt}. */
        @Size(max = 40)  String totalAmt,

        /** Supply amount (excl. tax) — engine {@code totalAmtWithoutTax}. */
        @Size(max = 40)  String totalAmtWithoutTax,

        /** VAT — engine {@code totalTaxAmt}. */
        @Size(max = 40)  String totalTaxAmt,

        /** Discount — engine {@code totalDiscount}. */
        @Size(max = 40)  String totalDiscount,

        /** GDT invoice status phrase — engine {@code invoiceStatus}
         *  (drives PROV_STTS OP/VD). */
        @Size(max = 100) String invoiceStatus,

        /** GDT check result phrase — engine {@code checkResult}
         *  (drives accept/skip + branch selection). */
        @Size(max = 100) String checkResult,

        /** Full invoice info blob — engine {@code invoiceInfo}. */
        @Size(max = 500) String invoiceInfo,

        /** Seller tax code — engine {@code sellerTaxCode}
         *  (appended to DOC_ID for expense / received-without-code rows). */
        @Size(max = 40)  String sellerTaxCode,

        /** Symbol-type marker — {@code ''} (C), {@code '1'} (K),
         *  {@code '2'} (POS). Legacy {@code einv_testResults}. */
        @Size(max = 1)   String einvTestResults,

        /** Optional transaction currency (legacy {@code TRCO_TRSC_CCY_CD});
         *  engine may omit. */
        @Size(max = 3)   String currencyCd,

        /** Optional seller/buyer company name when the engine detail
         *  carries it (legacy {@code TRCO_CMNM}). */
        @Size(max = 200) String trcoCmnm
) { }
