package com.kosign.wecms.domain.payment.approvalbox.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/**
 * Composite identity for {@link ExptResolApprLine}. Mirrors legacy
 * {@code wabooks.entity.ExptResolApprLinePk} — the triple
 * ({@code BIZ_KEY}, {@code APPR_SEQ_NO}, {@code APPR_LINE_NO}).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class ExptResolApprLineId implements Serializable {

    private String bizKey;

    private String apprSeqNo;

    private String apprLineNo;
}
