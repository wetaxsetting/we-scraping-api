package com.kosign.wecms.domain.payment.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Bulletin post row — one per Request-Approval submission.
 *
 * <p>Mirrors legacy {@code wabooks.entity.BltbPostInfm} + the
 * {@code BLTB_POST_INFM} table (Flyway {@code V18}). Written by the
 * legacy {@code createBulletinPost} step
 * ({@code comm_0192_001_c005_act.jsp:544} / {@code card_0001_005_c001_act.jsp:378}):
 * {@code POST_TYPE_CD='02'} (APPROVAL), {@code POST_STTS='PV'} (PRIVATE),
 * {@code BLTB_CD='000000000000000'} (BLTB_CD_APPR), {@code APPR_SEQ_NO}
 * linking back to {@code EXPT_RESOL_MSTR}.
 *
 * <p>Maps only the column subset the request-approval flow writes. The
 * remaining nullable / DB-default columns ({@code REG_CHNL},
 * {@code PC_MOD_YN}, {@code REQ_TYPE}, {@code PIN_*}, {@code REF_*}) stay
 * unmapped — Hibernate {@code ddl-auto=validate} ignores unmapped DB
 * columns, so no schema change is implied and the DB DEFAULTs apply.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "BLTB_POST_INFM")
@IdClass(BltbPostInfmId.class)
public class BltbPostInfm {

    @Id
    @Column(name = "BIZ_KEY", length = 20, nullable = false)
    private String bizKey;

    @Id
    @Column(name = "BLTB_CD", length = 15, nullable = false)
    private String bltbCd;

    @Id
    @Column(name = "POST_SEQ_NO", nullable = false)
    private BigDecimal postSeqNo;

    @Column(name = "POST_TITLE", length = 200, nullable = false)
    private String postTitle;

    /** {@code '02'} = APPROVAL (legacy {@code PostTypeCd.APPROVAL}). */
    @Column(name = "POST_TYPE_CD", length = 2, nullable = false)
    private String postTypeCd;

    /** Frontend {@code POST_APPR_DV} — mirrors APPR_DV for the five
     *  scheduled/overseas types ({@code 21/31/32/34/35}); {@code '10'}
     *  for Card. */
    @Column(name = "POST_APPR_DV", length = 2)
    private String postApprDv;

    /** {@code 'PV'} = PRIVATE (legacy {@code PostStts.PRIVATE}). */
    @Column(name = "POST_STTS", length = 2, nullable = false)
    private String postStts;

    @Column(name = "APPR_SEQ_NO", length = 15)
    private String apprSeqNo;

    @Column(name = "POST_CONT", columnDefinition = "TEXT")
    private String postCont;

    @Column(name = "REG_ID", length = 100, nullable = false)
    private String regId;

    @Column(name = "REG_DTM", length = 14, nullable = false)
    private String regDtm;

    @Column(name = "MOD_ID", length = 100, nullable = false)
    private String modId;

    @Column(name = "MOD_DTM", length = 14, nullable = false)
    private String modDtm;
}
