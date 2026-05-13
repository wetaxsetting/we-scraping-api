package com.kosign.wecms.domain.setting.chartofaccounts.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/**
 * Composite identity for {@link ChartOfAccount}. Mirrors the legacy JEX
 * PK on {@code CHART_ACCT_INFM}: {@code (BIZ_KEY, CHART_ACCT_SEQ_NO)}.
 *
 * <p>Both components are {@code String} — {@code BIZ_KEY} is the legacy
 * tenant key shape, {@code CHART_ACCT_SEQ_NO} is the legacy
 * {@code VARCHAR(14)} per-tenant surrogate (the value happens to be a
 * decimal integer text token — e.g. {@code "70"} — but is carried as a
 * string end-to-end to match DEV and avoid AttributeConverter
 * complexity).
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
public class ChartOfAccountId implements Serializable {

    /** Tenant scope. */
    private String bizKey;

    /** Per-tenant surrogate. {@code VARCHAR(14)} numeric-text token —
     *  carried as {@code String} to match DEV's column shape. */
    private String chartAcctSeqNo;
}
