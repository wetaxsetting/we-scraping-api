package com.kosign.wecms.domain.weinvoice.dto;

import java.util.List;

/**
 * A provider of the catalogue (UI Spec {@code Provider}, screen 16).
 *
 * @param linkStatus   {@code ok}, {@code check} (catalogue review note) or {@code missing} (no lookup link)
 * @param invoiceCount invoices of the user's MSTs issued through this provider
 * @param autoPdf      the engine has an adapter that fetches the original PDF
 */
public record ProviderItem(
        int stt,
        String taxId,
        String name,
        String fullName,
        String web,
        String lookupUrl,
        String captchaUrl,
        List<String> altLookupUrls,
        String verify,
        String linkStatus,
        long invoiceCount,
        boolean autoPdf) {
}
