package com.kosign.wecms.domain.setting.approvalline.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/**
 * Composite identity for {@link ApprovalLine}. Mirrors the legacy JEX
 * PK on {@code APPR_LINE_INFM}: {@code (BIZ_KEY, APPR_LINE_NO)}.
 *
 * <p>Tenant scoping is part of the PK ({@code bizKey} component) —
 * every repository lookup that builds an {@code Id} reads
 * {@code bizKey} from
 * {@link com.kosign.wecms.common.tenant.TenantContext#currentBizKey()},
 * never from the request body.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class ApprovalLineId implements Serializable {

    /** Tenant scope. */
    private String bizKey;

    /** Per-tenant surrogate. {@code Long} matches the V92-aligned
     *  {@code BIGINT} column on both {@code APPR_LINE_INFM} and
     *  {@code APPR_LINE_INFM_DETL}. */
    private Long apprLineNo;
}
