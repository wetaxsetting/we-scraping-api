package com.kosign.wecms.domain.payment.cardtransactions.dto;

import java.math.BigDecimal;

/**
 * One memo row on the side-panel list. {@code reg_dtm} is shipped
 * verbatim {@code yyyyMMddHHmmss} so the frontend can render relative
 * timestamps without a second round-trip.
 */
public record MemoRow(
        BigDecimal seqNo,
        String     memo,
        /** Legacy {@code wabooks.enums.ColorCd} value
         *  ({@code white / blue / green / yellow / orange / red /
         *  gray / purple}). Null is treated as {@code white}. */
        String     color,
        String     regId,
        /** Registrant display name (resolved server-side via
         *  {@code USER_INFM}). Falls back to {@link #regId} when the
         *  user row is missing. */
        String     regNm,
        String     regDtm,
        /** True when the current request user is the memo's author
         *  (drives the per-row Delete affordance on the side panel). */
        boolean    canEdit) {
}
