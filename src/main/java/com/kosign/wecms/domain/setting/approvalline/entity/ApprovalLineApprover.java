package com.kosign.wecms.domain.setting.approvalline.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Settings &gt; Approval Line &mdash; approver chain row (legacy
 * {@code APPR_LINE_INFM_DETL}). Mirrors JEX
 * {@code wabooks.entity.ApprLineInfmDetl} column-for-column (V90).
 *
 * <p>Maximum 5 rows per parent line is enforced in the service layer
 * (legacy {@code WBW1164} → refactor
 * {@code WBE_APPROVAL_LINE_TOO_MANY_APPROVERS}) and pinned at the DB
 * level by {@code APPRVR_DISP_ORD NUMERIC(1,0)}.
 *
 * <p>{@code APPRVR_POSI} and {@code APPRVR_DEPT} are nullable on DEV
 * &mdash; older rows may carry NULL. The service writes empty string
 * {@code ""} (never null) on Create / Update so newly-persisted rows
 * always materialise the fields; legacy parity with
 * {@code comm_0222_003_u001_act.jsp:144-148}.
 *
 * <p>Audit columns are nullable on DEV (older rows have NULL). The
 * service populates them with the current request's user id +
 * {@code yyyyMMddHHmmss} UTC on every write.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "APPR_LINE_INFM_DETL")
@IdClass(ApprovalLineApproverId.class)
public class ApprovalLineApprover {

    /** Tenant scope. PK component. */
    @Id
    @Column(name = "BIZ_KEY", length = 20, nullable = false)
    private String bizKey;

    /** Parent approval line. PK component. No JPA association &mdash;
     *  the service walks both tables explicitly. Stored as
     *  {@code BIGINT} locally after the V92 hotfix &mdash; DEV's
     *  {@code NUMERIC(15,0)} fits comfortably. */
    @Id
    @Column(name = "APPR_LINE_NO", nullable = false)
    private Long apprLineNo;

    /** Approver's {@code USER_INFM.USER_ID}. PK component. */
    @Id
    @Column(name = "APPRVR_ID", length = 100, nullable = false)
    private String apprvrId;

    /** Display + approval order, 1..5. Stored as {@code INTEGER}
     *  locally after the V92 hotfix (DEV is {@code NUMERIC(1,0)};
     *  Hibernate {@code validate} mode requires the integer type
     *  for the Java {@code Integer} mapping). The 5-approver cap
     *  is enforced at the service layer (legacy {@code WBW1164}
     *  parity → refactor {@code WBE_APPROVAL_LINE_TOO_MANY_APPROVERS});
     *  service re-numbers on every write from request-array position. */
    @Column(name = "APPRVR_DISP_ORD", nullable = false)
    private Integer apprvrDispOrd;

    /** Snapshot of the approver's display name at time of save. NOT NULL. */
    @Column(name = "APPRVR_NM", length = 100, nullable = false)
    private String apprvrNm;

    /** Snapshot of the approver's position. Nullable on DEV; service
     *  writes "" (never null) on Create / Update for legacy parity. */
    @Column(name = "APPRVR_POSI", length = 100)
    private String apprvrPosi;

    /** Snapshot of the approver's department. Same parity rule as
     *  {@link #apprvrPosi}. */
    @Column(name = "APPRVR_DEPT", length = 100)
    private String apprvrDept;

    /** Audit &mdash; create user. Nullable on DEV; populated on every
     *  service-side write. */
    @Column(name = "REG_ID", length = 100)
    private String regId;

    /** Audit &mdash; create timestamp ({@code yyyyMMddHHmmss}). */
    @Column(name = "REG_DTM", length = 14)
    private String regDtm;

    /** Audit &mdash; last-modify user. */
    @Column(name = "MOD_ID", length = 100)
    private String modId;

    /** Audit &mdash; last-modify timestamp ({@code yyyyMMddHHmmss}). */
    @Column(name = "MOD_DTM", length = 14)
    private String modDtm;
}
