package com.kosign.wecms.domain.banking.bankcredentials.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Composite identity for {@link BankCertInfo}. Mirrors legacy JEX
 * {@code BankCertInfoPk}: {@code (BIZ_KEY, SEQ_NO)}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class BankCertInfoId implements Serializable {
    private String bizKey;
    private BigDecimal seqNo;
}
