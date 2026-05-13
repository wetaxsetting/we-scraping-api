package com.kosign.wecms.domain.setting.chartofaccounts.entity;

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
 * Settings &gt; Chart of Accounts &mdash; master row. Mirrors legacy
 * JEX {@code wabooks.entity.ChartAcctInfm} and the
 * {@code CHART_ACCT_INFM} table column-for-column (V94 migration).
 *
 * <p>Sprint scope: full CRUD over a single table (5 endpoints). No
 * child entity, no FK declared in DEV — the parent/child relationship
 * is purely value-based on {@code (CHART_ACCT_CD, PARENT_CHART_ACCT_CD)}
 * within the same tenant. Walking the hierarchy is done in Java by the
 * {@code ChartOfAccountsTreeBuilder} service helper.
 *
 * <p>Audit columns ({@code REG_ID} / {@code REG_DTM} / {@code MOD_ID}
 * / {@code MOD_DTM}) follow the legacy {@code VARCHAR(14)}
 * {@code yyyyMMddHHmmss} timestamp shape, NOT the refactor's
 * {@code BaseEntity} {@code OffsetDateTime} shape &mdash; same
 * precedent as {@code ApprovalLine}, {@code TrcoInfm} and
 * {@code ErpTrnsHstr}. The service stamps these explicitly on
 * Create / Update.
 *
 * <p>{@code CHART_ACCT_SEQ_NO} is mapped as {@code String} end-to-end
 * (matches DEV's {@code VARCHAR(14)} shape). The legacy
 * {@code wb_get_next_val('chart_acct_infm','chart_acct_seq_no', biz_key)}
 * branch (V95) returns a numeric-text token; the service treats it as
 * opaque. No JPA converter, no auto-parse — keeps the storage layer
 * simple.
 *
 * <p>{@code STTS} / {@code DEL_YN} / {@code CAL_RULE} are
 * {@code CHAR(1)} on DEV. {@link JdbcTypeCode @JdbcTypeCode(SqlTypes.CHAR)}
 * tells Hibernate to map them via the JDBC {@code CHAR} type so the
 * schema validator accepts {@code bpchar(1)} against a Java
 * {@code String} field — same shape as the V38 {@code sort_yn} hotfix
 * on {@code SysScrOptionWecms}.
 *
 * <p>Tenant scoping: {@code BIZ_KEY} is part of the composite PK and
 * is the first predicate of every repository query.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "CHART_ACCT_INFM")
@IdClass(ChartOfAccountId.class)
public class ChartOfAccount {

    /** Tenant scope. PK component. */
    @Id
    @Column(name = "BIZ_KEY", length = 20, nullable = false)
    private String bizKey;

    /** Per-tenant surrogate. PK component. Allocated server-side via
     *  {@code legacyFnRepo.nextChartAcctSeqNo(bizKey)} (V95 branch on
     *  {@code wb_get_next_val}, per-tenant MAX+1). Carried as
     *  {@code String} end-to-end — DEV's shape is {@code VARCHAR(14)}. */
    @Id
    @Column(name = "CHART_ACCT_SEQ_NO", length = 14, nullable = false)
    private String chartAcctSeqNo;

    /** User-facing account code. NOT NULL. Per-tenant unique among
     *  non-deleted rows via {@code uk_chart_acct_cd
     *  (BIZ_KEY, CHART_ACCT_CD, DEL_YN)}. The 3-column UK is the only
     *  thing that lets a tenant carry both an active and a
     *  soft-deleted row with the same code — the
     *  ChartOfAccountsService Create flow exploits this to "revive"
     *  a deleted row instead of inserting a new one. */
    @Column(name = "CHART_ACCT_CD", length = 20, nullable = false)
    private String chartAcctCd;

    /** Account name (English). NOT NULL. Trimmed at the service
     *  layer to mirror legacy {@code StringUtils.sanitize}. */
    @Column(name = "CHART_ACCT_NM_EN", length = 255, nullable = false)
    private String chartAcctNmEn;

    /** Account name (Vietnamese). Nullable on DEV. */
    @Column(name = "CHART_ACCT_NM_VN", length = 255)
    private String chartAcctNmVn;

    /** Account name (Korean). Nullable on DEV. */
    @Column(name = "CHART_ACCT_NM_KO", length = 255)
    private String chartAcctNmKo;

    /** Free-form account type (Asset / Liability / Revenue / Expense
     *  / Equity in concept). Unused in DEV — every row has NULL.
     *  Kept in the entity for forward compatibility. */
    @Column(name = "CHART_ACCT_TYPE", length = 100)
    private String chartAcctType;

    /** Parent's {@code CHART_ACCT_CD} within the same tenant.
     *  Nullable — {@code NULL} ⇒ root. No FK declared on DEV;
     *  ChartOfAccountsValidator verifies the parent exists (active,
     *  same tenant) at write time. */
    @Column(name = "PARENT_CHART_ACCT_CD", length = 20)
    private String parentChartAcctCd;

    /** {@code A} Active / {@code I} Inactive. CHAR(1) on DEV. */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "STTS", length = 1, nullable = false)
    private String stts;

    /** Soft-delete flag: {@code Y} ⇒ deleted, {@code N} ⇒ active.
     *  DB DEFAULT {@code 'N'}. The hard {@code DELETE} IDO from DEV is
     *  never used in legacy or refactor flows — every delete in
     *  ChartOfAccountsService is a {@code DEL_YN='Y'} patch. */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "DEL_YN", length = 1, nullable = false)
    private String delYn;

    /** Audit — create user. Legacy {@code VARCHAR(100)} NOT NULL. */
    @Column(name = "REG_ID", length = 100, nullable = false)
    private String regId;

    /** Audit — create UTC timestamp as {@code yyyyMMddHHmmss}. NOT NULL. */
    @Column(name = "REG_DTM", length = 14, nullable = false)
    private String regDtm;

    /** Audit — last-modify user. Legacy {@code VARCHAR(100)} NOT NULL. */
    @Column(name = "MOD_ID", length = 100, nullable = false)
    private String modId;

    /** Audit — last-modify UTC timestamp. Legacy {@code VARCHAR(14)} NOT NULL. */
    @Column(name = "MOD_DTM", length = 14, nullable = false)
    private String modDtm;

    /** Sign-aggregation rule used by downstream financial reports.
     *  {@code P} (+) / {@code M} (-) / {@code null}. CHAR(1) on DEV. */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "CAL_RULE", length = 1)
    private String calRule;
}
