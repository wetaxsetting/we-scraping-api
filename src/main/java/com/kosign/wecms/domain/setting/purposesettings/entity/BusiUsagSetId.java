package com.kosign.wecms.domain.setting.purposesettings.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/**
 * Composite identity for {@link BusiUsagSet}. Mirrors the legacy JEX PK
 * on {@code BUSI_USAG_SET_INFM}: {@code (BIZ_KEY, USE_USAG_CD)}.
 *
 * <p>{@code bizKey} is part of the PK so tenant scoping is implicit on
 * every {@code findById} call — callers read {@code bizKey} from
 * {@link com.kosign.wecms.common.tenant.TenantContext#currentBizKey()}
 * and a cross-tenant lookup naturally returns empty (no row leak).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class BusiUsagSetId implements Serializable {
    private String bizKey;
    private String useUsagCd;
}
