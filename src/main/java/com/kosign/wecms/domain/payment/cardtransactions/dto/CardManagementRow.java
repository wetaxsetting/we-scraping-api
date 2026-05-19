package com.kosign.wecms.domain.payment.cardtransactions.dto;

/**
 * One row in the Card Management Setting modal — legacy
 * {@code card_0001_003_r001} envelope's REC sub-row, narrowed to the
 * fields the modal actually renders (the legacy {@code CARD_MSTR_R004}
 * IDO ships 26 columns; the refactor projection drops 17 that the UI
 * never reads).
 *
 * <p>{@code mod_dtm} is the raw legacy {@code yyyyMMddHHmmss} string —
 * the frontend formats it as "X minutes ago" / "DD-MMM-YYYY HH:mm"
 * per locale (mirrors legacy {@code dateTimeFormatViewGdtVn} +
 * {@code moment.from} in {@code card_0001_003.ts:241-255}).
 */
public record CardManagementRow(
        String bankCd,
        String bankNm,
        String cardNo,
        String cardNm,
        String cardNickNm,
        String cardHldrNm,
        /** {@code Y} active / {@code N} inactive. */
        String actvYn,
        String modId,
        String modDtm) {
}
