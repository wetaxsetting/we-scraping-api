package com.kosign.wecms.domain.setting.purposesettings.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Settings &gt; Purpose master row. Mirrors legacy JEX
 * {@code wabooks.entity.BusiUsagSetInfm} and the
 * {@code BUSI_USAG_SET_INFM} table column-for-column (15 columns, V80
 * migration; PK verified on DEV 2026-05-12 via {@code postgres} MCP).
 *
 * <p>Refactor scope is rows where {@code usag_grp_cd IS NULL} — the
 * "level-2 hierarchy" bucket per legacy
 * {@code comm_0187_001_r001_act.jsp:50}. Legacy aux columns
 * ({@link #usagGrpCd}, {@link #usagType}, {@link #reptDv},
 * {@link #reptGrp}) stay mapped for schema parity but the refactor
 * service never writes them.
 *
 * <p><b>Audit columns:</b> the legacy table has ONLY
 * {@link #modId} / {@link #modDtm} (no {@code REG_ID} / {@code REG_DTM}).
 * This entity therefore does NOT extend
 * {@link com.kosign.wecms.common.entity.BaseEntity} — that base would
 * try to write four columns the schema doesn't carry. The
 * {@code MOD_*} pair is written explicitly by the service on Create /
 * Update / Reorder. See feature spec §11 OQ-Backend-1.
 *
 * <p><b>{@link #usagType}</b> is {@code bpchar(2)} on disk (CHAR(2));
 * entity-side {@link JdbcTypeCode @JdbcTypeCode(SqlTypes.CHAR)} aligns
 * Hibernate's default {@code varchar} mapping so
 * {@code spring.jpa.hibernate.ddl-auto=validate} would accept the
 * column — same V38 "direction locked" rule as
 * {@code TbFirmConf.fileType}.
 *
 * <p><b>Tenant scoping:</b> {@link #bizKey} is part of the composite
 * PK and is the first predicate of every repository query. Cross-
 * tenant access cannot succeed by accident.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "BUSI_USAG_SET_INFM")
@IdClass(BusiUsagSetId.class)
public class BusiUsagSetInfm {

    /** Tenant key. PK component. */
    @Id
    @Column(name = "BIZ_KEY", length = 20, nullable = false)
    private String bizKey;

    /** System purpose code — 10-digit zero-padded, scoped per tenant.
     *  Generated server-side via the ported {@code wb_get_next_val}
     *  function (see {@code PurposeSettingsRepository.nextUseUsagCd}).
     *  PK component. */
    @Id
    @Column(name = "USE_USAG_CD", length = 20, nullable = false)
    private String useUsagCd;

    /** Active flag — {@code 'Y'} live, {@code 'N'} soft-deleted. */
    @Column(name = "USE_YN", length = 1, nullable = false)
    private String useYn;

    /** Display flag — controls whether the purpose appears in payment
     *  dropdowns. {@code 'Y'}/{@code 'N'}. */
    @Column(name = "DISP_YN", length = 1, nullable = false)
    private String dispYn;

    /** Per-tenant display order. Nullable on the schema (DEV parity)
     *  but always written by the refactor on Create / Reorder.
     *
     *  <p>The legacy column type is {@code NUMERIC} (unbounded — verified
     *  on DEV 2026-05-12); the refactor's V80 migration mirrors that
     *  for schema parity. {@link JdbcTypeCode @JdbcTypeCode(SqlTypes.NUMERIC)}
     *  aligns Hibernate's schema-validate against the {@code NUMERIC}
     *  column type while keeping the Java field as {@link Integer} —
     *  in practice the values are always small positive integers
     *  ({@code MAX(disp_ord)+1} within a per-tenant scope). Same
     *  pattern as {@code TbFirmConf.fileType} for the CHAR(3)
     *  vs VARCHAR mismatch. Resolves OQ-DB-5 — no corrective migration
     *  needed. */
    @JdbcTypeCode(SqlTypes.NUMERIC)
    @Column(name = "DISP_ORD")
    private Integer dispOrd;

    /** Login id of the user who last modified the row. */
    @Column(name = "MOD_ID", length = 100)
    private String modId;

    /** {@code yyyyMMddHHmmss} UTC string. Legacy format preserved on
     *  the column for schema parity. */
    @Column(name = "MOD_DTM", length = 14)
    private String modDtm;

    /** Legacy "usage group" bucket selector. Always {@code NULL} in
     *  the refactor scope. Kept on the entity so the schema-parity
     *  shape matches DEV; never set by service code. */
    @Column(name = "USAG_GRP_CD", length = 3)
    private String usagGrpCd;

    /** Legacy report division. Not consumed by the refactor. */
    @Column(name = "REPT_DV", length = 2)
    private String reptDv;

    /** Legacy report group. Not consumed by the refactor. */
    @Column(name = "REPT_GRP", length = 2)
    private String reptGrp;

    /** Legacy usage type — {@code bpchar(2)} on disk. Not consumed by
     *  the refactor; the JDBC-type hint matches the column. */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "USAG_TYPE", length = 2, columnDefinition = "CHAR(2)")
    private String usagType;

    /** User-visible purpose code (e.g. {@code "BD01"}, {@code "P01"}).
     *  Required at the application layer (legacy parity, see
     *  {@code comm_0187_001_c001_act.jsp:33}); the DB column allows
     *  NULL only to keep legacy rows importable. */
    @Column(name = "HAND_USE_USAG_CD", length = 20)
    private String handUseUsagCd;

    /** Self-reference to a level-1 row's {@link #useUsagCd}.
     *  {@code NULL} = level-1 row; non-null = level-2 row. */
    @Column(name = "PARENT_USE_USAG_CD", length = 20)
    private String parentUseUsagCd;

    /** Display name (e.g. {@code "Operating Expenses"}). Required at
     *  the application layer (legacy parity, see
     *  {@code comm_0187_001_c001_act.jsp:32}). */
    @Column(name = "USE_USAG_NM", length = 255)
    private String useUsagNm;

    /** Free-form description. */
    @Column(name = "USE_USAG_CONT", length = 500)
    private String useUsagCont;
}
