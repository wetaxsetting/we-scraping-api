package com.kosign.wecms.domain.weinvoice.partner;

import com.kosign.wecms.domain.einvoice.dto.ScrapedEInvoiceDetailRow;

import java.util.List;

/**
 * Detail rows in the exact shape WeCMS posts to {@code /api/v1/addon/einvoices/scrape-detail-results}
 * today, plus the keys WeInvoice does not hold.
 */
public record PartnerDetailResponse(List<ScrapedEInvoiceDetailRow> rows, List<PartnerDetailRequest.Key> notFound) {
}
