package com.kosign.wecms.domain.payment.cardtransactions.entity;

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

import java.math.BigDecimal;

/**
 * Payment &gt; Card Transactions &mdash; one row per scraped card
 * transaction. Mirrors legacy JEX {@code wabooks.entity.CardTrnsHstr}
 * (file {@code WECMS DEV/WECMS/src/wabooks/entity/CardTrnsHstr.java})
 * and the V176 column-for-column schema.
 *
 * <p>Sprint 0 scope: read + classify only. Two patchable scalars
 * ({@code CONT}, {@code CARD_TRNS_USAGE_CODE}) plus the always-paired
 * reporting account ({@code CHART_ACCT_CD} + {@code CHART_ACCT_SEQ_NO}).
 * The refactor never creates or deletes rows — scraping stays on the
 * JEX side under the documented coexistence carve-out.
 *
 * <p>The entity maps the full 26 DEV columns so future sprints that
 * surface {@code USING_TYPE} / {@code NOTE} / {@code AUTH_CODE} /
 * {@code CITY} / {@code INOUT_DV} / {@code ERP_SND_YN} don't need to
 * extend the JPA mapping (legacy parity precedent: {@code ScdulPymnSumr}
 * also maps every column even when Sprint 0 only reads a slice).
 *
 * <p>The audit columns ({@code REG_ID} / {@code REG_DTM} / {@code MOD_ID}
 * / {@code MOD_DTM}) follow the legacy {@code VARCHAR(14)} {@code
 * yyyyMMddHHmmss} timestamp shape rather than the refactor-wide
 * {@code BaseEntity} {@code OffsetDateTime} shape &mdash; same precedent
 * as {@code ApprovalLine}, {@code ChartOfAccount}, {@code ScdulPymnSumr},
 * and {@code ErpTrnsHstr}. The service stamps {@code MOD_ID} /
 * {@code MOD_DTM} explicitly on every PATCH / PUT.
 *
 * <p>{@code CCY_CD} / {@code INOUT_DV} / {@code ERP_SND_YN} are
 * {@code CHAR(N)} on DEV. {@link JdbcTypeCode @JdbcTypeCode(SqlTypes.CHAR)}
 * tells Hibernate to map them via the JDBC {@code CHAR} type so the
 * schema validator accepts {@code bpchar} against a Java {@code String}
 * field &mdash; same precedent as {@code ChartOfAccount.stts}.
 *
 * <p>Tenant scoping: {@code BIZ_KEY} is part of the composite PK and
 * the first predicate of every repository query.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "CARD_TRNS_HSTR")
@IdClass(CardTrnsHstrId.class)
public class CardTrnsHstr {

    /** Tenant scope. PK component. */
    @Id
    @Column(name = "BIZ_KEY", length = 20, nullable = false)
    private String bizKey;

    /** Per-tenant surrogate. PK component. Scraper-allocated; never
     *  written by refactor code (Sprint 0 has no Create path). */
    @Id
    @Column(name = "CARD_SEQ_NO", length = 20, nullable = false)
    private String cardSeqNo;

    @Column(name = "BANK_CD", length = 20, nullable = false)
    private String bankCd;

    @Column(name = "CARD_NO", length = 100, nullable = false)
    private String cardNo;

    /** {@code yyyyMMdd} transaction date. */
    @Column(name = "TRSC_DT", length = 8, nullable = false)
    private String trscDt;

    /** {@code HHmmss} transaction time. Nullable on DEV. */
    @Column(name = "TRSC_TM", length = 6)
    private String trscTm;

    @Column(name = "CARD_NM", length = 50)
    private String cardNm;

    @Column(name = "MERCH_NM", length = 50)
    private String merchNm;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "CCY_CD", length = 3, nullable = false)
    private String ccyCd;

    /** Legacy {@code '1'} in / {@code '2'} out / {@code '0'} other —
     *  see {@link com.kosign.wecms.common.enums.InoutDv}. Not surfaced
     *  on the wire today; kept on the entity for forward compat. */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "INOUT_DV", length = 1, nullable = false)
    private String inoutDv;

    /** Signed transaction amount. NUMERIC(20,6) on DEV (legacy parity). */
    @Column(name = "TRSC_AMT", precision = 20, scale = 6)
    private BigDecimal trscAmt;

    @Column(name = "USING_TYPE", length = 50)
    private String usingType;

    /** Purchase status code — single character resolved client-side
     *  via the {@code stts_key} i18n hint the service emits. */
    @Column(name = "PRCH_STTS", length = 50)
    private String prchStts;

    @Column(name = "NOTE", length = 255)
    private String note;

    /** Free-form description. Patchable via the {@code field=description}
     *  branch of {@code PATCH /api/v1/payment/card-transactions/{id}}. */
    @Column(name = "CONT", length = 200)
    private String cont;

    /** Linked Expense Resolution sequence number (FK shape to
     *  {@code EXPT_RESOL_MSTR.APPR_SEQ_NO}). Nullable while the
     *  transaction has not been included in an expense report. */
    @Column(name = "APPR_SEQ_NO", length = 15)
    private String apprSeqNo;

    /** Patchable purpose code. Refactor entry validates against
     *  {@code USAG_INFM} when the picker lands; Sprint 0 trusts the
     *  raw wire value. */
    @Column(name = "CARD_TRNS_USAGE_CODE", length = 20)
    private String cardTrnsUsageCode;

    @Column(name = "AUTH_CODE", length = 15)
    private String authCode;

    @Column(name = "CITY", length = 100)
    private String city;

    @Column(name = "REG_ID", length = 100, nullable = false)
    private String regId;

    /** {@code yyyyMMddHHmmss}. NOT NULL. */
    @Column(name = "REG_DTM", length = 14, nullable = false)
    private String regDtm;

    @Column(name = "MOD_ID", length = 100, nullable = false)
    private String modId;

    /** {@code yyyyMMddHHmmss}. NOT NULL. Service stamps this on every
     *  PATCH / PUT. */
    @Column(name = "MOD_DTM", length = 14, nullable = false)
    private String modDtm;

    /** Reporting-account surrogate from {@code CHART_ACCT_INFM}.
     *  Patchable via {@code PUT /…/{id}/reporting-account}. Nullable —
     *  cleared rows render the "Select Account" affordance on the
     *  frontend. */
    @Column(name = "CHART_ACCT_SEQ_NO", length = 10)
    private String chartAcctSeqNo;

    @Column(name = "CHART_ACCT_CD", length = 20)
    private String chartAcctCd;

    /** ERP-send flag. CHAR(1) on DEV, DEFAULT 'N'. Set by the JEX-side
     *  ERP push batch; the refactor reads but never writes. */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "ERP_SND_YN", length = 1, nullable = false)
    private String erpSndYn;
}
