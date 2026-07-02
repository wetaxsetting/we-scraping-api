package com.kosign.wecms.domain.payment.cardtransactions.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * One row in the card-transactions ledger response. Mirrors the legacy
 * {@code REC} sub-rule of {@code WSVC.card_0001_001_r001.xml} with
 * three refactor adjustments:
 *
 * <ul>
 *   <li>{@code stts_key} (sibling to the raw {@code stts} code) carries
 *       the i18n key the frontend resolves via {@code t(...)}. Replaces
 *       the legacy {@code wb_get_prch_stts_nm} server-side label
 *       rendering — see backend doc §3.4.</li>
 *   <li>{@code linked_docs[]} / {@code approvers[]} are Phase B
 *       placeholders — service emits empty lists until the linked-doc
 *       and approver-chain entities land. See feature doc §11 OQ-1.</li>
 *   <li>{@code bank_nm} is resolved server-side via
 *       {@code BankCdResolver} rather than the legacy {@code
 *       wb_get_bank_nm} DB function (refactor stance, backend doc §3.4).</li>
 * </ul>
 *
 * <p>Wire serialisation is lowercase snake_case via the global Jackson
 * naming strategy ({@code config/JacksonConfig}); Java field names stay
 * camelCase per the wire-naming standard in
 * {@code wecms-api-main/CLAUDE.md} §"API Naming &amp; Serialization Standards".
 */
public record CardTransactionRow(
        String           cardSeqNo,
        String           bankCd,
        String           bankNm,
        String           cardNo,
        String           cardNm,
        String           cardNickNm,
        String           cardHldrNm,
        String           merchNm,
        String           ccyCd,
        BigDecimal       trscAmt,
        String           trscDt,
        String           trscTm,
        String           cardTrnsUsageCode,
        String           chartAcctSeqNo,
        String           chartAcctCd,
        String           cont,
        String           apprSeqNo,
        /** {@code EXPT_RESOL_MSTR.DOC_NO} (e.g. {@code "VO_260521_001"})
         *  for the approval the row was submitted under. Null when the
         *  row carries no {@code APPR_SEQ_NO} (yellow / not requested).
         *  The frontend's Approval Ref. column renders this instead of
         *  the raw numeric {@code APPR_SEQ_NO}. */
        String           apprDocNo,
        String           stts,
        String           sttsKey,
        List<LinkedDocRef> linkedDocs,
        List<ApproverRef>  approvers,
        String           regId,
        String           regDtm,
        String           modId,
        String           modDtm,
        /* ---- legacy CARD_TRNS_HSTR_R002 detail fields (GAP-CARD-FIELDS) ----
         * Surfaced for the Approval Details popup card grid so the refactor
         * reaches legacy parity. All read straight off the entity (the
         * columns were already mapped); none are new schema. CARD_TRNS_USAGE_NM
         * (the resolved usage label) is intentionally NOT included — it needs
         * the legacy wb_get_usag_nm resolver / a USAG_INFM join; deferred as a
         * PO decision. The raw cardTrnsUsageCode above is shipped meanwhile. */
        String           usingType,
        String           authCode,
        String           city,
        /** {@code CARD_TRNS_HSTR.NOTE} — distinct from {@code CONT}
         *  (the free-form description). Both ship on the wire. */
        String           note,
        /** Row-level memo sticker color — the legacy {@code getMemoPopup2}
         *  token ({@code fileG/fileB/fileY/fileW/fileR/fileP}) of this
         *  card's memo thread, from {@code MEMO_INFM} scoped by
         *  {@code (BIZ_KEY, WEB_SVC_ID, REF_NO=CARD_SEQ_NO)}. Null when the
         *  row has no memo — the grid then renders the default empty icon. */
        String           memoColor,
        /** Number of memos on this card transaction (0 = none). Drives the
         *  memo-icon presence on the list grid. */
        int              memoCount) {
}
