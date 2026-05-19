package com.kosign.wecms.domain.payment.cardtransactions.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/**
 * Composite identity for {@link CardMstr}. Mirrors the legacy JEX PK on
 * {@code CARD_MSTR}: {@code (BIZ_KEY, BANK_CD, CARD_NO)}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class CardMstrId implements Serializable {

    private String bizKey;
    private String bankCd;
    private String cardNo;
}
