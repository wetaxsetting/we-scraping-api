package com.kosign.wecms.domain.setting.approvalline.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/**
 * Composite identity for {@link ApprovalLineApprover}. Mirrors the
 * legacy JEX PK on {@code APPR_LINE_INFM_DETL}:
 * {@code (BIZ_KEY, APPR_LINE_NO, APPRVR_ID)}.
 *
 * <p>The PK includes {@code APPRVR_ID} so the diff-sync algorithm can
 * locate the row to update / delete by user id without needing a
 * secondary surrogate.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class ApprovalLineApproverId implements Serializable {
    private String bizKey;
    private Long   apprLineNo;
    private String apprvrId;
}
