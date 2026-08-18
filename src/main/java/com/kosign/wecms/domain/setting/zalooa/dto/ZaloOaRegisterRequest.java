package com.kosign.wecms.domain.setting.zalooa.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /api/v1/settings/zalo-oa} — step one of the connection
 * flow: record the OA id so the row exists <b>before</b> the tenant is sent to
 * Zalo's consent popup.
 *
 * <p>The order matters. Zalo redirects back with {@code oa_id} + {@code code},
 * and the completion call looks the connection up by {@code (BIZ_KEY, OA_ID)}; if
 * no row was registered first, that lookup fails and the authorization code — a
 * single-use value — is already spent. The reference implementation has the same
 * ordering requirement and reports it as error {@code CLIENT_ID_NOT_FOUND}.
 *
 * @param oaId Zalo Official Account ID. Digits only: Zalo OA ids are numeric, and
 *             the value becomes half a primary key, so a typo'd id with stray
 *             whitespace or punctuation would create a permanently unusable row.
 * @param oaNm display label for the settings list. Optional — {@code null} falls
 *             back to the id in the UI.
 */
public record ZaloOaRegisterRequest(

        @NotBlank
        @Size(max = 50)
        @Pattern(regexp = "\\d+", message = "oa_id must be numeric")
        String oaId,

        @Size(max = 100)
        String oaNm
) {
    /**
     * Trim on construction so a pasted id with trailing whitespace does not become
     * a distinct primary key from the same id typed by hand.
     */
    public ZaloOaRegisterRequest {
        oaId = oaId == null ? null : oaId.trim();
        oaNm = oaNm == null ? null : oaNm.trim();
    }
}
