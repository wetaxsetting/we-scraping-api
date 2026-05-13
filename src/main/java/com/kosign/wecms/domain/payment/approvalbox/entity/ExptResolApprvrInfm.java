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
 * One row per approver assigned to an Approval Box document. Mirrors
 * legacy {@code WECMS DEV/WECMS/src/wabooks/entity/ExptResolApprvrInfm.java}
 * + {@code IDO.EXPT_RESOL_APPRVR_INFM_R001.xml} column-for-column.
 *
 * <p>The Approve and Reject endpoints find this entity by
 * ({@code bizKey}, {@code apprSeqNo}, {@code apprvrId}) and mutate
 * {@link #apprvrStts} (see
 * {@link com.kosign.wecms.domain.payment.approvalbox.service.ApprvrStts}),
 * {@link #apprDtm}, {@link #modId}, {@link #modDtm}. Reject also bumps
 * downstream {@code WAITING} rows to {@code STOPPED}.
 *
 * <p>Legacy schema has <b>no foreign key</b> from this table to
 * {@code EXPT_RESOL_MSTR} — preserved on the refactor side for
 * column-for-column parity. Application code enforces the master-row
 * existence check before issuing updates.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "EXPT_RESOL_APPRVR_INFM")
@IdClass(ExptResolApprvrInfmId.class)
public class ExptResolApprvrInfm {

    @Id
    @Column(name = "BIZ_KEY", length = 20, nullable = false)
    private String bizKey;

    @Id
    @Column(name = "APPR_SEQ_NO", length = 15, nullable = false)
    private String apprSeqNo;

    @Id
    @Column(name = "APPRVR_ID", length = 100, nullable = false)
    private String apprvrId;

    /** Approver state — {@code 'P'}/{@code 'A'}/{@code 'R'}/{@code 'W'}/
     *  {@code 'S'}. {@code @JdbcTypeCode(CHAR)} keeps the Java
     *  {@code String} bound to the legacy {@code CHAR(1)} column
     *  (same pattern as {@code SysScrOptionWecms.sortYn}). */
    @Column(name = "APPRVR_STTS", length = 1, nullable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String apprvrStts;

    @Column(name = "APPRVR_NM", length = 100, nullable = false)
    private String apprvrNm;

    @Column(name = "APPR_DTM", length = 14)
    private String apprDtm;

    @Column(name = "REG_ID", length = 100)
    private String regId;

    @Column(name = "REG_DTM", length = 14, nullable = false)
    private String regDtm;

    @Column(name = "MOD_ID", length = 100, nullable = false)
    private String modId;

    @Column(name = "MOD_DTM", length = 14, nullable = false)
    private String modDtm;

    @Column(name = "READ_YN", length = 1, nullable = false)
    private String readYn;

    /** Approver order (1..5). Drives the visible chain rendered by
     *  the frontend hover-card. */
    @Column(name = "APPRVR_LINE_NO")
    private BigDecimal apprvrLineNo;

    /** Reference to the approval-line template
     *  ({@code EXPT_RESOL_APPR_LINE.APPR_LINE_NO}). */
    @Column(name = "APPR_LINE_NO")
    private BigDecimal apprLineNo;
}
