package com.kosign.wecms.domain.setting.zalooa.dto;

import java.math.BigDecimal;

/**
 * Outcome of one send attempt.
 *
 * <p><b>A rejected send still returns HTTP 200 with {@code success=false}</b>,
 * rather than an error envelope. Zalo declining a message — recipient never
 * interacted with the OA, wallet out of credit, template not approved — is a
 * documented business outcome, and this repo maps those to 200 by design (see the
 * error-handling contract in {@code CLAUDE.md}: the frontend reads
 * {@code status.error}, not the HTTP code). It also means the caller gets
 * {@link #sendSeq} either way and can open the logged row.
 *
 * @param sendSeq    primary key of the {@code ZALO_SEND_HIS} row written for this
 *                   attempt. Always present — the row is written on failure too.
 * @param success    whether Zalo accepted the message ({@code error = 0})
 * @param rsltCd     Zalo's error code as text, or an HTTP status / local marker
 * @param rsltMsg    Zalo's message, or a local description
 * @param trackingId the {@code tracking_id} sent to Zalo, for correlating a future
 *                   delivery report
 */
public record ZaloSendResponse(
        BigDecimal sendSeq,
        boolean success,
        String rsltCd,
        String rsltMsg,
        String trackingId
) {
}
