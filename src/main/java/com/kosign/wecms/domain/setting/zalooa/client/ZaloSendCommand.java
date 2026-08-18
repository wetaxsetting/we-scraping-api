package com.kosign.wecms.domain.setting.zalooa.client;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * Request body for {@code POST business.openapi.zalo.me/message/template}.
 *
 * <p><b>Vendor contract — explicit {@code @JsonProperty} on every field.</b> This
 * is an external integration shape, not the WeCMS wire, so it follows the same
 * rule as the Coocon / NXiSAS gateway DTOs: the key names must match Zalo's
 * documentation verbatim and must not depend on the global {@code SNAKE_CASE}
 * naming strategy. If someone reconfigures {@code JacksonConfig}, this body still
 * serialises correctly.
 *
 * @param phone        recipient in E.164 digits with no {@code +} — e.g.
 *                     {@code 84912345678}. Zalo rejects local {@code 0…} form.
 * @param templateId   the id Zalo issued when it approved the template
 * @param templateData template parameter names → values. Names, types and max
 *                     lengths are fixed by the approved template; a missing
 *                     required key or an over-length value is rejected by Zalo,
 *                     not by us.
 * @param trackingId   correlation id we generate. Zalo echoes it, and it is
 *                     persisted in {@code ZALO_SEND_HIS.TRACKING_ID} so a future
 *                     delivery webhook can match a report back to its send.
 */
public record ZaloSendCommand(
        @JsonProperty("phone") String phone,
        @JsonProperty("template_id") String templateId,
        @JsonProperty("template_data") Map<String, String> templateData,
        @JsonProperty("tracking_id") String trackingId
) {
}
