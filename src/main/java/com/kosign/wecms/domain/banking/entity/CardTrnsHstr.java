package com.kosign.wecms.domain.banking.entity;

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
 * Mirrors legacy wecmsvndev.CARD_TRNS_HSTR (V166).
 *
 * <p>Source row for the Cash Flow report's corporate-card aggregation.
 * The legacy get_cashflow_report() function reads CHART_ACCT_CD and
 * CHART_ACCT_SEQ_NO directly off this table (not via a sibling join);
 * card_trns_rcv_pay_infm exists on DEV but is NOT consulted by the
 * cash-flow path. Each row participates iff CHART_ACCT_CD is non-empty
 * AND the parent appr_seq_no's wb_get_appr_stts(...) returns 'C'.
 *
 * <p>Does NOT extend BaseEntity: audit columns are VARCHAR(14) holding
 * yyyyMMddHHmmss strings (legacy parity), not OffsetDateTime. Same
 * precedent as AcctTrnsHstr.
 *
 * <p>CHAR(N) columns (CCY_CD, INOUT_DV, ERP_SND_YN) carry
 * {@code @JdbcTypeCode(SqlTypes.CHAR)} so Hibernate {@code validate}
 * accepts the {@code bpchar} JDBC type — same precedent as
 * {@link com.kosign.wecms.domain.setting.entity.SysScrOptionWecms#sortYn}
 * (V38).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "CARD_TRNS_HSTR")
@IdClass(CardTrnsHstrId.class)
public class CardTrnsHstr {

    @Id
    @Column(name = "BIZ_KEY", length = 20, nullable = false)
    private String bizKey;

    @Id
    @Column(name = "CARD_SEQ_NO", length = 20, nullable = false)
    private String cardSeqNo;

    @Column(name = "BANK_CD", length = 20, nullable = false)
    private String bankCd;

    @Column(name = "CARD_NO", length = 100, nullable = false)
    private String cardNo;

    @Column(name = "TRSC_DT", length = 8, nullable = false)
    private String trscDt;

    @Column(name = "TRSC_TM", length = 6)
    private String trscTm;

    @Column(name = "CARD_NM", length = 50)
    private String cardNm;

    @Column(name = "MERCH_NM", length = 50)
    private String merchNm;

    @Column(name = "CCY_CD", length = 3, nullable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String ccyCd;

    @Column(name = "INOUT_DV", length = 1, nullable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String inoutDv;

    @Column(name = "TRSC_AMT")
    private BigDecimal trscAmt;

    @Column(name = "USING_TYPE", length = 50)
    private String usingType;

    @Column(name = "PRCH_STTS", length = 50)
    private String prchStts;

    @Column(name = "NOTE", length = 255)
    private String note;

    @Column(name = "CONT", length = 200)
    private String cont;

    /**
     * Approval-line reference. Joined against EXPT_RESOL_MSTR by the
     * legacy {@code wb_get_appr_stts(...)} gate. The refactor replaces
     * the DB function with a Java approval-status resolver in Phase 2.
     */
    @Column(name = "APPR_SEQ_NO", length = 15)
    private String apprSeqNo;

    @Column(name = "CARD_TRNS_USAGE_CODE", length = 20)
    private String cardTrnsUsageCode;

    @Column(name = "AUTH_CODE", length = 15)
    private String authCode;

    @Column(name = "CITY", length = 100)
    private String city;

    @Column(name = "REG_ID", length = 100, nullable = false)
    private String regId;

    @Column(name = "REG_DTM", length = 14, nullable = false)
    private String regDtm;

    @Column(name = "MOD_ID", length = 100, nullable = false)
    private String modId;

    @Column(name = "MOD_DTM", length = 14, nullable = false)
    private String modDtm;

    /**
     * Chart-of-accounts classifier for the cash-flow report. The row
     * participates in the aggregation iff this column is non-null and
     * non-empty (matches the function's
     * {@code WHERE cth.chart_acct_cd <> ''} predicate).
     */
    @Column(name = "CHART_ACCT_SEQ_NO", length = 10)
    private String chartAcctSeqNo;

    @Column(name = "CHART_ACCT_CD", length = 20)
    private String chartAcctCd;

    @Column(name = "ERP_SND_YN", length = 1, nullable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String erpSndYn;
}
