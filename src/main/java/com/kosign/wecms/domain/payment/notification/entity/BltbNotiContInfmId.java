package com.kosign.wecms.domain.payment.notification.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/** Composite PK for BLTB_NOTI_CONT_INFM (BIZ_KEY + BLTB_CD + NOTI_SEQ_NO + LANG_CD). */
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class BltbNotiContInfmId implements Serializable {
    private String bizKey;
    private String bltbCd;
    private BigDecimal notiSeqNo;
    private String langCd;
}
