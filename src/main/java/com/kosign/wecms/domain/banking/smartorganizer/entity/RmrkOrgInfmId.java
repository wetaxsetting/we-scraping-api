package com.kosign.wecms.domain.banking.smartorganizer.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/**
 * Composite identity for {@link RmrkOrgInfm}. Mirrors DEV's PK on
 * {@code RMRK_ORG_INFM}: {@code (BIZ_KEY, RMRK_SEQ_NO)}.
 *
 * <p>Tenant scoping is part of the PK — every repository lookup that
 * builds an {@code Id} pulls {@code bizKey} from
 * {@link com.kosign.wecms.common.tenant.TenantContext}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class RmrkOrgInfmId implements Serializable {
    private String bizKey;
    private Long rmrkSeqNo;
}
