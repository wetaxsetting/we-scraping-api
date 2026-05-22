package com.kosign.wecms.domain.payment.cardtransactions.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Payment &gt; Card Transactions &mdash; Create Receipt request body.
 * Mirrors the legacy {@code prch_0011_001} "New Receipt" popup. Writes
 * one {@code BUSA_DOC_MSTR} row with {@code DOC_TYPE='E'} +
 * {@code HAND_REG_YN='Y'} (a manually-entered Expense / Receipt).
 *
 * <p>Two contact modes:
 * <ul>
 *   <li><b>Contacts mode</b> — the user picked a row from the
 *       {@code TRCO_INFM} dropdown. {@code trcoKey} is the picked
 *       {@code TRCO_KEY}; {@code contactName} mirrors the merchant
 *       display name; {@code taxId} echoes the picked
 *       {@code TRCO_TAX_ID}; {@code ccyCd} echoes the picked
 *       {@code TRSC_CCY_CD}.</li>
 *   <li><b>Manual mode</b> — {@code trcoKey} stays null; the legacy
 *       sentinel {@code "99999999999999999999"} is filled in
 *       server-side. {@code contactName} / {@code taxId} are free
 *       text typed in the popup.</li>
 * </ul>
 *
 * <p>{@code purposeCd} carries the {@code USE_USAG_CD} picked from the
 * Purpose dropdown (USAG_GRP_CD='6' filter); the display name is NOT
 * shipped — the read path resolves the localized label via
 * {@code USAG_INFM_LANG_DETL}.
 */
public record CreateReceiptRequest(
        /** Use date {@code yyyyMMdd}. */
        @NotNull @Pattern(regexp = "^[0-9]{8}$")
        String useDt,
        /** {@code TRCO_INFM.TRCO_KEY} when picked from the Contacts
         *  dropdown; null in Manual mode. */
        @Size(max = 20)
        String trcoKey,
        /** Display name typed (manual) or echoed from the picked
         *  contact (Contacts mode). */
        @NotBlank @Size(max = 200)
        String contactName,
        /** Tax id — auto-filled from picked contact in Contacts mode,
         *  free text in Manual mode. */
        @Size(max = 40)
        String taxId,
        /** Total amount (decimal string on the wire). */
        @NotNull @PositiveOrZero
        BigDecimal amount,
        /** Currency code — picked from contact in Contacts mode,
         *  defaults to {@code "VND"} when blank. */
        @Size(max = 3)
        String ccyCd,
        /** {@code USAG_INFM.USE_USAG_CD} picked from the Purpose
         *  dropdown. Null when no purpose picked. */
        @Size(max = 20)
        String purposeCd,
        /** Optional description (legacy {@code BUSA_DOC_MSTR.CONT}). */
        String description) {
}
