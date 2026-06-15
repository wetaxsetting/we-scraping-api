package com.kosign.wecms.domain.payment.notification.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/** Composite PK for BLTB_USER_NEW_POST_INFM (BIZ_KEY + BLTB_CD + USER_ID + POST_SEQ_NO). */
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class BltbUserNewPostInfmId implements Serializable {
    private String bizKey;
    private String bltbCd;
    private String userId;
    private BigDecimal postSeqNo;
}
