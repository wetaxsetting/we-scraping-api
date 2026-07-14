package com.kosign.wecms.domain.einvoice.dto;

/**
 * Response of the GDT e-Invoice engine-payload decrypt endpoint.
 *
 * <p>Carries the same {@code NXISAS_DATA} array shape the request sent,
 * but with every {@code Output.Result} SEED-CBC-decrypted and replaced by
 * the parsed JSON object (mirroring legacy {@code nxisas_decrypt_act.jsp}
 * which does {@code outputData.put("Result", JSONParser.parser(decResult))}).
 * The frontend {@code JSON.parse}s this string and feeds it to the
 * existing {@code extractScrapedInvoices} / {@code extractScrapedDetails}
 * parsers.
 *
 * @param nxisasData the decrypted {@code NXISAS_DATA} JSON array string.
 */
public record EInvoiceDecryptResponse(
        String nxisasData
) { }
