package com.kosign.wecms.domain.payment.entity;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/** Composite PK for OVRS_PYMN_SUMR_DETL (BIZ_KEY + OVRS_PYMN_NO + OVRS_PYMN_DETL_NO). */
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class OvrsPymnSumrDetlId implements Serializable {
    private String bizKey;
    private String ovrsPymnNo;
    private String ovrsPymnDetlNo;
}
