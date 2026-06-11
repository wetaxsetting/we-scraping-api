package com.kosign.wecms.domain.payment.cardtransactions.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.math.BigDecimal;

/** Composite key for {@link BusaDocRcvPayHis} —
 *  {@code (BIZ_KEY, DOC_ID, RCV_PAY_HIS_SEQ_NO)}, the JEX natural key
 *  (JEX has no PK / no {@code SEQ_NO} column). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class BusaDocRcvPayHisId implements Serializable {

    private String     bizKey;
    private String     docId;
    private BigDecimal rcvPayHisSeqNo;
}
