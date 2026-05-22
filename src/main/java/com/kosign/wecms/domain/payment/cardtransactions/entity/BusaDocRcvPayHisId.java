package com.kosign.wecms.domain.payment.cardtransactions.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.math.BigDecimal;

/** Composite PK for {@link BusaDocRcvPayHis} —
 *  {@code (BIZ_KEY, DOC_ID, SEQ_NO)}, mirrors V13 schema. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class BusaDocRcvPayHisId implements Serializable {

    private String     bizKey;
    private String     docId;
    private BigDecimal seqNo;
}
