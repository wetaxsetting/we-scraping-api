package com.kosign.wecms.domain.payment.approvalbox.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Composite identity for {@link ExptResolDetl}. Mirrors legacy
 * {@code wabooks.entity.ExptResolDetlPk} — the triple
 * ({@code BIZ_KEY}, {@code APPR_SEQ_NO}, {@code APPR_DETL_SEQ_NO}).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class ExptResolDetlId implements Serializable {

    private String bizKey;

    private String apprSeqNo;

    private BigDecimal apprDetlSeqNo;
}
