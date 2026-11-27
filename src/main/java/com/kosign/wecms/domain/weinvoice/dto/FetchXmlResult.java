package com.kosign.wecms.domain.weinvoice.dto;

/**
 * {@code POST /invoices/fetch-missing-xml}.
 *
 * @param queued        coded invoices without XML handed to the engine
 * @param skippedNoCode invoices without code (symbol K…): GDT keeps no XML for them
 * @param skippedOther  already have XML, cancelled, or not visible
 * @param requestId     engine request, {@code null} when nothing was queued
 */
public record FetchXmlResult(int queued, int skippedNoCode, int skippedOther, String requestId) {
}
