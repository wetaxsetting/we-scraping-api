package com.kosign.wecms.domain.setting.zalooa.dto;

import jakarta.validation.constraints.Size;

/**
 * Body of {@code PUT /api/v1/settings/zalo-oa/{oa_id}} — rename a connection
 * and/or make it the tenant's sending OA.
 *
 * <p><b>{@code active} is a radio choice, not a toggle.</b> Setting it
 * {@code true} stands every other connection of the tenant down in the same
 * transaction, because {@code zalo_oa_infm_uk01} permits exactly one live row per
 * tenant. The panel should render it accordingly — independent switches would let
 * a user construct a request the database rejects.
 *
 * @param oaNm   new display name. {@code null} leaves it unchanged.
 * @param active {@code true} = make this the sending OA; {@code false} = stand it
 *               down. {@code null} leaves it unchanged, so a rename does not have
 *               to restate the activation state.
 */
public record ZaloOaUpdateRequest(

        @Size(max = 100)
        String oaNm,

        Boolean active
) {
    public ZaloOaUpdateRequest {
        oaNm = oaNm == null ? null : oaNm.trim();
    }
}
