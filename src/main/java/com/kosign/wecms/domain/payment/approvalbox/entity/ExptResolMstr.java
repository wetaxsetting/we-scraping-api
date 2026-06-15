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
 * Payment &gt; Approval Box — master row. Mirrors legacy
 * {@code WECMS DEV/WECMS/src/wabooks/entity/ExptResolMstr.java} +
 * {@code IDO.EXPT_RESOL_MSTR_R001.xml} column-for-column.
 *
 * <p>The Approval Box list service (legacy
 * {@code resl_0001_001_r001_act.jsp:78}) filters this table to six
 * payment types — {@code APPR_DV IN ('21','31','32','33','34','35')} —
 * see {@link com.kosign.wecms.domain.payment.approvalbox.service.ApprDv}
 * for the enum.
 *
 * <p>Approve / Reject paths mutate two columns on this entity:
 * {@code APPR_TYPE_CD} (the tab discriminator — see
 * {@link com.kosign.wecms.domain.payment.approvalbox.service.ApprTypeCd})
 * and audit columns {@code MOD_ID} / {@code MOD_DTM}. Every other field
 * is read-only from the Approval Box surface.
 *
 * <p>Tenant scoping is the caller's responsibility — every repository
 * method must constrain on {@code BIZ_KEY} from
 * {@link com.kosign.wecms.common.tenant.TenantContext#currentBizKey()}.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "EXPT_RESOL_MSTR")
@IdClass(ExptResolMstrId.class)
public class ExptResolMstr {

    @Id
    @Column(name = "BIZ_KEY", length = 20, nullable = false)
    private String bizKey;

    @Id
    @Column(name = "APPR_SEQ_NO", length = 15, nullable = false)
    private String apprSeqNo;

    /** Payment-type discriminator. {@code '21'}/{@code '31'}/{@code '32'}/
     *  {@code '33'}/{@code '34'}/{@code '35'} for Approval Box rows. */
    @Column(name = "APPR_DV", length = 2, nullable = false)
    private String apprDv;

    /** Internal legacy status {@code '0'..'9'} — not the Approval Box
     *  status. The Approval Box badge derives from
     *  {@link #apprTypeCd}; {@code APPR_STS} is preserved for parity
     *  with payment-execution logic in sibling modules. */
    @Column(name = "APPR_STS", length = 2, nullable = false)
    private String apprSts;

    @Column(name = "DRAFT_DATE", length = 8, nullable = false)
    private String draftDate;

    @Column(name = "DRAFT_TIME", length = 6, nullable = false)
    private String draftTime;

    @Column(name = "DRAFT_USER_ID", length = 100, nullable = false)
    private String draftUserId;

    @Column(name = "DRAFT_USER_NM", length = 100, nullable = false)
    private String draftUserNm;

    /** BIZ_KEY of the drafting user's company — legacy parity value. */
    @Column(name = "DRAFT_USER_SITE_CD", length = 20, nullable = false)
    private String draftUserSiteCd;

    /** Company name of the drafting user — legacy parity value. */
    @Column(name = "DRAFT_USER_SITE_NM", length = 200, nullable = false)
    private String draftUserSiteNm;

    @Column(name = "TITL", length = 200, nullable = false)
    private String titl;

    @Column(name = "DOC_NO", length = 50, nullable = false)
    private String docNo;

    @Column(name = "DOC_NM", length = 100, nullable = false)
    private String docNm;

    /** Free-form approval content / requestor note (legacy
     *  {@code BLTB_POST_INFM.POST_CONT} surfaced through
     *  {@code EXPT_RESOL_MSTR.CONT}). Renders in the "Approval Content"
     *  section of the read-only Approval Details popup. */
    @Column(name = "CONT", columnDefinition = "TEXT")
    private String cont;

    /** Legacy column name preserved for schema parity — for VN tenants
     *  this stores the VND amount, not KHR. The refactor's wire field
     *  is the semantic {@code tot_pay_amt}. */
    @Column(name = "TOT_PAY_KHR_AMT")
    private BigDecimal totPayKhrAmt;

    @Column(name = "TOT_PAY_USD_AMT")
    private BigDecimal totPayUsdAmt;

    @Column(name = "TOT_PAY_VND_AMT")
    private BigDecimal totPayVndAmt;

    @Column(name = "PAY_DATE", length = 8)
    private String payDate;

    @Column(name = "DEL_YN", length = 1, nullable = false)
    private String delYn;

    /** 'Y' = manually registered by drafting user (legacy default 'Y'). */
    @Column(name = "HAND_REG_YN", length = 1, nullable = false)
    private String handRegYn;

    /** 'Y' = approval box feature is active for this document. */
    @Column(name = "APPR_BOX_USE_YN", length = 1, nullable = false)
    private String apprBoxUseYn;

    /** 'N' = do not display approval date in the printed form. */
    @Column(name = "APPR_DAT_DISP_YN", length = 1, nullable = false)
    private String apprDatDispYn;

    /** 'Y' = display company logo in the printed form. */
    @Column(name = "CMPN_LOGO_DISP_YN", length = 1, nullable = false)
    private String cmpnLogoDispYn;

    /** 'Y' = display drafter details in the printed form. */
    @Column(name = "REG_DISP_YN", length = 1, nullable = false)
    private String regDispYn;

    @Column(name = "PAY_END_YN", length = 1, nullable = false)
    private String payEndYn;

    /** Set on approval completion (legacy {@code comm_0192_010_u001/u002}
     *  when all approvers have voted): {@code yyyyMMddHHmmss}. */
    @Column(name = "PAY_END_DTM", length = 14)
    private String payEndDtm;

    /** Name of the user who cast the final vote (legacy
     *  {@code loginUser.getName()}). */
    @Column(name = "PAY_END_NM", length = 100)
    private String payEndNm;

    /** Approval Box tab discriminator. {@code '00'} Pending /
     *  {@code '01'} Approved / {@code '02'} Rejected /
     *  {@code '03'} In Progress. Approve / Reject mutate this column
     *  alongside the per-approver row updates. */
    @Column(name = "APPR_TYPE_CD", length = 2, nullable = false)
    private String apprTypeCd;

    @Column(name = "REG_ID", length = 100, nullable = false)
    private String regId;

    @Column(name = "REG_DTM", length = 14, nullable = false)
    private String regDtm;

    @Column(name = "MOD_ID", length = 100)
    private String modId;

    @Column(name = "MOD_DTM", length = 14, nullable = false)
    private String modDtm;

    /** Single-character legacy {@code APPR_FUND_TP} — mapped via
     *  {@code @JdbcTypeCode(CHAR)} so Hibernate binds the Java
     *  {@code String} to the DEV-parity {@code CHAR(1)} column. */
    @Column(name = "APPR_FUND_TP", length = 1)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String apprFundTp;
}
