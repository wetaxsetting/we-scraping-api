package com.kosign.wecms.domain.banking.smartorganizer.entity;

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
 * Smart Organizer rule row — mirrors legacy {@code RMRK_ORG_INFM} (V160).
 * Refactor slug: {@code smart-organizer}. Legacy WSVC: {@code fnsh_0200_002}
 * (Organizer Settings popup) — preserved only as a citation here.
 *
 * <p>One row = one auto-classification rule scoped to a tenant
 * ({@code BIZ_KEY}). When an account transaction's {@code RMRK} matches
 * a rule (by {@code MATCH_TYPE = 'E'} → exact, otherwise ILIKE
 * substring), the matched rule's {@code USE_USAG_CD} / {@code TRCO_KEY}
 * / {@code VAS_ACCT_SEQ_NO} / {@code CHART_ACCT_*} suggestions feed
 * into the Smart Organizer eligibility list.
 *
 * <p>For a grouped-usage rule, {@code RCV_PAY_DV} is derived at
 * write-time from {@code USE_USAG_CD}'s {@code USAG_GRP_CD}
 * ({@code '4'} → {@code RCV}, {@code '6'} → {@code PAY}) — legacy
 * {@code fnsh_0200_002_c001_act.jsp:103-110} parity, enforced by
 * {@code OrganizerRuleService.resolveRcvPayDv}. VAS-only / chart-only
 * rules (no {@code USE_USAG_CD}) keep the client-supplied value. The
 * Smart Organizer eligibility query joins against this column off the
 * bank row's {@code INOUT_DV} (1 → RCV, 2 → PAY).
 *
 * <p>Audit columns: {@code REG_ID}, {@code REG_DTM} only (no MOD_*
 * on legacy DEV — V160 does not add them). Does NOT extend
 * {@link com.kosign.wecms.common.entity.BaseEntity} because the audit
 * shape diverges: DEV stores {@code REG_DTM} as {@code VARCHAR(14)}
 * (yyyyMMddHHmmss), whereas {@code BaseEntity} maps {@code OffsetDateTime}
 * with {@code TIMESTAMPTZ}. The service populates these as strings via
 * {@link com.kosign.wecms.common.util.DateTimeUtils#utcNowYyyyMMddHHmmss()}.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "RMRK_ORG_INFM")
@IdClass(RmrkOrgInfmId.class)
public class RmrkOrgInfm {

    @Id
    @Column(name = "BIZ_KEY", length = 20, nullable = false)
    private String bizKey;

    @Id
    @JdbcTypeCode(SqlTypes.NUMERIC)
    @Column(name = "RMRK_SEQ_NO", precision = 10, scale = 0, nullable = false)
    private Long rmrkSeqNo;

    /** {@code RCV} or {@code PAY} — derived at write-time from
     *  {@code USE_USAG_CD}'s usage-group code. NULL when the rule
     *  is configured only with {@code VAS_ACCT_SEQ_NO} / chart codes
     *  (no usage). */
    @Column(name = "RCV_PAY_DV", length = 3)
    private String rcvPayDv;

    /** Remark text pattern. When {@code MATCH_TYPE = 'E'} the bank
     *  row's {@code RMRK} must equal this verbatim; otherwise the
     *  bank row's {@code RMRK} must ILIKE-contain this substring. */
    @Column(name = "RMRK", length = 500, nullable = false)
    private String rmrk;

    /** {@code C} = contains (ILIKE substring) · {@code E} = exact.
     *  {@code bpchar(1)} on DEV — annotated for Hibernate JDBC type
     *  alignment (same precedent as V38 sort_yn). */
    @Column(name = "MATCH_TYPE", length = 1, nullable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String matchType;

    /** Per-tenant display order — drives the Organizer Settings list
     *  ORDER BY ASC. JEX {@code NUMERIC(10,0)} — mapped via
     *  {@code @JdbcTypeCode(NUMERIC)} so Hibernate validate passes against
     *  the live JEX column (2026-06-09 JEX-parity alignment; supersedes the
     *  earlier local-INTEGER convention). */
    @JdbcTypeCode(SqlTypes.NUMERIC)
    @Column(name = "DISP_ORD", precision = 10, scale = 0, nullable = false)
    private Integer dispOrd;

    @Column(name = "TRCO_KEY", length = 20)
    private String trcoKey;

    @Column(name = "USE_USAG_CD", length = 20)
    private String useUsagCd;

    @JdbcTypeCode(SqlTypes.NUMERIC)
    @Column(name = "VAS_ACCT_SEQ_NO", precision = 10, scale = 0)
    private Long vasAcctSeqNo;

    @Column(name = "REG_ID", length = 100)
    private String regId;

    /** Legacy {@code VARCHAR(14)} yyyyMMddHHmmss audit timestamp. Set
     *  by the service on create; never updated (no MOD_DTM column). */
    @Column(name = "REG_DTM", length = 14)
    private String regDtm;

    @Column(name = "CHART_ACCT_SEQ_NO", length = 10)
    private String chartAcctSeqNo;

    @Column(name = "CHART_ACCT_CD", length = 20)
    private String chartAcctCd;
}
