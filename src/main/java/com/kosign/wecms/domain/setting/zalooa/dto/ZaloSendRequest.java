package com.kosign.wecms.domain.setting.zalooa.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * Body of {@code POST /api/v1/settings/zalo-oa/test-send}.
 *
 * <h2>Why this is a test-send and not the production send API</h2>
 *
 * <p>The recipient is supplied explicitly here. That is a deliberate limit, not a
 * shortcut: <b>who receives a Zalo message in WeCMS is still an open product
 * decision</b> (open decision 1 in
 * {@code docs/references/database/settings/zalo-oa-schema.md}). The reference
 * implementation sends to a <i>payer</i> of a <i>biller</i> — a two-level model
 * WeCMS has no equivalent of — so there is nothing to resolve a recipient from
 * yet.
 *
 * <p>Building a general "send to whoever this business event concerns" endpoint
 * before that is settled would mean guessing the model and then unpicking it. This
 * endpoint instead lets an admin verify the whole chain end to end — token
 * exchange, template resolution, Zalo call, send log — against their own phone.
 * When the catalogue lands, event-driven callers use
 * {@code ZaloMessageService.send(...)} directly and this endpoint stays as the
 * diagnostic it is.
 *
 * @param phone        recipient in E.164 digits, no {@code +} and no leading zero
 *                     — e.g. {@code 84912345678}. Zalo rejects the local
 *                     {@code 09…} form outright.
 * @param tmplType     which registered template to use
 * @param langCd       which language variant of it
 * @param templateData template parameter names → values. The names, types and
 *                     maximum lengths are fixed by the approved template; Zalo
 *                     rejects a missing required key or an over-length value, and
 *                     that rejection is recorded rather than hidden.
 */
public record ZaloSendRequest(

        @NotBlank
        @Size(max = 50)
        @Pattern(regexp = "\\d{8,20}", message = "phone must be E.164 digits without '+'")
        String phone,

        @NotBlank
        @Size(max = 20)
        String tmplType,

        @NotBlank
        @Size(max = 3)
        String langCd,

        @NotNull
        Map<String, String> templateData
) {
    public ZaloSendRequest {
        phone = phone == null ? null : phone.trim();
        tmplType = tmplType == null ? null : tmplType.trim().toUpperCase();
        langCd = langCd == null ? null : langCd.trim().toUpperCase();
    }
}
