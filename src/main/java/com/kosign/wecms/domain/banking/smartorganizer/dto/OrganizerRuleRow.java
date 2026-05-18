package com.kosign.wecms.domain.banking.smartorganizer.dto;

/**
 * One Smart Organizer rule on the wire (Organizer Settings list +
 * detail responses). Mirrors {@code RMRK_ORG_INFM} row shape exactly
 * — caller deserialises into in-memory camelCase semantic names per
 * the frontend's translation-at-the-boundary rule.
 *
 * <p>Wire keys are lowercase snake_case via the global Jackson naming
 * strategy.
 */
public record OrganizerRuleRow(
        Long rmrkSeqNo,
        String rcvPayDv,
        String rmrk,
        String matchType,
        Integer dispOrd,
        String trcoKey,
        String useUsagCd,
        Long vasAcctSeqNo,
        String chartAcctSeqNo,
        String chartAcctCd,
        String regId,
        String regDtm
) {
}
