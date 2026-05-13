package com.kosign.wecms.domain.payment.entity;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/** Composite PK for OVRS_PYMN_MSTR (BIZ_KEY + OVRS_PYMN_NO). */
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class OvrsPymnMstrId implements Serializable {
    private String bizKey;
    private String ovrsPymnNo;
}
