package com.kosign.wecms.domain.payment.validation.dto;

/**
 * One blocked-chars rule returned by
 * {@code GET /api/v1/payment/blocked-chars}. Wire shape is
 * lowercase snake_case via the global Jackson naming strategy.
 *
 * <p>The frontend joins {@code field_cd} against its
 * {@code FieldCd} dictionary to know which column header gets the
 * tooltip; {@code blocked_chars} is the literal set of characters
 * (e.g. {@code "<>'\"@$&"}) the upload + display tables surface
 * verbatim inside the tooltip.
 */
public record BlockedCharsRuleRow(
        /** Field code: RMKS / RCPT / MEMO / BENA / BEAD. */
        String fieldCd,
        /** Raw set of blocked characters — NOT a regex. */
        String blockedChars) { }
