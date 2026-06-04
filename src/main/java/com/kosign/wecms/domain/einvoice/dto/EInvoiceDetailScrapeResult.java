package com.kosign.wecms.domain.einvoice.dto;

/**
 * Response of {@code POST /api/v1/addon/einvoices/scrape-detail-results}.
 *
 * @param detailSaved   master rows enriched + line items (re)written
 * @param detailUpdated reserved (0 today — detail-save is delete-then-insert)
 * @param detailSkipped detail rows with no matching phase-1 master row
 * @param lineItems     total {@code BUSA_DOC_DETL} line rows written
 * @param modDtm        14-digit timestamp stamped on the writes
 */
public record EInvoiceDetailScrapeResult(
        int detailSaved,
        int detailUpdated,
        int detailSkipped,
        int lineItems,
        String modDtm
) { }
