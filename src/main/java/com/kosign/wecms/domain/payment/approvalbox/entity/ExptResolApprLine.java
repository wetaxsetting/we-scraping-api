package com.kosign.wecms.domain.payment.approvalbox.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One approval-line print-header snapshot row per Request Approval
 * submission. Mirrors legacy
 * {@code WECMS DEV/WECMS/src/wabooks/entity/ExptResolApprLine.java}
 * + the existing {@code EXPT_RESOL_APPR_LINE} table (Flyway {@code V55}).
 *
 * <p>Written by the Request Approval flow (legacy
 * {@code comm_0192_001_c005_act.jsp:489 createApprovalLines}). This is
 * the standing approval-line display snapshot (job-class + employee
 * names from the tenant's environment settings), <b>distinct</b> from
 * {@code EXPT_RESOL_APPRVR_INFM} which is the live routing chain. Rows
 * are created only when env-set {@code 00014} ({@code APPR_BOX_USE_YN})
 * is {@code 'Y'}; the count comes from env-set {@code 00015}
 * ({@code APPR_BOX_DISP_CNT}); names from env-set {@code 00029+i}
 * ({@code JBCL_NM}) / {@code 00034+i} ({@code EMPL_NM}).
 *
 * <p>{@code APPR_LINE_NO} is the legacy {@code varchar(1)} loop counter
 * {@code "1".."N"} — NOT a {@code wb_get_next_val} key (DEV has no
 * branch for this table).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "EXPT_RESOL_APPR_LINE")
@IdClass(ExptResolApprLineId.class)
public class ExptResolApprLine {

    @Id
    @Column(name = "BIZ_KEY", length = 20, nullable = false)
    private String bizKey;

    @Id
    @Column(name = "APPR_SEQ_NO", length = 15, nullable = false)
    private String apprSeqNo;

    @Id
    @Column(name = "APPR_LINE_NO", length = 1, nullable = false)
    private String apprLineNo;

    @Column(name = "JBCL_NM", length = 100)
    private String jbclNm;

    @Column(name = "EMPL_NM", length = 100)
    private String emplNm;

    @Column(name = "REG_ID", length = 100)
    private String regId;

    @Column(name = "REG_DTM", length = 14, nullable = false)
    private String regDtm;
}
