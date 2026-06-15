package com.kosign.wecms.domain.setting.approvalline.entity;

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
 * Settings &gt; Approval Line &mdash; approval-line master row. Mirrors
 * legacy JEX {@code wabooks.entity.ApprLineInfm} and the
 * {@code APPR_LINE_INFM} table column-for-column (V90 migration).
 *
 * <p>Sprint scope: master + child CRUD (5 endpoints). The child
 * collection is intentionally NOT mapped as a JPA {@code @OneToMany} —
 * the diff-sync algorithm in {@code ApprovalLineService.update(...)}
 * loads the existing child set via
 * {@code ApprovalLineApproverRepository.findAllByBizKeyAndApprLineNoOrderByApprvrDispOrdAsc}
 * and walks it explicitly. Decoupling the two repositories keeps the
 * unit of work small and matches the legacy DAO pattern.
 *
 * <p>Audit columns ({@code REG_ID} / {@code REG_DTM} / {@code MOD_ID}
 * / {@code MOD_DTM}) follow the legacy {@code VARCHAR(14)}
 * {@code yyyyMMddHHmmss} timestamp shape, NOT the refactor's
 * {@code BaseEntity} {@code OffsetDateTime} shape &mdash; same
 * precedent as {@code TrcoInfm} and {@code ErpTrnsHstr}. The service
 * stamps these explicitly on Create / Update.
 *
 * <p>Tenant scoping: {@code BIZ_KEY} is part of the composite PK
 * and is the first predicate of every repository query.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "APPR_LINE_INFM")
@IdClass(ApprovalLineId.class)
public class ApprovalLine {

    /** Tenant scope. PK component. */
    @Id
    @Column(name = "BIZ_KEY", length = 20, nullable = false)
    private String bizKey;

    /** Per-tenant surrogate. PK component. Generated server-side via
     *  the legacy {@code wb_get_next_val('appr_line_infm','appr_line_no',
     *  biz_key)} branch. JEX column is {@code NUMERIC(15,0)};
     *  {@code @JdbcTypeCode(SqlTypes.NUMERIC)} maps the Java {@code Long}
     *  onto it so Hibernate {@code validate} accepts the numeric column
     *  against the live JEX schema (Long covers 15 digits). */
    @Id
    @JdbcTypeCode(SqlTypes.NUMERIC)
    @Column(name = "APPR_LINE_NO", precision = 15, scale = 0, nullable = false)
    private Long apprLineNo;

    /** User-facing approval-line name. NOT NULL. Bean-validation cap
     *  100 (legacy WSVC length 100) is enforced at the DTO layer. */
    @Column(name = "APPR_LINE_NM", length = 100, nullable = false)
    private String apprLineNm;

    /** Audit &mdash; create user. Legacy {@code VARCHAR(100)}. */
    @Column(name = "REG_ID", length = 100, nullable = false)
    private String regId;

    /** Audit &mdash; create UTC timestamp as {@code yyyyMMddHHmmss}. */
    @Column(name = "REG_DTM", length = 14, nullable = false)
    private String regDtm;

    /** Audit &mdash; last-modify user. Legacy {@code VARCHAR(100)} NOT NULL. */
    @Column(name = "MOD_ID", length = 100, nullable = false)
    private String modId;

    /** Audit &mdash; last-modify UTC timestamp. Legacy {@code VARCHAR(14)} NOT NULL. */
    @Column(name = "MOD_DTM", length = 14, nullable = false)
    private String modDtm;
}
