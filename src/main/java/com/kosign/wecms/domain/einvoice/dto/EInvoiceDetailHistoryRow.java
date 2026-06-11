package com.kosign.wecms.domain.einvoice.dto;

import java.math.BigDecimal;

/**
 * One row of the e-Invoice "Latest Inquiry — Detail" popup — refactor
 * port of the legacy {@code nxisas_einvhis_dt_r001} REC shape
 * ({@code WSVC.nxisas_einvhis_dt_r001.xml}).
 *
 * <p>The popup drills into a single GDT inquiry batch: given the batch
 * {@code MOD_DTM} (the scrape timestamp) + the {@code DOC_TYPE} +
 * issue-date window, it lists every {@code BUSA_DOC_MSTR} invoice that
 * batch produced. Columns mirror the legacy WSVC subrule verbatim.
 *
 * <p>Wire shape lowercase snake_case via the global Jackson strategy
 * ({@code doc_id}, {@code issu_dt}, {@code trco_cmnm}, {@code totl_amt},
 * {@code einv_invoice_status}, {@code hand_reg_yn}, {@code disp_doc_id}).
 */
public record EInvoiceDetailHistoryRow(

        /** Internal document id (legacy {@code DOC_ID}). */
        String docId,

        /** Issue date {@code YYYYMMDD} (legacy {@code ISSU_DT}). */
        String issuDt,

        /** Counterparty company name (legacy {@code TRCO_CMNM}). */
        String trcoCmnm,

        /** Total amount (legacy {@code TOTL_AMT}). */
        BigDecimal totlAmt,

        /** Raw {@code EINV_INVOICESTATUS} — locale-resolved on the legacy
         *  backend; the refactor returns it verbatim (rule IR-4) and the
         *  frontend colours it via the shared status matcher. */
        String einvInvoiceStatus,

        /** Hand-registered flag {@code 'Y'} / {@code 'N'} (legacy
         *  {@code HAND_REG_YN}). The popup query only returns {@code 'N'}
         *  rows, but the column rides through so the frontend can pick
         *  {@code DISP_DOC_ID} vs {@code DOC_ID} exactly as legacy does. */
        String handRegYn,

        /** Display-friendly document id (legacy {@code DISP_DOC_ID} =
         *  {@code numericSymbol+invoiceSymbol-invoiceNo}). */
        String dispDocId
) { }
