package com.kosign.wecms.domain.einvoice.dto;

/**
 * Result of a scrape-intake call.
 *
 * @param savedCount   rows inserted into {@code BUSA_DOC_MSTR}
 * @param updatedCount existing rows updated (re-scrape of the same DOC_ID)
 * @param skippedCount engine rows skipped (checkResult not in the accepted set)
 * @param modDtm       the {@code YYYYMMDDHHmmss} stamp written on all rows
 */
public record EInvoiceScrapeResult(
        int savedCount,
        int updatedCount,
        int skippedCount,
        String modDtm
) { }
