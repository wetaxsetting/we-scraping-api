package com.kosign.wecms.domain.payment.cardtransactions.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Payment &gt; Card Transactions &mdash; Update Receipt request body.
 * Refactor counterpart of legacy {@code WABOOKS_MREC_C004} (mobile
 * "Edit Receipt"); the web had no update path at all, which is why this
 * arrived with the mobile receipt channel
 * ({@code docs/features/payment/mobile-receipt-channel/}).
 *
 * <p>Full replace, not a patch: the mobile edit screen posts every field
 * it owns, so a null here means "clear it", the same semantics the
 * create path has. The fields mirror {@link CreateReceiptRequest} one for
 * one — deliberately, so the app can reuse its create payload builder —
 * minus {@code useDt}'s counterpart in the identity columns
 * ({@code DOC_ID} is the path variable, and {@code DOC_TYPE} /
 * {@code HAND_REG_YN} / {@code REG_CHNL} are immutable after create).
 *
 * <p>What this endpoint will refuse (see the service's guard):
 * a document that is not a hand-registered receipt, one already attached
 * to an expense approval, and one owned by another user.
 */
public record UpdateReceiptRequest(
        /** Use date {@code yyyyMMdd}. */
        @NotNull @Pattern(regexp = "^[0-9]{8}$")
        String useDt,
        /** {@code TRCO_INFM.TRCO_KEY} when picked from the Contacts
         *  dropdown; null keeps the legacy manual-mode sentinel. */
        @Size(max = 20)
        String trcoKey,
        /** Display name typed (manual) or echoed from the picked contact. */
        @NotBlank @Size(max = 200)
        String contactName,
        /** Tax id — free text in manual mode. */
        @Size(max = 40)
        String taxId,
        /** Total amount. Same bounds as the create path: strictly positive,
         *  and inside {@code BUSA_DOC_MSTR.TOTL_AMT NUMERIC(20,6)}. */
        @NotNull @Positive @Digits(integer = 14, fraction = 6)
        BigDecimal amount,
        /** Currency code; defaults to {@code "VND"} when blank. */
        @Size(max = 3)
        String ccyCd,
        /** {@code USAG_INFM.USE_USAG_CD} picked from the Purpose dropdown. */
        @Size(max = 20)
        String purposeCd,
        /** Optional description (legacy {@code BUSA_DOC_MSTR.CONT}). */
        String description) {
}
