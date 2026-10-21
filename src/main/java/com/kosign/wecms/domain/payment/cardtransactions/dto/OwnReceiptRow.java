package com.kosign.wecms.domain.payment.cardtransactions.dto;

import java.math.BigDecimal;

/**
 * One of the caller's own hand-registered receipts, as returned by
 * {@code GET /api/v1/payment/card-transactions/receipts}. Refactor
 * counterpart of legacy {@code WABOOKS_MREC_R006} (mobile receipt list) —
 * see {@code docs/features/payment/mobile-receipt-channel/}.
 *
 * <p>Sibling of {@link LinkableReceiptRow}, but a different question:
 * that one answers "which receipts may I still attach to this card
 * transaction", this one answers "what have I captured". So it stays
 * scoped to one {@code USER_ID}, keeps already-linked receipts in the
 * list, and carries the two state flags the app needs to decide whether
 * to offer an Edit button.
 *
 * <p>Wire shape (Jackson SNAKE_CASE): {@code doc_id} /
 * {@code disp_doc_id} / {@code use_dt} / {@code merch_nm} /
 * {@code merch_tax_id} / {@code totl_amt} / {@code ccy_cd} /
 * {@code purpose} / {@code purpose_nm} / {@code description} /
 * {@code reg_chnl} / {@code appr_seq_no} / {@code file_count} /
 * {@code editable}.
 */
public record OwnReceiptRow(
        String     docId,
        /** Human-facing document number; falls back to {@code docId}. */
        String     dispDocId,
        /** Receipt use date {@code YYYYMMDD}, falling back to issue date. */
        String     useDt,
        /** Merchant name — {@code TRCO_CMNM} else {@code TRCO_CMENM}. */
        String     merchNm,
        String     merchTaxId,
        BigDecimal totlAmt,
        String     ccyCd,
        /** Raw {@code BUSA_USAG_CD} purpose code. */
        String     purpose,
        /** Localized purpose name; null when the code has no active
         *  group-6 entry for the caller's locale. */
        String     purposeNm,
        /** {@code BUSA_DOC_MSTR.CONT}. */
        String     description,
        /** {@code 'MOB'} when captured on mobile, {@code 'PC'} on the web.
         *  Provenance only — the list is not filtered by it unless the
         *  caller asks. */
        String     regChnl,
        /** Expense-approval this receipt is attached to, null when free. */
        String     apprSeqNo,
        /** Number of attachments in {@code BUSA_DOC_FILE}. */
        int        fileCount,
        /** Server-computed: whether {@code PUT /receipts/{doc_id}} would be
         *  accepted right now. False once the receipt is attached to an
         *  approval. The app should hide its Edit action instead of
         *  discovering the refusal on submit. */
        boolean    editable) {
}
