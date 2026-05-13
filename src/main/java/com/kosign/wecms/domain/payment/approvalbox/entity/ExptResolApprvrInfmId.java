package com.kosign.wecms.domain.payment.approvalbox.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/**
 * Composite identity for {@link ExptResolApprvrInfm}. Mirrors legacy
 * {@code wabooks.entity.ExptResolApprvrInfmPk} — same triple
 * ({@code BIZ_KEY}, {@code APPR_SEQ_NO}, {@code APPRVR_ID}). One
 * approver may appear once per approval row.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class ExptResolApprvrInfmId implements Serializable {

    private String bizKey;

    private String apprSeqNo;

    private String apprvrId;
}
