package com.kosign.wecms.common.memo.dto;

import java.math.BigDecimal;

/**
 * One memo row on the side-panel list. {@code reg_dtm} is shipped
 * verbatim {@code yyyyMMddHHmmss} so the frontend can render relative
 * timestamps without a second round-trip. Shared across every
 * memo-enabled screen (Card Transactions, Transaction History VND/FCY).
 */
public record MemoRow(
        BigDecimal seqNo,
        String     memo,
        /** Legacy sticker token
         *  ({@code fileG/fileB/fileY/fileW/fileR/fileP}). Null is
         *  treated as {@code fileW}. */
        String     color,
        String     regId,
        /** Registrant display name (resolved server-side via
         *  {@code USER_INFM}). Falls back to {@link #regId} when the
         *  user row is missing. */
        String     regNm,
        String     regDtm,
        /** True when the current request user is the memo's author
         *  (drives the per-row Edit / Delete affordance on the panel). */
        boolean    canEdit) {
}
