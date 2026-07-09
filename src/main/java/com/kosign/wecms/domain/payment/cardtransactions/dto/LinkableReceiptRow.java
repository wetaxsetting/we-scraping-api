package com.kosign.wecms.domain.payment.cardtransactions.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Payment &gt; Card Transactions &mdash; one manually-registered
 * receipt eligible to be linked to a card transaction. Surfaced on the
 * "Link Card Payments with the Expenses" popup's <i>Receipt</i> tab
 * (legacy {@code card_0001_002_r002}).
 *
 * <p>Sourced from {@code BUSA_DOC_MSTR} ({@code DOC_TYPE='E'} +
 * {@code HAND_REG_YN='Y'} — hand-entered expense rows). Same exclusion
 * rules as the e-Invoice sibling: not Rejected / Voided, not already
 * linked.
 *
 * <p>Wire shape (Jackson SNAKE_CASE): {@code doc_id} / {@code use_dt} /
 * {@code merch_nm} / {@code merch_tax_id} / {@code totl_amt} /
 * {@code ccy_cd} / {@code purpose} / {@code purpose_nm} /
 * {@code description}.
 */
public record LinkableReceiptRow(
        String     docId,
        /** Receipt use date {@code YYYYMMDD} (NOT issue date — legacy
         *  r002 files manual receipts by USE_DT). */
        String     useDt,
        /** Merchant name — {@code TRCO_CMNM} else {@code TRCO_CMENM}. */
        String     merchNm,
        /** Merchant/contact tax id ({@code BUSA_DOC_MSTR.TRCO_TAX_ID}) —
         *  the {@code MST : …} sub-line in Figma. Same source the e-Invoice
         *  list ({@code LinkableEInvoiceRow.merchTaxId}) reads, off the same
         *  {@code BUSA_DOC_MSTR} row; no extra join. Null when the receipt
         *  carries no contact tax id. Wire: {@code merch_tax_id}. */
        String     merchTaxId,
        BigDecimal totlAmt,
        String     ccyCd,
        /** Raw {@code BUSA_USAG_CD} purpose code. */
        String     purpose,
        /** Localized purpose name resolved from {@code USAG_INFM} +
         *  {@code USAG_INFM_LANG_DETL} (group {@code 6}) for the caller's
         *  locale — reuses the same lookup the New Receipt picker + Receipt
         *  Detail use. Null when {@code purpose} has no active group-6 entry;
         *  the frontend then renders the code alone. Wire: {@code purpose_nm}. */
        String     purposeNm,
        String     description,
        /** Attachment count for the paperclip badge (audit G3/G5). */
        int          fileCount,
        /** Attachment list for the paperclip dropdown (`BUSA_DOC_FILE`);
         *  empty when none. Wire: {@code files}. */
        List<ReceiptFileRow> files) {
}
