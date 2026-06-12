package com.kosign.wecms.domain.payment.approvalbox.entity;

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
 * One expense-resolution detail row per Request Approval submission.
 * Mirrors legacy {@code WECMS DEV/WECMS/src/wabooks/entity/ExptResolDetl.java}
 * + the existing {@code EXPT_RESOL_DETL} table (Flyway {@code V57}).
 *
 * <p>Written by the Request Approval flow (legacy
 * {@code comm_0192_001_c005_act.jsp:340 createExptResolDetl}). For the
 * scheduled payment types the row carries {@code DOC_ID} = the payment
 * batch business key, {@code AMT} = the batch total, {@code CCY_CD} =
 * the batch currency. {@code APPR_DETL_SEQ_NO} is allocated via the
 * existing {@code wb_get_next_val('expt_resol_detl','appr_detl_seq_no',
 * biz_key)} funnel branch.
 *
 * <p>Maps the column subset the Request Approval flow writes. The
 * scheduled / overseas single-detail paths populate {@code CCY_CD} /
 * {@code AMT} / {@code DOC_ID}; the Card path additionally populates
 * {@code BUSA_USAG_CD} / {@code MEMO} / {@code DOC_TYPE} / {@code TRSC_DT}
 * (legacy {@code card_0001_005} REC3 — one detail per card transaction).
 * The remaining nullable legacy columns ({@code TRCO_*}) stay unmapped —
 * Hibernate {@code ddl-auto=validate} ignores unmapped DB columns, so no
 * schema change is implied.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "EXPT_RESOL_DETL")
@IdClass(ExptResolDetlId.class)
public class ExptResolDetl {

    @Id
    @Column(name = "BIZ_KEY", length = 20, nullable = false)
    private String bizKey;

    @Id
    @Column(name = "APPR_SEQ_NO", length = 15, nullable = false)
    private String apprSeqNo;

    @Id
    @Column(name = "APPR_DETL_SEQ_NO", nullable = false)
    private BigDecimal apprDetlSeqNo;

    /** Currency of {@link #amt} — legacy {@code CHAR(3)} with a CHECK
     *  constraint limiting the value to {@code VND}/{@code USD}/{@code KRW}.
     *  {@code @JdbcTypeCode(CHAR)} keeps the Java {@code String} bound to
     *  the {@code bpchar(3)} column (same pattern as
     *  {@code ExptResolApprvrInfm.apprvrStts}). */
    @Column(name = "CCY_CD", length = 3, nullable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String ccyCd;

    @Column(name = "AMT", nullable = false)
    private BigDecimal amt;

    @Column(name = "DOC_ID", length = 50)
    private String docId;

    /** Card-detail purpose code (legacy {@code REC3.BUSA_USAG_CD}). Null
     *  for the scheduled / overseas single-detail paths. */
    @Column(name = "BUSA_USAG_CD", length = 20)
    private String busaUsagCd;

    /** Per-card memo (legacy {@code REC3.MEMO}). Null for non-card paths. */
    @Column(name = "MEMO", length = 1000)
    private String memo;

    /** Legacy {@code DOC_TYPE} — card details carry {@code 'B'}
     *  (legacy {@code card_0001_005} REC3); CHECK limits to
     *  {@code 'B'}/{@code 'E'}/{@code ''}. {@code @JdbcTypeCode(CHAR)}
     *  binds the Java {@code String} to the {@code bpchar(1)} column. */
    @Column(name = "DOC_TYPE", length = 1)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String docType;

    /** Legacy {@code TRSC_DT} ({@code yyyyMMdd}). Null on the card path
     *  (legacy REC3 sends {@code ""}). */
    @Column(name = "TRSC_DT", length = 8)
    private String trscDt;

    @Column(name = "REG_ID", length = 100, nullable = false)
    private String regId;

    @Column(name = "REG_DTM", length = 14, nullable = false)
    private String regDtm;
}
