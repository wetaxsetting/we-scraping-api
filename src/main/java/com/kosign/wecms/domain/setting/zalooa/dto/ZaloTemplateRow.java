package com.kosign.wecms.domain.setting.zalooa.dto;

/**
 * One Zalo-approved template bound to an Official Account, as returned to the
 * Settings panel. Wire shape is lowercase snake_case via the global Jackson
 * naming strategy.
 *
 * @param tmplType message-type code — see {@code ZaloTemplateType}. Provisional
 *                 until the WeCMS message catalogue is agreed.
 * @param langCd   {@code VI} / {@code EN} / {@code KO} / {@code KM}, matching
 *                 {@code common.enums.LangCd}
 * @param tmplId   the id Zalo issued on approval, e.g. {@code "358731"}
 * @param unitPrc  Zalo's per-message charge as a plain decimal <b>string</b>, not
 *                 a JSON number: the column is {@code NUMERIC(20,6)} and a
 *                 JavaScript number cannot hold that precision without silently
 *                 rounding. {@code null} when not recorded. Format at render.
 * @param active   {@code false} parks the template — the send path skips it
 */
public record ZaloTemplateRow(
        String tmplType,
        String langCd,
        String tmplId,
        String unitPrc,
        boolean active
) {
}
