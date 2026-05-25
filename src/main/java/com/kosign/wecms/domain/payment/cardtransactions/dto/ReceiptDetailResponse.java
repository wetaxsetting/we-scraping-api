package com.kosign.wecms.domain.payment.cardtransactions.dto;

import java.math.BigDecimal;

/**
 * Read-only detail of a manually-registered receipt or GDT-synced
 * bill. Backs the View Detail action on the linked-doc cell of the
 * Card Transactions table (mirrors legacy
 * {@code card_0001_001.ts:337-350} `.detail-receipt` / `.detail-e-invoice`
 * which opened {@code prch_0011_001} or {@code einv_0002_001} in
 * read-only mode).
 *
 * <p>Returns the full {@code BUSA_DOC_MSTR} row for the requested
 * doc, plus the resolved purpose label from
 * {@code USAG_INFM_LANG_DETL} in the caller's
 * {@code Accept-Language}. Tenant-scoped — wrong-tenant / missing
 * docs surface as {@code WBE_NOT_FOUND}.
 */
public record ReceiptDetailResponse(
        String     docId,
        /** Display-friendly id. Falls back to {@link #docId} when the
         *  row carries no {@code DISP_DOC_ID}. */
        String     dispDocId,
        /** {@code 'B'} Bill (e-Invoice) · {@code 'E'} Expense (manual
         *  receipt). Drives the dialog title server-side. */
        String     docType,
        /** {@code YYYYMMDD}. For receipts the {@code USE_DT} column;
         *  for bills the {@code ISSU_DT} column. */
        String     date,
        String     contactName,
        String     taxId,
        BigDecimal amount,
        String     ccyCd,
        /** {@code BUSA_USAG_CD} raw code. */
        String     purposeCd,
        /** Resolved name from {@code USAG_INFM_LANG_DETL} per
         *  {@code Accept-Language}; falls back to EN, then to the
         *  code itself. Null when the doc carries no purpose. */
        String     purposeNm,
        String     description) {
}
