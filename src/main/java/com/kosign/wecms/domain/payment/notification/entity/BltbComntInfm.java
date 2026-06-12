package com.kosign.wecms.domain.payment.notification.entity;

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
 * Approval-action comment row. Mirrors legacy
 * {@code wabooks.entity.BltbComntInfm} + {@code BLTB_COMNT_INFM}
 * (Flyway {@code V15}). Written by the legacy approve / reject actions
 * ({@code comm_0192_010_u001_act.jsp:262} / {@code comm_0192_010_u002_act.jsp:146})
 * as a system "remark" recording the action:
 * {@code COMNT_DV_CD='R'} (REMARK), {@code COMNT_TASK_CD} =
 * {@code R003} (COMPLETE) / {@code R004} (APPROVED) / {@code R002} (REJECT),
 * {@code COMNT_CONT} = null. Keyed by the parent {@code BLTB_POST_INFM}'s
 * {@code POST_SEQ_NO}.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "BLTB_COMNT_INFM")
@IdClass(BltbComntInfmId.class)
public class BltbComntInfm {

    @Id
    @Column(name = "BIZ_KEY", length = 20, nullable = false)
    private String bizKey;

    @Id
    @Column(name = "BLTB_CD", length = 15, nullable = false)
    private String bltbCd;

    @Id
    @Column(name = "POST_SEQ_NO", nullable = false)
    private BigDecimal postSeqNo;

    @Id
    @Column(name = "COMNT_SEQ_NO", nullable = false)
    private BigDecimal comntSeqNo;

    /** {@code 'C'} comment / {@code 'R'} remark. CHAR(1) on DEV. */
    @Column(name = "COMNT_DV_CD", length = 1, nullable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String comntDvCd;

    /** {@code R002}=Reject / {@code R003}=Complete / {@code R004}=Approved. */
    @Column(name = "COMNT_TASK_CD", length = 4, nullable = false)
    private String comntTaskCd;

    @Column(name = "COMNT_CONT", length = 1000)
    private String comntCont;

    @Column(name = "REG_ID", length = 100, nullable = false)
    private String regId;

    @Column(name = "REG_DTM", length = 14, nullable = false)
    private String regDtm;

    @Column(name = "MOD_ID", length = 100, nullable = false)
    private String modId;

    @Column(name = "MOD_DTM", length = 14, nullable = false)
    private String modDtm;
}
