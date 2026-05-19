package com.kosign.wecms.domain.payment.cardtransactions.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/**
 * Composite identity for {@link CardTrnsHstr}. Mirrors the legacy JEX
 * PK on {@code CARD_TRNS_HSTR}: {@code (BIZ_KEY, CARD_SEQ_NO)}.
 *
 * <p>Both components are {@code String} — same precedent as
 * {@code ChartOfAccountId}. Tenant scoping is part of the PK
 * ({@code bizKey} component) — every repository lookup that builds an
 * {@code Id} reads {@code bizKey} from
 * {@link com.kosign.wecms.common.tenant.TenantContext#currentBizKey()},
 * never from the request body.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class CardTrnsHstrId implements Serializable {

    /** Tenant scope. */
    private String bizKey;

    /** Per-tenant card-transaction surrogate. {@code VARCHAR(20)} numeric
     *  token on DEV — carried as {@code String} to match the column shape
     *  (scraper-allocated; the refactor never writes this column). */
    private String cardSeqNo;
}
