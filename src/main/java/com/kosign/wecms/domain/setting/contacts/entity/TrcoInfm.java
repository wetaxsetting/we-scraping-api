package com.kosign.wecms.domain.setting.contacts.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Settings &gt; Contacts &mdash; master contact row. Mirrors legacy
 * {@code WECMS DEV/WECMS/src/wabooks/entity/TrcoInfm.java} +
 * {@code IDO.TRCO_INFM_R016} column-for-column for the Sprint subset
 * the refactor reads / writes.
 *
 * <p><b>Primary key</b>: {@code TRCO_KEY} alone &mdash; verbatim from DEV
 * ({@code trco_infm_pk PRIMARY KEY (trco_key)}). {@code TRCO_KEY} is
 * globally unique across all tenants, drawn from {@code sq_trco_key}
 * (V85) via the legacy {@code wb_get_next_val('trco_infm','trco_key', &hellip;)}
 * branch. {@code BIZ_KEY} is therefore NOT part of the PK but stays as
 * the tenant-scope predicate on every query.
 *
 * <p><b>Sprint subset</b>: the JPA entity maps the columns the
 * Contacts feature reads / writes. Hibernate {@code validate} ignores
 * the rest of the DEV-parity columns the V84 schema carries (47 total).
 * Extend the mapping here in the MR that adds the first consumer of
 * any currently-unmapped column.
 *
 * <p>Tenant scoping is the caller's responsibility &mdash; every
 * repository method must constrain on {@code BIZ_KEY} from
 * {@link com.kosign.wecms.common.tenant.TenantContext#currentBizKey()}.
 *
 * <p>Audit columns ({@code REG_ID} / {@code REG_DTM} / {@code MOD_ID} /
 * {@code MOD_DTM}) follow the legacy {@code VARCHAR(14)} timestamp
 * shape, NOT the refactor's {@code BaseEntity} {@code OffsetDateTime}
 * shape &mdash; same precedent as {@code ErpTrnsHstr}. The service
 * stamps these explicitly on create / update.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "TRCO_INFM")
public class TrcoInfm {

    /** Global primary key &mdash; drawn from the legacy
     *  {@code sq_trco_key} sequence via {@code wb_get_next_val} (V85). */
    @Id
    @Column(name = "TRCO_KEY", length = 20, nullable = false)
    private String trcoKey;

    /** Tenant scope. Constraint enforced at the repository layer. */
    @Column(name = "BIZ_KEY", length = 20, nullable = false)
    private String bizKey;

    /** Tenant tax id snapshot (legacy {@code TAX_ID} column &mdash; not
     *  the contact's tax id which lives in {@link #trcoTaxId}). NOT NULL
     *  in DEV. */
    @Column(name = "TAX_ID", length = 40, nullable = false)
    private String taxId;

    /** Local-language company name (typically Vietnamese for VN tenants). */
    @Column(name = "TRCO_CMNM", length = 200, nullable = false)
    private String trcoCmnm;

    /** English company name. */
    @Column(name = "TRCO_CMENM", length = 200, nullable = false)
    private String trcoCmenm;

    /** Contact's tax id (different from the tenant's). Optional. */
    @Column(name = "TRCO_TAX_ID", length = 40)
    private String trcoTaxId;

    /** Contact type discriminator: {@code 'L'} = Local, {@code 'O'} = Overseas. */
    @Column(name = "TRCO_TYPE", length = 1)
    private String trcoType;

    /** Soft-delete sentinel: {@code 'C'} = trashed, {@code NULL} = active.
     *  {@code @JdbcTypeCode(CHAR)} so Hibernate maps the Java
     *  {@code String} to the DEV-parity {@code CHAR(1)} column instead
     *  of {@code VARCHAR}. Same pattern as
     *  {@code ErpTrnsHstr.erpType}. */
    @Column(name = "TRCO_STTS", length = 1)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String trcoStts;

    /** Transaction currency. Pinned to {@code 'VND'} or {@code 'USD'} by
     *  the {@code trco_infm_chk01} CHECK constraint. NOT NULL in DEV. */
    @Column(name = "TRSC_CCY_CD", length = 3, nullable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String trscCcyCd;

    /** Optional contact code (user-supplied identifier). The legacy
     *  list filter requires {@code HAND_TRCO_KEY IS NOT NULL} to
     *  surface a row, so unmanaged contacts are hidden by default. */
    @Column(name = "HAND_TRCO_KEY", length = 20)
    private String handTrcoKey;

    /** Optional nickname &mdash; participates in {@code trco_infm_uk01}
     *  UNIQUE({@code BIZ_KEY}, {@code TRCO_NICK_NM}). */
    @Column(name = "TRCO_NICK_NM", length = 200)
    private String trcoNickNm;

    /** Inquiry flag. OQ-3 resolved 2026-05-12: keep the field and
     *  default to {@code 'Y'} on create for legacy parity. Service-
     *  internal only &mdash; never exposed on the wire. TODO drop once
     *  a cross-tree code audit confirms no JEX inquiry / search /
     *  Payment / Sales path still reads it. */
    @Column(name = "TRCO_INQ_YN", length = 1, nullable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String trcoInqYn;

    /** Audit &mdash; create user. Legacy {@code VARCHAR(100)}. */
    @Column(name = "REG_ID", length = 100, nullable = false)
    private String regId;

    /** Audit &mdash; create UTC timestamp as {@code yyyyMMddHHmmss}. */
    @Column(name = "REG_DTM", length = 14, nullable = false)
    private String regDtm;

    /** Audit &mdash; last-modify user. Nullable on DEV (zero history). */
    @Column(name = "MOD_ID", length = 100)
    private String modId;

    /** Audit &mdash; last-modify UTC timestamp. Nullable on DEV. */
    @Column(name = "MOD_DTM", length = 14)
    private String modDtm;
}
