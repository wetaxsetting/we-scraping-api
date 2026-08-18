package com.kosign.wecms.domain.setting.zalooa.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /api/v1/settings/zalo-oa/{oa_id}/authorize} — step two:
 * exchange the one-time consent code for the first token pair.
 *
 * <p>The frontend receives {@code oa_id} and {@code code} on Zalo's redirect to
 * the callback page and forwards them here with the user's JWT attached. The
 * redirect target is a <b>frontend</b> route, so this endpoint is a normal
 * authenticated API call rather than a public webhook — no unauthenticated
 * surface is added by this feature.
 *
 * <p><b>Sent as a body, not a query string.</b> The reference implementation
 * passes both values on the query string, which writes a live authorization code
 * into access logs, browser history and any proxy in between. A code is
 * single-use, so the exposure window is short but not zero.
 *
 * @param code Zalo authorization code. Single-use — worthless once exchanged, and
 *             an exchange attempt with a spent code fails at Zalo.
 */
public record ZaloAuthorizeRequest(

        @NotBlank
        @Size(max = 255)
        String code
) {
    public ZaloAuthorizeRequest {
        code = code == null ? null : code.trim();
    }
}
