package com.kosign.wecms.domain.payment.approvalbox.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/**
 * Composite identity for {@link ExptResolMstr}. Mirrors legacy
 * {@code wabooks.entity.ExptResolMstrPk} — same column pair
 * ({@code BIZ_KEY}, {@code APPR_SEQ_NO}). Both columns are part of
 * every Approval Box query because {@code BIZ_KEY} is the tenant scope
 * and {@code APPR_SEQ_NO} is the per-tenant document sequence.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class ExptResolMstrId implements Serializable {

    private String bizKey;

    private String apprSeqNo;
}
