package com.kosign.wecms.domain.payment.entity;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/** Composite PK for OVRS_PYMN_DTLS (BIZ_KEY + OVRS_PYMN_NO + TRN_SEQ). */
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class OvrsPymnDtlsId implements Serializable {
    private String bizKey;
    private String ovrsPymnNo;
    private Integer trnSeq;
}
