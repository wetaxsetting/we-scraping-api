package com.kosign.wecms.domain.setting.zalooa.dto;

import java.math.BigDecimal;

/**
 * One outbound attempt, as shown in the send-history list.
 *
 * <p>The reference implementation stores only the request and response blobs, so
 * support cannot answer "did this recipient get the message, under which
 * template?" without parsing JSON out of a log table. The columns needed to answer
 * it are first-class here.
 *
 * <p>{@code REQ_DATA} / {@code RES_DATA} are deliberately <b>not</b> on this row:
 * they hold the full payload including template parameters, which can carry
 * customer names and amounts. The list is a support surface, not an export of
 * customer data.
 *
 * @param sendSeq    row id
 * @param rcvrNo     recipient phone, exactly as sent
 * @param tmplId     template id used
 * @param tmplType   message-type code, {@code null} if the send failed before
 *                   template resolution
 * @param langCd     language variant, {@code null} in the same case
 * @param success    whether Zalo accepted it
 * @param rsltCd     Zalo error code as text ({@code "0"} = success), or an HTTP
 *                   status / local failure marker
 * @param rsltMsg    Zalo's message, or a local description
 * @param trackingId correlation id sent to Zalo
 * @param reqDtm     raw 14-char {@code yyyyMMddHHmmss} request time
 */
public record ZaloSendHistoryRow(
        BigDecimal sendSeq,
        String rcvrNo,
        String tmplId,
        String tmplType,
        String langCd,
        boolean success,
        String rsltCd,
        String rsltMsg,
        String trackingId,
        String reqDtm
) {
}
