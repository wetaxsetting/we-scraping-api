package com.kosign.wecms.domain.setting.zalooa.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/**
 * Composite identity for {@link ZaloOaInfm}: {@code (BIZ_KEY, OA_ID)}.
 *
 * <p>Tenant scoping is part of the key — callers build this from
 * {@link com.kosign.wecms.common.tenant.TenantContext#currentBizKey()} plus the
 * Official Account id, never from a request body alone.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class ZaloOaInfmId implements Serializable {
    private String bizKey;
    private String oaId;
}
