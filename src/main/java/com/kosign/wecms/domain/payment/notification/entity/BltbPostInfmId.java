package com.kosign.wecms.domain.payment.notification.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/** Composite PK for BLTB_POST_INFM (BIZ_KEY + BLTB_CD + POST_SEQ_NO). */
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class BltbPostInfmId implements Serializable {
    private String bizKey;
    private String bltbCd;
    private BigDecimal postSeqNo;
}
