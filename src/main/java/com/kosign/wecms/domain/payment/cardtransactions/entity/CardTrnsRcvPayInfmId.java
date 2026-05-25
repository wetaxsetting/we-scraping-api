package com.kosign.wecms.domain.payment.cardtransactions.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.math.BigDecimal;

/** Composite PK for {@link CardTrnsRcvPayInfm} —
 *  {@code (BIZ_KEY, CARD_SEQ_NO, RCV_PAY_SEQ_NO)}, mirrors DEV. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class CardTrnsRcvPayInfmId implements Serializable {

    private String     bizKey;
    private String     cardSeqNo;
    private BigDecimal rcvPaySeqNo;
}
