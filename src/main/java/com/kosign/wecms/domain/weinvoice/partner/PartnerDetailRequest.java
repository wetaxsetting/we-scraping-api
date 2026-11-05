package com.kosign.wecms.domain.weinvoice.partner;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * {@code POST /api/partner/v1/einvoices/details}: detail rows for invoices WeCMS already got from
 * the list call. Keys use the field names of {@code ScrapedEInvoiceRow}.
 *
 * @param type {@code SALES} or {@code EXPENSE} ({@code BILL} accepted as EXPENSE)
 */
public record PartnerDetailRequest(
        @NotBlank String taxId,
        @NotBlank String type,
        @NotEmpty @Size(max = 500) List<@Valid Key> keys) {

    public record Key(
            @NotBlank @Size(max = 5) String numericSymbol,
            @NotBlank @Size(max = 10) String invoiceSymbol,
            @NotBlank @Size(max = 50) String invoiceNo,
            @NotBlank @Size(max = 40) String sellerTaxCode) {
    }
}
