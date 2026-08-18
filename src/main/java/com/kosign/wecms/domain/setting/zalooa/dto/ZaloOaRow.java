package com.kosign.wecms.domain.setting.zalooa.dto;

import java.util.List;

/**
 * One Official Account connected by the current tenant.
 *
 * <p><b>No token value is ever on the wire.</b> {@code ACCESS_TOKEN} /
 * {@code REFRESH_TOKEN} stay server-side; the panel needs only to know whether
 * the handshake completed ({@link #authorized}) and when the current access token
 * lapses ({@link #tokenExpiDtm}). Shipping either token to a browser would put a
 * live credential in devtools, page source, and any client-side log.
 *
 * @param oaId          Zalo Official Account ID. Immutable — half the primary key.
 * @param oaNm          display name; not used when sending
 * @param active        {@code true} = outbound messages go through this OA. At most
 *                      one per tenant, enforced by {@code zalo_oa_infm_uk01}.
 * @param authorized    whether the OAuth handshake completed. A row exists from the
 *                      moment the tenant submits an OA id, but tokens only arrive
 *                      after they approve in the Zalo popup, so {@code false} is a
 *                      normal intermediate state and the panel should show it as
 *                      "needs authorising", not as an error.
 * @param tokenExpiDtm  access-token expiry as the raw 14-char
 *                      {@code yyyyMMddHHmmss} UTC value, or {@code null} before the
 *                      first successful exchange. Format at render.
 * @param regDtm        raw 14-char connection timestamp
 * @param templates     templates registered against this OA; empty until an admin
 *                      records them
 */
public record ZaloOaRow(
        String oaId,
        String oaNm,
        boolean active,
        boolean authorized,
        String tokenExpiDtm,
        String regDtm,
        List<ZaloTemplateRow> templates
) {
}
